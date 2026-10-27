package com.danimanx.replicantsynth.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorNavTest {

    private fun clip(kind: ClipKind): Clip =
        Clip(0).also { applyKindDefaults(it, kind) }

    private fun titles(kind: ClipKind): List<String> = editorPages(clip(kind)).map { it.title }

    @Test
    fun emptyClipHasNoMenus() {
        assertTrue(editorPages(Clip(0)).isEmpty())
    }

    @Test
    fun menuRingPerKind() {
        assertEquals(
            listOf("CLIP", "SOUND", "STEPS", "FX", "PRESET"),
            titles(ClipKind.ACID),
        )
        assertEquals(
            listOf("CLIP", "SOUND", "STEPS", "FX", "PRESET"),
            titles(ClipKind.DRUMS),
        )
        assertEquals(
            listOf("CLIP", "SOUND", "PIANO", "FX", "PRESET"),
            titles(ClipKind.WAVE),
        )
        // Audio clips have gain/pitch but no factory bank and no pattern page.
        assertEquals(listOf("CLIP", "SOUND", "FX"), titles(ClipKind.SAMPLE))
    }

    @Test
    fun pageRingWrapsInBothDirections() {
        val pages = editorPages(clip(ClipKind.ACID))
        val nav = EditorNav()
        nav.movePage(-1, pages)
        assertEquals("PRESET", nav.pageTitle(pages))
        nav.movePage(1, pages)
        assertEquals("CLIP", nav.pageTitle(pages))
        nav.movePage(1, pages)
        assertEquals("SOUND", nav.pageTitle(pages))
    }

    @Test
    fun itemsClampInsteadOfWrapping() {
        val pages = editorPages(clip(ClipKind.ACID))
        val nav = EditorNav()
        repeat(pages.first().fields.size + 4) { nav.moveItem(1, pages) }
        assertEquals(pages.first().fields.lastIndex, nav.item)
        repeat(pages.first().fields.size + 4) { nav.moveItem(-1, pages) }
        assertEquals(0, nav.item)
    }

    @Test
    fun switchingPageKeepsItemInsideTheNewPage() {
        val pages = editorPages(clip(ClipKind.ACID))
        val nav = EditorNav()
        val clipPage = pages.first()
        repeat(clipPage.fields.size - 1) { nav.moveItem(1, pages) }
        assertEquals(clipPage.fields.lastIndex, nav.item)
        // The preset page only has PRESET + LOAD.
        nav.selectPage(pages.lastIndex, pages)
        assertEquals(1, nav.item)
        assertEquals(NavKey.LOAD_PRESET, nav.field(pages)?.key)
    }

    @Test
    fun numberFieldsReadTheClip() {
        val acid = clip(ClipKind.ACID)
        val pages = editorPages(acid)
        val fields = pages.first().fields
        val cursors = NavCursors()

        fun field(key: NavKey) = fields.first { it.key == key }

        assertEquals("LAUNCH", field(NavKey.LAUNCH).label)
        assertEquals("16/16", navValueText(acid, field(NavKey.LENGTH), cursors))
        assertEquals("80%", navValueText(acid, field(NavKey.GAIN), cursors))
        assertEquals("C", navValueText(acid, field(NavKey.PAN), cursors))
        assertEquals("OFF", navValueText(acid, field(NavKey.MUTE), cursors))
        acid.mute = true
        assertEquals("ON", navValueText(acid, field(NavKey.MUTE), cursors))
        acid.pan = -0.5f
        assertEquals("L50", navValueText(acid, field(NavKey.PAN), cursors))
    }

    @Test
    fun stepFieldsFollowTheCursors() {
        val acid = clip(ClipKind.ACID)
        val steps = editorPages(acid)[2].fields
        val cursors = NavCursors(step = 2, voice = 0)

        fun field(key: NavKey) = steps.first { it.key == key }

        assertEquals("3/16", navValueText(acid, field(NavKey.STEP), cursors))
        // Step 0 holds the first gate of the default acid line (root C2).
        val firstStep = cursors.copy(step = 0)
        assertEquals("C2", navValueText(acid, field(NavKey.NOTE), firstStep))
        assertEquals("ON", navValueText(acid, field(NavKey.GATE), firstStep))
        assertEquals("OFF", navValueText(acid, field(NavKey.GATE), cursors))

        val drums = clip(ClipKind.DRUMS)
        val drumFields = editorPages(drums)[2].fields
        fun drumField(key: NavKey) = drumFields.first { it.key == key }

        assertEquals("BD", navValueText(drums, drumField(NavKey.VOICE), cursors))
        assertEquals("ON", navValueText(drums, drumField(NavKey.HIT), firstStep))
        assertEquals("OFF", navValueText(drums, drumField(NavKey.HIT), cursors))
        assertEquals("120", navValueText(drums, drumField(NavKey.VELOCITY), firstStep))
        assertEquals(
            "SD",
            navValueText(drums, drumField(NavKey.VOICE), cursors.copy(voice = 1)),
        )
    }

    @Test
    fun pianoRollFieldsUseTheRollCursors() {
        val wave = clip(ClipKind.WAVE)
        val fields = editorPages(wave)[2].fields

        fun field(key: NavKey) = fields.first { it.key == key }

        val cursors = NavCursors(step = 4, rollNote = 60, rollLength = 4, rollVelocity = 90)
        assertEquals("5/16", navValueText(wave, field(NavKey.STEP), cursors))
        assertEquals("C4", navValueText(wave, field(NavKey.ROLL_NOTE), cursors))
        assertEquals("1/4", navValueText(wave, field(NavKey.MIDI_LENGTH), cursors))
        assertEquals("90", navValueText(wave, field(NavKey.VELOCITY), cursors))
        assertEquals("MINOR", navValueText(wave, field(NavKey.CHORD), cursors))
        assertEquals(4, midiLengthSteps(2))
        assertEquals(1, midiLengthSteps(9))
    }

    @Test
    fun fxPageTracksTheInsertAndMacros() {
        val clip = clip(ClipKind.WAVE)
        clip.applyFxDefaults(3)
        clip.setParam(FxParam.BASE + 1, 0.32f)
        val fields = editorPages(clip)[3].fields
        val cursors = NavCursors()

        fun field(key: NavKey, slot: Int = 0) =
            fields.first { it.key == key && it.slot == slot }

        assertEquals("ON", navValueText(clip, field(NavKey.FX_ON), cursors))
        assertEquals("ISO Isolator", navValueText(clip, field(NavKey.FX_SLOT), cursors))
        assertEquals(3, navChoiceIndex(clip, field(NavKey.FX_SLOT) as NavField.Choice, cursors))
        // Isolator factory defaults: LOW 0.55, MID 0.50, HIGH 0.55 …
        assertEquals("32%", navValueText(clip, field(NavKey.MACRO, 1), cursors))
        assertEquals("55%", navValueText(clip, field(NavKey.MACRO, 0), cursors))
        assertEquals(6, fields.count { it.key == NavKey.MACRO })
    }

    @Test
    fun presetPageMatchesTheFactoryBanks() {
        assertEquals(AcidPresets.COUNT, presetNamesFor(ClipKind.ACID).size)
        assertEquals(WAVE_PRESETS.size, presetNamesFor(ClipKind.WAVE).size)
        assertEquals(DrumPresets.all.size, presetNamesFor(ClipKind.DRUMS).size)
        assertTrue(presetNamesFor(ClipKind.SAMPLE).isEmpty())

        assertTrue(presetAt(ClipKind.ACID, 0) is AcidPreset)
        assertTrue(presetAt(ClipKind.WAVE, 0) is WavePreset)
        assertTrue(presetAt(ClipKind.DRUMS, 0) is DrumPreset)
        assertNull(presetAt(ClipKind.ACID, AcidPresets.COUNT))

        val clip = clip(ClipKind.WAVE)
        val page = editorPages(clip).last()
        assertEquals("PRESET", page.title)
        val choice = page.fields.first() as NavField.Choice
        assertEquals(WAVE_PRESETS[2].name, choice.options[2].substringAfter("· ").trim())
        assertEquals(
            WAVE_PRESETS[2].name,
            navValueText(clip, choice, NavCursors(presetIndex = 2)).substringAfter("· ").trim(),
        )
    }

    @Test
    fun cursorStaysValidAfterAKindChange() {
        val nav = EditorNav()
        val acid = clip(ClipKind.ACID)
        nav.selectPage(2, editorPages(acid))
        nav.selectItem(7, editorPages(acid))
        assertEquals(NavKey.CLEAR, nav.field(editorPages(acid))?.key)

        val wave = clip(ClipKind.WAVE)
        nav.clamp(editorPages(wave))
        assertEquals(2, nav.page)
        assertEquals(editorPages(wave)[2].fields.lastIndex, nav.item)
        assertFalse(nav.field(editorPages(wave)) == null)
    }


    @Test
    fun soundPageExposesTheEngineKnobs() {
        val acid = clip(ClipKind.ACID)
        val sound = editorPages(acid)[1]
        assertEquals("SOUND", sound.title)
        assertEquals(
            listOf("CUTOFF", "RESO", "ENV MOD", "DECAY", "ACCENT", "VOLUME"),
            sound.fields.map { it.label },
        )
        assertTrue(sound.fields.all { it.key == NavKey.SYNTH })
        assertEquals(synthKnobs(ClipKind.ACID).size, sound.fields.size)
        assertEquals(6, synthKnobs(ClipKind.WAVE).size)
        assertEquals(6, synthKnobs(ClipKind.DRUMS).size)
        assertEquals(2, synthKnobs(ClipKind.SAMPLE).size)

        val cutoff = sound.fields.first() as NavField.Number
        val cursors = NavCursors()
        assertEquals("420", navValueText(acid, cutoff, cursors))
        acid.setParam(AcidParam.CUTOFF, 1200f)
        assertEquals("1.20k", navValueText(acid, cutoff, cursors))
    }

    @Test
    fun stripsAreMasterPadGainAndTheFocusedPage() {
        val acid = clip(ClipKind.ACID)
        val pages = editorPages(acid)
        val cursors = NavCursors()

        // SOUND page: strips 2..7 are the six synth knobs.
        val sound = stripLayout(acid, 0.85f, pages, 1, cursors)
        assertEquals(STRIP_SLOTS, sound.size)
        assertEquals("MASTER", sound[STRIP_MASTER].label)
        assertEquals(StripTarget.MasterGain, sound[STRIP_MASTER].target)
        assertEquals("PAD", sound[STRIP_PAD_GAIN].label)
        assertEquals(StripTarget.PadGain, sound[STRIP_PAD_GAIN].target)
        assertEquals(
            listOf("CUTOFF", "RESO", "ENV MOD", "DECAY", "ACCENT", "VOLUME"),
            sound.subList(1, 7).map { it.label },
        )
        assertTrue(sound.subList(1, 7).all { it.target is StripTarget.NavNumber })
        assertEquals(0.85f / STRIP_GAIN_MAX, sound[STRIP_MASTER].value, 0.0001f)

        // FX page: the six macros.
        val fx = stripLayout(acid, 0.85f, pages, 3, cursors)
        assertEquals(
            listOf("MACRO 1", "MACRO 2", "MACRO 3", "MACRO 4", "MACRO 5", "MACRO 6"),
            fx.subList(1, 7).map { it.label },
        )
        assertEquals(StripTone.MAGENTA, fx[1].tone)

        // CLIP page has only four continuous fields: the rest stays idle.
        val clipPage = stripLayout(acid, 0.85f, pages, 0, cursors)
        assertEquals(listOf("LENGTH", "ROOT", "GAIN", "PAN"), clipPage.subList(1, 5).map { it.label })
        assertEquals(StripTarget.Idle, clipPage[5].target)
        assertEquals(StripTarget.Idle, clipPage[6].target)
        assertTrue(clipPage[4].bipolar)   // PAN

        // PRESET page has none.
        val preset = stripLayout(acid, 0.85f, pages, pages.lastIndex, cursors)
        (1..6).forEach { assertEquals(StripTarget.Idle, preset[it].target) }
    }

    @Test
    fun stripPositionRoundTripsThroughTheFieldRange() {
        val cutoff = NavField.Number(NavKey.SYNTH, "CUTOFF", 40f, 4200f, 10f)
        assertEquals(40f, stripToFieldValue(cutoff, 0f), 0.001f)
        assertEquals(4200f, stripToFieldValue(cutoff, 1f), 0.001f)
        assertEquals(2120f, stripToFieldValue(cutoff, 0.5f), 1f)
        val pan = NavField.Number(NavKey.PAN, "PAN", -1f, 1f, 0.1f)
        assertEquals(0f, stripToFieldValue(pan, 0.5f), 0.001f)
        assertEquals(-1f, stripToFieldValue(pan, 0f), 0.001f)
    }

    @Test
    fun everyPageHasExactlyOneFocusableField() {
        ClipKind.entries.filter { it != ClipKind.EMPTY }.forEach { kind ->
            editorPages(clip(kind)).forEach { page ->
                assertTrue("${kind.name}/${page.title} is empty", page.fields.isNotEmpty())
                assertTrue(
                    "${kind.name}/${page.title} title too long for the OLED",
                    page.title.length <= 8,
                )
                page.fields.forEach { field ->
                    assertTrue(field.label.isNotBlank())
                    if (field is NavField.Number) {
                        assertTrue(field.label, field.min <= field.max)
                        assertTrue(field.label, field.step > 0f)
                    }
                }
            }
        }
    }
}
