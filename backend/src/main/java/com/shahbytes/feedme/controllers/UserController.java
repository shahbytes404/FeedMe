package com.shahbytes.feedme.controllers;

import com.shahbytes.feedme.dtos.UserProfileResponse;
import com.shahbytes.feedme.services.FeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final FeedService feedService;

    @GetMapping
    public List<UserProfileResponse> getUsers() {
        return feedService.getUsers();
    }
}
