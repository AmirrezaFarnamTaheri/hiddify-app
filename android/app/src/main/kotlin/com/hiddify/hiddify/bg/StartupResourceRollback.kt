package com.hiddify.hiddify.bg

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Resource ownership for one Android service startup attempt.
 *
 * Mark each operation *before* invoking it: registration/native setup can throw
 * after partially acquiring resources. Failed startup unwinds only the work
 * this attempt could have acquired, never a core from an unrelated attempt.
 */
internal class StartupResourceRollback {
    @Volatile private var networkAttempted = false
    @Volatile private var coreAttempted = false
    private val rolledBack = AtomicBoolean(false)

    fun beforeNetworkStart() {
        networkAttempted = true
    }

    fun beforeCoreSetup() {
        coreAttempted = true
    }

    /**
     * Always try every owned cleanup step, even if one throws. Report errors
     * instead of claiming native cleanup succeeded when it did not.
     */
    suspend fun rollback(
        closeTun: () -> Unit,
        stopNetwork: suspend () -> Unit,
        closeCore: () -> Unit,
    ): List<Exception> {
        if (!rolledBack.compareAndSet(false, true)) return emptyList()

        val errors = mutableListOf<Exception>()
        if (coreAttempted) {
            try {
                closeTun()
            } catch (failure: Exception) {
                errors += failure
            }
        }
        if (networkAttempted) {
            try {
                stopNetwork()
            } catch (failure: Exception) {
                errors += failure
            }
        }
        if (coreAttempted) {
            try {
                closeCore()
            } catch (failure: Exception) {
                errors += failure
            }
        }
        return errors
    }
}
