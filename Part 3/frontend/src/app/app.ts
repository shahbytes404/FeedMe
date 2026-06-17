import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
  ViewEncapsulation,
} from '@angular/core';
import { FeedApiService } from './services/feed-api.service';
import { FeedItemResponse, MenuId, TimelinePageResponse, UserProfile } from './feed.models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AppTopbar } from './components/app-topbar/app-topbar';
import { ProfileCard } from './components/profile-card/profile-card';
import { UserListPanel } from './components/user-list-panel/user-list-panel';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [AppTopbar, ProfileCard, UserListPanel],
  templateUrl: './app.html',
  styleUrl: './app.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
})
export class App {
  private readonly api = inject(FeedApiService);

  protected readonly users = signal<UserProfile[]>([]);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly selectedViewerId = signal('u1');
  protected readonly selectedAuthorId = signal('u1');
  protected readonly selectedTimelineUserId = signal('u2');

  protected readonly followedUserIds = signal<ReadonlySet<string>>(new Set<string>());

  protected readonly homeFeed = signal<TimelinePageResponse | null>(null);
  protected readonly userFeed = signal<TimelinePageResponse | null>(null);

  protected readonly loadingHome = signal(false);
  protected readonly loadingUser = signal(false);
  protected readonly loadingMoreHome = signal(false);
  protected readonly loadingMoreUser = signal(false);

  protected readonly homeNextCursor = signal<string | null>(null);
  protected readonly userNextCursor = signal<string | null>(null);

  protected readonly activityLabel = signal('Idle');
  protected readonly requestError = signal('');

  protected readonly activeMenu = signal<MenuId | null>(null);

  protected readonly hotUserCount = computed(
    () => this.users().filter((user) => user.hotUser).length,
  );
  protected readonly selectedViewer = computed(
    () => this.getUserById(this.selectedViewerId()) ?? null,
  );

  protected readonly selectedAuthor = computed(
    () => this.getUserById(this.selectedAuthorId()) ?? null,
  );

  protected readonly selectedViewerLabel = computed(() =>
    this.getUserLabel(this.selectedViewerId()),
  );

  protected readonly selectedAuthorLabel = computed(() =>
    this.getUserLabel(this.selectedAuthorId()),
  );

  protected readonly selectedViewerBadge = computed(() =>
    this.getUserBadge(this.selectedViewerId()),
  );

  protected readonly selectedAuthorBadge = computed(() =>
    this.getUserBadge(this.selectedAuthorId()),
  );

  protected readonly selectedTimelineUserBadge = computed(() =>
    this.getUserBadge(this.selectedTimelineUserId()),
  );

  protected readonly viewerFollowing = computed(() =>
    this.users().filter((user) => !this.isViewer(user.id) && this.isFollowing(user.id)),
  );

  protected readonly suggestedUsers = computed(() =>
    this.users().filter((user) => !this.isViewer(user.id) && !this.isFollowing(user.id)),
  );

  constructor() {
    this.loadUsers();
  }

