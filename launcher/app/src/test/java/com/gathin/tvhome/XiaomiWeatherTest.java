package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class XiaomiWeatherTest {
    @Test public void chineseWeatherCodesAreNotInterpretedAsWmoCodes() {
        assertEquals(3, XiaomiWeather.scene(2)); // 阴, not WMO's partly cloudy.
        assertEquals(95, XiaomiWeather.scene(4));
        assertEquals(61, XiaomiWeather.scene(7));
        assertEquals(65, XiaomiWeather.scene(10));
        assertEquals(71, XiaomiWeather.scene(14));
        assertEquals(67, XiaomiWeather.scene(19));
        assertEquals(45, XiaomiWeather.scene(53));
        assertEquals(-1, XiaomiWeather.scene(999));
    }
    @Test public void windCompassWrapsAtNorth() {
        assertEquals("北风", XiaomiWeather.windDirection(359));
        assertEquals("北风", XiaomiWeather.windDirection(-1));
        assertEquals("东北风", XiaomiWeather.windDirection(45));
        assertEquals("西北风", XiaomiWeather.windDirection(337));
    }
    @Test public void dailyForecastShowsChangesAcrossDayAndNight() {
        assertEquals("晴转多云", XiaomiWeather.forecastDescription(0, 1));
        assertEquals("多云", XiaomiWeather.forecastDescription(1, 1));
        assertEquals("小雨转阴", XiaomiWeather.forecastDescription(7, 2));
    }
    @Test public void airQualityThresholdsMatchChineseAqi() {
        assertEquals("优", XiaomiWeather.airQuality(50));
        assertEquals("良", XiaomiWeather.airQuality(51));
        assertEquals("轻度污染", XiaomiWeather.airQuality(101));
        assertEquals("严重污染", XiaomiWeather.airQuality(301));
    }
}
