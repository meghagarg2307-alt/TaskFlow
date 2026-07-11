import { IsoInstant, UserRef, Uuid } from './common.models';

export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

export interface Task {
  id: Uuid;
  boardId: Uuid;
  columnId: Uuid;
  title: string;
  description?: string;
  priority: TaskPriority;
  position: number;
  assignee?: UserRef;
  dueDate?: IsoInstant;
  version: number;
  createdAt: IsoInstant;
  updatedAt: IsoInstant;
}

export interface CreateTaskRequest {
  columnId: Uuid;
  title: string;
  description?: string;
  priority?: TaskPriority;
  assigneeId?: Uuid;
  dueDate?: IsoInstant;
}

export interface UpdateTaskRequest {
  title?: string;
  description?: string;
  priority?: TaskPriority;
  /**
   * `null` clears the assignee; `undefined` (omit) leaves it alone.
   * Backend deserializes both to Optional. Matches the Optional<T> contract.
   */
  assigneeId?: Uuid | null;
  dueDate?: IsoInstant | null;
  expectedVersion: number;
}

export interface MoveTaskRequest {
  targetColumnId: Uuid;
  /** Drop-target neighbors. Either may be null for boundary positions. */
  beforeTaskId?: Uuid | null;
  afterTaskId?: Uuid | null;
  expectedVersion: number;
}
