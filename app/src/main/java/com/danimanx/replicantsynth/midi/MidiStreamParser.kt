package com.danimanx.replicantsynth.midi

import java.io.ByteArrayOutputStream

sealed class MidiMessage {
    data class NoteOn(val channel: Int, val note: Int, val velocity: Int) : MidiMessage()
    data class NoteOff(val channel: Int, val note: Int, val velocity: Int) : MidiMessage()
    data class ControlChange(val channel: Int, val controller: Int, val value: Int) : MidiMessage()
    data class PolyPressure(val channel: Int, val note: Int, val pressure: Int) : MidiMessage()
    data class ChannelPressure(val channel: Int, val pressure: Int) : MidiMessage()
    data class PitchBend(val channel: Int, val value: Int) : MidiMessage()
    data class SysEx(val bytes: ByteArray) : MidiMessage()
    data class SongPosition(val position: Int) : MidiMessage()
    data class Other(val status: Int) : MidiMessage()
    data object Clock : MidiMessage()
    data object Start : MidiMessage()
    data object Continue : MidiMessage()
    data object Stop : MidiMessage()
}

/**
 * Incremental MIDI 1.0 byte-stream parser. USB MIDI delivers packets that can
 * split a message in half, and running status is common on controllers, so the
 * parser keeps both the partial data bytes and the last channel status.
 */
class MidiStreamParser {

    private val sysex = ByteArrayOutputStream()
    private val data = IntArray(2)
    private var dataCount = 0
    private var runningStatus = 0
    private var inSysex = false

    fun reset() {
        sysex.reset()
        dataCount = 0
        runningStatus = 0
        inSysex = false
    }

    fun feed(buffer: ByteArray, offset: Int, count: Int): List<MidiMessage> {
        val out = ArrayList<MidiMessage>(4)
        var i = offset
        val end = (offset + count).coerceAtMost(buffer.size)
        while (i < end) {
            val byte = buffer[i].toInt() and 0xFF
            i++
            when {
                byte == 0xF7 -> {
                    if (inSysex) {
                        sysex.write(byte)
                        out += MidiMessage.SysEx(sysex.toByteArray())
                        sysex.reset()
                        inSysex = false
                    }
                }
                byte == 0xF0 -> {
                    sysex.reset()
                    sysex.write(byte)
                    inSysex = true
                    dataCount = 0
                    runningStatus = 0
                }
                inSysex -> {
                    if (byte and 0x80 == 0) {
                        sysex.write(byte)
                    } else {
                        // A non-realtime status aborts an unfinished SysEx.
                        sysex.reset()
                        inSysex = false
                        runningStatus = byte
                        dataCount = 0
                    }
                }
                byte >= 0xF8 -> {
                    when (byte) {
                        0xF8 -> out += MidiMessage.Clock
                        0xFA -> out += MidiMessage.Start
                        0xFB -> out += MidiMessage.Continue
                        0xFC -> out += MidiMessage.Stop
                        else -> Unit
                    }
                }
                byte >= 0xF0 -> {
                    runningStatus = 0
                    dataCount = 0
                    when (byte) {
                        0xF2 -> {
                            dataCount = 0
                            runningStatus = 0xF2
                        }
                        0xF1, 0xF3, 0xF6 -> Unit
                        else -> out += MidiMessage.Other(byte)
                    }
                }
                byte and 0x80 != 0 -> {
                    runningStatus = byte
                    dataCount = 0
                }
                else -> {
                    if (runningStatus == 0) continue
                    if (runningStatus == 0xF2) {
                        // Song position: two data bytes.
                        if (dataCount < 2) {
                            data[dataCount++] = byte
                            if (dataCount == 2) {
                                out += MidiMessage.SongPosition(data[0] or (data[1] shl 7))
                                dataCount = 0
                                runningStatus = 0
                            }
                        }
                        continue
                    }
                    val needed = dataBytesFor(runningStatus)
                    data[dataCount++] = byte
                    if (dataCount >= needed) {
                        emit(runningStatus, out)
                        dataCount = 0
                    }
                }
            }
        }
        return out
    }

    private fun dataBytesFor(status: Int): Int = when (status and 0xF0) {
        0xC0, 0xD0 -> 1
        else -> 2
    }

    private fun emit(status: Int, out: MutableList<MidiMessage>) {
        val type = status and 0xF0
        val channel = status and 0x0F
        when (type) {
            0x80 -> out += MidiMessage.NoteOff(channel, data[0], data[1])
            0x90 -> {
                if (data[1] == 0) out += MidiMessage.NoteOff(channel, data[0], 0)
                else out += MidiMessage.NoteOn(channel, data[0], data[1])
            }
            0xA0 -> out += MidiMessage.PolyPressure(channel, data[0], data[1])
            0xB0 -> out += MidiMessage.ControlChange(channel, data[0], data[1])
            0xC0 -> out += MidiMessage.Other(status)
            0xD0 -> out += MidiMessage.ChannelPressure(channel, data[0])
            0xE0 -> out += MidiMessage.PitchBend(channel, (data[0] or (data[1] shl 7)) - 8192)
            else -> out += MidiMessage.Other(status)
        }
    }
}
