export type UserProfile = {
    id: string;
    handle: string;
    name: string;
    bio: string;
    hotUser: boolean
}

export type FeedItem = {
    postId: string;
    authorId: string;
    authorHandle: string;
    authorName: string;
    content: string;
    createdAt: string;
    rankingScore: number;
    deliveryStrategy: string;
    rankingReason: string;
}

export type TimelinePageResponse = {
    timelineOwnerId: string,
    mode: 'HOME' | 'USER' | string;
    totalItems: number;
    items: FeedItem[];
    nextCursor: string | null;
}

export type PostResponse = {
    id: string;
    authorId: string;
    authorHandle: string;
    authorName: string;
    content: string;
    createdAt: string;
}

export type FollowingResponse = {
    followerId: string;
    targetUserIds: string[];
    totalFollowing: number
}

export type FollowResponse = {
    followerId: string;
    targetUserId: string;
    following: boolean;
    totalFollowing: number
}

export type DashboardData = {
    users: UserProfile[];
    following: FollowingResponse;
    homeFeed: TimelinePageResponse;
    profileFeed: TimelinePageResponse;
}