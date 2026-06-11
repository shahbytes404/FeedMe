package com.shahbytes.feedme.dtos;

import java.time.Instant;

public record PostResponse(String id, String authorId, String authorHandle, String authorName, String content,
                           Instant createdAt) {
}
