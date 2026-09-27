package dev.sampple.rambler;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Printer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.view.inputmethod.InputMethodSubtype;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** HoneyBoard 5.9.40.75 only. All app symbols use DEX names, not jadx aliases. */
public final class ModuleMain extends XposedModule {
    private static final String TARGET = "com.samsung.android.honeyboard";
    private static final String ID = "rambler_voice";
    private static String label() { return I18n.t("rambler_voice_input"); }
    private final Set<ClassLoader> installed = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<Object> ownInfo = Collections.newSetFromMap(new WeakHashMap<>());
    private final List<Registration> registrations = new ArrayList<>();
    private final Map<Object, String> beeStates = new WeakHashMap<>();
    private WeakReference<InputMethodService> service = new WeakReference<>(null);
    private WeakReference<View> root = new WeakReference<>(null);
    private WeakReference<Object> boardConfig = new WeakReference<>(null);
    private WeakReference<Object> toolbarSettings = new WeakReference<>(null);
    private WeakReference<Bee> activeBee = new WeakReference<>(null);
    private VoiceController controller;
    private final dev.sampple.rambler.translation.TranslationHook translation =
            new dev.sampple.rambler.translation.TranslationHook(this);
    private long generation;
    private boolean labelsReady;
    private volatile VoiceOptions voiceOptions = VoiceOptions.defaults();
    private SharedPreferences remoteOptions;
    // Keep a strong listener reference: SharedPreferences may retain it weakly.
    private final SharedPreferences.OnSharedPreferenceChangeListener optionsChanged = (prefs, key) ->
            guard("reload voice settings", () -> voiceOptions = VoiceOptions.read(prefs));

    private void loadVoiceOptions() {
        try {
            remoteOptions = getRemotePreferences(VoiceOptions.GROUP);
            voiceOptions = VoiceOptions.read(remoteOptions);
            remoteOptions.registerOnSharedPreferenceChangeListener(optionsChanged);
        } catch (Throwable error) { report("remote settings unavailable; using defaults", error); }
    }

    @Override public void onPackageReady(PackageReadyParam param) {
        try {
            if (!TARGET.equals(param.getPackageName()) || !param.isFirstPackage()) return;
            ClassLoader cl = param.getClassLoader();
            if (cl == null) return;
            synchronized (installed) { if (!installed.add(cl)) return; }
            loadVoiceOptions();
            guard("translation install", () -> translation.install(cl));
            installLabels(cl);
            installService(cl);
            installLanguage(cl);
            installSwitches(cl);
            installPlacement(cl);
            installQueen(cl);
            installBeeLifecycle(cl);
        } catch (Throwable t) { report("package setup", t); }
    }

    private interface Action { void run() throws Throwable; }
    private interface Observation { void accept(XposedInterface.Chain chain, Object result) throws Throwable; }

    private void guard(String where, Action action) {
        try { action.run(); } catch (Throwable t) { report(where, t); }
    }

    private void report(String where, Throwable t) {
        try { log(Log.ERROR, "SamsungRambler", where, t); }
        catch (Throwable ignored) { /* Logging must not take down the IME. */ }
    }

    private void onMain(String where, Action action) {
        guard(where, () -> {
            if (Looper.myLooper() == Looper.getMainLooper()) guard(where, action);
            else new Handler(Looper.getMainLooper()).post(() -> guard(where, action));
        });
    }

