package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class WeatherCityTest {
    @Test public void requestsSelectedCityInsteadOfHardcodedCoordinates() {
        WeatherCity beijing = new WeatherCity("北京", "weathercn:101010100",39.904,116.408);
        String url=beijing.endpoint();assertTrue(url.contains("latitude=39.904"));assertTrue(url.contains("longitude=116.408"));
        assertTrue(url.contains("locationKey=weathercn%3A101010100"));assertTrue(url.contains("isGlobal=false"));assertFalse(url.contains("101240704"));
    }
    @Test public void coordinatesCanLocateWithoutCityCode() {
        String url=new WeatherCity("测试城市","",30,120).endpoint();assertFalse(url.contains("locationKey="));assertEquals("30.0,120.0",new WeatherCity("测试城市","",30,120).cacheKey());
    }
    @Test public void locationKeysCannotInjectQueryParameters() { try {new WeatherCity("城市","x&other=1",30,120);fail();}catch(IllegalArgumentException expected){} }
    @Test public void invalidCoordinatesAreRejected() { for(double lat:new double[]{91,-91,Double.NaN})try{new WeatherCity("城市","",lat,120);fail();}catch(IllegalArgumentException expected){} }
    @Test public void cityNameCannotBeEmpty() { try{new WeatherCity(" ","",30,120);fail();}catch(IllegalArgumentException expected){} }
}
