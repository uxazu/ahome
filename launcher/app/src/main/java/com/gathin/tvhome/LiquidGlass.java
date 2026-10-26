package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Liquid glass without shaders (they need Android 13): the backdrop is blurred by shrinking it, then drawn through a
 * mesh whose vertices are pushed along the edge normal inside a bevel band, which bends the picture at the rim like
 * the thick edge of a lens. Red, green and blue go through slightly different bends and are added together, which
 * gives the colour fringe. The result is one bitmap, so drawing the band costs one drawBitmap per frame.
 */
final class LiquidGlass {
    private static final int COLS = 56, ROWS = 12;

    private LiquidGlass() {}

    /**
     * @param screenW screen size in px that the backdrop covers
     * @param band    the band in screen px
     * @param radius  corner radius in px
     * @return the glass for that band, band-sized, or null when the backdrop is unusable
     */
    static Bitmap bake(Bitmap backdrop, int screenW, int screenH, RectF band, float radius, float density) {
        int w = Math.round(band.width()), h = Math.round(band.height());
        if (backdrop == null || w < 8 || h < 8 || screenW <= 0 || screenH <= 0) return null;
        try {
            int bw = backdrop.getWidth(), bh = backdrop.getHeight();
            int sx = clamp(Math.round(band.left / screenW * bw), 0, bw - 2);
            int sy = clamp(Math.round(band.top / screenH * bh), 0, bh - 2);
            int sw = clamp(Math.round(band.width() / screenW * bw), 2, bw - sx);
            int sh = clamp(Math.round(band.height() / screenH * bh), 2, bh - sy);
            Bitmap region = Bitmap.createBitmap(backdrop, sx, sy, sw, sh);
            // Frost: a quarter-size copy stretched back with bilinear filtering is a cheap blur.
            Bitmap tiny = Bitmap.createScaledBitmap(region, Math.max(2, sw / 4), Math.max(2, sh / 4), true);
            Bitmap soft = Bitmap.createScaledBitmap(tiny, Math.max(2, sw), Math.max(2, sh), true);

            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            Path clip = new Path();
            clip.addRoundRect(new RectF(0, 0, w, h), radius, radius, Path.Direction.CW);
            c.clipPath(clip);

            float bevel = Math.min(h * 0.5f, 30f * density);
            int[] channel = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF};
            float[] strength = {1.0f, 0.86f, 0.72f};   // red bends most, blue least: the fringe
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
            for (int k = 0; k < 3; k++) {
                p.setColorFilter(new PorterDuffColorFilter(channel[k], PorterDuff.Mode.MULTIPLY));
                c.drawBitmapMesh(soft, COLS, ROWS, mesh(w, h, radius, bevel, 0.62f * bevel * strength[k]), 0, null, 0, p);
            }
            // Glass body: a faint lift towards the top, and a darker floor so the tiles keep their contrast.
            Paint body = new Paint();
            body.setShader(new LinearGradient(0, 0, 0, h, 0x30FFFFFF, 0x10000000, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, body);
            return out;
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    private static float[] mesh(int w, int h, float radius, float bevel, float push) {
        float[] v = new float[(COLS + 1) * (ROWS + 1) * 2];
        float hx = w / 2f, hy = h / 2f;
        float ix = hx - radius, iy = hy - radius;
        int n = 0;
        for (int j = 0; j <= ROWS; j++) {
            for (int i = 0; i <= COLS; i++) {
                float x = w * i / (float) COLS, y = h * j / (float) ROWS;
                float px = x - hx, py = y - hy;
                float qx = Math.abs(px) - ix, qy = Math.abs(py) - iy;
                float ox = Math.max(qx, 0), oy = Math.max(qy, 0);
                float outside = (float) Math.sqrt(ox * ox + oy * oy);
                float sd = outside + Math.min(Math.max(qx, qy), 0) - radius;   // negative inside
                float nx, ny;
                if (qx > 0 && qy > 0) {
                    nx = Math.signum(px) * ox / outside;
                    ny = Math.signum(py) * oy / outside;
                } else if (qx > qy) {
                    nx = Math.signum(px);
                    ny = 0;
                } else {
                    nx = 0;
                    ny = Math.signum(py);
                }
                float t = Math.max(0f, Math.min(1f, -sd / bevel));
                float k = 1f - t;
                k = k * k * k;                       // strong at the rim, gone quickly inside
                v[n++] = x + nx * k * push;
                v[n++] = y + ny * k * push;
            }
        }
        return v;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi < lo ? lo : hi, v));
    }
}
