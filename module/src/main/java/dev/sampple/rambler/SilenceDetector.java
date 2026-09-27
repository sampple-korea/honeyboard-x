package dev.sampple.rambler;

/** Raw AudioRecord RMS, NOT the amplified/smoothed animation level. */
final class SilenceDetector {
    // -40 dBFS floor, adaptive noise margin, capped at -30 dBFS for quiet voices.
    private float noise = .003f;
    private long candidateAt = -1, lastVoice = -1;
    private boolean speech;

    boolean update(float rms, long now, int timeoutSeconds) {
        if (!Float.isFinite(rms)) return false;
        float threshold = Math.min(.032f, Math.max(.01f, noise * 3f));
        if (rms >= (speech ? threshold * .65f : threshold)) {
            if (candidateAt < 0) candidateAt = now;
            // Reject a single click; require >= 180 ms sustained level to arm.
            if (now - candidateAt >= 180) speech = true;
            if (speech) lastVoice = now;
        } else {
            candidateAt = -1;
            noise += .025f * (rms - noise);
        }
        return timeoutSeconds > 0 && speech && lastVoice >= 0
                && now - lastVoice >= timeoutSeconds * 1000L;
    }
}
