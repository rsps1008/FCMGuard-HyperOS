package com.rsps1008.fcmguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Dashboard controls for the Settings.System Aurogon alarm allowlist. */
final class AlarmAllowListUi {
    private final Activity activity;
    private final Runnable serviceChanged;
    private final Switch toggle;
    private boolean binding;
    private boolean running;

    AlarmAllowListUi(Activity activity, Runnable serviceChanged) {
        this.activity = activity;
        this.serviceChanged = serviceChanged;
        toggle = activity.findViewById(R.id.alarmListSwitch);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (binding) return;
            if (checked && !Settings.System.canWrite(activity)) {
                binding = true;
                toggle.setChecked(false);
                binding = false;
                Toast.makeText(activity, R.string.alarm_list_permission, Toast.LENGTH_LONG).show();
                openWriteSettings();
                return;
            }
            AlarmAllowListProtection.setAutoSyncEnabled(activity, checked);
            serviceChanged.run();
            render();
            if (checked) syncNow();
        });
        activity.findViewById(R.id.alarmListViewSetting).setOnClickListener(v -> showCurrentList());
        activity.findViewById(R.id.alarmListSyncNow).setOnClickListener(v -> syncNow());
        activity.findViewById(R.id.alarmListRestoreBuiltIns).setOnClickListener(v -> restoreBuiltIns());
    }

    void render() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        binding = true;
        toggle.setChecked(AlarmAllowListProtection.isAutoSyncEnabled(activity));
        binding = false;
        ((TextView) activity.findViewById(R.id.alarmListSummary))
                .setText(AlarmAllowListProtection.summary(activity));
        activity.findViewById(R.id.alarmListSyncNow).setEnabled(!running && Settings.System.canWrite(activity));
        activity.findViewById(R.id.alarmListRestoreBuiltIns).setEnabled(!running && Settings.System.canWrite(activity));
        ((MainActivity) activity).refreshProtectionStatusCard();
    }

    private void syncNow() {
        if (!Settings.System.canWrite(activity)) {
            Toast.makeText(activity, R.string.alarm_list_permission, Toast.LENGTH_LONG).show();
            openWriteSettings();
            return;
        }
        MainActivity mainActivity = (MainActivity) activity;
        int scrollY = mainActivity.dashboardScrollPosition();
        running = true;
        render();
        mainActivity.restoreDashboardScrollPosition(scrollY);
        new Thread(() -> {
            AlarmAllowListProtection.SyncOutcome result =
                    AlarmAllowListProtection.syncCurrentAutostartApps(activity);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                running = false;
                render();
                mainActivity.restoreDashboardScrollPosition(scrollY);
                Toast.makeText(activity, result.verified
                        ? activity.getString(R.string.alarm_list_sync_done, result.packageCount)
                        : activity.getString(R.string.alarm_list_action_failed, result.error),
                        Toast.LENGTH_LONG).show();
            });
        }, "fcmguard-alarm-list-sync").start();
    }

    private void showCurrentList() {
        View button = activity.findViewById(R.id.alarmListViewSetting);
        button.setEnabled(false);
        new Thread(() -> {
            List<String> packages = AlarmAllowListProtection.currentPackages(activity);
            List<String> labels = new ArrayList<>();
            for (String packageName : packages) labels.add(applicationName(packageName));
            String message = labels.isEmpty() ? activity.getString(R.string.alarm_list_empty)
                    : android.text.TextUtils.join("\n", labels);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                button.setEnabled(true);
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.alarm_list_current_title)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            });
        }, "fcmguard-alarm-list-read").start();
    }

    private void restoreBuiltIns() {
        if (!Settings.System.canWrite(activity)) {
            Toast.makeText(activity, R.string.alarm_list_permission, Toast.LENGTH_LONG).show();
            openWriteSettings();
            return;
        }
        running = true;
        render();
        new Thread(() -> {
            AlarmAllowListProtection.SyncOutcome result = AlarmAllowListProtection.restoreBuiltIns(activity);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                running = false;
                render();
                Toast.makeText(activity, result.verified
                        ? activity.getString(R.string.alarm_list_restore_done)
                        : activity.getString(R.string.alarm_list_action_failed, result.error),
                        Toast.LENGTH_LONG).show();
            });
        }, "fcmguard-alarm-list-restore").start();
    }

    private String applicationName(String packageName) {
        try {
            android.content.pm.ApplicationInfo info = activity.getPackageManager().getApplicationInfo(packageName, 0);
            CharSequence label = activity.getPackageManager().getApplicationLabel(info);
            if (label != null && !label.toString().trim().isEmpty()) return label.toString().trim();
        } catch (Throwable ignored) {}
        return packageName;
    }

    private void openWriteSettings() {
        try {
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + activity.getPackageName())));
        } catch (Throwable ignored) {}
    }
}
