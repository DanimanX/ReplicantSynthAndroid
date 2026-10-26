package com.danimanx.replicantsynth.audio

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/** App-private sample storage so a project keeps working if the source moves. */
object SampleLibrary {

    private fun samplesDir(context: Context): File =
        File(context.filesDir, "samples").apply { mkdirs() }

    fun ensureDemoSample(context: Context): File? {
        val target = File(samplesDir(context), "neon_stab.wav")
        if (target.isFile && target.length() > 44) return target
        return runCatching {
            context.assets.open("demo/neon_stab.wav").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target
        }.getOrNull()
    }

    fun importAudio(context: Context, uri: Uri): File? = runCatching {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val extension = extensionFor(context, uri, mime)
        val target = File(samplesDir(context), "sample-${UUID.randomUUID()}.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        if (target.length() <= 44) {
            target.delete()
            return null
        }
        target
    }.getOrNull()

    private fun extensionFor(context: Context, uri: Uri, mime: String): String {
        val fromMime = when (mime.lowercase()) {
            "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/ogg", "audio/x-ogg" -> "ogg"
            "audio/mp4", "audio/aac", "audio/x-m4a" -> "m4a"
            else -> null
        }
        if (fromMime != null) return fromMime
        val name = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
        val extension = name?.substringAfterLast('.', "")?.lowercase()
        return if (extension in setOf("wav", "flac", "mp3", "ogg", "m4a", "aac")) {
            extension!!
        } else {
            "wav"
        }
    }
}
