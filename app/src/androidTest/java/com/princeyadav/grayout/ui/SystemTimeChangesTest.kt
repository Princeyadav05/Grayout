package com.princeyadav.grayout.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemTimeChangesTest {
    @Test
    fun receiverRegistersOnCollectionEmitsClockChangesAndUnregistersOnCancellation() = runBlocking {
        val context = RecordingContext(ApplicationProvider.getApplicationContext())
        val events = Channel<Unit>(Channel.UNLIMITED)
        val changes = context.systemTimeChanges()
        assertEquals(0, context.registrations)
        val observer = launch { changes.collect { events.send(it) } }
        withTimeout(2_000) { events.receive() }
        assertEquals(1, context.registrations)
        assertEquals(2, context.filter.countActions())
        assertTrue(context.filter.hasAction(Intent.ACTION_TIME_CHANGED))
        assertTrue(context.filter.hasAction(Intent.ACTION_TIMEZONE_CHANGED))

        context.receiver.onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))
        withTimeout(2_000) { events.receive() }
        context.receiver.onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))
        withTimeout(2_000) { events.receive() }
        context.receiver.onReceive(context, Intent(Intent.ACTION_TIME_TICK))
        assertTrue(events.tryReceive().isFailure)

        observer.cancelAndJoin()
        assertEquals(1, context.unregistrations)
        val resumed = launch { changes.collect { events.send(it) } }
        withTimeout(2_000) { events.receive() }
        assertEquals(2, context.registrations)
        resumed.cancelAndJoin()
        assertEquals(2, context.unregistrations)
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        lateinit var receiver: BroadcastReceiver
        lateinit var filter: IntentFilter
        var registrations = 0
        var unregistrations = 0

        override fun registerReceiver(
            receiver: BroadcastReceiver?,
            filter: IntentFilter,
            broadcastPermission: String?,
            scheduler: Handler?,
            flags: Int,
        ): Intent? = record(receiver, filter)

        override fun registerReceiver(
            receiver: BroadcastReceiver?,
            filter: IntentFilter,
            broadcastPermission: String?,
            scheduler: Handler?,
        ): Intent? = record(receiver, filter)

        private fun record(receiver: BroadcastReceiver?, filter: IntentFilter): Intent? {
            this.receiver = checkNotNull(receiver)
            this.filter = filter
            registrations++
            return null
        }

        override fun unregisterReceiver(receiver: BroadcastReceiver) {
            assertSame(this.receiver, receiver)
            unregistrations++
        }
    }
}
