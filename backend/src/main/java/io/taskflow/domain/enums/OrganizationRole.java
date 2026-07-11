package io.taskflow.domain.enums;

/**
 * A user's role within a single organization (tenant). A user can belong to multiple
 * organizations and hold a different role in each — the role is stored on the
 * {@code OrganizationMembership} join row, not on the user itself.
 */
public enum OrganizationRole {
    /** Full control: org settings, billing, member management, all CRUD. */
    ADMIN,
    /** Project/board CRUD, member invites, task management — no org-level destructive ops. */
    MANAGER,
    /** Task-level CRUD on boards they have access to, comments. */
    MEMBER
}
