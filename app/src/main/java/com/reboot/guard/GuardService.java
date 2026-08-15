package com.reboot.guard;

import android.app.*;
import android.app.usage.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

import java.util.*;

public class GuardService extends Service {

    public static final String ACTION_START = "reboot.guard.START";
    public static final String ACTION_STOP = "reboot.guard.STOP";
    public static final String ACTION_TEST = "reboot.guard.TEST";
    public static final String ACTION_START_AUTO = "reboot.guard.START_AUTO";

    private static final String CHANNEL = "reboot_guard";
    private static final int NOTIF_ID = 3021;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private WindowManager wm;
    private View overlay;

    private String lastSeenPackage = "";
    private long graceUntil = 0L;
    private int bypassCount = 0;
    private long lastRearmToken = 0L;

    private final Runnable monitor = new Runnable() {
        @Override
        public void run() {
            try {
                checkForeground();
            } catch (Throwable ignored) {
            }

            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        prefs = getSharedPreferences("guard", MODE_PRIVATE);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        bypassCount =
                prefs.getInt(
                        "bypassCount",
                        0
                );

        lastRearmToken = prefs.getLong("rearmToken", 0L);

        createChannel();
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        String action =
                intent == null
                        ? ACTION_START
                        : intent.getAction();

        if (ACTION_STOP.equals(action)) {

            prefs.edit().putBoolean("guardEnabled", false).apply();
            removeOverlay();
            handler.removeCallbacks(monitor);

            stopForeground(true);
            stopSelf();

            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(action) || ACTION_START_AUTO.equals(action)) {
            prefs.edit().putBoolean("guardEnabled", true).apply();
        }

        startForeground(
                NOTIF_ID,
                buildNotification()
        );

        if (ACTION_TEST.equals(action)) {
            showOverlay(
                    "测试防线",
                    true
            );
        }

        handler.removeCallbacks(monitor);
        handler.post(monitor);

        return START_STICKY;
    }

    private void checkForeground() {

        long token = prefs.getLong("rearmToken", 0L);
        if (token != lastRearmToken) {
            lastRearmToken = token;
            lastSeenPackage = "";
            graceUntil = 0L;
        }

        if (overlay != null) {
            return;
        }

        if (!Settings.canDrawOverlays(this)) {
            return;
        }

        String pkg =
                getForegroundPackage();

        if (pkg == null || pkg.isEmpty()) {
            return;
        }

        boolean changed =
                !pkg.equals(
                        lastSeenPackage
                );

        lastSeenPackage =
                pkg;

        if (
                pkg.equals(
                        getPackageName()
                )
        ) {
            return;
        }

        Set<String> blocked =
                prefs.getStringSet(
                        "blockedApps",
                        Collections.emptySet()
                );

        if (
                !blocked.contains(pkg)
        ) {
            return;
        }

        if (
                !withinRiskWindow()
        ) {
            return;
        }

        long now =
                System.currentTimeMillis();

        if (
                now < graceUntil
        ) {
            return;
        }

        /*
         * 重新进入风险 App 会拦；
         * 即使一直停留在风险 App，
         * “暂时继续”的短宽限结束后也会再拦。
         */
        if (
                changed
                        ||
                now >= graceUntil
        ) {
            showOverlay(
                    pkg,
                    false
            );
        }
    }

    private String getForegroundPackage() {

        UsageStatsManager usm =
                (UsageStatsManager)
                        getSystemService(
                                USAGE_STATS_SERVICE
                        );

        long end =
                System.currentTimeMillis();

        long begin =
                end - 6000;

        UsageEvents events =
                usm.queryEvents(
                        begin,
                        end
                );

        if (events == null) {
            return null;
        }

        UsageEvents.Event event =
                new UsageEvents.Event();

        String current =
                null;

        long currentTime =
                0;

        while (
                events.hasNextEvent()
        ) {

            events.getNextEvent(event);

            int type =
                    event.getEventType();

            if (
                    type == UsageEvents.Event.ACTIVITY_RESUMED
                            ||
                    type == UsageEvents.Event.MOVE_TO_FOREGROUND
            ) {

                if (
                        event.getTimeStamp()
                                >= currentTime
                ) {

                    currentTime =
                            event.getTimeStamp();

                    current =
                            event.getPackageName();
                }
            }
        }

        return current;
    }

    private boolean withinRiskWindow() {

        String start =
                prefs.getString(
                        "riskStart",
                        "22:30"
                );

        String end =
                prefs.getString(
                        "riskEnd",
                        "01:00"
                );

        int s =
                minutes(
                        start,
                        22 * 60 + 30
                );

        int e =
                minutes(
                        end,
                        60
                );

        Calendar c =
                Calendar.getInstance();

        int now =
                c.get(Calendar.HOUR_OF_DAY)
                        * 60
                        +
                c.get(Calendar.MINUTE);

        if (
                s == e
        ) {
            return false;
        }

        return s < e
                ? now >= s
                    && now < e
                : now >= s
                    || now < e;
    }

    private int minutes(
            String t,
            int fallback
    ) {

        try {

            String[] p =
                    t.split(":");

            int h =
                    Integer.parseInt(
                            p[0]
                    );

            int m =
                    Integer.parseInt(
                            p[1]
                    );

            return h * 60 + m;

        } catch (
                Exception e
        ) {

            return fallback;
        }
    }

    private void showOverlay(
            String source,
            boolean test
    ) {

        if (
                overlay != null
                        ||
                !Settings.canDrawOverlays(this)
        ) {
            return;
        }

        LinearLayout panel =
                new LinearLayout(this);

        panel.setOrientation(
                LinearLayout.VERTICAL
        );

        panel.setGravity(
                Gravity.CENTER_HORIZONTAL
        );

        panel.setPadding(
                dp(24),
                dp(42),
                dp(24),
                dp(24)
        );

        panel.setBackgroundColor(
                Color.rgb(
                        8,
                        16,
                        29
                )
        );

        TextView tag =
                text(
                        "重启防线启动",
                        14,
                        Color.rgb(
                                148,
                                163,
                                184
                        )
                );

        panel.addView(tag);

        TextView title =
                text(
                        test
                                ? "这是一次测试"
                                : "现在不要继续滑进去",
                        30,
                        Color.WHITE
                );

        title.setGravity(
                Gravity.CENTER
        );

        title.setTypeface(
                null,
                1
        );

        panel.addView(title);

        TextView desc =
                text(
                        test
                                ? "确认全屏干预可以正常覆盖其他 App。"
                                : "你进入了设定的高风险 App。\n先把决定延迟，而不是继续和冲动谈判。",
                        16,
                        Color.rgb(
                                203,
                                213,
                                225
                        )
                );

        desc.setGravity(
                Gravity.CENTER
        );

        panel.addView(desc);

        TextView sourceText =
                text(
                        "触发来源："
                                + source,
                        13,
                        Color.rgb(
                                148,
                                163,
                                184
                        )
                );

        sourceText.setGravity(
                Gravity.CENTER
        );

        panel.addView(
                sourceText
        );

        int waitSec =
                test
                        ? 5
                        : Math.min(
                                120,
                                30
                                        +
                                bypassCount
                                        * 20
                        );

        TextView countdown =
                text(
                        format(waitSec),
                        58,
                        Color.WHITE
                );

        countdown.setGravity(
                Gravity.CENTER
        );

        countdown.setTypeface(
                null,
                1
        );

        LinearLayout.LayoutParams countLp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        countLp.topMargin =
                dp(30);

        countLp.bottomMargin =
                dp(24);

        panel.addView(
                countdown,
                countLp
        );

        Button leave =
                darkButton(
                        "离开风险 App"
                );

        panel.addView(
                leave,
                buttonLp()
        );

        Button coach =
                darkButton(
                        "进入「重启」干预"
                );

        panel.addView(
                coach,
                buttonLp()
        );

        Button continueBtn =
                darkButton(
                        "倒计时结束后才能继续"
                );

        continueBtn.setEnabled(
                false
        );

        panel.addView(
                continueBtn,
                buttonLp()
        );

        TextView note =
                text(
                        "离开后再次打开风险 App，防线会重新触发。",
                        13,
                        Color.rgb(
                                148,
                                163,
                                184
                        )
                );

        note.setGravity(
                Gravity.CENTER
        );

        panel.addView(
                note
        );

        leave.setOnClickListener(
                v -> {

                    removeOverlay();

                    graceUntil =
                            System.currentTimeMillis()
                                    + 1500;

                    lastSeenPackage =
                            "";

                    Intent home =
                            new Intent(
                                    Intent.ACTION_MAIN
                            );

                    home.addCategory(
                            Intent.CATEGORY_HOME
                    );

                    home.setFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                    );

                    startActivity(
                            home
                    );
                }
        );

        coach.setOnClickListener(
                v -> {

                    removeOverlay();

                    graceUntil = 0L;
                    lastSeenPackage = "";
                    long token = System.currentTimeMillis();
                    lastRearmToken = token;
                    prefs.edit().putLong("rearmToken", token).apply();

                    Intent i =
                            new Intent(
                                    this,
                                    CoachActivity.class
                            );

                    i.setFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                    );

                    startActivity(i);
                }
        );

