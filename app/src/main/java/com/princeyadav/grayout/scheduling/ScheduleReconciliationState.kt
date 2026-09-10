package com.princeyadav.grayout.scheduling

import android.content.SharedPreferences

/** Evidence retained until a failed system-change display write is resolved. */
internal data class PendingScheduleReconciliation(val configuration: String, val observedGray: Boolean)

internal class ScheduleReconciliationState(private val prefs: SharedPreferences) {
    fun read(): PendingScheduleReconciliation? = prefs.getString(CONFIGURATION, null)?.let {
        PendingScheduleReconciliation(it, prefs.getBoolean(OBSERVED_GRAY, false))
    }

    fun save(value: PendingScheduleReconciliation?) {
        val editor = prefs.edit()
        if (value == null) editor.remove(CONFIGURATION).remove(OBSERVED_GRAY)
        else editor.putString(CONFIGURATION, value.configuration).putBoolean(OBSERVED_GRAY, value.observedGray)
        check(editor.commit()) { "Could not persist schedule reconciliation" }
    }

    private companion object {
        const val CONFIGURATION = "schedule_reconciliation_configuration"
        const val OBSERVED_GRAY = "schedule_reconciliation_observed_gray"
    }
}
