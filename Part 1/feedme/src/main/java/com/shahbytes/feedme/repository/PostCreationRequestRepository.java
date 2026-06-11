package com.shahbytes.feedme.repository;

import com.shahbytes.feedme.models.PostCreationRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PostCreationRequestRepository extends JpaRepository<PostCreationRequest, Long> {
    Optional<PostCreationRequest> findByUserIdAndIdempotencyKey(String authorId, String idempotencyKey);
}

