package com.princeyadav.grayout.testutil

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.FileInputStream

/** Exercise real ADB permission changes without leaking the grant to another test. */
class SecureSettingsPermissionRule(private val granted: Boolean) : TestRule {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val wasGranted = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED
            try {
                setGranted(granted)
                base.evaluate()
            } finally {
                // Also restore after failed setup/assertions and after the activity has closed.
                setGranted(wasGranted)
            }
        }
    }

    fun setGranted(granted: Boolean) {
        val operation = if (granted) "grant" else "revoke"
        val output = instrumentation.uiAutomation.executeShellCommand(
            "pm $operation ${context.packageName} android.permission.WRITE_SECURE_SETTINGS",
        ).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { it.readBytes().decodeToString() }
        }
        assertEquals(
            "Could not $operation WRITE_SECURE_SETTINGS: $output",
            if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS),
        )
    }
}
