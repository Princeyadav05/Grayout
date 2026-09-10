package com.princeyadav.grayout.service

internal const val RECOVERY_LOOKBACK_MS = 24 * 60 * 60 * 1_000L

/** One bounded history fallback on the first poll; steady polling stays cheap. */
internal class ForegroundRecoveryLookup(
    private val query: (begin: Long, end: Long, validateHistory: Boolean) -> String?,
    private val nowMs: () -> Long,
) : ForegroundAppProvider {
    private var firstPoll = true

    @Synchronized
    override fun currentForegroundPackage(): String? {
        val end = nowMs()
        val recent = query((end - LOOKBACK_MS).coerceAtLeast(0), end, false)
        val recover = firstPoll && recent == null
        firstPoll = false
        return if (recover) {
            query((end - RECOVERY_LOOKBACK_MS).coerceAtLeast(0), end, true)
        } else recent
    }
}

internal enum class ForegroundHistoryEvent { Resumed, Backgrounded, Reset, Other }

/**
 * Consumes chronological usage history. An old resume is usable only if no later
 * matching activity departure, lock/screen-off, or device restart contradicts it.
 * Unlock alone cannot establish a foreground app. This is deliberately used only
 * for the wider startup fallback; unknown history never means exclusion exit.
 */
internal class ForegroundHistory {
    var packageName: String? = null
        private set
    private var activityName: String? = null

    fun record(type: ForegroundHistoryEvent, pkg: String?, activity: String?) {
        when (type) {
            ForegroundHistoryEvent.Resumed -> {
                packageName = pkg
                activityName = activity
            }
            ForegroundHistoryEvent.Backgrounded -> {
                if (pkg == packageName &&
                    (activity == null || activityName == null || activity == activityName)
                ) clear()
            }
            ForegroundHistoryEvent.Reset -> clear()
            ForegroundHistoryEvent.Other -> Unit
        }
    }

    private fun clear() {
        packageName = null
        activityName = null
    }
}
