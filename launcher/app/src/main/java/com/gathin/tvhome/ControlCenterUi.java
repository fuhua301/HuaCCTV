package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioManager;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

final class ControlCenterUi {
    static int dp(Context c, float n) { return FrostedPanel.dp(c, n); }
    static TextView label(Context c, String text, int size) {
        TextView v = new TextView(c); v.setText(text); v.setTextSize(size); v.setTextColor(Color.WHITE);
        v.setIncludeFontPadding(false); v.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return v;
    }
    static GradientDrawable round(Context c, int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c, radius)); return d;
    }
    static StateListDrawable rowBackground(Context c) {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_focused}, round(c, 0xF2FFFFFF, 14));
        d.addState(new int[]{android.R.attr.state_pressed}, round(c, 0xCCFFFFFF, 14));
        d.addState(new int[]{}, round(c, 0x12FFFFFF, 14)); return d;
    }
    static LinearLayout header(Activity c, int selected, Runnable inputs, Runnable network, Runnable settings) {
        LinearLayout header = new LinearLayout(c); header.setOrientation(LinearLayout.VERTICAL);
        TextClock time = new TextClock(c); time.setFormat12Hour("HH:mm"); time.setFormat24Hour("HH:mm");
        time.setTextSize(29); time.setTextColor(Color.WHITE); time.setIncludeFontPadding(false);
        time.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); header.addView(time);
        TextClock date = new TextClock(c); date.setFormat12Hour("M月d日 EEEE"); date.setFormat24Hour("M月d日 EEEE");
        date.setTextSize(11); date.setTextColor(0xBBFFFFFF); header.addView(date);
        LinearLayout nav = new LinearLayout(c);
        String[] names = {"输入源", "网络", "桌面设置"}; int[] icons = {R.drawable.ic_input, R.drawable.ic_wifi, R.drawable.ic_home_settings_pill};
        Runnable[] actions = {inputs, network, settings};
        for (int i = 0; i < 3; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(c); item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER);
            item.setFocusable(true); item.setClickable(true); item.setPadding(0, dp(c, 8), 0, dp(c, 6));
            ImageView image = new ImageView(c); image.setImageResource(icons[i]); image.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
            image.setBackground(round(c, i == selected ? 0xF2FFFFFF : 0x18FFFFFF, 24));
            image.setImageTintList(ColorStateList.valueOf(i == selected ? 0xFF252A31 : Color.WHITE));
            item.addView(image, new LinearLayout.LayoutParams(dp(c, 44), dp(c, 44)));
            TextView name = label(c, names[i], 11); name.setGravity(Gravity.CENTER); name.setPadding(0, dp(c, 5), 0, 0); item.addView(name);
            item.setOnFocusChangeListener((v, focused) -> {
                boolean white = focused || index == selected;
                image.setBackground(round(c, white ? 0xF2FFFFFF : 0x18FFFFFF, 24));
                image.setImageTintList(ColorStateList.valueOf(white ? 0xFF252A31 : Color.WHITE));
            });
            item.setOnClickListener(v -> { if (actions[index] != null) actions[index].run(); });
            nav.addView(item, new LinearLayout.LayoutParams(0, dp(c, 78), 1));
        }
        header.addView(nav); return header;
    }
    static View volume(Activity c) {
        AudioManager audio = c.getSystemService(AudioManager.class);
        LinearLayout row = new LinearLayout(c); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(c, 12), 0, dp(c, 12), 0); row.setBackground(round(c, 0x18FFFFFF, 16));
        TextView icon = label(c, "音量", 11); row.addView(icon);
        SeekBar slider = new SeekBar(c); slider.setMax(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        slider.setProgress(audio.getStreamVolume(AudioManager.STREAM_MUSIC));
        slider.setProgressTintList(ColorStateList.valueOf(Color.WHITE));
        slider.setProgressBackgroundTintList(ColorStateList.valueOf(0x44FFFFFF));
        slider.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        LinearLayout.LayoutParams fill = new LinearLayout.LayoutParams(0, dp(c, 38), 1); fill.leftMargin = dp(c, 10); fill.rightMargin = dp(c, 8);
        row.addView(slider, fill); TextView value = label(c, String.valueOf(slider.getProgress()), 13); row.addView(value);
        slider.setContentDescription("音量，左右键调整");
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                value.setText(String.valueOf(progress));
                if (user) audio.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        }); return row;
    }
    static TextView row(Context c, String title, String value, Runnable action) {
        TextView row = label(c, title + (value == null || value.isEmpty() ? "" : "    " + value), 14);
        row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        row.setMinHeight(dp(c, 42)); row.setMaxLines(2); row.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.setFocusable(true); row.setClickable(true); row.setBackground(rowBackground(c));
        row.setTextColor(new ColorStateList(new int[][]{new int[]{android.R.attr.state_focused}, new int[]{}},
                new int[]{0xFF20252D, Color.WHITE}));
        row.setOnClickListener(v -> action.run()); return row;
    }
}
