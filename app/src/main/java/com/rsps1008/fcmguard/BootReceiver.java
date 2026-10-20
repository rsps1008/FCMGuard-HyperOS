package com.rsps1008.fcmguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        PendingResult pending = goAsync();
        NightFcmProtection night = NightFcmProtection.get(context);
        night.whenReady(() -> {
            try {
                if (!SettingsGuard.isProtectionEnabled(context) && !night.isEnabled()
                        && !(AurogonProtection.isAutoSyncEnabled(context)
                        && AurogonProtection.hasSecureSettingsPermission(context))
                        && !(AlarmAllowListProtection.isAutoSyncEnabled(context)
                        && android.provider.Settings.System.canWrite(context))) return;
                Intent service = new Intent(context, GuardService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        SettingsGuard.usePersistentNotification(context)) {
                    context.startForegroundService(service);
                } else {
                    context.startService(service);
                }
            } catch (Throwable error) {
                night.serviceFailure(error);
            } finally {
                pending.finish();
            }
        });
    }
}
