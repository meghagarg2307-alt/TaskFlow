import { IsoInstant, Uuid } from './common.models';
import { Task } from './task.models';

export interface BoardSummary {
  id: Uuid;
  projectId: Uuid;
  name: string;
  description?: string;
  version: number;
  createdAt: IsoInstant;
  updatedAt: IsoInstant;
}

export interface BoardColumn {
  id: Uuid;
  name: string;
  position: number;
  wipLimit?: number;
}

/** Single-shot full board payload returned by GET /boards/{id}. */
export interface BoardSnapshot {
  id: Uuid;
  projectId: Uuid;
  name: string;
  description?: string;
  version: number;
  updatedAt: IsoInstant;
  columns: BoardColumn[];
  tasks: Task[];
}

export interface CreateBoardRequest {
  name: string;
  description?: string;
}

export interface UpdateBoardRequest {
  name?: string;
  description?: string;
  expectedVersion: number;
}

export interface CreateColumnRequest {
  name: string;
  wipLimit?: number;
}

export interface UpdateColumnRequest {
  name?: string;
  wipLimit?: number;
  beforeColumnId?: Uuid;
  afterColumnId?: Uuid;
}
