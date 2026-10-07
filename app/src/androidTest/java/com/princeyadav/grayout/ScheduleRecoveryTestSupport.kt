package com.princeyadav.grayout

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withTimeout

/** Close all ActivityScenarios first, then drain process-owned work before changing fixtures. */
internal suspend fun awaitScheduleStartupRecovery() = withTimeout(10_000) {
    val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as GrayoutApp
    checkNotNull(app.scheduleRecoveryScope.coroutineContext[Job]).children.toList().joinAll()
}
