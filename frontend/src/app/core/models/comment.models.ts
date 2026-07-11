import { IsoInstant, UserRef, Uuid } from './common.models';

export interface Comment {
  id: Uuid;
  taskId: Uuid;
  author: UserRef;
  body: string;
  createdAt: IsoInstant;
}

export interface CreateCommentRequest {
  body: string;
}
