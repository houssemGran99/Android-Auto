package com.autoflow.app.runtime

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import javax.inject.Inject
import javax.inject.Singleton

/** Tracks whether any AutoFlow activity is visible (activities may then be started directly). */
@Singleton
class AppForegroundTracker @Inject constructor() {
    @Volatile
    private var foreground = false

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                foreground = true
            }

            override fun onStop(owner: LifecycleOwner) {
                foreground = false
            }
        })
    }

    fun isInForeground(): Boolean = foreground
}
