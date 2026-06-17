import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {FollowingResponse, FollowResponse, PostResponse, TimelinePageResponse, UserProfile} from '../feed.models';

@Injectable({providedIn: 'root'})
export class FeedApiService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = 'http://localhost:8080/api';

  getUsers(): Observable<UserProfile[]> {
    return this.http.get<UserProfile[]>(`${this.apiUrl}/users`);
  }

  createPost(authorId: string, content: string): Observable<PostResponse> {
    return this.http.post<PostResponse>(`${this.apiUrl}/posts`, {
      idempotencyKey: crypto.randomUUID(),
      authorId,
      content
    })
  }

  getFollowing(followerId: string): Observable<FollowingResponse> {
    return this.http.get<FollowingResponse>(`${this.apiUrl}/follows`, {
      params: {followerId}
    })
  }

  getHomeFeed(userId: string, cursor: string | null = null): Observable<TimelinePageResponse> {
    return this.http.get<TimelinePageResponse>(`${this.apiUrl}/feed/home`, {
      params: cursor ? {userId, cursor, limit: 5} : {userId, limit: 5}
    })
  }

  getUserFeed(userId: string, cursor: string | null = null): Observable<TimelinePageResponse> {
    return this.http.get<TimelinePageResponse>(`${this.apiUrl}/feed/user/${userId}`, {
      params: cursor ? {cursor, limit: 5} : {limit: 5}
    })
  }

  followUser(followerId: string, targetUserId: string): Observable<FollowResponse> {
    return this.http.post<FollowResponse>(`${this.apiUrl}/follows/${targetUserId}`,
      null,
      {params: {followerId}}
    )
  }

  unfollowUser(followerId: string, targetUserId: string): Observable<FollowResponse> {
    return this.http.delete<FollowResponse>(`${this.apiUrl}/follows/${targetUserId}`,
      {params: {followerId}}
    )
  }
}
