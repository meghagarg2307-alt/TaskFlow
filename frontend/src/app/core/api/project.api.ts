import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateProjectRequest, Project, UpdateProjectRequest } from '../models/project.models';
import { Uuid } from '../models/common.models';

@Injectable({ providedIn: 'root' })
export class ProjectApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/projects`;

  list(): Observable<Project[]>                                 { return this.http.get<Project[]>(this.base); }
  get(id: Uuid): Observable<Project>                            { return this.http.get<Project>(`${this.base}/${id}`); }
  create(req: CreateProjectRequest): Observable<Project>        { return this.http.post<Project>(this.base, req); }
  update(id: Uuid, req: UpdateProjectRequest): Observable<Project> {
    return this.http.patch<Project>(`${this.base}/${id}`, req);
  }
  remove(id: Uuid): Observable<void>                            { return this.http.delete<void>(`${this.base}/${id}`); }
}
