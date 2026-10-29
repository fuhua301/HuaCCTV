package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/** open_on_start_choice: the app Home opens once after each boot, picked in Launcher settings. */
final class OpenOnStart {
    /** The picked package in Config.SETTINGS_NAME; absent or empty means Off. */
    static final String KEY = "open_on_start";
    /** The boot whose first Home has been handled, shared with boot_to_live_tv. */
    static final String BOOT_PREFS = "boot";
    static final String BOOT_KEY = "live_tv_boot";
    static final String SESSION_KEY = "open_power_session";

    private OpenOnStart() {
    }

    /** Whether the first Home of this boot opens {@code pkg}: a known boot not handled yet, and an app that is not
     *  the launcher itself (which would only reopen Home). */
    static boolean due(int boot, int handledBoot, String pkg, String self) {
        return boot >= 0 && boot != handledBoot && pkg != null && !pkg.isEmpty() && !pkg.equals(self);
    }

    static int boot(Context ctx) {
        return Settings.Global.getInt(ctx.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }

    /** A pick takes effect from the next boot: the Home that follows the pick stays Home. */
    static void skipThisBoot(Context ctx) {
        ctx.getSharedPreferences(BOOT_PREFS, Context.MODE_PRIVATE).edit()
                .putInt(BOOT_KEY, boot(ctx)).putString(SESSION_KEY, session(ctx)).commit();
    }

    static String sessionKey(int boot, String wake) {
        if (boot < 0) return "";
        return boot + ":" + (wake != null && wake.matches("[0-9]{1,24}") ? wake : "boot");
    }

    /** Xiaomi STR power-on does not change BOOT_COUNT, but does update this read-only system property. */
    static String session(Context ctx) {
        String wake = "";
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/getprop", "sys.screen.turn_on_time").redirectErrorStream(true).start();
            if (process.waitFor(500, TimeUnit.MILLISECONDS)) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line = reader.readLine();
                    if (line != null) wake = line.trim();
                }
            }
        } catch (Exception unavailable) {
            if (unavailable instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            if (process != null) process.destroy();
        }
        return sessionKey(boot(ctx), wake);
    }

    static boolean dueSession(String session, String handled, String pkg, String self) {
        return session != null && !session.isEmpty() && !session.equals(handled)
                && pkg != null && !pkg.isEmpty() && !pkg.equals(self);
    }

    static boolean launchConfigured(Context ctx) {
        Config config = Config.load(ctx);
        String pkg = config.bootToLiveTv ? config.liveTvPackage : config.openOnStartChoice
                ? ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getString(KEY,
                    ctx.getString(R.string.open_on_start_package).trim()) : "";
        if (pkg == null || pkg.isEmpty() || pkg.equals(ctx.getPackageName())) return false;
        String session = session(ctx);
        android.content.SharedPreferences prefs = ctx.getSharedPreferences(BOOT_PREFS, Context.MODE_PRIVATE);
        if (!dueSession(session, prefs.getString(SESSION_KEY, ""), pkg, ctx.getPackageName())) return false;
        Intent launch = intent(ctx.getPackageManager(), pkg);
        if (launch == null && config.bootToLiveTv && !config.liveTvAction.isEmpty()) {
            Intent action = new Intent(config.liveTvAction).setPackage(pkg);
            if (ctx.getPackageManager().resolveActivity(action, 0) != null) launch = action;
        }
        if (launch == null) {
            Log.w("TvHome.Startup", "Selected app has no enabled launch activity: " + pkg);
            return false;
        }
        try {
            ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            prefs.edit().putInt(BOOT_KEY, boot(ctx)).putString(SESSION_KEY, session).commit();
            Log.i("TvHome.Startup", "Opened " + pkg + " for power session " + session);
            return true;
        } catch (RuntimeException rejected) {
            Log.w("TvHome.Startup", "Startup app could not be opened", rejected);
            return false;
        }
    }

    /** The app's TV entry point, or its phone one; null when it is uninstalled or disabled, so Home stays. */
    static Intent intent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }
}
