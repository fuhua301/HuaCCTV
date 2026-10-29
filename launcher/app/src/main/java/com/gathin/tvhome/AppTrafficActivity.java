package com.gathin.tvhome;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Per-app rates from Android 11's UID counters. Sampling exists only while this window is visible. */
public final class AppTrafficActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Integer,Row> rows = new HashMap<>();
    private LinearLayout list;
    private TextView status;
    private ScrollView scroll;
    private ScheduledExecutorService worker;
    private volatile Process command;
    private volatile boolean active;
    private Map<Integer,long[]> previous;
    private long previousAt;
    private static final class Row {
        int uid; String label; LinearLayout view; TextView rx, tx; long traffic;
    }
    private int dp(float n) { return ControlCenterUi.dp(this,n); }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setFinishOnTouchOutside(true);
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16),dp(15),dp(16),dp(12)); FrostedPanel.fromPending(this,panel);
        TextView brand = ControlCenterUi.label(this,"HuaCCTV",16); brand.setGravity(Gravity.CENTER);
        panel.addView(brand,new LinearLayout.LayoutParams(-1,dp(26)));
        LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = ControlCenterUi.label(this,"APP 实时网速",19);
        heading.addView(title,new LinearLayout.LayoutParams(0,dp(40),1)); title.setGravity(Gravity.CENTER_VERTICAL);
        TextView close = ControlCenterUi.row(this,"关闭","",this::finish);
        heading.addView(close,new LinearLayout.LayoutParams(dp(60),dp(36))); panel.addView(heading);
        status = ControlCenterUi.label(this,"正在读取系统统计…",11); status.setTextColor(0xCCFFFFFF);
        status.setPadding(0,dp(10),0,dp(12)); panel.addView(status);
        LinearLayout columns = new LinearLayout(this); columns.setPadding(dp(8),0,dp(8),dp(6));
        TextView app = ControlCenterUi.label(this,"应用",11); columns.addView(app,new LinearLayout.LayoutParams(0,-2,1));
        TextView down = ControlCenterUi.label(this,"↓ 下载",11); down.setGravity(Gravity.RIGHT);
        columns.addView(down,new LinearLayout.LayoutParams(dp(88),-2));
        TextView up = ControlCenterUi.label(this,"↑ 上传",11); up.setGravity(Gravity.RIGHT);
        columns.addView(up,new LinearLayout.LayoutParams(dp(88),-2)); panel.addView(columns);
        scroll = new ScrollView(this); list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list);
        int room = (int)(getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density)-215;
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,dp(Math.max(110,Math.min(270,room)))));
        TextView footer = ControlCenterUi.label(this,"每 2 秒采样 · 上下键查看 · 返回键关闭",10);
        footer.setTextColor(0xAAFFFFFF); footer.setPadding(0,dp(12),0,0); panel.addView(footer);
        setContentView(panel);
        Window window = getWindow(); window.setBackgroundDrawableResource(android.R.color.transparent);
        WindowManager.LayoutParams attributes = window.getAttributes(); attributes.width=dp(380); attributes.height=-2;
        attributes.gravity=Gravity.TOP|Gravity.RIGHT; attributes.x=dp(26); attributes.y=dp(20);
        attributes.flags|=WindowManager.LayoutParams.FLAG_DIM_BEHIND; attributes.dimAmount=.35f; window.setAttributes(attributes);
        close.requestFocus();
    }
    @Override protected void onResume() {
        super.onResume(); active=true; previous=null; previousAt=0;
        worker=Executors.newSingleThreadScheduledExecutor();
        worker.scheduleWithFixedDelay(this::sample,0,2,TimeUnit.SECONDS);
    }
    @Override protected void onPause() {
        active=false; if(worker!=null)worker.shutdownNow(); Process running=command; if(running!=null)running.destroy();
        super.onPause();
    }
    private void sample() {
        try {
            if(checkSelfPermission("android.permission.DUMP")!=PackageManager.PERMISSION_GRANTED)
                throw new IOException("系统统计权限未启用");
            // API 30 INetworkStatsService transaction 2 = openSessionForUsageStats.
            // Flags ON_OPEN|FORCE request a fresh snapshot instead of a 15-minute cached history.
            if(Build.VERSION.SDK_INT!=30) throw new IOException("此机型暂不支持 APP 实时统计");
            String poll=run("/system/bin/service","call","netstats","2","i32","3","s16",getPackageName());
            if(!poll.contains("0x00000000: 00000000")) throw new IOException("系统拒绝更新 APP 网络统计");
            Map<Integer,long[]> totals=AppTrafficStats.parse(run("/system/bin/dumpsys","-t","3","netstats","--uid"));
            long now=SystemClock.elapsedRealtime();
            Map<Integer,long[]> rates=new HashMap<>();
            for(Map.Entry<Integer,long[]> entry:totals.entrySet()) {
                long[] old=previous==null?null:previous.get(entry.getKey()), value=entry.getValue();
                rates.put(entry.getKey(),old==null?new long[2]:new long[]{AppTrafficStats.rate(old[0],value[0],now-previousAt),AppTrafficStats.rate(old[1],value[1],now-previousAt)});
            }
            boolean warm=previous!=null; previous=totals; previousAt=now;
            List<ApplicationInfo> apps=rows.isEmpty()?getPackageManager().getInstalledApplications(0):null;
            main.post(()->{if(active){if(apps!=null&&rows.isEmpty())createRows(apps);update(rates,warm);}});
        } catch(Exception failure) {
            previous=null; previousAt=0;
            main.post(()->{if(active){status.setText("无法读取："+failure.getMessage());for(Row row:rows.values()){row.rx.setText("—");row.tx.setText("—");}}});
        }
    }
    private String run(String... args) throws Exception {
        if(!active||Thread.currentThread().isInterrupted())throw new InterruptedException();
        Process process=new ProcessBuilder(args).redirectErrorStream(true).start(); command=process;
        Runnable timeout=process::destroy; main.postDelayed(timeout,4500);
        try(BufferedReader in=new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            StringBuilder out=new StringBuilder();String line;
            while((line=in.readLine())!=null){out.append(line).append('\n');if(out.length()>2_000_000)throw new IOException("统计数据过大");}
            if(process.waitFor()!=0)throw new IOException("系统统计服务暂不可用"); return out.toString();
        } finally {main.removeCallbacks(timeout);process.destroy();command=null;}
    }
    private void createRows(List<ApplicationInfo> apps) {
        PackageManager pm=getPackageManager();Map<Integer,List<String>> labels=new HashMap<>();
        for(ApplicationInfo app:apps) {
            if(!app.enabled||pm.checkPermission("android.permission.INTERNET",app.packageName)!=PackageManager.PERMISSION_GRANTED)continue;
            labels.computeIfAbsent(app.uid,k->new ArrayList<>()).add(pm.getApplicationLabel(app).toString());
        }
        for(Map.Entry<Integer,List<String>> entry:labels.entrySet()) {
            Row row=new Row();row.uid=entry.getKey();List<String> names=entry.getValue(); Collections.sort(names);
            row.label=row.uid==1000?"Android 系统（共享 UID）":names.get(0)+(names.size()>1?" 等（共享 UID）":"");
            row.view=new LinearLayout(this);row.view.setGravity(Gravity.CENTER_VERTICAL);row.view.setPadding(dp(8),0,dp(8),0);
            row.view.setBackground(ControlCenterUi.rowBackground(this));row.view.setFocusable(true);
            TextView name=ControlCenterUi.label(this,row.label,12);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.view.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            row.rx=ControlCenterUi.label(this,"采样中",12);row.rx.setGravity(Gravity.RIGHT);
            row.tx=ControlCenterUi.label(this,"采样中",12);row.tx.setGravity(Gravity.RIGHT);
            row.view.addView(row.rx,new LinearLayout.LayoutParams(dp(88),-2));row.view.addView(row.tx,new LinearLayout.LayoutParams(dp(88),-2));
            row.view.setOnFocusChangeListener((v,focus)->{int color=focus?0xFF20252D:Color.WHITE;name.setTextColor(color);row.rx.setTextColor(color);row.tx.setTextColor(color);});
            rows.put(row.uid,row);
        }
    }
    private void update(Map<Integer,long[]> rates,boolean warm) {
        int moving=0;
        for(Row row:rows.values()) {
            long[] value=rates.get(row.uid);if(value==null)value=new long[2];row.traffic=value[0]+value[1];
            row.rx.setText(warm?AppTrafficStats.speed(value[0]):"采样中");row.tx.setText(warm?AppTrafficStats.speed(value[1]):"采样中");
            if(row.traffic>0)moving++;
        }
        status.setText(warm?"正在联网 "+moving+" 项 · 共 "+rows.size()+" 项（共享 UID 合并）":"首次采样中，约 2 秒后显示速度…");
        if(!scroll.hasFocus()) {
            List<Row> sorted=new ArrayList<>(rows.values());sorted.sort(Comparator.comparingLong((Row row)->row.traffic).reversed().thenComparing(row->row.label));
            list.removeAllViews();for(Row row:sorted){LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,dp(44));size.bottomMargin=dp(4);list.addView(row.view,size);}
        }
    }
}
