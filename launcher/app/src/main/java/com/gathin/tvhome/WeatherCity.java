package com.gathin.tvhome;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** A validated weather location, independent of Android so URL construction can be tested. */
final class WeatherCity {
    final String name, key;
    final double latitude, longitude;
    WeatherCity(String name, String key, double latitude, double longitude) {
        if (name == null || name.trim().isEmpty() || name.length() > 120) throw new IllegalArgumentException("城市名称无效");
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180)
            throw new IllegalArgumentException("城市坐标无效");
        if (key == null || !key.matches("[A-Za-z0-9:_-]{0,128}")) throw new IllegalArgumentException("城市编号无效");
        this.name = name.trim(); this.key = key; this.latitude = latitude; this.longitude = longitude;
    }
    String cacheKey() { return key.isEmpty() ? latitude + "," + longitude : key; }
    String endpoint() {
        return "https://weatherapi.market.xiaomi.com/wtr-v3/weather/all?source=tweather"
                + "&latitude=" + latitude + "&longitude=" + longitude
                + (key.isEmpty() ? "" : "&locationKey=" + key.replace(":", "%3A"))
                + "&locale=zh_CN&isGlobal=" + (!key.isEmpty() && !key.startsWith("weathercn:"))
                + "&alpha=true&appKey=tvweatherapp202211&sign=t1nsfdw0pwbijgjke2zk"
                + "&romVersion=1&appVersion=1&device=mitv&modDevice=mitv_tweather";
    }
}
