import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'tf-forbidden',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="forbidden">
      <h1>403 — Not allowed</h1>
      <p class="tf-muted">You don't have permission to view this page.</p>
      <a class="tf-btn tf-btn--ghost" routerLink="/projects">Back to projects</a>
    </div>
  `,
  styles: [`
    .forbidden { display: grid; place-items: center; gap: var(--space-3);
                 height: 100%; padding: var(--space-6); text-align: center; }
    h1 { margin: 0; font-size: var(--font-size-2xl); }
  `],
})
export class ForbiddenComponent {}
