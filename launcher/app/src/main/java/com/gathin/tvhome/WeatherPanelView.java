package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Compact Xiaomi-style current weather and three daily forecasts, beside the Home clock. */
public final class WeatherPanelView extends LinearLayout {
    final TextView current, details, source;
    final WeatherIconView icon;
    private final TextView[] conditions = new TextView[3], ranges = new TextView[3];
    private final WeatherIconView[] icons = new WeatherIconView[3];

    public WeatherPanelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setPadding(dp(10), dp(8), dp(10), dp(6));
        // Same unfocused frosted-white colors as the Inputs button (pill_bg.xml).
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x2EFFFFFF, 0x14FFFFFF});
        background.setCornerRadius(dp(11));
        background.setStroke(dp(1), 0x38FFFFFF);
        setBackground(background);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setClickable(false);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        LinearLayout top = new LinearLayout(context);
        top.setGravity(Gravity.CENTER_VERTICAL);
        icon = new WeatherIconView(context, null);
        LayoutParams picture = new LayoutParams(dp(42), dp(34));
        picture.rightMargin = dp(8);
        top.addView(icon, picture);
        LinearLayout text = new LinearLayout(context);
        text.setOrientation(VERTICAL);
        current = label(16, 0xFFFFFFFF);
        current.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        current.setText("赣州·南康 · 天气加载中");
        details = label(9, 0xCCFFFFFF);
        details.setPadding(0, dp(3), 0, 0);
        text.addView(current);
        text.addView(details);
        top.addView(text, new LayoutParams(0, -2, 1));
        addView(top);

        LinearLayout days = new LinearLayout(context);
        LayoutParams daysSize = new LayoutParams(-1, -2);
        daysSize.topMargin = dp(8);
        String[] names = {"今天", "明天", "后天"};
        for (int i = 0; i < 3; i++) {
            if (i > 0) {
                View divider = new View(context);
                divider.setBackgroundColor(0x33FFFFFF);
                LayoutParams line = new LayoutParams(dp(.5f), dp(48));
                line.gravity = Gravity.CENTER_VERTICAL;
                days.addView(divider, line);
            }
            LinearLayout column = new LinearLayout(context);
            column.setOrientation(VERTICAL);
            column.setGravity(Gravity.CENTER_HORIZONTAL);
            column.setPadding(dp(4), 0, dp(4), 0);
            LinearLayout heading = new LinearLayout(context);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = label(11, 0xFFFFFFFF);
            name.setText(names[i]);
            heading.addView(name);
            icons[i] = new WeatherIconView(context, null);
            LayoutParams smallPicture = new LayoutParams(dp(25), dp(19));
            smallPicture.leftMargin = dp(6);
            heading.addView(icons[i], smallPicture);
            column.addView(heading);
            conditions[i] = label(10, 0xDDFFFFFF);
            conditions[i].setGravity(Gravity.CENTER);
            conditions[i].setPadding(0, dp(3), 0, dp(3));
            conditions[i].setText("—");
            ranges[i] = label(13, 0xFFFFFFFF);
            ranges[i].setGravity(Gravity.CENTER);
            ranges[i].setText("— / —");
            column.addView(conditions[i], new LayoutParams(-1, -2));
            column.addView(ranges[i]);
            days.addView(column, new LayoutParams(0, -2, 1));
        }
        addView(days, daysSize);
        source = label(8, 0xA6FFFFFF);
        source.setPadding(0, dp(6), 0, 0);
        addView(source);
    }

    void render(XiaomiWeather.Reading reading, boolean stale) {
        current.setText(reading.title);
        // The current summary keeps humidity/feels-like and air quality; daily ranges are below.
        String detailsText = reading.details;
        int firstSeparator = detailsText.indexOf(" · ");
        if (firstSeparator >= 0) detailsText = detailsText.substring(firstSeparator + 3);
        int tomorrow = reading.forecast.indexOf(" · 明日");
        String windAir = tomorrow < 0 ? reading.forecast : reading.forecast.substring(0, tomorrow);
        details.setText(detailsText + " · " + windAir);
        icon.setWeather(reading.scene, reading.day);
        StringBuilder description = new StringBuilder(reading.title);
        for (int i = 0; i < 3; i++) {
            XiaomiWeather.Day day = reading.days[i];
            conditions[i].setText(day.condition);
            String high = day.high + "°", range = high + " / " + day.low + "°";
            SpannableString styled = new SpannableString(range);
            styled.setSpan(new ForegroundColorSpan(0x99FFFFFF), high.length(), range.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ranges[i].setText(styled);
            icons[i].setWeather(day.scene, true);
            description.append(' ').append(new String[]{"今天", "明天", "后天"}[i])
                    .append(day.condition).append(range);
        }
        source.setText("小米天气 · " + new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                .format(new Date(reading.observed)) + " 发布" + (stale ? " · 缓存，等待更新" : ""));
        setContentDescription(description);
    }

    void start() { icon.start(); for (WeatherIconView image : icons) image.start(); }
    void stop() { icon.stop(); for (WeatherIconView image : icons) image.stop(); }

    private TextView label(int size, int color) {
        TextView text = new TextView(getContext());
        text.setTextColor(color);
        text.setShadowLayer(dp(2), 0, dp(.5f), 0xB3000000);
        text.setTextSize(size);
        text.setSingleLine(true);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.setIncludeFontPadding(false);
        text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return text;
    }

    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
