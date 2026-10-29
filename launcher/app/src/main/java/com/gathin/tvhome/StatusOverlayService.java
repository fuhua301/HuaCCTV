package com.gathin.tvhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A non-interactive, device-wide clock and actual traffic meter. */
public final class StatusOverlayService extends Service {
    private static StatusOverlayService running;
    private static boolean sourceVisible;
    private static boolean homeVisible = true;
    private static long foregroundHintAt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService foregroundWorker = Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    private long scannedUntil, observedAt;
    private String observedPackage;
    private final Runnable foregroundPoll = () -> foregroundWorker.execute(() -> {
        long now = System.currentTimeMillis();
        try {
            UsageStatsManager usage = getSystemService(UsageStatsManager.class);
            long from = scannedUntil == 0 ? now - 24 * 60 * 60 * 1000L : Math.max(0, scannedUntil - 2000);
            UsageEvents events = usage.queryEvents(from, now);
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED && event.getTimeStamp() >= observedAt) {
                    observedPackage = event.getPackageName();
                    observedAt = event.getTimeStamp();
                }
            }
            scannedUntil = now;
        } catch (RuntimeException unavailable) {
            // Accessibility window events remain available if usage access was revoked.
        }
        String pkg = observedPackage;
        long at = observedAt;
        handler.post(() -> {
            if (closed) return;
            if (pkg != null && at >= foregroundHintAt) {
                boolean source = isInputSource(pkg, "");
                boolean home = pkg.equals(getPackageName()) || pkg.equals("com.mitv.tvhome");
                if (sourceVisible != source || homeVisible != home) {
                    sourceVisible = source;
                    homeVisible = home;
                    applyVisibility();
                }
            }
            handler.postDelayed(this.foregroundPoll, 1000);
        });
    });
    private WindowManager windows;
    private LinearLayout panel;
    private TextView clock, speed;
    private long previousRx, previousTx, previousTime;
    private boolean attached;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (panel == null || sourceVisible || homeVisible) return;
            long now = SystemClock.elapsedRealtime();
            long rx = TrafficStats.getTotalRxBytes(), tx = TrafficStats.getTotalTxBytes();
            clock.setText(new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
            speed.setText("↓ " + rate(previousRx, rx, now - previousTime)
                    + "\n↑ " + rate(previousTx, tx, now - previousTime));
            previousRx = rx;
            previousTx = tx;
            previousTime = now;
            handler.postDelayed(this, 1000);
        }
    };
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            handler.removeCallbacks(tick);
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                sample();
                handler.post(tick);
            }
        }
    };

    static void ensureStarted(Context context) {
        if (!Settings.canDrawOverlays(context)) return;
        try {
            context.startForegroundService(new Intent(context, StatusOverlayService.class));
        } catch (RuntimeException e) {
            Log.w("TvHome.Overlay", "Cannot start status overlay", e);
        }
    }

    static void setSourceVisible(boolean visible) {
        foregroundHintAt = System.currentTimeMillis();
        if (sourceVisible == visible) return;
        sourceVisible = visible;
        applyVisibility();
    }

    static void setHomeVisible(boolean visible) {
        foregroundHintAt = System.currentTimeMillis();
        if (homeVisible == visible) return;
        homeVisible = visible;
        applyVisibility();
    }

    private static void applyVisibility() {
        boolean visible = !sourceVisible && !homeVisible;
        StatusOverlayService service = running;
        if (service == null || service.panel == null) return;
        service.panel.setVisibility(visible ? View.VISIBLE : View.GONE);
        service.handler.removeCallbacks(service.tick);
        if (visible && service.getSystemService(PowerManager.class).isInteractive()) {
            service.sample();
            service.handler.post(service.tick);
        }
    }

    static boolean isInputSource(String packageName, String className) {
        return "com.xiaomi.mitv.tvplayer".equals(packageName)
                || "com.mediatek.tvinput".equals(packageName);
    }

    @Override public void onCreate() {
        super.onCreate();
        running = this;
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("tv_status", "时间与实时网速",
                NotificationManager.IMPORTANCE_LOW));
        startForeground(3401, new Notification.Builder(this, "tv_status")
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setContentTitle("时间与实时网速正在显示")
                .setContentText("右上角显示当前时间、下载和上传速度")
                .setOngoing(true).setShowWhen(false).build());
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        windows = getSystemService(WindowManager.class);
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(8), dp(5), dp(8), dp(5));
        panel.setBackground(new Frost());
        panel.setElevation(dp(5));
        clock = text(14, Color.WHITE);
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        panel.addView(clock);
        speed = text(9, 0xFFD8E6ED);
        speed.setSingleLine(false);
        speed.setMaxLines(2);
        LinearLayout.LayoutParams meter = new LinearLayout.LayoutParams(0, -2, 1);
        meter.leftMargin = dp(8);
        panel.addView(speed, meter);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(dp(140), -2,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.RIGHT;
        params.x = dp(6);
        params.y = dp(8);
        params.setTitle("CleanYourTV · 时间与实时网速");
        // Android 12+ has compositor background blur. Android 11 uses a translucent frosted texture.
        if (Build.VERSION.SDK_INT >= 31) {
            params.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            params.setBlurBehindRadius(dp(18));
        }
        try {
            windows.addView(panel, params);
            attached = true;
            panel.setVisibility(sourceVisible || homeVisible ? View.GONE : View.VISIBLE);
        } catch (RuntimeException e) {
            Log.w("TvHome.Overlay", "Cannot attach status overlay", e);
            stopSelf();
            return;
        }
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screen, filter);
        ServiceRepair.restore(this);
        handler.post(foregroundPoll);
        sample();
        if (getSystemService(PowerManager.class).isInteractive()) handler.post(tick);
    }

    private TextView text(int size, int color) {
        TextView view = new TextView(this);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setSingleLine(true);
        view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        view.setFontFeatureSettings("tnum");
        return view;
    }

    private void sample() {
        previousRx = TrafficStats.getTotalRxBytes();
        previousTx = TrafficStats.getTotalTxBytes();
        previousTime = SystemClock.elapsedRealtime();
    }

    static String rate(long before, long after, long elapsedMs) {
        if (before < 0 || after < 0 || after < before || elapsedMs <= 0) return "—";
        double bytes = (after - before) * 1000.0 / elapsedMs;
        if (bytes >= 1024 * 1024) return String.format(Locale.US, "%.1f MB/s", bytes / (1024 * 1024));
        return String.format(Locale.US, "%.0f KB/s", bytes / 1024);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        closed = true;
        foregroundWorker.shutdownNow();
        running = null;
        handler.removeCallbacksAndMessages(null);
        if (attached) {
            unregisterReceiver(screen);
            windows.removeView(panel);
        }
        panel = null;
        super.onDestroy();
    }

    private final class Frost extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas canvas) {
            float w = getBounds().width(), h = getBounds().height(), r = dp(10);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            paint.setShader(new LinearGradient(0, 0, w, h, 0x991A1D22, 0x8006080B, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(0, 0, w, h, r, r, paint);
            paint.setShader(null);
            paint.setColor(0x12FFFFFF);
            // Fine, deterministic grain lends the translucent surface a frosted finish.
            for (int y = dp(3); y < h - dp(3); y += dp(3))
                for (int x = dp(8) + (y % 5); x < w - dp(8); x += dp(5))
                    canvas.drawPoint(x, y, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(.5f));
            paint.setColor(0x38FFFFFF);
            canvas.drawRoundRect(1, 1, w - 1, h - 1, r, r, paint);
        }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
