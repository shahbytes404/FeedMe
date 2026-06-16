package com.shahbytes.feedme.dtos;

import java.time.Instant;

public record FeedItemResponse
        (String postId, String authorId, String authorHandle, String authorName,
         String content,
         Instant createdAt, double rankingScore, String deliverStrategy, String rankinReason) {
}

