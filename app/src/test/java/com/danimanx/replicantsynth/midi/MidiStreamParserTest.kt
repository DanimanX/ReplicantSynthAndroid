package com.danimanx.replicantsynth.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiStreamParserTest {

    @Test
    fun parsesNoteOnAcrossPackets() {
        val parser = MidiStreamParser()
        assertTrue(parser.feed(byteArrayOf(0x90.toByte()), 0, 1).isEmpty())
        assertTrue(parser.feed(byteArrayOf(60), 0, 1).isEmpty())
        val messages = parser.feed(byteArrayOf(100), 0, 1)
        assertEquals(1, messages.size)
        val note = messages[0] as MidiMessage.NoteOn
        assertEquals(60, note.note)
        assertEquals(100, note.velocity)
    }

    @Test
    fun runningStatusProducesBothNotes() {
        val parser = MidiStreamParser()
        val messages = parser.feed(
            byteArrayOf(0x90.toByte(), 60, 100, 62, 90),
            0,
            5,
        )
        assertEquals(2, messages.size)
        assertEquals(62, (messages[1] as MidiMessage.NoteOn).note)
    }

    @Test
    fun zeroVelocityIsNoteOff() {
        val parser = MidiStreamParser()
        val messages = parser.feed(byteArrayOf(0x90.toByte(), 60, 0), 0, 3)
        assertTrue(messages[0] is MidiMessage.NoteOff)
    }

    @Test
    fun parsesControlChangeAndPitchBend() {
        val parser = MidiStreamParser()
        val cc = parser.feed(byteArrayOf(0xB0.toByte(), 90, 127), 0, 3)
        assertEquals(90, (cc[0] as MidiMessage.ControlChange).controller)
        val bend = parser.feed(byteArrayOf(0xE0.toByte(), 0, 64), 0, 3)
        assertEquals(0, (bend[0] as MidiMessage.PitchBend).value)
    }

    @Test
    fun parsesFragmentedSysex() {
        val parser = MidiStreamParser()
        assertTrue(parser.feed(byteArrayOf(0xF0.toByte(), 0x47, 0x00), 0, 3).isEmpty())
        val messages = parser.feed(
            byteArrayOf(0x53, 0x19, 0x00, 0x01, 0x00, 0xF7.toByte()),
            0,
            6,
        )
        val sysex = messages[0] as MidiMessage.SysEx
        assertEquals(9, sysex.bytes.size)
        assertEquals(0xF0.toByte(), sysex.bytes.first())
        assertEquals(0xF7.toByte(), sysex.bytes.last())
    }

    @Test
    fun realtimeClockDoesNotBreakRunningStatus() {
        val parser = MidiStreamParser()
        val messages = parser.feed(
            byteArrayOf(0x90.toByte(), 60, 100, 0xF8.toByte(), 62, 100),
            0,
            6,
        )
        assertEquals(3, messages.size)
        assertTrue(messages[1] is MidiMessage.Clock)
        assertEquals(62, (messages[2] as MidiMessage.NoteOn).note)
    }
}
