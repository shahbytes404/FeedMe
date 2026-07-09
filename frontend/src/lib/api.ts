import type {
    DashboardData,
    FollowingResponse,
    FollowResponse,
    PostResponse,
    TimelinePageResponse,
    UserProfile
} from "../types.ts";

const API_ROOT = '/api';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
    const response = await fetch(`${API_ROOT}${path}`, {
        ...init,
        headers: {
            'Content-Type': 'application/json',
            ...(init?.headers ?? {})
        }
    });

    if (!response.ok) {
        const message = await response.text();
        throw new Error(message || `Request failed with status ${response.status}`);
    }

    return response.json() as Promise<T>;
}

function createIdempotencyKey() {
    if (typeof globalThis.crypto !== 'undefined' && typeof globalThis.crypto.randomUUID() === 'function') {
        return globalThis.crypto.randomUUID();
    }

    return `feedme-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
}

export const feedApi = {
    getUsers: () => request<UserProfile[]>('/users'),

    getFollowing: (followerId: string) =>
        request<FollowingResponse>(`/follows?followerId=${encodeURIComponent(followerId)}`),

    getHomeFeed: (userId: string, cursor?: string | null) => {
        const params = new URLSearchParams({userId, limit: '5'});

        if (cursor) {
            params.set('cursor', cursor);
        }

        return request<TimelinePageResponse>(`/feed/home?${params.toString()}`)
    },

    getUserFeed: (userId: string, cursor?: string | null) => {
        const params = new URLSearchParams({userId, limit: '5'});

        if (cursor) {
            params.set('cursor', cursor);
        }

        return request<TimelinePageResponse>(`/feed/user/${encodeURIComponent(userId)}?${params.toString()}`)
    },

    createPost: (authorId: string, content: string) => request<PostResponse>('/posts',
        {
            method: 'POST',
            body: JSON.stringify({
                idempotencyKey: createIdempotencyKey(),
                authorId,
                content
            })
        }),

    followUser: (followerId: string, targetUserId: string) => request<FollowResponse>(
        `/follows/${encodeURIComponent(targetUserId)}?followerId=${encodeURIComponent(followerId)}`,
        {method: 'POST'}
    ),

    unfollowUser: (followerId: string, targetUserId: string) => request<FollowResponse>(
        `/follows/${encodeURIComponent(targetUserId)}?followerId=${encodeURIComponent(followerId)}`,
        {method: 'DELETE'}
    ),

    async getDashboardData(activeUserId: string): Promise<DashboardData> {
        const [users, following, homeFeed, profileFeed] = await Promise.all(
            [
                this.getUsers(),
                this.getFollowing(activeUserId),
                this.getHomeFeed(activeUserId),
                this.getUserFeed(activeUserId),
            ]
        )

        return {users, following, homeFeed, profileFeed};
    }
}