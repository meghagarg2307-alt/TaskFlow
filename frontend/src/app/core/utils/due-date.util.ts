/** Maps API instant to {@code <input type="date">} value (calendar day, UTC-stable). */
export function toDateInputValue(iso?: string): string {
  if (!iso) return '';
  const d = new Date(iso);
  const y = d.getUTCFullYear();
  const m = String(d.getUTCMonth() + 1).padStart(2, '0');
  const day = String(d.getUTCDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/** Noon UTC on the chosen day — avoids off-by-one day bugs across timezones. */
export function fromDateInputValue(value: string): string {
  const [y, m, d] = value.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d, 12, 0, 0, 0)).toISOString();
}

export function isDueDateOverdue(iso: string): boolean {
  const d = new Date(iso);
  const end = Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate(), 23, 59, 59, 999);
  return end < Date.now();
}

export function formatDueDateDisplay(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
}
