package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.animation.LinearInterpolator;

/**
 * The network pill's Wi-Fi icon: three arcs lit by the signal. A change of state sweeps them on, a connection that
 * has no internet pulses, and an offline one is dimmed and struck through.
 */
final class NetIcon extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private final int size;
    private final ValueAnimator clock = ValueAnimator.ofFloat(0f, 1f);
    private int lit = -1, color = 0xFFFFFFFF;
    private boolean online, validated, sweeping, pulse;
    private float t;
    private final Runnable end = () -> {
        sweeping = false;
        pulse = false;   // a few pulses say "connected, no internet"; after that the dim arcs and the tint stay
        clock.cancel();
        invalidateSelf();
    };

    NetIcon(int size) {
        this.size = size;
        paint.setStrokeCap(Paint.Cap.ROUND);
        clock.setInterpolator(new LinearInterpolator());
        clock.setRepeatCount(ValueAnimator.INFINITE);
        clock.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            invalidateSelf();
        });
    }

    void setColor(int c) {
        if (c != color) {
            color = c;
            invalidateSelf();
        }
    }

    /** level 0 to 4 (0: offline); a different level or state plays the sweep once. */
    void set(int newLevel, boolean isOnline, boolean isValidated) {
        // Only a real change (arcs lit, online, validated) animates: signal jitter between two readings does not.
        int newLit = !isOnline ? 0 : Math.min(3, (newLevel * 3 + 3) / 4);
        boolean changed = newLit != lit || isOnline != online || isValidated != validated;
        lit = newLit;
        online = isOnline;
        validated = isValidated;
        if (!changed) return;
        sweeping = true;
        pulse = online && !validated;
        clock.setDuration(1100);
        if (!clock.isRunning()) clock.start();
        unscheduleSelf(end);
        scheduleSelf(end, SystemClock.uptimeMillis() + (pulse ? 3300 : 1100));
    }

    private boolean pulsing() {
        return pulse;
    }

    @Override
    public void draw(Canvas c) {
        float k = getBounds().width() / 24f;
        c.save();
        c.translate(getBounds().left, getBounds().top);
        c.scale(k, k);
        float cx = 12f, cy = 19.4f;
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy, 1.9f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.3f);
        int base = color >>> 24;
        for (int i = 0; i < 3; i++) {
            float r = 5.2f + i * 4.4f, a = i < lit ? 1f : 0.28f;
            if (sweeping && !pulse) {
                float wave = Math.max(0f, Math.min(1f, t * 4f - i * 0.6f));
                a = i < lit ? 0.28f + 0.72f * wave : 0.28f;
            } else if (pulsing()) {
                a = 0.28f + 0.72f * (0.5f + 0.5f * (float) Math.sin(t * 2 * Math.PI - i * 0.9f));
            }
            paint.setAlpha(Math.round(base * a));
            arc.set(cx - r, cy - r, cx + r, cy + r);
            c.drawArc(arc, -135f, 90f, false, paint);
        }
        if (!online) {
            paint.setAlpha(base);
            c.drawLine(4f, 4f, 20f, 20f, paint);
        }
        c.restore();
    }

    @Override
    public int getIntrinsicWidth() {
        return size;
    }

    @Override
    public int getIntrinsicHeight() {
        return size;
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
