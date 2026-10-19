package com.rsps1008.fcmguard;

/** Binder contract executed only inside the user-authorized Shizuku service. */
interface IShizukuAutostartService {
    /** Returns "OK" or a compact diagnostic safe to show to the caller. */
    String setAutostart(String packageName, boolean enabled) = 1;
    String grantSecureSettings(String packageName) = 2;
    void destroy() = 16777114;
}
