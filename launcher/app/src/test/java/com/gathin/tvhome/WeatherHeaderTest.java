package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class WeatherHeaderTest {
    @Test public void rainSeverityAndFreezingRainHaveSeparateScenes() {
        for (int code : new int[]{51, 53, 55, 56, 57}) assertEquals(4, WeatherHeader.conditionIndex(code));
        for (int code : new int[]{61, 80}) assertEquals(5, WeatherHeader.conditionIndex(code));
        for (int code : new int[]{63, 81}) assertEquals(6, WeatherHeader.conditionIndex(code));
        for (int code : new int[]{65, 82}) assertEquals(7, WeatherHeader.conditionIndex(code));
        for (int code : new int[]{66, 67}) assertEquals(8, WeatherHeader.conditionIndex(code));
    }

    @Test public void snowAndThunderstormCodesNeverBecomeClearWeather() {
        for (int code : new int[]{71, 73, 75, 77, 85, 86}) assertEquals(9, WeatherHeader.conditionIndex(code));
        for (int code : new int[]{95, 96, 99}) assertEquals(10, WeatherHeader.conditionIndex(code));
    }

    @Test public void skyAndUnknownCodesAreDistinguished() {
        assertEquals(0, WeatherHeader.conditionIndex(0));
        assertEquals(1, WeatherHeader.conditionIndex(1));
        assertEquals(1, WeatherHeader.conditionIndex(2));
        assertEquals(2, WeatherHeader.conditionIndex(3));
        assertEquals(3, WeatherHeader.conditionIndex(45));
        assertEquals(3, WeatherHeader.conditionIndex(48));
        assertEquals(11, WeatherHeader.conditionIndex(-1));
        assertEquals(11, WeatherHeader.conditionIndex(100));
    }
}
