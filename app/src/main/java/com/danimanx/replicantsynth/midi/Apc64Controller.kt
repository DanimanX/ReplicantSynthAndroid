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
    val padMode: Apc64PadMode = Apc64PadMode.SESSION,
    val firmware: String? = null,
    val firmwareSupported: Boolean = true,
    val dawPort: String? = null,
    val notesPort: String? = null,
) {
    val connected: Boolean get() = connectedId != null
    val notesPortOpen: Boolean get() = notesPort != null
    val modeLabel: String get() = padMode.label
}

sealed class Apc64Event {
    data class Pad(val pad: Int, val velocity: Int, val pressed: Boolean) : Apc64Event()

    /** Raw key from the APC64 Notes/Chord surface, used by the piano roll. */
    data class Key(val note: Int, val velocity: Int, val pressed: Boolean) : Apc64Event()

    /** The unit changed its own pad mode (Session, Note, Chord, Step, ...). */
    data class Mode(val mode: Apc64PadMode) : Apc64Event()

    /** Release every key the unit was holding (mode change or unplug). */
    data object AllKeysOff : Apc64Event()

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
 * USB MIDI connection and APC64 profile. The APC64 exposes four USB MIDI
 * cables: the DAW cable carries the host-drawn session grid and every side
 * button, while the Notes cable carries expressive Note/Chord mode pads. The
 * app opens both so the hardware's own NOTE and CHORD modes never leak into
 * session clip launching.
 *
 * Detection is by the USB MIDI device name; the controller sends the Akai mode
 * SysEx on connect so the 64-pad surface is unlocked without Ableton Live.
 */
class Apc64Controller(
    context: Context,
    private val onEvent: (Apc64Event) -> Unit,
) {
    private enum class Source { DAW, NOTES }

    private data class InboundPort(
        val source: Source,
        val port: MidiOutputPort,
        val receiver: MidiReceiver,
    )

    private val appContext = context.applicationContext
    private val midiManager =
        appContext.getSystemService(Context.MIDI_SERVICE) as? MidiManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = appContext.getSharedPreferences("apc64", Context.MODE_PRIVATE)
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
    private val inbound = mutableListOf<InboundPort>()
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
        val inputs = info.ports.filter { it.type == MidiDeviceInfo.PortInfo.TYPE_INPUT }
        val outputs = info.ports.filter { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }
        if (inputs.isEmpty() || outputs.isEmpty()) {
            _state.update {
                it.copy(error = "APC64 has no MIDI input/output pair")
            }
            opened.close()
            return
        }

        val dawInputInfo = pickPort(inputs, Apc64PortRole.DAW, fallbackNumber = 0)
            ?: inputs.first()
        val dawOutputInfo = pickPort(outputs, Apc64PortRole.DAW, fallbackNumber = 0)
            ?: outputs.first()
        val notesOutputInfo = pickPort(
            outputs,
            Apc64PortRole.NOTES,
            fallbackNumber = 1,
            excludePort = dawOutputInfo.portNumber,
        )

        val input = runCatching { opened.openInputPort(dawInputInfo.portNumber) }.getOrNull()
        if (input == null) {
            opened.close()
            _state.update { it.copy(error = "Could not open APC64 DAW input") }
            return
        }

        if (!attachOutput(opened, dawOutputInfo, Source.DAW)) {
            runCatching { input.close() }
            opened.close()
            _state.update { it.copy(error = "Could not open APC64 DAW output") }
            return
        }
        val notesAttached = notesOutputInfo != null &&
            attachOutput(opened, notesOutputInfo, Source.NOTES)

        device = opened
        sendPort = input
        savedDeviceName = deviceName(info)
        prefs.edit().putString("deviceName", savedDeviceName).apply()
        ledCache.fill(-1)
        sceneCache.fill(-1)
        transportCache = -1
        lastRefreshMs = 0L
        _state.update {
            it.copy(
                connectedId = info.id,
                connectedName = deviceName(info),
                devices = it.devices,
                error = null,
                padMode = Apc64PadMode.SESSION,
                firmware = null,
                firmwareSupported = true,
                dawPort = portLabel(dawOutputInfo),
                notesPort = notesOutputInfo?.takeIf { notesAttached }?.let { portLabel(it) },
            )
        }

        // Let the USB endpoints settle, then unlock the surface and label the OLED.
        mainHandler.postDelayed({
            if (sendPort === input) {
                Apc64Codec.handshake().forEach { send(it) }
                // Some hubs drop the first identity request; ask once more so
                // the firmware validation can complete without a replug.
                mainHandler.postDelayed({
                    if (sendPort === input && _state.value.firmware == null) {
                        send(Apc64Codec.identityRequest)
                    }
                }, 900)
            }
        }, 180)
    }

