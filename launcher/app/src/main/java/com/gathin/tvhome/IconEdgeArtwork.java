package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Small cached tile texture: continue each icon edge, feather only the outer rim of the original. */
final class IconEdgeArtwork {
    private static final int N = 192, W = 288, H = 162;
    private IconEdgeArtwork() { }

    static Bitmap create(Drawable live, float share, int fallback) {
        Bitmap source = Bitmap.createBitmap(N, N, Bitmap.Config.ARGB_8888);
        if (live != null) {
            Drawable d = live.getConstantState() == null ? live : live.getConstantState().newDrawable().mutate();
            Rect bounds = new Rect(d.getBounds());
            d.setBounds(0, 0, N, N); d.draw(new Canvas(source)); d.setBounds(bounds);
        }
        int[] original = new int[N * N]; source.getPixels(original, 0, N, 0, 0, N, N);
        source.recycle();
        int left = N, top = N, right = -1, bottom = -1;
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            if (Color.alpha(original[y * N + x]) < 128) continue;
            left = Math.min(left, x); top = Math.min(top, y);
            right = Math.max(right, x); bottom = Math.max(bottom, y);
        }
        if (right < left) { left = top = 0; right = bottom = N - 1; }
        int[][] edges = new int[4][N];
        for (int t = 0; t < N; t++) {
            float at = t / (float) (N - 1);
            // Sample just inside rounded corners; transparent pixels do not contaminate edge colours.
            float safe = .16f + .68f * at;
            edges[0][t] = sample(original, left, top, right, bottom, .10f, safe, fallback);
            edges[1][t] = sample(original, left, top, right, bottom, .90f, safe, fallback);
            edges[2][t] = sample(original, left, top, right, bottom, safe, .10f, fallback);
            edges[3][t] = sample(original, left, top, right, bottom, safe, .90f, fallback);
        }
        float size = H * Math.max(.5f, Math.min(1f, share));
        float x0 = (W - size) / 2, y0 = (H - size) / 2;
        int[] output = new int[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            float u = (x - x0) / size, v = (y - y0) / size;
            float cu = Math.max(0, Math.min(1, u)), cv = Math.max(0, Math.min(1, v));
            int ix = Math.round(cu * (N - 1)), iy = Math.round(cv * (N - 1));
            // Inverse squared distance to the four edges gives a smooth spatial colour field.
            float wl = weight(u), wr = weight(1 - u), wt = weight(v), wb = weight(1 - v);
            if (u < 0) { wl = 2500; wr = wt = wb = 0; }
            else if (u > 1) { wr = 2500; wl = wt = wb = 0; }
            if (v < 0) { wt = 2500; if (u >= 0 && u <= 1) wl = wr = wb = 0; }
            else if (v > 1) { wb = 2500; if (u >= 0 && u <= 1) wl = wr = wt = 0; }
            int base = weighted(edges[0][iy], edges[1][iy], edges[2][ix], edges[3][ix], wl, wr, wt, wb);
            if (u >= 0 && u <= 1 && v >= 0 && v <= 1) {
                int sx = Math.min(right, left + Math.round(cu * (right - left)));
                int sy = Math.min(bottom, top + Math.round(cv * (bottom - top)));
                int pixel = original[sy * N + sx];
                float rim = Math.min(Math.min(u, 1 - u), Math.min(v, 1 - v));
                float alpha = Math.max(0, Math.min(1, rim / .12f));
                alpha = alpha * alpha * (3 - 2 * alpha) * Color.alpha(pixel) / 255f;
                base = blend(base, pixel, alpha);
            }
            output[y * W + x] = base;
        }
        return Bitmap.createBitmap(output, W, H, Bitmap.Config.ARGB_8888);
    }
    private static float weight(float distance) { return 1f / (.0004f + distance * distance); }
    private static int sample(int[] pixels, int l, int t, int r, int b, float u, float v, int fallback) {
        int cx = l + Math.round(u * (r - l)), cy = t + Math.round(v * (b - t));
        long red = 0, green = 0, blue = 0, total = 0;
        for (int dy = -5; dy <= 5; dy++) for (int dx = -5; dx <= 5; dx++) {
            int pixel = pixels[Math.max(t, Math.min(b, cy + dy)) * N + Math.max(l, Math.min(r, cx + dx))];
            int alpha = Color.alpha(pixel);
            red += Color.red(pixel) * alpha; green += Color.green(pixel) * alpha; blue += Color.blue(pixel) * alpha;
            total += alpha;
        }
        return total == 0 ? fallback | 0xFF000000 : Color.rgb((int) (red / total), (int) (green / total), (int) (blue / total));
    }
    private static int weighted(int a, int b, int c, int d, float wa, float wb, float wc, float wd) {
        float total = wa + wb + wc + wd;
        return Color.rgb(Math.round((Color.red(a)*wa + Color.red(b)*wb + Color.red(c)*wc + Color.red(d)*wd)/total),
                Math.round((Color.green(a)*wa + Color.green(b)*wb + Color.green(c)*wc + Color.green(d)*wd)/total),
                Math.round((Color.blue(a)*wa + Color.blue(b)*wb + Color.blue(c)*wc + Color.blue(d)*wd)/total));
    }
    private static int blend(int a, int b, float alpha) {
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a))*alpha),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a))*alpha),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a))*alpha));
    }
}
