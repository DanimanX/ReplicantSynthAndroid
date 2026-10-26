package com.danimanx.replicantsynth.midi

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteEntryTest {

    @Test
    fun simultaneousChordNotesShareOneStep() {
        val cursor = NoteGroupCursor(steps = 16, windowMs = 140)
        assertEquals(0, cursor.press(1_000, 1))
        assertEquals(0, cursor.press(1_008, 1))
        assertEquals(0, cursor.press(1_021, 1))
        // A later key press starts a new step.
        assertEquals(1, cursor.press(1_400, 1))
        assertEquals(2, cursor.press(1_800, 1))
    }

    @Test
    fun groupLengthControlsTheAdvance() {
        val cursor = NoteGroupCursor(steps = 16, windowMs = 100)
        assertEquals(0, cursor.press(0, 4))
        assertEquals(0, cursor.press(10, 4))
        assertEquals(4, cursor.press(500, 4))
        assertEquals(8, cursor.press(900, 2))
        assertEquals(10, cursor.press(1_300, 2))
    }

    @Test
    fun resetStartsASingleGroupAtTheTappedStep() {
        val cursor = NoteGroupCursor(steps = 16)
        cursor.press(0, 2)
        cursor.press(1_000, 2)
        cursor.reset(9)
        assertEquals(9, cursor.press(2_000, 1))
        assertEquals(9, cursor.press(2_010, 1))
        assertEquals(10, cursor.press(2_500, 1))
    }

    @Test
    fun cursorWrapsAtTheLoopBoundary() {
        val cursor = NoteGroupCursor(steps = 16, windowMs = 50)
        var step = 0
        repeat(20) { index ->
            step = cursor.press(index * 100L, 1)
        }
        assertEquals(3, step)
    }
}
