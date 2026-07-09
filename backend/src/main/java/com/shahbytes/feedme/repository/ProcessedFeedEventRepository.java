package com.shahbytes.feedme.repository;

import com.shahbytes.feedme.models.ProcessedFeedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedFeedEventRepository extends JpaRepository<ProcessedFeedEvent, String> {
}
