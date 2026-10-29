package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.OffsetDateTime;

/** Response contract used by the installed Xiaomi TWeather 2.5.18 client. */
final class XiaomiWeather {
    static final String ENDPOINT = "https://weatherapi.market.xiaomi.com/wtr-v3/weather/all?source=tweather"
            + "&latitude=25.832943528026&longitude=114.71256136027&locationKey=weathercn%3A101240704"
            + "&locale=zh_CN&isGlobal=false&alpha=true&appKey=tvweatherapp202211"
            + "&sign=t1nsfdw0pwbijgjke2zk&romVersion=1&appVersion=1&device=mitv&modDevice=mitv_tweather";

    static final class Reading {
        final String title, details, forecast;
        final Day[] days;
        final long observed;
        final int scene;
        final boolean day;
        Reading(String title, String details, String forecast, long observed, int scene, boolean day, Day[] days) {
            this.title = title;
            this.details = details;
            this.forecast = forecast;
            this.observed = observed;
            this.scene = scene;
            this.day = day;
            this.days = days;
        }
    }

    static final class Day {
        final String condition, high, low;
        final int scene;
        Day(String condition, String high, String low, int scene) {
            this.condition = condition;
            this.high = high;
            this.low = low;
            this.scene = scene;
        }
    }

    static Reading parse(JSONObject body, long now) throws Exception {
        JSONObject current = body.getJSONObject("current");
        double temperature = current.getJSONObject("temperature").getDouble("value");
        if (!Double.isFinite(temperature) || temperature < -60 || temperature > 65)
            throw new IllegalArgumentException("temperature");
        int code = current.getInt("weather");
        long observed = OffsetDateTime.parse(current.getString("pubTime")).toInstant().toEpochMilli();
        if (observed > now + 10 * 60 * 1000L || now - observed > 24 * 60 * 60 * 1000L)
            throw new IllegalArgumentException("outdated observation");
        JSONObject daily = body.optJSONObject("forecastDaily");
        JSONObject range = item(daily, "temperature", 0);
        String title = "赣州·南康  " + description(code) + "  " + Math.round(temperature) + "°C";
        String details = range == null ? "" : range.optString("to") + "–" + range.optString("from") + "°C";
        details = append(details, value(current, "feelsLike", "体感 ", "°C"));
        details = append(details, value(current, "humidity", "湿度 ", "%"));
        JSONObject wind = current.optJSONObject("wind");
        String windText = "";
        if (wind != null) {
            JSONObject direction = wind.optJSONObject("direction");
            if (direction != null) {
                double degrees = direction.optDouble("value", Double.NaN);
                if (Double.isFinite(degrees)) windText = windDirection(degrees);
            }
            windText = append(windText, value(wind, "speed", "", " km/h"));
        }
        JSONObject air = body.optJSONObject("aqi");
        String airText = "";
        if (air != null && air.optInt("status", -1) == 0) {
            int aqi = air.optInt("aqi", -1);
            if (aqi >= 0) airText = "空气" + airQuality(aqi) + " " + aqi;
        }
        String forecast = append(windText, airText);
        JSONObject tomorrow = item(daily, "temperature", 1);
        JSONObject tomorrowCode = item(daily, "weather", 1);
        if (tomorrow != null && tomorrowCode != null) {
            forecast = append(forecast, "明日" + description(tomorrowCode.optInt("from", -1))
                    + " " + tomorrow.optString("to") + "–" + tomorrow.optString("from") + "°C");
        }
        boolean day = false;
        JSONObject sun = item(daily, "sunRiseSet", 0);
        if (sun != null) {
            long rise = OffsetDateTime.parse(sun.getString("from")).toInstant().toEpochMilli();
            long set = OffsetDateTime.parse(sun.getString("to")).toInstant().toEpochMilli();
            day = now >= rise && now < set;
        }
        Day[] days = new Day[3];
        for (int i = 0; i < days.length; i++) {
            JSONObject temperatures = item(daily, "temperature", i);
            JSONObject conditions = item(daily, "weather", i);
            if (temperatures == null || conditions == null) {
                days[i] = new Day("暂无预报", "—", "—", -1);
                continue;
            }
            int from = conditions.optInt("from", -1), to = conditions.optInt("to", -1);
            days[i] = new Day(forecastDescription(from, to), temperatures.optString("from", "—"),
                    temperatures.optString("to", "—"), scene(from));
        }
        return new Reading(title, details, forecast, observed, scene(code), day, days);
    }

    static String forecastDescription(int from, int to) {
        return from == to ? description(from) : description(from) + "转" + description(to);
    }

    private static JSONObject item(JSONObject daily, String field, int index) {
        if (daily == null) return null;
        JSONObject group = daily.optJSONObject(field);
        if (group == null || group.optInt("status", -1) != 0) return null;
        JSONArray values = group.optJSONArray("value");
        return values == null ? null : values.optJSONObject(index);
    }

    private static String value(JSONObject object, String key, String before, String after) {
        JSONObject field = object.optJSONObject(key);
        if (field == null) return "";
        String value = field.optString("value", "").trim();
        return value.isEmpty() || value.equals("null") ? "" : before + value + after;
    }

    private static String append(String a, String b) {
        return a.isEmpty() ? b : b.isEmpty() ? a : a + " · " + b;
    }

    static String windDirection(double degrees) {
        String[] names = {"北风", "东北风", "东风", "东南风", "南风", "西南风", "西风", "西北风"};
        int sector = (int) Math.floor(((degrees % 360 + 360) % 360 + 22.5) / 45) % 8;
        return names[sector];
    }

    static String airQuality(int aqi) {
        if (aqi <= 50) return "优";
        if (aqi <= 100) return "良";
        if (aqi <= 150) return "轻度污染";
        if (aqi <= 200) return "中度污染";
        if (aqi <= 300) return "重度污染";
        return "严重污染";
    }

    static String description(int code) {
        String[] names = {"晴", "多云", "阴", "阵雨", "雷阵雨", "雷雨伴冰雹", "雨夹雪", "小雨", "中雨",
                "大雨", "暴雨", "大暴雨", "特大暴雨", "阵雪", "小雪", "中雪", "大雪", "暴雪", "雾", "冻雨",
                "沙尘暴", "小到中雨", "中到大雨", "大到暴雨", "暴雨到大暴雨", "大暴雨到特大暴雨",
                "小到中雪", "中到大雪", "大到暴雪", "浮尘", "扬沙", "强沙尘暴"};
        if (code >= 0 && code < names.length) return names[code];
        if (code == 53) return "霾";
        return "天气";
    }

    /** Convert Chinese weather codes to the WMO codes understood by the animated scenes. */
    static int scene(int code) {
        switch (code) {
            case 0: return 0;
            case 1: return 2;
            case 2: return 3;
            case 3: return 80;
            case 4: return 95;
            case 5: return 99;
            case 6: return 77;
            case 7: case 21: return 61;
            case 8: case 22: return 63;
            case 9: case 10: case 11: case 12: case 23: case 24: case 25: return 65;
            case 13: case 14: case 26: return 71;
            case 15: case 27: return 73;
            case 16: case 17: case 28: return 75;
            case 18: case 20: case 29: case 30: case 31: case 53: return 45;
            case 19: return 67;
            default: return -1;
        }
    }
}
