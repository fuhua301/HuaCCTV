package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

/** Fixed Ganzhou weather, with a small animated scene. No GPS or IP location requests. */
final class WeatherHeader {
    private static final long REFRESH_MS = 10 * 60 * 1000L;
    private static final long RETRY_MS = 5 * 60 * 1000L;
    private final TextView view;
    private final WeatherIconView icon;
    private final WeatherPanelView panel;
    private final Context context;
    private final SharedPreferences cache;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean active, closed, loading;
    private final Runnable refresh = this::refresh;

    WeatherHeader(WeatherPanelView panel) {
        this.panel = panel;
        this.view = panel.current;
        this.icon = panel.icon;
        TextView view = this.view;
        context = view.getContext().getApplicationContext();
        cache = context.getSharedPreferences("weather_header_xiaomi_nankang", Context.MODE_PRIVATE);
        render(false);
    }

    void start() {
        active = true;
        panel.start();
        refresh();
    }

    void stop() {
        active = false;
        panel.stop();
        ui.removeCallbacks(refresh);
    }

    void close() {
        stop();
        closed = true;
        worker.shutdownNow();
        ui.removeCallbacksAndMessages(null);
    }

    private void refresh() {
        if (!active || closed || loading) return;
        ui.removeCallbacks(refresh);
        long age = System.currentTimeMillis() - cache.getLong("updated", 0);
        if (age >= 0 && age < REFRESH_MS && cache.contains("body")) {
            render(false);
            ui.postDelayed(refresh, REFRESH_MS - age);
            return;
        }
        loading = true;
        worker.execute(() -> {
            boolean success = false;
            try {
                JSONObject body = get(XiaomiWeather.ENDPOINT);
                XiaomiWeather.Reading reading = XiaomiWeather.parse(body, System.currentTimeMillis());
                cache.edit().putString("body", body.toString()).putString("title", reading.title)
                        .putString("details", reading.details)
                        .putString("forecast", reading.forecast)
                        .putLong("observed", reading.observed)
                        .putInt("code", reading.scene)
                        .putBoolean("is_day", reading.day)
                        .putLong("updated", System.currentTimeMillis()).apply();
                success = true;
                Log.i("TvHome.Weather", "Current weather refreshed");
            } catch (Exception unavailable) {
                Log.w("TvHome.Weather", "Weather refresh unavailable: " + unavailable.getClass().getSimpleName());
            }
            final boolean ok = success;
            ui.post(() -> {
                loading = false;
                if (closed || !active) return;
                render(!ok);
                ui.postDelayed(refresh, ok ? REFRESH_MS : RETRY_MS);
            });
        });
    }

    private void render(boolean failed) {
        long updated = cache.getLong("updated", 0);
        if (updated == 0 || !cache.contains("body")) {
            icon.setWeather(-1, true);
            view.setText(failed ? R.string.weather_unavailable : R.string.weather_loading);
            return;
        }
        try {
            XiaomiWeather.Reading reading = XiaomiWeather.parse(new JSONObject(cache.getString("body", "{}")), System.currentTimeMillis());
            long age = System.currentTimeMillis() - updated;
            panel.render(reading, failed || age < 0 || age >= REFRESH_MS
                    || System.currentTimeMillis() - reading.observed > 2 * 60 * 60 * 1000L);
        } catch (Exception invalidCache) {
            view.setText(R.string.weather_unavailable);
            icon.setWeather(-1, true);
        }
    }

    static int conditionIndex(int code) {
        if (code == 0) return 0;
        if (code == 1 || code == 2) return 1;
        if (code == 3) return 2;
        if (code == 45 || code == 48) return 3;
        if (code >= 51 && code <= 57) return 4;
        if (code == 61 || code == 80) return 5;
        if (code == 63 || code == 81) return 6;
        if (code == 65 || code == 82) return 7;
        if (code == 66 || code == 67) return 8;
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return 9;
        if (code == 95 || code == 96 || code == 99) return 10;
        return 11;
    }

    private static JSONObject get(String address) throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "CleanYourTV/3.4");
        try {
            if (connection.getResponseCode() != 200) throw new IllegalStateException("HTTP response");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int n;
                while ((n = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    if (out.size() + n > 262144) throw new IllegalStateException("Response too large");
                    out.write(buffer, 0, n);
                }
                return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }
}
