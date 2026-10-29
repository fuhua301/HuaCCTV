package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class StatusOverlayTest {
    @Test public void speedUsesActualElapsedTimeAndBytesNotBits() {
        assertEquals("512 KB/s", StatusOverlayService.rate(1024, 1024 + 1024 * 1024, 2000));
        assertEquals("2.0 MB/s", StatusOverlayService.rate(0, 4 * 1024 * 1024, 2000));
        assertEquals("0 KB/s", StatusOverlayService.rate(100, 100, 1000));
    }
    @Test public void resetsAndUnsupportedCountersAreNotNegativeSpeeds() {
        assertEquals("—", StatusOverlayService.rate(-1, 1024, 1000));
        assertEquals("—", StatusOverlayService.rate(1000, 10, 1000));
        assertEquals("—", StatusOverlayService.rate(10, 1000, 0));
    }
    @Test public void physicalInputsHideTheOverlayButStreamingAppsDoNot() {
        assertTrue(StatusOverlayService.isInputSource("com.xiaomi.mitv.tvplayer", "com.xiaomi.mitv.tvplayer.ExternalSourceActivity"));
        assertTrue(StatusOverlayService.isInputSource("com.xiaomi.mitv.tvplayer", "com.xiaomi.mitv.tvplayer.AtvActivity"));
        assertFalse(StatusOverlayService.isInputSource("com.xiaomi.tweather", "com.xiaomi.tweather.TWeatherActivity"));
        assertFalse(StatusOverlayService.isInputSource("com.google.android.youtube.tv", "MainActivity"));
    }
}
