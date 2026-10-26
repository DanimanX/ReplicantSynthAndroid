package com.danimanx.replicantsynth.model

/**
 * The 48-slot SP-404MKII MFX catalog used by ReplicantFX.
 * Generated from the Rust plug-in's firmware table so the Android editor shows
 * the same names, LED codes, parameter labels and factory defaults.
 */
data class FxInfo(
    val id: Int,
    val name: String,
    val code: String,
    val labels: List<String>,
    val defaults: FloatArray,
) {
    override fun equals(other: Any?): Boolean = other is FxInfo && other.id == id
    override fun hashCode(): Int = id
}

val FX_CATALOG: List<FxInfo> = listOf(
    FxInfo(0, "Bypass", "BYP", listOf("GAIN", "PAN", "LOW TRIM", "PHASE", "HIGH TRIM", "BALANCE"), floatArrayOf(0.5f, 0.5f, 0.5f, 0.0f, 0.5f, 1.0f)),
    FxInfo(1, "Filter+Drive", "F+D", listOf("CUTOFF", "RESONANCE", "DRIVE", "FLT TYPE", "LOW FREQ", "LOW GAIN"), floatArrayOf(0.62f, 0.35f, 0.3f, 0.0f, 0.35f, 0.55f)),
    FxInfo(2, "TimeCtrlDly", "TCD", listOf("TIME", "FEEDBACK", "LEVEL", "HIGH CUT", "LOW DAMP", "BALANCE"), floatArrayOf(0.42f, 0.52f, 0.65f, 0.68f, 0.3f, 0.55f)),
    FxInfo(3, "Isolator", "ISO", listOf("LOW", "MID", "HIGH", "KILL LOW", "KILL MID", "KILL HIGH"), floatArrayOf(0.55f, 0.5f, 0.55f, 0.0f, 0.0f, 0.0f)),
    FxInfo(4, "Crusher", "CRS", listOf("FILTER", "RATE", "BALANCE", "BIT DEPTH", "RESONANCE", "DRIVE"), floatArrayOf(0.58f, 0.45f, 0.75f, 0.45f, 0.3f, 0.35f)),
    FxInfo(5, "Phaser", "PHS", listOf("RATE", "DEPTH", "RESONANCE", "MANUAL", "TYPE", "BALANCE"), floatArrayOf(0.32f, 0.68f, 0.45f, 0.5f, 0.33f, 0.7f)),
    FxInfo(6, "Flanger", "FLG", listOf("RATE", "DEPTH", "RESONANCE", "MANUAL", "LOW CUT F", "BALANCE"), floatArrayOf(0.28f, 0.65f, 0.58f, 0.45f, 0.25f, 0.65f)),
    FxInfo(7, "Overdrive", "ODV", listOf("DRIVE", "TONE", "BALANCE", "LEVEL", "AMP TYPE", "LOW CUT"), floatArrayOf(0.55f, 0.58f, 0.8f, 0.65f, 0.0f, 0.2f)),
    FxInfo(8, "Distortion", "DST", listOf("DRIVE", "TONE", "BALANCE", "LEVEL", "MODE", "LOW BOOST"), floatArrayOf(0.68f, 0.52f, 0.82f, 0.62f, 0.67f, 0.55f)),
    FxInfo(9, "Tape Echo", "TPE", listOf("TIME", "FEEDBACK", "LEVEL", "MODE", "TAPE DIST", "W/F DEPTH"), floatArrayOf(0.46f, 0.58f, 0.62f, 0.5f, 0.35f, 0.4f)),
    FxInfo(10, "Reverb", "REV", listOf("TIME", "LEVEL", "PRE DELAY", "TYPE", "LOW CUT", "HIGH CUT"), floatArrayOf(0.6f, 0.55f, 0.25f, 0.5f, 0.25f, 0.72f)),
    FxInfo(11, "WrmSaturator", "SAT", listOf("DRIVE", "EQ LOW", "EQ HIGH", "LOW FREQ", "HI FREQ", "BALANCE"), floatArrayOf(0.52f, 0.6f, 0.56f, 0.35f, 0.68f, 0.85f)),
    FxInfo(12, "Resonator", "RES", listOf("ROOT", "BRIGHT", "FEEDBACK", "CHORD", "ENV MOD", "PANNING"), floatArrayOf(0.35f, 0.65f, 0.68f, 0.45f, 0.4f, 0.6f)),
    FxInfo(13, "DJFX Looper", "DJL", listOf("LENGTH", "SPEED", "LOOP SW", "REVERSE", "TONE", "LEVEL"), floatArrayOf(0.55f, 0.75f, 0.0f, 0.0f, 0.65f, 0.85f)),
    FxInfo(14, "Chorus", "CHR", listOf("RATE", "DEPTH", "BALANCE", "EQ LOW", "EQ HIGH", "LEVEL"), floatArrayOf(0.35f, 0.6f, 0.65f, 0.55f, 0.6f, 0.75f)),
    FxInfo(15, "JUNO Chorus", "JNO", listOf("MODE", "NOISE", "BALANCE", "RATE", "DEPTH", "LEVEL"), floatArrayOf(0.5f, 0.22f, 0.75f, 0.4f, 0.68f, 0.8f)),
    FxInfo(16, "Tremolo/Pan", "TRM", listOf("RATE", "DEPTH", "TYPE", "WAVE", "EQ LOW", "EQ HIGH"), floatArrayOf(0.48f, 0.72f, 0.5f, 0.35f, 0.5f, 0.55f)),
    FxInfo(17, "Wah", "WAH", listOf("MANUAL", "PEAK", "RATE", "FLT TYPE", "DEPTH", "SENS"), floatArrayOf(0.45f, 0.7f, 0.38f, 1.0f, 0.65f, 0.6f)),
    FxInfo(18, "Sync Delay", "SYD", listOf("TIME", "FEEDBACK", "LEVEL", "HIGH CUT", "HIGH DAMP", "LOW DAMP"), floatArrayOf(0.48f, 0.55f, 0.6f, 0.72f, 0.45f, 0.3f)),
    FxInfo(19, "Equalizer", "EQL", listOf("LOW GAIN", "MID GAIN", "HIGH GAIN", "LOW FREQ", "MID FREQ", "MID Q"), floatArrayOf(0.58f, 0.52f, 0.58f, 0.32f, 0.5f, 0.45f)),
    FxInfo(20, "Lo-fi", "LFI", listOf("PRE FILT", "LOFI TYPE", "TONE", "CUTOFF", "POST FILT", "BALANCE"), floatArrayOf(0.4f, 0.45f, 0.52f, 0.58f, 0.45f, 0.8f)),
    FxInfo(21, "Ring Mod", "RNG", listOf("FREQUENCY", "SENS", "BALANCE", "POLARITY", "EQ LOW", "EQ HIGH"), floatArrayOf(0.42f, 0.55f, 0.65f, 0.0f, 0.5f, 0.55f)),
    FxInfo(22, "Chromatic PS", "CPS", listOf("PITCH1", "PITCH2", "BALANCE", "FINE1", "FINE2", "PAN SPREAD"), floatArrayOf(0.65f, 0.35f, 0.6f, 0.5f, 0.5f, 0.7f)),
    FxInfo(23, "Gt Amp Sim", "AMP", listOf("AMP TYPE", "DRIVE", "LEVEL", "BASS", "MIDDLE", "TREBLE"), floatArrayOf(0.4f, 0.58f, 0.65f, 0.55f, 0.52f, 0.58f)),
    FxInfo(24, "Stopper", "STP", listOf("RATE", "STOP TRIG", "DEPTH", "FILTER", "RESONANCE", "LEVEL"), floatArrayOf(0.52f, 0.0f, 0.8f, 0.68f, 0.35f, 0.85f)),
    FxInfo(25, "Downer", "DWN", listOf("DEPTH", "RATE", "FILTER", "PITCH", "RESONANCE", "LEVEL"), floatArrayOf(0.62f, 0.45f, 0.58f, 0.35f, 0.48f, 0.8f)),
    FxInfo(26, "Slicer", "SLC", listOf("PATTERN", "SPEED", "DEPTH", "ATTACK", "SHUFFLE", "MODE"), floatArrayOf(0.35f, 0.55f, 0.82f, 0.25f, 0.3f, 0.0f)),
    FxInfo(27, "Auto Pitch", "ATP", listOf("PITCH", "FORMANT", "BALANCE", "AT PITCH", "KEY", "ROBOT"), floatArrayOf(0.5f, 0.55f, 0.75f, 0.7f, 0.0f, 0.0f)),
    FxInfo(28, "Compressor", "CMP", listOf("SUSTAIN", "ATTACK", "LEVEL", "RATIO", "TONE", "BALANCE"), floatArrayOf(0.65f, 0.35f, 0.68f, 0.6f, 0.55f, 0.9f)),
    FxInfo(29, "303 BusComp", "C02", listOf("SUSTAIN", "ATTACK", "LEVEL", "RATIO", "TONE", "BALANCE"), floatArrayOf(0.78f, 0.25f, 0.7f, 0.82f, 0.52f, 0.95f)),
    FxInfo(30, "Scatter", "SCT", listOf("SCATTER", "DEPTH", "SPEED", "GATE", "ATTACK", "BALANCE"), floatArrayOf(0.3f, 0.72f, 0.55f, 0.5f, 0.2f, 0.85f)),
    FxInfo(31, "404 VinylSim", "404", listOf("FREQUENCY", "NOISE", "WOW FLUT", "DIST", "DISC TYPE", "BALANCE"), floatArrayOf(0.58f, 0.42f, 0.48f, 0.35f, 0.0f, 0.85f)),
    FxInfo(32, "303 VinylSim", "303", listOf("COMP", "NOISE", "WOW FLUT", "AGE", "TONE", "BALANCE"), floatArrayOf(0.68f, 0.45f, 0.52f, 0.5f, 0.48f, 0.9f)),
    FxInfo(33, "Zan-Zou", "ZNZ", listOf("TIME", "FEEDBACK", "HF DAMP", "MODE", "SPREAD", "LEVEL"), floatArrayOf(0.45f, 0.6f, 0.55f, 0.5f, 0.8f, 0.68f)),
    FxInfo(34, "Super Filter", "SFL", listOf("CUTOFF", "RESONANCE", "FLT TYPE", "FLT SLOPE", "RATE", "DEPTH"), floatArrayOf(0.55f, 0.52f, 0.0f, 0.5f, 0.45f, 0.6f)),
    FxInfo(35, "Hyper-Reso", "HYP", listOf("NOTE", "SPREAD", "FEEDBACK", "CHARACTER", "ENV MOD", "LEVEL"), floatArrayOf(0.42f, 0.65f, 0.72f, 0.58f, 0.45f, 0.7f)),
    FxInfo(36, "Ko-Da-Ma", "KDM", listOf("TIME", "FEEDBACK", "SEND", "MODE", "HIGH CUT", "MOD DEPTH"), floatArrayOf(0.48f, 0.62f, 0.75f, 0.5f, 0.65f, 0.45f)),
    FxInfo(37, "Ha-Dou", "HDO", listOf("TIME", "LEVEL", "MOD DEPTH", "PRE DELAY", "LOW CUT", "DENSITY"), floatArrayOf(0.75f, 0.62f, 0.58f, 0.35f, 0.3f, 0.8f)),
    FxInfo(38, "To-Gu-Ro", "TGR", listOf("DEPTH", "RATE", "RESONANCE", "FLT MOD", "AMP MOD", "HOLD"), floatArrayOf(0.68f, 0.48f, 0.55f, 0.65f, 0.5f, 0.0f)),
    FxInfo(39, "Cassette Sim", "CAS", listOf("TONE", "HISS", "WOW FLUT", "AGE", "DRIVE", "CATCH"), floatArrayOf(0.54f, 0.35f, 0.48f, 0.45f, 0.52f, 0.25f)),
    FxInfo(40, "SBF", "SBF", listOf("INTERVAL", "WIDTH", "BALANCE", "TYPE", "GAIN", "LOW CUT"), floatArrayOf(0.52f, 0.48f, 0.75f, 0.35f, 0.55f, 0.25f)),
    FxInfo(41, "SX Delay", "SXD", listOf("TIME", "FEEDBACK", "BALANCE", "FBK ROUTE", "HF DAMP", "MOD DEPTH"), floatArrayOf(0.44f, 0.56f, 0.58f, 0.5f, 0.52f, 0.35f)),
    FxInfo(42, "Vocoder", "VOC", listOf("NOTE", "FORMANT", "TONE", "CHORD", "COLOR", "BALANCE"), floatArrayOf(0.4f, 0.55f, 0.62f, 0.45f, 0.58f, 0.85f)),
    FxInfo(43, "Harmony", "HRM", listOf("PITCH", "HARMONY", "BALANCE", "FORMANT", "KEY", "AT PITCH"), floatArrayOf(0.5f, 0.6f, 0.65f, 0.52f, 0.25f, 0.6f)),
    FxInfo(44, "SX Reverb", "SXR", listOf("TIME", "TONE", "BALANCE", "TYPE", "PRE DELAY", "LEVEL"), floatArrayOf(0.58f, 0.6f, 0.55f, 0.4f, 0.25f, 0.75f)),
    FxInfo(45, "Sub Sonic", "SUB", listOf("FREQ", "SUB LEVEL", "BALANCE", "DRIVE", "TONE", "DIRECT"), floatArrayOf(0.38f, 0.68f, 0.75f, 0.45f, 0.42f, 0.8f)),
    FxInfo(46, "Back Spin", "BSP", listOf("LENGTH", "SPEED", "BACK SW", "CURVE", "TONE", "LEVEL"), floatArrayOf(0.55f, 0.72f, 0.0f, 0.5f, 0.65f, 0.85f)),
    FxInfo(47, "Cloud Delay", "CLD", listOf("PITCH", "WINDOW", "FEEDBACK", "CLOUDY", "LOFI", "BALANCE"), floatArrayOf(0.75f, 0.62f, 0.65f, 0.78f, 0.3f, 0.62f)),
)

fun fxById(id: Int): FxInfo = FX_CATALOG.getOrElse(id) { FX_CATALOG[0] }
