package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.View;
import java.util.function.Consumer;

/** Blurs a downsampled copy of our own window; works on Android 11 without privileged screen capture. */
final class FrostedPanel {
    private static Bitmap pending;
    static int dp(android.content.Context c, float n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }

    static void open(Activity activity, Runnable action) {
        capture(activity, bitmap -> {
            if (activity.isFinishing() || activity.isDestroyed()) { if (bitmap != null) bitmap.recycle(); return; }
            if (pending != null) pending.recycle();
            pending = bitmap;
            action.run();
        });
    }

    static void fromPending(Activity activity, View panel) {
        Bitmap image = pending;
        pending = null;
        panel.setBackground(new Surface(panel, image));
    }

    static void decorate(Activity activity, View panel) {
        Surface surface = new Surface(panel, null);
        panel.setBackground(surface);
        capture(activity, bitmap -> {
            surface.image = bitmap;
            panel.invalidate();
        });
    }

    private static void capture(Activity activity, Consumer<Bitmap> callback) {
        View decor = activity.getWindow().getDecorView();
        if (decor.getWidth() < 1 || decor.getHeight() < 1) { callback.accept(null); return; }
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, decor.getWidth() / 6), Math.max(1, decor.getHeight() / 6), Bitmap.Config.ARGB_8888);
        try {
            PixelCopy.request(activity.getWindow(), bitmap, result -> {
                if (result != PixelCopy.SUCCESS) { bitmap.recycle(); callback.accept(null); return; }
                new Thread(() -> {
                    blur(bitmap, 8);
                    decor.post(() -> callback.accept(bitmap));
                }, "PanelBlur").start();
            }, new Handler(Looper.getMainLooper()));
        } catch (RuntimeException unavailable) { bitmap.recycle(); callback.accept(null); }
    }

    private static void blur(Bitmap image, int radius) {
        int w = image.getWidth(), h = image.getHeight(), count = w * h, size = radius * 2 + 1;
        int[] pixels = new int[count], temp = new int[count];
        image.getPixels(pixels, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) {
            for (int y = 0; y < h; y++) {
                int red = 0, green = 0, blue = 0;
                for (int i = -radius; i <= radius; i++) {
                    int c = pixels[y * w + Math.max(0, Math.min(w - 1, i))];
                    red += c >> 16 & 255; green += c >> 8 & 255; blue += c & 255;
                }
                for (int x = 0; x < w; x++) {
                    temp[y * w + x] = 0xFF000000 | (red / size << 16) | (green / size << 8) | blue / size;
                    int a = pixels[y * w + Math.max(0, x - radius)], b = pixels[y * w + Math.min(w - 1, x + radius + 1)];
                    red += (b >> 16 & 255) - (a >> 16 & 255);
                    green += (b >> 8 & 255) - (a >> 8 & 255); blue += (b & 255) - (a & 255);
                }
            }
            for (int x = 0; x < w; x++) {
                int red = 0, green = 0, blue = 0;
                for (int i = -radius; i <= radius; i++) {
                    int c = temp[Math.max(0, Math.min(h - 1, i)) * w + x];
                    red += c >> 16 & 255; green += c >> 8 & 255; blue += c & 255;
                }
                for (int y = 0; y < h; y++) {
                    pixels[y * w + x] = 0xFF000000 | (red / size << 16) | (green / size << 8) | blue / size;
                    int a = temp[Math.max(0, y - radius) * w + x], b = temp[Math.min(h - 1, y + radius + 1) * w + x];
                    red += (b >> 16 & 255) - (a >> 16 & 255);
                    green += (b >> 8 & 255) - (a >> 8 & 255); blue += (b & 255) - (a & 255);
                }
            }
        }
        image.setPixels(pixels, 0, w, 0, 0, w, h);
    }

    private static final class Surface extends Drawable {
        private final View view;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Path clip = new Path();
        private Bitmap image;
        Surface(View view, Bitmap image) { this.view = view; this.image = image; }
        @Override public void draw(Canvas canvas) {
            float w = getBounds().width(), h = getBounds().height(), radius = dp(view.getContext(), 24);
            RectF box = new RectF(1, 1, w - 1, h - 1);
            clip.reset(); clip.addRoundRect(box, radius, radius, Path.Direction.CW);
            int save = canvas.save(); canvas.clipPath(clip);
            paint.setColor(Color.WHITE); paint.setStyle(Paint.Style.FILL); paint.setShader(null);
            if (image != null && !image.isRecycled()) {
                int[] position = new int[2]; view.getLocationOnScreen(position);
                android.util.DisplayMetrics screen = view.getResources().getDisplayMetrics();
                canvas.drawBitmap(image, null, new RectF(-position[0], -position[1],
                        screen.widthPixels - position[0], screen.heightPixels - position[1]), paint);
            } else { canvas.drawColor(0xFF404957); }
            paint.setShader(new LinearGradient(0, 0, w, h, 0x668E949F, 0x99293342, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            canvas.restoreToCount(save);
            paint.setShader(null); paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(view.getContext(), .8f)); paint.setColor(0x66FFFFFF);
            canvas.drawRoundRect(box, radius, radius, paint);
        }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
