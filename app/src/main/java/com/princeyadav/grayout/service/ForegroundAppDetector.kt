package com.princeyadav.grayout.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** How often the detector samples the foreground app while armed. */
const val POLL_INTERVAL_MS = 1_000L

/** How far back [UsageStatsForegroundProvider] looks for foreground events. */
const val LOOKBACK_MS = 10_000L

/** Outcome of one foreground-app poll for the exclusion state machine. */
sealed interface ExclusionTransition {
    /** Outside -> inside an excluded app. Save prior grayscale state, turn grayscale OFF. */
    data class Enter(val wasGrayscaleOn: Boolean) : ExclusionTransition

    /** Inside -> outside. [wasGrayscaleOn] is the saved entry state. */
    data class Exit(val wasGrayscaleOn: Boolean) : ExclusionTransition

    /** No edge this tick (inside->inside, outside->outside, own package, or undetermined). */
    data object None : ExclusionTransition
}

/**
 * Pure exclusion-state-machine step. No Android deps, no side effects.
 *
 * Evaluates the exclusion edge for one foreground-app poll. The caller performs
 * side effects via [applyExclusionTransition].
 *
 * @param foregroundPackage current foreground app, or `null` if undetermined -> [ExclusionTransition.None]
 * @param ownPackage this app's package; never ENTER/EXIT on our own UI (literal parity with the old `if (pkg == ownPkg) return`)
 * @param isExcluded whether [foregroundPackage] is in the user's exclusion set
 * @param excludedAppActive persisted FSM state ([ExclusionPrefs.isExcludedAppActive])
 * @param grayscaleEnabled current grayscale state, read once by the caller before this call
 * @param wasGrayscaleOnBeforeExclusion saved entry state, echoed back on Exit
 */
fun nextExclusionTransition(
    foregroundPackage: String?,
    ownPackage: String,
    isExcluded: Boolean,
    excludedAppActive: Boolean,
    grayscaleEnabled: Boolean,
    wasGrayscaleOnBeforeExclusion: Boolean,
): ExclusionTransition {
    if (foregroundPackage == null) return ExclusionTransition.None
    if (foregroundPackage == ownPackage) return ExclusionTransition.None
    return when {
        isExcluded && !excludedAppActive -> ExclusionTransition.Enter(grayscaleEnabled)
        !isExcluded && excludedAppActive -> ExclusionTransition.Exit(wasGrayscaleOnBeforeExclusion)
        else -> ExclusionTransition.None
    }
}

/**
 * Applies one [ExclusionTransition]'s side effects in a fixed, well-tested
 * write order. Pure-ish: only touches the injected prefs +
 * grayscale controller + [onExclusionEnded] lambda, so it is unit-testable with
 * fakes (mirrors [applyEnforcementTick]).
 *
 * - Enter: setWasGrayscaleOnBeforeExclusion -> setExcludedAppActive(true) -> setGrayscale(false)
 * - Exit : setExcludedAppActive(false) -> setWasGrayscaleOnBeforeExclusion(false) ->
 *          if (wasOn) setGrayscale(true) else if (enforcementInterval > 0) onExclusionEnded()
 * - None : nothing
 */
fun applyExclusionTransition(
    transition: ExclusionTransition,
    exclusionPrefs: ExclusionPrefs,
    grayscale: GrayscaleController,
    enforcementInterval: Int,
    onExclusionEnded: () -> Unit,
) {
    when (transition) {
        is ExclusionTransition.Enter -> {
            // Order matters: setExcludedAppActive(true) MUST precede setGrayscale(false).
            // setGrayscale(false) fires GrayoutService's ContentObserver on the main
            // thread, which reads isExcludedAppActive() to skip enforcement; the flag
            // must already be visible. SharedPreferences.apply() updates the in-memory
            // map synchronously, so the observer's callback sees it.
            exclusionPrefs.setWasGrayscaleOnBeforeExclusion(transition.wasGrayscaleOn)
            exclusionPrefs.setExcludedAppActive(true)
            grayscale.setGrayscale(false)
        }

        is ExclusionTransition.Exit -> {
            exclusionPrefs.setExcludedAppActive(false)
            exclusionPrefs.setWasGrayscaleOnBeforeExclusion(false)
            if (transition.wasGrayscaleOn) {
                grayscale.setGrayscale(true)
            } else if (enforcementInterval > 0) {
                onExclusionEnded()
            }
        }

        ExclusionTransition.None -> {
            // No edge this tick.
        }
    }
}