        final int[] remain =
                {waitSec};

        Runnable tick =
                new Runnable() {

                    @Override
                    public void run() {

                        if (
                                overlay == null
                        ) {
                            return;
                        }

                        remain[0]--;

                        countdown.setText(
                                format(
                                        Math.max(
                                                0,
                                                remain[0]
                                        )
                                )
                        );

                        if (
                                remain[0]
                                        <= 0
                        ) {

                            continueBtn.setEnabled(
                                    true
                            );

                            continueBtn.setText(
                                    test
                                            ? "关闭测试"
                                            : "暂时继续 20 秒"
                            );

                            return;
                        }

                        handler.postDelayed(
                                this,
                                1000
                        );
                    }
                };

        handler.postDelayed(
                tick,
                1000
        );

        continueBtn.setOnClickListener(
                v -> {

                    removeOverlay();

                    if (!test) {

                        bypassCount++;

                        prefs.edit()
                                .putInt(
                                        "bypassCount",
                                        bypassCount
                                )
                                .apply();

                        graceUntil =
                                System.currentTimeMillis()
                                        + 20_000;

                        lastSeenPackage =
                                "";
                    }
                }
        );

        int type =
                Build.VERSION.SDK_INT
                        >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams lp =
                new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        type,
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT
                );

        lp.gravity =
                Gravity.TOP
                        |
                Gravity.START;

        overlay =
                panel;

        wm.addView(
                overlay,
                lp
        );
    }

    private void removeOverlay() {

        if (
                overlay != null
        ) {

            try {

                wm.removeView(
                        overlay
                );

            } catch (
                    Exception ignored
            ) {
            }

            overlay =
                    null;
        }
    }

    private Notification buildNotification() {

        Intent open =
                new Intent(
                        this,
                        MainActivity.class
                );

        PendingIntent pi =
                PendingIntent.getActivity(
                        this,
                        0,
                        open,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                |
                        PendingIntent.FLAG_IMMUTABLE
                );

        return new Notification.Builder(
                this,
                CHANNEL
        )
                .setSmallIcon(
                        android.R.drawable.ic_lock_idle_alarm
                )
                .setContentTitle(
                        "重启防线运行中"
                )
                .setContentText(
                        "自动防线常驻中 · 正在监测高风险 App 与时段"
                )
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void createChannel() {

        if (
                Build.VERSION.SDK_INT
                        >= 26
        ) {

            NotificationChannel c =
                    new NotificationChannel(
                            CHANNEL,
                            "重启防线",
                            NotificationManager.IMPORTANCE_LOW
                    );

            c.setDescription(
                    "显示个人防线的常驻状态"
            );

            getSystemService(
                    NotificationManager.class
            )
                    .createNotificationChannel(
                            c
                    );
        }
    }

    private TextView text(
            String s,
            int sp,
            int color
    ) {

        TextView t =
                new TextView(this);

        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);

        t.setPadding(
                0,
                dp(8),
                0,
                dp(8)
        );

        return t;
    }

    private Button darkButton(
            String s
    ) {

        Button b =
                new Button(this);

        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(16);

        return b;
    }

    private LinearLayout.LayoutParams buttonLp() {

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(56)
                );

        lp.topMargin =
                dp(8);

        return lp;
    }

    private String format(
            int sec
    ) {

        return String.format(
                Locale.US,
                "%02d:%02d",
                sec / 60,
                sec % 60
        );
    }

    private int dp(
            int n
    ) {

        return Math.round(
                n
                        *
                getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    @Override
    public void onDestroy() {

        handler.removeCallbacksAndMessages(
                null
        );

        removeOverlay();

        super.onDestroy();
    }

    @Override
    public IBinder onBind(
            Intent intent
    ) {

        return null;
    }
}
