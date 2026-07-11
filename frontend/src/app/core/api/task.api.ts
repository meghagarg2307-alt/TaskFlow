import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Uuid } from '../models/common.models';
import {
  CreateTaskRequest,
  MoveTaskRequest,
  Task,
  UpdateTaskRequest,
} from '../models/task.models';

/**
 * Task REST client. Mutating methods accept an optional pre-generated trace id
 * — when provided, it's sent as {@code X-Trace-Id} so the resulting WebSocket
 * echo carries the same id and the optimistic store can recognize "this is mine,
 * skip it." Without the trace id, the interceptor generates one normally.
 */
@Injectable({ providedIn: 'root' })
export class TaskApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  create(boardId: Uuid, req: CreateTaskRequest, traceId?: string): Observable<Task> {
    return this.http.post<Task>(
      `${this.base}/boards/${boardId}/tasks`, req, opts(traceId),
    );
  }
  get(taskId: Uuid): Observable<Task> {
    return this.http.get<Task>(`${this.base}/tasks/${taskId}`);
  }
  update(taskId: Uuid, req: UpdateTaskRequest, traceId?: string): Observable<Task> {
    return this.http.patch<Task>(`${this.base}/tasks/${taskId}`, req, opts(traceId));
  }
  /**
   * The drag-drop endpoint. Returns the updated task with its new
   * column/position/version so the store can reconcile any optimistic move.
   */
  move(taskId: Uuid, req: MoveTaskRequest, traceId?: string): Observable<Task> {
    return this.http.post<Task>(`${this.base}/tasks/${taskId}/move`, req, opts(traceId));
  }
  remove(taskId: Uuid, traceId?: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/tasks/${taskId}`, opts(traceId));
  }
}

function opts(traceId?: string): { headers?: HttpHeaders } {
  return traceId ? { headers: new HttpHeaders({ 'X-Trace-Id': traceId }) } : {};
}
