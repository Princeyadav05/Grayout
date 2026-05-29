package com.princeyadav.grayout.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Reads the package of the current foreground app. This is the seam that lets
 * [ForegroundAppDetector] be unit-tested with a fake (mirrors the
 * [GrayscaleController] pattern); the production implementation talks to
 * [UsageStatsManager].
 */
interface ForegroundAppProvider {

    /**
     * The package of the most recently foregrounded app, or `null` if it cannot
     * be determined (empty query window or usage access not granted).
     *
     * Contract:
     *
     * 1. **Own-package passthrough.** This returns the true latest
     *    `MOVE_TO_FOREGROUND` package **verbatim, including this app's own
     *    package** (`com.princeyadav.grayout`). It must NOT skip the own package
     *    and must NOT substitute a previously-seen package for an own-package
     *    event. Carrying a last-known package forward across *empty* reads is the
     *    detector's job ([ForegroundAppDetector.tickOnce]); [nextExclusionTransition]
     *    is the single place that maps own -> [ExclusionTransition.None].
     *    Substituting here could resurface a stale *excluded* package and trigger
     *    a premature `Exit` (re-graying) while the user is looking at Grayout.
     *
     * 2. **Null contract.** Returns `null` on an empty query window or when usage
     *    access is denied, so the detector maps it to [ExclusionTransition.None]
     *    and never crashes. Detection latency is ~2-4s (UsageStats flush lag).
     *
     * 3. **Lock-screen-after-SCREEN_ON.** A poll fired right after
     *    `ACTION_SCREEN_ON` may run while the keyguard is still up. `queryEvents`
     *    still reports the last real foreground app (the keyguard does not emit
     *    `MOVE_TO_FOREGROUND`), so the detector's last-known-package + own/None
     *    handling prevent a spurious `Exit` on the lock screen.
     */
    fun currentForegroundPackage(): String?
}

/**
 * Production [ForegroundAppProvider] backed by [UsageStatsManager.queryEvents].
 *
 * Selects the package of the latest `MOVE_TO_FOREGROUND` event within a
 * [LOOKBACK_MS] window. `MOVE_TO_FOREGROUND` (value 1) is used rather than
 * `ACTIVITY_RESUMED` because the latter is API 29+ and would break the
 * minSdk-26 build; `MOVE_TO_FOREGROUND` is available since API 21 and still
 * delivered on newer releases.
 */
class UsageStatsForegroundProvider(
    context: Context,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : ForegroundAppProvider {

    private val usageStatsManager: UsageStatsManager? =
        context.getSystemService(UsageStatsManager::class.java)

    // MOVE_TO_FOREGROUND is @Deprecated since API 29 in favour of ACTIVITY_RESUMED,
    // but ACTIVITY_RESUMED is API 29+ and this app's minSdk is 26. MOVE_TO_FOREGROUND
    // is still delivered on every supported release, so it is the correct choice.
    @Suppress("DEPRECATION")
    override fun currentForegroundPackage(): String? {
        val usm = usageStatsManager ?: return null
        val end = nowMs()
        val begin = end - LOOKBACK_MS
        val events = usm.queryEvents(begin, end)
        val event = UsageEvents.Event()
        var latestPackage: String? = null
        var latestTime = Long.MIN_VALUE
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND &&
                event.timeStamp >= latestTime
            ) {
                latestTime = event.timeStamp
                latestPackage = event.packageName
            }
        }
        return latestPackage
    }
}
