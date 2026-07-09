import {
    startTransition,
    use,
    useActionState,
    useDeferredValue,
    useEffect,
    useEffectEvent,
    useMemo,
    useState
} from "react";
import {getDashboardResource, invalidateDashboardResource} from "../lib/dashboard-resource.ts";
import type {DashboardData, FeedItem, FollowResponse, TimelinePageResponse, UserProfile} from "../types.ts";
import {feedApi} from "../lib/api.ts";

export function useFeedmeDashboard(activeUserId: string) {
    const dashboardPromise = useMemo(() => getDashboardResource(activeUserId), [activeUserId]);

    const initialData = use<DashboardData>(dashboardPromise);

    const [searchQuery, setSearchQuery] = useState('');

    const activeUser = useMemo(
        () => initialData.users.find((user) => user.id === activeUserId) ?? initialData.users[0],
        [activeUserId, initialData.users]
    );

    const [followingIds, setFollowingIds] = useState<string[]>(initialData.following.targetUserIds);

    const [selectedTimelineUserId, setSelectedTimelineUserId] = useState(activeUserId);

    const [composerUserId, setComposerUserId] = useState(activeUserId);

    const [homeFeed, setHomeFeed] = useState<TimelinePageResponse>(initialData.homeFeed);

    const [selectedUserFeed, setSelectedUserFeed] = useState<TimelinePageResponse>(initialData.profileFeed);

    const deferredSearchQuery = useDeferredValue(searchQuery);

    const filteredUsers = useMemo(() => {
        const query = deferredSearchQuery.trim().toLowerCase();
        if (!query) {
            return initialData.users;
        }

        return initialData.users.filter((user) => {
            let filterBy = `${user.name} ${user.handle} ${user.bio}`;
            return filterBy.toLowerCase().includes(query);
        })
    }, [deferredSearchQuery, initialData.users])

    function selectTimelineUser(user: UserProfile) {
        setSelectedTimelineUserId(user.id);
    }

    const composeUser = useMemo(
        () => initialData.users.find((user) => user.id === composerUserId)
            ?? activeUser,
        [activeUser, composerUserId, initialData.users]
    );

    const selectedTimelineUser = useMemo(
        () => initialData.users.find((user) => user.id === selectedTimelineUserId)
            ?? activeUser,
        [activeUser, initialData.users, selectedTimelineUserId]
    )

    useEffect(() => {
        setHomeFeed(initialData.homeFeed);
        setSelectedUserFeed(initialData.profileFeed);
        setComposerUserId(activeUserId);
        setSelectedTimelineUserId(activeUserId);
        setFollowingIds(initialData.following.targetUserIds);
        setSearchQuery('');
    }, [activeUserId, initialData]);

    const refreshAll = useEffectEvent(async () => {
        invalidateDashboardResource(activeUserId);

        const nextData = await getDashboardResource(activeUserId);

        startTransition(() => {
            setHomeFeed(nextData.homeFeed);
            setFollowingIds(nextData.following.targetUserIds);

            if (selectedTimelineUserId === activeUserId) {
                setSelectedUserFeed(nextData.profileFeed);
            }
        })
    })

    async function handleFollowToggle(targetUserId: string): Promise<FollowResponse> {
        const currentlyFollowing = followingIds.includes(targetUserId);
        const response = currentlyFollowing
            ? await feedApi.unfollowUser(activeUserId, targetUserId)
            : await feedApi.followUser(activeUserId, targetUserId);

        setFollowingIds((previous) => currentlyFollowing ? previous.filter((id) => id !== targetUserId) : [...previous, targetUserId]);

        await refreshAll();

        return response;
    }

    type PostActionState = {
        error: string | null;
        submitted: boolean;
    }

    function mapPostToFeedItem(post: Awaited<ReturnType<typeof feedApi.createPost>>): FeedItem {
        return {
            postId: post.id,
            authorId: post.authorId,
            authorHandle: post.authorHandle,
            authorName: post.authorName,
            content: post.content,
            createdAt: post.createdAt,
            rankingScore: 0.99,
            deliveryStrategy: 'Fanout + cache refresh',
            rankingReason: 'Fresh post boosted for immediate visibility'
        }
    }

    async function loadMoreHome() {
        if (!homeFeed.nextCursor) {
            return;
        }

        const nextPage = await feedApi.getHomeFeed(activeUserId, homeFeed.nextCursor);

        startTransition(() => {
            setHomeFeed({
                ...nextPage,
                items: [...homeFeed.items, ...nextPage.items]
            })
        })
    }

    async function loadMoreSelectedUser() {
        if (!selectedUserFeed.nextCursor) {
            return;
        }

        const nextPage =
            await feedApi.getUserFeed(selectedTimelineUserId, selectedUserFeed.nextCursor);

        startTransition(() => {
            setSelectedUserFeed({
                ...nextPage,
                items: [...selectedUserFeed.items, ...nextPage.items]
            })
        })
    }

    useEffect(() => {
        let cancelled = false;

        feedApi.getUserFeed(selectedTimelineUserId).then((nextFeed) => {
            if (cancelled) {
                return;
            }
            void startTransition(() => {
                setSelectedUserFeed(nextFeed);
            })
        })

        return () => {
            cancelled = true;
        }
    }, [selectedTimelineUserId]);


    const [postState, submitPost, isPosting] =
        useActionState<PostActionState, FormData>(
            async (_previousState, formData) => {
                const content = String(formData.get('content') ?? '').trim();

                if (!content) {
                    return {error: 'Write something worth shipping', submitted: false};
                }

                if (content.length > 200) {
                    return {error: 'Posts are capped at 200 characters by the API.', submitted: false}
                }

                const authorId = String(formData.get('authorId') ?? composerUserId);
                const created = await feedApi.createPost(authorId, content);

                const optimisticItem = mapPostToFeedItem(created);

                startTransition(() => {
                    setHomeFeed((previous) => ({
                        ...previous,
                        totalItems: previous.totalItems + 1,
                        items: [optimisticItem, ...previous.items]
                    }))

                    if (authorId === selectedTimelineUserId) {
                        setSelectedUserFeed((previous) => ({
                            ...previous,
                            totalItems: previous.totalItems + 1,
                            items: [optimisticItem, ...previous.items]
                        }))
                    }
                })

                return {error: null, submitted: true};

            },
            {error: null, submitted: false}
        )

    return {
        users: initialData.users,
        activeUser,
        searchQuery,
        setSearchQuery,
        filteredUsers,
        followingIds,
        selectedTimelineUser,
        selectTimelineUser,
        handleFollowToggle,
        setComposerUserId,
        composeUser,
        postState,
        submitPost,
        isPosting,
        homeFeed,
        selectedUserFeed,
        composerUserId,
        loadMoreHome,
        loadMoreSelectedUser
    }
}