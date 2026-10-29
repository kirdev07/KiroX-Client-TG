package com.kirox.app.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoDownloadRuleTest {
    private fun settings(enabled: Boolean, video: Long, other: Long) = TdApi.AutoDownloadSettings(enabled, 0, video, other, 0, false, false, false, false)
    private val presets = TdApi.AutoDownloadSettingsPresets(settings(false, 0, 0), settings(true, 10, 1), settings(true, 100, 50))

    @Test fun networkPicksThePreset() {
        assertEquals(AutoDownloadRule(100, 50), autoDownloadRuleFor(DeviceNetwork.WiFi, presets))
        assertEquals(AutoDownloadRule(10, 1), autoDownloadRuleFor(DeviceNetwork.Mobile, presets))
        assertEquals(AutoDownloadRule(), autoDownloadRuleFor(DeviceNetwork.MobileRoaming, presets))
    }

    @Test fun nothingDownloadsOfflineOrBeforePresetsLoad() {
        assertEquals(AutoDownloadRule(), autoDownloadRuleFor(DeviceNetwork.None, presets))
        assertEquals(AutoDownloadRule(), autoDownloadRuleFor(DeviceNetwork.WiFi, null))
    }
}
