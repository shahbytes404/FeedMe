package com.shahbytes.feedme.controllers;

import com.shahbytes.feedme.dtos.CreatePostRequest;
import com.shahbytes.feedme.dtos.PostResponse;
import com.shahbytes.feedme.services.FeedmeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
public class PostController {
    private final FeedmeService feedmeService;

    public PostController(FeedmeService feedmeService) {
        this.feedmeService = feedmeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse createPost(@Valid @RequestBody CreatePostRequest request) {
        return feedmeService.createPost(request.authorId(), request.content(), request.idempotencyKey());
    }

    @GetMapping("/{postId}")
    public PostResponse getPost(@PathVariable String postId) {
        return feedmeService.getPost(postId);
    }
}
