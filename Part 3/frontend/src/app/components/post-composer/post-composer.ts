import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { UserMenu } from '../user-menu/user-menu';
import { MenuId, UserProfile } from '../../feed.models';

@Component({
  selector: 'app-post-composer',
  imports: [FormsModule, UserMenu],
  templateUrl: './post-composer.html',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PostComposer {
  readonly users = input.required<readonly UserProfile[]>();
  readonly selectedAuthorLabel = input.required<string>();
  readonly selectedAuthorBadge = input<string | null>(null);
  readonly authorMenuOpen = input(false);
  readonly draft = input.required<string>();

  readonly menuToggle = output<{ menuId: MenuId; event: Event }>();
  readonly draftChanged = output<string>();
  readonly publish = output<void>();
  readonly authorSelected = output<string>();
}
