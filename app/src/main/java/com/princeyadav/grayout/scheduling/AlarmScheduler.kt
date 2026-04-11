package com.princeyadav.grayout.scheduling

import com.princeyadav.grayout.data.ScheduleRepository

/**
 * Abstraction over the alarm-rescheduling surface used by [ScheduleAlarmManager].
 *
 * Extracted so ViewModels and other injected callers depend on an interface
 * rather than the concrete, context-bound manager — which enables JVM-only
 * fakes to record [reschedule] invocations without touching [android.app.AlarmManager].
 */
interface AlarmScheduler {
    suspend fun reschedule(repository: ScheduleRepository)
}
