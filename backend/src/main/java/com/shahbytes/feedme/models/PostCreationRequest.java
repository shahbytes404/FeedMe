package com.shahbytes.feedme.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "post_creation_requests",
        uniqueConstraints = @UniqueConstraint(name = "uk_post_creation_user_key",
                columnNames = {"user_id", "idempotency_key"})
)
@Getter
@Setter
@NoArgsConstructor
public class PostCreationRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostCreationStatus status;

    @Column(name = "post_id")
    private String postId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PostCreationRequest(String userId, String idempotencyKey, String requestHash) {
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = PostCreationStatus.IN_PROGRESS;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    public void markSucceeded(String postId) {
        this.postId = postId;
        this.status = PostCreationStatus.SUCCEEDED;
        this.updatedAt = Instant.now();
    }
}
