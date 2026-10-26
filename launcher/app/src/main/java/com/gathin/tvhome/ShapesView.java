package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.Random;

/**
 * The animated wallpaper: a Wally/Nyri generator scene (see Wally) drawn live on its own surface, at a third of the screen's
 * resolution and on its own thread, so the picture runs at the display's rate and the launcher's windows are not
 * redrawn for it. A new composition is picked when Home comes back after a while, and the last one fades into it; a
 * small copy of the picture is kept for the frosted layers to blur.
 */
final class ShapesView extends SurfaceView implements SurfaceHolder.Callback {
    /** A 40x23 copy of the whole picture, redrawn 4 times a second; the frosted layers draw it stretched. */
    static volatile Bitmap snapshot;
    /** True while the frosted apps page covers the picture: the surface is hidden and only the small copy moves. */
    static volatile boolean covered;
    /** The tint of the frosted layers, set from the wallpaper: the lighter the picture, the lighter and thinner the tint. */
    /** Off: the frosted layers are plain tinted panels and the small copy is never drawn. */
    static volatile boolean blur = true;
    /** True while another window has the focus (a menu, a dialog, an app): the surface keeps its last picture. */
    static volatile boolean paused;
    static volatile int frostTint = 0xAA000000;

    private final Random random = new Random();
    private final Object lock = new Object();
    private Wally.Scene cur, prev;
    private float curStart, prevStart, fadeStart;
    private int curDim, prevDim;
    private long lastCompose;
    private volatile boolean running;
    private Thread thread;
    private Bitmap spare;
    private boolean holdNext, keepAnimating;
    private String lastSignature = "";

    ShapesView(Context context) {
        super(context);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        paused = false;
        if (snapshot == null) snapshot = Bitmap.createBitmap(40, 23, Bitmap.Config.ARGB_8888);
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float q = 0.5f;
        try {
            q = Math.max(0.25f, Math.min(1f, Float.parseFloat(Config.extra(context, "wall_quality", "0.5"))));
        } catch (NumberFormatException ignored) {
            // the default resolution
        }
        keepAnimating = !"false".equals(Config.extra(context, "wall_anim_apps", "true"));
        getHolder().setFixedSize(Math.max(320, Math.round(dm.widthPixels * q)), Math.max(180, Math.round(dm.heightPixels * q)));
        getHolder().addCallback(this);
        compose();
    }

    /** The next compose() keeps the picture unless a wallpaper option changed (the settings were opened and closed). */
    void hold() {
        holdNext = true;
    }

    /** A new random composition. Quick trips in and out of an app keep the picture; a new one fades in over the last. */
    void compose() {
        long nowMs = SystemClock.uptimeMillis();
        if (cur != null && nowMs - lastCompose < 45000 && !holdNext) return;
        Context ctx = getContext();
        String signature = Config.extra(ctx, "wall_style", "") + Config.extra(ctx, "wall_palette", "") + Config.extra(ctx, "wall_dark", "")
                + Config.extra(ctx, "wall_scale", "") + Config.extra(ctx, "wall_dim", "") + Config.extra(ctx, "wall_blur", "");
        boolean held = holdNext && cur != null && signature.equals(lastSignature);
        holdNext = false;
        lastSignature = signature;
        if (held) return;
        blur = !"false".equals(Config.extra(ctx, "wall_blur", "true"));
        String style = Config.extra(ctx, "wall_style", "shapes"), pal = Config.extra(ctx, "wall_palette", "m3");
        String variant = "true".equals(Config.extra(ctx, "wall_dark", "")) ? "dark" : "";
        if ("random".equals(style)) style = Wally.STYLES[random.nextInt(Wally.STYLES.length)];
        if ("random".equals(pal)) pal = Wallpaper.NAMES[random.nextInt(Wallpaper.NAMES.length)];
        int[] colors = Wallpaper.palette(pal, variant);
        boolean own = colors == null;
        if (own) colors = Wallpaper.theme();
        float scale = 1f;
        try {
            scale = Float.parseFloat(Config.extra(ctx, "wall_scale", "1"));
        } catch (NumberFormatException ignored) {
            // the pattern keeps its designed size
        }
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        float w = 2880f, h = w * dm.heightPixels / Math.max(1, dm.widthPixels);
        int dim = 0;
        float lum = Wally.luminance(colors[0]);
        try {
            // Each palette has its own scrim: the lighter its background, the more it is darkened so the text reads.
            int def = own ? 0 : lum > 0.75f ? 55 : lum > 0.55f ? 45 : lum > 0.35f ? 28 : 10;
            dim = Integer.parseInt(Config.extra(ctx, "wall_dim", String.valueOf(def)));
        } catch (NumberFormatException ignored) {
            // no scrim
        }
        if (own) {
            frostTint = (Expressive.SURFACE_CONTAINER & 0x00FFFFFF) | 0xAA000000;
        } else {
            // Light pictures get a thin tint of their own hue, dark ones a stronger one: never a flat black slab.
            int alpha = lum > 0.75f ? 0x4A : lum > 0.55f ? 0x62 : lum > 0.35f ? 0x84 : 0xAA;
            frostTint = (Wally.mix(colors[0], 0xFF000000, lum > 0.55f ? 0.30f : 0.5f) & 0x00FFFFFF) | (alpha << 24);
        }
        Wally.Scene built = Wally.build(style, colors, random.nextLong() & 0xFFFFFFL, w, h, scale);
        synchronized (lock) {
            if (cur != null) {
                prev = cur;
                prevStart = curStart;
                prevDim = curDim;
                fadeStart = nowMs / 1000f;
            }
            cur = built;
            curStart = nowMs / 1000f;
            curDim = dim;
            lastCompose = nowMs;
        }
    }

