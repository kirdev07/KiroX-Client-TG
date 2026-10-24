package com.kirox.app.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SceneForDateTest {
    @Test fun halloweenBoundaries() {
        assertEquals(EffectScene.Main, sceneForDate(10, 24))
        assertEquals(EffectScene.Halloween, sceneForDate(10, 25))
        assertEquals(EffectScene.Halloween, sceneForDate(10, 31))
        assertEquals(EffectScene.Main, sceneForDate(11, 1))
    }

    @Test fun newYearBoundaries() {
        assertEquals(EffectScene.Winter, sceneForDate(12, 19))
        assertEquals(EffectScene.NewYear, sceneForDate(12, 20))
        assertEquals(EffectScene.NewYear, sceneForDate(12, 31))
        assertEquals(EffectScene.NewYear, sceneForDate(1, 1))
        assertEquals(EffectScene.NewYear, sceneForDate(1, 7))
        assertEquals(EffectScene.Winter, sceneForDate(1, 8))
    }

    @Test fun winterBoundaries() {
        assertEquals(EffectScene.Main, sceneForDate(11, 30))
        assertEquals(EffectScene.Winter, sceneForDate(12, 1))
        assertEquals(EffectScene.Winter, sceneForDate(2, 29))
        assertEquals(EffectScene.Main, sceneForDate(3, 1))
    }

    @Test fun ordinaryDays() {
        assertEquals(EffectScene.Main, sceneForDate(6, 15))
        assertEquals(EffectScene.Main, sceneForDate(9, 1))
    }

    @Test fun autoResolvesAndOthersStay() {
        val oct25 = java.util.Calendar.getInstance().apply { set(2026, java.util.Calendar.OCTOBER, 25) }
        assertEquals(EffectScene.Halloween, EffectScene.Auto.resolved(oct25))
        assertEquals(EffectScene.Winter, EffectScene.Winter.resolved(oct25))
    }
}
