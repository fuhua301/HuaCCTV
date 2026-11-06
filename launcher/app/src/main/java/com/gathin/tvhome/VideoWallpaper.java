package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;
import java.io.InputStream;

/** A hardware video layer preserves decoder HDR dataspace instead of flattening it into an SDR texture. */
final class VideoWallpaper implements SurfaceHolder.Callback {
    static final String KEY = "video_wallpaper_choice";
    static final String SURFACE_TAG = "TvHome.WallpaperSurface";
    private static final Handler MEDIA = mediaHandler();
    private static Handler mediaHandler() {
        HandlerThread thread = new HandlerThread("WallpaperVideo"); thread.start();
        return new Handler(thread.getLooper());
    }
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Context context;
    private final FrameLayout root, layer;
    private final SurfaceView video;
    private final ImageView poster;
    private final View shade;
    private final int dim;
    private final SharedPreferences state;
    private final String choice;
    private volatile boolean active, closed;
    private volatile long generation;
    private boolean openRequested, surfaceReady;
    private int scene;
    // The holder owns its Surface. Only the decoder is owned/released by MEDIA.
    private UhdVideoDecoder player;

    static String selection(Context context) {
        return VideoWallpaperCatalog.normalize(context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getString(KEY, VideoWallpaperCatalog.DEFAULT));
    }
    VideoWallpaper(Context context, FrameLayout root, String choice, int dim) {
        this.context = context; this.root = root; this.choice = choice; this.dim = Math.min(90, Math.max(0, dim));
        state = context.getSharedPreferences(WallpaperPlayer.KEY_PREFS, Context.MODE_PRIVATE);
        int selected = VideoWallpaperCatalog.index(choice);
        int remembered = VideoWallpaperCatalog.index(state.getString("video_scene", VideoWallpaperCatalog.DEFAULT));
        scene = selected >= 0 ? selected : Math.max(0, remembered);
        if (selected >= 0 || !state.contains("video_changed")) remember();
        layer = new FrameLayout(context); layer.setFocusable(false);
        layer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        video = new SurfaceView(context); video.setTag(SURFACE_TAG);
        video.setZOrderOnTop(false); video.setZOrderMediaOverlay(false);
        video.getHolder().addCallback(this);
        layer.addView(video, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        poster = new ImageView(context); poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        layer.addView(poster, new FrameLayout.LayoutParams(-1, -1));
        shade = new View(context); shade.setFocusable(false);
        layer.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        layer.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fit());
        root.addView(layer, 0, new FrameLayout.LayoutParams(-1, -1));
        showPoster();
    }
    void start() { if (!closed) { active = true; open(); } }
    void stop() {
        active = false; openRequested = false; generation++; poster.setVisibility(View.VISIBLE);
        MEDIA.post(this::releasePlayer);
    }
    void close() {
        closed = true; stop(); root.removeView(layer); poster.setImageDrawable(null);
    }
    void update(long interval) {
        if (!active || closed || !VideoWallpaperCatalog.rotate(choice, state.getLong("video_changed", 0),
                System.currentTimeMillis(), interval)) return;
        scene = VideoWallpaperCatalog.next(scene); remember(); generation++; openRequested = false;
        MEDIA.post(this::releasePlayer); showPoster(); open();
    }
    private void remember() {
        state.edit().putString("video_scene", VideoWallpaperCatalog.IDS[scene])
                .putLong("video_changed", System.currentTimeMillis()).apply();
    }
    private void showPoster() {
        poster.setVisibility(View.VISIBLE);
        // Keep the requested 90% ocean brightness as a neutral display shade, never as a pixel colour grade.
        float brightness = (1f - dim / 100f) * ("ocean".equals(VideoWallpaperCatalog.IDS[scene]) ? .9f : 1f);
        shade.setBackgroundColor(Math.round((1f - brightness) * 255) << 24);
        try (InputStream in = context.getAssets().open("video-wallpapers/" + VideoWallpaperCatalog.IDS[scene] + ".jpg")) {
            poster.setImageBitmap(BitmapFactory.decodeStream(in));
        } catch (Exception unavailable) { poster.setImageDrawable(null); }
    }
    private boolean current(long request) { return active && !closed && generation == request; }
    private void open() {
        if (!active || closed || !surfaceReady || openRequested || !video.getHolder().getSurface().isValid()) return;
        openRequested = true;
        final long request = ++generation;
        final String id = VideoWallpaperCatalog.IDS[scene];
        final Surface target = video.getHolder().getSurface();
        MEDIA.post(() -> {
            if (!current(request) || !target.isValid()) return;
            releasePlayer();
            UhdVideoDecoder next = new UhdVideoDecoder(MEDIA,
                    () -> {
                        Log.i("TvHome.VideoWallpaper", "Rendering " + id + " on hardware SurfaceView");
                        ui.post(() -> { if (current(request)) poster.setVisibility(View.GONE); });
                    }, () -> ui.post(() -> {
                        if (current(request)) {
                            openRequested = false; poster.setVisibility(View.VISIBLE);
                            Toast.makeText(context, "当前设备无法播放此原始视频，暂显示预览图", Toast.LENGTH_LONG).show();
                        }
                    }));
            player = next;
            try { next.open(context, id, target); }
            catch (Exception error) {
                Log.w("TvHome.VideoWallpaper", "Decoder failed: " + id, error); releasePlayer();
                ui.post(() -> { if (current(request)) openRequested = false; });
            }
        });
    }
    private void releasePlayer() {
        if (player != null) { player.close(); player = null; Log.i("TvHome.VideoWallpaper", "Video decoder released"); }
    }
    private void fit() {
        int w = layer.getWidth(), h = layer.getHeight(); if (w <= 0 || h <= 0) return;
        int coverW = Math.max(w, (int) Math.ceil(h * 16d / 9d));
        int coverH = Math.max(h, (int) Math.ceil(w * 9d / 16d));
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) video.getLayoutParams();
        if (p.width != coverW || p.height != coverH) {
            video.setLayoutParams(new FrameLayout.LayoutParams(coverW, coverH, Gravity.CENTER));
        }
    }
    @Override public void surfaceCreated(SurfaceHolder holder) { surfaceReady = true; fit(); open(); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { fit(); open(); }
    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false; generation++; openRequested = false; MEDIA.post(this::releasePlayer);
    }
}
