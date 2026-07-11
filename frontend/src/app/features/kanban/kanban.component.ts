import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CdkDropListGroup } from '@angular/cdk/drag-drop';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';

import { formatHttpError } from '@core/api/http-error.util';
import { AuthStore } from '@core/auth/auth.store';
import { BoardApi } from '@core/api/board.api';
import { TaskApi } from '@core/api/task.api';
import { TraceIdService } from '@core/api/trace-id.service';
import { StompService } from '@core/realtime/stomp.service';
import { Uuid } from '@core/models/common.models';
import { Task } from '@core/models/task.models';
import { BoardEvent } from '@core/models/event.models';

import { BoardStore } from './board.store';
import { KanbanColumnComponent } from './kanban-column.component';
import { TaskDetailComponent } from './task-detail.component';

/**
 * The kanban board view.
 *
 * <p>Responsibilities (the only place these all converge):</p>
 * <ol>
 *   <li>Loads the board snapshot once on entry.</li>
 *   <li>Owns the per-board {@link BoardStore} (via the component-scoped provider).</li>
 *   <li>Subscribes to STOMP events for this board and reconciles them.</li>
 *   <li>Translates UI drag-drop events into optimistic store mutations + API
 *       calls with pre-tagged trace ids.</li>
 *   <li>Routes task creation / detail edits through the same machinery.</li>
 * </ol>
 *
 * <p>Component-scoped {@link BoardStore} provider means leaving the route tears
 * the store down — no stale state survives navigation.</p>
 */
