package com.rsps1008.fcmguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.provider.Settings;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Maintains Xiaomi's Aurogon alarm-cache allowlist with the app UID and WRITE_SETTINGS. */
public final class AlarmAllowListProtection {
    public static final String KEY = "cloud_aurogon_alarm_allow_list";
    private static final String PREFS = "aurogon_alarm_allow_list";
    private static final String MANAGED = "managed_packages";
    private static final String AUTO_SYNC = "auto_sync_enabled";
    private static final Object LOCK = new Object();
    private static final String GMS = "com.google.android.gms";
    private static final List<String> BUILT_INS = Arrays.asList(
            "com.tencent.mm", "com.tencent.mobileqq", "com.xtc.watch");

    private AlarmAllowListProtection() {}

    public static boolean isAutoSyncEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(AUTO_SYNC, false);
    }

    public static void setAutoSyncEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(AUTO_SYNC, enabled).apply();
    }

    public static SyncOutcome syncCurrentAutostartApps(Context context) {
        if (!Settings.System.canWrite(context)) {
            String error = context.getString(R.string.alarm_list_permission);
            failure(context, error);
            return new SyncOutcome(false, 0, error);
        }
        AutostartStatusReader.ScanResult scan = AutostartStatusReader.findEnabledFcmPackages(context);
        if (!scan.readSucceeded) {
            failure(context, scan.error);
            return new SyncOutcome(false, 0, scan.error);
        }

        synchronized (LOCK) {
            String original = Settings.System.getString(context.getContentResolver(), KEY);
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            List<String> previouslyManaged = readPackages(prefs.getString(MANAGED, "[]"));
            List<String> desired = AurogonConfig.includeManagedUnknownPackages(
                    scan.enabledPackages, scan.unknownPackages, previouslyManaged);
            Set<String> desiredSet = new LinkedHashSet<>(desired);
            Set<String> managed = new LinkedHashSet<>();
            Set<String> current = parse(original);
            Set<String> previousSet = new LinkedHashSet<>(previouslyManaged);
            Set<String> alwaysIncluded = new LinkedHashSet<>(BUILT_INS);
            alwaysIncluded.add(GMS);
            Set<String> result = new LinkedHashSet<>(alwaysIncluded);

            // Preserve entries owned by the ROM, the user, or another tool. Remove only
            // stale entries that this feature can prove it previously added.
            for (String packageName : current) {
                if (!previousSet.contains(packageName) || desiredSet.contains(packageName)
                        || alwaysIncluded.contains(packageName)) result.add(packageName);
            }
            for (String packageName : desired) {
                if (!current.contains(packageName) || previousSet.contains(packageName)) {
                    result.add(packageName);
                    if (!alwaysIncluded.contains(packageName)) managed.add(packageName);
                }
            }

            // Rebuild only after checking that the setting has not changed during scan.
            String candidate = TextUtils.join(",", result);
            String beforeWrite = Settings.System.getString(context.getContentResolver(), KEY);
            if (!java.util.Objects.equals(original, beforeWrite)) {
                String error = "Setting changed during synchronization; retry later.";
                failure(context, error);
                return new SyncOutcome(false, desired.size(), error);
            }
            try {
                if (!java.util.Objects.equals(original, candidate)) {
                    boolean wrote = Settings.System.putString(context.getContentResolver(), KEY, candidate);
                    String readBack = Settings.System.getString(context.getContentResolver(), KEY);
                    if (!wrote || !candidate.equals(readBack)) {
                        String error = "Settings write or read-back verification failed.";
                        failure(context, error);
                        return new SyncOutcome(false, desired.size(), error);
                    }
                }
                JSONArray saved = new JSONArray();
                for (String packageName : managed) saved.put(packageName);
                if (!prefs.edit().putString(MANAGED, saved.toString())
                        .putLong("last_sync", System.currentTimeMillis())
                        .putInt("package_count", desired.size()).putBoolean("success", true)
                        .putBoolean("restored_defaults", false)
                        .putString("error", "").commit()) {
                    String error = "Could not save synchronization ownership.";
                    failure(context, error);
                    return new SyncOutcome(false, desired.size(), error);
                }
                return new SyncOutcome(true, desired.size(), "");
            } catch (SecurityException error) {
                String detail = error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
                failure(context, detail);
                return new SyncOutcome(false, desired.size(), detail);
            }
        }
    }

    /** Resets the setting to exactly the ROM's three built-in entries. */
    public static SyncOutcome restoreBuiltIns(Context context) {
        if (!Settings.System.canWrite(context)) {
            String error = context.getString(R.string.alarm_list_permission);
            return new SyncOutcome(false, BUILT_INS.size(), error);
        }
        synchronized (LOCK) {
            try {
                String original = Settings.System.getString(context.getContentResolver(), KEY);
                String candidate = TextUtils.join(",", BUILT_INS);
                String beforeWrite = Settings.System.getString(context.getContentResolver(), KEY);
                if (!java.util.Objects.equals(original, beforeWrite)) {
                    return new SyncOutcome(false, BUILT_INS.size(),
                            "Setting changed while restoring built-in entries; retry later.");
                }
                if (!java.util.Objects.equals(original, candidate)) {
                    boolean wrote = Settings.System.putString(context.getContentResolver(), KEY, candidate);
                    String readBack = Settings.System.getString(context.getContentResolver(), KEY);
                    if (!wrote || !candidate.equals(readBack)) {
                        return new SyncOutcome(false, BUILT_INS.size(),
                                "Settings write or read-back verification failed.");
                    }
                }
                SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                if (!prefs.edit().putString(MANAGED, "[]").putBoolean("restored_defaults", true).commit()) {
                    return new SyncOutcome(false, BUILT_INS.size(),
                            "Could not clear synchronization ownership.");
                }
                return new SyncOutcome(true, BUILT_INS.size(), "");
            } catch (Throwable error) {
                return new SyncOutcome(false, BUILT_INS.size(),
                        error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()));
            }
        }
    }

    public static List<String> currentPackages(Context context) {
        String raw = Settings.System.getString(context.getContentResolver(), KEY);
        Set<String> packages = raw == null ? new LinkedHashSet<>(BUILT_INS) : parse(raw);
        return new ArrayList<>(packages);
    }

    public static List<String> builtInPackages() { return new ArrayList<>(BUILT_INS); }

    public static String summary(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean("restored_defaults", false)) {
            String current = Settings.System.getString(context.getContentResolver(), KEY);
            if (TextUtils.join(",", BUILT_INS).equals(current))
                return context.getString(R.string.alarm_list_restored_status);
        }
        if (!isAutoSyncEnabled(context)) return context.getString(R.string.alarm_list_disabled);
        if (!Settings.System.canWrite(context)) return context.getString(R.string.alarm_list_permission);
        if (prefs.getLong("last_sync", 0L) == 0L) return context.getString(R.string.alarm_list_waiting);
        if (prefs.getBoolean("success", false))
            return context.getString(R.string.alarm_list_synced, prefs.getInt("package_count", 0));
        return context.getString(R.string.alarm_list_failed);
    }

    public static void failure(Context context, String reason) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("last_sync", System.currentTimeMillis()).putBoolean("success", false)
                .putString("error", reason).commit();
    }

    public static final class SyncOutcome {
        public final boolean verified;
        public final int packageCount;
        public final String error;
        SyncOutcome(boolean verified, int packageCount, String error) {
            this.verified = verified;
            this.packageCount = packageCount;
            this.error = error;
        }
    }

    private static Set<String> parse(String raw) {
        Set<String> result = new LinkedHashSet<>();
        if (raw != null && !raw.trim().isEmpty()) {
            for (String item : raw.split(",", -1)) {
                String value = item.trim();
                if (!value.isEmpty()) result.add(value);
            }
        }
        return result;
    }

    private static List<String> readPackages(String json) {
        List<String> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) result.add(array.getString(i));
        } catch (Exception ignored) {}
        return result;
    }
}
