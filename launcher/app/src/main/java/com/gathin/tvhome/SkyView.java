package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;

/**
 * A full-screen gradient that follows the clock: night blues, a warm horizon at sunset, a soft
 * glow that travels with the sun. Off by default (config.xml, sky_intensity = 0); a theme turns
 * it on with {@code "sky_intensity": 60}.
 *
 * Like the ambient glow, the backdrop is one small bitmap (480x270) stretched over the screen:
 * a gradient repainted at full resolution every minute is exactly the CPU cost the ambient glow
 * was rewritten to avoid on weak GPUs. It is redrawn once a minute and never animated, so the
 * cost is a single full-screen draw per minute.
 */
public class SkyView extends View {

    /** Full-screen and faded with setAlpha: without this every redraw of Home goes through an offscreen layer. */
    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private static final int TEX_W = 480;
    private static final int TEX_H = 270;

    /** Opacity of the whole backdrop, 0 to 255, from sky_intensity. */
    private int intensity;
    private int lastMinute = -1;
    private Bitmap texture;
    /** sky_scene: "cosy" adds a valley with a cottage under the gradient; anything else is the plain sky. */
    private String scene = "";
    private BitmapDrawable drawable;

    public SkyView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /** {@code percent} of sky_intensity: 0 hides the backdrop, 100 covers the wallpaper. */
    public void setIntensity(int percent) {
        intensity = Math.max(0, Math.min(100, percent));
        setAlpha(intensity / 100f);
        setVisibility(intensity == 0 ? GONE : VISIBLE);
        if (intensity > 0) render(true);
    }

    void setScene(String name) {
        scene = name == null ? "" : name;
    }

