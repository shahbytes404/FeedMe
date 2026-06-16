package com.shahbytes.feedme.controllers;

import com.shahbytes.feedme.dtos.FollowResponse;
import com.shahbytes.feedme.services.FeedmeService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/follows")
public class FollowController {

    private final FeedmeService feedmeService;

    public FollowController(FeedmeService feedmeService) {
        this.feedmeService = feedmeService;
    }

    @PostMapping("/{userId}")
    public FollowResponse follow(@RequestParam String followerId, @PathVariable String userId) {
        return feedmeService.follow(followerId, userId);
    }

    @DeleteMapping("/{userId}")
    public FollowResponse unfollow(@RequestParam String followerId, @PathVariable String userId) {
        return feedmeService.unfollow(followerId, userId);
    }
}
