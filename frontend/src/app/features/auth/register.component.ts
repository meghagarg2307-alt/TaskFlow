import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '@core/auth/auth.service';
import { formatHttpError } from '@core/api/http-error.util';

@Component({
  selector: 'tf-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="auth">
      <div class="auth__card tf-card">
        <h1 class="auth__title">Create your workspace</h1>
        <p class="auth__subtitle tf-muted">You'll be the admin of your new organization</p>

        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <label class="auth__field">
            <span>Full name</span>
            <input class="tf-input" formControlName="fullName" autocomplete="name" />
          </label>
          <label class="auth__field">
            <span>Work email</span>
            <input class="tf-input" type="email" formControlName="email" autocomplete="email" />
          </label>
          <label class="auth__field">
            <span>Password</span>
            <input class="tf-input" type="password" formControlName="password" autocomplete="new-password" />
          </label>
          <div class="auth__split">
            <label class="auth__field">
              <span>Organization name</span>
              <input class="tf-input" formControlName="organizationName" />
            </label>
            <label class="auth__field">
              <span>URL slug</span>
              <input class="tf-input" formControlName="organizationSlug"
                     placeholder="acme-inc" pattern="^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$" />
            </label>
          </div>

          @if (error(); as e) { <div class="auth__error">{{ e }}</div> }

          <button class="tf-btn auth__submit" [disabled]="loading() || form.invalid">
            @if (loading()) { <span>Creating…</span> } @else { <span>Create workspace</span> }
          </button>
        </form>

        <p class="auth__footer">
          Already have an account? <a routerLink="/login">Sign in</a>
        </p>
      </div>
    </div>
  `,
  styles: [`
    .auth { display: grid; place-items: center; height: 100%; padding: var(--space-6); background: var(--color-bg); }
    .auth__card { width: 100%; max-width: 520px; padding: var(--space-6); }
    .auth__title { margin: 0 0 var(--space-1); font-size: var(--font-size-2xl); }
    .auth__subtitle { margin: 0 0 var(--space-5); }
    .auth__field { display: flex; flex-direction: column; gap: var(--space-1); margin-bottom: var(--space-4); }
    .auth__split { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-3); }
    .auth__error {
      padding: var(--space-2) var(--space-3); margin-bottom: var(--space-3);
      background: color-mix(in srgb, var(--color-danger), transparent 88%);
      border: 1px solid color-mix(in srgb, var(--color-danger), transparent 70%);
      color: var(--color-danger); border-radius: var(--radius-md); font-size: var(--font-size-sm);
    }
    .auth__submit { width: 100%; }
    .auth__footer { margin: var(--space-5) 0 0; text-align: center; color: var(--color-text-muted); }
  `],
})
export class RegisterComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly loading = signal(false);
  protected readonly error   = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    email:            ['', [Validators.required, Validators.email]],
    password:         ['', [Validators.required, Validators.minLength(8)]],
    fullName:         ['', [Validators.required, Validators.minLength(2)]],
    organizationName: ['', [Validators.required, Validators.minLength(2)]],
    organizationSlug: ['', [Validators.required, Validators.pattern(/^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/)]],
  });

  submit(): void {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.error.set(null);
    this.authService.register(this.form.getRawValue()).subscribe({
      next: () => this.router.navigate(['/projects'], { replaceUrl: true }),
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(formatHttpError(err, 'Unable to create account.'));
      },
    });
  }
}
