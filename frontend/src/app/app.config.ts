import { APP_INITIALIZER, ApplicationConfig, inject, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding, withViewTransitions } from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { routes } from './app.routes';
import { traceIdInterceptor } from './core/api/trace-id.interceptor';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthService } from './core/auth/auth.service';

/**
 * Application-level providers.
 *
 * <p>The {@code APP_INITIALIZER} entry runs the bootstrap refresh BEFORE the
 * router activates — so by the time {@code AuthStore} is read by a guard, we
 * already know whether the user is authenticated. This avoids the dreaded
 * "flash of login screen" for users who reload mid-session.</p>
 *
 * <p>Interceptor order matters: {@code traceIdInterceptor} runs first (so every
 * request has a trace id, including the refresh call), then {@code authInterceptor}
 * (which may rerun the request with a new bearer).</p>
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true, runCoalescing: true }),

    provideRouter(
      routes,
      withComponentInputBinding(),  // bind route params/data directly to inputs
      withViewTransitions(),        // CSS view transitions on route change
    ),

    provideHttpClient(
      withFetch(),
      withInterceptors([traceIdInterceptor, authInterceptor]),
    ),

    {
      provide: APP_INITIALIZER,
      multi:   true,
      useFactory: () => {
        const auth = inject(AuthService);
        // Silent rehydrate from refresh cookie. We always resolve (even on failure)
        // so the SPA still boots into /login if the cookie is missing/invalid.
        return () => firstValueFrom(auth.bootstrap())
          .then(() => void 0)
          .catch(() => void 0);
      },
    },
  ],
};
