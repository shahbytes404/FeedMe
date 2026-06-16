package com.shahbytes.feedme.controllers;

import com.shahbytes.feedme.dtos.TimelinePageResponse;
import com.shahbytes.feedme.services.FeedmeService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feed")
public class FeedController {
    private final FeedmeService feedmeService;

    public FeedController(FeedmeService feedmeService) {
        this.feedmeService = feedmeService;
    }

    @GetMapping("/home")
    public TimelinePageResponse getHomeFeed(@RequestParam String userId, @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "5") int limit) {
        return feedmeService.getHomeFeed(userId, cursor, limit);
    }

    @GetMapping("/user/{userId}")
    public TimelinePageResponse getUserFeed(
            @PathVariable String userId, @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "5") int limit
    ) {
        return feedmeService.getUserFeed(userId, cursor, limit);
    }
}
