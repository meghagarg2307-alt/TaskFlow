import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Uuid } from '../models/common.models';
import { Comment, CreateCommentRequest } from '../models/comment.models';

@Injectable({ providedIn: 'root' })
export class CommentApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  list(taskId: Uuid): Observable<Comment[]> {
    return this.http.get<Comment[]>(`${this.base}/tasks/${taskId}/comments`);
  }
  create(taskId: Uuid, req: CreateCommentRequest): Observable<Comment> {
    return this.http.post<Comment>(`${this.base}/tasks/${taskId}/comments`, req);
  }
  remove(commentId: Uuid): Observable<void> {
    return this.http.delete<void>(`${this.base}/comments/${commentId}`);
  }
}
