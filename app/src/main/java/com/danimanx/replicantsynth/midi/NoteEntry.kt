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

/**
 * Free-running 16th-note clock for step input.
 *
 * The APC64's Step Sequencer is its own transport: when it plays, its notes
 * arrive as MIDI and the app has no audio phase for them. This clock turns the
 * wall-clock gap between notes into a step index so a captured sequence keeps its
 * rhythm instead of collapsing onto one step. It is only used when the app's own
 * transport is not playing the clip, in which case the engine phase wins.
 */
class StepClock(private val steps: Int = 16) {
    private var lastMs: Long = 0L
    private var step: Int = 0
    private var running: Boolean = false

    val current: Int get() = step

    /**
     * Advance by whole 16th notes since the previous call. The first note lands
     * on step 0 and restarts the clock, so a captured take always begins at bar
     * start regardless of how long the unit has been running.
     */
    fun tick(nowMs: Long, stepMs: Float): Int {
        val period = stepMs.coerceAtLeast(1f)
        if (!running) {
            // First note after reset/seed: keep the seeded position.
            running = true
            lastMs = nowMs
            return step
        }
        val elapsed = (nowMs - lastMs).coerceAtLeast(0L)
        val advance = (elapsed / period).toInt()
        if (advance > 0) {
            step = (step + advance).mod(steps)
            lastMs += (advance * period).toLong()
        }
        return step
    }

    fun reset() {
        running = false
        lastMs = 0L
        step = 0
    }

    /** Place the clock on a step without starting it (streams start from here). */
    fun seed(step: Int) {
        running = false
        lastMs = 0L
        this.step = step.mod(steps)
    }
}
