import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-topbar',
  standalone: true,
  templateUrl: './app-topbar.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppTopbar {
  readonly userCount = input.required<number>();
  readonly hotUserCount = input.required<number>();
}
