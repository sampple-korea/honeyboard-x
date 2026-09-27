package dev.sampple.rambler.translation;

import dev.sampple.rambler.I18n;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** TranslationViewModel 5.9.40.75 hook; no third-party classes linked at compile time. */
public final class TranslationHook {
    private final XposedModule module;
    private final Map<Object, Job> jobs = Collections.synchronizedMap(new WeakHashMap<>());
    private final ExecutorService workers = Executors.newCachedThreadPool(r -> { Thread t = new Thread(r, "HoneyBoardX-translate"); t.setDaemon(true); return t; });
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile TranslationOptions options = TranslationOptions.defaults();
    private android.content.SharedPreferences remote;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> reload();
    private XposedInterface.Invoker original;
    public TranslationHook(XposedModule module) { this.module = module; }
    private void reload() {
        try { options = TranslationOptions.read(remote); } catch (Throwable t) { Log.w("HoneyBoardX", "Translation settings unavailable", t); }
    }
    public void install(ClassLoader loader) {
        try {
            try {
                remote = module.getRemotePreferences(TranslationOptions.GROUP);
                reload(); remote.registerOnSharedPreferenceChangeListener(listener);
            } catch (Throwable error) { Log.w("HoneyBoardX", "Remote translation settings unavailable", error); }
            Class<?> vm = Class.forName("com.samsung.android.honeyboard.textboard.translate.viewmodel.TranslationViewModel", false, loader);
            Method internal = null;
            for (Method m : vm.getDeclaredMethods()) if (m.getName().equals("internalTranslate") && m.getParameterCount() == 4
                    && m.getParameterTypes()[0] == String.class && m.getParameterTypes()[1] == String.class
                    && m.getParameterTypes()[2] == String.class && m.getParameterTypes()[3].getName().equals("kotlin.jvm.functions.Function1")) internal = m;
            if (internal == null) return;
            internal.setAccessible(true);
            original = module.getInvoker(internal).setType(XposedInterface.Invoker.Type.ORIGIN);
            module.hook(internal).setId("honeyboardx:translation").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> intercept(chain));
            Class<?> base = vm.getSuperclass();
            for (Method method : base.getDeclaredMethods()) {
                if (method.getName().equals("onTextChanged") && method.getParameterCount() == 4
                        || method.getName().equals("setSourceLanguage$HoneyBoard_textBoard_globalRelease") && method.getParameterCount() == 2
                        || method.getName().equals("setTargetLanguage$HoneyBoard_textBoard_globalRelease") && method.getParameterCount() == 3) observeCancel(method);
            }
            for (Method method : vm.getDeclaredMethods()) {
                if (method.getName().equals("clear") && method.getParameterCount() == 0) observeCancel(method);
                if (method.getName().equals("doRunning") && method.getParameterCount() == 2) {
                    method.setAccessible(true);
                    module.hook(method).setId("honeyboardx:translation-enter").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                try {
                                    if (options.enabled && !Boolean.TRUE.equals(chain.getArg(0))) {
                                        Context context = context(chain.getThisObject());
                                        if (context != null) {
                                            if (Boolean.TRUE.equals(chain.getArg(1))) cancel(chain.getThisObject(), context);
                                            return null; // B: enter-only; no native translation on Enter.
                                        }
                                    }
                                } catch (Throwable t) { Log.w("HoneyBoardX", "Translation enter", t); }
                                return chain.proceed();
                            });
                }
            }
            try { installSettingsEntry(loader); } catch (Throwable t) { Log.w("HoneyBoardX", "Settings entry skipped", t); }
            Log.i("HoneyBoardX", "Translation hooks installed");
        } catch (Throwable t) { Log.e("HoneyBoardX", "Translation hooks skipped", t); }
    }
    /** Add one entry to the native preference screen without linking AndroidX at build time. */
    private void installSettingsEntry(ClassLoader loader) throws Exception {
        Class<?> fragment = Class.forName("com.samsung.android.honeyboard.settings.aiwriter.translation.WritingAssistTranslationSettingsFragment", false, loader);
        Method create = fragment.getDeclaredMethod("onCreatePreferences", Bundle.class, String.class);
        create.setAccessible(true);
        module.hook(create).setId("honeyboardx:settings-entry").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    try {
                        Object screen = reflect(chain.getThisObject(), "getPreferenceScreen");
                        Object context = reflect(chain.getThisObject(), "getContext");
                        if (!(context instanceof Context) || screen == null) return result;
                        try { if (reflect(screen, "findPreference", "HONEYBOARD_X_SETTINGS") != null) return result; }
                        catch (Throwable ignored) { }
                        Class<?> preference = Class.forName("androidx.preference.Preference", false, loader);
                        Object entry = preference.getConstructor(Context.class).newInstance(context);
                        reflect(entry, "setKey", "HONEYBOARD_X_SETTINGS");
                        reflect(entry, "setTitle", "HoneyBoard X");
                        reflect(entry, "setSummary", I18n.t("voice_input_and_codex_translation_settings"));
                        Intent intent = new Intent(Intent.ACTION_MAIN);
                        intent.setClassName("dev.sampple.honeyboardx", "dev.sampple.rambler.SettingsActivity");
                        reflect(entry, "setIntent", intent);
                        reflect(screen, "addPreference", entry);
                    } catch (Throwable error) { Log.w("HoneyBoardX", "Could not add Samsung settings entry", error); }
                    return result;
                });
    }
    private static Object reflect(Object receiver, String name, Object... args) throws Exception {
        for (Class<?> c = receiver.getClass(); c != null; c = c.getSuperclass())
            for (Method m : c.getDeclaredMethods()) if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                boolean compatible = true;
                for (int i = 0; i < args.length; i++) if (args[i] != null && !m.getParameterTypes()[i].isAssignableFrom(args[i].getClass())) compatible = false;
                if (compatible) { m.setAccessible(true); return m.invoke(receiver, args); }
            }
        throw new NoSuchMethodException(name);
    }
    private void observeCancel(Method method) {
        method.setAccessible(true);
        module.hook(method).setId("honeyboardx:translation-cancel:" + method.getName()).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    try { cancel(chain.getThisObject(), context(chain.getThisObject())); }
                    catch (Throwable t) { Log.w("HoneyBoardX", "Translation cancel", t); }
                    return chain.proceed(); // Original exceptions are never hidden.
                });
    }
    private Object intercept(XposedInterface.Chain chain) throws Throwable {
        Context context;
        Object owner = chain.getThisObject();
        String input, source, target;
        try {
            context = context(owner);
            input = (String) chain.getArg(0); source = (String) chain.getArg(1); target = (String) chain.getArg(2);
            if (!options.enabled || context == null || input == null || input.isBlank() || target == null || target.isBlank()
                    || source != null && source.equalsIgnoreCase(target)) return chain.proceed();
            Bundle status = context.getContentResolver().call(CodexBridgeProvider.URI, "status", null, null);
            if (status == null || !status.getBoolean("authenticated", false)) return chain.proceed();
        } catch (Throwable error) {
            Log.w("HoneyBoardX", "Translation unavailable; native translation", error);
            return chain.proceed();
        }
        // Successful preflight: submit before replacing the native path, or proceed on failure.
        Job job;
        try {
            cancel(owner, context);
            job = new Job(UUID.randomUUID().toString(), input);
            Object callback = chain.getArg(3);
            if (callback == null) return chain.proceed();
            synchronized (jobs) { jobs.put(owner, job); }
            showStatus(context, owner, job);
            workers.execute(() -> perform(context, owner, job, source, target, callback));
            // The network bridge may stall even when its individual read timeout is set.
            job.timeout = () -> { if (current(owner, job)) { cancel(owner, context); Toast.makeText(context, I18n.t("translation_timed_out"), Toast.LENGTH_SHORT).show(); } };
            main.postDelayed(job.timeout, 720000);
        } catch (Throwable error) {
            Log.w("HoneyBoardX", "Translation dispatch failed; native translation", error);
            cancel(owner, context);
            return chain.proceed();
        }
        return null;
    }
    private void perform(Context context, Object owner, Job job, String source, String target, Object callback) {
        Bundle args = new Bundle(); args.putString("input", job.input); args.putString("source", source); args.putString("target", target);
        // The hook owns LSPosed remote preferences; the app provider need not wait for the service Binder.
        TranslationOptions setting = options;
        args.putBoolean("enabled", setting.enabled); args.putBoolean("fallback", setting.fallback);
        args.putString("model", setting.model); args.putString("reasoning", setting.reasoning);
        args.putString("prompt", setting.prompt);
        Bundle response;
        try { response = context.getContentResolver().call(CodexBridgeProvider.URI, "translate", job.id, args); }
        catch (Throwable error) { response = new Bundle(); response.putString("error", I18n.t("can_t_connect_to_translation_service")); }
        Bundle result = response;
        main.post(() -> {
            if (!current(owner, job)) return;
            try {
                if (!job.input.equals(text(owner))) { finish(owner, job, null); return; }
                if (result != null && result.getBoolean("ok") && result.getString("result") != null && !result.getString("result").isBlank()) {
                    try {
                        Method invoke = callback.getClass().getMethod("invoke", Object.class);
                        invoke.setAccessible(true); invoke.invoke(callback, result.getString("result"));
                        finish(owner, job, null); return;
                    } catch (Throwable error) { Log.w("HoneyBoardX", "Could not apply translation", error); }
                }
                if (result != null && result.getBoolean("fallback") && options.fallback) {
                    try {
                        job.nativeFallback = true;
                        Object wrapped = Proxy.newProxyInstance(callback.getClass().getClassLoader(),
                                new Class[]{Class.forName("kotlin.jvm.functions.Function1", false, callback.getClass().getClassLoader())},
                                (proxy, method, values) -> {
                                    if (method.getDeclaringClass() == Object.class) {
                                        if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                                        if ("equals".equals(method.getName())) return proxy == values[0];
                                        return "HoneyBoardX fallback callback";
                                    }
                                    if (!current(owner, job) || !job.input.equals(text(owner))) return null;
                                    finish(owner, job, null);
                                    return method.invoke(callback, values);
                                });
                        original.invoke(owner, new Object[]{job.input, source, target, wrapped});
                        main.postDelayed(() -> finish(owner, job, I18n.t("default_translation_timed_out")), 120000);
                        return;
                    } catch (Throwable error) { Log.w("HoneyBoardX", "Native fallback failed", error); }
                }
                String message = result == null ? I18n.t("translation_failed") : result.getString("error", I18n.t("translation_failed"));
                finish(owner, job, I18n.t("canceled").equals(message) ? null : message);
            } catch (Throwable error) { Log.w("HoneyBoardX", "Translation result", error); finish(owner, job, I18n.t("couldn_t_apply_translation")); }
        });
    }
    private static Context context(Object owner) {
        try { Object c = owner.getClass().getMethod("getContext").invoke(owner); return c instanceof Context ? (Context) c : null; }
        catch (Throwable ignored) { return null; }
    }
    private static String text(Object owner) {
        try { Object value = owner.getClass().getMethod("getInputText").invoke(owner); return value instanceof String ? (String) value : null; }
        catch (Throwable ignored) { return null; }
    }
    private boolean current(Object owner, Job job) { synchronized (jobs) { return jobs.get(owner) == job && !job.cancelled.get(); } }
    private void cancel(Object owner, Context context) {
        Job job; synchronized (jobs) { job = jobs.remove(owner); }
        if (job == null) return;
        job.cancelled.set(true);
        if (job.timeout != null) main.removeCallbacks(job.timeout);
        removeStatus(job);
        resetPrevInput(owner);
        if (context != null) workers.execute(() -> {
            try { context.getContentResolver().call(CodexBridgeProvider.URI, "cancel", job.id, null); }
            catch (Throwable ignored) { }
        });
    }
    private static void resetPrevInput(Object owner) {
        try { reflect(owner, "setPrevInputText", ""); } catch (Throwable ignored) { }
    }
    private void finish(Object owner, Job job, String message) {
        synchronized (jobs) { if (jobs.get(owner) != job) return; jobs.remove(owner); }
        if (job.timeout != null) main.removeCallbacks(job.timeout);
        removeStatus(job);
        if (message != null) {
            resetPrevInput(owner);
            Context context = context(owner);
            if (context != null) Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }
    private static View findEdit(View v, int depth) {
        if (v == null || depth > 25) return null;
        if (v.getClass().getSimpleName().contains("TranslationEditText")) return v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
            View found = findEdit(((ViewGroup) v).getChildAt(i), depth + 1); if (found != null) return found;
        }
        return null;
    }
    private void showStatus(Context context, Object owner, Job job) {
        try {
            Object service = reflect(owner, "getService");
            Object dialog = reflect(service, "getWindow"); // sb/a.getWindow() returns Dialog, not Window.
            Window window = dialog instanceof Window ? (Window) dialog : ((android.app.Dialog) dialog).getWindow();
            if (window == null) return;
            View edit = findEdit(window.getDecorView(), 0);
            if (edit == null || !(edit.getParent() instanceof LinearLayout)
                    || !(edit.getLayoutParams() instanceof LinearLayout.LayoutParams)) return;
            LinearLayout parent = (LinearLayout) edit.getParent();
            LinearLayout status = new LinearLayout(context);
            status.setGravity(android.view.Gravity.CENTER_VERTICAL);
            ProgressBar progress = new ProgressBar(context, null, android.R.attr.progressBarStyleSmall);
            status.addView(progress, new LinearLayout.LayoutParams(dp(context, 16), dp(context, 16)));
            TextView label = new TextView(context); label.setTextSize(11); label.setMaxWidth(dp(context, 86));
            label.setPadding(dp(context, 4), 0, dp(context, 2), 0); status.addView(label);
            TextView cancel = new TextView(context); cancel.setText(I18n.t("cancel")); cancel.setTextSize(11);
            cancel.setGravity(android.view.Gravity.CENTER);
            cancel.setMinWidth(dp(context, 40)); cancel.setMinHeight(dp(context, 48));
            cancel.setOnClickListener(v -> this.cancel(owner, context)); status.addView(cancel);
            job.edit = edit;
            job.editParams = edit.getLayoutParams();
            job.status = status;
            LinearLayout.LayoutParams resized = new LinearLayout.LayoutParams((LinearLayout.LayoutParams) job.editParams);
            resized.width = 0; resized.weight = 1;
            edit.setLayoutParams(resized);
            parent.addView(status, new LinearLayout.LayoutParams(-2, -1));
            job.ticker = new Runnable() {
                @Override public void run() {
                    if (!current(owner, job) || job.status != status) return;
                    label.setText(I18n.t(job.nativeFallback ? "fallback_progress" : "translation_progress",
                            (SystemClock.elapsedRealtime() - job.started) / 1000));
                    main.postDelayed(this, 1000);
                }
            };
            status.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) { }
                @Override public void onViewDetachedFromWindow(View v) {
                    if (job.status == status && current(owner, job)) cancel(owner, context);
                }
            });
            job.ticker.run();
        } catch (Throwable error) {
            Log.w("HoneyBoardX", "Translation status unavailable", error);
            removeStatus(job); // Never leave the native edit field resized after a partial failure.
        }
    }
    private static int dp(Context context, int n) { return Math.round(n * context.getResources().getDisplayMetrics().density); }
    private void removeStatus(Job job) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(() -> removeStatus(job)); return; }
        if (job.ticker != null) main.removeCallbacks(job.ticker);
        View status = job.status;
        job.status = null; // Detach listener must not cancel a completed job.
        if (status != null && status.getParent() instanceof ViewGroup)
            ((ViewGroup) status.getParent()).removeView(status);
        if (job.edit != null && job.editParams != null) job.edit.setLayoutParams(job.editParams);
        job.edit = null; job.editParams = null; job.ticker = null;
    }
    private static final class Job {
        final String id, input; final long started = SystemClock.elapsedRealtime();
        final AtomicBoolean cancelled = new AtomicBoolean();
        boolean nativeFallback;
        View status, edit; ViewGroup.LayoutParams editParams; Runnable ticker, timeout;
        Job(String id, String input) { this.id = id; this.input = input; }
    }
}
