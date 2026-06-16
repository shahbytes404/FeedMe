package com.shahbytes.feedme.models;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dead_letter_feed_events")
public class DeadLetterFeedEvent {
    @Id
    private String id;

    @Column(nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String streamRecordId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(nullable = false, length = 1024)
    private String failureReason;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false, updatable = false)
    private Instant movedToDlqAt;

    public DeadLetterFeedEvent() {
    }

    public DeadLetterFeedEvent(int attempts, String failureReason, String payloadJson, String streamRecordId, String eventId) {
        this.id = UUID.randomUUID().toString();
        this.attempts = attempts;
        this.failureReason = failureReason;
        this.payloadJson = payloadJson;
        this.streamRecordId = streamRecordId;
        this.eventId = eventId;
    }

    @PrePersist
    void onCreate() {
        this.movedToDlqAt = Instant.now();
    }

    public String getEventId() {
        return eventId;
    }

    public String getStreamRecordId() {
        return streamRecordId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getMovedToDlqAt() {
        return movedToDlqAt;
    }
}
