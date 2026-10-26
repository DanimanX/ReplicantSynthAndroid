package com.danimanx.replicantsynth.midi

/**
 * Cursor logic for APC64 key input.
 *
 * A chord arrives as several note-on messages within a few milliseconds. Those
 * notes must share one step, while a new key press after a short gap advances
 * the cursor by the group's length. The 60 ms window is wide enough for USB
 * chord jitter and still shorter than a fast 16th-note run.
 */
class NoteGroupCursor(
    private val steps: Int = 16,
    private val windowMs: Long = 60L,
) {
    var cursor: Int = 0
        private set

    private var lastPressAt: Long = 0L
    private var active: Boolean = false
    private var groupLength: Int = 1

    /** Returns the step for this note and advances only on a new key group. */
    fun press(nowMs: Long, length: Int): Int {
        val requested = length.coerceIn(1, steps)
        val newGroup = !active || nowMs - lastPressAt > windowMs
        if (newGroup) {
            if (active) cursor = (cursor + groupLength).mod(steps)
            groupLength = requested
            active = true
        }
        lastPressAt = nowMs
        return cursor
    }

    /** Jump the cursor (a tap in the piano roll or the +/- buttons). */
    fun reset(cursor: Int) {
        this.cursor = cursor.coerceIn(0, steps - 1)
        active = false
        groupLength = 1
        lastPressAt = 0L
    }
}
