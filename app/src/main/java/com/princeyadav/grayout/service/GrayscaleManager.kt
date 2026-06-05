package com.princeyadav.grayout.service

import android.content.Context
import android.net.Uri
import android.provider.Settings

/**
 * The only class that touches the system daltonizer (Settings.Secure). Thin
 * Settings.Secure applier over the pure [daltonizerPlan] decision logic, plus:
 *
 * - **Verified writes:** a single `putInt` can be silently dropped or clobbered
 *   by a competing display feature, so [applyAndVerify] reads back and retries
 *   once. `setGrayscale` only returns true when the value actually stuck.
 * - **Baseline preservation:** the daltonizer is a shared system facility (a
 *   colour-blind user's correction lives here too), so the user's pre-Grayout
 *   state is snapshotted before the first enable and restored on disable instead
 *   of a hardcoded off. See [DaltonizerPlan].
 *
 * Takes a [Context] (not just a ContentResolver) because the baseline survives
 * process death in [EnforcementPrefs.PREFS_NAME].
 */
class GrayscaleManager(context: Context) : GrayscaleController {

    private val contentResolver = context.contentResolver
    private val prefs = context.applicationContext
        .getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE)

    override fun isGrayscaleEnabled(): Boolean = isGrayscaleOn(readState())

    override fun setGrayscale(enabled: Boolean): Boolean {
        val current = readState()
        val plan = daltonizerPlan(enable = enabled, current = current, baseline = readBaseline())
        plan.captureBaseline?.let { writeBaseline(it) }
        if (current == plan.write) return true
        return try {
            applyAndVerify(plan.write)
        } catch (_: SecurityException) {
            false
        }
    }

    override fun canWriteSecureSettings(): Boolean = try {
        val current = Settings.Secure.getInt(contentResolver, DALTONIZER_ENABLED, 0)
        Settings.Secure.putInt(contentResolver, DALTONIZER_ENABLED, current)
        true
    } catch (_: SecurityException) {
        false
    }

    private fun readState(): DaltonizerState = DaltonizerState(
        enabled = Settings.Secure.getInt(contentResolver, DALTONIZER_ENABLED, 0),
        mode = Settings.Secure.getInt(contentResolver, DALTONIZER_MODE, -1),
    )

    /**
     * Writes [target], reads it back, and retries once if it didn't stick. Same
     * ContentProvider, so the read-back reflects the write synchronously — no
     * delay or background thread needed. Returns whether the value holds.
     */
    private fun applyAndVerify(target: DaltonizerState): Boolean {
        writeState(target)
        if (readState() == target) return true
        writeState(target)
        return readState() == target
    }

    private fun writeState(state: DaltonizerState) {
        Settings.Secure.putInt(contentResolver, DALTONIZER_ENABLED, state.enabled)
        Settings.Secure.putInt(contentResolver, DALTONIZER_MODE, state.mode)
    }

    private fun readBaseline(): DaltonizerState? {
        if (!prefs.getBoolean(KEY_BASELINE_CAPTURED, false)) return null
        return DaltonizerState(
            enabled = prefs.getInt(KEY_BASELINE_ENABLED, 0),
            mode = prefs.getInt(KEY_BASELINE_MODE, -1),
        )
    }

    private fun writeBaseline(state: DaltonizerState) {
        prefs.edit()
            .putBoolean(KEY_BASELINE_CAPTURED, true)
            .putInt(KEY_BASELINE_ENABLED, state.enabled)
            .putInt(KEY_BASELINE_MODE, state.mode)
            .apply()
    }

    companion object {
        private const val DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
        private const val DALTONIZER_MODE = "accessibility_display_daltonizer"
        private const val KEY_BASELINE_CAPTURED = "daltonizer_baseline_captured"
        private const val KEY_BASELINE_ENABLED = "daltonizer_baseline_enabled"
        private const val KEY_BASELINE_MODE = "daltonizer_baseline_mode"
        val DALTONIZER_ENABLED_URI: Uri = Settings.Secure.getUriFor(DALTONIZER_ENABLED)
    }
}
