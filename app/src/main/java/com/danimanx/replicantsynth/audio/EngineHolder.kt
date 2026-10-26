package com.danimanx.replicantsynth.audio

import android.content.Context

/** Process-scoped engine holder shared by the activity and the foreground service. */
object EngineHolder {
    @Volatile
    private var controller: EngineController? = null

    fun get(context: Context): EngineController {
        val existing = controller
        if (existing != null) return existing
        return synchronized(this) {
            controller ?: EngineController(context.applicationContext).also { controller = it }
        }
    }

    fun peek(): EngineController? = controller

    fun stopEngine() {
        synchronized(this) {
            controller?.destroy()
            controller = null
        }
    }
}
