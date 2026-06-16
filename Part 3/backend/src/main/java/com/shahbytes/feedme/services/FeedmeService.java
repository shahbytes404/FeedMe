package com.shahbytes.feedme.services;

import com.shahbytes.feedme.dtos.*;
import com.shahbytes.feedme.models.*;
import com.shahbytes.feedme.repository.FollowRelationRepository;
import com.shahbytes.feedme.repository.PostCreationRequestRepository;
import com.shahbytes.feedme.repository.PostRepository;
import com.shahbytes.feedme.repository.UserProfileRepository;
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
public class FeedmeService {

    private static final Comparator<FeedItemResponse> FEED_ORDER = Comparator.comparing(FeedItemResponse::createdAt)
            .thenComparing(FeedItemResponse::postId)
            .reversed();

    private final UserProfileRepository userProfileRepository;
    private final PostRepository postRepository;
    private final PostCreationRequestRepository postCreationRequestRepository;
    private final FollowRelationRepository followRelationRepository;
    private final FeedCacheService feedCacheService;
    private final FeedEventOutboxService feedEventOutboxService;

    public FeedmeService(UserProfileRepository userProfileRepository, PostRepository postRepository, PostCreationRequestRepository postCreationRequestRepository, FollowRelationRepository followRelationRepository, FeedCacheService feedCacheService, FeedEventOutboxService feedEventOutboxService) {
        this.userProfileRepository = userProfileRepository;
        this.postRepository = postRepository;
        this.postCreationRequestRepository = postCreationRequestRepository;
        this.followRelationRepository = followRelationRepository;
        this.feedCacheService = feedCacheService;
        this.feedEventOutboxService = feedEventOutboxService;
    }

    @Transactional
    public PostResponse createPost(String authorId, String content, String idempotencyKey) {
        UserProfile author = getUser(authorId);
        String authorType = author.isHotUser() ? "hot" : "normal";

        String normalizedCOntent = content.trim();
        String requestHash = hashCreatePostRequest(authorId, normalizedCOntent);

        IdempotentPostAttempt attempt = resolveCreatePostAttempt(authorId, idempotencyKey, requestHash);

        if (attempt.replayedResponse().isPresent()) {
            return attempt.replayedResponse.get();
        }

        if (attempt.inProgress()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Post creation request already in progress");
        }

        Post post = postRepository.save(new Post(
                UUID.randomUUID().toString(), author, normalizedCOntent
        ));

        feedEventOutboxService.enqueuePostCreated(post);

        attempt.requestRecord().orElseThrow().markSucceeded(post.getId());

        return toPostResponse(post);
    }

    private IdempotentPostAttempt resolveCreatePostAttempt(String authorId, String idempotencyKey, String requestHash) {
        Optional<PostCreationRequest> existingRequest =
                postCreationRequestRepository.findByUserIdAndIdempotencyKey(authorId, idempotencyKey);

        if (existingRequest.isPresent()) {
            return handleExistingCreatePostRequest(existingRequest.get(), requestHash);
        }


        try {
            PostCreationRequest entity = new PostCreationRequest(authorId, idempotencyKey, requestHash);
            postCreationRequestRepository.saveAndFlush(
                    entity);
            return new IdempotentPostAttempt(
                    Optional.of(entity),
                    Optional.empty(),
                    false
            );
        } catch (DataIntegrityViolationException exception) {
            // if we lost that race
            PostCreationRequest existing = postCreationRequestRepository.findByUserIdAndIdempotencyKey(authorId, idempotencyKey)
                    .orElseThrow(() -> exception);

            return handleExistingCreatePostRequest(existing, requestHash);
        }

    }

    private IdempotentPostAttempt handleExistingCreatePostRequest(PostCreationRequest postCreationRequest, String requestHash) {
        PostCreationRequest validatedRecord = validateAndReuseCreatePostRequest(postCreationRequest, requestHash);
        return switch (validatedRecord.getStatus()) {
            case SUCCEEDED -> new IdempotentPostAttempt(Optional.empty(),
                    Optional.of(getPost(validatedRecord.getPostId())), false);
            case IN_PROGRESS -> new IdempotentPostAttempt(Optional.empty(),
                    Optional.empty(), true);
        };
    }

