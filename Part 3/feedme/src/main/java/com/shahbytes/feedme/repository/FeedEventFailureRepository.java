package com.shahbytes.feedme.repository;

import com.shahbytes.feedme.models.FeedEventFailure;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedEventFailureRepository extends JpaRepository<FeedEventFailure, String> {
}
