import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MenuId, UserProfile } from '../../feed.models';

@Component({
  selector: 'app-user-menu',
  standalone: true,
  templateUrl: './user-menu.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserMenu {
  readonly menuId = input.required<MenuId>();
  readonly users = input.required<readonly UserProfile[]>();
  readonly selectedLabel = input.required<string>();
  readonly selectedBadge = input<string | null>(null);
  readonly open = input(false);
  readonly compact = input(false);

  readonly menuToggle = output<Event>();
  readonly userSelected = output<string>();

  protected getUserLabel(user: UserProfile): string {
    return `${user.name} = @${user.handle}`;
  }
}
