import { Injectable, computed, signal } from '@angular/core';
import { Uuid } from '@core/models/common.models';
import { BoardSnapshot, BoardColumn } from '@core/models/board.models';
import { Task } from '@core/models/task.models';

/**
 * Per-board state container, provided at the {@code KanbanComponent} scope so
 * navigating away tears it down. NOT a global singleton.
 *
 * <p>Three pieces of state:</p>
 * <ul>
 *   <li><b>The snapshot</b> (board + columns + tasks) — the source of truth.</li>
 *   <li><b>Sorted, grouped views</b> (computed signals) — what the template renders.</li>
 *   <li><b>The in-flight map</b> — trace-id → revert callback. Used for two things:
 *     <ol>
 *       <li>If a STOMP echo carries a trace id we recognize, we already applied
 *           the change locally → skip.</li>
 *       <li>If an optimistic mutation's HTTP call fails, we run the revert
 *           callback to roll the UI back.</li>
 *     </ol>
 *   </li>
 * </ul>
 */
@Injectable()
export class BoardStore {
  private readonly _snapshot = signal<BoardSnapshot | null>(null);

  /** Trace id → revert fn. Presence means "this change is mine; ignore the echo". */
  private readonly inFlight = new Map<Uuid, () => void>();

  readonly snapshot = this._snapshot.asReadonly();
  readonly loaded = computed(() => this._snapshot() !== null);

  readonly columns = computed<BoardColumn[]>(() => {
    const s = this._snapshot();
    if (!s) return [];
    return [...s.columns].sort((a, b) => a.position - b.position);
  });

  /**
   * Tasks bucketed by column id, each bucket sorted by position. Recomputed
   * whenever the snapshot signal changes — cheap thanks to Angular's
   * memoization of computed signals.
   */
  readonly tasksByColumn = computed<Record<Uuid, Task[]>>(() => {
    const s = this._snapshot();
    if (!s) return {};
    const buckets: Record<Uuid, Task[]> = {};
    for (const c of s.columns) buckets[c.id] = [];
    for (const t of s.tasks) {
      const bucket = buckets[t.columnId];
      if (bucket) bucket.push(t);
    }
    for (const colId of Object.keys(buckets)) {
      buckets[colId]!.sort((a, b) => a.position - b.position);
    }
    return buckets;
  });

  // ============================================================ snapshot mgmt

  setSnapshot(snapshot: BoardSnapshot): void {
    this._snapshot.set(snapshot);
  }

  /**
   * Optimistically move a task within or across columns.
   *
   * <p>Returns the neighbor ids the caller needs to send to the backend.
   * Pure local update — no HTTP. Pair with {@link registerInFlight} and
   * {@link clearInFlight} around the API call.</p>
   *
   * @returns null if the task or target column couldn't be found.
   */
  moveTaskOptimistic(args: {
    taskId: Uuid;
    targetColumnId: Uuid;
    targetIndex: number;
  }): { beforeTaskId: Uuid | null; afterTaskId: Uuid | null; revert: () => void } | null {
    const s = this._snapshot();
    if (!s) return null;
    const task = s.tasks.find((t) => t.id === args.taskId);
    if (!task) return null;

    const fromColumnId = task.columnId;
    const fromPosition = task.position;

    // Build the destination bucket (excluding the moved task in case of intra-column).
    const destBucket = s.tasks
      .filter((t) => t.columnId === args.targetColumnId && t.id !== args.taskId)
      .sort((a, b) => a.position - b.position);

    const clampedIndex = Math.max(0, Math.min(args.targetIndex, destBucket.length));
    const before = clampedIndex > 0 ? destBucket[clampedIndex - 1]! : null;
    const after  = clampedIndex < destBucket.length ? destBucket[clampedIndex]! : null;

    // Synthesize a tentative position (midpoint) for the optimistic render. The
    // server returns the authoritative value and we upsert that on success.
    const tentativePosition = synthesizePosition(before?.position ?? null, after?.position ?? null);

    const updated: Task = {
      ...task,
      columnId: args.targetColumnId,
      position: tentativePosition,
      // version is intentionally NOT bumped optimistically — the server response carries the new value.
    };

    this._snapshot.update((curr) => curr && ({
      ...curr,
      tasks: curr.tasks.map((t) => (t.id === args.taskId ? updated : t)),
    }));

    const revert = () => {
      this._snapshot.update((curr) => curr && ({
        ...curr,
        tasks: curr.tasks.map((t) =>
          t.id === args.taskId ? { ...t, columnId: fromColumnId, position: fromPosition } : t,
        ),
      }));
    };

    return { beforeTaskId: before?.id ?? null, afterTaskId: after?.id ?? null, revert };
  }

  // ============================================================ trace tracking

  registerInFlight(traceId: Uuid, revert: () => void): void {
    this.inFlight.set(traceId, revert);
  }

  /** Idempotently clear an in-flight trace; safe to call multiple times. */
  clearInFlight(traceId: Uuid, delayMs = 0): void {
    if (delayMs > 0) {
      setTimeout(() => this.inFlight.delete(traceId), delayMs);
    } else {
      this.inFlight.delete(traceId);
    }
  }

  /** Roll back an optimistic change and clear it from the in-flight set. */
  revert(traceId: Uuid): void {
    const revertFn = this.inFlight.get(traceId);
    if (revertFn) revertFn();
    this.inFlight.delete(traceId);
  }

  /** True if the trace id corresponds to a mutation we initiated locally. */
  isOwnEcho(traceId: string | undefined): boolean {
    return !!traceId && this.inFlight.has(traceId);
  }

  // ============================================================ patch helpers

  upsertTask(task: Task): void {
    this._snapshot.update((curr) => {
      if (!curr) return curr;
      const idx = curr.tasks.findIndex((t) => t.id === task.id);
      if (idx === -1) return { ...curr, tasks: [...curr.tasks, task] };
      const next = curr.tasks.slice();
      next[idx] = task;
      return { ...curr, tasks: next };
    });
  }

  removeTask(taskId: Uuid): void {
    this._snapshot.update((curr) => curr && ({
      ...curr,
      tasks: curr.tasks.filter((t) => t.id !== taskId),
    }));
  }

  upsertColumn(column: BoardColumn): void {
    this._snapshot.update((curr) => {
      if (!curr) return curr;
      const idx = curr.columns.findIndex((c) => c.id === column.id);
      if (idx === -1) return { ...curr, columns: [...curr.columns, column] };
      const next = curr.columns.slice();
      next[idx] = column;
      return { ...curr, columns: next };
    });
  }

  removeColumn(columnId: Uuid): void {
    this._snapshot.update((curr) => curr && ({
      ...curr,
      columns: curr.columns.filter((c) => c.id !== columnId),
      tasks:   curr.tasks.filter((t) => t.columnId !== columnId),
    }));
  }
}

/**
 * Local-only midpoint estimate, mirroring the server's PositionCalculator
 * gap-based scheme. We don't need bit-perfect agreement — this is only used to
 * render order before the authoritative position arrives. Server's answer wins.
 */
function synthesizePosition(before: number | null, after: number | null): number {
  const GAP = 65_536;
  if (before === null && after === null) return GAP;
  if (before === null) return after! - GAP;
  if (after === null)  return before + GAP;
  return Math.floor((before + after) / 2);
}
