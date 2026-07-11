import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import {
  Observable,
  Subject,
  catchError,
  filter,
  switchMap,
  take,
  throwError,
} from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';
import { TokenStore } from './token.store';

/**
 * Auth interceptor with <b>single-flight refresh</b>.
 *
 * <p>Three concerns rolled into one interceptor:</p>
 * <ol>
 *   <li>Attaches the {@code Authorization: Bearer ...} header to every API call.</li>
 *   <li>Adds {@code withCredentials} so the HttpOnly refresh cookie always rides along.</li>
 *   <li>On a {@code 401}, fires <em>one</em> {@code /auth/refresh} for the entire app —
 *       any other 401 that arrives during the refresh waits for the same result.
 *       Without this, a page firing N parallel requests would trigger N concurrent
 *       refreshes, and the backend's refresh-reuse detector would terminate the
 *       session (correctly, but undesirably).</li>
 * </ol>
 *
 * <p>The refresh endpoint and the login endpoint are exempt — they have no access
 * token and must never recurse into themselves.</p>
 */

let refreshInFlight = false;
const refreshGate$ = new Subject<string | null>();

function isAuthEndpoint(url: string): boolean {
  return url.includes('/auth/login')
      || url.includes('/auth/refresh')
      || url.includes('/auth/register')
      || url.includes('/auth/logout');
}

function attachBearer(req: HttpRequest<unknown>, bearer: string | null): HttpRequest<unknown> {
  const headers = bearer
    ? req.headers.set('Authorization', bearer)
    : req.headers;
  return req.clone({ headers, withCredentials: true });
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // Only intercept calls to our backend (skip third-party CDNs etc.).
  if (!req.url.startsWith(environment.apiBaseUrl) && !req.url.startsWith('http')) {
    return next(req);
  }
  const tokenStore = inject(TokenStore);
  const authService = inject(AuthService);
  const router = inject(Router);

  const prepared = isAuthEndpoint(req.url)
    ? req.clone({ withCredentials: true })
    : attachBearer(req, tokenStore.bearerHeader());

  return next(prepared).pipe(
    catchError((err: unknown) => {
      if (!(err instanceof HttpErrorResponse) || err.status !== 401 || isAuthEndpoint(req.url)) {
        return throwError(() => err);
      }
      return handle401(req, next, authService, tokenStore, router);
    }),
  );
};

function handle401(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  authService: AuthService,
  tokenStore: TokenStore,
  router: Router,
): Observable<HttpEvent<unknown>> {
  if (refreshInFlight) {
    // Another request is already refreshing — wait for its result, then retry.
    return refreshGate$.pipe(
      filter((t): t is string | null => t !== undefined),
      take(1),
      switchMap((token) => {
        if (token === null) {
          return throwError(() => new HttpErrorResponse({
            status: 401,
            statusText: 'Refresh failed',
          }));
        }
        return next(attachBearer(req, `Bearer ${token}`));
      }),
    );
  }

  refreshInFlight = true;
  return authService.refresh().pipe(
    switchMap((res) => {
      refreshInFlight = false;
      refreshGate$.next(res.accessToken);
      return next(attachBearer(req, `Bearer ${res.accessToken}`));
    }),
    catchError((err: unknown) => {
      refreshInFlight = false;
      refreshGate$.next(null);
      tokenStore.clear();
      // Redirect on terminal auth failure. Use replace to avoid back-button loops.
      router.navigate(['/login'], { replaceUrl: true, queryParams: { returnUrl: router.url } });
      return throwError(() => err);
    }),
  );
}
