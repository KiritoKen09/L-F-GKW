package com.example.util

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppLockManager : DefaultLifecycleObserver {

    private var appContext: Context? = null
    private var backgroundTimestamp: Long = 0L
    private const val LOCK_TIMEOUT_MS = 60_000L // 1 minute in background

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    fun init(context: Context) {
        val isFirst = appContext == null
        appContext = context.applicationContext
        if (isFirst) {
            try {
                ProcessLifecycleOwner.get().lifecycle.addObserver(this)
            } catch (e: Exception) {
                android.util.Log.w("AppLockManager", "ProcessLifecycleOwner observer registration failed: ${e.message}")
            }
        }
        // If PIN is configured, start locked on cold launch
        if (SecurityManager.isPinSet(context)) {
            _isLocked.value = true
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        val ctx = appContext ?: return
        if (SecurityManager.isPinSet(ctx) && !_isLocked.value) {
            backgroundTimestamp = System.currentTimeMillis()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        val ctx = appContext ?: return
        if (SecurityManager.isPinSet(ctx) && backgroundTimestamp > 0L) {
            val elapsed = System.currentTimeMillis() - backgroundTimestamp
            if (elapsed >= LOCK_TIMEOUT_MS) {
                _isLocked.value = true
            }
        }
        backgroundTimestamp = 0L
    }

    /**
     * Testing hook to simulate backgrounding for a given elapsed duration.
     */
    fun simulateBackgroundElapsed(elapsedMs: Long, context: Context? = null) {
        val ctx = context?.applicationContext ?: appContext ?: return
        if (SecurityManager.isPinSet(ctx) && elapsedMs >= LOCK_TIMEOUT_MS) {
            _isLocked.value = true
        }
    }

    fun unlock() {
        _isLocked.value = false
        backgroundTimestamp = 0L
    }

    fun lockNow() {
        val ctx = appContext ?: return
        if (SecurityManager.isPinSet(ctx)) {
            _isLocked.value = true
        }
    }

    fun setLockedForTesting(locked: Boolean) {
        _isLocked.value = locked
    }
}
