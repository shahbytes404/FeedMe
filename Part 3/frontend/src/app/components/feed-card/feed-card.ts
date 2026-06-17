import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FeedItemResponse } from '../../feed.models';
import { DatePipe } from '@angular/common';

@Component({
  selector: 'app-feed-card',
  imports: [DatePipe],
  templateUrl: './feed-card.html',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FeedCard {
  readonly item = input.required<FeedItemResponse>();
  readonly hotBadge = input<string | null>(null);
  readonly showScore = input(false);
  readonly showReason = input(false);
  readonly secondary = input(false);
}
