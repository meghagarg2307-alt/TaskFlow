import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { CommentApi } from '@core/api/comment.api';
import { formatHttpError } from '@core/api/http-error.util';
import { OrganizationApi } from '@core/api/organization.api';
import { TaskApi } from '@core/api/task.api';
import { TraceIdService } from '@core/api/trace-id.service';
import { Task, TaskPriority, UpdateTaskRequest } from '@core/models/task.models';
import { Comment } from '@core/models/comment.models';
import { Uuid } from '@core/models/common.models';
import { Member } from '@core/models/org.models';
import { AuthStore } from '@core/auth/auth.store';
import {
  formatDueDateDisplay,
  fromDateInputValue,
  isDueDateOverdue,
  toDateInputValue,
} from '@core/utils/due-date.util';
import { BoardStore } from './board.store';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';

/**
 * Side panel that shows full task details, lets the user edit, and lists
 * comments. The parent controls visibility by setting/unsetting {@code taskId}.
 *
 * <p>Edits use the backend's optimistic-locking contract: we always send the
 * task's current {@code version}. If the server returns a 409, the parent's
 * snapshot reconciliation handles it (refetch on next event); for v1 we just
 * surface the message.</p>
 */
@Component({
  selector: 'tf-task-detail',
  standalone: true,
  imports: [FormsModule, ConfirmDialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (task(); as t) {
      <aside class="panel">
        <div class="panel__toolbar">
          <button class="panel__close tf-btn tf-btn--ghost" (click)="close.emit()" aria-label="Close">×</button>
          @if (canEdit()) {
            <button type="button" class="tf-btn tf-btn--ghost tf-btn--danger" (click)="confirmDelete.set(true)">
              Delete task
            </button>
          }
        </div>

        @if (actionError()) {
          <p class="panel__assignee-error">{{ actionError() }}</p>
        }

        <header class="panel__header">
          <input class="panel__title tf-input"
                 [(ngModel)]="titleDraft"
                 (blur)="commitTitleIfChanged()"
                 [disabled]="!canEdit()" />
        </header>

        <section class="panel__section">
          <label class="panel__label">Description</label>
          <textarea class="tf-input panel__textarea"
                    rows="5"
                    [(ngModel)]="descDraft"
                    (blur)="commitDescIfChanged()"
                    [disabled]="!canEdit()"></textarea>
        </section>

        <section class="panel__row">
          <div class="panel__field">
            <label class="panel__label">Priority</label>
            <select class="tf-input"
                    [ngModel]="t.priority"
                    (ngModelChange)="updatePriority($event)"
                    [disabled]="!canEdit()">
              @for (p of priorities; track p) {
                <option [value]="p">{{ p }}</option>
              }
            </select>
          </div>
          <div class="panel__field">
            <label class="panel__label">Due date</label>
            @if (canEdit()) {
              <div class="panel__due">
                <input
                  type="date"
                  class="tf-input"
                  [ngModel]="dueDateDraft"
                  (ngModelChange)="onDueDateChange($event)" />
                @if (task().dueDate) {
                  <button
                    type="button"
                    class="tf-btn tf-btn--ghost"
                    (click)="clearDueDate()"
                    [disabled]="patching()">
                    Clear
                  </button>
                }
              </div>
              @if (dueDateError(); as err) {
                <p class="panel__due-error">{{ err }}</p>
              }
            } @else if (task().dueDate) {
              <span class="panel__due-display" [class.panel__due-display--overdue]="isDueDateOverdueFn(task().dueDate!)">
                {{ formatDueDateDisplayFn(task().dueDate!) }}
              </span>
            } @else {
              <span class="tf-muted">No due date</span>
            }
          </div>
        </section>

        <section class="panel__row">
        <div class="panel__field">
  <label class="panel__label">Assignee</label>

  <div class="panel__assignee">

    @if (t.assignee; as a) {
      <span>{{ a.fullName }}</span>

      @if (canEdit()) {
        <button
          class="tf-btn tf-btn--ghost"
          type="button"
          (click)="clearAssignee()">
          Unassign
        </button>
      }
    } @else {
      <span class="tf-muted">Unassigned</span>
    }

    @if (canEdit()) {
      <select
        class="tf-input"
        #assigneeSelect
        (focus)="loadMembers(true)"
        (change)="assignTask(assigneeSelect)">

        <option value="">Select member</option>

        @if (membersLoading()) {
          <option disabled>Loading members…</option>
        } @else if (membersError()) {
          <option disabled>{{ membersError() }}</option>
        } @else if (members().length === 0) {
          <option disabled>No members found</option>
        } @else {
          @for (member of members(); track member.userId) {
            <option [value]="member.userId">
              {{ member.fullName || member.email }}
            </option>
          }
        }

      </select>
      @if (membersError()) {
        <p class="panel__assignee-error">{{ membersError() }}</p>
      }
      @if (assigneeError()) {
        <p class="panel__assignee-error">{{ assigneeError() }}</p>
      }
    }

  </div>
</div>
        </section>

        <section class="panel__section">
          <h3 class="panel__heading">Comments</h3>
          @if (commentsLoading()) {
            <p class="tf-muted">Loading…</p>
          } @else {
            <ul class="panel__comments">
              @for (c of comments(); track c.id) {
                <li class="panel__comment">
                  <div class="panel__comment-meta">
                    <strong>{{ c.author.fullName }}</strong>
                    <span class="tf-muted">{{ formatTime(c.createdAt) }}</span>
                  </div>
                  <p class="panel__comment-body">{{ c.body }}</p>
                </li>
              } @empty {
                <li class="tf-muted panel__comment--empty">No comments yet.</li>
              }
            </ul>
          }

          @if (canEdit()) {
            <form class="panel__comment-form" (ngSubmit)="postComment()">
              <textarea class="tf-input"
                        rows="2"
                        placeholder="Write a comment…"
                        [(ngModel)]="commentDraft"
                        name="body"></textarea>
              <button class="tf-btn" type="submit" [disabled]="!commentDraft.trim() || posting()">
                @if (posting()) { <span>Posting…</span> } @else { <span>Comment</span> }
              </button>
            </form>
          }
        </section>
      </aside>

      @if (confirmDelete()) {
        <tf-confirm-dialog
          title="Move task to trash?"
          [message]="'“' + t.title + '” will move to Trash for 30 days.'"
          confirmLabel="Move to trash"
          (confirmed)="deleteTask()"
          (cancelled)="confirmDelete.set(false)" />
      }
    }
  `,
  styles: [`
    :host {
      position: fixed; inset: 0;
      background: rgba(15, 23, 42, 0.45);
      display: grid; place-items: center;
      padding: var(--space-4);
      z-index: 100;
    }
    .panel {
      position: relative;
      width: 100%; max-width: 600px; max-height: 90vh;
      overflow-y: auto;
      background: var(--color-surface);
      border-radius: var(--radius-lg);
      box-shadow: var(--shadow-3);
      padding: var(--space-5);
    }
    .panel__toolbar {
      display: flex; justify-content: space-between; align-items: center;
      margin-bottom: var(--space-3);
    }
    .panel__close {
      width: 32px; height: 32px; padding: 0;
      font-size: 18px; line-height: 1;
    }
    .panel__header { margin-bottom: var(--space-4); }
    .panel__title { font-size: var(--font-size-lg); font-weight: 600; padding: var(--space-2); }
    .panel__section { margin-bottom: var(--space-5); }
    .panel__row { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-4); margin-bottom: var(--space-5); }
    .panel__field { display: flex; flex-direction: column; gap: var(--space-1); }
    .panel__label { font-size: var(--font-size-xs); font-weight: 600; color: var(--color-text-muted); text-transform: uppercase; letter-spacing: 0.5px; }
    .panel__textarea { resize: vertical; }
    .panel__assignee { display: flex; align-items: center; gap: var(--space-2); padding: var(--space-2) 0; }
    .panel__due { display: flex; align-items: center; gap: var(--space-2); flex-wrap: wrap; }
    .panel__due-display { font-size: var(--font-size-sm); }
    .panel__due-display--overdue { color: var(--color-danger); font-weight: 600; }
    .panel__due-error {
      margin: var(--space-1) 0 0; font-size: var(--font-size-xs);
      color: var(--color-danger);
    }
    .panel__assignee-error {
      margin: var(--space-1) 0 0; font-size: var(--font-size-xs);
      color: var(--color-danger);
    }
    .panel__heading { margin: 0 0 var(--space-3); font-size: var(--font-size-md); }
    .panel__comments { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: var(--space-3); }
    .panel__comment {
      padding: var(--space-3);
      background: var(--color-surface-alt);
      border-radius: var(--radius-md);
    }
    .panel__comment-meta { display: flex; gap: var(--space-2); font-size: var(--font-size-xs); margin-bottom: var(--space-1); }
    .panel__comment-body { margin: 0; font-size: var(--font-size-sm); white-space: pre-wrap; }
    .panel__comment--empty { padding: var(--space-3); }
    .panel__comment-form { display: flex; flex-direction: column; gap: var(--space-2); margin-top: var(--space-3); }
  `],
})
export class TaskDetailComponent {
  readonly task = input.required<Task>();
  readonly close = output<void>();
  /** Fired after a successful PATCH so the parent can upsert into its store. */
  readonly taskUpdated = output<Task>();
  readonly taskDeleted = output<Uuid>();
  /** Fired after a comment was posted so the parent could increment counters etc. */
  readonly commentAdded = output<Comment>();

  private readonly taskApi = inject(TaskApi);
  private readonly commentApi = inject(CommentApi);
  private readonly orgApi = inject(OrganizationApi);
  private readonly auth = inject(AuthStore);
  private readonly traces = inject(TraceIdService);
  // Provided at the KanbanComponent scope — found via Angular's hierarchical DI.
  private readonly board  = inject(BoardStore);
  protected readonly priorities: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

  protected titleDraft = '';
  protected descDraft = '';
  protected dueDateDraft = '';
  protected commentDraft = '';

  protected readonly comments = signal<Comment[]>([]);
  protected readonly commentsLoading = signal(false);
  protected readonly posting = signal(false);
  protected readonly patching = signal(false);
  protected readonly dueDateError = signal<string | null>(null);
  protected readonly members = signal<Member[]>([]);
  protected readonly membersLoading = signal(false);
  protected readonly membersError = signal<string | null>(null);
  protected readonly assigneeError = signal<string | null>(null);
  protected readonly actionError = signal<string | null>(null);
  protected readonly confirmDelete = signal(false);

  /** Authoritative version for the next PATCH — updated after every successful save. */
  private readonly taskVersion = signal(0);
  private readonly patchQueue: Array<{
    body: Omit<UpdateTaskRequest, 'expectedVersion'>;
    onSuccess?: () => void;
  }> = [];

  /** Whether the current viewer can edit the task — MEMBER+ can; could be tightened. */
  protected readonly canEdit = this.auth.canEditBoard;
  protected readonly isDueDateOverdueFn = isDueDateOverdue;
  protected readonly formatDueDateDisplayFn = formatDueDateDisplay;

  /** Tracks the last-loaded task id so we only reset drafts on panel switch — not on remote updates. */
  private boundTaskId: Uuid | null = null;

  constructor() {
    // Reset drafts + reload comments only when the user opens a DIFFERENT task.
    // For same-id updates (e.g. a remote WS edit while the panel is open), we
    // keep the user's drafts intact so in-progress typing isn't blown away.
   effect(() => {
  const t = this.task();
  // Never downgrade version — avoids stale parent input undoing an in-flight PATCH.
  this.taskVersion.update((v) => Math.max(v, t.version));

  if (t.id !== this.boundTaskId) {
    this.boundTaskId = t.id;
    this.titleDraft = t.title;
    this.descDraft  = t.description ?? '';
    this.dueDateDraft = toDateInputValue(t.dueDate);
    this.dueDateError.set(null);
    this.assigneeError.set(null);
    this.loadComments(t.id);
    this.loadMembers();
  }
});
  }

  private loadComments(taskId: Uuid): void {
    this.commentsLoading.set(true);
    this.commentApi.list(taskId).subscribe({
      next: (rows) => { this.comments.set(rows); this.commentsLoading.set(false); },
      error: () => this.commentsLoading.set(false),
    });
  }
  protected loadMembers(force = false): void {
    if (this.membersLoading()) return;
    if (!force && this.members().length > 0 && !this.membersError()) return;

    this.membersLoading.set(true);
    this.membersError.set(null);

    this.orgApi.listMembers().subscribe({
      next: (rows) => {
        this.members.set(rows);
        this.membersLoading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.members.set([]);
        this.membersLoading.set(false);
        this.membersError.set(formatHttpError(err, 'Could not load team members.'));
      },
    });
  }

  protected commitTitleIfChanged(): void {
    const t = this.task();
    if (this.titleDraft.trim() === t.title || !this.titleDraft.trim()) return;
    this.patch({ title: this.titleDraft.trim() });
  }

  protected commitDescIfChanged(): void {
    const t = this.task();
    if (this.descDraft === (t.description ?? '')) return;
    this.patch({ description: this.descDraft });
  }

  protected updatePriority(priority: TaskPriority): void {
    const t = this.task();
    if (t.priority === priority) return;
    this.patch({ priority });
  }

  protected onDueDateChange(value: string): void {
    this.dueDateDraft = value;
    this.commitDueDateIfChanged();
  }

  protected commitDueDateIfChanged(): void {
    const t = this.task();
    const current = toDateInputValue(t.dueDate);
    if (this.dueDateDraft === current) return;

    this.dueDateError.set(null);

    if (!this.dueDateDraft) {
      if (!t.dueDate) return;
      this.patch({ dueDate: null });
      return;
    }

    this.patch({ dueDate: fromDateInputValue(this.dueDateDraft) });
  }

  protected clearDueDate(): void {
    const t = this.task();
    if (!t.dueDate) return;
    this.dueDateDraft = '';
    this.dueDateError.set(null);
    this.patch({ dueDate: null });
  }

  protected clearAssignee(): void {
    this.assigneeError.set(null);
    this.patch({ assigneeId: null });
  }
  protected assignTask(select: HTMLSelectElement): void {
    const assigneeId = select.value;
    if (!assigneeId) return;

    this.assigneeError.set(null);
    this.patch(
      { assigneeId },
      () => { select.value = ''; },
    );
  }

  /**
   * Queued PATCH so rapid edits (e.g. Clear then pick a new date) always send the
   * latest {@code expectedVersion} from the previous successful save.
   */
  private patch(body: Omit<UpdateTaskRequest, 'expectedVersion'>, onSuccess?: () => void): void {
    this.patchQueue.push({ body, onSuccess });
    this.drainPatchQueue();
  }

  private drainPatchQueue(): void {
    if (this.patching() || this.patchQueue.length === 0) return;

    const { body, onSuccess } = this.patchQueue.shift()!;
    const req: UpdateTaskRequest = { ...body, expectedVersion: this.taskVersion() };
    this.patching.set(true);

    const traceId = this.traces.generate();
    this.board.registerInFlight(traceId, () => { /* no pre-server UI to revert */ });

    this.taskApi.update(this.task().id, req, traceId).subscribe({
      next: (updated) => {
        this.taskVersion.set(updated.version);
        if ('dueDate' in body) {
          this.dueDateDraft = toDateInputValue(updated.dueDate);
          this.dueDateError.set(null);
        }
        if ('assigneeId' in body) {
          this.assigneeError.set(null);
        }
        this.taskUpdated.emit(updated);
        this.board.clearInFlight(traceId, 1_500);
        this.patching.set(false);
        onSuccess?.();
        this.drainPatchQueue();
      },
      error: (err: HttpErrorResponse) => {
        console.warn('Task update failed', err);
        this.board.clearInFlight(traceId);
        this.patching.set(false);

        if (err.status === 409) {
          this.refreshTaskVersion(() => {
            const retryMsg = 'Syncing latest version and retrying…';
            if ('dueDate' in body) this.dueDateError.set(retryMsg);
            if ('assigneeId' in body) this.assigneeError.set(retryMsg);
            this.patchQueue.unshift({ body, onSuccess });
            this.drainPatchQueue();
          });
          return;
        }

        if ('dueDate' in body) {
          this.dueDateDraft = toDateInputValue(this.task().dueDate);
          this.dueDateError.set(formatHttpError(err, 'Could not save due date.'));
        }
        if ('assigneeId' in body) {
          this.assigneeError.set(formatHttpError(err, 'Could not assign member.'));
        }
        this.drainPatchQueue();
      },
    });
  }

  /** Re-sync version (and board) after a 409 so the next queued PATCH can succeed. */
  private refreshTaskVersion(done: () => void): void {
    this.taskApi.get(this.task().id).subscribe({
      next: (fresh) => {
        this.taskVersion.set(fresh.version);
        this.taskUpdated.emit(fresh);
        done();
      },
      error: () => done(),
    });
  }

  protected deleteTask(): void {
    this.confirmDelete.set(false);
    const id = this.task().id;
    this.taskApi.remove(id).subscribe({
      next: () => {
        this.taskDeleted.emit(id);
        this.close.emit();
      },
      error: (err: HttpErrorResponse) => {
        this.actionError.set(formatHttpError(err, 'Could not delete task.'));
      },
    });
  }

  protected postComment(): void {
    const body = this.commentDraft.trim();
    if (!body) return;
    this.posting.set(true);
    this.commentApi.create(this.task().id, { body }).subscribe({
      next: (c) => {
        this.comments.update((rows) => [...rows, c]);
        this.commentAdded.emit(c);
        this.commentDraft = '';
        this.posting.set(false);
      },
      error: () => this.posting.set(false),
    });
  }

  protected formatTime(iso: string): string {
    return new Date(iso).toLocaleString();
  }
}
