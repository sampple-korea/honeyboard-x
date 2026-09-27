package dev.sampple.rambler.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RuntimeShader;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

/**
 * Full-width Rambler header aurora, approximated from Gboard screenshots:
 * a rainbow halftone-dot burst on start that settles into a white/lavender
 * bottom-edge glow which undulates with the audio level. Requires API 33.
 */
public final class AuroraView extends View {
    private static final String TAG = "RamblerAurora";
    private static final float INTRO_HOLD = 0.9f;
    private static final float INTRO_FADE = 0.9f;

    private static final String SHADER =
            "uniform float2 uSize;\n" +
            "uniform float uTime;\n" +
            "uniform float uIntro;\n" +
            "uniform float uLevel;\n" +
            "uniform float uDark;\n" +
            "uniform float uAlpha;\n" +
            "float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }\n" +
            "float noise(float2 p) {\n" +
            "  float2 i = floor(p); float2 f = fract(p);\n" +
            "  float2 u = f * f * (3.0 - 2.0 * f);\n" +
            "  return mix(mix(hash(i), hash(i + float2(1, 0)), u.x),\n" +
            "             mix(hash(i + float2(0, 1)), hash(i + float2(1, 1)), u.x), u.y);\n" +
            "}\n" +
            "half4 main(float2 p) {\n" +
            "  float2 uv = p / uSize;\n" +
            "  float y = 1.0 - uv.y;\n" + // 0 at the bottom edge
            "  float px = uSize.y;\n" +
            // Settled glow: soft band hugging the bottom edge, height driven by voice.
            "  float w1 = sin(uv.x * 7.0 + uTime * 1.7);\n" +
            "  float w2 = sin(uv.x * 13.0 - uTime * 2.6);\n" +
            "  float w3 = noise(float2(uv.x * 5.0 + uTime * 0.8, uTime * 0.5));\n" +
            "  float h = 0.16 + 0.05 * (w1 * 0.5 + 0.5) + uLevel * (0.25 + 0.35 * (0.5 + 0.25 * w2 + 0.5 * w3));\n" +
            "  float glow = exp(-y / max(h, 0.02) * 2.4);\n" +
            "  float core = exp(-y * px / 3.0);\n" +
            "  float3 lav = uDark > 0.5 ? float3(0.78, 0.76, 1.0) : float3(0.58, 0.54, 0.96);\n" +
            "  float3 glowColor = mix(lav, float3(1.0), clamp(core * 0.9 + glow * 0.25, 0.0, 1.0));\n" +
            "  float glowAlpha = clamp(glow * 0.8 + core * 0.7, 0.0, 1.0);\n" +
            // Intro: rainbow sweep masked by a diagonal halftone dot lattice.
            "  float3 rainbow = 0.55 + 0.45 * cos(6.2831 * (uv.x * 0.85 - uTime * 0.18 + float3(0.0, 0.33, 0.67)));\n" +
            "  float n = noise(float2(uv.x * 5.0 + uTime * 0.7, y * 2.5 - uTime * 0.5));\n" +
            "  float rise = clamp((1.0 - y * 1.15) * (0.45 + 0.75 * n), 0.0, 1.0);\n" +
            "  float2 q = float2(p.x + p.y, p.x - p.y) * 0.70710678 / 7.0;\n" +
            "  float d = length(fract(q) - 0.5);\n" +
            "  float r = 0.46 * rise;\n" +
            "  float dots = 1.0 - smoothstep(r - 0.06, r + 0.02, d);\n" +
            "  float introAlpha = clamp(dots * rise * 1.1 + rise * rise * 0.25 + core * 0.6, 0.0, 1.0);\n" +
            "  float3 introColor = mix(rainbow, float3(1.0), core * 0.7);\n" +
            "  float3 c = mix(glowColor, introColor, uIntro);\n" +
            "  float a = mix(glowAlpha, introAlpha, uIntro) * uAlpha;\n" +
            "  return half4(half3(c * a), half(a));\n" +
            "}\n";

    private final Paint paint = new Paint();
    private RuntimeShader shader;
    private boolean running;
    private boolean paused;
    private boolean dark = true;
    private int state = WaveView.IDLE;
    private float targetLevel;
    private float level;
    private long startedAt;
    private long lastFrame;
    private float time;

    public AuroraView(Context context) {
        super(context);
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                shader = new RuntimeShader(SHADER);
                paint.setShader(shader);
            } catch (Throwable error) {
                Log.e(TAG, "Shader compile failed", error);
                shader = null;
            }
        }
        setWillNotDraw(false);
    }

    public void setLevel(float value) {
        // Raw RMS (0..1): map -50 dBFS..-12 dBFS onto 0..1 so speech visibly moves the glow.
        if (Float.isNaN(value) || value <= 0f) { targetLevel = 0f; return; }
        float db = (float) (20.0 * Math.log10(value));
        targetLevel = Math.max(0f, Math.min(1f, (db + 50f) / 38f));
    }

    public void setState(int value) {
        state = value;
        invalidate();
    }

    public void setPaused(boolean value) {
        paused = value;
        invalidate();
    }

    public void setDarkTheme(boolean value) {
        dark = value;
        invalidate();
    }

    public void start() {
        if (running) return;
        running = true;
        startedAt = SystemClock.uptimeMillis();
        lastFrame = startedAt;
        time = 0f;
        postInvalidateOnAnimation();
    }

    public void stop() {
        running = false;
        level = targetLevel = 0f;
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        try {
            if (shader == null || getWidth() == 0 || getHeight() == 0) return;
            long now = SystemClock.uptimeMillis();
            float dt = Math.min(0.1f, Math.max(0f, (now - lastFrame) / 1000f));
            lastFrame = now;
            boolean frozen = paused || state == WaveView.ERROR;
            time += dt * (frozen ? 0.25f : 1f);

            float goal = frozen ? 0f : targetLevel;
            level += (goal - level) * (goal > level ? 0.45f : 0.08f);

            float elapsed = running ? (now - startedAt) / 1000f : INTRO_HOLD + INTRO_FADE;
            float intro = elapsed <= INTRO_HOLD ? 1f
                    : Math.max(0f, 1f - (elapsed - INTRO_HOLD) / INTRO_FADE);
            intro = intro * intro * (3f - 2f * intro);

            float alpha = state == WaveView.ERROR ? 0.35f : paused ? 0.5f : 1f;
            shader.setFloatUniform("uSize", getWidth(), getHeight());
            shader.setFloatUniform("uTime", time);
            shader.setFloatUniform("uIntro", intro);
            shader.setFloatUniform("uLevel", level);
            shader.setFloatUniform("uDark", dark ? 1f : 0f);
            shader.setFloatUniform("uAlpha", alpha);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        } catch (Throwable error) {
            Log.e(TAG, "Aurora draw failed", error);
            shader = null;
        }
        if (running && shader != null) postInvalidateOnAnimation();
    }
}
