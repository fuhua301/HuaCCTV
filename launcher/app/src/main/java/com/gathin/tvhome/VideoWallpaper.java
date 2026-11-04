package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;
import java.io.InputStream;

/** Native UHD decoding on a worker, independent of firmware MediaPlayer implementations. */
final class VideoWallpaper implements TextureView.SurfaceTextureListener {
    static final String KEY = "video_wallpaper_choice";
    private static final Handler MEDIA = mediaHandler();
    private static Handler mediaHandler() {
        HandlerThread thread = new HandlerThread("WallpaperVideo");
        thread.start();
        return new Handler(thread.getLooper());
    }
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Context context;
    private final FrameLayout root, layer;
    private final TextureView texture;
    private final ImageView poster;
    private final SharedPreferences state;
    private final String choice;
    private volatile boolean active, closed;
    private volatile long generation;
    private boolean openRequested;
    private int scene, videoWidth, videoHeight;
    // Worker-thread ownership: callbacks, playback position, decoder and Surface share the MEDIA looper.
    private UhdVideoDecoder player;
    private Surface surface;
    private boolean prepared;
    private int position;

    static String selection(Context context) {
        return VideoWallpaperCatalog.normalize(context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getString(KEY, VideoWallpaperCatalog.DEFAULT));
    }

    VideoWallpaper(Context context, FrameLayout root, String choice, int dim) {
        this.context = context; this.root = root; this.choice = choice;
        state = context.getSharedPreferences(WallpaperPlayer.KEY_PREFS, Context.MODE_PRIVATE);
        int selected = VideoWallpaperCatalog.index(choice);
        int remembered = VideoWallpaperCatalog.index(state.getString("video_scene", VideoWallpaperCatalog.DEFAULT));
        scene = selected >= 0 ? selected : Math.max(0, remembered);
        if (selected >= 0 || !state.contains("video_changed")) remember();
        layer = new FrameLayout(context);
        layer.setFocusable(false);
        layer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        texture = new TextureView(context);
        texture.setOpaque(true);
        texture.setSurfaceTextureListener(this);
        layer.addView(texture, new FrameLayout.LayoutParams(-1, -1));
        poster = new ImageView(context);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        layer.addView(poster, new FrameLayout.LayoutParams(-1, -1));
        if (dim > 0) {
            View shade = new View(context);
            shade.setBackgroundColor((Math.min(90, dim) * 255 / 100) << 24);
            layer.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        }
        root.addView(layer, 0, new FrameLayout.LayoutParams(-1, -1));
        showPoster();
    }

    void start() { if (!closed) { active = true; open(); } }

    void stop() {
        active = false; openRequested = false; generation++;
        poster.setVisibility(View.VISIBLE);
        MEDIA.post(() -> {
            releasePlayer();
        });
    }

    void close() {
        closed = true;
        stop();
        // The detach callback keeps SurfaceTexture alive until the decoder has disconnected.
        root.removeView(layer);
        poster.setImageDrawable(null);
    }

    void update(long interval) {
        if (!active || closed || !VideoWallpaperCatalog.rotate(choice, state.getLong("video_changed", 0),
                System.currentTimeMillis(), interval)) return;
        scene = VideoWallpaperCatalog.next(scene);
        remember(); generation++; openRequested = false;
        MEDIA.post(() -> { position = 0; releasePlayer(); });
        showPoster(); open();
    }

    private void remember() {
        state.edit().putString("video_scene", VideoWallpaperCatalog.IDS[scene])
                .putLong("video_changed", System.currentTimeMillis()).apply();
    }

    private void showPoster() {
        poster.setVisibility(View.VISIBLE);
        try (InputStream in = context.getAssets().open("video-wallpapers/" + VideoWallpaperCatalog.IDS[scene] + ".jpg")) {
            poster.setImageBitmap(BitmapFactory.decodeStream(in));
        } catch (Exception unavailable) { poster.setImageDrawable(null); }
    }

    private boolean current(long request) { return active && !closed && generation == request; }

    private void open() {
        if (!active || closed || !texture.isAvailable() || openRequested) return;
        openRequested = true;
        final long request = ++generation;
        final String id = VideoWallpaperCatalog.IDS[scene];
        final Surface target = new Surface(texture.getSurfaceTexture());
        MEDIA.post(() -> {
            if (!current(request)) { target.release(); return; }
            releasePlayer();
            UhdVideoDecoder next = new UhdVideoDecoder(MEDIA,
                    () -> {
                        Log.i("TvHome.VideoWallpaper", "Rendering " + id + " 3840x2160");
                        ui.post(() -> { if (current(request)) poster.setVisibility(View.GONE); });
                    }, () -> ui.post(() -> {
                        if (current(request)) { openRequested = false; poster.setVisibility(View.VISIBLE);
                            Toast.makeText(context, "当前设备无法播放此 4K 视频，暂显示预览图", Toast.LENGTH_LONG).show(); }
                    }));
            player = next; surface = target;
            try { next.open(context, id, target); }
            catch (Exception error) {
                Log.w("TvHome.VideoWallpaper", "4K decoder failed: " + id, error);
                releasePlayer();
                ui.post(() -> { if (current(request)) openRequested = false; });
            }
        });
    }

    private void releasePlayer() {
        if (player != null) { player.close(); player = null;
            Log.i("TvHome.VideoWallpaper", "Video decoder released"); }
        if (surface != null) { surface.release(); surface = null; }
    }

    private void fit() {
        if (videoWidth <= 0 || videoHeight <= 0 || texture.getWidth() <= 0 || texture.getHeight() <= 0) return;
        float viewAspect = (float) texture.getWidth() / texture.getHeight();
        float videoAspect = (float) videoWidth / videoHeight;
        Matrix matrix = new Matrix();
        matrix.setScale(Math.max(1f, videoAspect / viewAspect), Math.max(1f, viewAspect / videoAspect),
                texture.getWidth() / 2f, texture.getHeight() / 2f);
        texture.setTransform(matrix);
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) { open(); }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) { fit(); }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        generation++; openRequested = false;
        MEDIA.post(() -> { releasePlayer(); texture.release(); });
        return false;
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) { }
}
