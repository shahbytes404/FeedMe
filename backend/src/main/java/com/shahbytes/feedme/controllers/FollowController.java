package com.shahbytes.feedme.controllers;

import com.shahbytes.feedme.dtos.FollowResponse;
import com.shahbytes.feedme.dtos.FollowingResponse;
import com.shahbytes.feedme.services.FeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
public class FollowController {
    private final FeedService feedService;

    @GetMapping
    public FollowingResponse getFollowing(@RequestParam String followerId) {
        return feedService.getFollowing(followerId);
    }

    @PostMapping("/{userId}")
    public FollowResponse follow(@RequestParam String followerId, @PathVariable String userId) {
        return feedService.follow(followerId, userId);
    }

    @DeleteMapping("/{userId}")
    public FollowResponse unfollow(@RequestParam String followerId, @PathVariable String userId) {
        return feedService.unfollow(followerId, userId);
    }
}
