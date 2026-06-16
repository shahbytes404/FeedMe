package com.shahbytes.feedme.services.schedulers;

import com.shahbytes.feedme.models.OutboxEvent;
import com.shahbytes.feedme.models.OutboxEventStatus;
import com.shahbytes.feedme.repository.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(value = "feed.async.enabled", havingValue = "true", matchIfMissing = true)
public class FeedEventOutboxPublisher {

    private final StringRedisTemplate redisTemplate;
    private final OutboxEventRepository outboxEventRepository;
    private final String streamKey;
    private final int publishBatchSize;
    private final int maxAttempts;

    public FeedEventOutboxPublisher(StringRedisTemplate redisTemplate,
                                    OutboxEventRepository outboxEventRepository,
                                    @Value("${feed.async.stream.key:feed:events}") String streamKey,
                                    @Value("${feed.async.publisher.batch-size:100}") int publishBatchSize,
                                    @Value("${feed.async.publisher.max-attempts:10}") int maxAttempts) {
        this.redisTemplate = redisTemplate;
        this.outboxEventRepository = outboxEventRepository;
        this.streamKey = streamKey;
        this.publishBatchSize = publishBatchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${feed.async.publisher.fixed-delay-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository
                .findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        OutboxEventStatus.PENDING,
                        Instant.now(),
                        PageRequest.of(0, publishBatchSize)
                );

        // publish to stream
        for (OutboxEvent event : pendingEvents) {
            try {
                Map<String, String> fields = new HashMap<>();
                fields.put("eventId", event.getId());
                fields.put("eventType", event.getEventType().name());
                fields.put("postId", event.getPostId());
                fields.put("authorId", event.getAuthorId());
                fields.put("hotUser", String.valueOf(event.isHotUser()));
                fields.put("occurredAtEpochMillis", String.valueOf(event.getOccurredAt().toEpochMilli()));

                redisTemplate.opsForStream().add(MapRecord.create(streamKey, fields));
                event.markPublished();
            } catch (Exception exception) {
                long delaySeconds = getDelaySeconds(event);
                event.scheduleRetry(Instant.now().plusSeconds(delaySeconds), maxAttempts);
            }
        }
    }

    private static long getDelaySeconds(OutboxEvent event) {
        int nextAttempt = event.getAttemptCount() + 1;

        // 1L << n , 2^n for positive n
        return Math.min(300, 1L << Math.min(10, nextAttempt));
        /*
        attempt 1 ->2s
        attempot 2 -> 4s
        ......

         */
    }
}
