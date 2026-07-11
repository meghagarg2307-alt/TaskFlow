import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap, of, catchError } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  SwitchOrgRequest,
} from '../models/auth.models';
import { AuthStore } from './auth.store';
import { TokenStore } from './token.store';

/**
 * Auth REST client. Owns:
 *  - register / login / refresh / switch-org / logout calls
 *  - syncing TokenStore + AuthStore on every successful response
 *  - `withCredentials: true` so the HttpOnly refresh cookie is included
 *
 * <p>Refresh is exposed as both an imperative call (for app boot) and reused by
 * the {@code authInterceptor} for single-flight 401 retry.</p>
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http       = inject(HttpClient);
  private readonly authStore  = inject(AuthStore);
  private readonly tokenStore = inject(TokenStore);

  private readonly baseUrl = `${environment.apiBaseUrl}/auth`;

  register(req: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/register`, req, { withCredentials: true })
      .pipe(tap((res) => this.applySuccess(res)));
  }

  login(req: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/login`, req, { withCredentials: true })
      .pipe(tap((res) => this.applySuccess(res)));
  }

  /**
   * Refresh the access token. Cookie does the heavy lifting; the body is empty.
   * The optional orgId query param re-pins the access token to a specific org.
   */
  refresh(orgId?: string): Observable<AuthResponse> {
    let params = new HttpParams();
    if (orgId) params = params.set('orgId', orgId);
    return this.http.post<AuthResponse>(`${this.baseUrl}/refresh`, {}, {
      withCredentials: true,
      params,
    }).pipe(tap((res) => this.applySuccess(res)));
  }

  switchOrg(req: SwitchOrgRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/switch-org`, req, { withCredentials: true })
      .pipe(tap((res) => this.applySuccess(res)));
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/logout`, {}, { withCredentials: true })
      .pipe(
        tap(() => this.applyLogout()),
        // Even if the call fails (network, expired token), clear local state.
        catchError(() => { this.applyLogout(); return of(void 0); })
      );
  }

  /** Called on app init — silently try to rehydrate from the refresh cookie. */
  bootstrap(): Observable<AuthResponse | null> {
    return this.refresh().pipe(
      catchError(() => of(null)),
    );
  }

  private applySuccess(res: AuthResponse): void {
    this.tokenStore.set(res.accessToken, res.accessTokenExpiresAt);
    this.authStore.hydrate(res);
  }

  private applyLogout(): void {
    this.tokenStore.clear();
    this.authStore.clear();
  }
}
