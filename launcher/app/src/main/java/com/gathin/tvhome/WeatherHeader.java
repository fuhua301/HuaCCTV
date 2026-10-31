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

/** Selected or IP-located city weather, cached with the location identity. */
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
        cache = context.getSharedPreferences("weather_header_dynamic", Context.MODE_PRIVATE);
        WeatherLocations.mode(context);
        render(false);
    }

    void start() {
        boolean enabled = WeatherLocations.enabled(context);
        panel.setVisibility(enabled ? android.view.View.VISIBLE : android.view.View.GONE);
        if (!enabled) { stop(); return; }
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
        if (!active || closed || loading || !WeatherLocations.enabled(context)) return;
        ui.removeCallbacks(refresh);
        long age = System.currentTimeMillis() - cache.getLong("updated", 0);
        if (age >= 0 && age < REFRESH_MS && cache.contains("body") && cacheMatches()) {
            render(false);
            ui.postDelayed(refresh, REFRESH_MS - age);
            return;
        }
        loading = true;
        worker.execute(() -> {
            boolean success = false;
            try {
                WeatherCity city = WeatherLocations.resolve(context);
                String mode = WeatherLocations.mode(context);
                JSONObject body = (JSONObject) WeatherLocations.get(city.endpoint());
                XiaomiWeather.Reading reading = XiaomiWeather.parse(body, System.currentTimeMillis(), city.name);
                cache.edit().putString("city_key", city.cacheKey()).putString("city_name", city.name).putString("mode", mode)
                        .putString("body", body.toString()).putString("title", reading.title)
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
        if (updated == 0 || !cache.contains("body") || !cacheMatches()) {
            icon.setWeather(-1, true);
            view.setText(failed ? R.string.weather_unavailable : R.string.weather_loading);
            return;
        }
        try {
            XiaomiWeather.Reading reading = XiaomiWeather.parse(new JSONObject(cache.getString("body", "{}")), System.currentTimeMillis(), cache.getString("city_name", "天气"));
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

    private boolean cacheMatches() {
        WeatherCity city = WeatherLocations.cached(context);
        return city != null && WeatherLocations.mode(context).equals(cache.getString("mode", ""))
                && city.cacheKey().equals(cache.getString("city_key", ""));
    }
}
