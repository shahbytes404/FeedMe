import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { UserProfile } from '../../feed.models';

@Component({
  selector: 'app-user-list-panel',
  imports: [],
  templateUrl: './user-list-panel.html',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserListPanel {
  readonly eyebrow = input.required<string>();
  readonly title = input.required<string>();
  readonly users = input.required<readonly UserProfile[]>();
  readonly actionLabel = input.required<string>();
  readonly actionVariant = input<'primary' | 'alt'>('primary');
  readonly emptyText = input('');

  readonly actionSelected = output<string>();
}
