package com.princeyadav.grayout.service

/**
 * Pure, Android-free model + decision logic for the system daltonizer that
 * Grayout drives. Mirrors the [nextExclusionTransition] pattern: the hard part
 * is a pure function unit-tested on the JVM, and [GrayscaleManager] is the thin
 * Settings.Secure applier on top.
 *
 * The daltonizer is a SHARED system facility: mode 0 is full monochromacy (what
 * Grayout uses for "grayscale on"), while modes 11/12/13 are colour-deficiency
 * corrections that a colour-blind user may rely on. Grayout must therefore never
 * blindly stomp it to a hardcoded off — it restores whatever the user had before
 * Grayout first turned grayscale on.
 */

/** A daltonizer state as stored in Settings.Secure: the enabled flag + the mode. */
data class DaltonizerState(val enabled: Int, val mode: Int)

/** Grayout's "grayscale on" state: enabled, full monochromacy. */
val GRAYSCALE_ON_STATE = DaltonizerState(enabled = 1, mode = 0)

/** The neutral "off" used only when no user baseline was ever captured. */
val NEUTRAL_OFF_STATE = DaltonizerState(enabled = 0, mode = -1)

/**
 * What [GrayscaleManager] should do for one [setGrayscale] call.
 *
 * @param write the daltonizer state to write to Settings.Secure
 * @param captureBaseline if non-null, persist this as the new restore baseline
 *        first; null means leave the stored baseline untouched
 */
data class DaltonizerPlan(
    val write: DaltonizerState,
    val captureBaseline: DaltonizerState?,
)

/**
 * Whether [state] is Grayout's grayscale: enabled AND full monochromacy (mode 0).
 * A colour-correction mode (11/12/13) has `enabled == 1` but is NOT grayscale, so
 * checking the flag alone would mis-report a colour-blind user's correction as
 * "grayscale on".
 */
fun isGrayscaleOn(state: DaltonizerState): Boolean =
    state.enabled == 1 && state.mode == 0

/**
 * Decides what to write and whether to snapshot a restore baseline.
 *
 * - enable: write [GRAYSCALE_ON_STATE]. Capture [current] as the baseline UNLESS
 *   grayscale is already on (that would snapshot our own monochrome state and make
 *   "off" restore gray — the self-capture trap). Capturing on every enable-from-off
 *   also re-snapshots if the user changed their correction while Grayout was off.
 * - disable: write the captured [baseline], or [NEUTRAL_OFF_STATE] if none. Never
 *   capture.
 */
fun daltonizerPlan(
    enable: Boolean,
    current: DaltonizerState,
    baseline: DaltonizerState?,
): DaltonizerPlan =
    if (enable) {
        val capture = if (isGrayscaleOn(current)) null else current
        DaltonizerPlan(write = GRAYSCALE_ON_STATE, captureBaseline = capture)
    } else {
        DaltonizerPlan(write = baseline ?: NEUTRAL_OFF_STATE, captureBaseline = null)
    }
