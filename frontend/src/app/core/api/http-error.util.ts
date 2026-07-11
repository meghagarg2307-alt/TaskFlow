import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from '../models/common.models';

/**
 * Turns an {@link HttpErrorResponse} into a user-facing message.
 * Handles non-JSON bodies (nginx 502 HTML, dev-server index.html) gracefully.
 */
export function formatHttpError(err: HttpErrorResponse, fallback: string): string {
  const body = err.error;

  if (body && typeof body === 'object' && 'message' in body) {
    return String((body as ApiError).message);
  }

  if (typeof body === 'string') {
    if (body.trimStart().startsWith('<')) {
      return gatewayMessage(err.status);
    }
    if (body.length > 0 && body.length < 300) {
      return body;
    }
  }

  if (err.status === 0) {
    return 'Cannot reach the server. Check that the backend is running.';
  }

  if (err.status === 502 || err.status === 503) {
    return gatewayMessage(err.status);
  }

  if (typeof err.message === 'string' && err.message.includes('JSON')) {
    return gatewayMessage(err.status);
  }

  return fallback;
}

function gatewayMessage(status: number): string {
  if (status === 502 || status === 503) {
    return 'The API is temporarily unreachable. If you rebuilt only the API container, restart the web proxy: docker compose -f infra/docker-compose.yml restart web';
  }
  return 'The server returned an unexpected response. Ensure the API is running and you are using http://localhost (Docker) or ng serve with the API proxy.';
}
