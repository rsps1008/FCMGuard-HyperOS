package com.rsps1008.fcmguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Set;

/** Binds the protection controls and concise live status; no debug/test actions are exposed. */
final class NightProtectionUi {
    private final Activity activity;
    private final NightFcmProtection guard;
    private final Runnable serviceChanged;
    private final Switch toggle;
    private final Switch aurogonToggle;
    private boolean binding, lastEnabled, first = true, aurogonActionRunning;
    private final Runnable listener = this::render;

    NightProtectionUi(Activity activity, Runnable serviceChanged) {
        this.activity = activity;
        this.serviceChanged = serviceChanged;
        guard = NightFcmProtection.get(activity);
        guard.refreshLocale();
        toggle = activity.findViewById(R.id.nightSwitch);
        aurogonToggle = activity.findViewById(R.id.aurogonSwitch);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (!binding) { toggle.setEnabled(false); guard.configure(checked); }
        });
        aurogonToggle.setOnCheckedChangeListener((button, checked) -> {
            if (!binding) {
                AurogonProtection.setAutoSyncEnabled(activity, checked);
                serviceChanged.run();
                render();
            }
        });
        activity.findViewById(R.id.aurogonShizukuSetup).setOnClickListener(v ->
                ((MainActivity) activity).setupAurogonPermission());
        activity.findViewById(R.id.aurogonViewSetting).setOnClickListener(v -> showAurogonSetting());
        activity.findViewById(R.id.aurogonClearSetting).setOnClickListener(v -> confirmClearAurogon());
        activity.findViewById(R.id.aurogonSyncNow).setOnClickListener(v -> syncAurogonNow());
        activity.findViewById(R.id.nightCleanup).setOnClickListener(v -> new AlertDialog.Builder(activity)
                .setMessage(R.string.night_cleanup_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> guard.cleanupWithConsent()).show());
    }

    void resume() { guard.listen(listener); guard.check(); render(); }
    void pause() { guard.unlisten(listener); }
    void refresh() { render(); }

    private void confirmClearAurogon() {
        new AlertDialog.Builder(activity)
                .setMessage(R.string.aurogon_clear_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    aurogonActionRunning = true;
                    updateAurogonActionAvailability();
                    ((MainActivity) activity).clearAurogonSetting((success, detail) -> {
                        aurogonActionRunning = false;
                        Toast.makeText(activity, success ? activity.getString(R.string.aurogon_clear_done)
                                : activity.getString(R.string.aurogon_action_failed, detail), Toast.LENGTH_LONG).show();
                        render();
                    });
                }).show();
    }

    private void syncAurogonNow() {
        MainActivity mainActivity = (MainActivity) activity;
        int scrollY = mainActivity.dashboardScrollPosition();
        aurogonActionRunning = true;
        updateAurogonActionAvailability();
        mainActivity.syncAurogonNow((success, detail) -> {
            aurogonActionRunning = false;
            Toast.makeText(activity, success ? activity.getString(R.string.aurogon_sync_done, detail)
                    : activity.getString(R.string.aurogon_action_failed, detail), Toast.LENGTH_LONG).show();
            render();
            mainActivity.restoreDashboardScrollPosition(scrollY);
        });
    }

    private void showAurogonSetting() {
        View button = activity.findViewById(R.id.aurogonViewSetting);
        button.setEnabled(false);
        new Thread(() -> {
            String value;
            try {
                String settings = android.provider.Settings.Global.getString(
                        activity.getContentResolver(), AurogonConfig.KEY);
                Set<String> appNames = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                for (String packageName : AurogonConfig.configuredPackages(settings)) {
                    appNames.add(applicationName(packageName));
                }
                value = appNames.isEmpty() ? activity.getString(R.string.aurogon_uncontrolled_none)
                        : android.text.TextUtils.join("\n", appNames);
            } catch (SecurityException e) {
                value = activity.getString(R.string.aurogon_uncontrolled_error,
                        "SecurityException: " + String.valueOf(e.getMessage()));
            } catch (Throwable e) {
                value = activity.getString(R.string.aurogon_uncontrolled_error,
                        e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
            }
            final String result = value;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                button.setEnabled(true);
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.aurogon_uncontrolled_title)
                        .setMessage(result)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            });
        }, "fcmguard-aurogon-read").start();
    }

    private String applicationName(String packageName) {
        try {
            android.content.pm.ApplicationInfo info = activity.getPackageManager()
                    .getApplicationInfo(packageName, 0);
            CharSequence label = activity.getPackageManager().getApplicationLabel(info);
            if (label != null && !label.toString().trim().isEmpty()) return label.toString().trim();
        } catch (Throwable ignored) {}
        return packageName;
    }

    private static void appendLine(StringBuilder out, String line) {
        if (out.length() > 0) out.append('\n');
        out.append(line);
    }

    private void render() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        binding = true;
        toggle.setChecked(guard.isEnabled());
        toggle.setEnabled(guard.isInitialized());
        aurogonToggle.setChecked(AurogonProtection.isAutoSyncEnabled(activity));
        binding = false;
        ((TextView) activity.findViewById(R.id.nightSummary)).setText(guard.summary());
        ((TextView) activity.findViewById(R.id.aurogonSummary)).setText(AurogonProtection.summary(activity));
        activity.findViewById(R.id.aurogonShizukuSetup).setVisibility(
                !AurogonProtection.hasSecureSettingsPermission(activity) ? View.VISIBLE : View.GONE);
        updateAurogonActionAvailability();
        activity.findViewById(R.id.nightCleanup).setVisibility(guard.cleanupPending() ? View.VISIBLE : View.GONE);
        if (guard.isInitialized() && (first || lastEnabled != guard.isEnabled())) {
            first = false;
            lastEnabled = guard.isEnabled();
            serviceChanged.run();
        }
        ((MainActivity) activity).refreshProtectionStatusCard();
    }

    private void updateAurogonActionAvailability() {
        // The Global-setting actions stay disabled until WRITE_SECURE_SETTINGS is granted.
        boolean enabled = !aurogonActionRunning
                && AurogonProtection.hasSecureSettingsPermission(activity);
        activity.findViewById(R.id.aurogonClearSetting).setEnabled(enabled);
        activity.findViewById(R.id.aurogonSyncNow).setEnabled(enabled);
    }
}