    public PostResponse getPost(String postId) {
        return postRepository.findById(postId)
                .map(this::toPostResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
    }

    private PostCreationRequest validateAndReuseCreatePostRequest(PostCreationRequest postCreationRequest, String requestHash) {
        if (!postCreationRequest.getRequestHash().equals(requestHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used for a different post request");
        }

        return postCreationRequest;
    }

    private String hashCreatePostRequest(String authorId, String normalizedCOntent) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 should always be available", e);
        }
        byte[] hash = digest.digest((authorId + "\n" + normalizedCOntent).getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(hash.length * 2);

        for (byte hashByte : hash) {
            builder.append(String.format("%02x", hashByte));
        }

        return builder.toString();
    }

    private PostResponse toPostResponse(Post post) {
        UserProfile author = post.getAuthor();
        return new PostResponse(post.getId(), author.getId(), author.getHandle(), author.getName(), post.getContent(), post.getCreatedAt());
    }

    private UserProfile getUser(String authorId) {
        return userProfileRepository.findById(authorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public TimelinePageResponse getHomeFeed(String userId, String cursor, int limit) {
        int pageSize = normalizeLimit(limit);

        UserProfile viewer = getUser(userId);

        FeedCursorCodec.FeedCursor pageCursor = FeedCursorCodec.parse(cursor);

        VisibleAuthors visibleAuthors = getVisibleAuthors(viewer);

        BaseFeedSLiceResult baseSLiceResult = getBaseHomeFeedSlice(viewer.getId(), visibleAuthors.nonHotAuthorIds(), pageCursor, pageSize);

        FeedSlice hotSlice = getHotHomeFeedSlice(viewer.getId(), visibleAuthors, pageCursor, pageSize);

        int totalItems = Math.toIntExact(postRepository.countByAuthor_IdIn(visibleAuthors.allAuthorIds));

        return mergeHomeFeedSlices(userId, totalItems, pageSize, baseSLiceResult.slice, hotSlice);
    }

    private TimelinePageResponse mergeHomeFeedSlices(String userId, int totalItems, int pageSize,
                                                     FeedSlice baseSlice, FeedSlice hotSlice) {
        List<FeedItemResponse> mergedItems = new ArrayList<>(pageSize + 1);

        int baseIndex = 0;
        int hotIndex = 0;
        int baseItemsUsed = 0;
        int hotItemsUsed = 0;

        while (mergedItems.size() < pageSize + 1
                && (baseIndex < baseSlice.items().size() || hotIndex < hotSlice.items().size())
        ) {
            FeedItemResponse nextBase = baseIndex < baseSlice.items.size() ? baseSlice.items().get(baseIndex) : null;
            FeedItemResponse nextHot = hotIndex < hotSlice.items().size() ? hotSlice.items().get(hotIndex) : null;

            if (nextHot == null || (nextBase != null && FEED_ORDER.compare(nextBase, nextHot) <= 0)) {
                mergedItems.add(nextBase);
                baseIndex++;
                baseItemsUsed++;
            } else {
                mergedItems.add(nextHot);
                hotIndex++;
                hotItemsUsed++;
            }
        }

        boolean hasMore = mergedItems.size() > pageSize
                || baseIndex < baseSlice.items().size()
                || hotIndex < hotSlice.items().size()
                || baseSlice.hasMore()
                || hotSlice.hasMore();

        List<FeedItemResponse> pageItems = mergedItems.size() > pageSize ? mergedItems.subList(0, pageSize)
                : mergedItems;

        String nextCursor = hasMore && !pageItems.isEmpty()
                ? FeedCursorCodec.encode(pageItems.get(pageItems.size() - 1))
                : null;

        return new TimelinePageResponse(userId, TimelineMode.HOME, totalItems, pageItems, nextCursor);
    }

    private FeedSlice getHotHomeFeedSlice(String userId, VisibleAuthors visibleAuthors, FeedCursorCodec.FeedCursor pageCursor, int pageSize) {
        if (visibleAuthors.hotAuthorIds().isEmpty()) {
            return new FeedSlice(List.of(), false);
        }

        List<Post> posts = fetchHomeFeedPosts(visibleAuthors.hotAuthorIds(), pageCursor, pageSize + 1);

        return buildFeedSlice(posts, pageSize, userId, visibleAuthors.hotAuthorIds());
    }

    private FeedSlice buildFeedSlice(List<Post> posts, int pageSize, String userId, Set<String> visibleAUthorIds) {
        boolean hasMore = posts.size() > pageSize;
        List<Post> pagePosts = hasMore ? posts.subList(0, pageSize) : posts;

        List<FeedItemResponse> pageItems = pagePosts.stream().map(post -> toFeedItem(post, userId, visibleAUthorIds))
                .toList();

        return new FeedSlice(pageItems, hasMore);
    }

    private FeedItemResponse toFeedItem(Post post, String viewerId, Set<String> visibleFollowIds) {
        UserProfile author = post.getAuthor();

        double recencyScore = 1.0 - Duration.between(post.getCreatedAt(), Instant.now()).toMinutes() / 600.0;
        double hotUserPenalty = author.isHotUser() ? 0.15 : 0.0;

        double affinityBoost = visibleFollowIds.contains(author.getId()) && viewerId.equals(author.getId()) ? 0.0 : 0.2;

        double rankingScore = Math.round((recencyScore + affinityBoost - hotUserPenalty) * 100.0) / 100.0;

        String deliveryStrategy = author.isHotUser() ? "hybrid-pull" : "fan-out-on-write";

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

        /**
         120 minutes
         rs = 1.0 - 0.2 = 0.8
         0.859999999999999999 * 100
         85.999999999999 => round => 86
         after dividing by 100
         0.86
         */
    }

    private List<Post> fetchHomeFeedPosts(Set<String> authorIds, FeedCursorCodec.FeedCursor pageCursor, int fetchSize) {
        PageRequest pageRequest = PageRequest.of(0, fetchSize);
        if (pageCursor == null) {
            return postRepository.findByAuthor_idInOrderByCreatedAtDescIdDesc(authorIds, pageRequest);
        }

        return postRepository.findHomeFeedPageAfterCursor(authorIds, pageCursor.createdAt(), pageCursor.postId(), pageRequest);
    }

    private BaseFeedSLiceResult getBaseHomeFeedSlice(String userId, Set<String> nonHotAuthorsIds,
                                                     FeedCursorCodec.FeedCursor pageCursor, int pageSize) {
        if (nonHotAuthorsIds.isEmpty()) {
            FeedSlice slice = new FeedSlice(List.of(), false);
            return new BaseFeedSLiceResult(slice, "empty_non_hot");
        }

        Optional<FeedSlice> cachedSlice = getCachedBaseHomeFeedSlice(userId, pageCursor, pageSize);

        if (cachedSlice.isPresent()) {
            return new BaseFeedSLiceResult(cachedSlice.get(), "hit");
        }

        // cache miss
        List<Post> posts = fetchHomeFeedPosts(nonHotAuthorsIds, pageCursor, pageSize + 1);

        FeedSlice slice = buildFeedSlice(posts, pageSize, userId, nonHotAuthorsIds);

        if (pageCursor == null && pageSize == FeedCacheService.DEFAULT_PAGE_SIZE) {
            TimelinePageResponse tpresponse = new TimelinePageResponse(userId, TimelineMode.HOME,
                    Math.toIntExact(postRepository.countByAuthor_IdIn(nonHotAuthorsIds)),
                    slice.items,
                    slice.hasMore() && !slice.items.isEmpty()
                            ? FeedCursorCodec.encode(
                            slice.items().get(slice.items().size() - 1)
                    ) : null);
            feedCacheService.cacheHomeFeed(tpresponse);
        }

        return new BaseFeedSLiceResult(slice, "miss");
    }

    private Optional<FeedSlice> getCachedBaseHomeFeedSlice(String userId, FeedCursorCodec.FeedCursor cursor,
                                                           int pageSize) {
        if (cursor != null) {
            return Optional.empty();
        }

        if (pageSize > FeedCacheService.DEFAULT_PAGE_SIZE) {
            return Optional.empty();
        }

        Optional<TimelinePageResponse> cachedPage = feedCacheService.getHomeFeed(userId);

        if (cachedPage.isEmpty()) {
            return Optional.empty();
        }

        return adaptCachedFirstPage(cachedPage.get(), pageSize);
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

        Set<String> hotAUthorIds = new LinkedHashSet<>();

        Set<String> nonHotAuthorIds = new LinkedHashSet<>();

        allAuthorIds.add(viewer.getId());

        if (viewer.isHotUser()) {
            hotAUthorIds.add(viewer.getId());
        } else {
            nonHotAuthorIds.add(viewer.getId());
        }

        for (FollowRelation relation : followRelations) {
            UserProfile author = relation.getTargetUser();
            allAuthorIds.add(author.getId());

            if (author.isHotUser()) {
                hotAUthorIds.add(author.getId());
            } else {
                nonHotAuthorIds.add(author.getId());
            }
        }

        return new VisibleAuthors(allAuthorIds, hotAUthorIds, nonHotAuthorIds);
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return FeedCacheService.DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, 20);
    }

    public TimelinePageResponse getUserFeed(String userId, String cursor, int limit) {
        int pageSize = normalizeLimit(limit);

        try {
            getUser(userId);

            FeedCursorCodec.FeedCursor pageCursor = FeedCursorCodec.parse(cursor);
            List<Post> posts = fetchUserFeedPosts(userId, pageCursor, pageSize + 1);
            int totalItems = Math.toIntExact(postRepository.countByAuthor_Id(userId));
            return buildTimelinePage(userId, TimelineMode.USER, posts, totalItems, pageSize);
        } catch (ResponseStatusException e) {
            // use metrics here
            throw e;
        }
    }

    private TimelinePageResponse buildTimelinePage(String timelineOwnerId, TimelineMode mode, List<Post> posts, int totalItems, int pageSize) {
        boolean hasMore = posts.size() > pageSize;
        List<Post> pagePosts = hasMore ? posts.subList(0, pageSize) : posts;

        List<FeedItemResponse> pageItems = pagePosts.stream().map(post -> toFeedItem(post, timelineOwnerId, Set.of(timelineOwnerId))).toList();

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

        return postRepository.findUserFeedPageAfterCursor(userId, pageCursor.createdAt(), pageCursor.postId(), pageRequest);
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
            // TODO: metrics
            return;
        }

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

    @Transactional
    public FollowResponse follow(String followerId, String targetUserId) {
        try {
            validateFollowRequest(followerId, targetUserId);

            UserProfile follower = getUser(followerId);
            UserProfile target = getUser(targetUserId);

            FollowRelationId relationId = new FollowRelationId(followerId, targetUserId);

            if (!followRelationRepository.existsById(relationId)) {
                followRelationRepository.save(new FollowRelation(follower, target));
            }

            feedCacheService.evictHomeFeed(followerId);

            return new FollowResponse(followerId, targetUserId, true,
                    Math.toIntExact(
                            followRelationRepository.countByFollower_Id(followerId)
                    )
            );

        } catch (Exception exception) {
            exception.printStackTrace();
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
    public FollowResponse unfollow(String followerId, String targetUserId) {
        try {
            validateFollowRequest(followerId, targetUserId);
            FollowRelationId relationId = new FollowRelationId(followerId, targetUserId);

            followRelationRepository.deleteById(relationId);
            feedCacheService.evictHomeFeed(followerId);

            return new FollowResponse(followerId, targetUserId, false,
                    Math.toIntExact(
                            followRelationRepository.countByFollower_Id(followerId)
                    )
            );

        } catch (Exception exception) {
            throw exception;
        }
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


    private record VisibleAuthors(Set<String> allAuthorIds, Set<String> hotAuthorIds, Set<String> nonHotAuthorIds) {
    }

    private record IdempotentPostAttempt(Optional<PostCreationRequest> requestRecord,
                                         Optional<PostResponse> replayedResponse, boolean inProgress) {

    }

    // non-hot users
    private record BaseFeedSLiceResult(FeedSlice slice, String cacheOutcome) {
    }

    // hot users
    private record FeedSlice(List<FeedItemResponse> items, boolean hasMore) {
    }
}
