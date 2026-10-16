package com.princeyadav.grayout.testutil

import androidx.test.platform.app.InstrumentationRegistry
import com.princeyadav.grayout.service.UsageAccess
import org.junit.Assert.assertEquals
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.FileInputStream

/**
 * Grants Usage Access for tests that run the real foreground detector, restoring the
 * prior app-op mode afterwards. Without it the detector correctly ends any active
 * exclusion session, since a denied query can no longer see the excluded app.
 */
class UsageAccessRule : TestRule {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val previous = Regex("GET_USAGE_STATS: (\\w+)")
                .find(shell("appops get ${context.packageName} GET_USAGE_STATS"))
                ?.groupValues?.get(1) ?: "default"
            try {
                shell("appops set ${context.packageName} GET_USAGE_STATS allow")
                assertEquals("Could not grant Usage Access", true, UsageAccess.isGranted(context))
                base.evaluate()
            } finally {
                shell("appops set ${context.packageName} GET_USAGE_STATS $previous")
            }
        }
    }

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { it.readBytes().decodeToString() }
        }
}
