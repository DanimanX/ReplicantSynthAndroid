package com.danimanx.replicantsynth.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectStoreTest {

    @Test
    fun roundTripsProjectAndClipState() {
        val project = ProjectState()
        project.name = "TEST GRID"
        project.bpm = 133f
        project.quantize = 4
        project.swing = 0.22f
        project.masterGain = 0.7f

        val acid = project.clips[0]
        applyKindDefaults(acid, ClipKind.ACID)
        acid.name = "ACID TEST"
        acid.root = 41
        acid.gain = 0.66f
        acid.pan = -0.25f
        acid.setParam(AcidParam.CUTOFF, 777f)
        acid.setStep(3, Step(gate = true, note = 55, velocity = 120, accent = true, slide = true))
        acid.applyFxDefaults(12)

        val drums = project.clips[5]
        applyKindDefaults(drums, ClipKind.DRUMS)
        drums.toggleDrumStepForTest(0, 2)
        drums.setDrumVelocity(0, 2, 121)

        val decoded = ProjectStore.decode(ProjectStore.encode(project))
        assertEquals("TEST GRID", decoded.name)
        assertEquals(133f, decoded.bpm, 0.001f)
        assertEquals(4, decoded.quantize)
        assertEquals(0.22f, decoded.swing, 0.0001f)
        assertEquals("ACID TEST", decoded.clips[0].name)
        assertEquals(41, decoded.clips[0].root)
        assertEquals(777f, decoded.clips[0].param(AcidParam.CUTOFF), 0.001f)
        assertEquals(12, decoded.clips[0].fxType)
        assertTrue(decoded.clips[0].steps[3].gate)
        assertTrue(decoded.clips[0].steps[3].accent)
        assertTrue(decoded.clips[0].steps[3].slide)
        assertEquals(55, decoded.clips[0].steps[3].note)

        assertTrue(decoded.clips[5].drumOn(0, 2))
        assertEquals(121, decoded.clips[5].drumVelocity(0, 2))
        assertEquals(ClipKind.DRUMS, decoded.clips[5].kind)
    }

    @Test
    fun missingSampleIsMarked() {
        val project = ProjectState()
        val clip = project.clips[3]
        applyKindDefaults(clip, ClipKind.SAMPLE)
        clip.samplePath = "/does/not/exist/neon.wav"
        clip.sampleName = "NEON"
        val decoded = ProjectStore.decode(ProjectStore.encode(project))
        assertTrue(decoded.clips[3].sampleMissing)
    }

    private fun Clip.toggleDrumStepForTest(voice: Int, step: Int) {
        setDrumStep(voice, step, !drumOn(voice, step))
    }
}
