package com.hiddify.hiddify.bg

import android.annotation.TargetApi
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.hiddify.hiddify.Application
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import java.net.UnknownHostException


object DefaultNetworkListener {
    private sealed class NetworkMessage {
        class Start(val key: Any, val listener: (Network?) -> Unit) : NetworkMessage()

        class Get : NetworkMessage() {
            val response = CompletableDeferred<Network>()
        }

        class Forget(val response: CompletableDeferred<Network>) : NetworkMessage()

        class Stop(val key: Any) : NetworkMessage()

        class Put(val network: Network, val generation: Long) : NetworkMessage()

        class Update(val network: Network, val generation: Long) : NetworkMessage()

        class Lost(val network: Network, val generation: Long) : NetworkMessage()
    }

    // Process-wide singleton: actor stays alive across start/stop cycles, but
    // callbacks never execute or wait on its message-processing coroutine.
    private val actorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Subscribers are untrusted callbacks; never let one exception terminate this actor. */
    private fun notifySafely(listeners: Collection<(Network?) -> Unit>, network: Network?) {
        SafeNetworkListenerFanout.dispatch(listeners, network) {
            // Never log a callback exception: its message can contain credentials.
            android.util.Log.w("DefaultNetworkListener", "A network subscriber callback failed")
        }
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    private val networkActor =
        actorScope.actor<NetworkMessage>(capacity = Channel.UNLIMITED) {
            val listeners = mutableMapOf<Any, (Network?) -> Unit>()
            var network: Network? = null
            val pendingRequests = PendingNetworkRequests<Network>()
            val callbackEpoch = NetworkCallbackEpoch()
            var registeredCallback: ConnectivityManager.NetworkCallback? = null
            for (message in channel) {
                when (message) {
                    is NetworkMessage.Start -> {
                        if (listeners.isEmpty()) {
                            val generation = callbackEpoch.newRegistration()
                            registeredCallback = register(generation)
                        }
                        listeners[message.key] = message.listener
                        if (network != null) notifySafely(listOf(message.listener), network)
                    }

                    is NetworkMessage.Get -> {
                        if (listeners.isEmpty()) {
                            // Never crash the actor on a Get/Stop race.
                            message.response.completeExceptionally(
                                IllegalStateException("Getting network without any listeners is not supported"),
                            )
                        } else if (network == null) {
                            pendingRequests.add(message.response)
                        } else {
                            message.response.complete(network)
                        }
                    }

                    is NetworkMessage.Forget -> pendingRequests.remove(message.response)

                    is NetworkMessage.Stop ->
                        if (listeners.isNotEmpty() &&
                            // was not empty
                            listeners.remove(message.key) != null &&
                            listeners.isEmpty()
                        ) {
                            network = null
                            callbackEpoch.stop()
                            pendingRequests.reject(CancellationException("Default network listener stopped"))
                            registeredCallback?.let { unregister(it) }
                            registeredCallback = null
                        }

                    is NetworkMessage.Put -> {
                        // A callback may already be queued when the last listener stops.
                        if (listeners.isNotEmpty() && callbackEpoch.accepts(message.generation)) {
                            network = message.network
                            pendingRequests.resolve(message.network)
                            notifySafely(listeners.values, network)
                        }
                    }

                    is NetworkMessage.Update ->
                        if (callbackEpoch.accepts(message.generation) && network == message.network) {
                            notifySafely(listeners.values, network)
                        }

                    is NetworkMessage.Lost ->
                        if (callbackEpoch.accepts(message.generation) && network == message.network) {
                            network = null
                            notifySafely(listeners.values, null)
                        }
                }
            }
        }

    suspend fun start(key: Any, listener: (Network?) -> Unit) = networkActor.send(
        NetworkMessage.Start(
            key,
            listener,
        ),
    )

    suspend fun get(): Network = if (fallback) {
        @TargetApi(23)
        Application.connectivity.activeNetwork
            ?: error("missing default network") // failed to listen, return current if available
    } else {
        NetworkMessage.Get().run {
            networkActor.send(this)
            try {
                withTimeout(15_000L) { response.await() }
            } finally {
                // Do not retain a waiter whose caller timed out or was cancelled.
                response.cancel()
                networkActor.trySend(NetworkMessage.Forget(response))
            }
        }
    }

    suspend fun stop(key: Any) = networkActor.send(NetworkMessage.Stop(key))

    // OS connectivity callbacks must not block. The actor processes these
    // in FIFO order even if a registration handler is slow.
    private fun dispatch(message: NetworkMessage) {
        if (networkActor.trySend(message).isFailure) {
            android.util.Log.w("DefaultNetworkListener", "Default-network actor unexpectedly closed")
        }
    }

    // NB: this runs in ConnectivityThread, and this behavior cannot be changed until API 26
    private class Callback(private val generation: Long) : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            dispatch(NetworkMessage.Put(network, generation))
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            dispatch(NetworkMessage.Update(network, generation))
        }

        override fun onLost(network: Network) {
            dispatch(NetworkMessage.Lost(network, generation))
        }
    }

    private var fallback = false
    private val request =
        NetworkRequest.Builder().apply {
            addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            if (Build.VERSION.SDK_INT == 23) { // workarounds for OEM bugs
                removeCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                removeCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
            }
        }.build()
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Unfortunately registerDefaultNetworkCallback is going to return VPN interface since Android P DP1:
     * https://android.googlesource.com/platform/frameworks/base/+/dda156ab0c5d66ad82bdcf76cda07cbc0a9c8a2e
     *
     * This makes doing a requestNetwork with REQUEST necessary so that we don't get ALL possible networks that
     * satisfies default network capabilities but only THE default network. Unfortunately, we need to have
     * android.permission.CHANGE_NETWORK_STATE to be able to call requestNetwork.
     *
     * Source: https://android.googlesource.com/platform/frameworks/base/+/2df4c7d/services/core/java/com/android/server/ConnectivityService.java#887
     */
    private fun register(generation: Long): ConnectivityManager.NetworkCallback {
        val callback = Callback(generation)
        when (Build.VERSION.SDK_INT) {
            in 31..Int.MAX_VALUE ->
                @TargetApi(31)
                {
                    Application.connectivity.registerBestMatchingNetworkCallback(
                        request,
                        callback,
                        mainHandler,
                    )
                }

            in 28 until 31 ->
                @TargetApi(28)
                { // we want REQUEST here instead of LISTEN
                    Application.connectivity.requestNetwork(request, callback, mainHandler)
                }

            in 26 until 28 ->
                @TargetApi(26)
                {
                    Application.connectivity.registerDefaultNetworkCallback(callback, mainHandler)
                }

            in 24 until 26 ->
                @TargetApi(24)
                {
                    Application.connectivity.registerDefaultNetworkCallback(callback)
                }

            else ->
                try {
                    fallback = false
                    Application.connectivity.requestNetwork(request, callback)
                } catch (e: RuntimeException) {
                    fallback =
                        true // known bug on API 23: https://stackoverflow.com/a/33509180/2245107
                }
        }
        return callback
    }
    private fun unregister(callback: ConnectivityManager.NetworkCallback) {
        runCatching {
            Application.connectivity.unregisterNetworkCallback(callback)
        }
    }
}