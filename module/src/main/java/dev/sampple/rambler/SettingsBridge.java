package dev.sampple.rambler;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** Only instantiated in the module APK process, never by the IME. */
public final class SettingsBridge extends Application {
    private static volatile XposedService service;
    private static final java.util.List<Runnable> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());

    /** Runs on the main thread whenever the LSPosed service binds or dies. */
    public static void addListener(Runnable r) { listeners.add(r); }
    public static void removeListener(Runnable r) { listeners.remove(r); }
    public static boolean connected() { return service != null; }
    private static void notifyListeners() { for (Runnable r : listeners) main.post(r); }
    @Override public void onCreate() {
        super.onCreate();
        try {
            XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
                @Override public void onServiceBind(XposedService value) { service = value; notifyListeners(); }
                @Override public void onServiceDied(XposedService value) { if (service == value) { service = null; notifyListeners(); } }
            });
        } catch (Throwable error) { Log.e("RamblerSettings", "register service", error); }
    }
    public static SharedPreferences remote(String group) {
        XposedService current = service;
        if (current == null) throw new IllegalStateException(I18n.t("not_connected_to_lsposed_enable_the_module_then_reopen_set"));
        if ((current.getFrameworkProperties() & XposedService.PROP_CAP_REMOTE) == 0)
            throw new IllegalStateException(I18n.t("this_framework_doesn_t_support_remote_settings"));
        return current.getRemotePreferences(group);
    }
    public static String frameworkStatus() {
        XposedService current = service;
        if (current == null) return I18n.t("lsposed_disconnected_check_module_status");
        boolean scoped = current.getScope().contains("com.samsung.android.honeyboard");
        return current.getFrameworkName() + " " + current.getFrameworkVersion()
                + (scoped ? I18n.t("samsung_keyboard_in_scope") : I18n.t("samsung_keyboard_not_in_scope"));
    }
    static SharedPreferences remote() { return remote(VoiceOptions.GROUP); }
}
