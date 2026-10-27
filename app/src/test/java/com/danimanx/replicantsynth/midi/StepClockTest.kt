package com.danimanx.replicantsynth.midi

import org.junit.Assert.assertEquals
import org.junit.Test

class StepClockTest {

    // 120 BPM: a 16th note is 125 ms.
    private val stepMs = 125f

    @Test
    fun firstNoteStartsAtBarStart() {
        val clock = StepClock(16)
        assertEquals(0, clock.tick(10_000L, stepMs))
        assertEquals(0, clock.tick(10_050L, stepMs))
    }

    @Test
    fun advancesByWholeStepsAndWraps() {
        val clock = StepClock(16)
        clock.tick(0L, stepMs)
        assertEquals(1, clock.tick(125L, stepMs))
        assertEquals(2, clock.tick(250L, stepMs))
        assertEquals(3, clock.tick(375L, stepMs))
        // Two steps in one gap still lands on the right index.
        assertEquals(5, clock.tick(625L, stepMs))
    }

    @Test
    fun wrapsAfterSixteenSteps() {
        val clock = StepClock(16)
        clock.tick(0L, stepMs)
        var step = 0
        repeat(16) { i ->
            step = clock.tick((i + 1) * 125L, stepMs)
        }
        assertEquals(0, step)
    }

    @Test
    fun resetRestartsTheBar() {
        val clock = StepClock(16)
        clock.tick(0L, stepMs)
        clock.tick(500L, stepMs)
        clock.reset()
        assertEquals(0, clock.tick(900L, stepMs))
        assertEquals(0, clock.current)
    }

    @Test
    fun tempoChangeKeepsTheStepCount() {
        val clock = StepClock(16)
        clock.tick(0L, stepMs)
        clock.tick(250L, stepMs)          // 2 steps at 120 BPM
        // 60 BPM: 250 ms per 16th, so one more step after 250 ms.
        assertEquals(3, clock.tick(500L, 250f))
    }

    @Test
    fun seededClockStartsWhereTheCursorIs() {
        val clock = StepClock(16)
        clock.seed(7)
        assertEquals(7, clock.tick(0L, stepMs))
        assertEquals(8, clock.tick(125L, stepMs))
        assertEquals(9, clock.tick(250L, stepMs))
    }

    @Test
    fun seedWrapsAndDoesNotRun() {
        val clock = StepClock(16)
        clock.seed(20)
        assertEquals(4, clock.current)
        assertEquals(4, clock.tick(0L, stepMs))
    }
}
