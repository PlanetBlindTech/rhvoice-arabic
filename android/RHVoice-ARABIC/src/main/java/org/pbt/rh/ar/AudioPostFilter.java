package org.pbt.rh.ar;

/**
 * Realtime audio post-filter for the RHVoice Arabic voice (Android).
 *
 * The bundled HTS voice ("Zayd") has a very strong low end and was driven hot
 * (voice.info volume was 1.45). That produced two artefacts:
 *
 *   - a constant sub-audible pressure on the ears — DC offset and sub-20 Hz
 *     rumble riding under the speech; and
 *   - intermittent clicks / crackle on high-energy voiced stops (د) and a
 *     mangled release on some final ت — transient peaks that overshot the
 *     16-bit ceiling and hard-clipped.
 *
 * This filter cleans both up in the audio path itself, so it works no matter
 * which phoneme or which upstream setting produced the peak:
 *
 *   1. A one-pole DC-blocking high-pass (~18 Hz at 24 kHz) removes the DC/rumble
 *      that causes the ear pressure, without touching any speech energy.
 *   2. A soft-knee limiter replaces hard clipping with smooth saturation, so a
 *      transient that would have clipped is rounded over instead of clicking.
 *
 * Both stages are cheap and streaming: filter state is carried across the small
 * buffers the engine hands us within one utterance, and reset at the start of
 * each new utterance via {@link #reset()}. The filter operates in place on the
 * short[] sample buffer.
 */
public final class AudioPostFilter {

    // One-pole high-pass pole. Closer to 1.0 = lower cutoff. 0.9965 @ 24 kHz is
    // roughly an 18 Hz cutoff: kills DC and subsonic rumble but leaves even the
    // deepest male fundamental (~80-100 Hz) untouched.
    private static final double HP_POLE = 0.9965;

    // Soft limiter. Below the knee the signal passes through linearly; above it,
    // it is compressed smoothly toward the ceiling so it can never hard-clip.
    private static final double LIMIT_KNEE = 0.80;   // fraction of full scale
    private static final double LIMIT_CEIL = 0.985;  // highest fraction reachable
    private static final double INT16_MAX = 32767.0;

    // High-pass state (previous input and output sample), carried across buffers.
    private double x1 = 0.0;
    private double y1 = 0.0;

    /** Clear filter memory. Call at the start of every new utterance. */
    public void reset() {
        x1 = 0.0;
        y1 = 0.0;
    }

    /** Soft-knee limiter on a single sample scaled to [-1, 1]. */
    private static double softLimit(double x) {
        double span = LIMIT_CEIL - LIMIT_KNEE;
        if (span <= 0) {
            if (x > LIMIT_CEIL) return LIMIT_CEIL;
            if (x < -LIMIT_CEIL) return -LIMIT_CEIL;
            return x;
        }
        double sign = x < 0 ? -1.0 : 1.0;
        double mag = Math.abs(x);
        if (mag <= LIMIT_KNEE) {
            return x;
        }
        // Compress the excess above the knee with tanh so it can never exceed the
        // ceiling. tanh(0)=0 keeps the curve continuous at the knee, and its slope
        // there (=1) matches the linear region, so there is no audible kink.
        double excess = (mag - LIMIT_KNEE) / span;
        mag = LIMIT_KNEE + span * Math.tanh(excess);
        return sign * mag;
    }

    /**
     * Filter a buffer of 16-bit PCM samples in place.
     * On any error the buffer is left untouched, so audio is never dropped.
     */
    public void process(short[] samples) {
        if (samples == null || samples.length == 0) return;
        try {
            double lx1 = x1;
            double ly1 = y1;
            for (int i = 0; i < samples.length; i++) {
                double in = samples[i];

                // One-pole DC-blocking high-pass:
                //   y[n] = x[n] - x[n-1] + pole * y[n-1]
                double hp = in - lx1 + HP_POLE * ly1;
                lx1 = in;
                ly1 = hp;

                // Soft limiter in normalised domain.
                double norm = hp / INT16_MAX;
                norm = softLimit(norm);
                double outv = norm * INT16_MAX;

                // Round and clamp to int16.
                long s = Math.round(outv);
                if (s > 32767L) s = 32767L;
                else if (s < -32768L) s = -32768L;
                samples[i] = (short) s;
            }
            x1 = lx1;
            y1 = ly1;
        } catch (Throwable t) {
            // Leave samples unchanged on any unexpected error.
        }
    }
}
