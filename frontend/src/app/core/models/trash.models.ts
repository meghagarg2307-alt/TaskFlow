import { IsoInstant, Uuid } from './common.models';

export type TrashResourceType = 'WORKSPACE' | 'PROJECT' | 'BOARD' | 'TASK';

export interface TrashItem {
  resourceType: TrashResourceType;
  id: Uuid;
  name: string;
  description?: string;
  deletedAt: IsoInstant;
  deletedBy?: Uuid;
  deletedByName?: string;
  daysUntilPermanentDeletion: number;
  parentId?: Uuid;
  parentName?: string;
}

export interface TrashPage {
  content: TrashItem[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