@Component({
  selector: 'tf-kanban',
  standalone: true,
  imports: [
    CdkDropListGroup,
    KanbanColumnComponent,
    TaskDetailComponent,
    FormsModule,
    RouterLink,
  ],
  providers: [BoardStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading()) {
      <p class="board__status tf-muted">Loading board…</p>
    } @else if (loadError()) {
      <div class="board__status">
        <p class="tf-muted">{{ loadError() }}</p>
        <a class="tf-btn tf-btn--ghost" routerLink="/projects">Back to projects</a>
      </div>
    } @else if (board.loaded()) {
      <header class="board__header">
        <div>
          <a class="tf-muted board__crumb" [routerLink]="['/projects', board.snapshot()!.projectId, 'boards']">
            ← Back to boards
          </a>
          <h1 class="board__title">{{ board.snapshot()!.name }}</h1>
        </div>

        @if (canEdit()) {
          @if (addingColumn()) {
            <form class="board__add-col" (ngSubmit)="createColumn()">
              <input class="tf-input" [(ngModel)]="newColName" name="name"
                     placeholder="Column name" autofocus />
              <button class="tf-btn" type="submit" [disabled]="!newColName.trim()">Add</button>
              <button class="tf-btn tf-btn--ghost" type="button" (click)="cancelAddColumn()">Cancel</button>
            </form>
          } @else {
            <button class="tf-btn" (click)="addingColumn.set(true)">+ Column</button>
          }
        }
      </header>

      <div class="board__columns" cdkDropListGroup>
        @for (col of board.columns(); track col.id) {
          <tf-kanban-column
            [column]="col"
            [tasks]="(board.tasksByColumn())[col.id]"
            [canEdit]="canEdit()"
            (dropTask)="onDrop($event)"
            (openTask)="onOpenTask($event)"
            (createTask)="onCreateTask($event)" />
        } @empty {
          <div class="board__empty tf-card">
            <p>No columns yet.</p>
            @if (canEdit()) { <p class="tf-muted">Add a column to start tracking tasks.</p> }
          </div>
        }
      </div>

      @if (openTaskId(); as openId) {
        @if (taskById(openId); as selectedTask) {
          <tf-task-detail
            [task]="selectedTask"
            (close)="openTaskId.set(null)"
            (taskUpdated)="board.upsertTask($event)"
            (taskDeleted)="onTaskDeleted($event)" />
        }
      }
    }
  `,
  styles: [`
    :host { display: flex; flex-direction: column; height: 100%; padding: var(--space-4); background: var(--color-bg); }
    .board__status { padding: var(--space-6); text-align: center; }
    .board__header { display: flex; align-items: flex-end; justify-content: space-between; gap: var(--space-3); margin-bottom: var(--space-4); }
    .board__crumb { display: inline-block; margin-bottom: var(--space-1); font-size: var(--font-size-sm); }
    .board__title { margin: 0; font-size: var(--font-size-xl); }
    .board__add-col { display: flex; gap: var(--space-2); align-items: center; }
    .board__columns {
      display: flex; gap: var(--space-4);
      flex: 1 1 auto;
      overflow-x: auto; overflow-y: hidden;
      padding-bottom: var(--space-3);
      align-items: stretch;
    }
    .board__empty {
      padding: var(--space-6); text-align: center; max-width: 480px; margin: var(--space-5) auto;
    }
  `],
})
export class KanbanComponent implements OnInit {
  readonly boardId = input.required<Uuid>();

  protected readonly board = inject(BoardStore);
  private   readonly boardApi = inject(BoardApi);
  private   readonly taskApi  = inject(TaskApi);
  private   readonly stomp    = inject(StompService);
  private   readonly traces   = inject(TraceIdService);
  private   readonly auth     = inject(AuthStore);
  private   readonly destroyRef = inject(DestroyRef);

  protected readonly loading   = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly openTaskId = signal<Uuid | null>(null);
  protected readonly addingColumn = signal(false);
  protected newColName = '';

  protected readonly canEdit = this.auth.canEditBoard;

  /** Returns the task currently bound to the detail panel — null if it was removed. */
  protected readonly taskById = (id: Uuid): Task | null => {
    const s = this.board.snapshot();
    return s?.tasks.find((t) => t.id === id) ?? null;
  };

  ngOnInit(): void {
    this.loadSnapshot();
    this.subscribeToRealtime();
  }

  // ================================================================== loading

  private loadSnapshot(): void {
    this.loading.set(true);
    this.boardApi.getSnapshot(this.boardId()).subscribe({
      next: (snapshot) => {
        this.board.setSnapshot(snapshot);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.loadError.set(formatHttpError(err, 'Failed to load board.'));
      },
    });
  }

  private subscribeToRealtime(): void {
    this.stomp.connect();
    this.stomp.subscribeBoard(this.boardId())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => this.handleRemoteEvent(event));
  }

  // =================================================== drag-drop optimistic

  protected onDrop(payload: {
    taskId: Uuid;
    targetColumnId: Uuid;
    targetIndex: number;
  }): void {
    const task = this.taskById(payload.taskId);
    if (!task) return;

    // 1. Optimistic local move — neighbors computed against the resulting bucket.
    const moveResult = this.board.moveTaskOptimistic({
      taskId:         payload.taskId,
      targetColumnId: payload.targetColumnId,
      targetIndex:    payload.targetIndex,
    });
    if (!moveResult) return;

    // 2. Pre-generate the trace id so we can recognize our own STOMP echo.
    const traceId = this.traces.generate();
    this.board.registerInFlight(traceId, moveResult.revert);

    // 3. Server call carries the pre-set trace id (interceptor honors it).
    this.taskApi.move(payload.taskId, {
      targetColumnId:  payload.targetColumnId,
      beforeTaskId:    moveResult.beforeTaskId,
      afterTaskId:     moveResult.afterTaskId,
      expectedVersion: task.version,
    }, traceId).subscribe({
      next: (updated) => {
        // Replace the optimistic placeholder with the authoritative server task
        // (correct position, bumped version). Keep the trace registered for a
        // short window so the late WS echo is still recognized as ours.
        this.board.upsertTask(updated);
        this.board.clearInFlight(traceId, 1_500);
      },
      error: () => {
        // 409 (version), 404 (deleted), network — all roll back the UI.
        this.board.revert(traceId);
      },
    });
  }

  // ================================================== task creation + detail

  protected onOpenTask(taskId: Uuid): void {
    this.openTaskId.set(taskId);
  }

  protected onTaskDeleted(taskId: Uuid): void {
    this.board.removeTask(taskId);
    this.openTaskId.set(null);
  }

  protected onCreateTask(payload: { columnId: Uuid; title: string; dueDate?: string }): void {
    // Tag the create with a trace id so the WS echo is absorbed silently —
    // otherwise we'd refetch the just-created task immediately.
    const traceId = this.traces.generate();
    this.board.registerInFlight(traceId, () => { /* nothing to revert pre-server */ });

    this.taskApi.create(this.boardId(), {
      columnId: payload.columnId,
      title:    payload.title,
      ...(payload.dueDate ? { dueDate: payload.dueDate } : {}),
    }, traceId).subscribe({
      next: (task) => {
        this.board.upsertTask(task);
        this.board.clearInFlight(traceId, 1_500);
      },
      error: () => this.board.clearInFlight(traceId),
    });
  }

  // ============================================================== columns

  protected createColumn(): void {
    const name = this.newColName.trim();
    if (!name) return;
    const traceId = this.traces.generate();
    this.board.registerInFlight(traceId, () => { /* no pre-server state to revert */ });

    this.boardApi.createColumn(this.boardId(), { name }, traceId).subscribe({
      next: (column) => {
        this.board.upsertColumn(column);
        this.newColName = '';
        this.addingColumn.set(false);
        this.board.clearInFlight(traceId, 1_500);
      },
      error: () => this.board.clearInFlight(traceId),
    });
  }

  protected cancelAddColumn(): void {
    this.newColName = '';
    this.addingColumn.set(false);
  }

  // =========================================================== remote events

  private handleRemoteEvent(event: BoardEvent): void {
    // If the echo carries our own trace id, we already applied this locally.
    if (this.board.isOwnEcho(event.traceId)) {
      // Keep the trace registered briefly so a late HTTP-success upsert can also
      // recognize it as ours, then clear it.
      this.board.clearInFlight(event.traceId!, 1_500);
      return;
    }

    switch (event.type) {
      case 'TASK_CREATED':
      case 'TASK_UPDATED':
      case 'TASK_MOVED':
      case 'TASK_ASSIGNED':
      case 'TASK_UNASSIGNED': {
        if (event.taskId) this.refetchTask(event.taskId);
        break;
      }
      case 'TASK_DELETED': {
        if (event.taskId) this.board.removeTask(event.taskId);
        break;
      }
      case 'COLUMN_CREATED':
      case 'COLUMN_UPDATED':
      case 'COLUMN_DELETED':
      case 'BOARD_UPDATED': {
        // Columns change infrequently — refetching the whole snapshot is the
        // simplest correct approach, and avoids the SPA decoding the activity
        // payload format which couples it to backend internals.
        this.refetchSnapshot();
        break;
      }
      case 'COMMENT_ADDED':
      case 'COMMENT_DELETED':
      case 'BOARD_DELETED':
      case 'BOARD_CREATED':
      case 'PROJECT_CREATED':
      case 'PROJECT_UPDATED':
      case 'PROJECT_DELETED':
      case 'MEMBER_INVITED':
      case 'MEMBER_JOINED':
      case 'MEMBER_REMOVED':
      case 'MEMBER_ROLE_CHANGED':
        // Irrelevant to the kanban view — ignored.
        break;
      default:
        console.debug('Unhandled board event type', event.type);
    }
  }

  private refetchTask(taskId: Uuid): void {
    this.taskApi.get(taskId).subscribe({
      next: (task) => this.board.upsertTask(task),
      error: (err) => {
        // 404 means it was deleted — best-effort removal.
        if (err?.status === 404) this.board.removeTask(taskId);
      },
    });
  }

  private refetchSnapshot(): void {
    this.boardApi.getSnapshot(this.boardId()).subscribe({
      next: (s) => this.board.setSnapshot(s),
    });
  }
}
