package com.kirox.app.voip

import com.kirox.app.voip.CallAudioRoute.Bluetooth
import com.kirox.app.voip.CallAudioRoute.Earpiece
import com.kirox.app.voip.CallAudioRoute.Headset
import com.kirox.app.voip.CallAudioRoute.Speaker
import org.junit.Assert.assertEquals
import org.junit.Test

class CallAudioRouteTest {
    @Test fun voiceCallStartsAtTheEarAndVideoOnTheSpeaker() {
        assertEquals(Earpiece, preferredRoute(setOf(Earpiece, Speaker), video = false))
        assertEquals(Speaker, preferredRoute(setOf(Earpiece, Speaker), video = true))
    }

    @Test fun headsetsWinBluetoothFirst() {
        assertEquals(Headset, preferredRoute(setOf(Speaker, Headset), video = true))
        assertEquals(Bluetooth, preferredRoute(setOf(Earpiece, Speaker, Headset, Bluetooth), video = false))
    }

    @Test fun withoutEarpieceVoiceGoesToSpeaker() {
        assertEquals(Speaker, preferredRoute(setOf(Speaker), video = false))
    }

    @Test fun buttonCyclesOnlyConnectedRoutes() {
        val routes = setOf(Earpiece, Speaker, Bluetooth)
        assertEquals(Speaker, nextRoute(Earpiece, routes))
        assertEquals(Bluetooth, nextRoute(Speaker, routes))
        assertEquals(Earpiece, nextRoute(Bluetooth, routes))
    }

    @Test fun lostCurrentRouteStartsFromTheFirst() {
        assertEquals(Earpiece, nextRoute(Headset, setOf(Earpiece, Speaker)))
    }
}
