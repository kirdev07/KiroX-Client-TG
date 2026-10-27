package com.kirox.app.telegram

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.drinkless.tdlib.TdApi

/** Largest files that download by themselves on the current network; 0 means never. */
data class AutoDownloadRule(val maxVideoBytes: Long = 0, val maxOtherBytes: Long = 0, val preloadStories: Boolean = false)

/** Picks Telegram's preset for the network: high on Wi-Fi, medium on mobile, low in roaming, none offline. */
internal fun autoDownloadRuleFor(network: DeviceNetwork, presets: TdApi.AutoDownloadSettingsPresets?): AutoDownloadRule {
    val settings = when (network) {
        DeviceNetwork.WiFi, DeviceNetwork.Other -> presets?.high
        DeviceNetwork.Mobile -> presets?.medium
        DeviceNetwork.MobileRoaming -> presets?.low
        DeviceNetwork.None -> null
    } ?: return AutoDownloadRule()
    if (!settings.isAutoDownloadEnabled) return AutoDownloadRule()
    return AutoDownloadRule(settings.maxVideoFileSize, settings.maxOtherFileSize, settings.preloadStories)
}

/**
 * Auto-download limits from the account's Telegram settings (the same presets the official apps
 * use), following the device network. Loaded once the account is ready.
 */
class AutoDownloadPolicy(private val client: TelegramClient, monitor: ConnectionMonitor) {
    private val presets = MutableStateFlow<TdApi.AutoDownloadSettingsPresets?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val rule: StateFlow<AutoDownloadRule> = combine(monitor.network, presets) { network, loaded -> autoDownloadRuleFor(network, loaded) }
        .stateIn(scope, SharingStarted.Eagerly, AutoDownloadRule())

    init {
        client.addUpdateHandler { update ->
            if (update is TdApi.UpdateAuthorizationState && update.authorizationState is TdApi.AuthorizationStateReady) {
                client.send(TdApi.GetAutoDownloadSettingsPresets()) { result -> if (result is TdApi.AutoDownloadSettingsPresets) presets.value = result }
            }
        }
    }
}
