import { Injectable, signal } from '@angular/core';

/**
 * In-memory access-token store. Kept deliberately tiny and out of localStorage.
 *
 * <p><b>Why in-memory?</b> A localStorage access token is an XSS goldmine. By keeping
 * it in a signal (process memory) and using the HttpOnly refresh cookie to re-mint
 * it on page reload, we eliminate the persistent-storage attack surface.</p>
 *
 * <p>Trade-off: a hard refresh loses the in-memory copy. The app-init flow calls
 * /auth/refresh once on startup to rehydrate.</p>
 */
@Injectable({ providedIn: 'root' })
export class TokenStore {
  private readonly _accessToken = signal<string | null>(null);
  private readonly _expiresAt   = signal<number | null>(null);  // epoch millis

  /** Signal exposed for components that want to react to login/logout. */
  readonly accessToken = this._accessToken.asReadonly();

  set(token: string, expiresAtIso: string): void {
    this._accessToken.set(token);
    this._expiresAt.set(Date.parse(expiresAtIso));
  }

  clear(): void {
    this._accessToken.set(null);
    this._expiresAt.set(null);
  }

  /** True if a token exists and is within its TTL minus a small clock-skew buffer. */
  isValid(skewMs = 5_000): boolean {
    const exp = this._expiresAt();
    return this._accessToken() !== null && exp !== null && exp - skewMs > Date.now();
  }

  /** Returns the current bearer header value, or null if no valid token. */
  bearerHeader(): string | null {
    const tok = this._accessToken();
    return tok ? `Bearer ${tok}` : null;
  }
}
