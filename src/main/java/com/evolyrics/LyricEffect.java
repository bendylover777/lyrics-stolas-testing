package com.evolyrics;

import java.util.Locale;

public enum LyricEffect {
    FADE("Fade"), RISE("Rise"), SCALE_IN("Scale In"), SCALE_OUT("Scale Out"),
    SLIDE_LEFT("Slide Left"), SLIDE_RIGHT("Slide Right"), POP("Pop"), FLOAT("Float"), ROTATE("Rotate"),
    BLUR("Blur"), GLITCH("Glitch"), TYPE("Type"), WAVE("Wave"), DROP("Drop"),
    DECODE("Decode"), FALL("Fall");

    public final String label;

    LyricEffect(String label) {
        this.label = label;
    }

    /** Reusable mutable state: nothing is allocated per frame. dx>0 = right, dy>0 = up (in blocks). */
    public static final class State {
        public float alpha, scale, dx, dy, rot;
        public float blur;      // 0..1 soft halo, text slightly dissolved
        public float glitch;    // 0..1 RGB-split / slice jitter
        public float wave;      // >0 letters bob in a wave
        public float reveal;    // 0..1 fraction of letters shown (typewriter)
        public boolean caret;   // blinking cursor after the last typed letter
        public float scramble;  // 0..1 share of letters still shown as random symbols (decode)
        public float fall;      // 0..1 letters falling in (1 = landed)
        public float fallOut;   // 0..1 letters falling out

        public void reset() {
            alpha = 1f; scale = 1f; dx = 0f; dy = 0f; rot = 0f;
            blur = 0f; glitch = 0f; wave = 0f; reveal = 1f; caret = false;
            scramble = 0f; fall = 1f; fallOut = 0f;
        }

        /** True when the letters must be drawn one by one. */
        public boolean needsLetters() {
            return glitch > 0.01f || wave > 0f || reveal < 0.999f || caret
                || scramble > 0.01f || fall < 0.999f || fallOut > 0.001f;
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

    public static float bounce(float x) {
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

    /** Short random bursts (0 or ~0.9) used by the glitch effect. */
    private static float burst(float time) {
        double b = Math.sin(time * 9.0) * Math.sin(time * 23.0);
        return b > 0.5 ? 0.9f : 0f;
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
                float b = burst(time);
                s.alpha = Math.min(1f, p * 3f) * (b > 0f ? 0.85f : 1f);
                s.glitch = Math.max(1f - p, b);
            }
            case TYPE -> {
                s.alpha = Math.min(1f, p * 5f);
                s.reveal = p;
                s.caret = p < 0.999f;
            }
            case WAVE -> {
                s.wave = 1f;
                s.dy = -(1f - e) * 0.3f;
            }
            case DROP -> {
                s.alpha = Math.min(1f, p * 4f);
                s.dy = (1f - bounce(p)) * 1.3f;
            }
            case DECODE -> {
                s.alpha = Math.min(1f, p * 4f);
                s.scramble = 1f - p;
            }
            case FALL -> {
                s.alpha = Math.min(1f, p * 4f);
                s.fall = p;
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
            case GLITCH -> s.glitch = Math.max(p, burst(p * 40f));
            case TYPE -> {
                s.reveal = 1f - p;
                s.caret = true;
                s.alpha = Math.min(1f, (1f - p) * 4f);
            }
            case WAVE -> {
                s.wave = 1f;
                s.dy += e * 0.4f;
            }
            case DROP -> s.dy -= e * 1.1f;
            case DECODE -> {
                s.scramble = p;
                s.alpha = Math.min(1f, (1f - p) * 3f);
            }
            case FALL -> s.fallOut = p;
        }
    }
}
