import { OrganizationRole } from '../models/auth.models';

/** Mirrors backend role hierarchy: ADMIN > MANAGER > MEMBER. */
export const ROLE_RANK: Record<OrganizationRole, number> = {
  MEMBER: 1,
  MANAGER: 2,
  ADMIN: 3,
};

export function hasMinRole(
  role: OrganizationRole | null | undefined,
  minimum: OrganizationRole,
): boolean {
  if (!role) return false;
  return ROLE_RANK[role] >= ROLE_RANK[minimum];
}
