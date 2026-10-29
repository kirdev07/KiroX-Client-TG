package com.kirox.app.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionMonitorTest {
    @Test fun noInternetIsNoneWhateverTheTransport() {
        assertEquals(DeviceNetwork.None, deviceNetworkOf(connected = false, wifi = true, cellular = false, roaming = false))
        assertEquals(DeviceNetwork.None, deviceNetworkOf(connected = false, wifi = false, cellular = true, roaming = true))
    }

    @Test fun wifiWinsOverCellular() {
        assertEquals(DeviceNetwork.WiFi, deviceNetworkOf(connected = true, wifi = true, cellular = true, roaming = true))
    }

    @Test fun cellularDistinguishesRoaming() {
        assertEquals(DeviceNetwork.Mobile, deviceNetworkOf(connected = true, wifi = false, cellular = true, roaming = false))
        assertEquals(DeviceNetwork.MobileRoaming, deviceNetworkOf(connected = true, wifi = false, cellular = true, roaming = true))
    }

    @Test fun otherTransportsAreOther() {
        assertEquals(DeviceNetwork.Other, deviceNetworkOf(connected = true, wifi = false, cellular = false, roaming = false))
    }

    @Test fun everyKindMapsToItsTdlibType() {
        assertTrue(DeviceNetwork.None.toTd() is TdApi.NetworkTypeNone)
        assertTrue(DeviceNetwork.WiFi.toTd() is TdApi.NetworkTypeWiFi)
        assertTrue(DeviceNetwork.Mobile.toTd() is TdApi.NetworkTypeMobile)
        assertTrue(DeviceNetwork.MobileRoaming.toTd() is TdApi.NetworkTypeMobileRoaming)
        assertTrue(DeviceNetwork.Other.toTd() is TdApi.NetworkTypeOther)
    }
}
