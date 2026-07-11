import { IsoInstant, Uuid } from './common.models';

export type OrganizationRole = 'ADMIN' | 'MANAGER' | 'MEMBER';

export interface UserSummary {
  id: Uuid;
  email: string;
  fullName: string;
  avatarUrl?: string;
}

export interface OrganizationSummary {
  id: Uuid;
  name: string;
  slug: string;
  role: OrganizationRole;
}

export interface AuthResponse {
  accessToken: string;
  accessTokenExpiresAt: IsoInstant;
  user: UserSummary;
  activeOrganization: OrganizationSummary;
  organizations: OrganizationSummary[];
}

export interface LoginRequest {
  email: string;
  password: string;
  organizationId?: Uuid;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  organizationName: string;
  organizationSlug: string;
}

export interface SwitchOrgRequest {
  organizationId: Uuid;
}
