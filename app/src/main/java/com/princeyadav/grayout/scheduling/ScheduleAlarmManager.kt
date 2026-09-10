package com.princeyadav.grayout.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.data.scheduleOperationMutex
import com.princeyadav.grayout.logic.isCurrentlyFiring
import com.princeyadav.grayout.logic.legacyScheduleDeliveryTarget
import com.princeyadav.grayout.logic.nextScheduleEvent
import com.princeyadav.grayout.logic.scheduleDeliveryTarget
import com.princeyadav.grayout.logic.scheduleConfiguration
import com.princeyadav.grayout.logic.scheduleReconciliationTarget
import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.ExclusionPrefs
import com.princeyadav.grayout.service.GrayoutService
import com.princeyadav.grayout.service.GrayscaleManager
import com.princeyadav.grayout.service.GrayscaleController
import com.princeyadav.grayout.service.GrayscaleStateLock
import com.princeyadav.grayout.service.applyScheduleGrayscale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class ScheduleAlarmManager(
    private val context: Context,
    private val clock: Clock? = null,
    private val grayscale: GrayscaleController = GrayscaleManager(context),
) : AlarmScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
    private val state = ScheduleAlarmState(prefs)
    private val reconciliationState = ScheduleReconciliationState(prefs)

    override suspend fun reschedule(repository: ScheduleRepository): Unit = withContext(Dispatchers.IO) {
        scheduleOperationMutex.withLock {
            val enabledSchedules = repository.getEnabledSchedules()
            if (reconciliationState.read()?.configuration?.let {
                    it != scheduleConfiguration(enabledSchedules)
                } == true) clearReconciliation()
            val reading = currentClock()
            replaceNextAlarm(enabledSchedules, reading.instant(), reading.zone)
            synchronizeActiveWindow(enabledSchedules)
        }
    }

    /** Rebuild OS registrations while preserving manual choices within unchanged coverage. */
    internal suspend fun reconcileSystemChange(
        repository: ScheduleRepository,
        retryOnly: Boolean = false,
    ): Unit = withContext(Dispatchers.IO) {
        scheduleOperationMutex.withLock {
            val schedules = repository.getEnabledSchedules()
            val pending = reconciliationState.read()
            if (retryOnly && pending == null) return@withLock
            val armed = state.read()
            val decision = synchronized(GrayscaleStateLock) {
                val reading = currentClock()
                val now = reading.instant()
                val zone = reading.zone
                val configuration = scheduleConfiguration(schedules)
                val excluded = ExclusionPrefs(prefs).isExcludedAppActive()
                val mayRetry = pending != null && pending.configuration == configuration &&
                    (excluded || grayscale.isGrayscaleEnabled() == pending.observedGray)
                val boundaryTarget = if (retryOnly) {
                    // A retry can race a normal boundary. Consume its current
                    // decision before replacing the alarm, even when the older
                    // failed write was superseded by a manual display choice.
                    armed?.let { scheduleDeliveryTarget(it.event(), schedules, now, zone,
                        Instant.ofEpochMilli(it.startMillis), Instant.ofEpochMilli(it.endMillis)) }
                } else scheduleReconciliationTarget(armed, schedules, now, zone)
                val target = boundaryTarget ?: if (mayRetry) {
                    schedules.any { isCurrentlyFiring(it, now, zone) }
                } else null
                if (target != null) applyWithRecovery(target, schedules)
                else if (pending != null) clearReconciliation()
                DeliveryDecision(now, zone, null)
            }
            replaceNextAlarm(schedules, decision.at, decision.zone)
            if (reconciliationState.read() != null) scheduleReconciliationRetry()
        }
    }

    /**
     * Consume only the current registration, validate its original occurrence, and
     * rearm without the editor/boot path's active-window display synchronization.
     * Returns the handled boundary kind (start/end), or null for an ignored event.
     */
    internal suspend fun handleAlarm(
        repository: ScheduleRepository,
        generation: String?,
        legacyIsStart: Boolean?,
    ): Boolean? = withContext(Dispatchers.IO) {
        scheduleOperationMutex.withLock {
            val armed = state.read()
            // A canceled/duplicate event must not replace a newer alarm or undo a
            // manual toggle after that registration. This includes legacy intents.
            if (armed != null && armed.generation != generation) {
                // Repair the persist-before-register crash gap without changing
                // generation/deadline or applying the stale delivery's display state.
                setExactAlarm(armed)
                return@withLock null
            }
            val enabledSchedules = repository.getEnabledSchedules()
            val decision = synchronized(GrayscaleStateLock) {
                val reading = currentClock()
                val now = reading.instant()
                val zone = reading.zone
                // Due ends still close after a zone change. Starts must be valid
                // in both the armed absolute interval and current civil window.
                val target = if (armed != null) {
                    scheduleDeliveryTarget(armed.event(), enabledSchedules, now, zone,
                        Instant.ofEpochMilli(armed.startMillis), Instant.ofEpochMilli(armed.endMillis))
                } else if (armed == null && generation == null && !state.isInitialized()) {
                    legacyScheduleDeliveryTarget(legacyIsStart, enabledSchedules, now, zone)
                } else null
                val handledStart = if (target != null) {
                    applyWithRecovery(target, enabledSchedules)
                    armed?.isStart ?: legacyIsStart
                } else null
                DeliveryDecision(now, zone, handledStart)
            }
            // Expired, edited, or deleted current boundaries still advance the
            // chain, but never call synchronizeActiveWindow after being ignored.
            // Keep the decision time: an end crossed during the display write
            // must still be armed, even if Android now needs to deliver it immediately.
            replaceNextAlarm(enabledSchedules, decision.at, decision.zone)
            if (reconciliationState.read() != null) scheduleReconciliationRetry()
            decision.handledStart
        }
    }

    private fun replaceNextAlarm(schedules: List<Schedule>, now: Instant, zone: ZoneId) {
        val next = nextScheduleEvent(schedules, now, zone)?.let { ArmedScheduleAlarm.create(it, zone) }
        state.save(next)
        cancelAll()
        if (next != null) setExactAlarm(next)
    }

    /** Only explicit schedule edits/boot synchronize an already active window. */
    private fun synchronizeActiveWindow(enabledSchedules: List<Schedule>) {
        val isCurrentlyInSchedule = synchronized(GrayscaleStateLock) {
            // Recheck after acquiring the transition lock. A boundary may have
            // passed while an in-flight detector write held it.
            val reading = currentClock()
            val applyTime = reading.instant()
            val active = enabledSchedules.any { isCurrentlyFiring(it, applyTime, reading.zone) }
            if (active) {
                applyWithRecovery(true, enabledSchedules)
            } else {
                val pending = reconciliationState.read()
                if (pending != null) {
                    if (ExclusionPrefs(prefs).isExcludedAppActive() ||
                        grayscale.isGrayscaleEnabled() == pending.observedGray) {
                        applyWithRecovery(false, enabledSchedules)
                    } else clearReconciliation()
                }
            }
            active
        }

        if (reconciliationState.read() != null) scheduleReconciliationRetry()

        if (isCurrentlyInSchedule) {
            val enforcementPrefs = EnforcementPrefs(prefs)
            val interval = enforcementPrefs.getInterval()
            if (interval > 0) {
                val serviceIntent = Intent(context, GrayoutService::class.java)
                    .putExtra(GrayoutService.EXTRA_INTERVAL, interval)
                context.startForegroundServiceSafely(serviceIntent)
            }
        }
    }

    private fun applyWithRecovery(target: Boolean, schedules: List<Schedule>) {
        reconciliationState.save(PendingScheduleReconciliation(
            scheduleConfiguration(schedules), grayscale.isGrayscaleEnabled(),
        ))
        val success = applyScheduleGrayscale(target, ExclusionPrefs(prefs), grayscale) {
            context.getSystemService(PowerManager::class.java).isInteractive
        }
        if (success) clearReconciliation()
        else reconciliationState.save(PendingScheduleReconciliation(
            scheduleConfiguration(schedules), grayscale.isGrayscaleEnabled(),
        ))
    }

    private fun clearReconciliation() {
        if (reconciliationState.read() != null) reconciliationState.save(null)
        val intent = Intent(context, SystemScheduleReceiver::class.java)
            .setAction(SystemScheduleReceiver.ACTION_RETRY)
        PendingIntent.getBroadcast(context, RETRY_REQUEST_CODE, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun scheduleReconciliationRetry() {
        val intent = Intent(context, SystemScheduleReceiver::class.java)
            .setAction(SystemScheduleReceiver.ACTION_RETRY)
        val pendingIntent = PendingIntent.getBroadcast(context, RETRY_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // Recovery is not a user-visible precise boundary and can be batched by Android.
        alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 60_000L, pendingIntent)
    }

    private fun setExactAlarm(alarm: ArmedScheduleAlarm) {
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ACTION_SCHEDULE_FIRE
            putExtra(EXTRA_IS_START, alarm.isStart)
            putExtra(EXTRA_GENERATION, alarm.generation)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, alarm.triggerMillis, pendingIntent,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, alarm.triggerMillis, pendingIntent,
                )
            }
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the call. Fall back to inexact.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, alarm.triggerMillis, pendingIntent,
            )
        }
    }

    private fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    // A retained manager must see later device timezone changes too.
    private fun currentClock(): Clock = clock ?: Clock.systemDefaultZone()

    private data class DeliveryDecision(val at: Instant, val zone: ZoneId, val handledStart: Boolean?)

    private fun cancelAll() {
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ACTION_SCHEDULE_FIRE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    companion object {
        const val ACTION_SCHEDULE_FIRE = "com.princeyadav.grayout.SCHEDULE_FIRE"
        const val EXTRA_IS_START = "is_start"
        const val EXTRA_GENERATION = "schedule_generation"
        private const val REQUEST_CODE = 1001
        internal const val RETRY_REQUEST_CODE = 1002
    }
}
