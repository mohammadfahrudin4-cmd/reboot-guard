package com.reboot.guard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Collections;
import java.util.Set;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }

        SharedPreferences prefs =
                context.getSharedPreferences("guard", Context.MODE_PRIVATE);

        // 从旧版迁移：已有风险 App 配置但尚无 guardEnabled 字段时，默认启用。
        if (!prefs.contains("guardEnabled")) {
            Set<String> blocked = prefs.getStringSet(
                    "blockedApps",
                    Collections.emptySet()
            );
            prefs.edit()
                    .putBoolean("guardEnabled", blocked != null && !blocked.isEmpty())
                    .apply();
        }

        if (!prefs.getBoolean("guardEnabled", false)) return;

        Intent service = new Intent(context, GuardService.class);
        service.setAction(GuardService.ACTION_START_AUTO);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(service);
            } else {
                context.startService(service);
            }
        } catch (Exception ignored) {
            // OEM/系统后台限制可能拒绝启动；用户下次打开 App 时 MainActivity 会再次恢复。
        }
    }
}
