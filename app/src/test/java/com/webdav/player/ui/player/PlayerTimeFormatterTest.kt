package com.webdav.player.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerTimeFormatterTest {

    @Test
    fun formatMs_zeroAndNegative_returnsZeroTime() {
        assertEquals("00:00", PlayerTimeFormatter.formatMs(0L))
        assertEquals("00:00", PlayerTimeFormatter.formatMs(-500L))
    }

    @Test
    fun formatMs_secondsAndMinutes() {
        assertEquals("00:05", PlayerTimeFormatter.formatMs(5_000L))
        assertEquals("01:23", PlayerTimeFormatter.formatMs(83_000L))
        assertEquals("04:05", PlayerTimeFormatter.formatMs(245_000L))
        assertEquals("59:59", PlayerTimeFormatter.formatMs(3_599_000L))
    }

    @Test
    fun formatMs_overOneHour() {
        assertEquals("1:00:00", PlayerTimeFormatter.formatMs(3_600_000L))
        assertEquals("1:05:20", PlayerTimeFormatter.formatMs(3_920_000L))
        assertEquals("10:00:01", PlayerTimeFormatter.formatMs(36_001_000L))
    }
}
