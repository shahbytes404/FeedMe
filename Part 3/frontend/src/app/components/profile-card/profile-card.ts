import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MenuId, UserProfile } from '../../feed.models';
import { UserMenu } from '../user-menu/user-menu';

@Component({
  selector: 'app-profile-card',
  standalone: true,
  imports: [UserMenu],
  templateUrl: './profile-card.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfileCard {
  readonly users = input.required<readonly UserProfile[]>();
  readonly viewer = input<UserProfile | null>(null);
  readonly selectedLabel = input.required<string>();
  readonly selectedBadge = input<string | null>(null);
  readonly menuOpen = input(false);

  readonly menuToggle = output<{ menuId: MenuId; event: Event }>();
  readonly viewerSelected = output<string>();
}
