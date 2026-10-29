package com.gathin.tvhome;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/** Repairs recorded key services even when the stock launcher is the visible Home. */
public final class HomeRecoveryJob extends JobService {
    private static final int PERIODIC = 3101;
    private static final int STARTUP = 3102;
    private final Handler handler = new Handler(Looper.getMainLooper());

    static void schedule(Context context, boolean startup) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        ComponentName component = new ComponentName(context, HomeRecoveryJob.class);
        if (scheduler.getPendingJob(PERIODIC) == null) {
            scheduler.schedule(new JobInfo.Builder(PERIODIC, component)
                    .setPeriodic(15 * 60 * 1000L).setPersisted(true).build());
        }
        if (startup) {
            scheduler.schedule(new JobInfo.Builder(STARTUP, component)
                    .setMinimumLatency(1000).setOverrideDeadline(10000).build());
        }
    }

    @Override public boolean onStartJob(JobParameters params) {
        if (!Config.load(this).restoreKeyServices) return false;
        ServiceRepair.restore(this);
        OpenOnStart.launchConfigured(this);
        // ServiceRepair's background rebind takes two seconds. Keep the job alive until it finishes.
        handler.postDelayed(() -> jobFinished(params, false), 5000);
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        handler.removeCallbacksAndMessages(null);
        return true;
    }
}
