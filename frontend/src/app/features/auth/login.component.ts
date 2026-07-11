import { ChangeDetectionStrategy, Component, signal, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '@core/auth/auth.service';
import { formatHttpError } from '@core/api/http-error.util';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
  selector: 'tf-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="auth">
      <div class="auth__card tf-card">
        <h1 class="auth__title">Welcome back</h1>
        <p class="auth__subtitle tf-muted">Sign in to your workspace</p>

        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <label class="auth__field">
            <span>Email</span>
            <input class="tf-input" type="email" autocomplete="username"
                   formControlName="email" placeholder="you@example.com" />
          </label>
          <label class="auth__field">
            <span>Password</span>
            <input class="tf-input" type="password" autocomplete="current-password"
                   formControlName="password" placeholder="••••••••" />
          </label>

          @if (error(); as e) {
            <div class="auth__error">{{ e }}</div>
          }

          <button class="tf-btn auth__submit" type="submit" [disabled]="loading() || form.invalid">
            @if (loading()) { <span>Signing in…</span> } @else { <span>Sign in</span> }
          </button>
        </form>

        <p class="auth__footer">
          New here? <a routerLink="/register">Create an account</a>
          · Have an invite? <a routerLink="/join">Join a workspace</a>
        </p>
      </div>
    </div>
  `,
  styles: [`
    .auth {
      display: grid; place-items: center; height: 100%;
      padding: var(--space-6); background: var(--color-bg);
    }
    .auth__card {
      width: 100%; max-width: 420px;
      padding: var(--space-6);
    }
    .auth__title { margin: 0 0 var(--space-1); font-size: var(--font-size-2xl); }
    .auth__subtitle { margin: 0 0 var(--space-5); }
    .auth__field { display: flex; flex-direction: column; gap: var(--space-1); margin-bottom: var(--space-4); }
    .auth__error {
      padding: var(--space-2) var(--space-3); margin-bottom: var(--space-3);
      background: color-mix(in srgb, var(--color-danger), transparent 88%);
      border: 1px solid color-mix(in srgb, var(--color-danger), transparent 70%);
      color: var(--color-danger);
      border-radius: var(--radius-md); font-size: var(--font-size-sm);
    }
    .auth__submit { width: 100%; }
    .auth__footer { margin: var(--space-5) 0 0; text-align: center; color: var(--color-text-muted); }
  `],
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly loading = signal(false);
  protected readonly error   = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    email:    ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  submit(): void {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.error.set(null);
    this.authService.login(this.form.getRawValue()).subscribe({
      next: () => {
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/';
        this.router.navigateByUrl(returnUrl, { replaceUrl: true });
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(formatHttpError(err, 'Unable to sign in. Please try again.'));
      },
    });
  }
}
