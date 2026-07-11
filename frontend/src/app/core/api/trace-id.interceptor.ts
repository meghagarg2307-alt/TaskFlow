import { HttpInterceptorFn } from '@angular/common/http';

/**
 * Generates a per-request trace id and propagates it as {@code X-Trace-Id}. The
 * backend reads it (or generates one if absent), echoes it in the response header
 * AND embeds it into every WebSocket event it produces — that's how the SPA
 * recognizes "this is my own optimistic update echoing back" and skips re-applying.
 */
function uuid(): string {
  // crypto.randomUUID is available in all modern browsers and Node 19+.
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  // Fallback for environments lacking it — fine for dev only.
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

export const traceIdInterceptor: HttpInterceptorFn = (req, next) => {
  // If the caller already attached a trace id (e.g. an optimistic mutation that
  // needs to know the id in advance for echo dedupe), respect it. Otherwise mint
  // a fresh one. This dual mode is what makes the optimistic-update loop work.
  if (req.headers.has('X-Trace-Id')) {
    return next(req);
  }
  const tagged = req.clone({
    headers: req.headers.set('X-Trace-Id', uuid()),
  });
  return next(tagged);
};
