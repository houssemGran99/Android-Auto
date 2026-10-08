package com.autoflow.app.runtime

import android.util.Log
import com.autoflow.core.engine.EngineLogger

/** Engine diagnostics to Logcat (`adb logcat -s AutoFlow`). User-facing logs live in the history screen. */
object AndroidEngineLogger : EngineLogger {
    private const val TAG = "AutoFlow"

    override fun debug(message: String) {
        Log.d(TAG, message)
    }

    override fun warn(message: String, throwable: Throwable?) {
        Log.w(TAG, message, throwable)
    }
}
