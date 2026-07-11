/** UUID type alias for readability — Angular doesn't have a native UUID type. */
export type Uuid = string;

/** ISO-8601 timestamp string as returned by the backend (e.g. "2026-05-11T12:00:00Z"). */
export type IsoInstant = string;

/** Minimal user reference used across many DTOs. */
export interface UserRef {
  id: Uuid;
  fullName: string;
  avatarUrl?: string;
}

/** Uniform error envelope matching backend's ApiError. */
export interface ApiError {
  timestamp: IsoInstant;
  status: number;
  code: string;
  message: string;
  path: string;
  traceId?: string;
  fieldErrors?: { field: string; message: string }[];
}

/** Spring Data Page<T> wire shape — for paginated endpoints. */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}
