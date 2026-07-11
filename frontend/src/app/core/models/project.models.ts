import { IsoInstant, Uuid } from './common.models';

export interface Project {
  id: Uuid;
  name: string;
  key: string;
  description?: string;
  createdAt: IsoInstant;
  updatedAt: IsoInstant;
}

export interface CreateProjectRequest {
  name: string;
  key: string;
  description?: string;
}

export interface UpdateProjectRequest {
  name?: string;
  description?: string;
}
