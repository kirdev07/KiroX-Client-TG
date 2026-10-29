package com.kirox.app.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoSourceGroupsTest {
    @Test fun groupsAreEncodedForTheEngine() {
        val groups = arrayOf(
            TdApi.GroupCallVideoSourceGroup("SIM", intArrayOf(11, 12, 13)),
            TdApi.GroupCallVideoSourceGroup("FID", intArrayOf(11, 21)),
            TdApi.GroupCallVideoSourceGroup("FID", intArrayOf())
        )
        assertEquals("SIM:11,12,13;FID:11,21", encodeSourceGroups(groups))
    }

    @Test fun noGroupsGiveAnEmptyString() {
        assertEquals("", encodeSourceGroups(null))
    }
}
