package dev.sampple.rambler;

import android.content.SharedPreferences;
import java.util.LinkedHashSet;
import java.util.Locale;

/** Validated immutable settings, frozen for the lifetime of one visible panel. */
final class VoiceOptions {
    static final String GROUP = "voice";
    final String primary, additional;
    final int silenceSeconds;
    final boolean aurora, haptic, sound;
    /** User-entered Gboard API key; empty = use the key compiled into this build (if any). */
    String apiKey = "";

    VoiceOptions(String primary, String additional, int seconds, boolean aurora, boolean haptic, boolean sound) {
        this.primary = language(primary, true);
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (additional != null) for (String tag : additional.split(",")) {
            if (!tag.trim().isEmpty()) tags.add(language(tag, false));
        }
        if (tags.size() > 3) throw new IllegalArgumentException(I18n.t("choose_up_to_3_additional_languages"));
        this.additional = String.join(",", tags);
        if (seconds < 0 || seconds > 30 || seconds == 1)
            throw new IllegalArgumentException(I18n.t("silence_timeout_must_be_off_0_or_2_30_seconds"));
        silenceSeconds = seconds; this.aurora = aurora; this.haptic = haptic; this.sound = sound;
    }

    static VoiceOptions defaults() { return new VoiceOptions("", "", 3, true, true, false); }
    static VoiceOptions read(SharedPreferences p) {
        VoiceOptions o = new VoiceOptions(p.getString("primary", ""), p.getString("additional", ""),
                p.getInt("silence", 3), p.getBoolean("aurora", true),
                p.getBoolean("haptic", true), p.getBoolean("sound", false));
        o.apiKey = p.getString("api_key", "").trim();
        return o;
    }
    static boolean validKey(String key) { return key.isEmpty() || key.matches("AIza[0-9A-Za-z_-]{35}"); }
    /** Key actually used for requests: user key first, then the build-time key. */
    String effectiveApiKey() { return !apiKey.isEmpty() ? apiKey : BuildKeys.GOOGLE_API_KEY; }
    SharedPreferences.Editor write(SharedPreferences p) {
        return p.edit().putString("primary", primary).putString("additional", additional)
                .putInt("silence", silenceSeconds).putBoolean("aurora", aurora)
                .putBoolean("haptic", haptic).putBoolean("sound", sound).putBoolean("configured", true).putString("api_key", apiKey);
    }
    String[] additionalTags() { return additional.isEmpty() ? new String[0] : additional.split(","); }
    static String language(String tag, boolean auto) {
        String value = tag == null ? "" : tag.trim();
        if (auto && value.isEmpty()) return "";
        if (!value.matches("[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*"))
            throw new IllegalArgumentException(I18n.t("check_the_language_tag_e_g_ko_kr_en_us"));
        Locale locale;
        try { locale = new Locale.Builder().setLanguageTag(value).build(); }
        catch (RuntimeException e) { throw new IllegalArgumentException(I18n.t("invalid_language_tag") + value); }
        value = locale.toLanguageTag();
        if (value.equalsIgnoreCase("zh-CN")) return "zh-Hans";
        if (value.equalsIgnoreCase("zh-TW")) return "zh-Hant";
        if (value.equalsIgnoreCase("zh-HK")) return "yue";
        return value;
    }
}
