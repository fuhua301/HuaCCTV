package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/** Small vector weather scene. Draws at 15 fps only while the Home header is visible. */
public final class WeatherIconView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int code = -1;
    private boolean day = true, active;
    private final Runnable frame = new Runnable() {
        @Override public void run() {
            invalidate();
            if (shouldAnimate()) postDelayed(this, 66);
        }
    };

    public WeatherIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    void setWeather(int code, boolean day) {
        this.code = code;
        this.day = day;
        invalidate();
        updateAnimation();
    }

    void start() { active = true; updateAnimation(); }
    void stop() { active = false; removeCallbacks(frame); }

    private boolean shouldAnimate() {
        return active && code >= 0 && isShown() && isAttachedToWindow()
                && getWindowVisibility() == VISIBLE && ValueAnimator.areAnimatorsEnabled();
    }

    private void updateAnimation() {
        removeCallbacks(frame);
        if (shouldAnimate()) post(frame);
    }

    @Override public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        updateAnimation();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(frame);
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.scale(getWidth() / 64f, getHeight() / 48f);
        float t = ValueAnimator.areAnimatorsEnabled() ? (SystemClock.uptimeMillis() % 6000) / 6000f : 0.25f;
        paint.setStyle(Paint.Style.FILL);
        int kind = WeatherHeader.conditionIndex(code);
        if (kind == 0) {
            skyLight(canvas, 32, 24, t);
        } else {
            if (kind == 1) skyLight(canvas, 22, 17, t);
            float drift = (float) Math.sin(t * Math.PI * 2) * 2;
            cloud(canvas, 32 + drift, kind >= 4 && kind <= 10 ? 20 : 25, kind >= 4 && kind <= 10);
            if (kind >= 4 && kind <= 8 || kind == 10) rain(canvas, t, kind == 7 || kind == 10);
            if (kind == 9) snow(canvas, t);
            if (kind == 3) fog(canvas, t);
            if (kind == 10) lightning(canvas, t);
        }
        canvas.restore();
    }

    private void skyLight(Canvas c, float x, float y, float t) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(day ? 0xFFFFD563 : 0xFFFFEFBB);
        if (day) {
            c.save();
            c.rotate(t * 360, x, y);
            paint.setStrokeWidth(2);
            paint.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 8; i++) {
                c.drawLine(x, y - 11, x, y - 14, paint);
                c.rotate(45, x, y);
            }
            c.restore();
            c.drawCircle(x, y, 8, paint);
        } else {
            path.reset();
            path.addCircle(x + 4, y - 3, 8, Path.Direction.CW);
            c.save();
            c.clipOutPath(path);
            c.drawCircle(x, y, 9, paint);
            c.restore();
            paint.setAlpha(160 + Math.round(70 * (float) Math.sin(t * Math.PI * 2)));
            c.drawCircle(x + 15, y - 9, 1.3f, paint);
            c.drawCircle(x - 13, y + 10, 0.9f, paint);
            paint.setAlpha(255);
        }
    }

    private void cloud(Canvas c, float x, float y, boolean rainy) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(rainy ? 0xFF9EB9CD : 0xFFEAF5FF);
        c.drawCircle(x - 12, y + 1, 7, paint);
        c.drawCircle(x - 4, y - 5, 10, paint);
        c.drawCircle(x + 8, y - 2, 8, paint);
        c.drawCircle(x + 15, y + 3, 6, paint);
        c.drawRoundRect(x - 13, y, x + 15, y + 9, 4, 4, paint);
        paint.setColor(rainy ? 0xFF82A4BD : 0xFFC9DFEE);
        c.drawRoundRect(x - 14, y + 5, x + 15, y + 9, 3, 3, paint);
    }

    private void rain(Canvas c, float t, boolean heavy) {
        paint.setColor(0xFF75D4FF);
        paint.setStrokeWidth(heavy ? 1.8f : 1.4f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        c.save();
        c.clipRect(8, 30, 56, 46);
        int drops = heavy ? 8 : 5;
        for (int i = 0; i < drops; i++) {
            float phase = (t * (heavy ? 12 : 9) + i * 0.271f) % 1;
            float x = 14 + i * (heavy ? 5 : 8) - phase * 3;
            float y = 27 + phase * 23;
            c.drawLine(x, y, x - 2, y + (heavy ? 6 : 4), paint);
        }
        c.restore();
    }

    private void snow(Canvas c, float t) {
        paint.setColor(0xFFF2FAFF);
        paint.setStrokeWidth(1.1f);
        for (int i = 0; i < 5; i++) {
            float phase = (t * 3 + i * 0.23f) % 1;
            float x = 15 + i * 8 + (float) Math.sin(phase * Math.PI * 2) * 2;
            float y = 30 + phase * 14;
            c.drawLine(x - 1.8f, y, x + 1.8f, y, paint);
            c.drawLine(x, y - 1.8f, x, y + 1.8f, paint);
        }
    }

    private void fog(Canvas c, float t) {
        paint.setColor(0xFFD1E4F1);
        paint.setStrokeWidth(2);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 3; i++) {
            float x = 15 + (float) Math.sin(t * Math.PI * 2 + i) * 4;
            c.drawLine(x, 34 + i * 4, x + 30 - i * 3, 34 + i * 4, paint);
        }
    }

    private void lightning(Canvas c, float t) {
        paint.setColor(0xFFFFD659);
        paint.setAlpha(150 + (int) (100 * (0.5 + 0.5 * Math.sin(t * Math.PI * 4))));
        path.reset();
        path.moveTo(33, 26); path.lineTo(26, 36); path.lineTo(32, 35);
        path.lineTo(29, 44); path.lineTo(40, 31); path.lineTo(34, 32); path.close();
        c.drawPath(path, paint);
        paint.setAlpha(255);
    }
}
