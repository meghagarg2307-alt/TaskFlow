import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

/**
 * Lightweight confirmation overlay — no external dialog library.
 */
@Component({
  selector: 'tf-confirm-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="confirm-backdrop" role="dialog" aria-modal="true">
      <div class="confirm-card tf-card">
        <h2 class="confirm-card__title">{{ title() }}</h2>
        <p class="confirm-card__message">{{ message() }}</p>
        @if (warning()) {
          <p class="confirm-card__warning">{{ warning() }}</p>
        }
        <div class="confirm-card__actions">
          <button type="button" class="tf-btn tf-btn--ghost" (click)="cancelled.emit()">
            {{ cancelLabel() }}
          </button>
          <button
            type="button"
            class="tf-btn"
            [class.tf-btn--danger]="danger()"
            (click)="confirmed.emit()">
            {{ confirmLabel() }}
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .confirm-backdrop {
      position: fixed;
      inset: 0;
      z-index: 1000;
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(0, 0, 0, 0.45);
      padding: var(--space-4);
    }
    .confirm-card {
      max-width: 28rem;
      width: 100%;
      padding: var(--space-5);
    }
    .confirm-card__title { margin: 0 0 var(--space-2); font-size: var(--font-size-lg); }
    .confirm-card__message { margin: 0 0 var(--space-3); color: var(--color-text); }
    .confirm-card__warning {
      margin: 0 0 var(--space-4);
      padding: var(--space-2) var(--space-3);
      border-radius: var(--radius-sm);
      background: rgba(220, 53, 69, 0.12);
      color: var(--color-danger, #dc3545);
      font-size: var(--font-size-sm);
    }
    .confirm-card__actions {
      display: flex;
      justify-content: flex-end;
      gap: var(--space-2);
    }
  `],
})
export class ConfirmDialogComponent {
  readonly title = input.required<string>();
  readonly message = input.required<string>();
  readonly warning = input<string | null>(null);
  readonly confirmLabel = input('Confirm');
  readonly cancelLabel = input('Cancel');
  readonly danger = input(true);

  readonly confirmed = output<void>();
  readonly cancelled = output<void>();
}
