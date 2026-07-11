import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CdkDrag, CdkDragDrop, CdkDragPlaceholder, CdkDropList } from '@angular/cdk/drag-drop';
import { BoardColumn } from '@core/models/board.models';
import { Task } from '@core/models/task.models';
import { Uuid } from '@core/models/common.models';
import { fromDateInputValue } from '@core/utils/due-date.util';
import { KanbanTaskCardComponent } from './kanban-task-card.component';

/**
 * One vertical column in the kanban. Holds a CDK drop list and a stack of task
 * cards. Emits user intents — never mutates state directly. The parent
 * {@link KanbanComponent} owns the BoardStore and handles all writes.
 */
@Component({
  selector: 'tf-kanban-column',
  standalone: true,
  imports: [CdkDropList, CdkDrag, CdkDragPlaceholder, KanbanTaskCardComponent, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="column">
      <header class="column__header">
        <div class="column__heading">
          <h2 class="column__title">{{ column().name }}</h2>
          <span class="column__count tf-muted">{{ tasks().length }}{{ column().wipLimit ? ' / ' + column().wipLimit : '' }}</span>
        </div>
        @if (canEdit()) {
          <button class="column__action" (click)="addingTask.set(!addingTask())"
                  [attr.aria-label]="addingTask() ? 'Cancel adding task' : 'Add task'">
            {{ addingTask() ? '×' : '+' }}
          </button>
        }
      </header>

      <div class="column__list"
           cdkDropList
           [cdkDropListData]="tasks()"
           (cdkDropListDropped)="onDrop($event)">
        @for (task of tasks(); track task.id) {
          <div class="column__item" cdkDrag [cdkDragData]="task">
            <tf-kanban-task-card [task]="task" (open)="openTask.emit($event)" />
            <div class="column__placeholder" *cdkDragPlaceholder></div>
          </div>
        } @empty {
          <div class="column__empty tf-muted">Drop tasks here</div>
        }
      </div>

      @if (addingTask()) {
        <form class="column__create" (ngSubmit)="emitCreate()">
          <textarea class="tf-input column__create-input"
                    placeholder="Task title — Enter to save, Esc to cancel"
                    [(ngModel)]="newTitle"
                    name="title"
                    rows="2"
                    (keydown.enter)="$event.preventDefault(); emitCreate()"
                    (keydown.escape)="cancelCreate()"
                    #titleInput
                    autofocus></textarea>
          <label class="column__create-due">
            <span class="tf-muted">Due date (optional)</span>
            <input type="date" class="tf-input" [(ngModel)]="newDueDate" name="dueDate" />
          </label>
          <div class="column__create-actions">
            <button class="tf-btn" type="submit" [disabled]="!newTitle.trim()">Add task</button>
            <button class="tf-btn tf-btn--ghost" type="button" (click)="cancelCreate()">Cancel</button>
          </div>
        </form>
      }
    </section>
  `,
  styles: [`
    :host { display: block; flex: 0 0 300px; }
    .column {
      display: flex; flex-direction: column;
      max-height: 100%;
      background: var(--color-surface-alt);
      border-radius: var(--radius-lg);
      border: 1px solid var(--color-border);
    }
    .column__header {
      display: flex; align-items: center; justify-content: space-between;
      padding: var(--space-3) var(--space-3) var(--space-2);
      border-bottom: 1px solid var(--color-border);
    }
    .column__heading { display: flex; align-items: baseline; gap: var(--space-2); }
    .column__title  { margin: 0; font-size: var(--font-size-md); font-weight: 600; }
    .column__count  { font-size: var(--font-size-xs); font-variant-numeric: tabular-nums; }
    .column__action {
      width: 24px; height: 24px;
      border: 0; background: transparent; color: var(--color-text-muted);
      border-radius: var(--radius-sm); font-size: 18px; line-height: 1;
      cursor: pointer;
    }
    .column__action:hover { background: var(--color-border); color: var(--color-text); }
    .column__list {
      display: flex; flex-direction: column; gap: var(--space-2);
      padding: var(--space-3);
      min-height: 60px;
      overflow-y: auto;
      flex: 1 1 auto;
    }
    .column__item { display: block; }
    .column__placeholder {
      background: var(--color-surface);
      border: 2px dashed var(--color-brand-500);
      border-radius: var(--radius-md);
      height: 56px;
      transition: transform 200ms ease;
    }
    .column__empty {
      padding: var(--space-4); text-align: center;
      border: 2px dashed var(--color-border); border-radius: var(--radius-md);
      font-size: var(--font-size-sm);
    }
    .column__create { padding: var(--space-2) var(--space-3) var(--space-3); display: flex; flex-direction: column; gap: var(--space-2); }
    .column__create-input { resize: vertical; }
    .column__create-due { display: flex; flex-direction: column; gap: var(--space-1); font-size: var(--font-size-xs); }
    .column__create-actions { display: flex; gap: var(--space-2); }

    /* CDK drag state styling */
    .cdk-drag-preview {
      box-shadow: var(--shadow-3);
      transform: rotate(2deg);
    }
    .cdk-drag-placeholder { opacity: 0; }
    .cdk-drop-list-dragging .column__item:not(.cdk-drag-placeholder) {
      transition: transform 200ms cubic-bezier(0, 0, 0.2, 1);
    }
  `],
})
export class KanbanColumnComponent {
  readonly column = input.required<BoardColumn>();
  readonly tasks  = input.required<Task[]>();
  readonly canEdit = input.required<boolean>();

  readonly openTask = output<Uuid>();
  readonly dropTask = output<{ taskId: Uuid; targetColumnId: Uuid; targetIndex: number }>();
  readonly createTask = output<{ columnId: Uuid; title: string; dueDate?: string }>();

  protected readonly addingTask = signal(false);
  protected newTitle = '';
  protected newDueDate = '';

  protected onDrop(event: CdkDragDrop<Task[]>): void {
    const movedTask = event.item.data as Task;
    // Same-column same-index drops are a no-op — skip the API call entirely.
    if (event.previousContainer === event.container && event.previousIndex === event.currentIndex) {
      return;
    }
    this.dropTask.emit({
      taskId:         movedTask.id,
      targetColumnId: this.column().id,
      targetIndex:    event.currentIndex,
    });
  }

  protected emitCreate(): void {
    const title = this.newTitle.trim();
    if (!title) return;
    const payload: { columnId: Uuid; title: string; dueDate?: string } = {
      columnId: this.column().id,
      title,
    };
    if (this.newDueDate) {
      payload.dueDate = fromDateInputValue(this.newDueDate);
    }
    this.createTask.emit(payload);
    this.newTitle = '';
    this.newDueDate = '';
    this.addingTask.set(false);
  }

  protected cancelCreate(): void {
    this.newTitle = '';
    this.newDueDate = '';
    this.addingTask.set(false);
  }
}
