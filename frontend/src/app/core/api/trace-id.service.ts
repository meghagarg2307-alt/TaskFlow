import { Injectable } from '@angular/core';

/**
 * Allows components/stores to generate a trace id <em>before</em> sending a request,
 * so the same id can be used for optimistic-update bookkeeping ("I just dispatched
 * trace X; ignore the WS echo carrying trace X").
 *
 * <p>The interceptor still tags requests automatically; this service is for the
 * cases where the caller needs to know the id in advance.</p>
 */
@Injectable({ providedIn: 'root' })
export class TraceIdService {
  generate(): string {
    if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
      return crypto.randomUUID();
    }
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
      const r = (Math.random() * 16) | 0;
      return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
    });
  }
}
