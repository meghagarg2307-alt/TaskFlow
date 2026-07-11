import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { Observable, Subject, filter } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TokenStore } from '../auth/token.store';
import { BoardEvent } from '../models/event.models';

/**
 * Single shared STOMP client.
 *
 * <p>Design choices:</p>
 * <ul>
 *   <li><b>One client, multiple subscriptions.</b> Opening N WebSockets for N boards
 *       wastes connections and confuses backend metrics. We multiplex over one
 *       client and reuse it across feature components.</li>
 *   <li><b>Auto-reconnect.</b> 5s back-off, indefinite — covers transient network
 *       blips, deploys, load-balancer recycles. Active subscriptions are remembered
 *       and re-established by stompjs internally on reconnect.</li>
 *   <li><b>Token refresh on reconnect.</b> The {@code beforeConnect} hook re-reads the
 *       current bearer from {@link TokenStore} so a CONNECT after a 15-min idle
 *       picks up the freshly-rotated access token. Without this, we'd reconnect
 *       with an expired token and immediately get an ERROR frame.</li>
 *   <li><b>Heartbeats: 10 s each way.</b> Matches the backend config; mismatched
 *       heartbeats cause one side to consider the other dead inappropriately.</li>
 * </ul>
 *
 * <p>Public API is a stream-per-destination ({@link subscribeBoard},
 * {@link subscribeOrg}) that yields {@link BoardEvent}s. Internally we keep one
 * Subject per destination so multiple components on the same board share one
 * STOMP subscription.</p>
 */
@Injectable({ providedIn: 'root' })
export class StompService {
  private readonly tokenStore = inject(TokenStore);
  private readonly destroyRef = inject(DestroyRef);

  private client: Client | null = null;
  /** Active local subjects keyed by destination, with their underlying STOMP subscriptions. */
  private readonly subjects = new Map<string, { subject: Subject<BoardEvent>; sub?: StompSubscription }>();

  private readonly _connected = signal(false);
  readonly connected = this._connected.asReadonly();
  readonly disconnected = computed(() => !this._connected());

  constructor() {
    this.destroyRef.onDestroy(() => this.disconnect());
  }

  /** Connects (idempotent). Safe to call multiple times — subsequent calls are no-ops. */
  connect(): void {
    if (this.client) return;
    this.client = new Client({
      brokerURL: environment.wsUrl,
      reconnectDelay: 5_000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      beforeConnect: () => {
        const bearer = this.tokenStore.bearerHeader();
        // stompjs lets us mutate connectHeaders right before each (re)connect.
        this.client!.connectHeaders = bearer ? { Authorization: bearer } : {};
      },
      onConnect: () => {
        this._connected.set(true);
        // Re-establish any subscriptions that survived a disconnect.
        for (const [destination, entry] of this.subjects.entries()) {
          if (!entry.sub) {
            entry.sub = this.attachStompSubscription(destination, entry.subject);
          }
        }
      },
      onWebSocketClose: () => {
        this._connected.set(false);
        // Mark every subscription as needing re-attach on next CONNECT.
        for (const entry of this.subjects.values()) entry.sub = undefined;
      },
      onStompError: (frame) => {
        // STOMP-level error (e.g. invalid token). Log; let the auto-reconnect retry,
        // which will pick up a fresh token via beforeConnect.
        console.warn('STOMP error', frame.headers['message'], frame.body);
      },
    });
    this.client.activate();
  }

  disconnect(): void {
    if (!this.client) return;
    for (const entry of this.subjects.values()) entry.sub?.unsubscribe();
    this.subjects.clear();
    this.client.deactivate().catch(() => {/* swallow */});
    this.client = null;
    this._connected.set(false);
  }

  /** Subscribe to a board's event stream. The returned Observable completes when the SPA destroys it. */
  subscribeBoard(boardId: string): Observable<BoardEvent> {
    return this.streamFor(`/topic/boards/${boardId}`);
  }

  subscribeOrg(orgId: string): Observable<BoardEvent> {
    return this.streamFor(`/topic/orgs/${orgId}`);
  }

  // ----------------------------------------------------------- internals

  private streamFor(destination: string): Observable<BoardEvent> {
    let entry = this.subjects.get(destination);
    if (!entry) {
      const subject = new Subject<BoardEvent>();
      entry = { subject };
      this.subjects.set(destination, entry);
      // Connect lazily on first subscriber.
      this.connect();
      if (this.client?.connected) {
        entry.sub = this.attachStompSubscription(destination, subject);
      }
    }
    return entry.subject.asObservable().pipe(filter((e) => !!e));
  }

  private attachStompSubscription(destination: string, sink: Subject<BoardEvent>): StompSubscription {
    return this.client!.subscribe(destination, (msg: IMessage) => {
      try {
        const event = JSON.parse(msg.body) as BoardEvent;
        sink.next(event);
      } catch (e) {
        console.error('Failed to parse STOMP message body', e, msg.body);
      }
    });
  }
}
