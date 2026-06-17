export interface UserProfile {
  id: string;
  handle: string;
  name: string;
  bio: string;
  hotUser: boolean;
}

export interface PostResponse {
  id: string;
  authorId: string;
  authorHandle: string;
  authorName: string;
  content: string;
  createdAt: string;
}

export interface FeedItemResponse {
  postId: string;
  authorId: string;
  authorHandle: string;
  authorName: string;
  content: string;
  createdAt: string;
  rankingScore: number;
  deliverStrategy: string;
  rankingReason: string;
}

export type TimelineMode = 'HOME' | 'USER';

export interface TimelinePageResponse {
  timelineOwnerId: string;
  mode: TimelineMode;
  totalItems: number;
  items: FeedItemResponse[];
  nextCursor: string | null;
}

export interface FollowResponse {
  followerId: string;
  targetUserId: string;
  following: boolean;
  totalFollowing: number;
}

export interface FollowingResponse {
  followerId: string;
  targetUserIds: string[];
  totalFollowing: number;
}

export type MenuId = 'viewer' | 'author' | 'timeline';

export interface ActivityEntry {
  id: string;
  label: string;
  createdAt: Date;
}
