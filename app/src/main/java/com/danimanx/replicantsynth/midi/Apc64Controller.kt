package com.danimanx.replicantsynth.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class Apc64DeviceInfo(val id: Int, val name: String)

data class Apc64State(
    val available: Boolean = false,
    val devices: List<Apc64DeviceInfo> = emptyList(),
    val connectedId: Int? = null,
    val connectedName: String? = null,
    val lastMessage: String = "",
    val error: String? = null,
    val padBaseNote: Int = Apc64Codec.PAD_BASE_DAW,
    val flipRows: Boolean = true,
    val ledFeedback: Boolean = true,
) {
    val connected: Boolean get() = connectedId != null
}

sealed class Apc64Event {
    data class Pad(val pad: Int, val velocity: Int, val pressed: Boolean) : Apc64Event()
    data class Scene(val row: Int) : Apc64Event()
    data class Transport(val action: TransportAction) : Apc64Event()
    data class Encoder(val delta: Int) : Apc64Event()
    data class TouchStrip(val index: Int, val value: Float) : Apc64Event()
    data class TrackSelect(val column: Int) : Apc64Event()
    data class Mute(val pressed: Boolean) : Apc64Event()
    data class Solo(val pressed: Boolean) : Apc64Event()
    data class Shift(val pressed: Boolean) : Apc64Event()
    data object ClipStop : Apc64Event()
}

enum class TransportAction { PLAY, STOP, RECORD }

/**
 * USB MIDI connection and APC64 profile. Detection is by the USB MIDI device
 * name; the controller sends the Akai mode SysEx on connect so the 64-pad
 * surface is unlocked without Ableton Live.
 */
