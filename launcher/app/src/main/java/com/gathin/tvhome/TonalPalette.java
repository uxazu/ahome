package com.gathin.tvhome;


/**
 * Material 3 tonal palettes from a seed colour, in the "tonal spot" recipe: primary chroma 36 at the seed's hue,
 * secondary 16, tertiary 24 at hue + 60, neutral 6 and neutral variant 8. Tone is CIE L*, as in M3's HCT; hue and
 * chroma are taken in CIE LCh rather than CAM16, so the hues sit a few degrees off Google's tool, and a colour
 * that does not fit sRGB at the asked chroma loses chroma until it does.
 */
final class TonalPalette {
    private final float hue, chroma;

    TonalPalette(float hue, float chroma) {
        this.hue = hue;
        this.chroma = chroma;
    }

    /** The seed's hue in degrees, 0 to 360. */
    static float hueOf(int argb) {
        double[] lab = lab(argb);
        double h = Math.toDegrees(Math.atan2(lab[2], lab[1]));
        return (float) (h < 0 ? h + 360 : h);
    }

    /** The colour of this palette at tone 0 (black) to 100 (white). */
    int tone(float tone) {
        if (tone <= 0f) return 0xFF000000;
        if (tone >= 100f) return 0xFFFFFFFF;
        double lo = 0, hi = chroma;
        int best = fromLch(tone, 0, hue);
        for (int i = 0; i < 14; i++) {
            double mid = (lo + hi) / 2;
            int c = fromLchOrZero(tone, mid, hue);
            if (c != 0) {
                best = c;
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return best;
    }

    private static int fromLch(double l, double c, double h) {
        int v = fromLchOrZero(l, c, h);
        return v != 0 ? v : 0xFF808080;
    }

    /** Returns 0 when the colour is outside sRGB. */
    private static int fromLchOrZero(double l, double c, double hDeg) {
        double h = Math.toRadians(hDeg);
        double a = c * Math.cos(h), b = c * Math.sin(h);
        double fy = (l + 16) / 116, fx = fy + a / 500, fz = fy - b / 200;
        double x = 0.95047 * finv(fx), y = finv(fy), z = 1.08883 * finv(fz);
        double r = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z;
        double g = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z;
        double bl = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z;
        double eps = 0.0005;
        if (r < -eps || r > 1 + eps || g < -eps || g > 1 + eps || bl < -eps || bl > 1 + eps) return 0;
        return 0xFF000000 | (to8(r) << 16) | (to8(g) << 8) | to8(bl);
    }

    private static double finv(double t) {
        double t3 = t * t * t;
        return t3 > 0.008856 ? t3 : (t - 16.0 / 116) / 7.787;
    }

    private static int to8(double linear) {
        double v = Math.max(0, Math.min(1, linear));
        double s = v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
        return (int) Math.round(Math.max(0, Math.min(1, s)) * 255);
    }

    private static double[] lab(int argb) {
        double r = lin(((argb >> 16) & 0xFF) / 255.0), g = lin(((argb >> 8) & 0xFF) / 255.0), b = lin((argb & 0xFF) / 255.0);
        double x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047;
        double y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b;
        double z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883;
        double fx = f(x), fy = f(y), fz = f(z);
        return new double[]{116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double lin(double s) {
        return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    private static double f(double t) {
        return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116;
    }
}
