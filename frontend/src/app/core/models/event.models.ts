import { IsoInstant, Uuid } from './common.models';

/**
 * Activity / WebSocket event types — must stay in lockstep with the backend
 * {@code io.taskflow.domain.enums.ActivityType}. A type-only mirror is fine; if a
 * new backend value arrives, TypeScript narrows it to `string` and the SPA's
 * exhaustive-switch helper logs the unknown variant.
 */
export type ActivityType =
  | 'PROJECT_CREATED' | 'PROJECT_UPDATED' | 'PROJECT_DELETED'
  | 'BOARD_CREATED'   | 'BOARD_UPDATED'   | 'BOARD_DELETED'
  | 'COLUMN_CREATED'  | 'COLUMN_UPDATED'  | 'COLUMN_DELETED'
  | 'TASK_CREATED'    | 'TASK_UPDATED'    | 'TASK_MOVED'
  | 'TASK_ASSIGNED'   | 'TASK_UNASSIGNED' | 'TASK_DELETED'
  | 'COMMENT_ADDED'   | 'COMMENT_DELETED'
  | 'MEMBER_INVITED'  | 'MEMBER_JOINED'   | 'MEMBER_REMOVED'
  | 'MEMBER_ROLE_CHANGED';

/** Wire-format STOMP event emitted from /topic/boards/{id} and /topic/orgs/{id}. */
export interface BoardEvent {
  type: ActivityType;
  organizationId: Uuid;
  projectId?: Uuid;
  boardId?: Uuid;
  taskId?: Uuid;
  actorId: Uuid;
  payload?: Record<string, unknown>;
  /** Echoes the X-Trace-Id of the originating REST request — used for echo dedupe. */
  traceId?: string;
  timestamp: IsoInstant;
}
