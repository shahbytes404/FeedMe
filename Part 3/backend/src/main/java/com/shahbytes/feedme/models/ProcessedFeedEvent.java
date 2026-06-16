package com.shahbytes.feedme.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "processed_feed_events")
public class ProcessedFeedEvent {
    @Id
    private String eventId;

    @Column(nullable = false, updatable = false)
    private Instant processedAt;

    public ProcessedFeedEvent() {
    }

    public ProcessedFeedEvent(String eventId) {
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }

    public String getEventId() {
        return eventId;
    }
}
