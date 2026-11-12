package com.hiddify.hiddify.bg

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupResourceRollbackTest {
    @Test
    fun partialNetworkRegistrationIsStillUnregistered() = runBlocking {
        val resources = StartupResourceRollback()
        resources.beforeNetworkStart()
        val events = mutableListOf<String>()
        val errors = resources.rollback(
            closeTun = { events += "tun" },
            stopNetwork = { events += "network" },
            closeCore = { events += "core" },
        )
        assertEquals(listOf("network"), events)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun failureAfterNativeSetupUnwindsTunnelMonitorThenCore() = runBlocking {
        val resources = StartupResourceRollback()
        resources.beforeNetworkStart()
        resources.beforeCoreSetup()
        val events = mutableListOf<String>()
        val errors = resources.rollback(
            closeTun = { events += "tun" },
            stopNetwork = { events += "network" },
            closeCore = { events += "core" },
        )
        assertEquals(listOf("tun", "network", "core"), events)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun cleanupFailureCannotSkipRemainingResources() = runBlocking {
        val resources = StartupResourceRollback()
        resources.beforeNetworkStart()
        resources.beforeCoreSetup()
        val events = mutableListOf<String>()
        val errors = resources.rollback(
            closeTun = { events += "tun"; error("tun failure") },
            stopNetwork = { events += "network"; error("network failure") },
            closeCore = { events += "core" },
        )
        assertEquals(listOf("tun", "network", "core"), events)
        assertEquals(2, errors.size)
    }

    @Test
    fun abortBeforeResourceAcquisitionDoesNotTearDownUnrelatedCore() = runBlocking {
        val resources = StartupResourceRollback()
        val events = mutableListOf<String>()
        val errors = resources.rollback(
            closeTun = { events += "tun" },
            stopNetwork = { events += "network" },
            closeCore = { events += "core" },
        )
        assertTrue(events.isEmpty())
        assertTrue(errors.isEmpty())
    }

    @Test
    fun duplicateRollbackRequestRunsCleanupOnlyOnce() = runBlocking {
        val resources = StartupResourceRollback()
        resources.beforeNetworkStart()
        resources.beforeCoreSetup()
        val events = mutableListOf<String>()
        val stopTun = { events += "tun"; Unit }
        val stopNetwork: suspend () -> Unit = { events += "network" }
        val closeCore = { events += "core"; Unit }
        resources.rollback(stopTun, stopNetwork, closeCore)
        resources.rollback(stopTun, stopNetwork, closeCore)
        assertEquals(listOf("tun", "network", "core"), events)
    }
}
