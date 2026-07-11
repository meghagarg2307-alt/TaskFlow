-- =====================================================================================
-- V2: Trash / soft-delete metadata (deletedBy, restore audit, workspace + column trash)
-- =====================================================================================

-- Workspace (organization) soft delete
ALTER TABLE organizations
    ADD COLUMN deleted_at    timestamptz,
    ADD COLUMN deleted_by    uuid,
    ADD COLUMN restored_at   timestamptz,
    ADD COLUMN restored_by   uuid;

CREATE INDEX idx_organizations_active ON organizations (slug) WHERE deleted_at IS NULL;

-- Projects / boards / tasks / comments — audit + restore fields
ALTER TABLE projects
    ADD COLUMN deleted_by    uuid,
    ADD COLUMN restored_at   timestamptz,
    ADD COLUMN restored_by   uuid;

ALTER TABLE boards
    ADD COLUMN deleted_by    uuid,
    ADD COLUMN restored_at   timestamptz,
    ADD COLUMN restored_by   uuid;

ALTER TABLE tasks
    ADD COLUMN deleted_by    uuid,
    ADD COLUMN restored_at   timestamptz,
    ADD COLUMN restored_by   uuid;

ALTER TABLE comments
    ADD COLUMN deleted_by    uuid,
    ADD COLUMN restored_at   timestamptz,
    ADD COLUMN restored_by   uuid;

-- Columns: soft delete (was hard delete + CASCADE)
ALTER TABLE board_columns
    ADD COLUMN deleted_at    timestamptz,
    ADD COLUMN deleted_by    uuid,
    ADD COLUMN restored_at   timestamptz,
    ADD COLUMN restored_by   uuid;

CREATE INDEX idx_columns_board_active ON board_columns (board_id, position)
    WHERE deleted_at IS NULL;

-- Trash purge queries
CREATE INDEX idx_projects_deleted_at ON projects (deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX idx_boards_deleted_at ON boards (deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX idx_tasks_deleted_at ON tasks (deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX idx_comments_deleted_at ON comments (deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX idx_organizations_deleted_at ON organizations (deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX idx_columns_deleted_at ON board_columns (deleted_at) WHERE deleted_at IS NOT NULL;
