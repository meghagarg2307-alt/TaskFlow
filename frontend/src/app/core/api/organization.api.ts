import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Uuid } from '../models/common.models';
import {
  AcceptInvitationRequest,
  AcceptInvitationResult,
  Invitation,
  InviteMemberRequest,
  Member,
  Organization,
  UpdateOrganizationRequest,
} from '../models/org.models';

@Injectable({ providedIn: 'root' })
export class OrganizationApi {
  private readonly http = inject(HttpClient);
  private readonly orgBase = `${environment.apiBaseUrl}/organization`;

  getCurrent(): Observable<Organization> {
    return this.http.get<Organization>(this.orgBase);
  }

  updateCurrent(req: UpdateOrganizationRequest): Observable<Organization> {
    return this.http.patch<Organization>(this.orgBase, req);
  }

  deleteCurrent(): Observable<void> {
    return this.http.delete<void>(this.orgBase);
  }

  listMembers(): Observable<Member[]> {
    return this.http.get<Member[]>(`${this.orgBase}/members`);
  }

  invite(req: InviteMemberRequest): Observable<Invitation> {
    return this.http.post<Invitation>(`${this.orgBase}/invitations`, req);
  }

  listInvitations(): Observable<Invitation[]> {
    return this.http.get<Invitation[]>(`${this.orgBase}/invitations`);
  }

  revokeInvitation(invitationId: Uuid): Observable<void> {
    return this.http.delete<void>(`${this.orgBase}/invitations/${invitationId}`);
  }

  acceptInvitation(req: AcceptInvitationRequest): Observable<AcceptInvitationResult> {
    return this.http.post<AcceptInvitationResult>(
      `${environment.apiBaseUrl}/invitations/accept`,
      req,
    );
  }
}
