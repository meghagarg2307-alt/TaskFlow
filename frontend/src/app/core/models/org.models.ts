import { IsoInstant, Uuid } from './common.models';
import { OrganizationRole } from './auth.models';

export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'REVOKED' | 'EXPIRED';

export interface Organization {
  id: Uuid;
  name: string;
  slug: string;
  description?: string;
  createdAt: IsoInstant;
  updatedAt: IsoInstant;
}

export interface UpdateOrganizationRequest {
  name?: string;
  description?: string;
}

export interface Member {
  userId: Uuid;
  email: string;
  fullName: string;
  avatarUrl?: string;
  role: OrganizationRole;
  joinedAt: IsoInstant;
}

export interface Invitation {
  id: Uuid;
  email: string;
  role: OrganizationRole;
  status: InvitationStatus;
  expiresAt: IsoInstant;
  createdAt: IsoInstant;
  /** Present only immediately after creating an invitation. */
  inviteToken?: string;
}

export interface InviteMemberRequest {
  email: string;
  role: OrganizationRole;
}

export interface AcceptInvitationRequest {
  token: string;
}

export interface AcceptInvitationResult {
  organizationId: Uuid;
  organizationName: string;
  organizationSlug: string;
  member: Member;
}
