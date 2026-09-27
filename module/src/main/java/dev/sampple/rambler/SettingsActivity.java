package dev.sampple.rambler;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import dev.sampple.rambler.translation.CodexAccount;
import dev.sampple.rambler.translation.CodexAuth;
import dev.sampple.rambler.translation.TranslationOptions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One UI-style settings: large title, status hero, grouped rounded cards, values shown in the
 * accent color under each title, and instant auto-save to libxposed remote preferences.
 * Switches and dialogs come from Theme.DeviceDefault, so on Galaxy devices they are native One UI.
 */
public final class SettingsActivity extends Activity {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final String[][] LANGUAGES = {
            {"ko-KR", I18n.t("korean")}, {"en-US", "English (US)"}, {"en-GB", "English (UK)"}, {"ja-JP", "日本語"},
            {"zh-Hans", "中文 (简体)"}, {"zh-Hant", "中文 (繁體)"}, {"es-ES", "Español"}, {"fr-FR", "Français"},
            {"de-DE", "Deutsch"}, {"vi-VN", "Tiếng Việt"}, {"th-TH", "ไทย"}, {"id-ID", "Bahasa Indonesia"}};
    private static final int[] SILENCE = {0, 2, 3, 4, 5, 7, 10, 15};
    private static final String[] EFFORTS = {"low", "medium", "high", "xhigh", "max"};
    private static String[] effortLabels() { return new String[]{I18n.t("low_fastest"), I18n.t("medium"), I18n.t("high"), I18n.t("very_high"), I18n.t("maximum_most_accurate")}; }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable persist = this::persistNow;
    private LinearLayout page;
    private TextView heroStatus, heroDetail, account, model, reasoning, prompt, primary, additional, silence, framework;
    private View heroDot;
    private Switch aurora, haptic, sound, enabled, fallback;
    private final List<View> translationDependents = new ArrayList<>();
    private boolean loaded, binding;
    private long request;
    private VoiceOptions voice = VoiceOptions.defaults();
    private TranslationOptions translation = TranslationOptions.defaults();
    private int background, cardColor, textColor, muted, accent, divider, good, warn;
    private CodexAuth auth;

