package dev.sampple.rambler;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.sampple.rambler.ui.AuroraView;
import dev.sampple.rambler.ui.WaveView;

/** Single-row voice header; only its strip consumes touches, never the keys below. */
public class VoicePanel extends FrameLayout {
    private static final String TAG = "RamblerVoicePanel";
    private AuroraView aurora;
    private TextView status;
    private FrameLayout micButton;
    private IconButton closeButton;
    private boolean listening;
    private boolean running;
    private boolean paused;
    private boolean customStatus;
    private int waveState = WaveView.IDLE;

    public VoicePanel(Context context, Runnable onToggleMic, Runnable onPauseMic, Runnable onClose, boolean enableAurora) {
        super(context);
        setClickable(true); // Consume touches on the strip, not on the keyboard below.
        setFocusable(false);
        try {
            // Keep the keyboard's opaque toolbar backing beneath the animated layer.
            // API < 33 uses only that backing and the ordinary controls.
            try {
                if (enableAurora && Build.VERSION.SDK_INT >= 33) {
                    aurora = new AuroraView(context);
                    aurora.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                    addView(aurora, new FrameLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                }
            } catch (Throwable error) {
                Log.e(TAG, "Aurora creation failed", error);
                aurora = null;
            }

            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.HORIZONTAL);
            content.setGravity(Gravity.CENTER_VERTICAL);
            addView(content, new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            closeButton = new IconButton(context, false);
            closeButton.setContentDescription(I18n.t("close_voice_input"));
            closeButton.setOnClickListener(v -> invoke(onClose, "close panel"));
            content.addView(closeButton, new LinearLayout.LayoutParams(dp(52), LayoutParams.MATCH_PARENT));

            status = new TextView(context);
            status.setGravity(Gravity.CENTER);
            status.setTextSize(14f);
            status.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            status.setLetterSpacing(.01f);
            status.setMaxLines(2);
            status.setEllipsize(TextUtils.TruncateAt.END);
            status.setText(I18n.t("speak_now"));
            status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            status.setPadding(dp(8), 0, dp(8), 0);
            content.addView(status, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));

            // The wrapper is the accessible 48dp-wide target; its visible disc is ~36dp.
            micButton = new FrameLayout(context) {
                @Override protected void dispatchDraw(Canvas canvas) {
                    try { super.dispatchDraw(canvas); }
                    catch (Throwable error) { Log.e(TAG, "Microphone drawing failed", error); }
                }
            };
            micButton.setClickable(true);
            micButton.setFocusable(true);
            micButton.setContentDescription(I18n.t("finish_voice_input"));
            micButton.setOnClickListener(v -> invoke(onToggleMic, "finish microphone"));
            // Feedback is controlled centrally; do not let long-click bypass its toggle.
            micButton.setHapticFeedbackEnabled(false);
            micButton.setOnLongClickListener(v -> { invoke(onPauseMic, "pause/resume microphone"); return true; });
            IconButton micIcon = new IconButton(context, true);
            micIcon.setClickable(false);
            micIcon.setFocusable(false);
            micIcon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            micButton.addView(micIcon, new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            content.addView(micButton, new LinearLayout.LayoutParams(dp(56), LayoutParams.MATCH_PARENT));
            this.micIcon = micIcon;
            updateTheme();
        } catch (Throwable error) {
            Log.e(TAG, "Header creation failed", error);
            // A partially built header must not crash the host keyboard.
            updateTheme();
        }
    }

    private IconButton micIcon;

    private static void invoke(Runnable callback, String action) {
        try {
            if (callback != null) callback.run();
        } catch (Throwable error) {
            Log.e(TAG, "UI callback failed: " + action, error);
        }
    }

    public void setStatus(String text) {
        try {
            customStatus = text != null;
            if (status != null) status.setText(customStatus ? text : defaultStatus());
        } catch (Throwable error) {
            Log.e(TAG, "Status update failed", error);
        }
    }

    private String defaultStatus() {
        return paused ? I18n.t("paused") : listening ? I18n.t("listening") : I18n.t("speak_now");
    }

    public void setListening(boolean value) {
        try {
            listening = value;
            if (value) setPaused(false);
            if (!customStatus && status != null) status.setText(defaultStatus());
            updateMicDescription();
            if (waveState == WaveView.IDLE || waveState == WaveView.LISTENING) {
                waveState = value ? WaveView.LISTENING : WaveView.IDLE;
                if (aurora != null) aurora.setState(waveState);
            }
        } catch (Throwable error) {
            Log.e(TAG, "Listening update failed", error);
        }
    }

    public void setPaused(boolean value) {
        try {
            paused = value;
            if (aurora != null) aurora.setPaused(value);
            updateMicDescription();
            if (!customStatus && status != null) status.setText(defaultStatus());
        } catch (Throwable error) {
            Log.e(TAG, "Pause update failed", error);
        }
    }

    private void updateMicDescription() {
        if (micButton != null) micButton.setContentDescription(
                paused ? I18n.t("tap_to_finish_hold_to_resume") : I18n.t("tap_to_finish_hold_to_pause"));
    }

    public void setLevel(float value) {
        try {
            if (aurora != null && Build.VERSION.SDK_INT >= 33) aurora.setLevel(value);
        } catch (Throwable error) {
            Log.e(TAG, "Audio level update failed", error);
        }
    }

    public void setWaveState(int value) {
        try {
            waveState = value;
            if (aurora != null) aurora.setState(value);
        } catch (Throwable error) {
            Log.e(TAG, "Animation state update failed", error);
        }
    }

    public void start() {
        running = true;
        try {
            if (aurora != null && Build.VERSION.SDK_INT >= 33) aurora.start();
        } catch (Throwable error) {
            Log.e(TAG, "Aurora start failed", error);
        }
    }

    public void stop() {
        running = false;
        try {
            if (aurora != null) aurora.stop();
        } catch (Throwable error) {
            Log.e(TAG, "Aurora stop failed", error);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        try { super.dispatchDraw(canvas); }
        catch (Throwable error) { Log.e(TAG, "Header drawing failed", error); }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        try { super.onMeasure(widthSpec, heightSpec); }
        catch (Throwable error) {
            Log.e(TAG, "Header measurement failed", error);
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec));
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        try { super.onLayout(changed, left, top, right, bottom); }
        catch (Throwable error) { Log.e(TAG, "Header layout failed", error); }
    }

    @Override
    protected void onAttachedToWindow() {
        try {
            super.onAttachedToWindow();
            if (running && aurora != null && Build.VERSION.SDK_INT >= 33) aurora.start();
        } catch (Throwable error) {
            Log.e(TAG, "Attach callback failed", error);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        try {
            if (aurora != null) aurora.stop();
        } catch (Throwable error) {
            Log.e(TAG, "Stop on detach failed", error);
        } finally {
            try { super.onDetachedFromWindow(); }
            catch (Throwable error) { Log.e(TAG, "Detach callback failed", error); }
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration configuration) {
        try { super.onConfigurationChanged(configuration); }
        catch (Throwable error) { Log.e(TAG, "Configuration callback failed", error); }
        updateTheme();
    }

    private void updateTheme() {
        try {
            boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
            int background = themeColor("beehive_primary_background",
                    themeColor("keyboard_background", dark ? 0xff222228 : 0xfffafafa));
            int text = themeColor("normal_key_text", dark ? Color.WHITE : 0xff202124);
            int icon = themeColor("bee_icon_enable_tint", text);
            int edit = themeColor("edit_bee_icon_tint", icon);
            // Theme overlays may use translucent toolbar colors; hide old Bee icons.
            if (Color.alpha(background) != 255) {
                int base = themeColor("keyboard_background", dark ? 0xff222228 : 0xfffafafa);
                float alpha = Color.alpha(background) / 255f;
                background = Color.rgb(
                        Math.round(Color.red(background) * alpha + Color.red(base) * (1f - alpha)),
                        Math.round(Color.green(background) * alpha + Color.green(base) * (1f - alpha)),
                        Math.round(Color.blue(background) * alpha + Color.blue(base) * (1f - alpha)));
            }
            setBackgroundColor(background);
            if (status != null) status.setTextColor(text);
            // Use the actual keyboard palette rather than the system night-mode flag.
            boolean darkPalette = .2126 * Color.red(background) + .7152 * Color.green(background)
                    + .0722 * Color.blue(background) < 128;
            if (aurora != null) aurora.setDarkTheme(darkPalette);
            if (closeButton != null) closeButton.setPalette(darkPalette, edit);
            if (micIcon != null) micIcon.setPalette(darkPalette, icon);
        } catch (Throwable error) {
            Log.e(TAG, "Header theme update failed", error);
        }
    }

    private int themeColor(String name, int fallback) {
        Context context = getContext();
        try {
            Resources resources = context.getResources();
            int attr = resources.getIdentifier(name, "attr", context.getPackageName());
            if (attr == 0 && !"com.samsung.android.honeyboard".equals(context.getPackageName())) {
                attr = resources.getIdentifier(name, "attr", "com.samsung.android.honeyboard");
            }
            if (attr == 0) return fallback;
            TypedValue value = new TypedValue();
            if (!context.getTheme().resolveAttribute(attr, value, true)) return fallback;
            if (value.resourceId != 0) return context.getColor(value.resourceId);
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && value.type <= TypedValue.TYPE_LAST_COLOR_INT) return value.data;
        } catch (Throwable error) {
            Log.w(TAG, "Theme color unavailable: " + name, error);
        }
        return fallback;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Icons are built once in code; no module resource IDs enter the keyboard process. */
    private static final class IconButton extends View {
        // Material "close" and "mic" (24dp viewport); sparkle is a Gemini-style 4-point star.
        private static final String CLOSE = "M19,6.41L17.59,5L12,10.59L6.41,5L5,6.41L10.59,12L5,17.59L6.41,19L12,13.41L17.59,19L19,17.59L13.41,12Z";
        private static final String MIC = "M12,14c1.66,0 3,-1.34 3,-3V5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3z"
                + "M17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11H5c0,3.41 2.72,6.23 6,6.72V21h2v-3.28c3.28,-0.49 6,-3.31 6,-6.72H17.3z";
        private static final String SPARKLE = "M19.5,1.5C19.8,3.3 20.7,4.2 22.5,4.5C20.7,4.8 19.8,5.7 19.5,7.5C19.2,5.7 18.3,4.8 16.5,4.5C18.3,4.2 19.2,3.3 19.5,1.5Z";

        private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path glyph;
        private final Path sparkle;
        private final boolean mic;
        private final float density;

        IconButton(Context context, boolean mic) {
            super(context);
            this.mic = mic;
            density = getResources().getDisplayMetrics().density;
            glyph = mic ? SvgPath.parse(MIC) : SvgPath.parse(CLOSE);
            sparkle = mic ? SvgPath.parse(SPARKLE) : null;
            disc.setStyle(Paint.Style.FILL);
            ink.setStyle(Paint.Style.FILL);
            accent.setStyle(Paint.Style.FILL);
        }

        void setPalette(boolean dark, int themeInk) {
            if (mic) {
                disc.setColor(dark ? 0xfff1efff : 0xffffffff);
                ink.setColor(0xff1f1d33);
                accent.setColor(0xff6b5cf5);
                disc.setShadowLayer(4f * density, 0f, 1f * density, dark ? 0x66000000 : 0x40503cc8);
            } else {
                disc.setColor(dark ? 0xcc3a3940 : 0xd9ffffff);
                ink.setColor(dark ? 0xffe8e6f0 : 0xff3c3a48);
                disc.setShadowLayer(2f * density, 0f, .5f * density, dark ? 0x40000000 : 0x26000000);
            }
            invalidate();
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            try {
                animate().scaleX(pressed ? .9f : 1f).scaleY(pressed ? .9f : 1f)
                        .setDuration(pressed ? 90 : 160).start();
            } catch (Throwable error) {
                Log.e(TAG, "Press animation failed", error);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            try {
                float cx = getWidth() * .5f;
                float cy = getHeight() * .5f;
                float max = Math.min(getWidth(), getHeight()) * .5f - 3f * density;
                float radius = Math.max(0f, Math.min((mic ? 20f : 17f) * density, max));
                canvas.drawCircle(cx, cy, radius, disc);
                float size = radius * (mic ? 1.1f : .95f);
                canvas.save();
                canvas.translate(cx - size * .5f, cy - size * .5f);
                canvas.scale(size / 24f, size / 24f);
                canvas.drawPath(glyph, ink);
                if (sparkle != null) canvas.drawPath(sparkle, accent);
                canvas.restore();
            } catch (Throwable error) {
                Log.e(TAG, "Icon drawing failed", error);
            }
        }
    }

    /** Minimal SVG path-data parser (M L H V C S Q Z, absolute and relative). */
    private static final class SvgPath {
        static Path parse(String d) {
            Path path = new Path();
            float x = 0, y = 0, sx = 0, sy = 0, cx = 0, cy = 0;
            char cmd = 0, prev = 0;
            int i = 0, n = d.length();
            float[] v = new float[6];
            while (i < n) {
                char c = d.charAt(i);
                if (Character.isLetter(c)) { cmd = c; i++; }
                else if (c == ',' || c == ' ') { i++; continue; }
                else if (cmd == 0) { i++; continue; }
                if (cmd == 'Z' || cmd == 'z') {
                    path.close(); x = sx; y = sy; prev = cmd; cmd = 0; continue;
                }
                int count;
                switch (Character.toUpperCase(cmd)) {
                    case 'H': case 'V': count = 1; break;
                    case 'M': case 'L': count = 2; break;
                    case 'S': case 'Q': count = 4; break;
                    case 'C': count = 6; break;
                    default: return path;
                }
                int[] pos = {i};
                for (int k = 0; k < count; k++) v[k] = number(d, pos);
                i = pos[0];
                boolean rel = Character.isLowerCase(cmd);
                float ox = rel ? x : 0, oy = rel ? y : 0;
                switch (Character.toUpperCase(cmd)) {
                    case 'M':
                        x = v[0] + ox; y = v[1] + oy; sx = x; sy = y; path.moveTo(x, y);
                        cmd = rel ? 'l' : 'L'; // implicit lineto after the first pair
                        cx = x; cy = y; break;
                    case 'L': x = v[0] + ox; y = v[1] + oy; path.lineTo(x, y); cx = x; cy = y; break;
                    case 'H': x = v[0] + ox; path.lineTo(x, y); cx = x; cy = y; break;
                    case 'V': y = v[0] + oy; path.lineTo(x, y); cx = x; cy = y; break;
                    case 'C':
                        path.cubicTo(v[0] + ox, v[1] + oy, v[2] + ox, v[3] + oy, v[4] + ox, v[5] + oy);
                        cx = v[2] + ox; cy = v[3] + oy; x = v[4] + ox; y = v[5] + oy; break;
                    case 'S': {
                        char p = Character.toUpperCase(prev);
                        float rx = (p == 'C' || p == 'S') ? 2 * x - cx : x;
                        float ry = (p == 'C' || p == 'S') ? 2 * y - cy : y;
                        path.cubicTo(rx, ry, v[0] + ox, v[1] + oy, v[2] + ox, v[3] + oy);
                        cx = v[0] + ox; cy = v[1] + oy; x = v[2] + ox; y = v[3] + oy; break;
                    }
                    case 'Q':
                        path.quadTo(v[0] + ox, v[1] + oy, v[2] + ox, v[3] + oy);
                        cx = v[0] + ox; cy = v[1] + oy; x = v[2] + ox; y = v[3] + oy; break;
                }
                prev = cmd;
            }
            return path;
        }

        private static float number(String d, int[] pos) {
            int i = pos[0], n = d.length();
            while (i < n && (d.charAt(i) == ',' || d.charAt(i) == ' ')) i++;
            int start = i;
            if (i < n && (d.charAt(i) == '-' || d.charAt(i) == '+')) i++;
            boolean dot = false;
            while (i < n) {
                char c = d.charAt(i);
                if (Character.isDigit(c)) i++;
                else if (c == '.' && !dot) { dot = true; i++; }
                else break;
            }
            pos[0] = i;
            return Float.parseFloat(d.substring(start, i));
        }
    }
}
