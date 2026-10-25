package com.kirox.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test fun releaseIsNewerThanItsBetas() {
        assertTrue(AppVersion.isNewer("1.0.0", "1.0.0-beta.3"))
        assertFalse(AppVersion.isNewer("1.0.0-beta.3", "1.0.0"))
    }

    @Test fun betasOrderNumerically() {
        assertTrue(AppVersion.isNewer("1.0.0-beta.10", "1.0.0-beta.2"))
        assertTrue(AppVersion.isNewer("1.0.0-rc.1", "1.0.0-beta.9"))
        assertFalse(AppVersion.isNewer("1.0.0-beta.1", "1.0.0-beta.1"))
    }

    @Test fun coreVersionsAndTags() {
        assertTrue(AppVersion.isNewer("v1.0.1", "1.0.0"))
        assertTrue(AppVersion.isNewer("1.1.0-beta.1", "1.0.9"))
        assertFalse(AppVersion.isNewer("0.9.0", "1.0.0-beta.1"))
    }
}
