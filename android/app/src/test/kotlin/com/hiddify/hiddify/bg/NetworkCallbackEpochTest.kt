package com.hiddify.hiddify.bg

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkCallbackEpochTest {
    @Test fun oldRegistrationCannotDeliverEventsToNewSession() {
        val epochs = NetworkCallbackEpoch()
        val old = epochs.newRegistration()
        assertTrue(epochs.accepts(old))
        epochs.stop()
        assertFalse(epochs.accepts(old))
        val fresh = epochs.newRegistration()
        assertNotEquals(old, fresh)
        assertFalse(epochs.accepts(old))
        assertTrue(epochs.accepts(fresh))
    }

    @Test fun queuedCallbackAfterLastListenerStopsIsRejected() {
        val epochs = NetworkCallbackEpoch()
        val token = epochs.newRegistration()
        epochs.stop()
        assertFalse(epochs.accepts(token))
    }

    @Test fun twoRapidReRegistrationsNeverReviveFirstCallback() {
        val epochs = NetworkCallbackEpoch()
        val first = epochs.newRegistration()
        epochs.stop()
        val second = epochs.newRegistration()
        epochs.stop()
        val third = epochs.newRegistration()
        assertFalse(epochs.accepts(first))
        assertFalse(epochs.accepts(second))
        assertTrue(epochs.accepts(third))
    }
}
