package com.novabox.tv.data

import com.novabox.tv.player.bestRefreshRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PictureTest {
    @Test fun refreshRateMatchesTheFilm() {
        val tv = listOf(60f, 50f, 24f, 23.976f, 59.94f)
        assertEquals(24f, bestRefreshRate(24f, tv))
        assertEquals(23.976f, bestRefreshRate(23.976f, tv))
        assertEquals(50f, bestRefreshRate(25f, tv))        // 25 fps → 50 Hz
        assertEquals(60f, bestRefreshRate(30f, tv))
        assertNull(bestRefreshRate(24f, listOf(60f)))      // no fit: leave the TV alone
        assertNull(bestRefreshRate(0f, tv))
    }
}
