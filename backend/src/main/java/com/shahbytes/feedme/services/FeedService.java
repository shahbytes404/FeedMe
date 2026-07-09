package com.shahbytes.feedme.services;

import com.shahbytes.feedme.dtos.*;
import com.shahbytes.feedme.models.*;
import com.shahbytes.feedme.repository.FollowRelationRepository;
import com.shahbytes.feedme.repository.PostCreationRequestRepository;
import com.shahbytes.feedme.repository.PostRepository;
import com.shahbytes.feedme.repository.UserProfileRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FeedService {
    private static final Comparator<FeedItemResponse> FEED_ORDER
            = Comparator.comparing(FeedItemResponse::createdAt)
            .thenComparing(FeedItemResponse::postId)
            .reversed();

    private final FeedMetricsService feedMetricsService;
    private final UserProfileRepository userProfileRepository;
    private final FollowRelationRepository followRelationRepository;
    private final FeedCacheService feedCacheService;
    private final PostRepository postRepository;
    private final PostCreationRequestRepository postCreationRequestRepository;
    private final FeedEventOutboxService feedEventOutboxService;

    public TimelinePageResponse getHomeFeed(String userId, String cursor, int limit) {
        long startedAtNanos = feedMetricsService.startTimer();
        int pageSize = normalizeLimit(limit);
        feedMetricsService.recordHomeFeedRequestedPageSize(limit, pageSize);

        try {
            UserProfile viewer = getUser(userId);
            FeedCursorCodec.FeedCursor pageCursor = FeedCursorCodec.parse(cursor);
            VisibleAuthors visibleAuthors = getVisibleAuthors(viewer);

            NormalFeedSliceResult normalFeedSliceResult =
                    getNormalHomeFeedSlice(viewer.getId(), visibleAuthors.nonHotAuthorIds(),
                            pageCursor, pageSize);

            FeedSlice hotSlice = getHotHomeFeedSlice(viewer.getId(), visibleAuthors.hotAuthorIds(),
                    pageCursor, pageSize);

            int totalItems = Math.toIntExact(postRepository.countByAuthor_IdIn(visibleAuthors.allAuthorIds));

            TimelinePageResponse response = mergeHomeFeedSlices(userId, totalItems, pageSize,
                    normalFeedSliceResult.slice, hotSlice);

            feedMetricsService.recordHomeFeedRequest(startedAtNanos, normalFeedSliceResult.cacheOutcome,
                    determineMergeMode(normalFeedSliceResult.slice, hotSlice),
                    response.nextCursor() != null,
                    response.items().size()
            );
            return response;

        } catch (ResponseStatusException exception) {
            feedMetricsService.recordServiceError("get_home_feed", exception.getStatusCode().toString());
            throw exception;
        }
    }

    private TimelinePageResponse mergeHomeFeedSlices(String userId, int totalItems, int pageSize,
                                                     FeedSlice normalSlice,
                                                     FeedSlice hotSlice) {
        List<FeedItemResponse> mergedItems = new ArrayList<>(pageSize + 1);

        int normalIndex = 0;
        int hotIndex = 0;
        int normalItemsUsed = 0;
        int hotItemsUsed = 0;

        while (mergedItems.size() < pageSize + 1
                && (normalIndex < normalSlice.items().size() ||
                hotIndex < hotSlice.items().size())
        ) {

            FeedItemResponse nextNormal = normalIndex < normalSlice.items().size()
                    ? normalSlice.items().get(normalIndex) : null;

            FeedItemResponse nextHot = hotIndex < hotSlice.items().size()
                    ? hotSlice.items().get(hotIndex) : null;

            /*
            compare(a, b)
            returns:
            - -ve => a should come before b
            -   0 => they are equal in sort order
            - +ve => a should come after b

             */
            if (nextHot == null || (nextNormal != null && FEED_ORDER.compare(nextNormal, nextHot) <= 0)) {
                mergedItems.add(nextNormal);
                normalIndex++;
                normalItemsUsed++;
            } else {
                mergedItems.add(nextHot);
                hotIndex++;
                hotItemsUsed++;
            }
        }

        boolean hasMore = mergedItems.size() > pageSize
                || normalIndex < normalSlice.items().size()
                || hotIndex < hotSlice.items().size()
                || normalSlice.hasMore()
                || hotSlice.hasMore();

        List<FeedItemResponse> pageItems = mergedItems.size() > pageSize ? mergedItems.subList(0, pageSize)
                : mergedItems;

        String nextCursor = hasMore && !pageItems.isEmpty()
                ? FeedCursorCodec.encode(pageItems.get(pageItems.size() - 1))
                : null;

        feedMetricsService.recordHomeFeedMerge(determineMergeMode(normalSlice, hotSlice),
                normalItemsUsed, hotItemsUsed);

        return new TimelinePageResponse(
                userId,
                TimelineMode.HOME,
                totalItems,
                pageItems,
                nextCursor
        );
    }

    private String determineMergeMode(FeedSlice normalSlice, FeedSlice hotSlice) {
        boolean hasNormal = !normalSlice.items().isEmpty();
        boolean hasHot = !hotSlice.items.isEmpty();

        if (hasNormal && hasHot) {
            return "mixed";
        }

        if (hasNormal) {
            return "normal_only";
        }

        if (hasHot) {
            return "hot_only";
        }

        return "empty";
    }

    private FeedSlice getHotHomeFeedSlice(String userId, Set<String> hotAuthorIds,
                                          FeedCursorCodec.FeedCursor pageCursor, int pageSize) {
        if (hotAuthorIds.isEmpty()) {
            return new FeedSlice(List.of(), false);
        }

        List<Post> posts = fetchHomeFeedPosts(hotAuthorIds, pageCursor, pageSize + 1);

        return buildFeedSlice(posts, pageSize, userId, hotAuthorIds);
    }

    private NormalFeedSliceResult getNormalHomeFeedSlice(String userId, Set<String> nonHotAuthorIds,
                                                         FeedCursorCodec.FeedCursor pageCursor, int pageSize) {
        if (nonHotAuthorIds.isEmpty()) {
            FeedSlice slice = new FeedSlice(List.of(), false);
            return new NormalFeedSliceResult(slice, "empty_non_hot");
        }

        Optional<FeedSlice> cachedSlice = getCachedNormalHomeFeedSlice(userId, pageCursor, pageSize);

        if (cachedSlice.isPresent()) {
            return new NormalFeedSliceResult(cachedSlice.get(), "hit");
        }

        List<Post> posts = fetchHomeFeedPosts(nonHotAuthorIds, pageCursor, pageSize + 1);

        FeedSlice slice = buildFeedSlice(posts, pageSize, userId, nonHotAuthorIds);

        if (pageCursor == null && pageSize == FeedCacheService.DEFAULT_PAGE_SIZE) {
            TimelinePageResponse tpResponse = new TimelinePageResponse(
                    userId,
                    TimelineMode.HOME,
                    Math.toIntExact(postRepository.countByAuthor_IdIn(nonHotAuthorIds)),
                    slice.items,
                    slice.hasMore() && !slice.items.isEmpty()
                            ? FeedCursorCodec.encode(
                            slice.items().get(slice.items().size() - 1)
                    )
                            : null
            );

            feedCacheService.cacheHomeFeed(tpResponse);
        }

        return new NormalFeedSliceResult(slice, "miss");
    }

    private FeedSlice buildFeedSlice(List<Post> posts, int pageSize, String userId,
                                     Set<String> visibleAuthorIds) {
        boolean hasMore = posts.size() > pageSize;
        List<Post> pagePosts = hasMore ? posts.subList(0, pageSize) : posts;

        List<FeedItemResponse> pageItems = pagePosts.stream().
                map(post -> toFeedItem(post, userId, visibleAuthorIds))
                .toList();
        return new FeedSlice(pageItems, hasMore);
    }

    private FeedItemResponse toFeedItem(Post post, String viewerId, Set<String> visibleAuthorIds) {
        /*
           - post age = 120 minutes

           - recency divisor = 600 minutes = 10 hours
           at 0 minutes old , recency Score 1.0
           at 300 minutes, rs => 0.5
           at 600 minutes, rs => 0.0

           - hot users penalty = 0.15
           - followed-author boost = 0.2
           - rounding multiplier = 100

           Example:
           normal user

           120 minutes
           120/600 = 0.2
           rs = 1.0 - 0.2 = 0.8;

           hot-user penalty = 0.0

           0.2 => affinity boost

           raw score => 0.8 + 0.2 - 0.0 = 1.0
         */

        UserProfile author = post.getAuthor();

        double freshnessWindow = 600.0;
        double recencyScore = 1.0 - Duration.between(
                post.getCreatedAt(), Instant.now()).toMinutes() / freshnessWindow;

        double hotUserPenalty = author.isHotUser() ? 0.15 : 0.0;

        double affinityBoost = visibleAuthorIds.contains(author.getId())
                && viewerId.equals(author.getId()) ? 0.0 : 0.2;

        double rankingScore = Math.round((recencyScore + affinityBoost - hotUserPenalty) * 100.0) / 100.0;
        /*
        raw score = 0.856 * 100 - 85.6 -> round -> 86 -> 0.86
         */
        String deliveryStrategy = author.isHotUser()
                ? "hybrid-pull" : "fan-out-on-write";

        String rankingReason = author.isHotUser()
                ? "Hot-user content is blended with a pull-based bias to reduce write amplifications"
                : "Recent Content from followed accounts is promoted for freshness and affinity";

        return new FeedItemResponse(
                post.getId(),
                author.getId(),
                author.getHandle(),
                author.getName(),
                post.getContent(),
                post.getCreatedAt(),
                rankingScore,
                deliveryStrategy,
                rankingReason
        );
    }

    private List<Post> fetchHomeFeedPosts(Set<String> authorIds, FeedCursorCodec.FeedCursor pageCursor,
                                          int fetchSize) {
        PageRequest pageRequest = PageRequest.of(0, fetchSize);
        if (pageCursor == null) {
            return postRepository.findByAuthor_IdInOrderByCreatedAtDescIdDesc(authorIds, pageRequest);
        }

        return postRepository.findHomeFeedPageAfterCursor
                (authorIds, pageCursor.createdAt(), pageCursor.postId(), pageRequest);
    }

    private Optional<FeedSlice> getCachedNormalHomeFeedSlice(String userId,
                                                             FeedCursorCodec.FeedCursor cursor, int pageSize) {
        if (cursor != null) {
            feedMetricsService.recordHomeFeedCacheLookup("bypass_cursor");
            return Optional.empty();
        }

        if (pageSize > FeedCacheService.DEFAULT_PAGE_SIZE) {
            feedMetricsService.recordHomeFeedCacheLookup("bypass_page_size");
            return Optional.empty();
        }

        Optional<TimelinePageResponse> cachedPage = feedCacheService.getHomeFeed(userId);

        if (cachedPage.isEmpty()) {
            feedMetricsService.recordHomeFeedCacheLookup("miss");
            return Optional.empty();
        }

        Optional<FeedSlice> adapted = adaptCachedFirstPage(cachedPage.get(), pageSize);
        feedMetricsService.recordHomeFeedCacheLookup(adapted.isPresent() ? "hit" : "incomplete");
        return adapted;
    }

    private Optional<FeedSlice> adaptCachedFirstPage(TimelinePageResponse cachedPage, int pageSize) {
        if (cachedPage.items().size() < pageSize && cachedPage.nextCursor() != null) {
            return Optional.empty();
        }

        List<FeedItemResponse> pageItems = cachedPage.items().stream().limit(pageSize).toList();

        FeedSlice slice = new FeedSlice(pageItems, cachedPage.totalItems() > pageItems.size());
        return Optional.of(slice);
    }

    private VisibleAuthors getVisibleAuthors(UserProfile viewer) {
        List<FollowRelation> followRelations = followRelationRepository.findByFollower_Id(viewer.getId());

        Set<String> allAuthorIds = new LinkedHashSet<>();
        Set<String> hotAuthorIds = new LinkedHashSet<>();
        Set<String> nonHotAuthorIds = new LinkedHashSet<>();

        allAuthorIds.add(viewer.getId());

        if (viewer.isHotUser()) {
            hotAuthorIds.add(viewer.getId());
        } else {
            nonHotAuthorIds.add(viewer.getId());
        }

        for (FollowRelation relation : followRelations) {
            UserProfile author = relation.getTargetUser();
            allAuthorIds.add(author.getId());
            if (author.isHotUser()) {
                hotAuthorIds.add(author.getId());
            } else {
                nonHotAuthorIds.add(author.getId());
            }
        }

        return new VisibleAuthors(allAuthorIds, hotAuthorIds, nonHotAuthorIds);
    }

    private UserProfile getUser(String userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return FeedCacheService.DEFAULT_PAGE_SIZE;
        }

        return Math.min(limit, 20);
    }

    public TimelinePageResponse getUserFeed(String userId, String cursor, int limit) {
        long startedAtNanos = feedMetricsService.startTimer();
        int pageSize = normalizeLimit(limit);

        feedMetricsService.recordUserFeedRequestedPageSize(limit, pageSize);

        try {
            getUser(userId);

            FeedCursorCodec.FeedCursor pageCursor = FeedCursorCodec.parse(cursor);
            List<Post> posts = fetchUserFeedPosts(userId, pageCursor, pageSize + 1);
            int totalItems = Math.toIntExact(postRepository.countByAuthor_Id(userId));
            TimelinePageResponse response = buildTimelinePage(
                    userId, TimelineMode.USER, posts, totalItems, pageSize
            );

            feedMetricsService.recordUserFeedRequest(startedAtNanos,
                    response.nextCursor() != null, response.items().size());
            return response;

        } catch (ResponseStatusException e) {
            feedMetricsService.recordServiceError(
                    "get_user_feed",
                    e.getStatusCode().toString()
            );
            throw e;
        }
    }

    private TimelinePageResponse buildTimelinePage(String timelineOwnerId, TimelineMode mode,
                                                   List<Post> posts, int totalItems, int pageSize) {
        boolean hasMore = posts.size() > pageSize;
        List<Post> pagePosts = hasMore ? posts.subList(0, pageSize) : posts;

        List<FeedItemResponse> pageItems = pagePosts.stream()
                .map(post -> toFeedItem(post, timelineOwnerId, Set.of(timelineOwnerId)))
                .toList();

        String nextCursor = hasMore && !pageItems.isEmpty()
                ? FeedCursorCodec.encode(pageItems.get(pageItems.size() - 1))
                : null;

        return new TimelinePageResponse(timelineOwnerId, mode, totalItems, pageItems, nextCursor);
    }

    private List<Post> fetchUserFeedPosts(String userId, FeedCursorCodec.FeedCursor pageCursor, int fetchSize) {
        PageRequest pageRequest = PageRequest.of(0, fetchSize);
        if (pageCursor == null) {
            return postRepository.findByAuthor_IdOrderByCreatedAtDescIdDesc(userId, pageRequest);
        }

        return postRepository.findUserFeedPageAfterCursor(userId, pageCursor.createdAt(), pageCursor.postId(),
                pageRequest);

    }

    public List<UserProfileResponse> getUsers() {
        return userProfileRepository.findAll(Sort.by("id")).stream()
                .map(this::toUserProfileResponse)
                .toList();
    }

    private UserProfileResponse toUserProfileResponse(UserProfile user) {
        return new UserProfileResponse(
                user.getId(),
                user.getHandle(),
                user.getName(),
                user.getBio(),
                user.isHotUser()
        );
    }

    public FollowingResponse getFollowing(String followerId) {
        getUser(followerId);
        List<String> targetUserIds = followRelationRepository.findByFollower_Id(followerId).stream()
                .map(relation -> relation.getTargetUser().getId())
                .toList();
        return new FollowingResponse(followerId, targetUserIds, targetUserIds.size());
    }

    @Transactional
    public FollowResponse follow(String followerId, String targetUserId) {
        long startedAtNanos = feedMetricsService.startTimer();
        try {
            validateFollowRequest(followerId, targetUserId);

            UserProfile follower = getUser(followerId);
            UserProfile target = getUser(targetUserId);

            FollowRelationId relationId = new FollowRelationId(followerId, targetUserId);

            boolean createdRelation = false;

            if (!followRelationRepository.existsById(relationId)) {
                followRelationRepository.save(new FollowRelation(follower, target));
                createdRelation = true;
            }

            feedCacheService.evictHomeFeed(followerId);
            feedMetricsService.recordFollowRequest(startedAtNanos, "follow", createdRelation);
            return new FollowResponse(followerId, targetUserId,
                    true,
                    Math.toIntExact(
                            followRelationRepository.countByFollower_Id(followerId)
                    )
            );

        } catch (ResponseStatusException exception) {
            feedMetricsService.recordServiceError(
                    "follow", exception.getStatusCode().toString()
            );
            throw exception;
        }
    }

    @Transactional
    public FollowResponse unfollow(String followerId, String targetUserId) {
        long startedAtNanos = feedMetricsService.startTimer();

        try {
            validateFollowRequest(followerId, targetUserId);
            FollowRelationId relationId = new FollowRelationId(followerId, targetUserId);
            boolean relationExisted = followRelationRepository.existsById(relationId);

            followRelationRepository.deleteById(relationId);
            feedCacheService.evictHomeFeed(followerId);
            feedMetricsService.recordFollowRequest(startedAtNanos, "unfollow", relationExisted);

            return new FollowResponse(followerId, targetUserId, false,
                    Math.toIntExact(
                            followRelationRepository.countByFollower_Id(followerId)
                    ));
        } catch (ResponseStatusException exception) {
            feedMetricsService.recordServiceError("unfollow", exception.getStatusCode().toString());
            throw exception;
        }
    }

    private void validateFollowRequest(String followerId, String targetUserId) {
        if (followerId.equals(targetUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Users cannot follow themselves"
            );
        }

        getUser(followerId);
        getUser(targetUserId);
    }

    @Transactional
    public PostResponse createPost(String authorId, String content, String idempotencyKey) {
        long startedAtNanos = feedMetricsService.startTimer();
        UserProfile author = getUser(authorId);
        String authorType = author.isHotUser() ? "hot" : "normal";

        String normalizedContent = content.trim();

        try {
            String requestHash = hashCreatePostRequest(authorId, normalizedContent);

            IdempotencyPostAttempt attempt = resolveCreatePostAttempt(
                    authorId, idempotencyKey, requestHash
            );

            if (attempt.replayedResponse().isPresent()) {
                feedMetricsService.recordPostCreation(startedAtNanos, authorType,
                        "replay");
                return attempt.replayedResponse().get();
            }

            if (attempt.isProgress()) {
                feedMetricsService.recordPostCreation(startedAtNanos, authorType, "in_progress");
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Post creation already in progress");
            }

            Post post = postRepository.save(new Post(
                    UUID.randomUUID().toString(),
                    author,
                    normalizedContent
            ));

            feedEventOutboxService.enqueuePostCreated(post);

            attempt.requestRecord().orElseThrow().markSucceeded(post.getId());

            feedMetricsService.recordPostCreation(startedAtNanos, authorType, "new");

            return toPostResponse(post);

        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().equals(HttpStatus.CONFLICT)
                    && exception.getReason() != null
                    && exception.getReason().contains("Idempotency key")
            ) {
                feedMetricsService.recordPostCreation(startedAtNanos, authorType, "conflict");
            }

            feedMetricsService.recordServiceError("create_post", exception.getStatusCode().toString());
            throw exception;
        }
    }

    private IdempotencyPostAttempt resolveCreatePostAttempt(String authorId, String idempotencyKey, String requestHash) {
        Optional<PostCreationRequest> existingRequest =
                postCreationRequestRepository.findByUserIdAndIdempotencyKey(authorId, idempotencyKey);

        if (existingRequest.isPresent()) {
            return handleExistingCreatePostRequest(existingRequest.get(), requestHash);
        }

        try {
            PostCreationRequest entity = new PostCreationRequest(authorId, idempotencyKey, requestHash);
            postCreationRequestRepository.saveAndFlush(entity);
            return new IdempotencyPostAttempt(
                    Optional.of(entity),
                    Optional.empty(),
                    false
            );
        } catch (DataIntegrityViolationException exception) {
            // if we lost the race
            PostCreationRequest existing = postCreationRequestRepository
                    .findByUserIdAndIdempotencyKey(authorId, idempotencyKey)
                    .orElseThrow(() -> exception);

            return handleExistingCreatePostRequest(existing, requestHash);
        }
    }

    private IdempotencyPostAttempt handleExistingCreatePostRequest(PostCreationRequest existing, String requestHash) {
        PostCreationRequest validatedRecord = validateAndReuseCreatePostRequest(existing, requestHash);

        return switch (validatedRecord.getStatus()) {
            case SUCCEEDED -> new IdempotencyPostAttempt(Optional.empty(),
                    Optional.of(getPost(validatedRecord.getPostId())), false);
            case IN_PROGRESS -> new IdempotencyPostAttempt(Optional.empty(), Optional.empty(), true);
        };
    }

    public PostResponse getPost(String postId) {
        return postRepository.findById(postId)
                .map(this::toPostResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
    }

    private PostResponse toPostResponse(Post post) {
        UserProfile author = post.getAuthor();
        return new PostResponse(post.getId(), author.getId(), author.getHandle(),
                author.getName(), post.getContent(),
                post.getCreatedAt());
    }

    private PostCreationRequest validateAndReuseCreatePostRequest(PostCreationRequest existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used for a different post request");
        }

        return existing;
    }

    private String hashCreatePostRequest(String authorId, String normalizedContent) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 should be available", e);
        }

        byte[] hash = digest.digest((authorId + "\n" + normalizedContent).getBytes(StandardCharsets.UTF_8));
        // 4byte => 4 * 8 bits

        // 4 bits, 4bits, 4 bits, 4bits,4 bits, 4bits,4 bits, 4bits

        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte hashByte : hash) {
            builder.append(String.format("%02x", hashByte));
        }

        return builder.toString();
    }

    @Transactional
    public void processPostCreatedEvent(String postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Post not found for async propagation"));
        applyDeliveryStrategy(post);
    }

    private void applyDeliveryStrategy(Post post) {
        if (post.getAuthor().isHotUser()) {
            feedMetricsService.recordDeliveryPath("hybrid_pull");
            return;
        }

        feedMetricsService.recordDeliveryPath("fan_out_on_write");

        for (String viewerId : getAffectedViewerIds(post.getAuthor().getId())) {
            Set<String> visibleAuthorIds = getVisibleAuthors(getUser(viewerId)).allAuthorIds();
            FeedItemResponse item = toFeedItem(post, viewerId, visibleAuthorIds);
            feedCacheService.prependToHomeFeed(viewerId, item);
        }

    }

    private Set<String> getAffectedViewerIds(String authorId) {
        Set<String> viewerIds = new LinkedHashSet<>();
        viewerIds.add(authorId);
        followRelationRepository.findByTargetUser_Id(authorId)
                .forEach(relation -> viewerIds.add(relation.getFollower().getId()));
        return viewerIds;
    }

    private record IdempotencyPostAttempt(Optional<PostCreationRequest> requestRecord,
                                          Optional<PostResponse> replayedResponse, boolean isProgress) {
    }

    private record VisibleAuthors(Set<String> allAuthorIds, Set<String> hotAuthorIds, Set<String> nonHotAuthorIds) {
    }

    private record NormalFeedSliceResult(FeedSlice slice, String cacheOutcome) {
    }

    private record FeedSlice(List<FeedItemResponse> items, boolean hasMore) {
    }
}
