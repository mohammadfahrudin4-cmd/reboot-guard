package com.reboot.guard;

import android.app.*;
import android.app.usage.UsageStatsManager;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

import java.text.Collator;
import java.util.*;

public class MainActivity extends Activity {

    private static final String PWA_URL =
            "https://mohammadfahrudin4-cmd.github.io/reboot-pwa/";

    private LinearLayout root;
    private SharedPreferences prefs;
    private TextView permissionStatus;
    private EditText startTime;
    private EditText endTime;
    private final Map<String, CheckBox> appBoxes = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("guard", MODE_PRIVATE);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissions();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(36));
        root.setBackgroundColor(Color.rgb(245, 247, 251));

        scroll.addView(root);

        addTitle("重启防线 V3");
        addText("原生执行层 · 高风险 App 干预", 15, Color.DKGRAY);

        permissionStatus = addText("", 14, Color.DKGRAY);

        addSection("① 授权");

        addButton("授予「使用情况访问」", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(
                        this,
                        "无法打开使用情况访问设置",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });

        addButton("授予「显示在其他应用上层」", v -> {
            Intent i = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(i);
        });

        if (Build.VERSION.SDK_INT >= 33) {
            addButton("允许防线常驻通知", v -> {
                requestPermissions(
                        new String[]{"android.permission.POST_NOTIFICATIONS"},
                        20
                );
            });
        }

        addSection("② 高风险时段");

        addText(
                "使用 24 小时制，例如 22:30 到 01:00。跨午夜可以正常识别。",
                13,
                Color.GRAY
        );

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);

        startTime = new EditText(this);
        endTime = new EditText(this);

        startTime.setHint("22:30");
        endTime.setHint("01:00");

        startTime.setSingleLine(true);
        endTime.setSingleLine(true);

        startTime.setText(
                prefs.getString("riskStart", "22:30")
        );

        endTime.setText(
                prefs.getString("riskEnd", "01:00")
        );

        timeRow.addView(
                startTime,
                new LinearLayout.LayoutParams(
                        0,
                        dp(52),
                        1
                )
        );

        Space space = new Space(this);

        timeRow.addView(
                space,
                new LinearLayout.LayoutParams(
                        dp(10),
                        1
                )
        );

        timeRow.addView(
                endTime,
                new LinearLayout.LayoutParams(
                        0,
                        dp(52),
                        1
                )
        );

        root.addView(timeRow);

        addSection("③ 选择高风险 App");

        addText(
                "勾选你想让「重启防线」在危险时段主动干预的应用。",
                13,
                Color.GRAY
        );

        loadLauncherApps();

        addSection("④ 防线控制");

        addButton("保存设置", v -> saveSettings());

        addButton("▶ 启动防线", v -> startGuard());

        addButton("■ 停止防线", v -> stopGuard());

        addButton("测试一次全屏干预", v -> {

            if (!Settings.canDrawOverlays(this)) {

                Toast.makeText(
                        this,
                        "请先授予悬浮层权限",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            Intent i = new Intent(
                    this,
                    GuardService.class
            );

            i.setAction(
                    GuardService.ACTION_TEST
            );

            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(i);
            } else {
                startService(i);
            }
        });

        addSection("⑤ 教练层");

        addButton("打开「重启 V2.1」", v -> {

            Intent i = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(PWA_URL)
            );

            startActivity(i);
        });

        addText(
                "说明：V3 Alpha 只负责跨 App 的检测与干预。" +
                        "你的行为记录、视频、复盘仍由 V2.1 保存。",
                13,
                Color.GRAY
        );

        setContentView(scroll);

        refreshPermissions();
    }

    private void loadLauncherApps() {

        Intent launcher = new Intent(
                Intent.ACTION_MAIN,
                null
        );

        launcher.addCategory(
                Intent.CATEGORY_LAUNCHER
        );

        PackageManager pm =
                getPackageManager();

        List<ResolveInfo> infos =
                pm.queryIntentActivities(
                        launcher,
                        0
                );

        Collator collator =
                Collator.getInstance(
                        Locale.CHINA
                );

        infos.sort(
                (a, b) ->
                        collator.compare(
                                String.valueOf(
                                        a.loadLabel(pm)
                                ),
                                String.valueOf(
                                        b.loadLabel(pm)
                                )
                        )
        );

        Set<String> selected =
                prefs.getStringSet(
                        "blockedApps",
                        new HashSet<>()
                );

        for (ResolveInfo info : infos) {

            String pkg =
                    info.activityInfo.packageName;

            if (
                    pkg.equals(
                            getPackageName()
                    )
                            ||
                    appBoxes.containsKey(pkg)
            ) {
                continue;
            }

            String label =
                    String.valueOf(
                            info.loadLabel(pm)
                    );

            CheckBox cb =
                    new CheckBox(this);

            cb.setText(
                    label + "\n" + pkg
            );

            cb.setTextSize(14);

            cb.setPadding(
                    dp(8),
                    dp(8),
                    dp(8),
                    dp(8)
            );

            cb.setChecked(
                    selected.contains(pkg)
            );

            root.addView(
                    cb,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    )
            );

            appBoxes.put(
                    pkg,
                    cb
            );
        }
    }

    private void saveSettings() {

        String st =
                normalizeTime(
                        startTime.getText().toString(),
                        "22:30"
                );

        String et =
                normalizeTime(
                        endTime.getText().toString(),
                        "01:00"
                );

        Set<String> selected =
                new HashSet<>();

        for (
                Map.Entry<String, CheckBox> e :
                        appBoxes.entrySet()
        ) {

            if (
                    e.getValue().isChecked()
            ) {
                selected.add(
                        e.getKey()
                );
            }
        }

        prefs.edit()
                .putString(
                        "riskStart",
                        st
                )
                .putString(
                        "riskEnd",
                        et
                )
                .putStringSet(
                        "blockedApps",
                        selected
                )
                .apply();

        startTime.setText(st);
        endTime.setText(et);

        Toast.makeText(
                this,
                "已保存：" +
                        selected.size() +
                        " 个高风险 App",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void startGuard() {

        saveSettings();

        if (!hasUsageAccess()) {

            Toast.makeText(
                    this,
                    "还没有授予「使用情况访问」",
                    Toast.LENGTH_LONG
            ).show();

            startActivity(
                    new Intent(
                            Settings.ACTION_USAGE_ACCESS_SETTINGS
                    )
            );

            return;
        }

        if (
                !Settings.canDrawOverlays(this)
        ) {

            Toast.makeText(
                    this,
                    "还没有授予悬浮层权限",
                    Toast.LENGTH_LONG
            ).show();

            startActivity(
                    new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse(
                                    "package:" +
                                            getPackageName()
                            )
                    )
            );

            return;
        }

        Intent i =
                new Intent(
                        this,
                        GuardService.class
                );

        i.setAction(
                GuardService.ACTION_START
        );

        if (
                Build.VERSION.SDK_INT >= 26
        ) {
            startForegroundService(i);
        } else {
            startService(i);
        }

        Toast.makeText(
                this,
                "防线已启动",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void stopGuard() {

        Intent i =
                new Intent(
                        this,
                        GuardService.class
                );

        i.setAction(
                GuardService.ACTION_STOP
        );

        startService(i);

        Toast.makeText(
                this,
                "防线已停止",
                Toast.LENGTH_SHORT
        ).show();
    }

    private boolean hasUsageAccess() {

        AppOpsManager appOps =
                (AppOpsManager)
                        getSystemService(
                                APP_OPS_SERVICE
                        );

        int mode =
                appOps.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        getPackageName()
                );

        return mode ==
                AppOpsManager.MODE_ALLOWED;
    }

    private void refreshPermissions() {

        if (
                permissionStatus == null
        ) {
            return;
        }

        String usage =
                hasUsageAccess()
                        ? "✅ 使用情况访问"
                        : "❌ 使用情况访问";

        String overlay =
                Settings.canDrawOverlays(this)
                        ? "✅ 悬浮层"
                        : "❌ 悬浮层";

        permissionStatus.setText(
                usage + "\n" + overlay
        );
    }

    private String normalizeTime(
            String s,
            String fallback
    ) {

        try {

            s = s.trim();

            String[] p =
                    s.split(":");

            int h =
                    Integer.parseInt(
                            p[0]
                    );

            int m =
                    Integer.parseInt(
                            p[1]
                    );

            if (
                    h < 0 ||
                    h > 23 ||
                    m < 0 ||
                    m > 59
            ) {
                return fallback;
            }

            return String.format(
                    Locale.US,
                    "%02d:%02d",
                    h,
                    m
            );

        } catch (Exception e) {

            return fallback;
        }
    }

    private void addSection(
            String title
    ) {

        TextView tv =
                addText(
                        title,
                        18,
                        Color.rgb(
                                17,
                                24,
                                39
                        )
                );

        LinearLayout.LayoutParams lp =
                (LinearLayout.LayoutParams)
                        tv.getLayoutParams();

        lp.topMargin =
                dp(24);

        tv.setLayoutParams(lp);
    }

    private void addTitle(
            String title
    ) {

        TextView tv =
                addText(
                        title,
                        30,
                        Color.rgb(
                                17,
                                24,
                                39
                        )
                );

        tv.setTypeface(
                null,
                1
        );
    }

    private TextView addText(
            String text,
            int sp,
            int color
    ) {

        TextView tv =
                new TextView(this);

        tv.setText(text);

        tv.setTextSize(sp);

        tv.setTextColor(color);

        tv.setPadding(
                0,
                dp(4),
                0,
                dp(8)
        );

        root.addView(
                tv,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        return tv;
    }

    private void addButton(
            String text,
            View.OnClickListener listener
    ) {

        Button b =
                new Button(this);

        b.setText(text);

        b.setAllCaps(false);

        b.setTextSize(15);

        b.setOnClickListener(
                listener
        );

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(54)
                );

        lp.topMargin =
                dp(7);

        root.addView(
                b,
                lp
        );
    }

    private int dp(int n) {

        return Math.round(
                n *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }
}