/**
 * Whether [GrayoutService] should stay alive. Exclusion must work even when
 * enforcement is off (interval 0), so the service stays up while any app is
 * excluded.
 */
fun shouldServiceRun(interval: Int, excludedCount: Int): Boolean =
    interval > 0 || excludedCount > 0

/**
 * Polls the foreground app and drives the exclusion state machine. Hosted by
 * [GrayoutService]; it is the foreground-app event source for exclusions. The
 * grayscale/enforcement contracts ([shouldReEnableOnExclusionEnd],
 * [applyEnforcementTick], the countdown/alarm machinery) are untouched.
 *
 * [tickOnce] is the single non-pure glue point: it holds (a) [lastKnownPkg]
 * carry-forward across empty reads and (b) the read-`isGrayscaleEnabled()`-once
 * threading, and is therefore directly unit-tested. The lock-screen-after-
 * SCREEN_ON caveat (see [ForegroundAppProvider]) is neutralized here by the
 * [lastKnownPkg] + own/None handling, so a poll that lands on the keyguard does
 * not produce a spurious `Exit`.
 */
class ForegroundAppDetector(
    private val provider: ForegroundAppProvider,
    private val exclusionPrefs: ExclusionPrefs,
    private val enforcementPrefs: EnforcementPrefs,
    private val grayscale: GrayscaleController,
    private val ownPackage: String,
    private val onExclusionEnded: () -> Unit,
    private val scope: CoroutineScope,
    private val isScreenOn: () -> Boolean = { true },
    private val pollIntervalMs: Long = POLL_INTERVAL_MS,
) {
    private var loopJob: Job? = null

    /**
     * Last successfully-read package; carried forward only across empty reads.
     * `@Volatile` because stop()/start() (main thread) can relaunch the loop on a
     * different `Dispatchers.Default` worker than the one that last wrote it.
     */
    @Volatile
    internal var lastKnownPkg: String? = null

    /** Starts the poll loop. Idempotent. */
    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            while (isActive) {
                tickOnce()
                delay(pollIntervalMs)
            }
        }
    }

    /** Stops the poll loop. Idempotent. */
    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    /**
     * One poll: resolve the foreground package, evaluate the pure FSM, apply.
     * No-op when no apps are excluded. `internal` for direct unit testing.
     */
    internal fun tickOnce() {
        if (exclusionPrefs.getExcludedCount() == 0) return

        val read = provider.currentForegroundPackage()
        if (read != null) lastKnownPkg = read
        val pkg = read ?: lastKnownPkg ?: return

        val grayscaleEnabled = grayscale.isGrayscaleEnabled()
        val transition = nextExclusionTransition(
            foregroundPackage = pkg,
            ownPackage = ownPackage,
            isExcluded = exclusionPrefs.isExcluded(pkg),
            excludedAppActive = exclusionPrefs.isExcludedAppActive(),
            grayscaleEnabled = grayscaleEnabled,
            wasGrayscaleOnBeforeExclusion = exclusionPrefs.wasGrayscaleOnBeforeExclusion(),
        )
        // The detector must not mutate the FSM once the screen is off. This poll runs on
        // a background thread, and stop()'s cooperative cancel cannot abort a tick already
        // in progress, so a tick that began while the screen was on could otherwise land
        // here after GrayoutService cleared the exclusion state on SCREEN_OFF, re-Enter,
        // and undo the pre-gray (see preGrayOnScreenOff). Checked after the read so it
        // reflects screen state at apply time; isScreenInteractive is @Volatile.
        if (!isScreenOn()) return
        applyExclusionTransition(
            transition = transition,
            exclusionPrefs = exclusionPrefs,
            grayscale = grayscale,
            enforcementInterval = enforcementPrefs.getInterval(),
            onExclusionEnded = onExclusionEnded,
        )
    }
}
