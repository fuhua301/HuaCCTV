package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;
public class WeatherAutoPolicyTest {
    @Test public void ipCacheCannotOverrideSystemCoordinates() { assertFalse(WeatherAutoPolicy.useCache(true,"公网 IP 估算","电视系统定位","","nankang",100)); }
    @Test public void systemLocationChangesInvalidateCache() { assertFalse(WeatherAutoPolicy.useCache(true,"电视系统定位","电视系统定位","old","new",100)); }
    @Test public void matchingFreshSystemPositionCanBeReused() { assertTrue(WeatherAutoPolicy.useCache(true,"电视系统定位","电视系统定位","same","same",100)); }
    @Test public void placeholdersAndExpiredCacheAreRejected() {
        assertFalse(WeatherAutoPolicy.validDeviceCoordinates(0,0));assertFalse(WeatherAutoPolicy.validDeviceCoordinates(Double.NaN,30));
        assertTrue(WeatherAutoPolicy.validDeviceCoordinates(25.8329,114.7125));
        assertFalse(WeatherAutoPolicy.useCache(true,"电视系统定位","电视系统定位","same","same",21600000));
    }
}
