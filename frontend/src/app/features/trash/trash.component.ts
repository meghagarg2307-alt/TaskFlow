import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { formatHttpError } from '@core/api/http-error.util';
import { TrashApi } from '@core/api/trash.api';
import { AuthStore } from '@core/auth/auth.store';
import { TrashItem, TrashResourceType } from '@core/models/trash.models';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';

type PendingAction = 'restore' | 'purge';

@Component({
  selector: 'tf-trash',
  standalone: true,
  imports: [ConfirmDialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page__header">
        <h1 class="page__title">Trash</h1>
        <p class="tf-muted">
          Deleted items are kept for 30 days, then removed permanently by an automatic cleanup job.
        </p>
      </header>

      @if (error()) {
        <p class="page__error">{{ error() }}</p>
      }

      @if (loading()) {
        <p class="tf-muted">Loading trash…</p>
      } @else if (items().length === 0) {
        <div class="tf-card page__empty">
          <p>Trash is empty.</p>
        </div>
      } @else {
        <div class="trash-list">
          @for (item of items(); track item.resourceType + item.id) {
            <article class="trash-item tf-card">
              <div class="trash-item__meta">
                <span class="trash-item__type">{{ typeLabel(item.resourceType) }}</span>
                <h2 class="trash-item__name">{{ item.name }}</h2>
                @if (item.parentName) {
                  <p class="tf-muted">In {{ item.parentName }}</p>
                }
                <p class="tf-muted trash-item__dates">
                  Deleted {{ formatDate(item.deletedAt) }}
                  @if (item.deletedByName) { · by {{ item.deletedByName }} }
                </p>
                <p class="trash-item__countdown">
                  @if (item.daysUntilPermanentDeletion === 0) {
                    <strong>Eligible for permanent deletion today</strong>
                  } @else {
                    {{ item.daysUntilPermanentDeletion }} day(s) until auto-deletion
                  }
                </p>
              </div>
              @if (auth.isAdmin()) {
                <div class="trash-item__actions">
                  <button type="button" class="tf-btn tf-btn--ghost" (click)="askRestore(item)">
                    Restore
                  </button>
                  <button type="button" class="tf-btn tf-btn--danger" (click)="askPurge(item)">
                    Delete permanently
                  </button>
                </div>
              }
            </article>
          }
        </div>
      }
    </div>

    @if (confirm(); as c) {
      <tf-confirm-dialog
        [title]="c.title"
        [message]="c.message"
        [warning]="c.warning"
        [confirmLabel]="c.confirmLabel"
        [danger]="c.danger"
        (confirmed)="runConfirm()"
        (cancelled)="confirm.set(null)" />
    }
  `,
  styles: [`
    .page { max-width: 52rem; margin: 0 auto; padding: var(--space-5); }
    .page__header { margin-bottom: var(--space-5); }
    .page__title { margin: 0 0 var(--space-2); }
    .page__error { color: var(--color-danger, #dc3545); }
    .page__empty { padding: var(--space-6); text-align: center; }
    .trash-list { display: flex; flex-direction: column; gap: var(--space-3); }
    .trash-item {
      display: flex; align-items: flex-start; justify-content: space-between;
      gap: var(--space-4); padding: var(--space-4);
    }
    .trash-item__type {
      font-size: var(--font-size-xs); text-transform: uppercase;
      letter-spacing: 0.04em; color: var(--color-text-muted);
    }
    .trash-item__name { margin: var(--space-1) 0; font-size: var(--font-size-md); }
    .trash-item__countdown { margin: var(--space-2) 0 0; font-size: var(--font-size-sm); }
    .trash-item__actions { display: flex; flex-direction: column; gap: var(--space-2); flex-shrink: 0; }
  `],
})
export class TrashComponent implements OnInit {
  private readonly trashApi = inject(TrashApi);
  protected readonly auth = inject(AuthStore);

  protected readonly items = signal<TrashItem[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly confirm = signal<{
    title: string;
    message: string;
    warning: string | null;
    confirmLabel: string;
    danger: boolean;
    item: TrashItem;
    action: PendingAction;
  } | null>(null);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.trashApi.list(undefined, 0, 50).subscribe({
      next: (page) => {
        this.items.set(page.content);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(formatHttpError(err, 'Could not load trash.'));
        this.loading.set(false);
      },
    });
  }

  protected typeLabel(type: TrashResourceType): string {
    const labels: Record<TrashResourceType, string> = {
      WORKSPACE: 'Workspace',
      PROJECT: 'Project',
      BOARD: 'Board',
      TASK: 'Task',
    };
    return labels[type];
  }

  protected formatDate(iso: string): string {
    return new Date(iso).toLocaleString();
  }

  protected askRestore(item: TrashItem): void {
    this.confirm.set({
      title: 'Restore item?',
      message: `Restore "${item.name}" and all related data that was deleted with it.`,
      warning: null,
      confirmLabel: 'Restore',
      danger: false,
      item,
      action: 'restore',
    });
  }

  protected askPurge(item: TrashItem): void {
    this.confirm.set({
      title: 'Delete permanently?',
      message: `"${item.name}" will be removed forever. This cannot be undone.`,
      warning: 'All related data (tasks, comments, boards, etc.) will be permanently destroyed.',
      confirmLabel: 'Delete forever',
      danger: true,
      item,
      action: 'purge',
    });
  }

  protected runConfirm(): void {
    const c = this.confirm();
    if (!c) return;
    const { item, action } = c;
    this.confirm.set(null);

    const req = action === 'restore'
      ? this.trashApi.restore(item.resourceType, item.id)
      : this.trashApi.permanentlyDelete(item.resourceType, item.id);

    req.subscribe({
      next: () => this.load(),
      error: (err: HttpErrorResponse) => {
        this.error.set(formatHttpError(err, action === 'restore' ? 'Restore failed.' : 'Permanent delete failed.'));
      },
    });
  }
}
