import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { formatHttpError } from '@core/api/http-error.util';
import { BoardApi } from '@core/api/board.api';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { BoardSummary } from '@core/models/board.models';
import { AuthStore } from '@core/auth/auth.store';
import { Uuid } from '@core/models/common.models';

@Component({
  selector: 'tf-boards',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ConfirmDialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page">
      <header class="page__header">
        <div>
          <a class="tf-muted page__crumb" routerLink="/projects">← Projects</a>
          <h1>Boards</h1>
        </div>
        @if (canCreate()) {
          <button class="tf-btn" (click)="creating.set(!creating())">
            {{ creating() ? 'Cancel' : 'New board' }}
          </button>
        }
      </header>

      @if (error()) {
        <p class="page__error">{{ error() }}</p>
      }

      @if (creating()) {
        <form class="board-form tf-card" [formGroup]="form" (ngSubmit)="create()">
          <label class="board-form__field">
            <span>Name</span>
            <input class="tf-input" formControlName="name" placeholder="Sprint 14" />
          </label>
          <label class="board-form__field">
            <span>Description (optional)</span>
            <textarea class="tf-input" rows="2" formControlName="description"></textarea>
          </label>
          <div class="board-form__actions">
            <button class="tf-btn" [disabled]="form.invalid || saving()" type="submit">
              @if (saving()) { <span>Creating…</span> } @else { <span>Create board</span> }
            </button>
          </div>
        </form>
      }

      @if (loading()) {
        <p class="tf-muted">Loading…</p>
      } @else if (boards().length === 0) {
        <div class="empty tf-card">No boards in this project yet.</div>
      } @else {
        <ul class="board-grid">
          @for (b of boards(); track b.id) {
            <li class="board-grid__item tf-card">
              <div class="board-grid__head">
                <a class="board-grid__link" [routerLink]="['/boards', b.id]"><h2>{{ b.name }}</h2></a>
                @if (canDelete()) {
                  <button type="button" class="tf-btn tf-btn--ghost tf-btn--danger" (click)="askDelete(b, $event)">Delete</button>
                }
              </div>
              @if (b.description) {
                <p class="tf-muted">{{ b.description }}</p>
              }
            </li>
          }
        </ul>
      }
    </section>

    @if (confirmBoard(); as b) {
      <tf-confirm-dialog
        title="Move board to trash?"
        [message]="'“' + b.name + '” and all its columns and tasks will move to Trash for 30 days.'"
        warning="You can restore it from Trash before automatic permanent deletion."
        confirmLabel="Move to trash"
        (confirmed)="deleteBoard(b)"
        (cancelled)="confirmBoard.set(null)" />
    }
  `,
  styles: [`
    .page { padding: var(--space-5); max-width: 1100px; margin: 0 auto; }
    .page__header { display: flex; align-items: flex-end; justify-content: space-between; margin-bottom: var(--space-5); }
    .page__crumb { display: inline-block; margin-bottom: var(--space-1); font-size: var(--font-size-sm); }
    h1 { margin: 0; font-size: var(--font-size-2xl); }
    .board-form { padding: var(--space-4); margin-bottom: var(--space-5); display: grid; gap: var(--space-3); }
    .board-form__field { display: flex; flex-direction: column; gap: var(--space-1); }
    .board-form__actions { display: flex; justify-content: flex-end; }
    .empty { padding: var(--space-6); text-align: center; color: var(--color-text-muted); }
    .board-grid {
      list-style: none; padding: 0; margin: 0;
      display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: var(--space-4);
    }
    .board-grid__item {
      padding: var(--space-4);
      transition: transform 120ms ease, box-shadow 120ms ease;
    }
    .board-grid__item:hover { transform: translateY(-2px); box-shadow: var(--shadow-2); }
    .board-grid__head { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--space-2); }
    .board-grid__link { color: inherit; text-decoration: none; flex: 1; }
    .board-grid__item h2 { margin: 0 0 var(--space-1); font-size: var(--font-size-lg); }
    .page__error {
      margin-bottom: var(--space-3); padding: var(--space-2) var(--space-3);
      background: color-mix(in srgb, var(--color-danger), transparent 88%);
      border: 1px solid color-mix(in srgb, var(--color-danger), transparent 70%);
      color: var(--color-danger); border-radius: var(--radius-md); font-size: var(--font-size-sm);
    }
  `],
})
export class BoardsComponent implements OnInit {
  readonly projectId = input.required<Uuid>();

  private readonly api = inject(BoardApi);
  private readonly fb = inject(FormBuilder);
  protected readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly boards = signal<BoardSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly creating = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly canCreate = this.auth.canManage;
  protected readonly canDelete = this.auth.canManage;
  protected readonly confirmBoard = signal<BoardSummary | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
    description: [''],
  });

  ngOnInit(): void {
    this.reload();
  }

  private reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.listForProject(this.projectId()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (rows) => { this.boards.set(rows); this.loading.set(false); },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(formatHttpError(err, 'Could not load boards.'));
      },
    });
  }

  create(): void {
    if (this.form.invalid) return;
    this.saving.set(true);
    this.error.set(null);
    this.api.create(this.projectId(), this.form.getRawValue()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (b) => {
        this.boards.update((rows) => [b, ...rows]);
        this.creating.set(false);
        this.saving.set(false);
        this.form.reset({ name: '', description: '' });
        this.router.navigate(['/boards', b.id]);
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(formatHttpError(err, 'Could not create board.'));
      },
    });
  }

  protected askDelete(board: BoardSummary, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.confirmBoard.set(board);
  }

  protected deleteBoard(board: BoardSummary): void {
    this.confirmBoard.set(null);
    this.api.remove(board.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => this.boards.update((rows) => rows.filter((b) => b.id !== board.id)),
      error: (err: HttpErrorResponse) => this.error.set(formatHttpError(err, 'Could not delete board.')),
    });
  }
}
