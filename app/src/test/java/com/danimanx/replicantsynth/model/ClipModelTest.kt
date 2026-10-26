package com.danimanx.replicantsynth.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipModelTest {

    @Test
    fun acidDefaultsCreatePlayablePattern() {
        val clip = Clip(0)
        applyKindDefaults(clip, ClipKind.ACID)
        assertEquals(ClipKind.ACID, clip.kind)
        assertEquals(36, clip.root)
        assertTrue(clip.steps.count { it.gate } >= 4)
        assertTrue(clip.param(AcidParam.CUTOFF) > 100f)
        assertTrue(clip.param(AcidParam.VOLUME) > 0f)
    }

    @Test
    fun drumDefaultsCreateKickAndHatPattern() {
        val clip = Clip(8)
        applyKindDefaults(clip, ClipKind.DRUMS)
        assertTrue(clip.drumOn(0, 0))
        assertTrue(clip.drumOn(0, 4))
        assertTrue(clip.drumOn(7, 2))
        assertFalse(clip.drumOn(0, 1))
    }

    @Test
    fun quantizeLabelsCycle() {
        assertEquals("OFF", quantizeLabel(0))
        assertEquals("1 BAR", quantizeLabel(16))
        assertEquals(4, nextQuantize(1))
        assertEquals(0, nextQuantize(64))
    }

    @Test
    fun fxDefaultsFillAllSixSlots() {
        val clip = Clip(2)
        clip.applyFxDefaults(1)
        assertEquals(1, clip.fxType)
        assertTrue(clip.fxEnabled)
        val info = fxById(1)
        for (i in 0 until 6) {
            assertEquals(info.defaults[i], clip.param(FxParam.BASE + i), 0.0001f)
        }
        assertEquals(1f, clip.param(FxParam.ENABLED), 0.0001f)
    }

    @Test
    fun transposeMovesRootAndSteps() {
        val clip = Clip(3)
        applyKindDefaults(clip, ClipKind.ACID)
        val before = clip.steps.filter { it.gate }.map { it.note }
        clip.root += 12
        clip.updateSteps(clip.steps.map { it.copy(note = it.note + 12) })
        val after = clip.steps.filter { it.gate }.map { it.note }
        assertEquals(before, after.map { it - 12 })
    }
}
