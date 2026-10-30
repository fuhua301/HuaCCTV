package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.*;
import java.io.File;
import java.util.*;

/** Right-hand frosted control center; all settings use remote-friendly nested pages. */
public final class LauncherSettingsActivity extends Activity {
    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    static final String EXTRA_INPUTS = "com.gathin.tvhome.extra.INPUTS_FROM_SETTINGS";
    private SharedPreferences choices;
    private WallpaperLanServer wallpaperServer;
    private final java.util.concurrent.ExecutorService weatherWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private int weatherSearchSerial;
    private LinearLayout body;
    private TextView breadcrumb, footer;
    private final ArrayList<Page> pages = new ArrayList<>();
    private static final class Page {
        final String title; final Runnable build;
        Page(String title, Runnable build) { this.title = title; this.build = build; }
    }
    private int dp(float n) { return ControlCenterUi.dp(this, n); }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        setFinishOnTouchOutside(true);
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(15), dp(16), dp(12));
        FrostedPanel.fromPending(this, panel);
        TextView brand = ControlCenterUi.label(this, "HuaCCTV", 16);
        brand.setGravity(Gravity.CENTER);
        brand.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        panel.addView(brand, new LinearLayout.LayoutParams(-1, dp(24)));
        panel.addView(ControlCenterUi.header(this, 2,
                () -> result(EXTRA_INPUTS), () -> open(SystemSettings.intent(this, Settings.ACTION_WIFI_SETTINGS)), this::home));
        View volume = ControlCenterUi.volume(this);
        LinearLayout.LayoutParams volumeSize = new LinearLayout.LayoutParams(-1, dp(36)); volumeSize.topMargin = dp(4);
        panel.addView(volume, volumeSize);
        breadcrumb = ControlCenterUi.label(this, "简洁桌面设置", 13); breadcrumb.setPadding(dp(3), dp(12), 0, dp(9));
        panel.addView(breadcrumb);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); scroll.addView(body);
        int room = (int) (getResources().getDisplayMetrics().heightPixels / getResources().getDisplayMetrics().density) - 289;
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, dp(Math.min(230, Math.max(100, room)))));
        footer = ControlCenterUi.label(this, "简洁桌面 · 设置自动保存", 10);
        footer.setTextColor(0x99FFFFFF); footer.setPadding(dp(3), dp(10), 0, 0); panel.addView(footer);
        setContentView(panel);
        android.view.Window window = getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.width = dp(320); attributes.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        attributes.gravity = Gravity.TOP | Gravity.RIGHT; attributes.x = dp(26); attributes.y = dp(20);
        attributes.dimAmount = .35f; attributes.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        window.setAttributes(attributes);
        home();
    }
    private void home() { pages.clear(); push("简洁桌面设置", this::rootPage); }
    private void push(String title, Runnable builder) { pages.add(new Page(title, builder)); render(0); }
    private void render(int focus) {
        body.removeAllViews(); Page page = pages.get(pages.size() - 1);
        breadcrumb.setText((pages.size() > 1 ? "‹  " : "") + page.title);
        if (pages.size() > 1) add("‹ 返回上一级", "", this::back);
        footer.setText("扫码管理壁纸".equals(page.title) ? "同一网络扫码管理 · 完成后电视按返回键" : "简洁桌面 · 设置自动保存");
        page.build.run();
        if (body.getChildCount() > 0) body.getChildAt(Math.min(focus, body.getChildCount() - 1)).requestFocus();
    }
    private void back() { if (pages.size() <= 1) finish(); else { pages.remove(pages.size() - 1); render(0); } }
    @Override public void onBackPressed() { back(); }
    private void add(String name, String value, Runnable action) {
        TextView row = ControlCenterUi.row(this, name, value, action);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2); size.bottomMargin = dp(4);
        body.addView(row, size);
    }
    private void rootPage() {
        add("个性化", "布局、壁纸与动效  ›", () -> push("个性化", this::personal));
        add("天气位置", weatherLocationLabel(), () -> push("天气位置", this::weatherLocationPage));
        add("开机启动", appLabel(), () -> push("开机启动 · 用户应用", this::startup));
        add("整理应用", "移动桌面图标  ›", () -> result(EXTRA_REORDER));
        add("投屏", "›", () -> open(SystemSettings.casting(this)));
        add("系统设置", "›", () -> open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        add("恢复主题默认", "›", () -> push("恢复主题默认", () -> {
            add("取消", "", this::back);
            add("确认恢复布局、壁纸与动效", "", this::reset);
        }));
    }
    private String weatherLocationLabel() {
        WeatherCity city = WeatherLocations.cached(this);
        return (WeatherLocations.AUTO.equals(WeatherLocations.mode(this)) ? "自动 · " : "手动 · ")
                + (city == null ? "待定位" : city.name) + "  ›";
    }
    private void weatherLocationPage() {
        boolean automatic = WeatherLocations.AUTO.equals(WeatherLocations.mode(this));
        add("自动定位城市", mark(automatic), () -> { WeatherLocations.automatic(this); changed(); refresh(); });
        add("手动选择城市", !automatic ? "✓  ›" : "›", () -> push("搜索天气城市", this::weatherCitySearch));
        WeatherCity city = WeatherLocations.cached(this);
        TextView hint = ControlCenterUi.label(this, "当前：" + (city == null ? "返回桌面后获取位置" : city.name)
                + "\n定位来源：" + WeatherLocations.source(this)
                + "\n优先使用电视系统位置；系统未提供时才使用公网 IP 估算。地址有误时可手动选择。", 11);
        hint.setPadding(dp(8), dp(12), dp(8), dp(8)); hint.setTextColor(0xCCFFFFFF); body.addView(hint);
    }
    private void weatherCitySearch() {
        final int request = ++weatherSearchSerial;
        WeatherCity saved = WeatherLocations.lastManual(this);
        if (saved != null) add("使用 " + saved.name, "", () -> { WeatherLocations.manual(this, saved); changed(); back(); });
        EditText input = new EditText(this); input.setSingleLine(true); input.setTextSize(14); input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xAAFFFFFF); input.setHint("城市或区县，如：北京、深圳、南康");
        input.setPadding(dp(12), dp(6), dp(12), dp(6)); input.setBackground(ControlCenterUi.round(this, 0x18FFFFFF, 12));
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT); input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        body.addView(input, new LinearLayout.LayoutParams(-1, dp(44)));
        LinearLayout results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL);
        Runnable search = () -> {
            String query = input.getText().toString().trim();
            android.view.inputmethod.InputMethodManager keyboard = getSystemService(android.view.inputmethod.InputMethodManager.class);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
            results.removeAllViews(); results.addView(ControlCenterUi.label(this, "正在搜索…", 12));
            weatherWorker.execute(() -> {
                java.util.List<WeatherCity> cities = java.util.Collections.emptyList(); String problem = null;
                try { cities = WeatherLocations.search(query); } catch (Exception unavailable) { problem = unavailable.getMessage(); }
                final java.util.List<WeatherCity> found = cities; final String error = problem;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || request != weatherSearchSerial || pages.isEmpty()
                            || !"搜索天气城市".equals(pages.get(pages.size()-1).title)) return;
                    results.removeAllViews();
                    if (found.isEmpty()) { results.addView(ControlCenterUi.label(this, error == null ? "没有找到城市，请尝试其他名称。" : "搜索失败：" + error, 12)); return; }
                    for (WeatherCity city : found) {
                        TextView row = ControlCenterUi.row(this, city.name, "", () -> { WeatherLocations.manual(this, city); changed(); back(); });
                        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2); size.bottomMargin = dp(4); results.addView(row, size);
                    }
                    if (results.getChildCount() > 0) results.getChildAt(0).requestFocus();
                });
            });
        };
        add("搜索", "", search); body.addView(results);
        input.setOnEditorActionListener((v, action, event) -> { if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) { search.run(); return true; } return false; });
    }

    private void personal() {
        add("桌面布局", "›", () -> push("桌面布局", this::layout));
        add("壁纸与轮换", "›", () -> push("壁纸与轮换", this::wallpapers));
        add("动态效果", "›", () -> push("动态效果", this::motion));
        add("Dock 栏不透明度", Config.load(this).dockOpacity + "%  ›", () -> push("Dock 栏不透明度", () -> {
            for (int n : new int[]{100, 70, 50, 30}) add(n + "%", mark(Config.load(this).dockOpacity == n),
                    () -> saveInt(Config.DOCK_OPACITY_KEY, n));
        }));
    }
    private void layout() {
        Config config = Config.load(this);
        add("每行应用数量", config.columns + "  ›", () -> push("每行应用数量", () -> {
            for (int n = 4; n <= 7; n++) { final int value = n;
                add(n + " 个", mark(Config.load(this).columns == n), () -> saveInt("columns", value)); }
        }));
        add("Dock 下方应用行数", config.rowsBelowDock + "  ›", () -> push("下方应用行数", () -> {
            for (int n = 0; n <= 3; n++) { final int value = n;
                add(n + " 行", mark(Config.load(this).rowsBelowDock == n), () -> saveInt(Config.ROWS_BELOW_DOCK_KEY, value)); }
        }));
    }
    private void wallpapers() {
        add("手机扫码管理壁纸", "添加、删除与选择  ›", () -> push("扫码管理壁纸", this::wallpaperQr));
        add("选择壁纸", "›", () -> push("选择壁纸", this::wallpaperChoices));
        int minutes = getSharedPreferences(WallpaperPlayer.KEY_PREFS, MODE_PRIVATE).getInt(WallpaperPlayer.KEY_MINUTES, 0);
        add("自动轮换", minutes == 0 ? "跟随主题  ›" : minutes + " 分钟  ›", () -> push("壁纸轮换间隔", () -> {
            for (int n = 0; n <= 10; n++) { final int value = n;
                add(n == 0 ? "跟随主题" : n + " 分钟", mark(getSharedPreferences(WallpaperPlayer.KEY_PREFS, MODE_PRIVATE)
                        .getInt(WallpaperPlayer.KEY_MINUTES, 0) == n), () -> {
                    getSharedPreferences(WallpaperPlayer.KEY_PREFS, MODE_PRIVATE).edit().putInt(WallpaperPlayer.KEY_MINUTES, value).apply();
                    changed(); refresh();
                }); }
        }));
    }
    private void wallpaperQr() {
        try {
            String address = wallpaperAddress();
            if (address == null) {
                TextView hint = ControlCenterUi.label(this, "请先将电视连接到局域网，再返回此页面。", 13);
                body.addView(hint); add("重试", "", this::refresh); return;
            }
            if (wallpaperServer == null) {
                byte[] html;
                try (java.io.InputStream input = getAssets().open("wallpaper-manager.html");
                     java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    html = output.toByteArray();
                }
                wallpaperServer = new WallpaperLanServer(new WallpaperWebStore(this), html);
            }
            String url = wallpaperServer.url(address);
            java.util.Map<com.google.zxing.EncodeHintType, Object> hints = new java.util.EnumMap<>(com.google.zxing.EncodeHintType.class);
            hints.put(com.google.zxing.EncodeHintType.MARGIN, 3);
            hints.put(com.google.zxing.EncodeHintType.ERROR_CORRECTION, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M);
            com.google.zxing.common.BitMatrix matrix = new com.google.zxing.qrcode.QRCodeWriter().encode(
                    url, com.google.zxing.BarcodeFormat.QR_CODE, dp(176), dp(176), hints);
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(matrix.getWidth(), matrix.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
            int[] pixels = new int[matrix.getWidth() * matrix.getHeight()];
            for (int y = 0; y < matrix.getHeight(); y++) for (int x = 0; x < matrix.getWidth(); x++)
                pixels[y * matrix.getWidth() + x] = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
            bitmap.setPixels(pixels, 0, matrix.getWidth(), 0, 0, matrix.getWidth(), matrix.getHeight());
            ImageView qr = new ImageView(this); qr.setImageBitmap(bitmap); qr.setContentDescription("壁纸管理二维码：" + url);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(176), dp(176)); size.gravity = Gravity.CENTER_HORIZONTAL;
            body.addView(qr, size);
            TextView hint = ControlCenterUi.label(this, "同一局域网的手机扫码添加、删除和选择壁纸。保持设置开启，完成后返回桌面生效。", 11);
            hint.setPadding(dp(4), dp(8), dp(4), dp(8)); body.addView(hint);
            TextView link = ControlCenterUi.label(this, url, 9); link.setTextColor(0xBBFFFFFF); body.addView(link);
            add("刷新二维码", "", () -> { stopWallpaperServer(); refresh(); });
        } catch (Exception failure) {
            TextView hint = ControlCenterUi.label(this, "无法开启壁纸管理：" + failure.getMessage(), 12); body.addView(hint);
            add("重试", "", this::refresh);
        }
    }
    private String wallpaperAddress() {
        android.net.ConnectivityManager networks = getSystemService(android.net.ConnectivityManager.class);
        if (networks == null) return null;
        for (android.net.Network network : networks.getAllNetworks()) {
            android.net.NetworkCapabilities capabilities = networks.getNetworkCapabilities(network);
            if (capabilities == null || !(capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                    || capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET))) continue;
            android.net.LinkProperties links = networks.getLinkProperties(network); if (links == null) continue;
            for (android.net.LinkAddress link : links.getLinkAddresses()) {
                java.net.InetAddress address = link.getAddress();
                if (address instanceof java.net.Inet4Address && !address.isLoopbackAddress() && !address.isLinkLocalAddress()) return address.getHostAddress();
            }
        }
        return null;
    }
    private void stopWallpaperServer() {
        if (wallpaperServer != null) { try { wallpaperServer.close(); } catch (java.io.IOException ignored) { } wallpaperServer = null; }
    }
    @Override protected void onDestroy() { weatherWorker.shutdownNow(); stopWallpaperServer(); super.onDestroy(); }

    private void wallpaperChoices() {
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        add("跟随主题", mark(current == null), () -> { choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply(); changed(); refresh(); });
        add("不显示壁纸", mark("none".equals(current)), () -> saveString(Config.WALLPAPER_CHOICE_KEY, "none"));
        File dir = Config.dir(this);
        List<File> images = new ArrayList<>(WallpaperRotation.images(dir == null ? null : new File(dir, "wallpapers")));
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        for (File image : images) add(image.getName(), mark(image.getName().equals(current)),
                () -> saveString(Config.WALLPAPER_CHOICE_KEY, image.getName()));
    }
    private void motion() {
        Config config = Config.load(this);
        add("动态效果", config.animations ? "开" : "关", () -> {
            boolean enabled = !Config.load(this).animations;
            SharedPreferences.Editor editor = choices.edit().putBoolean("animations", enabled);
            if (!enabled) editor.putBoolean("advanced_effects", false);
            editor.apply(); changed(); refresh();
        });
        add("高级动态效果", config.advancedEffects ? "开  ›" : "关  ›", () -> push("高级动态效果", () -> {
            TextView hint = ControlCenterUi.label(this, "图标上浮与背景颜色过渡会增加内存占用。", 10);
            hint.setPadding(dp(8), dp(6), dp(8), dp(10)); hint.setTextColor(0xBBFFFFFF); body.addView(hint);
            add("开启", mark(Config.load(this).advancedEffects), () -> { choices.edit().putBoolean("animations", true)
                    .putBoolean("advanced_effects", true).apply(); changed(); refresh(); });
            add("关闭", mark(!Config.load(this).advancedEffects), () -> { choices.edit().putBoolean("advanced_effects", false).apply(); changed(); refresh(); });
        }));
    }
    private void startup() {
        String current = choices.getString(OpenOnStart.KEY, getString(R.string.open_on_start_package).trim());
        add("关闭开机启动", mark(current.isEmpty()), () -> selectStartup(""));
        PackageManager pm = getPackageManager(); List<ApplicationInfo> apps = new ArrayList<>();
        for (ApplicationInfo info : pm.getInstalledApplications(0)) {
            if (!info.enabled || (info.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                    || info.packageName.equals(getPackageName()) || OpenOnStart.intent(pm, info.packageName) == null) continue;
            apps.add(info);
        }
        apps.sort((a, b) -> pm.getApplicationLabel(a).toString().compareToIgnoreCase(pm.getApplicationLabel(b).toString()));
        for (ApplicationInfo info : apps) add(pm.getApplicationLabel(info).toString(), mark(info.packageName.equals(current)),
                () -> selectStartup(info.packageName));
    }
    private void reset() {
        choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations").remove("advanced_effects")
                .remove(Config.WALLPAPER_CHOICE_KEY).remove(Config.DOCK_OPACITY_KEY).remove(OpenOnStart.KEY).commit();
        getSharedPreferences(WallpaperPlayer.KEY_PREFS, MODE_PRIVATE).edit().remove(WallpaperPlayer.KEY_MINUTES).commit();
        OpenOnStart.skipThisBoot(this); changed(); home();
    }
    private void selectStartup(String pkg) { OpenOnStart.skipThisBoot(this); saveString(OpenOnStart.KEY, pkg); }
    private void saveInt(String key, int value) { choices.edit().putInt(key, value).apply(); changed(); refresh(); }
    private void saveString(String key, String value) { choices.edit().putString(key, value).apply(); changed(); refresh(); }
    private void refresh() { render(Math.max(0, body.indexOfChild(getCurrentFocus()))); }
    private void changed() { setResult(RESULT_OK); choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
            choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply(); }
    private String mark(boolean selected) { return selected ? "✓" : ""; }
    private String appLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, getString(R.string.open_on_start_package).trim()); if (pkg.isEmpty()) return "关闭  ›";
        try { return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)) + "  ›"; }
        catch (PackageManager.NameNotFoundException unavailable) { return "应用不可用  ›"; }
    }
    private void result(String extra) { setResult(RESULT_OK, new Intent().putExtra(extra, true)); finish(); }
    private void open(Intent intent) {
        try { startActivity(intent); finish(); }
        catch (RuntimeException unavailable) { Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show(); }
    }
}
