package com.hiddify.hiddify.bg

/** Owned by the single-threaded network actor. Late callbacks from a previous
 * registration must never change the state of a newer registration. */
internal class NetworkCallbackEpoch {
    private var current = 0L

    fun newRegistration(): Long = ++current

    fun stop() {
        ++current
    }

    fun accepts(registration: Long): Boolean = registration == current
}