    /** Picks up a new time of day; cheap and safe to call every minute. */
    public void update() {
        if (intensity == 0 || getVisibility() != VISIBLE) return;
        render(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (intensity > 0) render(true);
    }

    /** Today's sunrise and sunset at the configured place, or null without one (fixed reference day). */
    @Nullable
    private int[] sunTimes() {
        String lat = getContext().getString(R.string.latitude);
        String lon = getContext().getString(R.string.longitude);
        if (lat.isEmpty() || lon.isEmpty()) return null;
        try {
            return Sun.riseAndSet(LocalDate.now(), Double.parseDouble(lat), Double.parseDouble(lon),
                    ZoneId.systemDefault());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void render(boolean force) {
        Calendar now = Calendar.getInstance();
        int minute = Sky.minuteOfDay(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
        if (!force && minute == lastMinute) return;
        lastMinute = minute;

        int[] sun = sunTimes();
        Sky.Palette sky = sun == null ? Sky.at(minute) : Sky.at(minute, sun[0], sun[1]);
        if ("cosy".equals(scene)) sky = cosyPalette(sky, minute, sun);
        if (scene.startsWith("m3")) sky = new Sky.Palette(Expressive.SURFACE, Expressive.SURFACE, Expressive.SURFACE, 0, 0.5f);
        if (texture == null) texture = Bitmap.createBitmap(TEX_W, TEX_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(texture);
        canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0f, 0f, 0f, TEX_H,
                new int[]{sky.top, sky.mid, sky.bottom},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, TEX_W, TEX_H, paint);

        if (sky.sun > 0) {
            // A warm core low on the horizon, where the sun (or its afterglow) sits.
            int warm = Sky.lerpColor(sky.bottom, 0xFFFFD9A0, 0.65f);
            paint.setShader(new RadialGradient(
                    sky.sunX * TEX_W, TEX_H * 0.80f, TEX_W * 0.55f,
                    new int[]{(sky.sun << 24) | (warm & 0x00FFFFFF), warm & 0x00FFFFFF},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0f, 0f, TEX_W, TEX_H, paint);
        }

        if ("cosy".equals(scene)) drawCosy(canvas, sky);
        if ("m3".equals(scene)) drawExpressiveShapes(canvas);
        Backdrop.set(texture);

        if (drawable == null) {
            drawable = new BitmapDrawable(getResources(), texture);
            drawable.setGravity(Gravity.FILL);
            setBackground(drawable);
        }
        invalidate();
    }

    /** M3 Expressive's shape language, spread over the whole screen in container roles only. */
    private void drawExpressiveShapes(Canvas c) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Large cookie in the top right corner, cropped by the edge.
        cookie(c, p, TEX_W * 0.96f, TEX_H * 0.04f, 80f, 12, Expressive.TERTIARY_CONTAINER, 120);
        // Small cookie low on the left edge.
        cookie(c, p, TEX_W * 0.015f, TEX_H * 0.64f, 38f, 8, Expressive.PRIMARY_CONTAINER, 130);
        // Pill across the middle, behind the title, very quiet.
        c.save();
        c.rotate(-18f, TEX_W * 0.55f, TEX_H * 0.34f);
        p.setColor(Expressive.SECONDARY_CONTAINER);
        p.setAlpha(80);
        c.drawRoundRect(TEX_W * 0.55f - 90f, TEX_H * 0.34f - 16f, TEX_W * 0.55f + 90f, TEX_H * 0.34f + 16f, 16f, 16f, p);
        c.restore();
        // Circles: one on the right edge, one near the top centre.
        p.setColor(Expressive.PRIMARY_CONTAINER);
        p.setAlpha(150);
        c.drawCircle(TEX_W * 1.0f, TEX_H * 0.5f, 30f, p);
        p.setColor(Expressive.TERTIARY_CONTAINER);
        p.setAlpha(90);
        c.drawCircle(TEX_W * 0.46f, TEX_H * 0.07f, 14f, p);
        // A soft pill low right, above the dock.
        c.save();
        c.rotate(24f, TEX_W * 0.86f, TEX_H * 0.6f);
        p.setColor(Expressive.SECONDARY_CONTAINER);
        p.setAlpha(110);
        c.drawRoundRect(TEX_W * 0.86f - 38f, TEX_H * 0.6f - 12f, TEX_W * 0.86f + 38f, TEX_H * 0.6f + 12f, 12f, 12f, p);
        c.restore();
    }

    /** A scalloped circle: {@code lobes} soft bumps around a circle of radius r. */
    private static void cookie(Canvas c, Paint p, float cx, float cy, float r, int lobes, int color, int alpha) {
        android.graphics.Path path = new android.graphics.Path();
        for (int i = 0; i <= 180; i++) {
            double a = Math.PI * 2 * i / 180;
            float rr = r * (1f + 0.07f * (float) Math.cos(lobes * a));
            float x = cx + rr * (float) Math.cos(a), y = cy + rr * (float) Math.sin(a);
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
        p.setColor(color);
        p.setAlpha(alpha);
        c.drawPath(path, p);
    }

    /** Deep navy at night, the usual sky by day; blended over the hour either side of sunrise and sunset. */
    private Sky.Palette cosyPalette(Sky.Palette day, int minute, @Nullable int[] sun) {
        int rise = sun == null ? Sky.REF_SUNRISE : sun[0], set = sun == null ? Sky.REF_SUNSET : sun[1];
        float night;
        if (minute < rise - 40) night = 1f;
        else if (minute < rise + 50) night = 1f - (minute - (rise - 40)) / 90f;
        else if (minute < set - 50) night = 0f;
        else if (minute < set + 40) night = (minute - (set - 50)) / 90f;
        else night = 1f;
        night = Math.max(0f, Math.min(1f, night));
        if (night <= 0f) return day;
        return new Sky.Palette(Sky.lerpColor(day.top, 0xFF060A1E, night), Sky.lerpColor(day.mid, 0xFF0D1636, night),
                Sky.lerpColor(day.bottom, 0xFF18264E, night), Math.round(day.sun * (1f - night)), day.sunX);
    }

    /** A night valley: stars, a crescent moon, soft hills, a cottage with lit windows, smoke and a few spruces. */
    private void drawCosy(Canvas c, Sky.Palette sky) {
        int top = sky.top;
        float lum = (((top >> 16) & 0xFF) + ((top >> 8) & 0xFF) + (top & 0xFF)) / 3f;
        float night = Math.max(0f, Math.min(1f, (70f - lum) / 55f));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Shader skyShader = new LinearGradient(0f, 0f, 0f, TEX_H, new int[]{sky.top, sky.mid, sky.bottom},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);

        if (night > 0f) {
            java.util.Random rnd = new java.util.Random(11);
            p.setColor(0xFFFFFFFF);
            for (int i = 0; i < 46; i++) {
                float x = rnd.nextFloat() * TEX_W, y = rnd.nextFloat() * TEX_H * 0.55f, r = 0.35f + rnd.nextFloat() * 0.55f;
                p.setAlpha(Math.round(night * (70 + rnd.nextInt(150))));
                c.drawCircle(x, y, r, p);
            }
            float mx = TEX_W * 0.76f, my = TEX_H * 0.2f;
            p.setColor(0xFFF1EBD6);
            p.setAlpha(Math.round(night * 255));
            c.drawCircle(mx, my, 11f, p);
            // The bite that makes the crescent is the sky itself, painted over the disc.
            p.setAlpha(255);
            p.setShader(skyShader);
            c.drawCircle(mx + 5f, my - 2.5f, 10f, p);
            // The halo goes on last, over the crescent and its bite alike, so the bite keeps the sky's own glow.
            p.setShader(new RadialGradient(mx, my, 42f, new int[]{Math.round(night * 60) << 24 | 0xFFE9B0, 0x00FFE9B0},
                    null, Shader.TileMode.CLAMP));
            c.drawCircle(mx, my, 42f, p);
            p.setShader(null);
        }

        int far = Sky.lerpColor(sky.bottom, 0xFF2A3A6E, 0.55f);
        int mid = Sky.lerpColor(sky.bottom, 0xFF111A38, 0.6f);
        int near = Sky.lerpColor(sky.bottom, 0xFF060A18, 0.82f);
        p.setAlpha(255);
        android.graphics.Path hill = new android.graphics.Path();

        p.setColor(far);
        hill.moveTo(0, TEX_H);
        for (int x = 0; x <= TEX_W; x += 6) hill.lineTo(x, TEX_H * 0.69f - 9f * (float) Math.sin(x / 120f + 0.4f) - 3f * (float) Math.sin(x / 37f));
        hill.lineTo(TEX_W, TEX_H);
        hill.close();
        c.drawPath(hill, p);

        p.setColor(mid);
        hill.reset();
        hill.moveTo(0, TEX_H);
        for (int x = 0; x <= TEX_W; x += 6) hill.lineTo(x, TEX_H * 0.77f - 8f * (float) Math.sin(x / 90f + 2.2f) - 2f * (float) Math.sin(x / 29f));
        hill.lineTo(TEX_W, TEX_H);
        hill.close();
        c.drawPath(hill, p);

        // Cottage on the mid hill, near the right edge, with a few spruces beside it.
        float cx = TEX_W * 0.84f;
        float base = TEX_H * 0.77f - 8f * (float) Math.sin(cx / 90f + 2.2f) - 2f * (float) Math.sin(cx / 29f) + 1f;
        p.setColor(0xFF080C1A);
        for (float[] t : new float[][]{{-26, 11, 13}, {-19, 8, 10}, {27, 12, 16}, {35, 9, 11}}) {
            android.graphics.Path tree = new android.graphics.Path();
            tree.moveTo(cx + t[0] - t[1] / 2f, base);
            tree.lineTo(cx + t[0], base - t[2]);
            tree.lineTo(cx + t[0] + t[1] / 2f, base);
            tree.close();
            c.drawPath(tree, p);
        }
        c.drawRect(cx - 10, base - 11, cx + 10, base, p);
        android.graphics.Path roof = new android.graphics.Path();
        roof.moveTo(cx - 14, base - 10);
        roof.lineTo(cx, base - 21);
        roof.lineTo(cx + 14, base - 10);
        roof.close();
        c.drawPath(roof, p);
        c.drawRect(cx + 5, base - 23, cx + 8, base - 14, p);

        float glow = 0.35f + 0.65f * Math.max(night, sky.sun / 255f);
        p.setShader(new RadialGradient(cx, base - 6, 30f, new int[]{Math.round(120 * glow) << 24 | 0xFFB347, 0x00FFB347}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, base - 6, 30f, p);
        p.setShader(null);
        p.setColor(0xFFFFB347);
        p.setAlpha(Math.round(255 * glow));
        c.drawRect(cx - 6, base - 8, cx - 2.5f, base - 4.5f, p);
        c.drawRect(cx + 2.5f, base - 8, cx + 6, base - 4.5f, p);

        // Smoke: three pale circles drifting up from the chimney.
        p.setColor(0xFFC8D2F0);
        for (int i = 0; i < 3; i++) {
            p.setAlpha(Math.round(70 - i * 18));
            c.drawCircle(cx + 6.5f + i * 2.2f, base - 28 - i * 7f, 2.1f + i * 0.9f, p);
        }

        p.setColor(near);
        p.setAlpha(255);
        hill.reset();
        hill.moveTo(0, TEX_H);
        for (int x = 0; x <= TEX_W; x += 6) hill.lineTo(x, TEX_H * 0.88f - 7f * (float) Math.sin(x / 80f + 1.0f) - 2f * (float) Math.sin(x / 25f));
        hill.lineTo(TEX_W, TEX_H);
        hill.close();
        c.drawPath(hill, p);
    }
}
