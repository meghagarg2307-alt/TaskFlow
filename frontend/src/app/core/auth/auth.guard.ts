import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthStore } from './auth.store';
import { OrganizationRole } from '../models/auth.models';
import { ROLE_RANK } from './role.util';

/** Allows the route iff the user is logged in; otherwise → /login. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  if (auth.isAuthenticated()) return true;
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/**
 * Role-gated guard. Use as:
 *   { canActivate: [authGuard, roleGuard(['ADMIN','MANAGER'])] }
 *
 * Mirrors the backend's role hierarchy (ADMIN > MANAGER > MEMBER) — passing
 * ['MEMBER'] also allows ADMIN/MANAGER.
 */
export function roleGuard(allowed: OrganizationRole[]): CanActivateFn {
  const minRank = Math.min(...allowed.map((r) => ROLE_RANK[r]));
  return () => {
    const auth = inject(AuthStore);
    const router = inject(Router);
    const role = auth.activeRole();
    if (role && ROLE_RANK[role] >= minRank) return true;
    return router.createUrlTree(['/forbidden']);
  };
}
