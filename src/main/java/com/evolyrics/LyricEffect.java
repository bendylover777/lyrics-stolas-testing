package com.evolyrics;

import java.util.Locale;

public enum LyricEffect {
    FADE("Fade"), RISE("Rise"), SCALE_IN("Scale In"), SCALE_OUT("Scale Out"),
    SLIDE_LEFT("Slide Left"), SLIDE_RIGHT("Slide Right"), POP("Pop"), FLOAT("Float"), ROTATE("Rotate"),
    DROP("Drop"), BOUNCE("Bounce"), WAVE("Wave"), SHAKE("Shake"), GLITCH("Glitch"), TYPEWRITER("Typewriter");

    public final String label;

    LyricEffect(String label) {
        this.label = label;
    }

    /** Reusable mutable state: nothing is allocated per frame. dx>0 = right, dy>0 = up (in blocks). */
    public static final class State {
        public float alpha, scale, dx, dy, rot, reveal;

        public void reset() {
            alpha = 1f; scale = 1f; dx = 0f; dy = 0f; rot = 0f; reveal = 1f;
        }
    }

    public static LyricEffect parse(String s) {
        if (s == null || s.isBlank()) return null;
        String k = s.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        try {
            return valueOf(k);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public LyricEffect next() {
        LyricEffect[] v = values();
        return v[(ordinal() + 1) % v.length];
    }

    private static float easeOut(float p) {
        float q = 1f - p;
        return 1f - q * q * q;
    }

    private static float easeBack(float p) {
        float c1 = 1.70158f, c3 = c1 + 1f;
        float q = p - 1f;
        return 1f + c3 * q * q * q + c1 * q * q;
    }

    private static float bounceOut(float p) {
        float n1 = 7.5625f, d1 = 2.75f;
        if (p < 1f / d1) return n1 * p * p;
        if (p < 2f / d1) {
            p -= 1.5f / d1;
            return n1 * p * p + 0.75f;
        }
        if (p < 2.5f / d1) {
            p -= 2.25f / d1;
            return n1 * p * p + 0.9375f;
        }
        p -= 2.625f / d1;
        return n1 * p * p + 0.984375f;
    }

    private static float jitter(float p, int salt) {
        int h = (int) (p * 24f) * 73856093 ^ salt * 19349663;
        h ^= h >>> 13;
        return ((h & 1023) / 1023f) - 0.5f;
    }

    /** p: 0..1 appear progress (stays 1 while the line is shown). */
    public void applyIn(State s, float p, float time) {
        float e = easeOut(p);
        s.alpha = p;
        switch (this) {
            case FADE -> { }
            case RISE -> s.dy = -(1f - e) * 0.9f;
            case SCALE_IN -> s.scale = 0.3f + 0.7f * e;
            case SCALE_OUT -> s.scale = 1.8f - 0.8f * e;
            case SLIDE_LEFT -> s.dx = (1f - e) * 1.8f;
            case SLIDE_RIGHT -> s.dx = -(1f - e) * 1.8f;
            case POP -> {
                s.alpha = Math.min(1f, p * 3f);
                s.scale = 0.2f + 0.8f * easeBack(p);
            }
            case FLOAT -> {
                s.dy = -(1f - e) * 0.4f + (float) Math.sin(time * 2.2) * 0.06f;
                s.dx = (float) Math.cos(time * 1.5) * 0.03f;
            }
            case ROTATE -> {
                s.rot = -(1f - e) * 70f;
                s.scale = 0.6f + 0.4f * e;
            }
            case DROP -> {
                s.alpha = Math.min(1f, p * 4f);
                s.dy = (1f - bounceOut(p)) * 1.3f;
            }
            case BOUNCE -> {
                s.alpha = Math.min(1f, p * 3f);
                s.scale = 0.4f + 0.6f * bounceOut(p);
            }
            case WAVE -> {
                s.dy = -(1f - e) * 0.5f + (float) Math.sin(time * 3.2) * 0.07f;
                s.rot = (float) Math.sin(time * 2.4) * 2.5f;
            }
            case SHAKE -> {
                s.alpha = Math.min(1f, p * 3f);
                s.dx = (1f - p) * (float) Math.sin(p * 45.0) * 0.3f;
            }
            case GLITCH -> {
                s.dx = (1f - p) * jitter(p, 1) * 1.2f;
                s.dy = (1f - p) * jitter(p, 2) * 0.5f;
                s.scale = 1f + (1f - p) * jitter(p, 3) * 0.4f;
            }
            case TYPEWRITER -> {
                s.alpha = Math.min(1f, p * 6f);
                s.reveal = p;
            }
        }
    }

    /** p: 0..1 disappear progress. Combines with the in-state (offsets add, scale multiplies). */
    public void applyOut(State s, float p) {
        float e = p * p;
        s.alpha = 1f - p;
        switch (this) {
            case FADE -> { }
            case RISE -> s.dy += e * 0.9f;
            case SCALE_IN -> s.scale *= 1f + 0.15f * e;
            case SCALE_OUT -> s.scale *= 1f - 0.7f * e;
            case SLIDE_LEFT -> s.dx -= e * 1.8f;
            case SLIDE_RIGHT -> s.dx += e * 1.8f;
            case POP -> s.scale *= 1f + 0.3f * e;
            case FLOAT -> s.dy += e * 0.5f;
            case ROTATE -> {
                s.rot += e * 70f;
                s.scale *= 1f - 0.4f * e;
            }
            case DROP -> s.dy -= e * 1.0f;
            case BOUNCE -> s.scale *= 1f - 0.6f * e;
            case WAVE -> s.dy += e * 0.5f;
            case SHAKE -> s.dx += (float) Math.sin(p * 40.0) * 0.2f * e;
            case GLITCH -> {
                s.dx += jitter(p, 4) * 1.0f * e;
                s.scale *= 1f - 0.2f * e;
            }
            case TYPEWRITER -> { }
        }
    }
}