    @Override public void onCreate(Bundle state) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(state);
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        // One UI 8 palette.
        background = dark ? 0xff000000 : 0xfff3f3f6;
        cardColor = dark ? 0xff171719 : 0xffffffff;
        textColor = dark ? 0xfffafafa : 0xff111114;
        muted = dark ? 0xff9a9aa2 : 0xff75757d;
        accent = dark ? 0xff5ea3ff : 0xff0a6cff;
        divider = dark ? 0xff26262a : 0xffececf0;
        good = dark ? 0xff4fd18b : 0xff1a9e5a;
        warn = dark ? 0xffffb44f : 0xffd97a00;
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);
        if (!dark) getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        auth = new CodexAuth(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(background);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), 0, dp(14), dp(40));
        scroll.addView(page);
        setContentView(scroll);

        largeTitle();
        hero();

        category(I18n.t("voice_input"));
        LinearLayout speech = card();
        primary = row(speech, I18n.t("recognition_language"), () -> pickPrimary());
        additional = row(speech, I18n.t("additional_languages"), () -> pickAdditional());
        silence = row(speech, I18n.t("finish_when_silent"), this::pickSilence);
        keyRow = row(speech, I18n.t("google_api_key"), this::apiKeyDialog);
        LinearLayout feedback = card();
        aurora = toggle(feedback, I18n.t("aurora_effect"), I18n.t("animated_light_on_the_toolbar_during_voice_input"));
        haptic = toggle(feedback, I18n.t("vibration"), I18n.t("short_vibration_on_start_and_finish"));
        sound = toggle(feedback, I18n.t("sound"), I18n.t("silent_in_mute_and_do_not_disturb_modes"));
        note(I18n.t("audio_and_the_paragraph_before_the_cursor_up_to_2_000_char"));

        category(I18n.t("ai_translation"));
        LinearLayout switchCard = card();
        enabled = toggle(switchCard, I18n.t("codex_translation"), I18n.t("translate_with_codex_in_samsung_keyboard"));
        LinearLayout engine = card();
        account = row(engine, I18n.t("account"), this::accountDialog);
        model = row(engine, I18n.t("model"), this::modelDialog);
        reasoning = row(engine, I18n.t("reasoning_effort"), this::reasoningDialog);
        prompt = row(engine, I18n.t("translation_instructions"), this::promptDialog);
        fallback = toggle(engine, I18n.t("use_samsung_translation_on_failure"), I18n.t("retry_with_default_translation_if_codex_fails"));
        translationDependents.add(engine);
        note(I18n.t("text_to_translate_is_sent_to_chatgpt_codex_when_the_codex"));

        category(I18n.t("about"));
        LinearLayout about = card();
        framework = row(about, I18n.t("lsposed_connection"), this::load);
        row(about, I18n.t("reload_settings"), I18n.t("read_the_values_applied_to_the_keyboard"), this::load);
        row(about, I18n.t("version"), "HoneyBoard X " + version() + I18n.t("samsung_keyboard_5_9_40_75"), null);
        row(about, I18n.t("created_by"), "sampple-korea", () -> startActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sampple-korea"))));
        TextView credit = text("Made by sampple-korea", 13, muted);
        credit.setGravity(Gravity.CENTER);
        credit.setPadding(0, dp(28), 0, 0);
        page.addView(credit, new LinearLayout.LayoutParams(-1, -2));

        enabled.setOnCheckedChangeListener((b, on) -> { updateDependents(); changed(); });
        for (Switch s : new Switch[]{aurora, haptic, sound, fallback})
            s.setOnCheckedChangeListener((b, on) -> changed());
        refreshValues();
        SettingsBridge.addListener(serviceChanged);
        load();
    }

    /** LSPosed binds its service asynchronously after process start; reload as soon as it arrives. */
    private final Runnable serviceChanged = () -> { if (!isDestroyed()) load(); };
    private long openedAt = android.os.SystemClock.uptimeMillis();

    @Override protected void onDestroy() {
        SettingsBridge.removeListener(serviceChanged);
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        if (account != null) refreshValues();
    }

    @Override protected void onPause() {
        super.onPause();
        if (main.hasCallbacks(persist)) { main.removeCallbacks(persist); persistNow(); }
    }

    // ---------------------------------------------------------------- layout

    private void largeTitle() {
        TextView title = text("HoneyBoard X", 32, textColor);
        title.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(96), 0, dp(6));
        page.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView tagline = text(I18n.t("samsung_keyboard_extension_voice_input_and_ai_translation"), 14, muted);
        tagline.setGravity(Gravity.CENTER);
        tagline.setPadding(0, 0, 0, dp(40));
        page.addView(tagline, new LinearLayout.LayoutParams(-1, -2));
    }

    private void hero() {
        LinearLayout card = card();
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        heroDot = new View(this);
        heroDot.setBackground(round(muted, dp(6)));
        line.addView(heroDot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        heroStatus = text(I18n.t("checking_status"), 18, textColor);
        heroStatus.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heroStatus.setPadding(dp(10), 0, 0, 0);
        line.addView(heroStatus);
        card.addView(line);
        heroDetail = text(I18n.t("enable_the_module_in_lsposed_and_add_samsung_keyboard_to_i"), 13, muted);
        heroDetail.setPadding(dp(20), dp(6), 0, 0);
        card.addView(heroDetail);
    }

    private void category(String title) {
        TextView label = text(title, 14, accent);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setPadding(dp(22), dp(26), 0, dp(8));
        page.addView(label);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(cardColor, dp(26)));
        c.setClipToOutline(true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(10);
        page.addView(c, p);
        return c;
    }

    private void separator(LinearLayout group) {
        if (group.getChildCount() == 0) return;
        View line = new View(this);
        line.setBackgroundColor(divider);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, Math.max(1, dp(1) / 2));
        p.leftMargin = dp(22); p.rightMargin = dp(22);
        group.addView(line, p);
    }

    /** Title with its current value underneath in the accent color (One UI list preference). */
    private TextView row(LinearLayout group, String title, Runnable action) {
        return row(group, title, "", action, true);
    }

    private TextView row(LinearLayout group, String title, String summary, Runnable action) {
        return row(group, title, summary, action, false);
    }

    private TextView row(LinearLayout group, String title, String summary, Runnable action, boolean valueStyle) {
        separator(group);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(22), dp(15), dp(22), dp(15));
        box.setMinimumHeight(dp(64));
        TextView label = text(title, 17, textColor);
        box.addView(label);
        TextView detail = text(summary, 14, valueStyle ? accent : muted);
        detail.setPadding(0, dp(2), 0, 0);
        detail.setMaxLines(2);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        if (!valueStyle && summary.isEmpty()) detail.setVisibility(View.GONE);
        box.addView(detail);
        if (action != null) {
            box.setBackground(ripple());
            box.setOnClickListener(v -> safe(action));
        }
        group.addView(box, new LinearLayout.LayoutParams(-1, -2));
        return detail;
    }

    private Switch toggle(LinearLayout group, String title, String summary) {
        separator(group);
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(22), dp(13), dp(16), dp(13));
        box.setMinimumHeight(dp(64));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(title, 17, textColor));
        TextView detail = text(summary, 14, muted);
        detail.setPadding(0, dp(2), dp(8), 0);
        words.addView(detail);
        box.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        sw.setContentDescription(title);
        sw.setClickable(false);
        sw.setFocusable(false);
        box.addView(sw);
        box.setBackground(ripple());
        box.setOnClickListener(v -> sw.setChecked(!sw.isChecked()));
        group.addView(box, new LinearLayout.LayoutParams(-1, -2));
        return sw;
    }

    private void note(String content) {
        TextView t = text(content, 13, muted);
        t.setLineSpacing(dp(2), 1f);
        t.setPadding(dp(22), dp(10), dp(22), 0);
        page.addView(t);
    }

    private TextView text(String value, int sp, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        return v;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private RippleDrawable ripple() {
        return new RippleDrawable(ColorStateList.valueOf(0x1f808080), null, round(Color.WHITE, 0));
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private String version() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Throwable e) { return "1.0"; }
    }

    // ---------------------------------------------------------------- state

    private TextView keyRow;
    private String apiKey = "";

    private void refreshValues() {
        binding = true;
        if (keyRow != null) {
            boolean custom = !apiKey.isEmpty(), builtIn = !BuildKeys.GOOGLE_API_KEY.isEmpty();
            keyRow.setText(I18n.t(custom ? "api_key_custom" : builtIn ? "api_key_built_in" : "api_key_missing"));
            keyRow.setTextColor(custom || builtIn ? accent : warn);
        }
        try {
            primary.setText(voice.primary.isEmpty() ? I18n.t("follow_keyboard_language") : languageName(voice.primary));
            additional.setText(voice.additional.isEmpty() ? I18n.t("off") : joinNames(voice.additionalTags()));
            silence.setText(voice.silenceSeconds == 0 ? I18n.t("off_finish_with_mic_button")
                    : I18n.t("silence_finish", voice.silenceSeconds));
            aurora.setChecked(voice.aurora); haptic.setChecked(voice.haptic); sound.setChecked(voice.sound);
            enabled.setChecked(translation.enabled); fallback.setChecked(translation.fallback);
            model.setText(translation.model);
            int effort = Arrays.asList(EFFORTS).indexOf(translation.reasoning);
            reasoning.setText(effort < 0 ? translation.reasoning : effortLabels()[effort]);
            prompt.setText(TranslationOptions.DEFAULT_PROMPT.equals(translation.prompt) ? I18n.t("default_instructions") : I18n.t("custom"));
            boolean connected = auth.connected();
            account.setText(connected ? I18n.t("signed_in") : auth.pending() ? I18n.t("finish_sign_in_paste_callback_url") : I18n.t("sign_in_required"));
            account.setTextColor(connected ? accent : warn);
            updateDependents();
        } finally { binding = false; }
    }

    private void updateDependents() {
        boolean on = enabled.isChecked();
        for (View v : translationDependents) setEnabledDeep(v, on);
    }

    private void setEnabledDeep(View v, boolean on) {
        v.setEnabled(on);
        v.setAlpha(on ? 1f : .4f);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View child = g.getChildAt(i);
                child.setEnabled(on);
                if (child instanceof ViewGroup) setEnabledDeep(child, on);
                child.setAlpha(1f);
            }
        }
    }

    private void setHero(boolean ok, String status, String detail) {
        heroDot.setBackground(round(ok ? good : warn, dp(6)));
        heroStatus.setText(status);
        heroDetail.setText(detail);
    }

    /** Any edit: rebuild the option objects from the UI and save shortly after. */
    private void changed() {
        if (binding || !loaded) return;
        try {
            voice = new VoiceOptions(voice.primary, voice.additional, voice.silenceSeconds,
                    aurora.isChecked(), haptic.isChecked(), sound.isChecked());
            translation = new TranslationOptions(enabled.isChecked(), translation.model, translation.reasoning,
                    translation.prompt, fallback.isChecked());
        } catch (Throwable error) { report(error); return; }
        refreshValues();
        main.removeCallbacks(persist);
        main.postDelayed(persist, 350);
    }

    private void load() {
        long token = ++request;
        if (!SettingsBridge.connected()) {
            // Binding normally completes within a second or two; show progress, not an error.
            boolean waiting = android.os.SystemClock.uptimeMillis() - openedAt < 6000;
            framework.setText(waiting ? I18n.t("connecting") : I18n.t("disconnected_tap_to_retry"));
            if (waiting) {
                setHero(false, I18n.t("connecting_to_lsposed"), I18n.t("please_wait"));
                main.postDelayed(() -> { if (!isDestroyed() && token == request) load(); }, 6200);
            } else {
                setHero(false, I18n.t("lsposed_connection_required"),
                        I18n.t("enable_honeyboard_x_in_lsposed_and_add_samsung_keyboard_to"));
            }
            return;
        }
        framework.setText(I18n.t("checking"));
        IO.execute(() -> {
            try {
                VoiceOptions v = VoiceOptions.read(SettingsBridge.remote(VoiceOptions.GROUP));
                TranslationOptions t = TranslationOptions.read(SettingsBridge.remote(TranslationOptions.GROUP));
                String status = SettingsBridge.frameworkStatus();
                runOnUiThread(() -> {
                    if (isDestroyed() || token != request) return;
                    voice = v; translation = t; apiKey = v.apiKey; loaded = true;
                    refreshValues();
                    framework.setText(status);
                    setHero(true, I18n.t("active"), I18n.t("settings_save_immediately_and_apply_to_the_next_voice_inpu"));
                });
            } catch (Throwable e) {
                runOnUiThread(() -> {
                    if (isDestroyed() || token != request) return;
                    Log.e("HoneyBoardX", "Remote preferences", e);
                    framework.setText(I18n.t("disconnected_tap_to_retry"));
                    setHero(false, I18n.t("lsposed_connection_required"),
                            I18n.t("enable_honeyboard_x_in_lsposed_add_samsung_keyboard_to_its"));
                });
            }
        });
    }

    private void persistNow() {
        if (!loaded) return;
        VoiceOptions v = voice; TranslationOptions t = translation;
        v.apiKey = apiKey;
        IO.execute(() -> {
            try {
                if (!v.write(SettingsBridge.remote(VoiceOptions.GROUP)).commit()
                        || !t.write(SettingsBridge.remote(TranslationOptions.GROUP)).commit())
                    throw new IllegalStateException(I18n.t("could_not_save_settings"));
            } catch (Throwable error) {
                Log.e("HoneyBoardX", "Save", error);
                runOnUiThread(() -> { if (!isDestroyed()) report(error); });
            }
        });
    }

    private void report(Throwable error) {
        String message = error.getMessage() == null ? I18n.t("unknown_error") : error.getMessage();
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void safe(Runnable action) { try { action.run(); } catch (Throwable error) { report(error); } }

    private void apply(Runnable edit) {
        try { edit.run(); } catch (Throwable error) { report(error); return; }
        refreshValues();
        main.removeCallbacks(persist);
        main.postDelayed(persist, 150);
    }

    private boolean ready() {
        if (!loaded) Toast.makeText(this, I18n.t("connect_to_lsposed_to_make_changes"), Toast.LENGTH_SHORT).show();
        return loaded;
    }

    // ---------------------------------------------------------------- voice pickers

    private static String languageName(String tag) {
        for (String[] l : LANGUAGES) if (l[0].equalsIgnoreCase(tag)) return l[1];
        return tag;
    }

    private static String joinNames(String[] tags) {
        StringBuilder b = new StringBuilder();
        for (String t : tags) { if (b.length() > 0) b.append(", "); b.append(languageName(t)); }
        return b.toString();
    }

    private void pickPrimary() {
        if (!ready()) return;
        String[] labels = new String[LANGUAGES.length + 2];
        labels[0] = I18n.t("follow_keyboard_language");
        for (int i = 0; i < LANGUAGES.length; i++) labels[i + 1] = LANGUAGES[i][1];
        labels[labels.length - 1] = I18n.t("enter_manually");
        int checked = 0;
        for (int i = 0; i < LANGUAGES.length; i++) if (LANGUAGES[i][0].equalsIgnoreCase(voice.primary)) checked = i + 1;
        new AlertDialog.Builder(this).setTitle(I18n.t("recognition_language"))
                .setSingleChoiceItems(labels, checked, (d, n) -> {
                    d.dismiss();
                    if (n == labels.length - 1) { typeLanguage(true); return; }
                    String tag = n == 0 ? "" : LANGUAGES[n - 1][0];
                    apply(() -> voice = new VoiceOptions(tag, voice.additional, voice.silenceSeconds,
                            voice.aurora, voice.haptic, voice.sound));
                }).setNegativeButton(I18n.t("cancel"), null).show();
    }

    private void pickAdditional() {
        if (!ready()) return;
        String[] labels = new String[LANGUAGES.length];
        boolean[] checked = new boolean[LANGUAGES.length];
        List<String> current = Arrays.asList(voice.additionalTags());
        for (int i = 0; i < LANGUAGES.length; i++) {
            labels[i] = LANGUAGES[i][1];
            for (String c : current) if (c.equalsIgnoreCase(LANGUAGES[i][0])) checked[i] = true;
        }
        new AlertDialog.Builder(this).setTitle(I18n.t("additional_languages_up_to_3"))
                .setMultiChoiceItems(labels, checked, (d, n, on) -> checked[n] = on)
                .setPositiveButton(I18n.t("done"), (d, n) -> {
                    List<String> tags = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) if (checked[i]) tags.add(LANGUAGES[i][0]);
                    apply(() -> voice = new VoiceOptions(voice.primary, String.join(",", tags), voice.silenceSeconds,
                            voice.aurora, voice.haptic, voice.sound));
                })
                .setNeutralButton(I18n.t("enter_manually"), (d, n) -> typeLanguage(false))
                .setNegativeButton(I18n.t("cancel"), null).show();
    }

    private void typeLanguage(boolean mainLanguage) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setHint(mainLanguage ? I18n.t("e_g_ko_kr") : I18n.t("e_g_en_us_ja_jp"));
        field.setText(mainLanguage ? voice.primary : voice.additional);
        new AlertDialog.Builder(this).setTitle(I18n.t("language_code_bcp_47")).setView(padded(field))
                .setPositiveButton(I18n.t("ok"), (d, n) -> apply(() -> voice = new VoiceOptions(
                        mainLanguage ? field.getText().toString() : voice.primary,
                        mainLanguage ? voice.additional : field.getText().toString(),
                        voice.silenceSeconds, voice.aurora, voice.haptic, voice.sound)))
                .setNegativeButton(I18n.t("cancel"), null).show();
    }

    private void apiKeyDialog() {
        if (!ready()) return;
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        field.setTypeface(Typeface.MONOSPACE);
        field.setHint("AIza…");
        field.setText(apiKey);
        new AlertDialog.Builder(this).setTitle(I18n.t("google_api_key"))
                .setMessage(I18n.t("api_key_help"))
                .setView(padded(field))
                .setPositiveButton(android.R.string.ok, (d, n) -> {
                    String key = field.getText().toString().trim();
                    if (!VoiceOptions.validKey(key)) { Toast.makeText(this, I18n.t("api_key_invalid"), Toast.LENGTH_LONG).show(); return; }
                    apply(() -> apiKey = key);
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void pickSilence() {
        if (!ready()) return;
        String[] labels = new String[SILENCE.length];
        int checked = 0;
        for (int i = 0; i < SILENCE.length; i++) {
            labels[i] = SILENCE[i] == 0 ? I18n.t("off") : SILENCE[i] + I18n.t("s");
            if (SILENCE[i] == voice.silenceSeconds) checked = i;
        }
        new AlertDialog.Builder(this).setTitle(I18n.t("finish_when_silent"))
                .setSingleChoiceItems(labels, checked, (d, n) -> {
                    d.dismiss();
                    apply(() -> voice = new VoiceOptions(voice.primary, voice.additional, SILENCE[n],
                            voice.aurora, voice.haptic, voice.sound));
                }).setNegativeButton(I18n.t("cancel"), null).show();
    }

    // ---------------------------------------------------------------- translation pickers

    private void setTranslation(String modelId, String effort, String instructions) {
        apply(() -> translation = new TranslationOptions(enabled.isChecked(), modelId, effort, instructions,
                fallback.isChecked()));
    }

    private void modelDialog() {
        if (!ready()) return;
        String[] common = {"gpt-6-luna", "gpt-6-sol", "gpt-6-astra", "gpt-5.6-luna", "gpt-5.6-terra", "gpt-5.6-sol"};
        String[] labels = {I18n.t("gpt_6_luna_fast"), I18n.t("gpt_6_sol_balanced"), I18n.t("gpt_6_astra_high_quality"), "gpt-5.6-luna", "gpt-5.6-terra", "gpt-5.6-sol"};
        int checked = Arrays.asList(common).indexOf(translation.model);
        new AlertDialog.Builder(this).setTitle(I18n.t("translation_model"))
                .setSingleChoiceItems(labels, checked, (d, n) -> {
                    d.dismiss(); setTranslation(common[n], translation.reasoning, translation.prompt);
                })
                .setNeutralButton(I18n.t("account_models"), (d, n) -> background(() -> new CodexAccount(this).models(), list -> {
                    @SuppressWarnings("unchecked") List<String> names = (List<String>) list;
                    if (names.isEmpty()) { Toast.makeText(this, I18n.t("no_models_available"), Toast.LENGTH_LONG).show(); return; }
                    new AlertDialog.Builder(this).setTitle(I18n.t("models_available_to_this_account"))
                            .setSingleChoiceItems(names.toArray(new String[0]), names.indexOf(translation.model),
                                    (dialog, i) -> { dialog.dismiss(); setTranslation(names.get(i), translation.reasoning, translation.prompt); })
                            .setNegativeButton(I18n.t("cancel"), null).show();
                }))
                .setPositiveButton(I18n.t("enter_manually"), (d, n) -> {
                    EditText field = new EditText(this);
                    field.setSingleLine();
                    field.setText(translation.model);
                    new AlertDialog.Builder(this).setTitle(I18n.t("model_id")).setView(padded(field))
                            .setPositiveButton(I18n.t("ok"), (dialog, i) -> setTranslation(field.getText().toString(),
                                    translation.reasoning, translation.prompt))
                            .setNegativeButton(I18n.t("cancel"), null).show();
                }).show();
    }

    private void reasoningDialog() {
        if (!ready()) return;
        new AlertDialog.Builder(this).setTitle(I18n.t("reasoning_effort"))
                .setSingleChoiceItems(effortLabels(), Arrays.asList(EFFORTS).indexOf(translation.reasoning), (d, n) -> {
                    d.dismiss(); setTranslation(translation.model, EFFORTS[n], translation.prompt);
                }).setNegativeButton(I18n.t("cancel"), null).show();
    }

    private void promptDialog() {
        if (!ready()) return;
        EditText field = new EditText(this);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        field.setMinLines(8);
        field.setGravity(Gravity.TOP);
        field.setTextSize(14);
        field.setText(translation.prompt);
        ScrollView outer = new ScrollView(this);
        outer.addView(field);
        new AlertDialog.Builder(this).setTitle(I18n.t("translation_instructions"))
                .setMessage(I18n.t("source_language_and_target_language_are_replaced_with_lang"))
                .setView(padded(outer))
                .setPositiveButton(I18n.t("save"), (d, n) -> setTranslation(translation.model, translation.reasoning,
                        field.getText().toString()))
                .setNeutralButton(I18n.t("reset_to_default"), (d, n) -> setTranslation(translation.model, translation.reasoning,
                        TranslationOptions.DEFAULT_PROMPT))
                .setNegativeButton(I18n.t("cancel"), null).show();
    }

    private void accountDialog() {
        if (auth.connected()) {
            String[] actions = {I18n.t("usage_and_models"), I18n.t("sign_out")};
            new AlertDialog.Builder(this).setTitle(I18n.t("chatgpt_account")).setItems(actions, (d, n) -> {
                if (n == 0) showUsage();
                else new AlertDialog.Builder(this).setTitle(I18n.t("sign_out_2"))
                        .setMessage(I18n.t("this_removes_sign_in_details_saved_on_this_device"))
                        .setPositiveButton(I18n.t("sign_out"), (dialog, which) -> { auth.signOut(); refreshValues(); })
                        .setNegativeButton(I18n.t("cancel"), null).show();
            }).show();
        } else if (auth.pending()) callbackDialog();
        else new AlertDialog.Builder(this).setTitle(I18n.t("sign_in_to_chatgpt"))
                .setMessage(I18n.t("sign_in_in_your_browser_when_you_see_can_t_connect_to_loca"))
                .setPositiveButton(I18n.t("open_browser"), (d, n) -> background(() -> auth.loginUrl(), url -> {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(String.valueOf(url))));
                    refreshValues();
                }))
                .setNegativeButton(I18n.t("cancel"), null).show();
    }

    // ---------------------------------------------------------------- usage & models sheet

    private static final class Limit {
        final String group; final double used; final long window, resetAt;
        Limit(String group, double used, long window, long resetAt) {
            this.group = group; this.used = used; this.window = window; this.resetAt = resetAt;
        }
    }

    /** Same traversal as the original module: find every object carrying used/remaining percent. */
    private static void collect(org.json.JSONObject o, String group, long nowSec, List<Limit> out, int depth) {
        if (o == null || depth > 5) return;
        if (!o.has("used_percent") && !o.has("remaining_percent")) {
            List<String> keys = new ArrayList<>();
            o.keys().forEachRemaining(keys::add);
            java.util.Collections.sort(keys, (a, b) -> {
                int pa = a.startsWith("primary") ? 0 : a.startsWith("secondary") ? 1 : 2;
                int pb = b.startsWith("primary") ? 0 : b.startsWith("secondary") ? 1 : 2;
                return pa != pb ? pa - pb : a.compareTo(b);
            });
            for (String k : keys) {
                collect(o.optJSONObject(k), group, nowSec, out, depth + 1);
                org.json.JSONArray arr = o.optJSONArray(k);
                if (arr != null) for (int i = 0; i < arr.length(); i++) collect(arr.optJSONObject(i), group, nowSec, out, depth + 1);
            }
            return;
        }
        double used = o.has("used_percent") ? o.optDouble("used_percent") : 100 - o.optDouble("remaining_percent");
        if (!Double.isFinite(used)) return;
        long window = o.optLong("limit_window_seconds", o.optLong("window_minutes", 0) * 60);
        long reset = o.optLong("reset_at", o.optLong("resets_at", 0));
        if (reset == 0 && o.has("reset_after_seconds")) reset = nowSec + o.optLong("reset_after_seconds");
        out.add(new Limit(group, Math.max(0, Math.min(100, used)), window, reset));
    }

    private static String windowLabel(long seconds) {
        if (seconds <= 0) return "";
        if (seconds == 18000) return I18n.t("5_hours");
        if (seconds == 604800) return I18n.t("weekly");
        if (seconds % 86400 == 0) return I18n.t("duration_days", seconds / 86400);
        if (seconds % 3600 == 0) return I18n.t("duration_hours", seconds / 3600);
        return I18n.t("duration_minutes", Math.max(1, seconds / 60));
    }

    private static String groupLabel(String group) {
        switch (group) {
            case "Codex": return "Codex";
            case "code_review": return I18n.t("code_review");
            case "account_usage": return I18n.t("account_usage");
            default: return group;
        }
    }

    private static String until(long resetAt, long nowSec) {
        if (resetAt <= 0) return "";
        long left = Math.max(0, resetAt - nowSec);
        java.util.Locale locale = I18n.displayLocale();
        String pattern = left > 86400 ? I18n.t("mmm_d_h_mm_a") : "a h:mm";
        String when = new java.text.SimpleDateFormat(pattern, locale)
                .format(new java.util.Date(resetAt * 1000));
        String rel = left >= 86400 ? I18n.t("reset_days_hours", left / 86400, left % 86400 / 3600)
                : left >= 3600 ? I18n.t("reset_hours_minutes", left / 3600, left % 3600 / 60)
                : I18n.t("reset_minutes", Math.max(1, left / 60));
        return I18n.t("reset_at", when, rel);
    }

    private View meter(double remaining) {
        int fill = remaining >= 50 ? accent : remaining >= 20 ? warn : 0xffff5a5f;
        View bar = new View(this) {
            final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(android.graphics.Canvas c) {
                float h = getHeight(), w = getWidth(), r = h / 2f;
                p.setColor(divider);
                c.drawRoundRect(0, 0, w, h, r, r, p);
                float x = (float) (w * remaining / 100.0);
                if (x > 0) { p.setColor(fill); c.drawRoundRect(0, 0, Math.max(h, x), h, r, r, p); }
            }
        };
        return bar;
    }

    private void showUsage() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), dp(4), dp(24), dp(8));
        android.widget.ProgressBar spinner = new android.widget.ProgressBar(this);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(40), dp(40));
        sp.gravity = Gravity.CENTER_HORIZONTAL; sp.topMargin = dp(24); sp.bottomMargin = dp(24);
        body.addView(spinner, sp);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(I18n.t("usage_and_models")).setView(scroll)
                .setPositiveButton(I18n.t("close"), null).show();
        background(() -> {
            CodexAccount client = new CodexAccount(this);
            org.json.JSONObject usage = client.fetch("usage");
            List<String> models;
            try { models = client.models(); } catch (Throwable e) { models = new ArrayList<>(); }
            return new Object[]{usage, models};
        }, result -> {
            if (!dialog.isShowing()) return;
            Object[] r = (Object[]) result;
            @SuppressWarnings("unchecked") List<String> models = (List<String>) r[1];
            fillUsage(body, (org.json.JSONObject) r[0], models, dialog);
        });
    }

    private void fillUsage(LinearLayout body, org.json.JSONObject usage, List<String> models, AlertDialog dialog) {
        body.removeAllViews();
        long now = System.currentTimeMillis() / 1000;
        String plan = usage.optString("plan_type", "");
        if (!plan.isEmpty()) {
            TextView chip = text("ChatGPT " + Character.toUpperCase(plan.charAt(0)) + plan.substring(1), 13, accent);
            chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            chip.setBackground(round((accent & 0x00ffffff) | 0x22000000, dp(12)));
            chip.setPadding(dp(12), dp(5), dp(12), dp(5));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
            cp.topMargin = dp(8);
            body.addView(chip, cp);
        }
        List<Limit> limits = new ArrayList<>();
        collect(usage.optJSONObject("rate_limit"), "Codex", now, limits, 0);
        collect(usage.optJSONObject("code_review_rate_limit"), "code_review", now, limits, 0);
        org.json.JSONArray extra = usage.optJSONArray("additional_rate_limits");
        if (extra != null) for (int i = 0; i < extra.length(); i++) {
            org.json.JSONObject e = extra.optJSONObject(i);
            if (e != null) collect(e.optJSONObject("rate_limit"), e.optString("limit_name", e.optString("metered_feature", I18n.t("additional_limit"))), now, limits, 0);
        }
        collect(usage.optJSONObject("spend_control"), "account_usage", now, limits, 0);
        collect(usage.optJSONObject("rate_limits"), "Codex", now, limits, 0);

        boolean exhausted = false;
        for (Limit l : limits) if ("Codex".equals(l.group) && l.used >= 100) exhausted = true;
        if (exhausted) {
            TextView banner = text(I18n.t("codex_usage_is_exhausted_translation_will_use_gpt_reserve"), 14, textColor);
            banner.setBackground(round((warn & 0x00ffffff) | 0x2e000000, dp(16)));
            banner.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
            bp.topMargin = dp(14);
            body.addView(banner, bp);
        }
        if (limits.isEmpty()) {
            TextView none = text(I18n.t("no_usage_data_available"), 14, muted);
            none.setPadding(0, dp(16), 0, 0);
            body.addView(none);
        }
        for (Limit l : limits) {
            double remaining = 100 - l.used;
            LinearLayout head = new LinearLayout(this);
            head.setGravity(Gravity.BOTTOM);
            head.setPadding(0, dp(20), 0, dp(8));
            String title = groupLabel(l.group) + (l.window > 0 ? " · " + windowLabel(l.window) : "");
            head.addView(text(title, 16, textColor), new LinearLayout.LayoutParams(0, -2, 1));
            TextView pct = text(Math.round(remaining) + "%", 22, remaining >= 50 ? textColor : remaining >= 20 ? warn : 0xffff5a5f);
            pct.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            head.addView(pct);
            TextView left = text(I18n.t("remaining"), 13, muted);
            head.addView(left);
            body.addView(head);
            body.addView(meter(remaining), new LinearLayout.LayoutParams(-1, dp(8)));
            String reset = until(l.resetAt, now);
            if (!reset.isEmpty()) {
                TextView r = text(reset, 13, muted);
                r.setPadding(0, dp(6), 0, 0);
                body.addView(r);
            }
        }

        if (!models.isEmpty()) {
            TextView h = text(I18n.t("available_models_tap_to_choose"), 14, accent);
            h.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            h.setPadding(0, dp(28), 0, dp(6));
            body.addView(h);
            for (String m : models) {
                boolean current = m.equals(translation.model);
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(4), dp(12), dp(4), dp(12));
                row.setBackground(ripple());
                TextView name = text(m, 15, current ? accent : textColor);
                if (current) name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
                if (current) row.addView(text(I18n.t("active"), 13, accent));
                row.setOnClickListener(v -> {
                    if (!ready()) return;
                    setTranslation(m, translation.reasoning, translation.prompt);
                    Toast.makeText(this, I18n.t("model_selected", m), Toast.LENGTH_SHORT).show();
                    fillUsage(body, usage, models, dialog);
                });
                body.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
        }
    }

    private void callbackDialog() {
        EditText field = new EditText(this);
        field.setHint("http://localhost:1455/auth/callback?code=…");
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this).setTitle(I18n.t("paste_callback_url"))
                .setMessage(I18n.t("paste_the_localhost_callback_url_from_your_browser_s_addre"))
                .setView(padded(field))
                .setPositiveButton(I18n.t("complete_sign_in"), (d, n) -> {
                    String pasted = field.getText().toString();
                    background(() -> { auth.finish(pasted); return I18n.t("signed_in_2"); },
                            value -> { refreshValues(); Toast.makeText(this, String.valueOf(value), Toast.LENGTH_LONG).show(); });
                })
                .setNeutralButton(I18n.t("start_over"), (d, n) -> { auth.signOut(); refreshValues(); accountDialog(); })
                .setNegativeButton(I18n.t("later"), null).show();
    }

    private View padded(View content) {
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(22), dp(8), dp(22), 0);
        box.addView(content);
        return box;
    }

    private interface Work { Object run() throws Exception; }
    private interface Done { void accept(Object result); }

    private void background(Work task, Done done) {
        IO.execute(() -> {
            try {
                Object result = task.run();
                runOnUiThread(() -> { if (!isDestroyed()) safe(() -> done.accept(result)); });
            } catch (Throwable error) {
                runOnUiThread(() -> { if (!isDestroyed()) report(error); });
            }
        });
    }
}
