package com.shahbytes.feedme.services;

import com.shahbytes.feedme.dtos.TimelinePageResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

@Service
public class FeedCacheService {

    public static final int DEFAULT_PAGE_SIZE = 5;

    private static final Duration HOME_FEED_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    public FeedCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<TimelinePageResponse> getHomeFeed(String userId) {
        try {
            String payload = redisTemplate.opsForValue().get(homeFeedKey(userId));

            if (payload == null) {
                return Optional.empty();
            }

            return Optional.of(objectMapper.readValue(payload, TimelinePageResponse.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String homeFeedKey(String userId) {
        return "feed:home:" + userId;
    }

    public void cacheHomeFeed(TimelinePageResponse tpresponse) {
        writeHomeFeed(tpresponse);
    }

    private void writeHomeFeed(TimelinePageResponse response) {
        try {
            String payload = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(homeFeedKey(response.timelineOwnerId()), payload, HOME_FEED_TTL);
        } catch (Exception ignored) {

        }
    }
}
