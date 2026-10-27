package com.kirox.app.telegram

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import org.drinkless.tdlib.TdApi

/** Kind of the device's default network, as TDLib distinguishes it. */
enum class DeviceNetwork { None, WiFi, Mobile, MobileRoaming, Other }

/** Pure mapping, kept apart from Android classes so it can be unit-tested. */
internal fun deviceNetworkOf(connected: Boolean, wifi: Boolean, cellular: Boolean, roaming: Boolean): DeviceNetwork = when {
    !connected -> DeviceNetwork.None
    wifi -> DeviceNetwork.WiFi
    cellular && roaming -> DeviceNetwork.MobileRoaming
    cellular -> DeviceNetwork.Mobile
    else -> DeviceNetwork.Other
}

internal fun DeviceNetwork.toTd(): TdApi.NetworkType = when (this) {
    DeviceNetwork.None -> TdApi.NetworkTypeNone()
    DeviceNetwork.WiFi -> TdApi.NetworkTypeWiFi()
    DeviceNetwork.Mobile -> TdApi.NetworkTypeMobile()
    DeviceNetwork.MobileRoaming -> TdApi.NetworkTypeMobileRoaming()
    DeviceNetwork.Other -> TdApi.NetworkTypeOther()
}

/**
 * Tells TDLib which network the device uses (SetNetworkType). Without it TDLib only notices a lost
 * or switched network by its own timeouts, so reconnecting after Wi-Fi ↔ mobile or airplane mode is slow.
 * Each new client (start, account switch, return from background) gets the current type once.
 */
class ConnectionMonitor(context: Context, private val client: TelegramClient) {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    // Read and written only on the TDLib dispatcher.
    private var current = DeviceNetwork.Other
    private var reportedToClient = false

    init {
        client.addUpdateHandler { update ->
            if (update !is TdApi.UpdateAuthorizationState) return@addUpdateHandler
            when (update.authorizationState) {
                is TdApi.AuthorizationStateClosing, is TdApi.AuthorizationStateClosed -> reportedToClient = false
                else -> if (!reportedToClient) report()
            }
        }
        val initial = connectivity?.let { manager -> manager.activeNetwork?.let { manager.getNetworkCapabilities(it) } }
        client.post { current = initial.toDeviceNetwork() }
        runCatching {
            connectivity?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = changed(capabilities.toDeviceNetwork())
                override fun onLost(network: Network) = changed(DeviceNetwork.None)
            })
        }
    }

    private fun changed(network: DeviceNetwork) = client.post {
        if (network == current) return@post
        current = network
        report()
    }

    private fun report() {
        // false while no client runs; the next client reports on its first auth update.
        reportedToClient = client.send(TdApi.SetNetworkType(current.toTd()))
    }

    private fun NetworkCapabilities?.toDeviceNetwork(): DeviceNetwork = if (this == null) DeviceNetwork.None else deviceNetworkOf(
        connected = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        wifi = hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
        cellular = hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
        roaming = !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
    )
}