  private loadUsers() {
    this.activityLabel.set('Loading users');
    this.api
      .getUsers()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (users) => {
          this.users.set(users);
          this.ensureSelectedUserExist(users);
          this.loadViewerFollowing();
          this.refreshAll();
          this.activityLabel.set('Users loaded');
        },
        error: () => {
          this.requestError.set('Unable to load users. Start the backend on port 8080');
          this.activityLabel.set('User load failed');
        },
      });
  }

  private ensureSelectedUserExist(users: UserProfile[]) {
    if (users.length > 0) {
      const firstUser = users[0];

      if (!users.some((user) => user.id === this.selectedViewerId())) {
        this.selectedViewerId.set(firstUser.id);
      }

      if (!users.some((user) => user.id === this.selectedAuthorId())) {
        this.selectedAuthorId.set(firstUser.id);
      }

      if (!users.some((user) => user.id === this.selectedTimelineUserId())) {
        this.selectedTimelineUserId.set(firstUser.id);
      }
    }
  }

  private loadViewerFollowing() {
    const viewerId = this.selectedViewerId();
    this.api
      .getFollowing(viewerId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          if (viewerId !== this.selectedViewerId()) {
            return;
          }
          this.followedUserIds.set(new Set(response.targetUserIds));
        },
        error: () => {
          if (viewerId !== this.selectedViewerId()) {
            return;
          }
          this.requestError.set('Unable to load the follow graph');
          this.activityLabel.set('Follow graph load failed');
        },
      });
  }

  private refreshAll() {
    this.loadHomeFeed();
    this.loadUserFeed();
  }

  private loadHomeFeed() {
    this.loadHomeFeedPage(null, false);
  }

  private loadHomeFeedPage(cursor: string | null, append: boolean) {
    const viewerId = this.selectedViewerId();
    if (append) {
      this.loadingMoreHome.set(true);
    } else {
      this.loadingHome.set(true);
      this.requestError.set('');
      this.homeFeed.set(null);
      this.homeNextCursor.set(null);
      this.activityLabel.set(
        `Loading home feed for ${this.getUserById(viewerId)?.name ?? viewerId}`,
      );
    }

    this.api
      .getHomeFeed(viewerId, cursor)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          if (viewerId !== this.selectedViewerId()) {
            return;
          }

          this.homeFeed.set(
            append && this.homeFeed()
              ? {
                  ...response,
                  items: this.mergeUniquePosts(this.homeFeed()?.items ?? [], response.items),
                }
              : response,
          );
          this.homeNextCursor.set(response.nextCursor);
          this.loadingHome.set(false);
          this.loadingMoreHome.set(false);
          this.activityLabel.set(
            `Home feed ready for ${this.getUserById(viewerId)?.name ?? viewerId}`,
          );
        },
        error: () => {
          if (viewerId !== this.selectedViewerId()) {
            return;
          }

          this.requestError.set('Unable to load the home feed. Start the backend on port 8080.');
          this.loadingHome.set(false);
          this.loadingMoreHome.set(false);
          this.activityLabel.set('Home feed load failed');
        },
      });
  }

  private loadUserFeed() {
    this.loadUserFeedPage(null, false);
  }

  protected getUserById(userId: string): UserProfile | undefined {
    return this.users().find((entry) => entry.id === userId);
  }

  private mergeUniquePosts(existing: FeedItemResponse[], incoming: FeedItemResponse[]) {
    const seen = new Set(existing.map((item) => item.postId));
    const merged = [...existing];
    for (const item of incoming) {
      if (seen.has(item.postId)) {
        continue;
      }
      seen.add(item.postId);
      merged.push(item);
    }
    return merged;
  }

  private loadUserFeedPage(cursor: string | null, append: boolean) {
    const timeLineUserId = this.selectedTimelineUserId();
    if (append) {
      this.loadingMoreUser.set(true);
    } else {
      this.loadingUser.set(true);
      this.userFeed.set(null);
      this.userNextCursor.set(null);
      this.activityLabel.set(
        `Loading author timeline fir ${this.getUserById(timeLineUserId)?.name ?? timeLineUserId}`,
      );
    }

    this.api
      .getUserFeed(timeLineUserId, cursor)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          if (timeLineUserId !== this.selectedTimelineUserId()) {
            return;
          }
          this.userFeed.set(
            append && this.userFeed()
              ? {
                  ...response,
                  items: this.mergeUniquePosts(this.userFeed()?.items ?? [], response.items),
                }
              : response,
          );
          this.userNextCursor.set(response.nextCursor);
          this.loadingUser.set(false);
          this.loadingMoreUser.set(false);
          this.activityLabel.set(
            `Author timeline ready for ${this.getUserById(timeLineUserId)?.name ?? timeLineUserId}`,
          );
        },
        error: () => {
          if (timeLineUserId !== this.selectedTimelineUserId()) {
            return;
          }
          this.requestError.set('Unable to load the selected user timeline');
          this.loadingUser.set(false);
          this.loadingMoreUser.set(false);
          this.activityLabel.set('Author timeline load failed');
        },
      });
  }

  protected toggleMenu(menuId: MenuId, event: Event) {
    event.stopPropagation();
    this.activeMenu.update((current) => (current === menuId ? null : menuId));
  }

  protected toggleFollow(targetUserId: string) {
    const viewerId = this.selectedViewerId();
    const isFollowing = this.isFollowing(targetUserId);
    const target = this.getUserById(targetUserId);
    this.activityLabel.set(
      `${isFollowing ? 'Unfollowing' : 'Following'} ${target?.name ?? targetUserId}`,
    );

    const request = isFollowing
      ? this.api.unfollowUser(viewerId, targetUserId)
      : this.api.followUser(viewerId, targetUserId);

    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (response) => {
        this.followedUserIds.update((current) => {
          const next = new Set(current);
          if (response.following) {
            next.add(targetUserId);
          } else {
            next.delete(targetUserId);
          }
          return next;
        });

        this.loadHomeFeed();
        this.activityLabel.set(
          `${response.following ? 'Followed' : 'Unfollowed'} ${target?.name ?? targetUserId}`,
        );
      },
      error: () => {
        this.requestError.set('Follow state could not be updated.');
        this.activityLabel.set('Follow update failed');
      },
    });
  }

  private getUserLabel(userId: string) {
    const user = this.getUserById(userId);
    return user ? `${user.name} - @${user.handle}` : userId;
  }

  private getUserBadge(userId: string): string | null {
    return this.getUserById(userId)?.hotUser ? 'Hot user' : null;
  }

  protected isMenuOpen(menuId: MenuId) {
    return this.activeMenu() === menuId;
  }

  protected selectViewer(userId: string) {
    this.selectedViewerId.set(userId);
    this.activeMenu.set(null);
    this.loadViewerFollowing();
    this.loadHomeFeed();
  }

  private isViewer(userId: string) {
    return userId === this.selectedViewerId();
  }

  private isFollowing(targetUserId: string) {
    return this.followedUserIds().has(targetUserId);
  }
}
