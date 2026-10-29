package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Recheck services after boot and after an APK update without opening a screen. */
public final class HomeRecoveryReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())
                || "mitv.action.STR_BOOT_COMPLETED".equals(intent.getAction())) {
            if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) OpenOnStart.skipThisBoot(context);
            StatusOverlayService.ensureStarted(context);
            if (Config.load(context).restoreKeyServices) HomeRecoveryJob.schedule(context, true);
        }
    }
}
