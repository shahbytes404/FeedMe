package com.shahbytes.feedme.controllers;

import com.shahbytes.feedme.dtos.UserProfileResponse;
import com.shahbytes.feedme.services.FeedmeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final FeedmeService feedmeService;

    public UserController(FeedmeService feedmeService) {
        this.feedmeService = feedmeService;
    }

    @GetMapping
    public List<UserProfileResponse> getUsers() {
        return feedmeService.getUsers();
    }
}
