package dev.sampple.rambler.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/** Gboard Rambler's rounded P6 mic ring, not a sine-wave audio visualizer. */
public class WaveView extends View {
    public static final int IDLE = 0;
    public static final int LISTENING = 1;
    public static final int PROCESSING = 2;
    public static final int ERROR = 3;

    private static final int[] COLORS = {0, 0xff4285f4, 0xffea4335, 0xfff1bc29, 0xff34a853, 0};
    // uql's two 600ms listening sweeps; uqp.e() keyframes sum to one.
    private static final float[][] SWEEP = {
            {.05f, 0, 0, 0, 0, .95f},
            {.05f, 0, .80f, 0, 0, .15f},
            {.05f, .765f, .135f, 0, 0, .05f},
            {.85f, .10f, 0, 0, 0, .05f},
            {.95f, 0, 0, 0, 0, .05f},
            {.15f, 0, 0, .80f, 0, .05f},
            {.05f, 0, 0, .135f, .765f, .05f},
            {.05f, 0, 0, 0, .10f, .85f}
    };
    private static final float[] HIDDEN = {.5f, 0, 0, 0, 0, .5f};
    private static final float[] THINKING = {.15f, .175f, .175f, .175f, .175f, .15f};
    private static final long LISTENING_SWEEP_MS = 600L;
    private static final int[] SWEEP_FRAME_END_MS = {120, 480, 570, 600};
    private static final long ENTER_MS = 450L;
    private static final long EXIT_MS = 367L;
    private static final long THINKING_ENTER_MS = 400L;
    private static final long AUDIO_RESET_MS = 500L;

    private final float density;
    private final Paint discPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint micPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path ring = new Path();
    private final Path mic = new Path();
    private final Path[] segments = new Path[6];
    private final PathMeasure measure = new PathMeasure();
    private final Shader[] glows = new Shader[4];
    private final float[] weights = new float[6];
    private final float[] previous = new float[6];
    private final float[] audioWeights = new float[6];
    private final float[] baseWeights = new float[6];
    private final int[] recent = new int[4];

    private int state = IDLE;
    private int recentCursor;
    private boolean running;
    private boolean dark;
    private boolean paused;
    private long stateAt;
    private long audioAt;
    private long lastFrame;
    private float targetLevel;
    private float smoothLevel;
    private float radius;
    private float centerX;
    private float centerY;
    private float length;

