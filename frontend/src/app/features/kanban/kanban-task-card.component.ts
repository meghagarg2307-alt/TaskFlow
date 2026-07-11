import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Task } from '@core/models/task.models';
import { isDueDateOverdue } from '@core/utils/due-date.util';

@Component({
  selector: 'tf-kanban-task-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="card"
             [class.card--low]="task().priority === 'LOW'"
             [class.card--medium]="task().priority === 'MEDIUM'"
             [class.card--high]="task().priority === 'HIGH'"
             [class.card--urgent]="task().priority === 'URGENT'"
             (click)="open.emit(task().id)">
      <header class="card__header">
        <h3 class="card__title">{{ task().title }}</h3>
      </header>

      <footer class="card__meta">
        <span class="card__priority card__priority--{{ task().priority.toLowerCase() }}">
          {{ task().priority }}
        </span>

        @if (task().assignee; as a) {
          <span class="card__assignee" [title]="a.fullName">
            {{ initials(a.fullName) }}
          </span>
        }

        @if (task().dueDate; as due) {
          <span class="card__due tf-muted" [class.card__due--overdue]="isDueDateOverdueFn(due)">
            {{ formatDue(due) }}
          </span>
        }
      </footer>
    </article>
  `,
  styles: [`
    .card {
      display: flex; flex-direction: column;
      padding: var(--space-3);
      background: var(--color-surface);
      border-left: 3px solid var(--color-border);
      border-radius: var(--radius-md);
      box-shadow: var(--shadow-1);
      cursor: grab;
      user-select: none;
      transition: transform 80ms ease, box-shadow 80ms ease;
    }
    .card:hover { box-shadow: var(--shadow-2); }
    .card:active { cursor: grabbing; }

    .card--low    { border-left-color: var(--priority-low); }
    .card--medium { border-left-color: var(--priority-medium); }
    .card--high   { border-left-color: var(--priority-high); }
    .card--urgent { border-left-color: var(--priority-urgent); }

    .card__title {
      margin: 0;
      font-size: var(--font-size-md);
      font-weight: 500;
      line-height: var(--line-tight);
      color: var(--color-text);
    }

    .card__meta {
      display: flex; align-items: center; gap: var(--space-2);
      margin-top: var(--space-3);
      font-size: var(--font-size-xs);
    }

    .card__priority {
      padding: 2px 6px; border-radius: var(--radius-sm);
      font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px;
      font-size: 10px;
      color: #fff;
    }
    .card__priority--low    { background: var(--priority-low); }
    .card__priority--medium { background: var(--priority-medium); }
    .card__priority--high   { background: var(--priority-high); }
    .card__priority--urgent { background: var(--priority-urgent); }

    .card__assignee {
      display: inline-flex; align-items: center; justify-content: center;
      width: 22px; height: 22px;
      border-radius: 50%;
      background: var(--color-brand-100); color: var(--color-brand-700);
      font-size: 10px; font-weight: 700;
    }

    .card__due { margin-left: auto; }
    .card__due--overdue { color: var(--color-danger); font-weight: 600; }
  `],
})
export class KanbanTaskCardComponent {
  readonly task = input.required<Task>();
  readonly open = output<string>();

  protected initials(name: string): string {
    return name.split(/\s+/).slice(0, 2).map((p) => p[0] ?? '').join('').toUpperCase();
  }

  protected readonly isDueDateOverdueFn = isDueDateOverdue;

  protected formatDue(iso: string): string {
    const d = new Date(iso);
    return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  }

}