    private void paintScenes(Canvas c, float vw, float vh, Paint paint) {
        Wally.Scene a, b;
        float aStart, bStart, f;
        int aDim, bDim;
        float now = SystemClock.uptimeMillis() / 1000f;
        synchronized (lock) {
            if (cur == null) return;
            f = prev == null ? 1f : Math.min(1f, (now - fadeStart) / 2.2f);
            if (f >= 1f) prev = null;
            a = prev;
            aStart = prevStart;
            aDim = prevDim;
            b = cur;
            bStart = curStart;
            bDim = curDim;
        }
        if (a == null) {
            drawOne(c, b, now - bStart, bDim, vw, vh, paint);
            return;
        }
        int layer = c.saveLayerAlpha(0, 0, vw, vh, Math.round(255 * (1f - f)));
        drawOne(c, a, now - aStart, aDim, vw, vh, paint);
        c.restoreToCount(layer);
        layer = c.saveLayerAlpha(0, 0, vw, vh, Math.round(255 * f));
        drawOne(c, b, now - bStart, bDim, vw, vh, paint);
        c.restoreToCount(layer);
    }

    private void drawOne(Canvas c, Wally.Scene s, float t, int dim, float vw, float vh, Paint paint) {
        Wally.draw(c, s, t, vw, vh, paint);
        if (dim > 0) c.drawColor(dim * 255 / 100 << 24);
    }

    /** Draws the small copy the frosted layers blur (a software canvas: it is tiny). */
    private void updateSnapshot(Paint paint) {
        Bitmap front = snapshot;
        if (!blur || front == null) return;
        // Draw into the spare copy and swap, so the UI thread never reads a half-drawn picture (a flat dark flash).
        if (spare == null) spare = Bitmap.createBitmap(front.getWidth(), front.getHeight(), Bitmap.Config.ARGB_8888);
        paintScenes(new Canvas(spare), spare.getWidth(), spare.getHeight(), paint);
        Bitmap done = spare;
        spare = front;
        snapshot = done;
    }

    /**
     * While the frosted apps page covers the picture the surface is left as it is (hiding it would destroy and recreate
     * it, a hitch on every page change) and the thread only keeps the small copy moving.
     */
    void setCovered(boolean on) {
        covered = on && !keepAnimating;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        running = true;
        thread = new Thread(this::loop, "wallpaper");
        thread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
        if (thread != null) {
            try {
                thread.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
    }

    private void loop() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        SurfaceHolder holder = getHolder();
        long lastSnap = 0;
        while (running) {
            if (paused) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                    return;
                }
                continue;
            }
            if (covered) {
                updateSnapshot(paint);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                    return;
                }
                continue;
            }
            Canvas c = null;
            try {
                c = holder.lockHardwareCanvas();
                if (c == null) {
                    Thread.sleep(16);
                    continue;
                }
                paintScenes(c, c.getWidth(), c.getHeight(), paint);
            } catch (Exception e) {
                // the surface went away while drawing: the loop ends with running
            } finally {
                if (c != null) {
                    try {
                        holder.unlockCanvasAndPost(c);
                    } catch (Exception ignored) {
                        // surface already gone
                    }
                }
            }
            long now = SystemClock.uptimeMillis();
            if (now - lastSnap > 250) {
                lastSnap = now;
                updateSnapshot(paint);
            }
        }
    }
}