    private fun attachOutput(
        opened: MidiDevice,
        portInfo: MidiDeviceInfo.PortInfo,
        source: Source,
    ): Boolean {
        val port = runCatching { opened.openOutputPort(portInfo.portNumber) }.getOrNull()
            ?: return false
        val parser = MidiStreamParser()
        val parserLock = Any()
        val receiver = object : MidiReceiver() {
            override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                val messages = try {
                    synchronized(parserLock) { parser.feed(msg, offset, count) }
                } catch (error: Exception) {
                    Log.w(TAG, "MIDI parse error on $source", error)
                    return
                }
                if (messages.isNotEmpty()) {
                    mainHandler.post { handleMessages(messages, source) }
                }
            }

            override fun onFlush() {
                synchronized(parserLock) { parser.reset() }
            }
        }
        val connected = runCatching { port.connect(receiver) }.isSuccess
        if (!connected) {
            runCatching { port.close() }
            return false
        }
        inbound += InboundPort(source, port, receiver)
        return true
    }

    fun disconnect() {
        inbound.forEach { slot ->
            runCatching { slot.port.disconnect(slot.receiver) }
            runCatching { slot.port.close() }
        }
        inbound.clear()
        runCatching { sendPort?.close() }
        runCatching { device?.close() }
        sendPort = null
        device = null
        ledCache.fill(-1)
        sceneCache.fill(-1)
        transportCache = -1
        lastRefreshMs = 0L
        _state.update {
            it.copy(
                connectedId = null,
                connectedName = null,
                padMode = Apc64PadMode.SESSION,
                firmware = null,
                firmwareSupported = true,
                dawPort = null,
                notesPort = null,
            )
        }
        onEvent(Apc64Event.AllKeysOff)
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

        // In Note/Chord/Step/Project/Custom the unit owns the pad grid. Sending
        // host LED frames there is ignored by the hardware, but it would fight
        // the mode display if a future firmware starts listening. On return to
        // Session the caches are invalidated, so the whole grid is redrawn.
        if (current.padMode.hostOwnsPads) {
            for (pad in 0 until Apc64Codec.PAD_COUNT) {
                val packed = states.getOrElse(pad) { 0 }
                val state = (packed shr 4) and 0x0F
                val kind = packed and 0x0F
                val (color, channel) = Apc64Codec.ledColorFor(state, kind, selectedPad == pad)
                val key = color or (channel shl 8)
                if (ledCache[pad] != key || fullRefresh) {
                    val note = Apc64Codec.noteForPad(
                        pad,
                        current.padBaseNote,
                        current.flipRows,
                    )
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

    private fun handleMessages(messages: List<MidiMessage>, source: Source) {
        for (message in messages) {
            val summary = describe(message, source)
            if (summary.isNotBlank()) _state.update { it.copy(lastMessage = summary) }
            when (message) {
                is MidiMessage.SysEx -> if (source == Source.DAW) {
                    handleSysEx(message.bytes)
                }
                is MidiMessage.NoteOn -> handleNote(
                    message.note,
                    message.velocity,
                    pressed = true,
                    source = source,
                )
                is MidiMessage.NoteOff -> handleNote(
                    message.note,
                    velocity = 0,
                    pressed = false,
                    source = source,
                )
                is MidiMessage.ControlChange -> {
                    if (source == Source.DAW &&
                        message.controller == Apc64Codec.CC_ENCODER
                    ) {
                        onEvent(Apc64Event.Encoder(Apc64Codec.encoderDelta(message.value)))
                    }
                }
                is MidiMessage.PitchBend -> {
                    if (source == Source.DAW && message.channel in 0..7) {
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

    private fun handleNote(
        note: Int,
        velocity: Int,
        pressed: Boolean,
        source: Source,
    ) {
        // If the app connected while the unit was already in Note/Chord view,
        // the mode report may predate the connection. A Notes-cable event is a
        // reliable fallback: it cannot be a session clip launch.
        if (source == Source.NOTES) {
            val before = _state.value.padMode
            if (before.hostOwnsPads || before == Apc64PadMode.UNKNOWN) {
                applyPadMode(Apc64PadMode.NOTE)
            }
        }
        val current = _state.value
        val route = Apc64Codec.routeNote(
            mode = current.padMode,
            fromNotesPort = source == Source.NOTES,
            note = note,
        )
        when (route) {
            Apc64NoteRoute.KEY -> onEvent(Apc64Event.Key(note, velocity, pressed))
            Apc64NoteRoute.CLIP -> {
                val pad = Apc64Codec.padForNote(
                    note,
                    current.padBaseNote,
                    current.flipRows,
                )
                if (pad != null) onEvent(Apc64Event.Pad(pad, velocity, pressed))
            }
            Apc64NoteRoute.IGNORE -> Unit
            Apc64NoteRoute.CONTROL -> handleControlNote(note, pressed)
        }
    }

    /** Scene, transport, mute/solo, shift and track-select notes on the DAW cable. */
    private fun handleControlNote(note: Int, pressed: Boolean) {
        if (pressed) {
            val scene = Apc64Codec.sceneForNote(note)
            if (scene != null) {
                onEvent(Apc64Event.Scene(scene))
                return
            }
            when (note) {
                Apc64Codec.NOTE_PLAY -> onEvent(Apc64Event.Transport(TransportAction.PLAY))
                Apc64Codec.NOTE_STOP -> onEvent(Apc64Event.Transport(TransportAction.STOP))
                Apc64Codec.NOTE_RECORD -> onEvent(Apc64Event.Transport(TransportAction.RECORD))
                Apc64Codec.NOTE_CLIP_STOP -> onEvent(Apc64Event.ClipStop)
                Apc64Codec.NOTE_MUTE -> onEvent(Apc64Event.Mute(true))
                Apc64Codec.NOTE_SOLO -> onEvent(Apc64Event.Solo(true))
                Apc64Codec.NOTE_SHIFT -> onEvent(Apc64Event.Shift(true))
                in Apc64Codec.TRACK_SELECT_FIRST..Apc64Codec.TRACK_SELECT_LAST -> {
                    onEvent(Apc64Event.TrackSelect(note - Apc64Codec.TRACK_SELECT_FIRST))
                }
            }
        } else {
            when (note) {
                Apc64Codec.NOTE_MUTE -> onEvent(Apc64Event.Mute(false))
                Apc64Codec.NOTE_SOLO -> onEvent(Apc64Event.Solo(false))
                Apc64Codec.NOTE_SHIFT -> onEvent(Apc64Event.Shift(false))
            }
        }
    }

    private fun handleSysEx(bytes: ByteArray) {
        val mode = Apc64Codec.padModeFromSysex(bytes)
        if (mode != null) {
            applyPadMode(mode)
            return
        }
        val firmware = Apc64Codec.firmwareFromIdentity(bytes) ?: return
        val supported = Apc64Codec.firmwareSupported(firmware)
        _state.update {
            it.copy(
                firmware = firmware,
                firmwareSupported = supported,
                lastMessage = "FW $firmware",
            )
        }
        if (!supported) {
            _state.update {
                it.copy(
                    error = "APC64 firmware $firmware is older than " +
                        Apc64Codec.VALIDATED_FIRMWARE,
                )
            }
        }
    }

    private fun applyPadMode(mode: Apc64PadMode) {
        val previous = _state.value.padMode
        if (mode == previous) return
        _state.update { it.copy(padMode = mode, lastMessage = "MODE ${mode.label}") }
        // The unit clears its host-drawn grid on every mode change; force a
        // complete redraw when Session becomes host-owned again.
        ledCache.fill(-1)
        sceneCache.fill(-1)
        transportCache = -1
        lastRefreshMs = 0L
        if (previous.keySurface && !mode.keySurface) {
            onEvent(Apc64Event.AllKeysOff)
        }
        onEvent(Apc64Event.Mode(mode))
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

    private fun pickPort(
        ports: List<MidiDeviceInfo.PortInfo>,
        role: Apc64PortRole,
        fallbackNumber: Int,
        excludePort: Int = -1,
    ): MidiDeviceInfo.PortInfo? {
        val candidates = ports.filter { it.portNumber != excludePort }
        return candidates.firstOrNull {
            Apc64Codec.portRole(portName(it), it.portNumber) == role
        } ?: candidates.firstOrNull { it.portNumber == fallbackNumber }
            ?: candidates.firstOrNull()
    }

    private fun portName(port: MidiDeviceInfo.PortInfo): String =
        runCatching { port.name }.getOrNull().orEmpty()

    private fun portLabel(port: MidiDeviceInfo.PortInfo): String {
        val name = portName(port)
        return if (name.isNotBlank()) name else "PORT ${port.portNumber + 1}"
    }

    private fun deviceName(info: MidiDeviceInfo): String =
        info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
            ?: info.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
            ?: "USB MIDI"

    private fun isApc64(info: MidiDeviceInfo): Boolean =
        deviceName(info).contains("APC64", ignoreCase = true) ||
            info.properties.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER)
                ?.contains("Akai", ignoreCase = true) == true

    private fun describe(message: MidiMessage, source: Source): String = when (message) {
        is MidiMessage.NoteOn -> if (source == Source.NOTES) {
            "KEY ${message.note} VEL ${message.velocity}"
        } else {
            "NOTE ${message.note} VEL ${message.velocity}"
        }
        is MidiMessage.NoteOff -> if (source == Source.NOTES) {
            "KEY OFF ${message.note}"
        } else {
            "NOTE OFF ${message.note}"
        }
        is MidiMessage.ControlChange -> "CC ${message.controller} = ${message.value}"
        is MidiMessage.PitchBend -> "PB ${message.channel} ${message.value}"
        is MidiMessage.SysEx -> "SYSEX ${message.bytes.size} B"
        else -> ""
    }

    companion object {
        private const val TAG = "ReplicantApc64"
    }
}
