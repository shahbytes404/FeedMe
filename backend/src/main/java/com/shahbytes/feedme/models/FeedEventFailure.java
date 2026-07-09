package com.shahbytes.feedme.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "feed_event_failures")
@Getter
@NoArgsConstructor
public class FeedEventFailure {
    @Id
    private String eventId;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false, length = 1024)
    private String lastError;

    @Column(nullable = false, updatable = false)
    private Instant firstFailedAt;

    @Column(nullable = false)
    private Instant lastFailedAt;

    public FeedEventFailure(String eventId) {
        this.eventId = eventId;
        this.attemptCount = 0;
        this.lastError = "UNSET";
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.firstFailedAt = now;
        this.lastFailedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.lastFailedAt = Instant.now();
    }

    public int recordFailure(String error) {
        this.attemptCount++;
        this.lastError = error;
        this.lastFailedAt = Instant.now();
        return this.attemptCount;
    }
}
