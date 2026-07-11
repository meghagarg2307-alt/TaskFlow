import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Uuid } from '../models/common.models';
import {
  BoardColumn,
  BoardSnapshot,
  BoardSummary,
  CreateBoardRequest,
  CreateColumnRequest,
  UpdateBoardRequest,
  UpdateColumnRequest,
} from '../models/board.models';

@Injectable({ providedIn: 'root' })
export class BoardApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  // -------- boards
  listForProject(projectId: Uuid): Observable<BoardSummary[]> {
    return this.http.get<BoardSummary[]>(`${this.base}/projects/${projectId}/boards`);
  }
  create(projectId: Uuid, req: CreateBoardRequest): Observable<BoardSummary> {
    return this.http.post<BoardSummary>(`${this.base}/projects/${projectId}/boards`, req);
  }
  getSnapshot(boardId: Uuid): Observable<BoardSnapshot> {
    return this.http.get<BoardSnapshot>(`${this.base}/boards/${boardId}`);
  }
  update(boardId: Uuid, req: UpdateBoardRequest, traceId?: string): Observable<BoardSummary> {
    return this.http.patch<BoardSummary>(`${this.base}/boards/${boardId}`, req, opts(traceId));
  }
  remove(boardId: Uuid): Observable<void> {
    return this.http.delete<void>(`${this.base}/boards/${boardId}`);
  }

  // -------- columns
  createColumn(boardId: Uuid, req: CreateColumnRequest, traceId?: string): Observable<BoardColumn> {
    return this.http.post<BoardColumn>(`${this.base}/boards/${boardId}/columns`, req, opts(traceId));
  }
  updateColumn(columnId: Uuid, req: UpdateColumnRequest, traceId?: string): Observable<BoardColumn> {
    return this.http.patch<BoardColumn>(`${this.base}/columns/${columnId}`, req, opts(traceId));
  }
  removeColumn(columnId: Uuid, traceId?: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/columns/${columnId}`, opts(traceId));
  }
}

function opts(traceId?: string): { headers?: HttpHeaders } {
  return traceId ? { headers: new HttpHeaders({ 'X-Trace-Id': traceId }) } : {};
}
