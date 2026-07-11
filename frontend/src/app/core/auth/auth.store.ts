import { Injectable, computed, signal } from '@angular/core';
import { AuthResponse, OrganizationSummary, UserSummary } from '../models/auth.models';
import { hasMinRole } from './role.util';

/**
 * Signal-based store for "who am I and which org am I in" state.
 *
 * <p>Components read via the readonly signals; only {@link AuthService} writes.
 * Computed signals (e.g. {@link isAuthenticated}) update reactively without
 * subscriptions — what NgRx would call a selector, done with one line of code.</p>
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly _user = signal<UserSummary | null>(null);
  private readonly _activeOrg = signal<OrganizationSummary | null>(null);
  private readonly _organizations = signal<OrganizationSummary[]>([]);

  readonly user = this._user.asReadonly();
  readonly activeOrg = this._activeOrg.asReadonly();
  readonly organizations = this._organizations.asReadonly();

  readonly isAuthenticated = computed(() => this._user() !== null);
  readonly activeRole = computed(() => this._activeOrg()?.role ?? null);

  /** ADMIN only — trash restore/purge, workspace settings. */
  readonly isAdmin = computed(() => this.activeRole() === 'ADMIN');

  /** ADMIN or MANAGER — projects, boards, invites. */
  readonly canManage = computed(() => hasMinRole(this.activeRole(), 'MANAGER'));

  /** ADMIN, MANAGER, or MEMBER — tasks, columns, comments on boards. */
  readonly canEditBoard = computed(() => hasMinRole(this.activeRole(), 'MEMBER'));

  hydrate(response: AuthResponse): void {
    this._user.set(response.user);
    this._activeOrg.set(response.activeOrganization);
    this._organizations.set(response.organizations);
  }

  clear(): void {
    this._user.set(null);
    this._activeOrg.set(null);
    this._organizations.set([]);
  }

  /** Replace the active org locally (after a successful /auth/switch-org call). */
  setActiveOrg(org: OrganizationSummary): void {
    this._activeOrg.set(org);
  }
}
