import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '@core/auth/auth.service';
import { AuthStore } from '@core/auth/auth.store';
import { StompService } from '@core/realtime/stomp.service';

/**
 * Authenticated shell — top bar with org switcher + user menu + sign-out, plus
 * the router outlet for nested feature routes. Owns the lifecycle of the global
 * STOMP connection: connect on mount, disconnect on logout.
 */
@Component({
  selector: 'tf-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="shell__topbar">
      <a routerLink="/projects" class="shell__brand">TaskFlow</a>

      <nav class="shell__nav">
        <a routerLink="/projects" routerLinkActive="shell__nav-link--active" class="shell__nav-link">Projects</a>
        <a routerLink="/team" routerLinkActive="shell__nav-link--active" class="shell__nav-link">Team</a>
        @if (auth.isAdmin()) {
          <a routerLink="/trash" routerLinkActive="shell__nav-link--active" class="shell__nav-link">Trash</a>
        }
      </nav>

      @if (auth.activeOrg(); as org) {
        <div class="shell__org">
          @if (auth.organizations().length > 1) {
            <select
              class="shell__org-select tf-input"
              [value]="org.id"
              (change)="onOrgChange($event)">
              @for (o of auth.organizations(); track o.id) {
                <option [value]="o.id">{{ o.name }}</option>
              }
            </select>
          } @else {
            <span class="shell__org-name">{{ org.name }}</span>
          }
          <span class="shell__role">{{ org.role }}</span>
        </div>
      }

      <span class="shell__spacer"></span>

      @if (rt.connected()) {
        <span class="shell__indicator shell__indicator--ok" title="Real-time connected">●</span>
      } @else {
        <span class="shell__indicator shell__indicator--down" title="Real-time disconnected">●</span>
      }

      @if (auth.user(); as user) {
        <div class="shell__user">{{ user.fullName }}</div>
        <button class="tf-btn tf-btn--ghost" (click)="signOut()">Sign out</button>
      }
    </header>

    <main class="shell__main">
      <router-outlet />
    </main>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; height: 100%; }
    .shell__topbar {
      display: flex; align-items: center; gap: var(--space-4);
      padding: var(--space-3) var(--space-5);
      background: var(--color-surface);
      border-bottom: 1px solid var(--color-border);
      box-shadow: var(--shadow-1);
    }
    .shell__brand {
      font-weight: 700; font-size: var(--font-size-lg); color: var(--color-brand-600);
    }
    .shell__nav { display: flex; gap: var(--space-1); }
    .shell__nav-link {
      padding: var(--space-1) var(--space-3); font-size: var(--font-size-sm);
      color: var(--color-text-muted); border-radius: var(--radius-md);
    }
    .shell__nav-link:hover { color: var(--color-text); background: var(--color-surface-alt); }
    .shell__nav-link--active { color: var(--color-brand-600); font-weight: 600; }
    .shell__org {
      display: flex; align-items: center; gap: var(--space-2);
      padding: var(--space-1) var(--space-3);
      background: var(--color-surface-alt);
      border-radius: var(--radius-md);
      font-size: var(--font-size-sm);
    }
    .shell__org-select {
      font-size: var(--font-size-sm); padding: var(--space-1) var(--space-2);
      min-width: 140px;
    }
    .shell__role {
      font-size: 11px; text-transform: uppercase; letter-spacing: 0.5px;
      color: var(--color-text-muted);
    }
    .shell__spacer { flex: 1 1 auto; }
    .shell__indicator { font-size: 10px; }
    .shell__indicator--ok   { color: var(--color-success); }
    .shell__indicator--down { color: var(--color-text-muted); }
    .shell__user { font-size: var(--font-size-sm); color: var(--color-text-muted); }
    .shell__main { flex: 1 1 auto; overflow: auto; }
  `],
})
export class ShellComponent implements OnInit {
  protected readonly auth = inject(AuthStore);
  protected readonly rt   = inject(StompService);
  private   readonly authService = inject(AuthService);
  private   readonly router      = inject(Router);

  ngOnInit(): void {
    // Open the WebSocket once we're authenticated. Disconnect happens on sign-out.
    this.rt.connect();
  }

  protected onOrgChange(event: Event): void {
    const orgId = (event.target as HTMLSelectElement).value;
    if (!orgId || orgId === this.auth.activeOrg()?.id) return;
    this.authService.switchOrg({ organizationId: orgId }).subscribe({
      next: () => this.router.navigate(['/projects'], { replaceUrl: true }),
    });
  }

  signOut(): void {
    this.authService.logout().subscribe({
      complete: () => {
        this.rt.disconnect();
        this.router.navigate(['/login'], { replaceUrl: true });
      },
    });
  }
}
