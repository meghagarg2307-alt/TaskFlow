package io.taskflow.common.order;

/**
 * Computes BIGINT positions for the gap-based ordering scheme used by board columns
 * and tasks within a column.
 *
 * <p><b>Why BIGINT gaps and not floats?</b> Floats lose precision after ~52 bisections
 * and turn into a nightmare to debug. BIGINT with a generous initial step
 * ({@link #STEP}) gives us ~63 safe in-place bisections per slot before two siblings
 * become adjacent integers. After that, a rebalance is required — see
 * {@link #needsRebalance(long, long)}.</p>
 *
 * <p>This is the same scheme Trello, Linear, and Asana use (with minor variations).
 * It is what makes drag-and-drop O(1) at the write path: moving a task only updates
 * <em>that one row</em>, never its neighbors.</p>
 */
public final class PositionCalculator {

    /** Initial spacing between siblings. 2^16 leaves room for 16+ middle insertions. */
    public static final long STEP = 1L << 16; // 65 536

    private PositionCalculator() {}

    /** Position for the first item ever placed in an empty list. */
    public static long first() {
        return STEP;
    }

    /** Position right after {@code last} (append). */
    public static long after(long last) {
        return last + STEP;
    }

    /** Position right before {@code first} (prepend). */
    public static long before(long first) {
        return first - STEP;
    }

    /**
     * Position strictly between {@code before} and {@code after}. Caller must ensure
     * {@code before < after}; we don't sort for them because that hides bugs.
     *
     * @throws IllegalStateException when the slot is exhausted — caller should trigger
     *         a column-wide rebalance and retry.
     */
    public static long between(long before, long after) {
        if (before >= after) {
            throw new IllegalArgumentException(
                    "before (" + before + ") must be strictly less than after (" + after + ")");
        }
        if (needsRebalance(before, after)) {
            throw new IllegalStateException("Position slot exhausted; rebalance required");
        }
        // Math.floorDiv to be deterministic on negatives if we ever allow before<0.
        return before + Math.floorDiv(after - before, 2);
    }

    /** True if no integer position exists strictly between {@code before} and {@code after}. */
    public static boolean needsRebalance(long before, long after) {
        return after - before <= 1;
    }
}
