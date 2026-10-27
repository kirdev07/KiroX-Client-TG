package com.kirox.app.voip

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where call audio is played. */
enum class CallAudioRoute(val label: String) {
    Earpiece("Телефон"), Speaker("Динамик"), Headset("Наушники"), Bluetooth("Bluetooth")
}

/** Route used when a call connects or the current route disappears: a headset first, then the call kind's default. */
internal fun preferredRoute(available: Set<CallAudioRoute>, video: Boolean): CallAudioRoute = when {
    CallAudioRoute.Bluetooth in available -> CallAudioRoute.Bluetooth
    CallAudioRoute.Headset in available -> CallAudioRoute.Headset
    video || CallAudioRoute.Earpiece !in available -> CallAudioRoute.Speaker
    else -> CallAudioRoute.Earpiece
}

/** Next route for the route button, in a fixed order, skipping what is not connected. */
internal fun nextRoute(current: CallAudioRoute, available: Set<CallAudioRoute>): CallAudioRoute {
    val order = CallAudioRoute.entries.filter { it in available }
    if (order.isEmpty()) return current
    val index = order.indexOf(current)
    return order[(index + 1) % order.size]
}

/**
 * Routes call audio between earpiece, speaker, wired headset and Bluetooth. On Android 12+ it uses
 * communication devices (setCommunicationDevice); on Android 10–11 speakerphone and Bluetooth SCO.
 * Follows headsets plugged in or connected during the call. All methods run on the main thread.
 */
class CallAudioRouter(context: Context) {
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var previousMode = AudioManager.MODE_NORMAL
    private var running = false
    private var video = false
    /** Chosen by the user; until then headsets are followed automatically. */
    private var userChoice: CallAudioRoute? = null

    private val _route = MutableStateFlow(CallAudioRoute.Earpiece)
    val route: StateFlow<CallAudioRoute> = _route.asStateFlow()
    private val _available = MutableStateFlow(setOf(CallAudioRoute.Earpiece, CallAudioRoute.Speaker))
    val available: StateFlow<Set<CallAudioRoute>> = _available.asStateFlow()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = devicesChanged(headsetAdded = added.any { routeOf(it.type).isHeadset() })
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = devicesChanged(headsetAdded = false)
    }

    fun start(isVideo: Boolean) {
        if (running || audio == null) return
        running = true
        video = isVideo
        userChoice = null
        previousMode = audio.mode
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        audio.registerAudioDeviceCallback(deviceCallback, handler)
        refreshAvailable()
        apply(preferredRoute(_available.value, video))
    }

    fun select(route: CallAudioRoute) {
        if (!running || route !in _available.value) return
        userChoice = route
        apply(route)
    }

    fun toggle() = select(nextRoute(_route.value, _available.value))

    fun stop() {
        if (!running || audio == null) return
        running = false
        audio.unregisterAudioDeviceCallback(deviceCallback)
        if (Build.VERSION.SDK_INT >= 31) {
            audio.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audio.isSpeakerphoneOn = false
            @Suppress("DEPRECATION")
            if (audio.isBluetoothScoOn) { audio.stopBluetoothSco(); audio.isBluetoothScoOn = false }
        }
        audio.mode = previousMode
    }

    private fun devicesChanged(headsetAdded: Boolean) {
        if (!running) return
        refreshAvailable()
        val current = _route.value
        val target = when {
            // A headset connected mid-call takes the audio, as on a phone call.
            headsetAdded -> preferredRoute(_available.value, video).also { userChoice = null }
            current !in _available.value -> preferredRoute(_available.value, video).also { userChoice = null }
            else -> userChoice ?: current
        }
        apply(target)
    }

    private fun refreshAvailable() {
        val audio = audio ?: return
        val devices: List<AudioDeviceInfo> =
            if (Build.VERSION.SDK_INT >= 31) audio.availableCommunicationDevices
            else audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        val routes = devices.mapNotNull { routeOf(it.type) }.toMutableSet()
        routes += CallAudioRoute.Speaker
        if (Build.VERSION.SDK_INT < 31) routes += CallAudioRoute.Earpiece
        // A wired headset replaces the earpiece on the same route; showing both would be confusing.
        if (CallAudioRoute.Headset in routes) routes -= CallAudioRoute.Earpiece
        _available.value = routes
    }

    private fun apply(route: CallAudioRoute) {
        val audio = audio ?: return
        if (Build.VERSION.SDK_INT >= 31) {
            val device = audio.availableCommunicationDevices.firstOrNull { routeOf(it.type) == route }
            if (device != null && audio.setCommunicationDevice(device)) _route.value = route
            return
        }
        @Suppress("DEPRECATION")
        run {
            if (route == CallAudioRoute.Bluetooth) {
                audio.isSpeakerphoneOn = false
                audio.startBluetoothSco()
                audio.isBluetoothScoOn = true
            } else {
                if (audio.isBluetoothScoOn) { audio.stopBluetoothSco(); audio.isBluetoothScoOn = false }
                audio.isSpeakerphoneOn = route == CallAudioRoute.Speaker
            }
        }
        _route.value = route
    }

    private fun CallAudioRoute?.isHeadset() = this == CallAudioRoute.Headset || this == CallAudioRoute.Bluetooth

    private fun routeOf(type: Int): CallAudioRoute? = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> CallAudioRoute.Earpiece
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> CallAudioRoute.Speaker
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET -> CallAudioRoute.Headset
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> CallAudioRoute.Bluetooth
        else -> if (Build.VERSION.SDK_INT >= 31 && type == AudioDeviceInfo.TYPE_BLE_HEADSET) CallAudioRoute.Bluetooth else null
    }
}
