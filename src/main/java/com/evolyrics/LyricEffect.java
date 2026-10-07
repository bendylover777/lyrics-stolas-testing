package com.evolyrics;

import java.util.Locale;

public enum LyricEffect {
    FADE("Fade"), RISE("Rise"), SCALE_IN("Scale In"), SCALE_OUT("Scale Out"),
    SLIDE_LEFT("Slide Left"), SLIDE_RIGHT("Slide Right"), POP("Pop"), FLOAT("Float"), ROTATE("Rotate"),
    BLUR("Blur"), GLITCH("Glitch"), TYPE("Type"), WAVE("Wave"), DROP("Drop");

    public final String label;

    LyricEffect(String label) {
        this.label = label;
    }

    /** Reusable mutable state: nothing is allocated per frame. dx>0 = right, dy>0 = up (in blocks). */
    public static final class State {
        public float alpha, scale, dx, dy, rot;
        public float blur;      // 0..1 extra soft halo, text slightly dissolved
        public float glitch;    // 0..1 RGB-split jitter
        public float wave;      // >0 letters bob in a wave
        public float reveal;    // 0..1 fraction of letters shown (typewriter)

        public void reset() {
            alpha = 1f; scale = 1f; dx = 0f; dy = 0f; rot = 0f;
            blur = 0f; glitch = 0f; wave = 0f; reveal = 1f;
        }

        /** True when the letters must be drawn one by one. */
        public boolean needsLetters() {
            return glitch > 0.01f || wave > 0f || reveal < 0.999f;
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

    private static float easeBounce(float x) {
        float n1 = 7.5625f, d1 = 2.75f;
        if (x < 1f / d1) return n1 * x * x;
        if (x < 2f / d1) {
            x -= 1.5f / d1;
            return n1 * x * x + 0.75f;
        }
        if (x < 2.5f / d1) {
            x -= 2.25f / d1;
            return n1 * x * x + 0.9375f;
        }
        x -= 2.625f / d1;
        return n1 * x * x + 0.984375f;
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
            case BLUR -> {
                s.blur = 1f - e;
                s.scale = 1.12f - 0.12f * e;
            }
            case GLITCH -> {
                s.alpha = Math.min(1f, p * 3f);
                s.glitch = Math.max(1f - p, Math.sin(time * 11.0) > 0.93 ? 0.8f : 0f);
            }
            case TYPE -> {
                s.alpha = Math.min(1f, p * 5f);
                s.reveal = p;
            }
            case WAVE -> {
                s.wave = 1f;
                s.dy = -(1f - e) * 0.3f;
            }
            case DROP -> {
                s.alpha = Math.min(1f, p * 4f);
                s.dy = (1f - easeBounce(p)) * 1.3f;
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
            case BLUR -> s.blur = e;
            case GLITCH -> s.glitch = p;
            case TYPE -> s.reveal = 1f - p;
            case WAVE -> {
                s.wave = 1f;
                s.dy += e * 0.4f;
            }
            case DROP -> s.dy -= e * 1.1f;
        }
    }
}
