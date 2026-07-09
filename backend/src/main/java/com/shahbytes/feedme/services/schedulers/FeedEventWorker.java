package com.shahbytes.feedme.services.schedulers;

import com.shahbytes.feedme.config.FeedAsyncProperties;
import com.shahbytes.feedme.models.DeadLetterFeedEvent;
import com.shahbytes.feedme.models.FeedEventFailure;
import com.shahbytes.feedme.models.ProcessedFeedEvent;
import com.shahbytes.feedme.repository.DeadLetterFeedEventRepository;
import com.shahbytes.feedme.repository.FeedEventFailureRepository;
import com.shahbytes.feedme.repository.ProcessedFeedEventRepository;
import com.shahbytes.feedme.services.FeedMetricsService;
import com.shahbytes.feedme.services.FeedService;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(value = "feed.async.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Getter
public class FeedEventWorker {
    @PostConstruct
    public void initializeConsumerGroup() {
        validateConfiguration();
        String streamKey = properties.getStream().getKey();
        String consumerGroupName = properties.getConsumer().getGroup();
        try {

            StreamInfo.XInfoGroups groups = redisTemplate.opsForStream()
                    .groups(streamKey);

            boolean exists = groups.stream()
                    .anyMatch(group ->
                            consumerGroupName.equals(group.groupName()));

            if (!exists) {
                redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.latest(), consumerGroupName);
            }
        } catch (RedisSystemException exception) {
            if (isMissingStream(exception)) {
                createConsumerGroupWithStream(streamKey, consumerGroupName);
                return;
            }
            throw exception;
        }
    }

    private void createConsumerGroupWithStream(String streamKey, String consumerGroupName) {
        RecordId initializeRecordId = redisTemplate.opsForStream().add(
                MapRecord.create(streamKey, Map.of("__system__", "stream-init"))
        );

        try {
            redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.latest(), consumerGroupName);
        } catch (RedisSystemException exception) {
            if (!groupAlreadyExists(exception)) {
                throw exception;
            }
        } finally {
            if (initializeRecordId != null) {
                redisTemplate.opsForStream().delete(streamKey, initializeRecordId);
            }
        }
    }

    private boolean groupAlreadyExists(RedisSystemException exception) {
        Throwable cause = exception.getCause();
        return cause != null
                && cause.getMessage() != null
                && cause.getMessage().contains("BUSYGROUP");
    }

    private boolean isMissingStream(RedisSystemException exception) {
        Throwable cause = exception.getCause();
        return cause != null
                && cause.getMessage() != null
                && cause.getMessage().contains("ERR no such key");
    }


    private void validateConfiguration() {
        requireText(properties.getStream().getKey(), "feed.async.stream.key");
        requireText(properties.getConsumer().getGroup(), "feed.async.consumer.group");
        requireText(properties.getConsumer().getName(), "feed.async.consumer.name");
        requireText(properties.getConsumer().getDlqStreamKey(), "feed.async.consumer.dlq-stream-key");

        if (properties.getConsumer().getBatchSize() <= 0) {
            throw new IllegalStateException("feed.async.consumer.batch-size must be greater than 0");
        }

        // TODO: do rest of the validations
    }

    private void requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " must not be blank");
        }
    }

    private final StringRedisTemplate redisTemplate;
    private final ProcessedFeedEventRepository processedFeedEventRepository;
    private final DeadLetterFeedEventRepository deadLetterFeedEventRepository;
    private final FeedEventFailureRepository feedEventFailureRepository;
    private final ObjectMapper objectMapper;

    private final FeedService feedService;
    private final FeedMetricsService feedMetricsService;

    private final FeedAsyncProperties properties;

    @Scheduled(fixedDelayString = "${feed.async.consumer.fixed-delay-ms}")
    @Transactional
    public void consumePostEvents() {
        List<MapRecord<String, Object, Object>> records;
        try {
            records = redisTemplate.opsForStream().read(
                    Consumer.from(
                            properties.getConsumer().getGroup(),
                            properties.getConsumer().getName()
                    ),
                    StreamReadOptions.empty().count(properties.getConsumer().getBatchSize()),
                    StreamOffset.create(properties.getStream().getKey(),
                            ReadOffset.lastConsumed())
            );
        } catch (Exception exception) {
            feedMetricsService.recordServiceError(
                    "consume_post_event",
                    "REDIS_READ_ERROR"
            );
            return;
        }

        if (records == null || records.isEmpty()) {
            return;
        }

        for (MapRecord<String, Object, Object> record : records) {
            processRecord(record, "consume_post_event");
        }
    }

    @Scheduled(fixedDelayString = "${feed.async.consumer.recover-fixed-delay-ms}")
    @Transactional
    public void recoverPendingPostEvents() {

        List<MapRecord<String, Object, Object>> pendingRecords;
        try {
            pendingRecords = redisTemplate.opsForStream().read(
                    Consumer.from(
                            properties.getConsumer().getGroup(),
                            properties.getConsumer().getName()
                    ),
                    StreamReadOptions.empty().count(properties.getConsumer().getBatchSize()),
                    StreamOffset.create(properties.getStream().getKey(),
                            ReadOffset.from("0"))
            );
        } catch (Exception exception) {
            feedMetricsService.recordServiceError(
                    "recover_post_event",
                    "REDIS_READ_ERROR"
            );
            return;
        }
        if (pendingRecords == null || pendingRecords.isEmpty()) {
            return;
        }

        for (MapRecord<String, Object, Object> record : pendingRecords) {
            processRecord(record, "recover_post_event");
        }

    }

    @Scheduled(fixedDelayString = "${feed.async.consumer.reclaim-fixed-delay-ms}")
    @Transactional
    public void reclaimStalePendingPostEvents() {
        if (!properties.getConsumer().isReclaimEnabled()) {
            return;
        }
        List<MapRecord<String, Object, Object>> claimRecords = claimStalePendingRecords();
        for (MapRecord<String, Object, Object> record : claimRecords) {
            processRecord(record, "reclaim_post_event");
        }
    }

    private List<MapRecord<String, Object, Object>> claimStalePendingRecords() {
        try {
            String streamKey = properties.getStream().getKey();
            String consumerGroup = properties.getConsumer().getGroup();
            String consumerName = properties.getConsumer().getName();
            long reclaimIdleMs = properties.getConsumer().getReclaimIdleMs();
            PendingMessages pendingMessages = redisTemplate.opsForStream()
                    .pending(
                            streamKey,
                            consumerGroup,
                            Range.unbounded(),
                            properties.getConsumer().getReclaimBatchSize()
                    );

            if (pendingMessages.isEmpty()) {
                return List.of();
            }

            List<RecordId> staleRecordIds = pendingMessages.stream()
                    .filter(this::isClaimedPendingMessage)
                    .map(PendingMessage::getId)
                    .toList();

            if (staleRecordIds.isEmpty()) {
                return List.of();
            }

            return redisTemplate.opsForStream().claim(
                    streamKey,
                    consumerGroup,
                    consumerName,
                    Duration.ofMillis(reclaimIdleMs),
                    staleRecordIds.toArray(new RecordId[0])
            );
        } catch (Exception exception) {
            feedMetricsService.recordServiceError(
                    "reclaim_post_event",
                    "REDIS_CLAIM_ERROR"
            );
            return List.of();
        }
    }

    private boolean isClaimedPendingMessage(PendingMessage pendingMessage) {
        if (properties.getConsumer().getName().equals(pendingMessage.getConsumerName())) {
            return false;
        }

        return pendingMessage.getElapsedTimeSinceLastDelivery().toMillis() >=
                properties.getConsumer().getReclaimIdleMs();
    }

    private void processRecord(MapRecord<String, Object, Object> record, String operationName) {
        String streamKey = properties.getStream().getKey();
        String consumerGroup = properties.getConsumer().getGroup();

        Map<Object, Object> eventFields = record.getValue();

        String eventId = (String) eventFields.get("eventId");
        String postId = (String) eventFields.get("postId");

        if (eventId == null || postId == null) {
            redisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, record.getId());
            feedMetricsService.recordServiceError(operationName, "MALFORMED_EVENT");
            return;
        }
        try {
            try {
                processedFeedEventRepository.saveAndFlush(new ProcessedFeedEvent(eventId));
            } catch (DataIntegrityViolationException duplicateEvent) {
                redisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, record.getId());
                return;
            }

            feedService.processPostCreatedEvent(postId);
            clearFailureState(eventId);

            redisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, record.getId());
        } catch (Exception exception) {
            int attemptCount = recordFailureAttempt(eventId, exception);
            feedMetricsService.recordServiceError(operationName, "PROCESSING_ERROR");

            if (attemptCount < properties.getConsumer().getMaxProcessingAttempts()) {
                feedMetricsService.recordAsyncWorkerRetry(operationName);
                return;
            }

            moveToDeadLetter(record, eventFields, eventId, attemptCount, exception, operationName);
            redisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, record.getId());
            feedEventFailureRepository.deleteById(eventId);
            feedMetricsService.recordAsyncWorkerDeadLetter(operationName);
        }
    }

    private void clearFailureState(String eventId) {
        feedEventFailureRepository.deleteById(eventId);
    }

    private void moveToDeadLetter(MapRecord<String, Object, Object> record,
                                  Map<Object, Object> eventFields, String eventId,
                                  int attemptCount, Exception exception, String operationName) {
        String payloadJson = toJson(eventFields);
        String failureReason = sanitizeErrorMessage(exception);

        deadLetterFeedEventRepository.save(new DeadLetterFeedEvent(attemptCount,
                failureReason, payloadJson, record.getId().getValue(),
                eventId));

        try {
            redisTemplate.opsForStream().add(MapRecord.create(
                    properties.getConsumer().getDlqStreamKey(),
                    Map.of(
                            "eventId", eventId,
                            "sourceRecordId", record.getId().getValue(),
                            "failureReason", failureReason,
                            "attemptCount", String.valueOf(attemptCount),
                            "payloadJson", payloadJson
                    )
            ));
        } catch (Exception redisException) {
            feedMetricsService.recordServiceError(
                    operationName,
                    "DLQ_STREAM_PUBLISH_ERROR"
            );
        }
    }

    private String toJson(Map<Object, Object> eventFields) {
        try {
            return objectMapper.writeValueAsString(eventFields);
        } catch (Exception exception) {
            return "{\"serializationError\":true}";
        }
    }

    private int recordFailureAttempt(String eventId, Exception exception) {
        FeedEventFailure failure = feedEventFailureRepository.findById(eventId)
                .orElseGet(() -> new FeedEventFailure(eventId));

        int attemptCount = failure.recordFailure(sanitizeErrorMessage(exception));
        feedEventFailureRepository.saveAndFlush(failure);
        return attemptCount;
    }

    private String sanitizeErrorMessage(Exception exception) {
        String rawMessage = exception.getMessage() == null ? exception.getClass().getSimpleName()
                : exception.getMessage();
        return rawMessage.length() > 1000 ? rawMessage.substring(0, 1000) : rawMessage;
    }
}