    public WaveView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        for (int i = 0; i < segments.length; i++) segments[i] = new Path();
        System.arraycopy(HIDDEN, 0, weights, 0, 6);
        System.arraycopy(HIDDEN, 0, previous, 0, 6);
        discPaint.setStyle(Paint.Style.FILL);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(dp(2));
        micPaint.setStyle(Paint.Style.FILL);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.BUTT);
        lightPaint.setStyle(Paint.Style.STROKE);
        lightPaint.setStrokeWidth(dp(2));
        lightPaint.setStrokeCap(Paint.Cap.BUTT);
        buildMicPath();
        updateTheme();
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Input is this module's normalized PCM RMS, not Gboard's internal 0..100 level. */
    public void setLevel(float rms0to1) {
        float value = Float.isNaN(rms0to1) ? 0f : clamp(rms0to1, 0f, 1f);
        targetLevel = value;
        long now = SystemClock.uptimeMillis();
        // Perceptual RMS mapping is an estimate: ~0.1 RMS reaches Gboard's level 70.
        float perceptual = (float) Math.sqrt(Math.min(1f, value * 5f));
        recent[recentCursor++ & 3] = Math.round(perceptual * 100f);
        audioAt = now;
        if (running && isAttachedToWindow()) postInvalidateOnAnimation();
    }

    public void setState(int value) {
        int next = value >= IDLE && value <= ERROR ? value : IDLE;
        if (state == next) return;
        long now = SystemClock.uptimeMillis();
        if (running) updateWeights(now);
        System.arraycopy(weights, 0, previous, 0, 6);
        state = next;
        stateAt = now;
        if (next != LISTENING) {
            targetLevel = 0f;
            clearRecent();
        } else {
            clearRecent();
            targetLevel = smoothLevel = 0f;
        }
        if (running && isAttachedToWindow()) postInvalidateOnAnimation();
        else invalidate();
    }

    public void start() {
        if (running) return;
        running = true;
        lastFrame = 0L;
        stateAt = SystemClock.uptimeMillis();
        if (isAttachedToWindow()) postInvalidateOnAnimation();
    }

    public void stop() {
        running = false;
        targetLevel = smoothLevel = 0f;
        clearRecent();
        lastFrame = 0L;
        invalidate();
    }

    /** Optional theme hooks for the hosting keyboard's actual palette. */
    public void setDarkTheme(boolean value) {
        dark = value;
        updateTheme();
        invalidate();
    }

    public void setPaused(boolean value) {
        paused = value;
        updateTheme();
        invalidate();
    }

    private void updateTheme() {
        // Gboard's ring mic has a white backing in both themes; add a quiet outline
        // on white keyboards where the original white outline would disappear.
        discPaint.setColor(Color.WHITE);
        outlinePaint.setColor(dark ? Color.WHITE : 0xffdadce0);
        micPaint.setColor(paused ? 0xff9aa0a6 : 0xff5f6368);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w * .5f;
        centerY = h * .5f;
        // The original glow view is inset 2dp in its mic container; khd then
        // uses (glowViewWidth - 2dp)/2 as the end radius.
        float glowWidth = Math.max(0f, Math.min(w, h) - dp(4));
        radius = Math.max(0f, (glowWidth - dp(2)) * .5f);
        ring.reset();
        if (radius <= 0f) return;
        // ROUNDED_LINE with endAngle=180 and lineSize=0 is two semicircles.
        ring.addArc(centerX - radius, centerY - radius, centerX + radius,
                centerY + radius, 270f, -180f);
        ring.arcTo(centerX - radius, centerY - radius, centerX + radius,
                centerY + radius, 90f, -180f, false);
        measure.setPath(ring, false);
        length = measure.getLength();
        float thickness = glowWidth * .3888889f;
        glowPaint.setStrokeWidth(thickness);
        // Radial shaders are hardware-accelerated and are created only on resize.
        // They approximate P6's composed radial end-glows and straight glow shader.
        float outer = radius + thickness * .5f;
        float inner = Math.max(0f, radius - thickness * .5f);
        for (int i = 0; i < 4; i++) {
            int color = COLORS[i + 1] & 0x00ffffff;
            glows[i] = new RadialGradient(centerX, centerY, outer,
                    new int[]{color, color, (115 << 24) | color, color},
                    new float[]{0f, inner / outer, radius / outer, 1f},
                    Shader.TileMode.CLAMP);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (radius <= 0f) return;
        long now = SystemClock.uptimeMillis();
        if (running) {
            float dt = lastFrame == 0L ? 0f : clamp((now - lastFrame) / 1000f, 0f, .1f);
            lastFrame = now;
            // Estimated RMS envelope: 45ms attack, 180ms release. Input callbacks
            // arrive every 60ms; do not interpret silence as perpetual loudness.
            if (now - audioAt > AUDIO_RESET_MS) targetLevel = 0f;
            float tau = targetLevel > smoothLevel ? .045f : .180f;
            smoothLevel += (targetLevel - smoothLevel) * (1f - (float) Math.exp(-dt / tau));
            updateWeights(now);
        }
        canvas.drawCircle(centerX, centerY, Math.max(0f, radius - dp(1)), discPaint);
        canvas.drawCircle(centerX, centerY, radius, outlinePaint);

        if (running && !paused && state != IDLE) {
            float distance = 0f;
            for (int i = 0; i < 6; i++) {
                segments[i].reset();
                float next = i == 5 ? length : Math.min(length, distance + length * weights[i]);
                if (i > 0 && i < 5 && next - distance >= 1f) {
                    measure.getSegment(distance, next, segments[i], true);
                }
                distance = next;
            }
            int alpha = state == ERROR ? 180 : 255;
            for (int i = 1; i <= 4; i++) {
                if (segments[i].isEmpty()) continue;
                glowPaint.setShader(glows[i - 1]);
                canvas.drawPath(segments[i], glowPaint);
                lightPaint.setColor(state == ERROR ? COLORS[2] : COLORS[i]);
                lightPaint.setAlpha(alpha);
                canvas.drawPath(segments[i], lightPaint);
            }
            glowPaint.setShader(null);
        }
        // Gboard drawable_0x7f080483 has a 960x960 viewport and a 24dp icon.
        float iconSize = Math.min(dp(24), radius * 1.2f);
        canvas.save();
        canvas.translate(centerX - iconSize * .5f, centerY - iconSize * .5f);
        canvas.scale(iconSize / 960f, iconSize / 960f);
        canvas.drawPath(mic, micPaint);
        canvas.restore();
        if (running && isShown() && isAttachedToWindow() && state != IDLE && !paused) {
            postInvalidateOnAnimation();
        }
    }

    private void updateWeights(long now) {
        if (state == IDLE || state == ERROR) {
            interpolate(previous, HIDDEN, clamp((now - stateAt) / (float) EXIT_MS, 0f, 1f));
            return;
        }
        if (state == PROCESSING) {
            float entry = clamp((now - stateAt) / (float) THINKING_ENTER_MS, 0f, 1f);
            interpolate(previous, THINKING, entry);
            // P6 THINKING uses 400/1080/4000/5000ms sequences, not a wave phase.
            // Slowly redistribute the four colored lanes instead of drawing a sine.
            if (entry >= 1f) {
                int lane = (int) ((now - stateAt - THINKING_ENTER_MS) / 1080L) % 4 + 1;
                float phase = ((now - stateAt - THINKING_ENTER_MS) % 1080L) / 1080f;
                weights[lane] += .08f * (1f - Math.abs(2f * phase - 1f));
                weights[5] -= .08f * (1f - Math.abs(2f * phase - 1f));
            }
            return;
        }
        if (now - audioAt > AUDIO_RESET_MS) clearRecent();
        int maximum = 0;
        for (int i = 0; i < 4; i++) maximum = Math.max(maximum, recent[i]);
        if (maximum >= 70) {
            // Responsive USER_INPUT: 200 + 50*(magnitude/70)ms, 13.888889..25
            // target weight, one colored lane emphasized, the other three at 1.
            float magnitude = Math.min(70f, 100f - maximum);
            float ratio = magnitude / 70f;
            float edge = 13.888889f + 11.111111f * ratio;
            int lane = (int) ((now / 300L) % 4L) + 1; // lane rotation is an estimate
            // Preserve khf's four-sample >=70 gate, but use the smoothed RMS
            // envelope for the continuously varying responsive color weight.
            float smoothed = (float) Math.sqrt(Math.min(1f, smoothLevel * 5f));
            float emphasis = 2f + smoothed * 7f;
            float middle = 100f - 2f * edge;
            float unit = middle / (emphasis + 3f) / 100f;
            audioWeights[0] = audioWeights[5] = edge / 100f;
            for (int i = 1; i <= 4; i++) audioWeights[i] = unit * (i == lane ? emphasis : 1f);
            float duration = 200f + 50f * ratio;
            float responsiveness = 1f - (float) Math.exp(-14f / duration);
            for (int i = 0; i < 6; i++) {
                weights[i] += (audioWeights[i] - weights[i]) * responsiveness;
            }
        } else {
            long elapsed = Math.max(0L, now - stateAt);
            // 450ms entrance from GONE, followed by the two 600ms listening sweeps.
            int half = (int) ((elapsed / LISTENING_SWEEP_MS) & 1L);
            int time = (int) (elapsed % LISTENING_SWEEP_MS);
            int index = 0;
            while (time >= SWEEP_FRAME_END_MS[index] && index < 3) index++;
            int begin = index == 0 ? 0 : SWEEP_FRAME_END_MS[index - 1];
            float part = (time - begin) / (float) (SWEEP_FRAME_END_MS[index] - begin);
            int step = half * 4 + index;
            float[] from = SWEEP[step];
            float[] to = SWEEP[(step + 1) & 7];
            // uqp.e() yields normalized weights; interpolate its keyframes.
            for (int i = 0; i < 6; i++) {
                baseWeights[i] = from[i] + (to[i] - from[i]) * part;
            }
            if (elapsed < ENTER_MS) {
                float entrance = elapsed / (float) ENTER_MS;
                for (int i = 0; i < 6; i++) {
                    weights[i] = previous[i] + (baseWeights[i] - previous[i]) * entrance;
                }
            } else {
                // Gboard's listening loop supplies the target shape; ease back
                // from a user-input burst rather than snapping to it.
                for (int i = 0; i < 6; i++) {
                    weights[i] += (baseWeights[i] - weights[i]) * .12f;
                }
            }
        }
    }

    private void interpolate(float[] from, float[] to, float t) {
        for (int i = 0; i < 6; i++) weights[i] = from[i] + (to[i] - from[i]) * t;
    }

    private void clearRecent() {
        for (int i = 0; i < 4; i++) recent[i] = 0;
    }

    private void buildMicPath() {
        // The two contours of Gboard's 24dp microphone vector (viewport 960).
        mic.moveTo(395, 525);
        mic.quadTo(360, 490, 360, 440);
        mic.lineTo(360, 200);
        mic.quadTo(360, 150, 395, 115);
        mic.quadTo(430, 80, 480, 80);
        mic.quadTo(530, 80, 565, 115);
        mic.quadTo(600, 150, 600, 200);
        mic.lineTo(600, 440);
        mic.quadTo(600, 490, 565, 525);
        mic.quadTo(530, 560, 480, 560);
        mic.quadTo(430, 560, 395, 525);
        mic.close();
        mic.moveTo(440, 800);
        mic.lineTo(440, 717);
        mic.quadTo(348, 704, 282.5f, 639);
        mic.quadTo(217, 574, 203, 481);
        mic.quadTo(201, 464, 212, 452);
        mic.quadTo(223, 440, 240, 440);
        mic.quadTo(257, 440, 268, 452);
        mic.quadTo(279, 464, 277, 481);
        mic.quadTo(291, 551, 346.5f, 596);
        mic.quadTo(402, 641, 480, 640);
        mic.quadTo(552, 640, 607, 594.5f);
        mic.quadTo(662, 549, 676, 480);
        mic.quadTo(680, 463, 691.5f, 451.5f);
        mic.quadTo(703, 440, 720, 440);
        mic.quadTo(737, 440, 748, 452);
        mic.quadTo(759, 464, 757, 481);
        mic.quadTo(743, 572, 678, 638);
        mic.quadTo(613, 704, 520, 717);
        mic.lineTo(520, 800);
        mic.quadTo(520, 817, 508.5f, 828.5f);
        mic.quadTo(497, 840, 480, 840);
        mic.quadTo(463, 840, 451.5f, 828.5f);
        mic.quadTo(440, 817, 440, 800);
        mic.close();
    }

    private float dp(float value) {
        return value * density;
    }

    private static float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(high, value));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (running) postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        lastFrame = 0L;
        clearRecent();
        super.onDetachedFromWindow();
    }
}
