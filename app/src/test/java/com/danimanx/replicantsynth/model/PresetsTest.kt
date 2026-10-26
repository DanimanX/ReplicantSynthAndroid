package com.danimanx.replicantsynth.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetsTest {

    @Test
    fun waveBankMatchesTheDesktopFactory() {
        assertEquals(44, WAVE_PRESETS.size)
        assertEquals(44, WAVE_PRESETS.map { it.name }.distinct().size)
        for (preset in WAVE_PRESETS) {
            assertTrue(preset.name.isNotBlank())
            assertTrue(preset.category.isNotBlank())
            assertTrue(preset.params.size >= 17)
            assertTrue(preset.params.all { it.isFinite() })
            assertTrue(preset.notes.isNotEmpty())
            assertTrue(preset.notes.all { it.note in 0..127 && it.start in 0..15 })
        }
        assertEquals("Night City Bass", WAVE_PRESETS.first().name)
        assertEquals("Bass", WAVE_PRESETS.first().category)
    }

    @Test
    fun acidBankHasEightBanksOfSixtyFour() {
        assertEquals(512, AcidPresets.COUNT)
        val all = AcidPresets.all
        assertEquals(512, all.size)
        assertEquals("Amber Line", all[0].name)
        assertEquals("Last Replicant", all[7 * 64].name)
        assertEquals(8, all.map { it.bank }.distinct().size)
        for (preset in all) {
            assertEquals(ClipLimits.PARAMS, preset.params.size)
            assertEquals(ClipLimits.STEPS, preset.steps.size)
            assertTrue(preset.length in 1..16)
            assertTrue(preset.root in 24..127)
        }
    }

    @Test
    fun acidBankMatchesTheDesktopGenerator() {
        // Values dumped from Replicant303 PresetBank (bank 3 = BLADE, slot 17).
        val preset = AcidPresets.preset(3, 17)
        assertEquals("Midnight Bishop", preset.name)
        assertEquals(16, preset.length)
        assertEquals(564.275f, preset.params[AcidParam.CUTOFF], 0.01f)
        assertEquals(0.760f, preset.params[AcidParam.RESONANCE], 0.001f)
        assertEquals(0.523f, preset.params[AcidParam.ENV_MOD], 0.001f)
        assertEquals(0.643f, preset.params[AcidParam.DECAY], 0.001f)
        assertEquals(0.778f, preset.params[AcidParam.ACCENT], 0.001f)
        assertEquals(3f, preset.params[AcidParam.DIST_TYPE], 0.001f)
        assertEquals(0.506f, preset.params[AcidParam.DIST_DRIVE], 0.001f)
        assertEquals(0.319f, preset.params[AcidParam.DIST_TONE], 0.001f)
        assertEquals(0.822f, preset.params[AcidParam.DIST_MIX], 0.001f)
        val notes = intArrayOf(39, 48, 42, 42, 46, 39, 24, 44, 42, 34, 34, 37, 39, 32, 37, 39)
        val gates = "1111110111111111"
        assertEquals(notes.toList(), preset.steps.map { it.note })
        assertEquals(gates, preset.steps.joinToString("") { if (it.gate) "1" else "0" })
        assertEquals(listOf(2, 3, 11, 13), preset.steps.indices.filter { preset.steps[it].accent })
        assertEquals(listOf(3, 9, 12), preset.steps.indices.filter { preset.steps[it].slide })
    }

    @Test
    fun acidBankIsDeterministic() {
        val first = AcidPresets.preset(3, 17)
        val second = AcidPresets.preset(3, 17)
        assertEquals(first.name, second.name)
        assertEquals(first.length, second.length)
        assertEquals(first.root, second.root)
        assertEquals(first.steps, second.steps)
        for (i in 0 until ClipLimits.PARAMS) {
            assertEquals(first.params[i], second.params[i], 0.0001f)
        }
    }

    @Test
    fun drumBankHasTheEightClassicPatterns() {
        assertEquals(8, DrumPresets.all.size)
        assertEquals("Classic House", DrumPresets.all.first().name)
        for (preset in DrumPresets.all) {
            assertEquals(ClipLimits.DRUM_VOICES, preset.masks.size)
            assertEquals(ClipLimits.DRUM_VOICES * ClipLimits.STEPS, preset.velocities.size)
            assertTrue(preset.accent in 0f..1f)
        }
        val house = DrumPresets.all.first()
        assertTrue(house.masks[0] and 1 != 0)
        assertTrue(house.masks[0] and (1 shl 4) != 0)
    }

    @Test
    fun applyingPresetsKeepsTheFxInsert() {
        val clip = Clip(4)
        applyKindDefaults(clip, ClipKind.WAVE)
        clip.gain = 0.42f
        clip.applyFxDefaults(9)
        val fxType = clip.fxType
        val fxParam = clip.param(FxParam.BASE)
        clip.applyWavePreset(WAVE_PRESETS.first())
        assertEquals(ClipKind.WAVE, clip.kind)
        assertEquals("Night City Bass", clip.name)
        assertEquals(fxType, clip.fxType)
        assertTrue(clip.fxEnabled)
        assertEquals(fxParam, clip.param(FxParam.BASE), 0.0001f)
        assertEquals(0.42f, clip.gain, 0.0001f)
        assertTrue(clip.midiNotes.isNotEmpty())
        assertTrue(clip.steps.none { it.gate })

        val drums = Clip(5)
        drums.applyFxDefaults(3)
        val drumFx = drums.fxType
        drums.applyDrumPreset(DrumPresets.all.first())
        assertEquals(ClipKind.DRUMS, drums.kind)
        assertEquals(drumFx, drums.fxType)
        assertTrue(drums.drumOn(0, 0))

        val acid = Clip(6)
        acid.applyAcidPreset(AcidPresets.all[65])
        assertEquals(ClipKind.ACID, acid.kind)
        assertTrue(acid.steps.any { it.gate })
        assertFalse(acid.midiNotes.isNotEmpty())
    }
}