    /** proceed is deliberately OUTSIDE all module catch blocks: original exceptions propagate. */
    private void observe(Executable target, boolean before, Observation callback) {
        target.setAccessible(true);
        hook(target).setId("rambler:" + target + (before ? ":before" : ":after"))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    if (before) guard("before " + target.getName(), () -> callback.accept(chain, null));
                    Object result = chain.proceed();
                    if (!before) guard("after " + target.getName(), () -> callback.accept(chain, result));
                    return result;
                });
    }

    private static Class<?> type(ClassLoader cl, String name) throws ClassNotFoundException {
        return Class.forName(name, false, cl);
    }

    private static Method method(Class<?> type, String name, Class<?>... args) throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try { Method m = c.getDeclaredMethod(name, args); m.setAccessible(true); return m; }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static Object invoke(Object receiver, String name) throws Throwable {
        return call(method(receiver.getClass(), name), receiver);
    }

    private static Object call(Method method, Object receiver, Object... args) throws Throwable {
        try { return method.invoke(receiver, args); }
        catch (InvocationTargetException t) { throw t.getCause(); }
    }

    // Original short names come first; aliases only help with deobfuscated test fixtures.
    private static Object field(Object receiver, String... names) throws ReflectiveOperationException {
        for (String name : names) {
            for (Class<?> c = receiver.getClass(); c != null; c = c.getSuperclass()) {
                try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(receiver); }
                catch (NoSuchFieldException ignored) { }
            }
        }
        throw new NoSuchFieldException(receiver.getClass().getName() + ":" + String.join(",", names));
    }

    private void installLabels(ClassLoader cl) {
        guard("BeeInfo label hooks", () -> {
            Class<?> info = type(cl, "z6.j");
            for (String name : new String[]{"a", "b", "e"}) {
                Method m = method(info, name);
                if (m.getReturnType() != String.class) throw new NoSuchMethodException("BeeInfo." + name);
                hook(m).setId("rambler:label:" + name)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            boolean ours = false;
                            try { synchronized (ownInfo) { ours = ownInfo.contains(chain.getThisObject()); } }
                            catch (Throwable t) { report("label identity", t); }
                            if (ours) return label();
                            return chain.proceed();
                        });
            }
            labelsReady = true;
        });
    }

    private void installQueen(ClassLoader cl) {
        guard("PluginQueenBee hook", () -> {
            Class<?> queen = type(cl, TARGET + ".bee.b");
            Constructor<?> ctor = queen.getDeclaredConstructor(Context.class, type(cl, "Eb.c"));
            observe(ctor, false, (chain, result) -> {
                Object instance = chain.getThisObject();
                Context context = (Context) chain.getArg(0);
                onMain("register Rambler", () -> register(instance, context, cl));
            });
        });
    }

    private void installBeeLifecycle(ClassLoader cl) {
        guard("beehive rebuild hook", () -> {
            Class<?> component = type(cl, "X9.e");
            observe(method(component, "r"), true, (chain, result) -> {
                // r() snapshots queen.e() BEFORE na.b.initialize(); a posted repair is too late.
                for (Object queen : (List<?>) field(chain.getThisObject(), "n", "f13945n")) {
                    if (type(cl, TARGET + ".bee.b").isInstance(queen)) {
                        Context context = (Context) field(queen, "a", "f22237a");
                        register(queen, context, cl);
                    }
                }
            });
            observe(method(component, "r"), false, (chain, result) ->
                    traceBeeState("rebuild", field(chain.getThisObject(), "k", "f13942k")));
        });
        Class<?> model;
        try { model = type(cl, TARGET + ".beehive.viewmodel.K"); }
        catch (Throwable t) { report("beehive diagnostics", t); return; }
        guard("beehive save hook", () -> observe(method(model, "w"), false,
                (chain, result) -> traceBeeState("save", chain.getThisObject())));
        guard("beehive edit hook", () -> observe(method(model, "v", String.class, boolean.class), false,
                (chain, result) -> traceBeeState("edit/save", chain.getThisObject())));
        guard("beehive remove hook", () -> observe(method(model, "u", type(cl, "z6.c"), boolean.class), false,
                (chain, result) -> {
                    if (Boolean.TRUE.equals(result) && ID.equals(invoke(chain.getArg(0), "getBeeId"))) {
                        Log.i("HoneyBoardX", "Bee removed from BeeWorld");
                        traceBeeState("remove", chain.getThisObject());
                    }
                }));
        guard("beehive visibility hook", () -> observe(method(model, "x",
                boolean.class, boolean.class, boolean.class, boolean.class), false,
                (chain, result) -> traceBeeState("visibility", chain.getThisObject())));
    }

    @SuppressWarnings("unchecked")
    private void traceBeeState(String event, Object model) throws Throwable {
        String position = "absent";
        for (String name : new String[]{"t", "n", "o"}) {
            Object collection = field(model, name);
            List<?> list = "t".equals(name) ? (List<?>) collection : (List<?>) invoke(collection, "e");
            for (Object item : list) {
                if (ID.equals(invoke(item, "getBeeId"))) {
                    position = ("t".equals(name) ? "primary" : "n".equals(name) ? "swarm" : "sleep")
                            + ":" + list.indexOf(item) + ":vis=" + invoke(item, "getBeeVisibility");
                    break;
                }
            }
            if (!"absent".equals(position)) break;
        }
        Object settings = field(model, "a", "f22399a");
        int rank = ((Number) call(method(settings.getClass(), "n", String.class), settings, ID)).intValue();
        String state = position + ":rank=" + rank;
        String previous = beeStates.put(model, state);
        if (!state.equals(previous)) Log.i("HoneyBoardX", event + " " + state);
    }

    @SuppressWarnings("unchecked")
    private void register(Object queen, Context context, ClassLoader cl) throws Throwable {
        if (!labelsReady) return; // Do not publish a partially initialized BeeInfo.
        List<Object> bees;
        try { bees = (List<Object>) invoke(queen, "e"); }
        catch (Throwable t) {
            report("queen.e fallback", t);
            bees = (List<Object>) field(queen, "d", "f22240d");
        }
        for (Object bee : bees) if (ID.equals(invoke(bee, "getBeeId"))) return;
        for (Registration r : registrations) {
            if (r.list.get() == bees && r.proxy.get() != null) {
                Log.i("HoneyBoardX", "Bee missing from queen list=" + System.identityHashCode(bees)
                        + "; re-registering");
                break;
            }
        }
        Context app = context.getApplicationContext();
        if (app == null) app = context;
        Class<?> iface = type(cl, "z6.c");
        Class<?> builderClass = type(cl, "z6.i");
        int labelRes = app.getResources().getIdentifier("toolbar_voice_input", "string", TARGET);
        if (labelRes == 0) labelRes = android.R.string.untitled;
        Object builder;
        Icon icon = microphone();
        try {
            builder = builderClass.getDeclaredConstructor(Context.class, Icon.class, int.class,
                    Resources.class, String.class).newInstance(app, icon, labelRes, app.getResources(), TARGET);
        } catch (NoSuchMethodException t) {
            report("BeeInfo constructor fallback", t);
            builder = builderClass.getDeclaredConstructor(Context.class, Icon.class, int.class)
                    .newInstance(app, icon, labelRes);
        }
        Object info = type(cl, "z6.j").getDeclaredConstructor(builderClass).newInstance(builder);
        synchronized (ownInfo) { ownInfo.add(info); }
        Bee handler = new Bee(info);
        Object proxy = Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, handler::dispatch);
        Registration registration = new Registration(bees, proxy, handler, app);
        registrations.removeIf(r -> r.list.get() == null || r.proxy.get() == null);
        registrations.add(registration);
        // If QueenBee appeared after na.b.initialize(), seed rank before its
        // ObservableList callback builds the BeeItem; otherwise a full primary
        // silently sends an unranked Bee to the overflow menu.
        Object settings = toolbarSettings.get();
        if (settings != null) guard("late toolbar placement", () -> placeOnce(settings, cl));
        bees.add(proxy);
        Log.i("HoneyBoardX", "Bee registered queen=" + System.identityHashCode(queen)
                + " list=" + System.identityHashCode(bees) + " size=" + bees.size());
    }

    private static Icon microphone() {
        Bitmap bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        c.drawRoundRect(35, 12, 61, 59, 13, 13, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6);
        p.setStrokeCap(Paint.Cap.ROUND);
        c.drawArc(25, 31, 71, 72, 0, 180, false, p);
        c.drawLine(25, 44, 25, 52, p);
        c.drawLine(71, 44, 71, 52, p);
        c.drawLine(48, 73, 48, 84, p);
        c.drawLine(35, 84, 61, 84, p);
        return Icon.createWithBitmap(bitmap);
    }

    private final class Bee {
        final Object info;
        // BeeItem gives ownership of this callback to the Bee; a weak reference gets GC'd.
        // The module only weakly tracks Bees, so this does not root the IME globally.
        Object callback;
        boolean fresh;
        boolean asleep;
        Bee(Object info) { this.info = info; }

        Object dispatch(Object proxy, Method m, Object[] args) {
            try {
                switch (m.getName()) {
                    case "equals": return proxy == args[0];
                    case "hashCode": return System.identityHashCode(proxy);
                    case "toString": return "RamblerBee(" + ID + ")";
                    case "getBeeId": return ID;
                    case "getBeeInfo": return info;
                    case "getBeeFlags": return 1;
                    case "getBeeVisibility": return 0;
                    case "getPinBeePriority": return Integer.MAX_VALUE;
                    case "getCallback": return callback;
                    case "setCallback": callback = args[0]; return null;
                    case "isNew": return fresh;
                    case "setNew": fresh = (Boolean) args[0]; return null;
                    case "isSleep": case "getIsSleep": return asleep;
                    case "setSleep": asleep = (Boolean) args[0]; return null;
                    case "needLabel": case "isPpAccepted": case "isUsingNetwork": return true;
                    case "isSelected": return activeBee.get() == this && controller != null && controller.isShowing();
                    case "execute":
                        fresh = false;
                        onMain("execute", () -> toggle(this));
                        return null; // Samsung's default execute(Function0) does not invoke the callback either.
                    case "finish": case "onDestroy":
                        onMain("finish", () -> { if (activeBee.get() == this) closeController(); });
                        return null;
                    case "invalidate":
                        onMain("invalidate", () -> notifyCallback("e")); return null;
                    case "updateBeeVisibility": return null;
                    case "renew":
                        fresh = (Boolean) args[0];
                        onMain("renew", () -> notifyCallback("m")); return null;
                    case "dump": if (args[0] instanceof Printer) ((Printer) args[0]).println(ID); return null;
                    default: return defaultValue(m.getReturnType());
                }
            } catch (Throwable t) {
                report("Bee." + m.getName(), t);
                return defaultValue(m.getReturnType());
            }
        }

        void notifyCallback(String name) throws Throwable {
            Object target = callback;
            if (target != null) invoke(target, name);
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == char.class) return '\0';
        return null;
    }

    private static final class Registration {
        final WeakReference<List<Object>> list;
        final WeakReference<Object> proxy;
        final WeakReference<Bee> bee;
        final WeakReference<Context> context;
        Registration(List<Object> list, Object proxy, Bee bee, Context context) {
            this.list = new WeakReference<>(list);
            this.proxy = new WeakReference<>(proxy);
            this.bee = new WeakReference<>(bee);
            this.context = new WeakReference<>(context);
        }
    }

    private void installPlacement(ClassLoader cl) {
        guard("primary placement hook", () -> observe(method(type(cl, "na.b"), "initialize"), false,
                (chain, result) -> {
                    // Do not post: rank assignment immediately follows initialize in X9.e.r().
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        Object settings = chain.getThisObject();
                        toolbarSettings = new WeakReference<>(settings);
                        placeOnce(settings, cl);
                    }
                }));
    }

    private void placeOnce(Object settings, ClassLoader cl) throws Throwable {
        Context context = null;
        for (Registration r : registrations) {
            if (r.proxy.get() != null && r.context.get() != null) { context = r.context.get(); break; }
        }
        if (context == null) return; // na.b constructor may run before PluginQueenBee exists.
        Object current = field(settings, "d", "f32417d");
        Object user = field(current, "b", "f32419b");
        String setName = (String) invoke(user, "p");
        if (!"bee_user_set_main".equals(setName) && !"bee_user_set_sub".equals(setName)) return;
        SharedPreferences prefs = context.getSharedPreferences("rambler_hook_state", Context.MODE_PRIVATE);
        String marker = "primary_inserted_v1_" + setName;
        if (prefs.getBoolean(marker, false)) return; // Including after Samsung's toolbar reset: respect removal.
        List<?> existing = (List<?>) invoke(current, "d");
        List<Object> tags = new ArrayList<>(existing);
        for (Object tag : tags) {
            if (ID.equals(invoke(tag, "getId"))) { prefs.edit().putBoolean(marker, true).apply(); return; }
        }
        int primary = ((Number) invoke(current, "h")).intValue();
        int max = ((Number) invoke(current, "l")).intValue();
        int sleeping = ((Number) invoke(current, "c")).intValue();
        if (max <= 0 || primary < 0 || primary > tags.size() || sleeping < 0
                || sleeping > tags.size() - primary) return;
        int position = Math.min(primary, max - 1);
        // Add after translation if present, always before edit_toolbar and the last full primary slot.
        for (int i = 0; i < position; i++) {
            String id = (String) invoke(tags.get(i), "getId");
            if ("translation".equals(id) || "translate".equals(id)) { position = i + 1; break; }
        }
        for (int i = 0; i < position; i++) {
            if ("edit_toolbar".equals(invoke(tags.get(i), "getId"))) { position = i; break; }
        }
        Object tag = type(cl, TARGET + ".common.beehive.BeeTag")
                .getDeclaredConstructor(String.class, boolean.class).newInstance(ID, false);
        tags.add(position, tag);
        int count = Math.min(max, primary + 1);
        call(method(settings.getClass(), "j", List.class, int.class, int.class), settings, tags, count, sleeping);
        // j() selects/persists ia.c without changing the preset or deleting any old item.
        prefs.edit().putBoolean(marker, true).apply();
    }

    private void installService(ClassLoader cl) {
        guard("service hooks", () -> {
            Class<?> svc = type(cl, TARGET + ".service.HoneyBoardService");
            for (Method m : svc.getDeclaredMethods()) {
                guard("install lifecycle " + m.getName(), () -> {
                String name = m.getName();
                if (name.equals("onCreate") || name.equals("onCreateInputView")
                        || name.equals("onStartInput") || name.equals("onStartInputView")) {
                    if (name.equals("onStartInput") || name.equals("onStartInputView"))
                        observe(m, true, (chain, result) -> onMain("input changed", this::closeController));
                    // Different ID for before and after observers is required on the same method.
                    installServiceAfter(m);
                } else if (name.equals("onFinishInputView") || name.equals("onFinishInput")
                        || name.equals("onWindowHidden") || name.equals("onConfigurationChanged")
                        || name.equals("onDestroy")) {
                    observe(m, true, (chain, result) -> onMain(name, () -> {
                        closeController();
                        if (name.equals("onDestroy")) {
                            cleanupRegistrations();
                            service.clear(); root.clear(); boardConfig.clear(); toolbarSettings.clear();
                        } else if (name.equals("onConfigurationChanged")) root.clear();
                    }));
                }
                });
            }
        });
    }

    private void installServiceAfter(Method m) {
        hook(m).setId("rambler:service-after:" + m.getName())
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    guard("service capture", () -> {
                        InputMethodService svc = (InputMethodService) chain.getThisObject();
                        onMain("service/root capture", () -> {
                            if (service.get() != svc) {
                                closeController();
                                root.clear();
                            }
                            service = new WeakReference<>(svc);
                            View view = result instanceof View ? (View) result : findRoot(svc);
                            if (view != null) {
                                if (root.get() != null && root.get() != view) closeController();
                                root = new WeakReference<>(view);
                            }
                        });
                    });
                    return result;
                });
    }

    private View findRoot(InputMethodService svc) {
        try {
            Object view = field(svc, "viewHolderLayout");
            if (view instanceof View && ((View) view).isAttachedToWindow()) return (View) view;
        } catch (Throwable t) { report("viewHolderLayout fallback", t); }
        View previous = root.get();
        if (previous != null && previous.isAttachedToWindow()) return previous;
        try {
            if (svc.getWindow() != null && svc.getWindow().getWindow() != null)
                return svc.getWindow().getWindow().getDecorView();
        } catch (Throwable t) { report("window root fallback", t); }
        return null;
    }

    private void toggle(Bee bee) throws Throwable {
        if (controller != null) { closeController(); return; }
        InputMethodService svc = service.get();
        if (svc == null || !svc.isInputViewShown()) return;
        View view = findRoot(svc);
        if (view == null || !view.isAttachedToWindow()) return;
        root = new WeakReference<>(view);
        long token = ++generation;
        activeBee = new WeakReference<>(bee);
        try {
            if (remoteOptions == null) loadVoiceOptions();
            controller = new VoiceController(svc, view, languageTag(svc), voiceOptions, () ->
                    onMain("controller closed", () -> {
                        if (generation != token) return;
                        controller = null;
                        activeBee.clear();
                        generation++;
                        bee.notifyCallback("e");
                    }));
            controller.show();
            bee.notifyCallback("e");
        } catch (Throwable t) {
            closeController();
            throw t;
        }
    }

    private void closeController() {
        VoiceController old = controller;
        Bee bee = activeBee.get();
        controller = null;
        activeBee.clear();
        generation++; // Stale onClosed callbacks cannot clear a newer controller.
        if (old != null) guard("controller.close", old::close);
        if (bee != null) guard("selection cleared", () -> bee.notifyCallback("e"));
    }

    private void cleanupRegistrations() {
        // PluginQueenBee belongs to Koin and can outlive HoneyBoardService. Removing
        // our Bee here leaves the next X9.e with no entry until the process restarts.
        for (Registration r : registrations) {
            guard("retained Bee", () -> {
                List<Object> list = r.list.get();
                Object proxy = r.proxy.get();
                if (list != null && proxy != null && list.contains(proxy))
                    Log.i("HoneyBoardX", "service destroyed; retaining Bee in queen list="
                            + System.identityHashCode(list));
            });
        }
        registrations.clear();
        beeStates.clear();
    }

    private void installLanguage(ClassLoader cl) {
        guard("language capture", () -> {
            Class<?> config = type(cl, "a7.e");
            for (Constructor<?> ctor : config.getDeclaredConstructors())
                observe(ctor, false, (chain, result) -> boardConfig = new WeakReference<>(chain.getThisObject()));
            observe(method(config, "g"), false,
                    (chain, result) -> boardConfig = new WeakReference<>(chain.getThisObject()));
        });
    }

    private String languageTag(InputMethodService svc) {
        try {
            Object config = boardConfig.get();
            if (config != null) {
                Object language = invoke(config, "g");
                String code = (String) invoke(language, "getLanguageCode");
                String country = (String) invoke(language, "getCountryCode");
                String tag = normalizeTag(code + (country == null || country.isEmpty() ? "" : "-" + country));
                if (tag != null) return tag;
            }
        } catch (Throwable t) { report("HoneyBoard language fallback", t); }
        try {
            InputMethodManager imm = (InputMethodManager) svc.getSystemService(Context.INPUT_METHOD_SERVICE);
            InputMethodSubtype subtype = imm == null ? null : imm.getCurrentInputMethodSubtype();
            if (subtype != null) {
                String tag = normalizeTag(subtype.getLanguageTag());
                if (tag == null) tag = normalizeTag(subtype.getLocale());
                if (tag != null) return tag;
            }
        } catch (Throwable t) { report("subtype language fallback", t); }
        try {
            Configuration config = svc.getResources().getConfiguration();
            if (!config.getLocales().isEmpty()) return config.getLocales().get(0).toLanguageTag();
        } catch (Throwable t) { report("locale fallback", t); }
        return Locale.getDefault().toLanguageTag();
    }

    private static String normalizeTag(String tag) {
        if (tag == null || tag.isEmpty()) return null;
        String normalized = Locale.forLanguageTag(tag.replace('_', '-')).toLanguageTag();
        return "und".equals(normalized) ? null : normalized;
    }

    /**
     * Installs a voice strip over BeeHive's measured toolbar, never over the key body.
     * When BeeHive is absent/hidden or the keyboard is floating/split, use a strip
     * at the top of layout_body. No Samsung view is removed or made invisible.
     */
    static HeaderMount mountVoiceHeader(View root, VoicePanel panel, Runnable onMountLost) {
        if (root == null || panel == null) return null;
        HeaderMount mount = null;
        try {
            mount = new HeaderMount(root, panel, onMountLost);
            mount.update();
            root.getViewTreeObserver().addOnGlobalLayoutListener(mount);
            return mount;
        } catch (Throwable t) {
            Log.e("SamsungRambler", "voice header mount failed", t);
            if (mount != null) mount.close();
            return null;
        }
    }

    static final class HeaderMount implements AutoCloseable, ViewTreeObserver.OnGlobalLayoutListener {
        private final View root;
        private final VoicePanel panel;
        private final Runnable onMountLost;
        private final FrameLayout frame;
        private final FrameLayout body;
        private final View header;
        private final View toolbar;
        private View beeHive;
        private final int fallbackHeight;
        private boolean closed;
        private boolean toolbarAccessibilityHidden;
        private int toolbarAccessibility;

        HeaderMount(View root, VoicePanel panel, Runnable onMountLost) {
            this.root = root;
            this.panel = panel;
            this.onMountLost = onMountLost;
            header = findResourceView(root, "layout_header");
            toolbar = findResourceView(root, "layout_toolbar");
            beeHive = findResourceView(root, "beehive_primary_constraint_layout");
            View frameView = findResourceView(root, "layout_honey_frame");
            View bodyView = findResourceView(root, "layout_body");
            frame = frameView instanceof FrameLayout ? (FrameLayout) frameView : null;
            body = bodyView instanceof FrameLayout ? (FrameLayout) bodyView : null;
            if (body == null) throw new IllegalStateException("HoneyBoard layout_body missing");
            int dimen = root.getResources().getIdentifier("header_layout_area_height", "dimen", TARGET);
            int px = dimen == 0 ? 0 : root.getResources().getDimensionPixelSize(dimen);
            fallbackHeight = px > 0 ? px : Math.round(44 * root.getResources().getDisplayMetrics().density);
        }

        private static View findResourceView(View root, String name) {
            int id = root.getResources().getIdentifier(name, "id", TARGET);
            return id == 0 ? null : root.findViewById(id);
        }

        private static boolean visible(View view, View ancestor) {
            if (view == null || !view.isAttachedToWindow()) return false;
            for (View v = view; v != null; ) {
                if (v.getVisibility() != View.VISIBLE || v.getAlpha() <= 0f) return false;
                if (v == ancestor) return true;
                if (!(v.getParent() instanceof View)) break;
                v = (View) v.getParent();
            }
            return false;
        }

        private boolean compactKeyboard() {
            // Full docked keyboards span the window. Floating/one-hand/split layouts
            // must not capture an unrelated top header or the center of a split board.
            int displayWidth = root.getResources().getDisplayMetrics().widthPixels;
            if (frame == null || displayWidth <= 0 || frame.getWidth() < displayWidth * 0.85f)
                return true;
            View left = findResourceView(root, "layout_board_left");
            View right = findResourceView(root, "layout_board_right");
            return left != null && right != null && left.getWidth() > 0 && right.getWidth() > 0
                    && visible(left, root) && visible(right, root);
        }

        private void update() {
            if (closed) return;
            FrameLayout target = body;
            int x = 0, y = 0, width = ViewGroup.LayoutParams.MATCH_PARENT;
            int height = Math.min(fallbackHeight, body.getHeight() > 0 ? body.getHeight() : fallbackHeight);
            // layout_toolbar is the header slot; X9.e.o() inflates the BeeHive
            // primary ConstraintLayout into it. Its whole root includes expand area.
            // BeeHive's binding can replace its inflated root during configuration
            // or toolbar refresh; do not retain the stale view from construction.
            beeHive = findResourceView(root, "beehive_primary_constraint_layout");
            if (frame != null && header != null && toolbar != null && beeHive != null
                    && !compactKeyboard() && visible(beeHive, toolbar)
                    && visible(toolbar, header) && visible(header, frame)
                    && beeHive.getWidth() > 0 && beeHive.getHeight() > 0) {
                int[] origin = new int[2], point = new int[2];
                frame.getLocationOnScreen(origin);
                beeHive.getLocationOnScreen(point);
                Rect area = new Rect(point[0] - origin[0], point[1] - origin[1],
                        point[0] - origin[0] + beeHive.getWidth(),
                        point[1] - origin[1] + beeHive.getHeight());
                if (area.left >= 0 && area.top >= 0 && area.right <= frame.getWidth()
                        && area.bottom <= frame.getHeight()) {
                    target = frame;
                    x = area.left; y = area.top; width = area.width(); height = area.height();
                }
            }
            if (height <= 0) height = fallbackHeight;
            // Hide only the obscured toolbar from accessibility; keyboard keys in
            // layout_body remain focusable and can still receive normal touch input.
            if (toolbar != null && target == frame && !toolbarAccessibilityHidden) {
                toolbarAccessibility = toolbar.getImportantForAccessibility();
                toolbar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                toolbarAccessibilityHidden = true;
            } else if (target != frame) restoreToolbarAccessibility();
            if (panel.getParent() != target) {
                if (panel.getParent() instanceof ViewGroup)
                    ((ViewGroup) panel.getParent()).removeView(panel);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height,
                        Gravity.TOP | Gravity.LEFT);
                params.leftMargin = x; params.topMargin = y;
                target.addView(panel, params);
            } else {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) panel.getLayoutParams();
                if (params.width != width || params.height != height || params.leftMargin != x
                        || params.topMargin != y) {
                    params.width = width; params.height = height;
                    params.leftMargin = x; params.topMargin = y;
                    panel.setLayoutParams(params);
                }
                // Samsung may reinsert its own toolbar while this panel is showing.
                // Only reorder our own overlay; never manipulate a keyboard child.
                if (target.indexOfChild(panel) != target.getChildCount() - 1) panel.bringToFront();
            }
        }

        private void restoreToolbarAccessibility() {
            if (!toolbarAccessibilityHidden) return;
            toolbarAccessibilityHidden = false;
            toolbar.setImportantForAccessibility(toolbarAccessibility);
        }

        @Override public void onGlobalLayout() {
            try { update(); }
            catch (Throwable t) {
                Log.e("SamsungRambler", "voice header layout failed", t);
                close();
                try { if (onMountLost != null) onMountLost.run(); }
                catch (Throwable error) { Log.e("SamsungRambler", "voice mount cleanup failed", error); }
            }
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            try {
                ViewTreeObserver observer = root.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
            } catch (Throwable t) { Log.e("SamsungRambler", "voice layout listener removal failed", t); }
            try { restoreToolbarAccessibility(); }
            catch (Throwable t) { Log.e("SamsungRambler", "voice toolbar accessibility restore failed", t); }
            try {
                if (panel.getParent() instanceof ViewGroup)
                    ((ViewGroup) panel.getParent()).removeView(panel);
            } catch (Throwable t) { Log.e("SamsungRambler", "voice panel removal failed", t); }
        }
    }

    private void installSwitches(ClassLoader cl) {
        guard("other Bee execution hook", () -> {
            Class<?> item = type(cl, TARGET + ".beehive.data.BeeItem");
            observe(method(item, "executeInner"), true, (chain, result) -> {
                if (!ID.equals(invoke(chain.getThisObject(), "getBeeId")))
                    onMain("other Bee", this::closeController);
            });
        });
        guard("board transition hook", () -> {
            Class<?> board = type(cl, "qa.e");
            // qa.e.a() returns early for same board/state unless forced, but even a forced
            // rebind must not close a voice session unless the logical board/state changes.
            observe(method(board, "a", type(cl, "F6.b"), type(cl, "F6.G"), boolean.class), true,
                    (chain, result) -> {
                        Object current = field(chain.getThisObject(), "D", "f33917D");
                        Object state = field(chain.getThisObject(), "E");
                        if (!Objects.equals(current, chain.getArg(0))
                                || !Objects.equals(state, chain.getArg(1))) {
                            // The hook executes on the IME main thread; defer nothing so a
                            // real switch cancels before Samsung unbinds the old board.
                            onMain("board transition", this::closeController);
                        }
                    });
        });
        guard("physical key hook", () -> {
            Class<?> svc = type(cl, TARGET + ".service.HoneyBoardService");
            guard("physical key down", () -> observe(method(svc, "onKeyDown", int.class,
                    android.view.KeyEvent.class), true,
                    (chain, result) -> onMain("external physical key", () -> {
                        if (controller != null) controller.onExternalInput();
                    })));
            guard("selection update", () -> observe(method(svc, "onUpdateSelection", int.class,
                    int.class, int.class, int.class, int.class, int.class), false,
                    (chain, result) -> onMain("external selection", () -> {
                        if (controller != null) controller.onExternalSelectionChanged(
                                (Integer) chain.getArg(2), (Integer) chain.getArg(3));
                    })));
        });
        guard("soft keyboard edit hooks", () -> {
            // HoneyBoard's L7.b is the InputConnection used by its text-board keys.
            // Hook mutations BEFORE the original action; never swallow/replace key input.
            Class<?> ic = type(cl, "L7.b");
            for (Method m : ic.getDeclaredMethods()) {
                String name = m.getName();
                if (name.equals("commitText") || name.equals("setComposingText")
                        || name.equals("deleteSurroundingText")
                        || name.equals("deleteSurroundingTextInCodePoints")
                        || name.equals("sendKeyEvent")) {
                    guard("install keyboard edit " + name,
                            () -> observe(m, true, (chain, result) -> onMain("external keyboard edit", () -> {
                                if (controller != null) controller.onExternalInput();
                            })));
                }
            }
        });
    }
}
