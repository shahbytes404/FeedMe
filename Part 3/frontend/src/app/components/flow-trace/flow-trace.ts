import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ActivityEntry } from '../../feed.models';
import { DatePipe } from '@angular/common';

@Component({
  selector: 'app-flow-trace',
  imports: [DatePipe],
  templateUrl: './flow-trace.html',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlowTrace {
  readonly currentLabel = input.required<string>();
  readonly entries = input.required<readonly ActivityEntry[]>();
}
