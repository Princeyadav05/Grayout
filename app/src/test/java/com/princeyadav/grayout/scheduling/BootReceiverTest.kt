package com.princeyadav.grayout.scheduling

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BootReceiverTest {

    @Test
    fun `an app update restarts recovery without the boot resync`() {
        assertEquals(StartupRecovery.Boot, startupRecoveryFor(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(StartupRecovery.Update, startupRecoveryFor(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertNull(startupRecoveryFor(Intent.ACTION_TIME_CHANGED))
    }
}