class Apc64Controller(
    context: Context,
    private val onEvent: (Apc64Event) -> Unit,
) {
    private val appContext = context.applicationContext
    private val midiManager =
        appContext.getSystemService(Context.MIDI_SERVICE) as? MidiManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = appContext.getSharedPreferences("apc64", Context.MODE_PRIVATE)
    private val parser = MidiStreamParser()
    private val parserLock = Any()
    private val sendLock = Any()

    private val _state = MutableStateFlow(
        Apc64State(
            available = midiManager != null,
            padBaseNote = prefs.getInt("padBase", Apc64Codec.PAD_BASE_DAW),
            flipRows = prefs.getBoolean("flipRows", true),
            ledFeedback = prefs.getBoolean("ledFeedback", true),
        ),
    )
    val state: StateFlow<Apc64State> = _state.asStateFlow()

    private var device: MidiDevice? = null
    private var sendPort: MidiInputPort? = null
    private var receivePort: MidiOutputPort? = null
    private var receiver: MidiReceiver? = null
    private var savedDeviceName: String? = prefs.getString("deviceName", null)
    private var started = false

    private val ledCache = IntArray(Apc64Codec.PAD_COUNT) { -1 }
    private val sceneCache = IntArray(8) { -1 }
    private var transportCache = -1
    private var lastRefreshMs = 0L

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) {
            scanDevices()
            if (!_state.value.connected && isApc64(device)) {
                connect(device.id)
            }
        }

        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            if (_state.value.connectedId == device.id) {
                disconnect()
            }
            scanDevices()
        }
    }

    fun start() {
        if (started) {
            scanDevices()
            return
        }
        started = true
        if (midiManager == null) {
            _state.update {
                it.copy(available = false, error = "Android MIDI service unavailable")
            }
            return
        }
        midiManager.registerDeviceCallback(deviceCallback, mainHandler)
        scanDevices()
        val target = _state.value.devices.firstOrNull { it.name.contains("APC64", true) }
            ?: _state.value.devices.firstOrNull { it.name == savedDeviceName }
        if (target != null) connect(target.id)
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { midiManager?.unregisterDeviceCallback(deviceCallback) }
        disconnect()
        _state.update { it.copy(devices = emptyList(), connectedId = null, connectedName = null) }
    }

    fun scanDevices() {
        val manager = midiManager ?: return
        val devices = runCatching {
            manager.devices.map { info ->
                Apc64DeviceInfo(info.id, deviceName(info))
            }.sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList())
        _state.update { it.copy(available = true, devices = devices) }
        if (_state.value.connectedId != null && devices.none { it.id == _state.value.connectedId }) {
            disconnect()
        }
    }

    fun connect(deviceId: Int) {
        val manager = midiManager ?: return
        val info = runCatching { manager.devices.firstOrNull { it.id == deviceId } }
            .getOrNull() ?: return
        disconnect()
        _state.update { it.copy(error = null, connectedId = null, connectedName = null) }
        runCatching {
            manager.openDevice(info, { opened ->
                if (opened == null) {
                    _state.update { it.copy(error = "Could not open ${deviceName(info)}") }
                    return@openDevice
                }
                openPorts(opened, info)
            }, mainHandler)
        }.onFailure {
            _state.update { st -> st.copy(error = it.message ?: "MIDI open failed") }
        }
    }

    private fun openPorts(opened: MidiDevice, info: MidiDeviceInfo) {
        var inputIndex = -1
        var outputIndex = -1
        for (port in info.ports) {
            when (port.type) {
                MidiDeviceInfo.PortInfo.TYPE_INPUT -> if (inputIndex < 0) inputIndex = port.portNumber
                MidiDeviceInfo.PortInfo.TYPE_OUTPUT -> if (outputIndex < 0) outputIndex = port.portNumber
            }
        }
        if (inputIndex < 0 || outputIndex < 0) {
            _state.update {
                it.copy(error = "APC64 has no MIDI input/output pair")
            }
            opened.close()
            return
        }
        val input = runCatching { opened.openInputPort(inputIndex) }.getOrNull()
        val output = runCatching { opened.openOutputPort(outputIndex) }.getOrNull()
        if (input == null || output == null) {
            runCatching { input?.close() }
            runCatching { output?.close() }
            opened.close()
            _state.update { it.copy(error = "Could not open APC64 ports") }
            return
        }

        val newReceiver = object : MidiReceiver() {
            override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                val messages = try {
                    synchronized(parserLock) { parser.feed(msg, offset, count) }
                } catch (error: Exception) {
                    Log.w(TAG, "MIDI parse error", error)
                    return
                }
                if (messages.isNotEmpty()) {
                    mainHandler.post { handleMessages(messages) }
                }
            }

            override fun onFlush() {
                synchronized(parserLock) { parser.reset() }
            }
        }
        output.connect(newReceiver)

        device = opened
        sendPort = input
        receivePort = output
        receiver = newReceiver
        savedDeviceName = deviceName(info)
        prefs.edit().putString("deviceName", savedDeviceName).apply()
        ledCache.fill(-1)
        sceneCache.fill(-1)
        transportCache = -1
        _state.update {
            it.copy(
                connectedId = info.id,
                connectedName = deviceName(info),
                devices = it.devices,
                error = null,
            )
        }

        // Let the USB endpoints settle, then unlock the surface and label the OLED.
        mainHandler.postDelayed({
            if (sendPort === input) {
                Apc64Codec.handshake().forEach { send(it) }
            }
        }, 180)
    }

    fun disconnect() {
        val output = receivePort
        val rec = receiver
        if (output != null && rec != null) runCatching { output.disconnect(rec) }
        runCatching { receivePort?.close() }
        runCatching { sendPort?.close() }
        runCatching { device?.close() }
        receivePort = null
        sendPort = null
        receiver = null
        device = null
        synchronized(parserLock) { parser.reset() }
        ledCache.fill(-1)
        sceneCache.fill(-1)
        transportCache = -1
        _state.update { it.copy(connectedId = null, connectedName = null) }
    }

    fun setPadBaseNote(base: Int) {
        val value = if (base == Apc64Codec.PAD_BASE_CUSTOM) Apc64Codec.PAD_BASE_CUSTOM
        else Apc64Codec.PAD_BASE_DAW
        prefs.edit().putInt("padBase", value).apply()
        _state.update { it.copy(padBaseNote = value) }
        ledCache.fill(-1)
    }

    fun setFlipRows(flip: Boolean) {
        prefs.edit().putBoolean("flipRows", flip).apply()
        _state.update { it.copy(flipRows = flip) }
        ledCache.fill(-1)
    }

    fun setLedFeedback(enabled: Boolean) {
        prefs.edit().putBoolean("ledFeedback", enabled).apply()
        _state.update { it.copy(ledFeedback = enabled) }
        if (!enabled) blankLeds()
        ledCache.fill(-1)
    }

    fun sendTestPattern() {
        if (!_state.value.ledFeedback) return
        for (pad in 0 until Apc64Codec.PAD_COUNT) {
            val note = Apc64Codec.noteForPad(pad, _state.value.padBaseNote, _state.value.flipRows)
            val color = when (pad % 4) {
                0 -> Apc64Codec.COLOR_AMBER
                1 -> Apc64Codec.COLOR_BLUE
                2 -> Apc64Codec.COLOR_YELLOW
                else -> Apc64Codec.COLOR_GREEN
            }
            send(Apc64Codec.led(note, color, Apc64Codec.LED_FULL))
        }
        ledCache.fill(-1)
    }

    fun sendOled(lines: List<String>) {
        lines.take(4).forEachIndexed { index, text ->
            send(Apc64Codec.oledLine(index, text))
        }
    }

    fun updateLeds(
        states: IntArray,
        selectedPad: Int,
        playing: Boolean,
        recording: Boolean,
    ) {
        val current = _state.value
        if (!current.ledFeedback || sendPort == null) return
        val now = System.currentTimeMillis()
        val fullRefresh = now - lastRefreshMs > 1200
        if (fullRefresh) lastRefreshMs = now

        for (pad in 0 until Apc64Codec.PAD_COUNT) {
            val packed = states.getOrElse(pad) { 0 }
            val state = (packed shr 4) and 0x0F
            val kind = packed and 0x0F
            val (color, channel) = Apc64Codec.ledColorFor(state, kind, selectedPad == pad)
            val key = color or (channel shl 8)
            if (ledCache[pad] != key || fullRefresh) {
                val note = Apc64Codec.noteForPad(pad, current.padBaseNote, current.flipRows)
                send(Apc64Codec.led(note, color, channel))
                ledCache[pad] = key
            }
        }

        for (row in 0 until 8) {
            var color = 0
            var channel = Apc64Codec.LED_FULL
            for (col in 0 until 8) {
                val packed = states.getOrElse(row * 8 + col) { 0 }
                val state = (packed shr 4) and 0x0F
                val kind = packed and 0x0F
                if (kind == 0) continue
                if (state == 2) {
                    color = Apc64Codec.COLOR_GREEN
                    channel = Apc64Codec.LED_FULL
                    break
                }
                if (state == 1) {
                    color = Apc64Codec.COLOR_YELLOW
                    channel = Apc64Codec.LED_BLINK
                }
            }
            val key = color or (channel shl 8)
            if (sceneCache[row] != key || fullRefresh) {
                send(Apc64Codec.led(Apc64Codec.SCENE_FIRST + row, color, channel))
                sceneCache[row] = key
            }
        }

        val transport = (if (playing) 1 else 0) or (if (recording) 2 else 0)
        if (transportCache != transport || fullRefresh) {
            send(Apc64Codec.led(Apc64Codec.NOTE_PLAY, if (playing) Apc64Codec.COLOR_GREEN else 0))
            send(Apc64Codec.led(Apc64Codec.NOTE_STOP, if (playing) 0 else Apc64Codec.COLOR_GREEN))
            send(
                Apc64Codec.led(
                    Apc64Codec.NOTE_RECORD,
                    if (recording) Apc64Codec.COLOR_RED else 0,
                    if (recording) Apc64Codec.LED_BLINK else Apc64Codec.LED_FULL,
                ),
            )
            transportCache = transport
        }
    }

    private fun blankLeds() {
        for (note in 0 until Apc64Codec.PAD_COUNT) send(Apc64Codec.led(note, 0))
        for (row in 0 until 8) send(Apc64Codec.led(Apc64Codec.SCENE_FIRST + row, 0))
    }

    private fun handleMessages(messages: List<MidiMessage>) {
        val current = _state.value
        for (message in messages) {
            val summary = describe(message)
            if (summary.isNotBlank()) _state.update { it.copy(lastMessage = summary) }
            when (message) {
                is MidiMessage.NoteOn -> {
                    val pad = Apc64Codec.padForNote(
                        message.note, current.padBaseNote, current.flipRows,
                    )
                    if (pad != null) {
                        onEvent(Apc64Event.Pad(pad, message.velocity, true))
                        continue
                    }
                    val scene = Apc64Codec.sceneForNote(message.note)
                    if (scene != null) {
                        onEvent(Apc64Event.Scene(scene))
                        continue
                    }
                    when (message.note) {
                        Apc64Codec.NOTE_PLAY -> onEvent(Apc64Event.Transport(TransportAction.PLAY))
                        Apc64Codec.NOTE_STOP -> onEvent(Apc64Event.Transport(TransportAction.STOP))
                        Apc64Codec.NOTE_RECORD -> onEvent(Apc64Event.Transport(TransportAction.RECORD))
                        Apc64Codec.NOTE_CLIP_STOP -> onEvent(Apc64Event.ClipStop)
                        Apc64Codec.NOTE_MUTE -> onEvent(Apc64Event.Mute(true))
                        Apc64Codec.NOTE_SOLO -> onEvent(Apc64Event.Solo(true))
                        Apc64Codec.NOTE_SHIFT -> onEvent(Apc64Event.Shift(true))
                        in Apc64Codec.TRACK_SELECT_FIRST..Apc64Codec.TRACK_SELECT_LAST ->
                            onEvent(Apc64Event.TrackSelect(message.note - Apc64Codec.TRACK_SELECT_FIRST))
                    }
                }
                is MidiMessage.NoteOff -> {
                    val pad = Apc64Codec.padForNote(
                        message.note, current.padBaseNote, current.flipRows,
                    )
                    if (pad != null) {
                        onEvent(Apc64Event.Pad(pad, 0, false))
                        continue
                    }
                    when (message.note) {
                        Apc64Codec.NOTE_MUTE -> onEvent(Apc64Event.Mute(false))
                        Apc64Codec.NOTE_SOLO -> onEvent(Apc64Event.Solo(false))
                        Apc64Codec.NOTE_SHIFT -> onEvent(Apc64Event.Shift(false))
                    }
                }
                is MidiMessage.ControlChange -> {
                    if (message.controller == Apc64Codec.CC_ENCODER) {
                        onEvent(Apc64Event.Encoder(Apc64Codec.encoderDelta(message.value)))
                    }
                }
                is MidiMessage.PitchBend -> {
                    if (message.channel in 0..7) {
                        onEvent(
                            Apc64Event.TouchStrip(
                                message.channel,
                                Apc64Codec.touchStripValue(message.value + 8192),
                            ),
                        )
                    }
                }
                else -> Unit
            }
        }
    }

    private fun send(bytes: ByteArray) {
        val port = sendPort ?: return
        synchronized(sendLock) {
            runCatching { port.send(bytes, 0, bytes.size, -1) }
                .onFailure {
                    Log.w(TAG, "MIDI send failed", it)
                    _state.update { st -> st.copy(error = "MIDI send failed: ${it.message}") }
                }
        }
    }

    private fun deviceName(info: MidiDeviceInfo): String =
        info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
            ?: info.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
            ?: "USB MIDI"

    private fun isApc64(info: MidiDeviceInfo): Boolean =
        deviceName(info).contains("APC64", ignoreCase = true) ||
            info.properties.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER)
                ?.contains("Akai", ignoreCase = true) == true

    private fun describe(message: MidiMessage): String = when (message) {
        is MidiMessage.NoteOn -> "NOTE ${message.note} VEL ${message.velocity}"
        is MidiMessage.NoteOff -> "NOTE OFF ${message.note}"
        is MidiMessage.ControlChange -> "CC ${message.controller} = ${message.value}"
        is MidiMessage.PitchBend -> "PB ${message.channel} ${message.value}"
        is MidiMessage.SysEx -> "SYSEX ${message.bytes.size} B"
        else -> ""
    }

    companion object {
        private const val TAG = "ReplicantApc64"
    }
}

