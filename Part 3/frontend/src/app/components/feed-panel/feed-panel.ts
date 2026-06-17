import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FeedCard } from '../feed-card/feed-card';
import { TimelinePageResponse } from '../../feed.models';

@Component({
  selector: 'app-feed-panel',
  imports: [FeedCard],
  templateUrl: './feed-panel.html',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FeedPanel {
  readonly eyebrow = input.required<string>();
  readonly title = input.required<string>();
  readonly feed = input<TimelinePageResponse | null>(null);
  readonly loading = input(false);
  readonly loadingMore = input(false);
  readonly nextCursor = input<string | null>(null);
  readonly compact = input(false);
  readonly framed = input(true);
  readonly showHeader = input(true);
  readonly showScore = input(true);
  readonly showReason = input(true);
  readonly emptyText = input.required<string>();

  readonly loadMore = output<void>();
  readonly badgeForUser = input.required<(userId: string) => string | null>();
}
