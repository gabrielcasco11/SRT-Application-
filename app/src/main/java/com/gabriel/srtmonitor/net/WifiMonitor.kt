package com.gabriel.srtmonitor.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks whether the phone is on Wi-Fi (or Ethernet) and its local IPv4 address,
 * independently of which network Android uses for internet. That matters for demo
 * networks without internet access, where Android keeps mobile data as default.
 */
class WifiMonitor(context: Context) {

    data class State(
        val connected: Boolean = false,
        val ip: String? = null,
        val network: Network? = null,
    )

    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val addresses = ConcurrentHashMap<Network, String>()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val connectivityManager: ConnectivityManager get() = cm

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            addresses[network] = ipv4(cm.getLinkProperties(network)) ?: ""
            publish()
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            addresses[network] = ipv4(linkProperties) ?: ""
            publish()
        }

        override fun onLost(network: Network) {
            addresses.remove(network)
            publish()
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
    }

    private fun publish() {
        val entry = addresses.entries.firstOrNull { it.value.isNotEmpty() } ?: addresses.entries.firstOrNull()
        _state.value = if (entry == null) State() else State(true, entry.value.ifEmpty { null }, entry.key)
    }

    private fun ipv4(lp: LinkProperties?): String? =
        lp?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
}
