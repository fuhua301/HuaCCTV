package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;

/** Prefer the TV system location; use IP estimation only when no device position is available. */
final class WeatherLocations {
    static final String AUTO = "auto", MANUAL = "manual";
    private static final String PREFS = "weather_locations";
    static final WeatherCity NANKANG = new WeatherCity("赣州·南康", "weathercn:101240704", 25.832943528026, 114.71256136027);
    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    static boolean enabled(Context context) { return prefs(context).getBoolean("enabled", true); }
    static void setEnabled(Context context, boolean enabled) { prefs(context).edit().putBoolean("enabled", enabled).commit(); }
    static synchronized String mode(Context context) {
        SharedPreferences prefs = prefs(context);
        if (!prefs.contains("mode")) {
            // Existing installations keep the user's formerly fixed location; clean installs locate automatically.
            boolean legacy = context.getSharedPreferences("weather_header_xiaomi_nankang", Context.MODE_PRIVATE).contains("body");
            SharedPreferences.Editor edit = prefs.edit().putString("mode", legacy ? MANUAL : AUTO);
            if (legacy) edit.putString("manual", json(NANKANG).toString()); edit.commit();
        }
        if (prefs.getInt("resolver_version", 0) < 2) {
            SharedPreferences.Editor edit = prefs.edit().putInt("resolver_version", 2);
            if (AUTO.equals(prefs.getString("mode", AUTO))) edit.remove("automatic").remove("auto_updated")
                    .remove("auto_source").remove("auto_device");
            edit.commit();
        }
        return prefs.getString("mode", AUTO);
    }
    static void automatic(Context context) { prefs(context).edit().putString("mode", AUTO).remove("automatic")
            .remove("auto_updated").remove("auto_source").remove("auto_device").commit(); }
    static void manual(Context context, WeatherCity city) { prefs(context).edit().putString("mode", MANUAL).putString("manual", json(city).toString()).commit(); }
    static WeatherCity cached(Context context) {
        boolean manual = MANUAL.equals(mode(context));
        try { return from(new JSONObject(prefs(context).getString(manual ? "manual" : "automatic", "{}"))); }
        catch (Exception absent) { return manual ? NANKANG : null; }
    }
    static WeatherCity lastManual(Context context) {
        try { return from(new JSONObject(prefs(context).getString("manual", "{}"))); } catch(Exception absent) { return null; }
    }
    static String source(Context context) {
        return MANUAL.equals(mode(context)) ? "手动选择" : prefs(context).getString("auto_source", "定位中");
    }
    private static WeatherCity readSystemCity(Context context) {
        String[] keys = {"mitv_user_location", "mitv_location"};
        WeatherCity fallback = null;
        for (String key : keys) {
            try {
                String raw = android.provider.Settings.System.getString(context.getContentResolver(), key);
                if (raw == null || raw.length() > 8192) continue;
                JSONObject value = new JSONObject(raw);
                double lat = value.getDouble("latitude"), lon = value.getDouble("longitude");
                if (!WeatherAutoPolicy.validDeviceCoordinates(lat, lon)) continue;
                String city = value.optString("city", "").trim(), district = value.optString("district", "").trim();
                String label = city.isEmpty() ? district : district.isEmpty() || city.equals(district) ? city : city + "·" + district;
                if (label.isEmpty()) label = "电视所在城市";
                WeatherCity result = new WeatherCity(label, "", lat, lon);
                if (key.equals("mitv_user_location")) {
                    if (value.optBoolean("isUserSet", false)) return result;
                    fallback = result;
                } else return result;
            } catch (Exception unavailable) { }
        }
        return fallback;
    }
    static WeatherCity resolve(Context context) throws Exception {
        if (MANUAL.equals(mode(context))) return cached(context);
        SharedPreferences prefs = prefs(context); WeatherCity previous = cached(context);
        WeatherCity device = readSystemCity(context);
        String desiredSource = device == null ? "公网 IP 估算" : "电视系统定位";
        String deviceKey = device == null ? "" : device.latitude + "," + device.longitude + "," + device.name;
        long age = System.currentTimeMillis() - prefs.getLong("auto_updated", 0);
        if (WeatherAutoPolicy.useCache(previous != null, prefs.getString("auto_source", ""), desiredSource,
                prefs.getString("auto_device", ""), deviceKey, age)) return previous;
        if (device != null) {
            WeatherCity city = geocode(device.latitude, device.longitude, device.name, true);
            remember(prefs, city, desiredSource, deviceKey); return city;
        }
        try {
            JSONObject ip;
            try { ip = (JSONObject) get("https://ipwho.is/"); if (!ip.optBoolean("success", true)) throw new IOException("IP 定位暂不可用"); }
            catch (Exception unavailable) { ip = (JSONObject) get("https://ipapi.co/json/"); }
            WeatherCity city = geocode(ip.getDouble("latitude"), ip.getDouble("longitude"), ip.optString("city", "所在城市"), false);
            remember(prefs, city, desiredSource, deviceKey); return city;
        } catch (Exception unavailable) { if (previous != null) return previous; throw unavailable; }
    }
    private static WeatherCity geocode(double latitude, double longitude, String fallbackName, boolean preserveName) {
        try {
            JSONArray result = (JSONArray) get("https://weatherapi.market.xiaomi.com/wtr-v3/location/city/geo?latitude="
                    + latitude + "&longitude=" + longitude + "&locale=zh_CN");
            if (result.length() > 0) {
                WeatherCity city = fromSearch(result.getJSONObject(0));
                return new WeatherCity(preserveName ? fallbackName : city.name, city.key, latitude, longitude);
            }
        } catch (Exception unavailable) { }
        return new WeatherCity(fallbackName, "", latitude, longitude);
    }
    private static void remember(SharedPreferences prefs, WeatherCity city, String source, String device) {
        prefs.edit().putString("automatic", json(city).toString()).putString("auto_source", source).putString("auto_device", device)
                .putLong("auto_updated", System.currentTimeMillis()).commit();
    }
    static List<WeatherCity> search(String query) throws Exception {
        if (query == null || query.trim().isEmpty() || query.length() > 60) throw new IOException("请输入城市或区县名称");
        JSONArray values = (JSONArray) get("https://weatherapi.market.xiaomi.com/wtr-v3/location/city/search?locale=zh_CN&name="
                + URLEncoder.encode(query.trim(), "UTF-8"));
        List<WeatherCity> cities = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (int i = 0; i < values.length() && cities.size() < 40; i++) {
            try { WeatherCity city = fromSearch(values.getJSONObject(i)); if (seen.add(city.cacheKey())) cities.add(city); }
            catch (Exception invalid) { }
        }
        return cities;
    }
    private static WeatherCity fromSearch(JSONObject value) throws Exception {
        String name = value.getString("name"), affiliation = value.optString("affiliation", "");
        String parent = affiliation.split("[,，]")[0].trim();
        if (!parent.isEmpty() && !parent.equals("中国") && !name.startsWith(parent)) name = parent + "·" + name;
        return new WeatherCity(name, value.optString("locationKey", value.optString("key", "")), value.getDouble("latitude"), value.getDouble("longitude"));
    }
    static JSONObject json(WeatherCity city) {
        try { return new JSONObject().put("name", city.name).put("key", city.key).put("latitude", city.latitude).put("longitude", city.longitude); }
        catch (JSONException impossible) { throw new IllegalArgumentException(impossible); }
    }
    static WeatherCity from(JSONObject json) throws Exception { return new WeatherCity(json.getString("name"), json.getString("key"), json.getDouble("latitude"), json.getDouble("longitude")); }
    static Object get(String address) throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(8000); connection.setReadTimeout(10000); connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "HuaCCTV/5.7");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("网络服务暂不可用");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int n;
                while ((n = input.read(buffer)) != -1) { if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    if (output.size() + n > 524288) throw new IOException("服务返回数据过大"); output.write(buffer, 0, n); }
                return new JSONTokener(new String(output.toByteArray(), StandardCharsets.UTF_8)).nextValue();
            }
        } finally { connection.disconnect(); }
    }
}
