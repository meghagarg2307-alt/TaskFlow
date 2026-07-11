import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { OrganizationApi } from '@core/api/organization.api';
import { formatHttpError } from '@core/api/http-error.util';
import { AuthStore } from '@core/auth/auth.store';
import { OrganizationRole } from '@core/models/auth.models';
import { Invitation, Member, Organization } from '@core/models/org.models';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { Uuid } from '@core/models/common.models';

@Component({
  selector: 'tf-team',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ConfirmDialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page">
      <header class="page__header">
        <div>
          <h1>Team</h1>
          <p class="tf-muted page__lead">
            Manage who has access to <strong>{{ auth.activeOrg()?.name }}</strong>.
          </p>
        </div>
        <a routerLink="/projects" class="tf-btn tf-btn--ghost">← Projects</a>
      </header>

      @if (auth.isAdmin()) {
        <section class="panel tf-card workspace-panel">
          <h2 class="panel__title">Workspace settings</h2>
          <p class="tf-muted panel__hint">Admins can rename the workspace or move it to Trash.</p>
          <form class="workspace-form" [formGroup]="workspaceForm" (ngSubmit)="saveWorkspace()">
            <label class="invite-form__field">
              <span>Workspace name</span>
              <input class="tf-input" formControlName="name" />
            </label>
            <label class="invite-form__field">
              <span>Description</span>
              <textarea class="tf-input" rows="2" formControlName="description"></textarea>
            </label>
            <button class="tf-btn" type="submit" [disabled]="workspaceForm.invalid || savingWorkspace()">
              Save workspace
            </button>
          </form>
          <button type="button" class="tf-btn tf-btn--danger workspace-panel__delete" (click)="confirmWorkspaceDelete.set(true)">
            Delete workspace
          </button>
        </section>
      }

      @if (auth.canManage()) {
        <section class="panel tf-card">
          <h2 class="panel__title">Invite teammate</h2>
          <p class="tf-muted panel__hint">
            Send an invite link to their email. They must sign in with that same email to join.
          </p>

          @if (lastInviteLink(); as link) {
            <div class="invite-result">
              <p class="invite-result__label">Share this link (shown once):</p>
              <div class="invite-result__row">
                <input class="tf-input invite-result__input" readonly [value]="link" />
                <button type="button" class="tf-btn" (click)="copyLink(link)">
                  {{ copied() ? 'Copied' : 'Copy' }}
                </button>
              </div>
            </div>
          }

          <form class="invite-form" [formGroup]="inviteForm" (ngSubmit)="sendInvite()">
            <label class="invite-form__field">
              <span>Email</span>
              <input class="tf-input" type="email" formControlName="email" placeholder="colleague@company.com" />
            </label>
            <label class="invite-form__field invite-form__field--role">
              <span>Role</span>
              <select class="tf-input" formControlName="role">
                @for (r of inviteRoles; track r) {
                  <option [value]="r">{{ r }}</option>
                }
              </select>
            </label>
            <button class="tf-btn" type="submit" [disabled]="inviteForm.invalid || inviting()">
              @if (inviting()) { <span>Sending…</span> } @else { <span>Send invitation</span> }
            </button>
          </form>

          @if (error(); as e) {
            <div class="page__error">{{ e }}</div>
          }
        </section>

        <section class="panel tf-card">
          <h2 class="panel__title">Pending invitations</h2>
          @if (invitationsLoading()) {
            <p class="tf-muted">Loading…</p>
          } @else if (invitations().length === 0) {
            <p class="tf-muted">No pending invitations.</p>
          } @else {
            <ul class="invite-list">
              @for (inv of invitations(); track inv.id) {
                <li class="invite-list__item">
                  <div>
                    <strong>{{ inv.email }}</strong>
                    <span class="tf-muted"> · {{ inv.role }} · expires {{ formatDate(inv.expiresAt) }}</span>
                  </div>
                  <button
                    type="button"
                    class="tf-btn tf-btn--ghost"
                    [disabled]="revokingId() === inv.id"
                    (click)="revoke(inv.id)">
                    Revoke
                  </button>
                </li>
              }
            </ul>
          }
        </section>
      } @else {
        <p class="tf-muted tf-card panel panel--info">
          Only <strong>Admins</strong> and <strong>Managers</strong> can invite teammates.
          Ask your workspace admin for an invitation link.
        </p>
      }

      <section class="panel tf-card">
        <h2 class="panel__title">Members ({{ members().length }})</h2>
        @if (membersLoading()) {
          <p class="tf-muted">Loading…</p>
        } @else {
          <ul class="member-list">
            @for (m of members(); track m.userId) {
              <li class="member-list__item">
                <div class="member-list__avatar">{{ initials(m.fullName) }}</div>
                <div class="member-list__info">
                  <strong>{{ m.fullName }}</strong>
                  <span class="tf-muted">{{ m.email }}</span>
                </div>
                <span class="member-list__role">{{ m.role }}</span>
              </li>
            }
          </ul>
        }
      </section>
    </section>

    @if (confirmWorkspaceDelete()) {
      <tf-confirm-dialog
        title="Delete workspace?"
        message="The entire workspace, all projects, boards, and tasks will move to Trash for 30 days."
        warning="Only admins can restore or permanently delete from the Trash page."
        confirmLabel="Move workspace to trash"
        (confirmed)="deleteWorkspace()"
        (cancelled)="confirmWorkspaceDelete.set(false)" />
    }
  `,
  styles: [`
    .page { padding: var(--space-5); max-width: 800px; margin: 0 auto; }
    .page__header {
      display: flex; align-items: flex-start; justify-content: space-between;
      gap: var(--space-4); margin-bottom: var(--space-5);
    }
    h1 { margin: 0 0 var(--space-1); font-size: var(--font-size-2xl); }
    .page__lead { margin: 0; font-size: var(--font-size-sm); }
    .panel { padding: var(--space-4); margin-bottom: var(--space-4); }
    .panel--info { padding: var(--space-4); }
    .panel__title { margin: 0 0 var(--space-2); font-size: var(--font-size-md); }
    .panel__hint { margin: 0 0 var(--space-4); font-size: var(--font-size-sm); }
    .invite-form {
      display: grid; grid-template-columns: 1fr auto auto; gap: var(--space-3); align-items: end;
    }
    @media (max-width: 640px) {
      .invite-form { grid-template-columns: 1fr; }
    }
    .invite-form__field { display: flex; flex-direction: column; gap: var(--space-1); }
    .invite-result {
      margin-bottom: var(--space-4); padding: var(--space-3);
      background: var(--color-surface-alt); border-radius: var(--radius-md);
    }
    .invite-result__label { margin: 0 0 var(--space-2); font-size: var(--font-size-sm); }
    .invite-result__row { display: flex; gap: var(--space-2); }
    .invite-result__input { flex: 1; font-size: var(--font-size-xs); }
    .page__error {
      margin-top: var(--space-3); padding: var(--space-2) var(--space-3);
      background: color-mix(in srgb, var(--color-danger), transparent 88%);
      border: 1px solid color-mix(in srgb, var(--color-danger), transparent 70%);
      color: var(--color-danger); border-radius: var(--radius-md); font-size: var(--font-size-sm);
    }
    .invite-list, .member-list { list-style: none; padding: 0; margin: 0; }
    .invite-list__item, .member-list__item {
      display: flex; align-items: center; justify-content: space-between; gap: var(--space-3);
      padding: var(--space-3) 0; border-bottom: 1px solid var(--color-border);
    }
    .invite-list__item:last-child, .member-list__item:last-child { border-bottom: none; }
    .member-list__item { justify-content: flex-start; }
    .member-list__avatar {
      width: 36px; height: 36px; border-radius: 50%;
      background: var(--color-brand-100); color: var(--color-brand-700);
      display: grid; place-items: center; font-size: var(--font-size-xs); font-weight: 600;
    }
    .member-list__info { flex: 1; display: flex; flex-direction: column; gap: 2px; }
    .member-list__info span { font-size: var(--font-size-xs); }
    .member-list__role {
      font-size: 11px; text-transform: uppercase; letter-spacing: 0.5px;
      color: var(--color-text-muted);
    }
    .workspace-form { display: grid; gap: var(--space-3); margin-bottom: var(--space-4); }
    .workspace-panel__delete { margin-top: var(--space-2); }
  `],
})
export class TeamComponent implements OnInit {
  protected readonly auth = inject(AuthStore);
  private readonly api = inject(OrganizationApi);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);

  protected readonly members = signal<Member[]>([]);
  protected readonly invitations = signal<Invitation[]>([]);
  protected readonly membersLoading = signal(true);
  protected readonly invitationsLoading = signal(false);
  protected readonly inviting = signal(false);
  protected readonly revokingId = signal<Uuid | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly lastInviteLink = signal<string | null>(null);
  protected readonly copied = signal(false);

  protected readonly inviteRoles: OrganizationRole[] = ['MEMBER', 'MANAGER', 'ADMIN'];

  protected readonly confirmWorkspaceDelete = signal(false);
  protected readonly savingWorkspace = signal(false);

  protected readonly workspaceForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
    description: [''],
  });

  protected readonly inviteForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    role: ['MEMBER' as OrganizationRole, Validators.required],
  });

  ngOnInit(): void {
    if (this.auth.isAdmin()) {
      this.api.getCurrent().subscribe({
        next: (org) => this.workspaceForm.patchValue({
          name: org.name,
          description: org.description ?? '',
        }),
      });
    }

    this.api.listMembers().subscribe({
      next: (rows) => { this.members.set(rows); this.membersLoading.set(false); },
      error: () => this.membersLoading.set(false),
    });

    if (this.auth.canManage()) {
      this.loadInvitations();
    }
  }

  private loadInvitations(): void {
    this.invitationsLoading.set(true);
    this.api.listInvitations().subscribe({
      next: (rows) => { this.invitations.set(rows); this.invitationsLoading.set(false); },
      error: () => this.invitationsLoading.set(false),
    });
  }

  protected sendInvite(): void {
    if (this.inviteForm.invalid) return;
    this.inviting.set(true);
    this.error.set(null);
    this.lastInviteLink.set(null);

    this.api.invite(this.inviteForm.getRawValue()).subscribe({
      next: (inv) => {
        this.inviting.set(false);
        if (inv.inviteToken) {
          this.lastInviteLink.set(this.buildJoinUrl(inv.inviteToken));
        }
        this.invitations.update((rows) => [inv, ...rows]);
        this.inviteForm.reset({ email: '', role: 'MEMBER' });
      },
      error: (err: HttpErrorResponse) => {
        this.inviting.set(false);
        this.error.set(formatHttpError(err, 'Could not send invitation.'));
      },
    });
  }

  protected revoke(id: Uuid): void {
    this.revokingId.set(id);
    this.api.revokeInvitation(id).subscribe({
      next: () => {
        this.invitations.update((rows) => rows.filter((i) => i.id !== id));
        this.revokingId.set(null);
      },
      error: () => this.revokingId.set(null),
    });
  }

  protected saveWorkspace(): void {
    if (this.workspaceForm.invalid) return;
    this.savingWorkspace.set(true);
    this.api.updateCurrent(this.workspaceForm.getRawValue()).subscribe({
      next: () => this.savingWorkspace.set(false),
      error: (err: HttpErrorResponse) => {
        this.savingWorkspace.set(false);
        this.error.set(formatHttpError(err, 'Could not update workspace.'));
      },
    });
  }

  protected deleteWorkspace(): void {
    this.confirmWorkspaceDelete.set(false);
    this.api.deleteCurrent().subscribe({
      next: () => this.router.navigate(['/trash']),
      error: (err: HttpErrorResponse) => {
        this.error.set(formatHttpError(err, 'Could not delete workspace.'));
      },
    });
  }

  protected copyLink(link: string): void {
    navigator.clipboard.writeText(link).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2_000);
    });
  }

  protected initials(name: string): string {
    return name.split(/\s+/).map((p) => p[0]).join('').slice(0, 2).toUpperCase();
  }

  protected formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString();
  }

  private buildJoinUrl(token: string): string {
    const origin = typeof window !== 'undefined' ? window.location.origin : '';
    return `${origin}/join?token=${encodeURIComponent(token)}`;
  }
}
