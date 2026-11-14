package com.hiddify.hiddify.bg

/**
 * Isolates subscriber exceptions from the process-wide network actor.
 * One callback cannot prevent other subscribers from hearing a handover.
 */
internal object SafeNetworkListenerFanout {
    fun <T> dispatch(
        listeners: Collection<(T?) -> Unit>,
        network: T?,
        onException: (Exception) -> Unit = {},
    ) {
        listeners.toList().forEach { observer ->
            try {
                observer(network)
            } catch (error: Exception) {
                // A malfunctioning reporter must not kill the network actor either.
                try { onException(error) } catch (_: Exception) { }
            }
        }
    }
}
