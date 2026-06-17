package com.shahbytes.feedme.services.schedulers;

import com.shahbytes.feedme.models.DeadLetterFeedEvent;
import com.shahbytes.feedme.models.FeedEventFailure;
import com.shahbytes.feedme.models.ProcessedFeedEvent;
import com.shahbytes.feedme.repository.DeadLetterFeedEventRepository;
import com.shahbytes.feedme.repository.FeedEventFailureRepository;
import com.shahbytes.feedme.repository.ProcessedFeedEventRepository;
import com.shahbytes.feedme.services.FeedMetricsService;
import com.shahbytes.feedme.services.FeedmeService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Range;
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
public class FeedEventWorker {
    @PostConstruct
    public void initializeConsumerGroup() {
        try {
            StreamInfo.XInfoGroups groups = redisTemplate.opsForStream().groups(streamKey);

            boolean exists = groups.stream()
                    .anyMatch(group -> consumerGroup.equals(group.groupName()));

            if (!exists)
                redisTemplate.opsForStream()
                        .createGroup(streamKey, ReadOffset.latest(), consumerGroup);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private final StringRedisTemplate redisTemplate;
    private final ProcessedFeedEventRepository processedFeedEventRepository;
    private final DeadLetterFeedEventRepository deadLetterFeedEventRepository;
    private final FeedEventFailureRepository feedEventFailureRepository;
    private final ObjectMapper objectMapper;

    private final FeedmeService feedmeService;
    private final String streamKey;
    private final String consumerGroup;
    private final String consumerName;
    private final int batchSize;
    private final int maxProcessingAttempts;
    private final String deadLetterStreamKey;
    private final boolean reclaimEnabled;
    private final long reclaimIdleMs;
    private final int reclaimBatchSize;
    private final FeedMetricsService feedMetricsService;


    public FeedEventWorker(StringRedisTemplate redisTemplate,
                           ProcessedFeedEventRepository processedFeedEventRepository,
                           DeadLetterFeedEventRepository deadLetterFeedEventRepository,
                           FeedEventFailureRepository feedEventFailureRepository, ObjectMapper objectMapper,
                           FeedmeService feedmeService,
                           @Value("${feed.async.stream.key:feed:events}") String streamKey,
                           @Value("${feed.async.consumer.group}") String consumerGroup,
                           @Value("${feed.async.consumer.name}") String consumerName,
                           @Value("${feed.async.consumer.batch-size:100}") int batchSize,
                           @Value("${feed.async.consumer.max-processing-attempts}") int maxProcessingAttempts,
                           @Value("${feed.async.consumer.dlq-stream-key}") String deadLetterStreamKey,
                           @Value("${feed.async.consumer.reclaim-enabled}") boolean reclaimEnabled,
                           @Value("${feed.async.consumer.reclaim-idle-ms}") long reclaimIdleMs,
                           @Value("${feed.async.consumer.reclaim-batch-size}") int reclaimBatchSize, FeedMetricsService feedMetricsService) {
        this.redisTemplate = redisTemplate;
        this.processedFeedEventRepository = processedFeedEventRepository;
        this.deadLetterFeedEventRepository = deadLetterFeedEventRepository;
        this.feedEventFailureRepository = feedEventFailureRepository;
        this.objectMapper = objectMapper;
        this.feedmeService = feedmeService;
        this.streamKey = streamKey;
        this.consumerGroup = consumerGroup;
        this.consumerName = consumerName;
        this.batchSize = batchSize;
        this.maxProcessingAttempts = maxProcessingAttempts;
        this.deadLetterStreamKey = deadLetterStreamKey;
        this.reclaimEnabled = reclaimEnabled;
        this.reclaimIdleMs = reclaimIdleMs;
        this.reclaimBatchSize = reclaimBatchSize;
        this.feedMetricsService = feedMetricsService;
    }

    @Scheduled(fixedDelayString = "${feed.async.consumer.fixed-delay-ms:1000}")
    @Transactional
    public void consumePostEvents() {
        List<MapRecord<String, Object, Object>> records;
        try {
            records = redisTemplate.opsForStream().read(
                    Consumer.from(consumerGroup, consumerName),
                    StreamReadOptions.empty().count(batchSize),
                    StreamOffset.create(streamKey, ReadOffset.lastConsumed())
            );
        } catch (Exception exception) {
            feedMetricsService.recordServiceError("consume_post_event", "REDIS_READ_ERROR");
            return;
        }

        if (records == null || records.isEmpty()) {
            return;
        }

        for (MapRecord<String, Object, Object> record : records) {
            processRecord(record, "consume_post_event");
        }
    }

    @Scheduled(fixedDelayString = "${feed.async.consumer.recover-fixed-delay-ms:5000}")
    @Transactional
    public void recoverPendingPostEvents() {
        List<MapRecord<String, Object, Object>> pendingRecords;

        try {
            pendingRecords = redisTemplate.opsForStream().read(
                    Consumer.from(consumerGroup, consumerName),
                    StreamReadOptions.empty().count(batchSize),
                    StreamOffset.create(streamKey, ReadOffset.from("0"))
            );
        } catch (Exception exception) {
            feedMetricsService.recordServiceError("recover_post_event", "REDIS_READ_ERROR");
            return;
        }

        if (pendingRecords == null || pendingRecords.isEmpty()) {
            return;
        }

        for (MapRecord<String, Object, Object> record : pendingRecords) {
            processRecord(record, "recover_post_event");
        }
    }

    @Scheduled(fixedDelayString = "${feed.async.consumer.reclaims-fixed-delay-ms:5000}")
    @Transactional
    public void reclaimStalePendingPostEvents() {
        if (!reclaimEnabled) {
            return;
        }


        List<MapRecord<String, Object, Object>> claimedRecords = claimStalePendingRecords();

        for (MapRecord<String, Object, Object> record : claimedRecords) {
            processRecord(record, "reclaim_post_event");
        }
    }

    private List<MapRecord<String, Object, Object>> claimStalePendingRecords() {
        try {
            // XPENDING
            PendingMessages pendingMessages = redisTemplate.opsForStream().
                    pending(streamKey,
                            consumerGroup,
                            Range.unbounded(),
                            reclaimBatchSize);
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
        } catch (Exception e) {
            feedMetricsService.recordServiceError("reclaim_post_event", "REDIS_CLAIM_ERROR");
            return List.of();
        }
    }

    private boolean isClaimedPendingMessage(PendingMessage pendingMessage) {
        if (consumerName.equals(pendingMessage.getConsumerName())) {
            return false;
        }

        return pendingMessage.getElapsedTimeSinceLastDelivery().toMillis() >= reclaimIdleMs;
    }

    private void processRecord(MapRecord<String, Object, Object> record, String operationName) {
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

            feedmeService.processPostCreatedEvent(postId);

            clearFailureState(eventId);

            redisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, record.getId());
        } catch (Exception exception) {
            int attemptCount = recordFailureAttempt(eventId, exception);
            feedMetricsService.recordServiceError(operationName, "PROCESSING_ERROR");

            if (attemptCount < maxProcessingAttempts) {
                feedMetricsService.recordAsyncWorkerRetry(operationName);
                return;
            }

            moveToDeadLetter(record, eventFields, eventId, attemptCount, exception, operationName);
            redisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, record.getId());
            feedEventFailureRepository.deleteById(eventId);
            feedMetricsService.recordAsyncWorkerDeadLetter(operationName);
        }
    }

    private void moveToDeadLetter(MapRecord<String, Object, Object> record,
                                  Map<Object, Object> eventFields, String eventId, int attemptCount,
                                  Exception exception, String operationName) {
        String payloadJson = toJson(eventFields);
        String failureReason = sanitizeErrorMessage(exception);

        deadLetterFeedEventRepository.save(
                new DeadLetterFeedEvent(attemptCount, failureReason, payloadJson,
                        record.getId().getValue(), eventId)
        );

        try {
            redisTemplate.opsForStream().add(MapRecord.create(deadLetterStreamKey,
                    Map.of(
                            "eventId", eventId,
                            "sourceRecordId", record.getId().getValue(),
                            "failureReson", failureReason,
                            "attemptCount", String.valueOf(attemptCount),
                            "payloadJson", payloadJson
                    )));
        } catch (Exception redisException) {
            feedMetricsService.recordServiceError(operationName, "DLQ_STREAM_PLUBLISH_ERROR");
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

    private void clearFailureState(String eventId) {
        feedEventFailureRepository.deleteById(eventId);
    }

}
