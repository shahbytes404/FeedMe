package com.shahbytes.feedme.services;

import com.shahbytes.feedme.models.OutboxEvent;
import com.shahbytes.feedme.models.Post;
import com.shahbytes.feedme.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedEventOutboxService {

    private final OutboxEventRepository outboxEventRepository;

    public FeedEventOutboxService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public void enqueuePostCreated(Post post) {
        outboxEventRepository.save(new OutboxEvent(post));
    }
}
