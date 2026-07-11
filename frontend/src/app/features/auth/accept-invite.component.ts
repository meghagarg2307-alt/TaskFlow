import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { OrganizationApi } from '@core/api/organization.api';
import { formatHttpError } from '@core/api/http-error.util';
import { AuthService } from '@core/auth/auth.service';
import { AuthStore } from '@core/auth/auth.store';

@Component({
  selector: 'tf-accept-invite',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="auth">
      <div class="auth__card tf-card">
        <h1 class="auth__title">Join workspace</h1>
        <p class="auth__subtitle tf-muted">
          Paste the invitation link or token you received from your teammate.
        </p>

        @if (!auth.isAuthenticated()) {
          <p class="auth__notice">
            You need to
            <a routerLink="/login" [queryParams]="loginQuery()">sign in</a>
            or
            <a routerLink="/register" [queryParams]="loginQuery()">create an account</a>
            with the <strong>same email</strong> the invitation was sent to.
          </p>
        }

        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <label class="auth__field">
            <span>Invitation token</span>
            <input class="tf-input" formControlName="token" placeholder="Paste token from invite link" />
          </label>

          @if (error(); as e) {
            <div class="auth__error">{{ e }}</div>
          }

          @if (success(); as msg) {
            <div class="auth__success">{{ msg }}</div>
          }

          <button
            class="tf-btn auth__submit"
            type="submit"
            [disabled]="form.invalid || loading() || !auth.isAuthenticated()">
            @if (loading()) { <span>Joining…</span> } @else { <span>Accept invitation</span> }
          </button>
        </form>

        <p class="auth__footer">
          <a routerLink="/projects">Back to projects</a>
        </p>
      </div>
    </div>
  `,
  styles: [`
    .auth {
      display: grid; place-items: center; height: 100%;
      padding: var(--space-6); background: var(--color-bg);
    }
    .auth__card { width: 100%; max-width: 480px; padding: var(--space-6); }
    .auth__title { margin: 0 0 var(--space-1); font-size: var(--font-size-2xl); }
    .auth__subtitle { margin: 0 0 var(--space-4); }
    .auth__notice {
      margin: 0 0 var(--space-4); padding: var(--space-3);
      background: var(--color-surface-alt); border-radius: var(--radius-md);
      font-size: var(--font-size-sm);
    }
    .auth__field { display: flex; flex-direction: column; gap: var(--space-1); margin-bottom: var(--space-4); }
    .auth__error {
      padding: var(--space-2) var(--space-3); margin-bottom: var(--space-3);
      background: color-mix(in srgb, var(--color-danger), transparent 88%);
      border: 1px solid color-mix(in srgb, var(--color-danger), transparent 70%);
      color: var(--color-danger); border-radius: var(--radius-md); font-size: var(--font-size-sm);
    }
    .auth__success {
      padding: var(--space-2) var(--space-3); margin-bottom: var(--space-3);
      background: color-mix(in srgb, var(--color-success), transparent 88%);
      border: 1px solid color-mix(in srgb, var(--color-success), transparent 70%);
      color: var(--color-success); border-radius: var(--radius-md); font-size: var(--font-size-sm);
    }
    .auth__submit { width: 100%; }
    .auth__footer { margin: var(--space-5) 0 0; text-align: center; color: var(--color-text-muted); }
  `],
})
export class AcceptInviteComponent implements OnInit {
  protected readonly auth = inject(AuthStore);
  private readonly api = inject(OrganizationApi);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    token: ['', Validators.required],
  });

  ngOnInit(): void {
    const fromQuery = this.route.snapshot.queryParamMap.get('token');
    if (fromQuery) {
      this.form.patchValue({ token: fromQuery });
    }
  }

  protected loginQuery(): Record<string, string> {
    const token = this.form.getRawValue().token;
    return token ? { returnUrl: `/join?token=${encodeURIComponent(token)}` } : { returnUrl: '/join' };
  }

  protected submit(): void {
    if (this.form.invalid || !this.auth.isAuthenticated()) return;
    this.loading.set(true);
    this.error.set(null);
    this.success.set(null);

    const token = this.extractToken(this.form.getRawValue().token);

    this.api.acceptInvitation({ token }).subscribe({
      next: (res) => {
        this.authService.switchOrg({ organizationId: res.organizationId }).subscribe({
          next: () => {
            this.loading.set(false);
            this.success.set(`Welcome to ${res.organizationName}! Redirecting…`);
            setTimeout(() => this.router.navigate(['/projects'], { replaceUrl: true }), 1_200);
          },
          error: (err: HttpErrorResponse) => {
            this.loading.set(false);
            this.success.set(`Joined ${res.organizationName}. Switch workspace from the header menu.`);
            this.error.set(formatHttpError(err, 'Joined, but could not switch workspace automatically.'));
          },
        });
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(formatHttpError(err, 'Could not accept invitation.'));
      },
    });
  }

  /** Accept raw token or full /join?token=… URL pasted into the field. */
  private extractToken(value: string): string {
    const trimmed = value.trim();
    try {
      const url = new URL(trimmed, window.location.origin);
      const q = url.searchParams.get('token');
      if (q) return q;
    } catch {
      /* not a URL */
    }
    return trimmed;
  }
}
