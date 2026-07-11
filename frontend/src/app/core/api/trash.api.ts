import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Uuid } from '../models/common.models';
import { TrashPage, TrashResourceType } from '../models/trash.models';

@Injectable({ providedIn: 'root' })
export class TrashApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/trash`;

  list(type?: TrashResourceType, page = 0, size = 20): Observable<TrashPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (type) params = params.set('type', type);
    return this.http.get<TrashPage>(this.base, { params });
  }

  restore(type: TrashResourceType, id: Uuid): Observable<void> {
    return this.http.post<void>(`${this.base}/${type}/${id}/restore`, null);
  }

  permanentlyDelete(type: TrashResourceType, id: Uuid): Observable<void> {
    return this.http.delete<void>(`${this.base}/${type}/${id}`);
  }
}
