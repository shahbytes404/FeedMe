package com.shahbytes.feedme.services;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class FeedMetricsService {

    private final MeterRegistry meterRegistry;

    public FeedMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public long startTimer() {
        return System.nanoTime();
    }

    // latency, requestCount, returnitemCount, paginationcan continue

    public void recordHomeFeedRequest(long startedAtNanos, String cacheOutcome, String mergeMode,
                                      boolean hasNextCursor, int itemsReturned) {
        Timer.builder("feedme.feed.home.latency")
                .description("Home feed request latency")
                .tag("cache_outcome", cacheOutcome)
                .tag("merge_mode", mergeMode)
                .register(meterRegistry)
                .record(System.nanoTime() - startedAtNanos, TimeUnit.NANOSECONDS);

        Counter.builder("feedme.feed.home.requests")
                .description("Home feed requests")
                .tag("cache_outcome", cacheOutcome)
                .tag("merge_mode", mergeMode)
                .register(meterRegistry)
                .increment();

        DistributionSummary.builder("feedme.feed.home.items_returned")
                .description("Number of items returned in home feed pages")
                .baseUnit("items")
                .register(meterRegistry)
                .record(itemsReturned);

        if (hasNextCursor) {
            meterRegistry.counter("feedme.feed.home.next_cursor").increment();
        }
    }

    public void recordHomeFeedRequestedPageSize(int requestedLimit, int normalizedPageSize) {
        DistributionSummary.builder("feedme.feed.home.requested_page_size")
                .description("Requested and normalized home feed pages sizes")
                .baseUnit("items")
                .tag("requested_limit", String.valueOf(requestedLimit))
                .register(meterRegistry)
                .record(normalizedPageSize);
    }

    public void recordUserFeedRequest(long startedAtNanos, boolean hasNextCursor, int itemsReturned) {
        meterRegistry.timer("feedme.feed.user.latency")
                .record(System.nanoTime() - startedAtNanos, TimeUnit.NANOSECONDS);
        meterRegistry.counter("feedme.feed.user.requests").increment();

        meterRegistry.summary("feedme.feed.user.items_returned").record(itemsReturned);

        if (hasNextCursor) {
            meterRegistry.counter("feedme.feed.user.next_cursor").increment();
        }
    }

    public void recordUserFeedRequestedPageSize(int requestedLimit, int normalizedPageSize) {
        DistributionSummary.builder("feedme.feed.user.requested_page_size")
                .description("Requested and normalized user feed pages sizes")
                .baseUnit("items")
                .tag("requested_limit", String.valueOf(requestedLimit))
                .register(meterRegistry)
                .record(normalizedPageSize);
    }

    public void recordHomeFeedCacheLookup(String outcome) {
        meterRegistry.counter("feedme.feed.home.cache.lookups", "outcome", outcome).increment();
    }

    public void recordHomeFeedMerge(String mode, int baseItemsUsed, int hotItemsUsed) {
        meterRegistry.counter("feedme.feed.home.merge", "mode", mode).increment();

        meterRegistry.summary("feedme.feed.home.merge.base_items").record(baseItemsUsed);

        meterRegistry.summary("feedme.feed.home.merge.hot_items").record(hotItemsUsed);
    }

    public void recordPostCreation(long startedAtNanos, String authorType, String idempotencyOutcome) {
        Timer.builder("feedme.posts.create.latency")
                .description("Post creation latency")
                .tag("author_type", authorType)
                .tag("idempotency_outcome", idempotencyOutcome)
                .register(meterRegistry)
                .record(System.nanoTime() - startedAtNanos, TimeUnit.NANOSECONDS);

        Counter.builder("feedme.posts.create.requests")
                .description("Post creation requests")
                .tag("author_type", authorType)
                .tag("idempotency_outcome", idempotencyOutcome)
                .register(meterRegistry)
                .increment();
    }

    public void recordFollowRequest(long startedAtNanos, String action, boolean createdRelation) {
        Timer.builder("feedme.follows.latency")
                .description("Follow and unfollow request latency")
                .tag("action", action)
                .tag("changed_state", String.valueOf(createdRelation))
                .register(meterRegistry)
                .record(System.nanoTime() - startedAtNanos, TimeUnit.NANOSECONDS);

        Counter.builder("feedme.follows.requests")
                .description("Follow and unfollow requests")
                .tag("action", action)
                .tag("changed_state", String.valueOf(createdRelation))
                .register(meterRegistry)
                .increment();
    }


    public void recordServiceError(String operation, String statusCode) {
        Counter.builder("feedme.service.errors")
                .description("Service-level errors by operation and status code")
                .tag("operation", operation)
                .tag("status", statusCode)
                .register(meterRegistry)
                .increment();
    }

    public void recordDeliveryPath(String deliveryPath) {
        meterRegistry.counter("feedme.feed.delivery.path", "path", deliveryPath).increment();
    }

    public void recordCacheMutation(String action) {
        meterRegistry.counter("feedme.feed.cache.mutations", "action", action).increment();
    }

    public void recordAsyncWorkerRetry(String operation) {
        meterRegistry.counter("feedme.feed.async.worker.retries", "operation", operation).increment();
    }

    public void recordAsyncWorkerDeadLetter(String operation) {
        meterRegistry.counter("feedme.feed.async.worker.dead_letter", "operation", operation).increment();
    }
}
