package com.princeyadav.grayout.fakes

import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.scheduling.AlarmScheduler

/**
 * Fake [AlarmScheduler] for JVM tests. Records the number of [reschedule]
 * invocations and otherwise does nothing — no real AlarmManager interaction.
 *
 * Tests that care about "did the VM ask the scheduler to reschedule?" assert
 * on [rescheduleCallCount]. Tests that also care about which schedules are
 * being scheduled can inspect [lastRescheduleRepository] on the fake.
 */
class FakeScheduleAlarmManager : AlarmScheduler {
    var rescheduleCallCount = 0
        private set

    var lastRescheduleRepository: ScheduleRepository? = null
        private set

    override suspend fun reschedule(repository: ScheduleRepository) {
        rescheduleCallCount++
        lastRescheduleRepository = repository
    }
}
