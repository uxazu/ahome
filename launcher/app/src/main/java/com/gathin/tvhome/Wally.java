package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The wallpaper generator of Wally and Nyri (nyri-wall, github.com/yzewe/Nyri, MIT): 21 styles, each a pure function of
 * (style, palette, seed, size) that builds a small scene of flat shapes with motions. The generators here follow
 * nyri-wall's, shape by shape; the motions follow its Quickshell scene player (SceneNode.qml): spin, sway, bob, breathe,
 * stretch, twinkle, flip, scroll, drift, orbit, swell, ripple and the comets of the cosmos style.
 */
final class Wally {
    static final String[] STYLES = {"bauhaus", "arches", "waves", "hills", "pebbles", "ribbons", "shapes", "rainbow",
            "dots", "cosmos", "truchet", "memphis", "sunset", "peaks", "flowers", "rings", "stripes", "bands", "garden",
            "objects", "pair"};
    private static final float TAU = (float) (Math.PI * 2);

    private Wally() {}

    // ---- scene model -------------------------------------------------------------------------------------

    private static final int SPIN = 0, SWAY = 1, BOB = 2, BREATHE = 3, STRETCH = 4, TWINKLE = 5, FLIP = 6, SCROLL = 7,
            DRIFT = 8, ORBIT = 9, SWELL = 10, RIPPLE = 11;

    private static final class M {
        int k;
        float a, p, ph, v, dx, dy, e, st, x0, bw, w, rx, ry, s0, d, n, min, period;
    }

    private static final class Item {
        Path path;
        int color;
        boolean stroke, butt;
        float sw;
        int alpha = 255;
    }

    private static final class N {
        boolean dot;
        float x, y, rad, r, ox, oy;
        int color;
        List<Item> items = new ArrayList<>();
        M[] m = new M[0];
        float[][] group;
        List<N> kids;
    }

    static final class Scene {
        float w, h;
        int bg;
        List<N> nodes = new ArrayList<>();
        boolean comets;
        int cometColor;
        float cometEvery = 7f;
    }

    // ---- python-like random ------------------------------------------------------------------------------

    private static final class Rng {
        final Random r;

        Rng(long seed) {
            r = new Random(seed);
        }

        float uniform(double a, double b) {
            return (float) (a + (b - a) * r.nextDouble());
        }

        boolean chance(double p) {
            return r.nextDouble() < p;
        }

        int below(int n) {
            return r.nextInt(n);
        }

        float cd(double... v) {
            return (float) v[r.nextInt(v.length)];
        }

        int ci(int... v) {
            return v[r.nextInt(v.length)];
        }

        boolean flag() {
            return r.nextBoolean();
        }

        int pick(List<Integer> l) {
            return l.get(r.nextInt(l.size()));
        }

        List<Integer> sample(List<Integer> l, int k) {
            List<Integer> c = new ArrayList<>(l);
            Collections.shuffle(c, r);
            return new ArrayList<>(c.subList(0, Math.min(k, c.size())));
        }
    }

    // ---- colour helpers ----------------------------------------------------------------------------------

    static int mix(int a, int b, float f) {
        int r = Math.round(((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int g = Math.round(((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = Math.round((a & 255) * (1 - f) + (b & 255) * f);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    static float luminance(int c) {
        return 0.2126f * ((c >> 16) & 255) / 255f + 0.7152f * ((c >> 8) & 255) / 255f + 0.0722f * (c & 255) / 255f;
    }

    private static float[] hls(int c) {
        float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
        float mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
        float l = (mx + mn) / 2f, h = 0, s = 0;
        if (mx != mn) {
            float d = mx - mn;
            s = l <= 0.5f ? d / (mx + mn) : d / (2f - mx - mn);
            if (mx == r) h = ((g - b) / d) % 6f;
            else if (mx == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h = (h / 6f + 1f) % 1f;
        }
        return new float[]{h, l, s};
    }

    private static float hue(float m1, float m2, float h) {
        h = ((h % 1f) + 1f) % 1f;
        if (h < 1f / 6f) return m1 + (m2 - m1) * h * 6f;
        if (h < 0.5f) return m2;
        if (h < 2f / 3f) return m1 + (m2 - m1) * (2f / 3f - h) * 6f;
        return m1;
    }

    private static int fromHls(float h, float l, float s) {
        l = Math.max(0, Math.min(1, l));
        s = Math.max(0, Math.min(1, s));
        float r, g, b;
        if (s == 0) {
            r = g = b = l;
        } else {
            float m2 = l <= 0.5f ? l * (1f + s) : l + s - l * s, m1 = 2f * l - m2;
            r = hue(m1, m2, h + 1f / 3f);
            g = hue(m1, m2, h);
            b = hue(m1, m2, h - 1f / 3f);
        }
        return 0xFF000000 | Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
    }

    /** The dark or light version of a palette, as nyri-wall's toned(). */
    static int[] toned(int[] colors, boolean dark) {
        int[] out = new int[colors.length];
        float[] b = hls(colors[0]);
        out[0] = dark ? fromHls(b[0], 0.1f, Math.min(b[2], 0.4f)) : fromHls(b[0], 0.93f, Math.min(b[2], 0.55f));
        for (int i = 1; i < colors.length; i++) {
            float[] c = hls(colors[i]);
            out[i] = dark ? fromHls(c[0], 0.2f + c[1] * 0.46f, Math.min(1, c[2] * 1.05f))
                    : fromHls(c[0], 0.5f + c[1] * 0.38f, Math.min(1, c[2] * 1.05f));
        }
        return out;
    }

    // ---- paths -------------------------------------------------------------------------------------------

    private interface Radius {
        float at(float t);
    }

    private static Path smooth(List<float[]> points, boolean closed) {
        int n = points.size();
        List<float[]> pts = new ArrayList<>();
        if (!closed) pts.add(points.get(0));
        pts.addAll(points);
        if (!closed) pts.add(points.get(n - 1));
        Path d = new Path();
        d.moveTo(points.get(0)[0], points.get(0)[1]);
        int from = closed ? 0 : 1, to = closed ? n : pts.size() - 2;
        int len = pts.size();
        for (int i = from; i < to; i++) {
            float[] p0 = pts.get(Math.floorMod(i - 1, len)), p1 = pts.get(i % len), p2 = pts.get((i + 1) % len), p3 = pts.get((i + 2) % len);
            d.cubicTo(p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6,
                    p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6, p2[0], p2[1]);
        }
        if (closed) d.close();
        return d;
    }

    private static Path polar(float cx, float cy, Radius fn, int steps, float rot) {
        List<float[]> pts = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            float t = TAU * i / steps, r = fn.at(t);
            pts.add(new float[]{cx + r * (float) Math.cos(t + rot), cy + r * (float) Math.sin(t + rot)});
        }
        return smooth(pts, true);
    }

    private static Path circ(float x, float y, float r) {
        Path p = new Path();
        p.addCircle(x, y, Math.max(0.01f, r), Path.Direction.CW);
        return p;
    }

    private static Path ccwCirc(float x, float y, float r) {
        Path p = new Path();
        p.addCircle(x, y, r, Path.Direction.CCW);
        return p;
    }

    private static Path rrect(float x, float y, float w, float h, float r, float rot, float ox, float oy) {
        r = Math.max(0f, Math.min(r, Math.min(w / 2, h / 2)));
        Path p = new Path();
        p.addRoundRect(new RectF(x, y, x + w, y + h), r, r, Path.Direction.CW);
        if (rot != 0) {
            Matrix m = new Matrix();
            m.setRotate(rot, ox, oy);
            p.transform(m);
        }
        return p;
    }

    private static Path rrect(float x, float y, float w, float h, float r) {
        return rrect(x, y, w, h, r, 0, 0, 0);
    }

    private static Path poly(boolean close, float... xy) {
        Path p = new Path();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
        if (close) p.close();
        return p;
    }

    private static Path sine(float y, float amp, float period, float ph, float x0, float x1) {
        int n = Math.max(64, (int) ((x1 - x0) / period * 48));
        Path p = new Path();
        for (int i = 0; i <= n; i++) {
            float x = x0 + (x1 - x0) * i / n, yy = y + amp * (float) Math.sin(ph + TAU * x / period);
            if (i == 0) p.moveTo(x, yy); else p.lineTo(x, yy);
        }
        return p;
    }

    // ---- canvas ------------------------------------------------------------------------------------------

    private static final class C {
        final float w, h, k, u;
        final int bg;
        final int[] fg;
        Rng mr;
        final Scene scene = new Scene();
        final List<List<N>> stack = new ArrayList<>();

        C(float w, float h, int[] palette, float scale) {
            this.w = w;
            this.h = h;
            k = Math.max(0.3f, Math.min(3f, scale));
            u = h * k;
            bg = palette[0];
            fg = Arrays.copyOfRange(palette, 1, palette.length);
            scene.w = w;
            scene.h = h;
            scene.bg = bg;
            stack.add(scene.nodes);
        }

        List<Integer> fgList() {
            List<Integer> l = new ArrayList<>();
            for (int c : fg) l.add(c);
            return l;
        }

        List<Integer> all() {
            List<Integer> l = new ArrayList<>();
            l.add(bg);
            for (int c : fg) l.add(c);
            return l;
        }

        int n(double count, double power, int lo, int hi) {
            return Math.max(lo, Math.min(hi, (int) Math.round(count / Math.pow(k, power))));
        }

        int nl(double count, int lo) {
            return n(count, 1, lo, 400);
        }

        int np(double count, double power) {
            return n(count, power, 1, 400);
        }

        N put(N n) {
            stack.get(stack.size() - 1).add(n);
            return n;
        }

        N node(Item[] items, float r, float ox, float oy, M... m) {
            N n = new N();
            n.items.addAll(Arrays.asList(items));
            n.r = r;
            n.ox = ox;
            n.oy = oy;
            n.m = m;
            return put(n);
        }

        N circle(float x, float y, float r, int col, M... m) {
            N n = new N();
            n.dot = true;
            n.x = x;
            n.y = y;
            n.rad = r;
            n.color = col;
            n.m = m;
            return put(n);
        }

        N rect(float x, float y, float w, float h, int col, float r, float rot, M... m) {
            return node(new Item[]{fill(rrect(x, y, w, h, r), col)}, rot, x + w / 2, y + h / 2, m);
        }

        N path(Path d, int col) {
            return node(new Item[]{fill(d, col)}, 0, 0, 0);
        }

        void group(float[]... tf) {
            N g = new N();
            g.group = tf;
            g.kids = new ArrayList<>();
            put(g);
            stack.add(g.kids);
        }

        void end() {
            stack.remove(stack.size() - 1);
        }

        float lum(int c) {
            return luminance(c);
        }

        M flow(float v, float period) {
            M m = new M();
            m.k = SCROLL;
            m.v = -v * period / TAU;
            m.period = period;
            return m;
        }

        N wave(float y, float amp, float period, float ph, int col, float v) {
            float a = -40 - period, b = w + 40 + period;
            Path d = sine(y, amp, period, ph, a, b);
            d.lineTo(b, h + 40);
            d.lineTo(a, h + 40);
            d.close();
            return node(new Item[]{fill(d, col)}, 0, 0, 0, flow(v, period));
        }
    }

    private static Item fill(Path d, int col) {
        Item i = new Item();
        i.path = d;
        i.color = col;
        return i;
    }

    private static Item fill(Path d, int col, float op) {
        Item i = fill(d, col);
        i.alpha = Math.round(255 * op);
        return i;
    }

    private static Item line(Path d, int col, float sw, boolean butt) {
        Item i = new Item();
        i.path = d;
        i.color = col;
        i.stroke = true;
        i.sw = sw;
        i.butt = butt;
        return i;
    }

    private static Item line(Path d, int col, float sw) {
        return line(d, col, sw, false);
    }

    // motions (their random parts come from the motion generator, like c.mr in nyri-wall)

    private static M spin(C c, double lo, double hi, int sign) {
        M m = new M();
        m.k = SPIN;
        m.v = c.mr.uniform(lo, hi) * (sign != 0 ? sign : (c.mr.flag() ? 1 : -1));
        return m;
    }

    private static M spin(C c, double lo, double hi) {
        return spin(c, lo, hi, 0);
    }

    private static M wob(C c, int kind, float a, double lo, double hi, Float ph) {
        M m = new M();
        m.k = kind;
        m.a = a;
        m.p = c.mr.uniform(lo, hi);
        m.ph = ph != null ? ph : c.mr.uniform(0, TAU);
        return m;
    }

    private static M sway(C c, double a, double lo, double hi) {
        return wob(c, SWAY, (float) a, lo, hi, null);
    }

    private static M sway(C c, double a, double lo, double hi, float ph) {
        return wob(c, SWAY, (float) a, lo, hi, ph);
    }

    private static M breathe(C c, double a, double lo, double hi) {
        return wob(c, BREATHE, (float) a, lo, hi, null);
    }

    private static M breathe(C c, double a, double lo, double hi, float ph) {
        return wob(c, BREATHE, (float) a, lo, hi, ph);
    }

    private static M stretch(C c, double a, double lo, double hi) {
        return wob(c, STRETCH, (float) a, lo, hi, null);
    }

    private static M bob(C c, double dx, double dy, double lo, double hi, Float ph) {
        M m = wob(c, BOB, 0, lo, hi, ph);
        m.dx = (float) dx;
        m.dy = (float) dy;
        return m;
    }

    private static M bob(C c, double dx, double dy, double lo, double hi) {
        return bob(c, dx, dy, lo, hi, null);
    }

    private static M twinkle(C c, double a, double lo, double hi) {
        M m = new M();
        m.k = TWINKLE;
        m.a = (float) a;
        m.p = c.mr.uniform(lo, hi);
        return m;
    }

    private static M flip(C c, double lo, double hi) {
        M m = new M();
        m.k = FLIP;
        m.e = c.mr.uniform(lo, hi);
        m.ph = c.mr.uniform(1, m.e);
        m.st = 90 * (c.mr.flag() ? 1 : -1);
        return m;
    }

    private static M drift(C c, double v, double x0, double bw) {
        M m = new M();
        m.k = DRIFT;
        m.v = (float) v;
        m.x0 = (float) x0;
        m.bw = (float) bw;
        m.w = c.w;
        return m;
    }

    private static M orbit(C c, double rx, double ry, double lo, double hi) {
        M m = new M();
        m.k = ORBIT;
        m.rx = (float) rx;
        m.ry = (float) ry;
        m.p = c.mr.uniform(lo, hi) * (c.mr.flag() ? 1 : -1);
        m.ph = c.mr.uniform(0, TAU);
        return m;
    }

    private static M scroll(float v, float period) {
        M m = new M();
        m.k = SCROLL;
        m.v = v;
        m.period = period;
        return m;
    }

    // ---- generators --------------------------------------------------------------------------------------

    private static void bauhaus(C c, Rng r) {
        int cols = c.nl(r.ci(6, 7, 8, 9), 2);
        float s = c.w / cols;
        int rows = (int) Math.ceil(c.h / s);
        float oy = (c.h - rows * s) / 2;
        List<Integer> colors = c.all();
        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < cols; i++) {
                float x = i * s, y = oy + j * s;
                int base = r.pick(colors);
                List<Integer> inks = new ArrayList<>(colors);
                inks.remove((Integer) base);
                int ink = r.pick(inks);
                c.rect(x - 0.75f, y - 0.75f, s + 1.5f, s + 1.5f, base, 0, 0);
                int kind = r.ci(0, 1, 2, 3, 4, 0, 3, 2);   // circle ring half quarter tri circle quarter half
                float cx = x + s / 2, cy = y + s / 2, k = (s + 2) / s, S = s + 2, X = cx - S / 2, Y = cy - S / 2;
                if (kind == 0) {
                    c.node(new Item[]{fill(circ(cx, cy, s * 0.36f * k), ink)}, 0, cx, cy, breathe(c, 0.08, 3.5, 6.5));
                } else if (kind == 1) {
                    List<Integer> others = new ArrayList<>(colors);
                    others.remove((Integer) ink);
                    int inner = r.pick(others);
                    c.node(new Item[]{fill(circ(cx, cy, s * 0.4f * k), ink), fill(circ(cx, cy, s * 0.18f * k), inner)}, 0, cx, cy,
                            breathe(c, 0.07, 4, 7));
                } else {
                    int d = r.ci(0, 90, 180, 270);
                    Path p = new Path();
                    if (kind == 2) {
                        p.moveTo(X, Y + S);
                        p.arcTo(new RectF(X, Y + S - S / 2, X + S, Y + S + S / 2), 180, 180);
                        p.close();
                    } else if (kind == 3) {
                        p.moveTo(X, Y + S);
                        p.lineTo(X, Y);
                        p.arcTo(new RectF(X - S, Y, X + S, Y + 2 * S), 270, 90);
                        p.close();
                    } else {
                        p = poly(true, X, Y, X + S, Y + S, X, Y + S);
                    }
                    c.node(new Item[]{fill(p, ink)}, d, cx, cy, flip(c, 9, 26));
                }
            }
        }
    }

    private static void arches(C c, Rng r) {
        int cols = c.nl(r.ci(5, 6), 2);
        float unit = c.w / cols;
        int rows = (int) Math.ceil(c.h / unit) + 2;
        int bands = r.ci(4, 5);
        for (int j = 0; j < rows; j++) {
            float off = j % 2 == 1 ? unit / 2 : 0;
            for (int i = -1; i < cols + 1; i++) {
                float cx = i * unit + unit / 2 + off, base = (j + 1) * unit * 0.95f - unit * 0.3f;
                List<Integer> seq = r.sample(c.fgList(), c.fg.length);
                Item[] ps = new Item[bands];
                for (int b = 0; b < bands; b++) {
                    float rad = unit / 2 * (1 - (float) b / bands);
                    int col = b < bands - 1 ? seq.get(b % seq.size()) : c.bg;
                    Path p = new Path();
                    p.moveTo(cx - rad, base + unit);
                    p.lineTo(cx - rad, base);
                    p.arcTo(new RectF(cx - rad, base - rad, cx + rad, base + rad), 180, 180);
                    p.lineTo(cx + rad, base + unit);
                    p.close();
                    ps[b] = fill(p, col);
                }
                c.node(ps, 0, cx, base, bob(c, 0, unit * 0.022, 6.5, 6.5, -(cx / c.w) * 5 - j * 0.7f));
            }
        }
    }

    private static void waves(C c, Rng r) {
        List<Integer> colors = c.all();
        float bandH = c.h / colors.size();
        int way = c.mr.flag() ? 1 : -1;
        for (int k = 1; k < colors.size(); k++) {
            float y = bandH * k, amp = Math.min(c.u * r.uniform(0.02, 0.045), bandH * 0.18f), freq = r.cd(1, 1.5, 2) / c.k,
                    ph = r.uniform(0, 2 * Math.PI);
            c.wave(y, amp, c.w / freq, ph, colors.get(k), way * c.mr.uniform(0.25, 0.4) * (1 + 0.35f * k / colors.size()));
        }
    }

    private static List<Integer> sortedByLum(List<Integer> l, boolean reverse) {
        List<Integer> out = new ArrayList<>(l);
        Collections.sort(out, (a, b) -> Float.compare(luminance(a), luminance(b)));
        if (reverse) Collections.reverse(out);
        return out;
    }

    private static int maxLum(List<Integer> l) {
        int best = l.get(0);
        for (int c : l) if (luminance(c) > luminance(best)) best = c;
        return best;
    }

    private static void hills(C c, Rng r) {
        int sun = r.pick(c.fgList());
        float sx = c.w * r.uniform(0.2, 0.8), sy = c.h * r.uniform(0.3, 0.45), sr = c.u * r.uniform(0.14, 0.2);
        c.circle(sx, sy, sr, sun, breathe(c, 0.025, 8, 12));
        List<Integer> layers = new ArrayList<>();
        for (int k : c.fg) if (k != sun) layers.add(k);
        if (layers.isEmpty()) layers = c.fgList();
        layers = sortedByLum(layers, luminance(c.bg) < 0.5f);
        while (layers.size() < 4) layers.add(layers.get(layers.size() - 1));
        for (int k = 0; k < 4; k++) {
            float base = c.h * (0.5f + 0.13f * k), amp = c.u * r.uniform(0.05, 0.12), ph = r.uniform(0, 6);
            int cnt = c.n(10, 1, 10, 400);
            List<float[]> pts = new ArrayList<>();
            for (int t = -1; t < cnt + 2; t++) {
                float x = c.w * t / cnt;
                pts.add(new float[]{x, base - amp * (0.6f * (float) Math.sin(ph + 2 * Math.PI * x / (c.w * c.k) * r.uniform(0.8, 1.4))
                        + 0.4f * (float) Math.sin(ph * 2 + 5 * Math.PI * x / (c.w * c.k)))});
            }
            Path d = smooth(pts, false);
            d.lineTo(c.w + 40, c.h + 40);
            d.lineTo(-40, c.h + 40);
            d.close();
            c.path(d, layers.get(k));
        }
        int cloud = luminance(c.bg) < 0.5f ? maxLum(c.fgList()) : maxLum(c.all());
        int wind = c.mr.flag() ? 1 : -1;
        int clouds = r.ci(2, 3);
        for (int q = 0; q < clouds; q++) {
            float cx = c.w * r.uniform(0.1, 0.9), cy = c.h * r.uniform(0.12, 0.3), w = c.w * c.k * r.uniform(0.09, 0.14);
            if (Math.abs(cx - sx) < sr + w * 0.7f) cx = sx < c.w / 2 ? (sx + sr + w) % c.w : Math.max(w, sx - sr - w);
            float h = w * 0.2f;
            List<Item> ps = new ArrayList<>();
            ps.add(fill(rrect(cx - w / 2, cy, w, h, h / 2), cloud));
            float[][] bumps = {{0.3f, 0.22f}, {0.52f, 0.3f}, {0.74f, 0.18f}};
            for (float[] b : bumps) ps.add(fill(circ(cx - w / 2 + w * b[0], cy + h * 0.2f, w * b[1] * 0.62f), cloud));
            c.node(ps.toArray(new Item[0]), 0, cx, cy, drift(c, wind * c.mr.uniform(9, 18) * c.w / 2880, cx - w / 2, w));
        }
    }

    private static void pebbles(C c, Rng r) {
        List<float[]> placed = new ArrayList<>();
        int tries = 0;
        while (placed.size() < c.np(14, 2) && tries < c.n(900, 2, 1, 20000)) {
            tries++;
            float rad = c.u * r.uniform(0.09, 0.2), x = r.uniform(-rad * 0.3, c.w + rad * 0.3), y = r.uniform(-rad * 0.3, c.h + rad * 0.3);
            boolean ok = true;
            for (float[] p : placed) if (Math.hypot(x - p[0], y - p[1]) <= (rad + p[2]) * 0.92) { ok = false; break; }
            if (!ok) continue;
            placed.add(new float[]{x, y, rad});
            final float[] ph = {r.uniform(0, 6), r.uniform(0, 6), r.uniform(0, 6)};
            final float rr = rad;
            Radius fn = t -> rr * (1 + 0.08f * (float) Math.sin(2 * t + ph[0]) + 0.05f * (float) Math.sin(3 * t + ph[1])
                    + 0.03f * (float) Math.sin(5 * t + ph[2]));
            int col = r.pick(c.fgList());
            List<Item> ps = new ArrayList<>();
            ps.add(fill(polar(x, y, fn, 48, r.uniform(0, 6)), col));
            if (r.chance(0.3)) {
                List<Integer> others = c.all();
                others.remove((Integer) col);
                int inner = r.pick(others);
                ps.add(fill(polar(x + rad * 0.08f, y - rad * 0.05f, t -> fn.at(t) * 0.5f, 48, r.uniform(0, 6)), inner));
            }
            float a = c.mr.uniform(0, TAU);
            c.node(ps.toArray(new Item[0]), 0, x, y, sway(c, c.mr.uniform(3, 6), 8, 13),
                    bob(c, Math.cos(a) * rad * 0.05, Math.sin(a) * rad * 0.05, 6, 10));
        }
        int dots = c.n(r.ci(0, 12, 24), 2, 0, 400);
        for (int i = 0; i < dots; i++) c.circle(r.uniform(0, c.w), r.uniform(0, c.h), c.u * r.uniform(0.004, 0.008), r.pick(c.fgList()));
    }

    private static void ribbons(C c, Rng r) {
        int n = c.n(r.ci(4, 5, 6), 1, 2, 40);
        float tilt = r.uniform(-16, 16);
        c.group(new float[]{2, tilt, c.w / 2, c.h / 2}, new float[]{1, 1.25f}, new float[]{0, -c.w * 0.1f, -c.h * 0.1f});
        int way = c.mr.flag() ? 1 : -1;
        for (int k = 0; k < n; k++) {
            float width = c.u * r.uniform(0.09, 0.15), y0 = c.h * (0.1f + 0.8f * k / Math.max(1, n - 1)) + c.h * r.uniform(-0.05, 0.05);
            float amp = c.u * r.uniform(0.08, 0.16), freq = r.uniform(0.7, 1.3) / c.k, ph = r.uniform(0, 6);
            int steps = c.n(6, 1, 6, 400);
            float period = TAU * c.w / (6 * freq), x0 = -c.w / steps, x1 = c.w * (steps + 1) / steps;
            c.node(new Item[]{line(sine(y0, amp, period, ph, x0 - period, x1 + period), c.fg[k % c.fg.length], width)}, 0, 0, 0,
                    c.flow(way * c.mr.uniform(0.35, 0.6), period));
        }
        c.end();
    }

    private static Radius cookie(int n, float a) {
        return t -> 1 + a * (float) Math.cos(n * t);
    }

    private static void shapes(C c, Rng r) {
        Radius[] cookies = {cookie(9, 0.07f), cookie(4, 0.14f), cookie(8, 0.06f), cookie(12, 0.04f), cookie(6, 0.1f), cookie(4, 0.26f)};
        // kinds: six cookies, a circle, a pill, an arch
        List<float[]> placed = new ArrayList<>();
        int tries = 0;
        while (placed.size() < c.np(12, 2) && tries < c.n(900, 2, 1, 20000)) {
            tries++;
            float R = c.u * r.uniform(0.1, 0.22), x = r.uniform(-R * 0.4, c.w + R * 0.4), y = r.uniform(-R * 0.4, c.h + R * 0.4);
            boolean ok = true;
            for (float[] p : placed) if (Math.hypot(x - p[0], y - p[1]) <= (R + p[2]) * 0.95) { ok = false; break; }
            if (!ok) continue;
            placed.add(new float[]{x, y, R});
            int col = r.pick(c.fgList());
            int kind = r.below(9);
            float rot = r.uniform(0, 360), a = c.mr.uniform(0, TAU);
            M dr = bob(c, Math.cos(a) * R * 0.06, Math.sin(a) * R * 0.06, 7, 12);
            if (kind == 6) {
                c.circle(x, y, R, col, breathe(c, 0.06, 4, 7), dr);
            } else if (kind == 7) {
                c.node(new Item[]{fill(rrect(x - R, y - R * 0.5f, 2 * R, R, R * 0.5f), col)}, rot, x, y, sway(c, c.mr.uniform(10, 20), 5, 9), dr);
            } else if (kind == 8) {
                Path p = new Path();
                p.moveTo(x - R, y + R);
                p.lineTo(x - R, y);
                p.arcTo(new RectF(x - R, y - R, x + R, y + R), 180, 180);
                p.lineTo(x + R, y + R);
                p.close();
                c.node(new Item[]{fill(p, col)}, rot, x, y, sway(c, c.mr.uniform(8, 16), 6, 10), dr);
            } else {
                final Radius k = cookies[kind];
                final float RR = R;
                Path p = polar(x, y, t -> RR * k.at(t) * 0.92f, 120, (float) Math.toRadians(rot));
                if (c.mr.chance(0.4)) c.node(new Item[]{fill(p, col)}, 0, x, y, spin(c, 4, 12), breathe(c, 0.04, 5, 8), dr);
                else c.node(new Item[]{fill(p, col)}, 0, x, y, spin(c, 4, 12), dr);
            }
        }
    }

    private static void rainbow(C c, Rng r) {
        float[][] corners = {{0, c.h}, {c.w, c.h}, {c.w / 2, c.h}, {0, 0}};
        float[] corner = corners[r.below(4)];
        int n = c.n(r.ci(6, 7, 8), 1, 4, 40);
        float maxr = (float) Math.hypot(c.w, c.h) * r.uniform(0.7, 0.95) * Math.max(1, c.k);
        int L = c.fg.length;
        float band = maxr / (n + 1), cycle = L * c.mr.uniform(3.2, 4.2);
        for (int k = 0; k < n + L; k++) {
            float start = maxr - k * band, end = maxr - (k - L) * band, s0 = Math.max(0f, start / end);
            M m = new M();
            m.k = SWELL;
            m.s0 = s0;
            m.p = cycle;
            c.node(new Item[]{fill(circ(corner[0], corner[1], end), c.fg[k % L])}, 0, corner[0], corner[1], m);
        }
        c.circle(corner[0], corner[1], band, c.bg);
        float far = (float) Math.hypot(c.w, c.h) * 2 + maxr;
        Path mask = circ(corner[0], corner[1], far);
        mask.addPath(ccwCirc(corner[0], corner[1], maxr));
        c.node(new Item[]{fill(mask, c.bg)}, 0, 0, 0);
    }

    private static void dots(C c, Rng r) {
        int cols = c.nl(24, 6);
        float s = c.w / cols;
        int rows = (int) Math.ceil(c.h / s);
        float fx = r.uniform(0.2, 0.8) * c.w, fy = r.uniform(0.2, 0.8) * c.h, maxd = (float) Math.hypot(c.w, c.h);
        int[] ink = Arrays.copyOfRange(c.fg, 0, Math.min(3, c.fg.length));
        boolean hex = r.chance(0.5);
        float w = c.mr.uniform(0.7, 1.0);
        for (int j = 0; j <= rows; j++) {
            for (int i = 0; i <= cols; i++) {
                float x = i * s + (hex && j % 2 == 1 ? s / 2 : 0), y = j * s, d = (float) Math.hypot(x - fx, y - fy) / maxd;
                if (d > 0.42f) continue;
                M m = new M();
                m.k = RIPPLE;
                m.d = d;
                m.n = (float) (2.4 * Math.PI);
                m.w = w;
                m.min = 0.134f;
                c.circle(x, y, s * 0.45f, ink.length > 1 ? ink[(i + j) % ink.length] : ink[0], m);
            }
        }
    }

    private static void cosmos(C c, Rng r) {
        int stars = c.np(90, 2);
        for (int i = 0; i < stars; i++) {
            float x = r.uniform(0, c.w), y = r.uniform(0, c.h), s = r.uniform(3, 9) * c.k;
            c.circle(x, y, s, c.fg[c.fg.length - 1], twinkle(c, c.mr.uniform(0.4, 0.85), 1.8, 6));
        }
        float mx = c.w * r.uniform(0.6, 0.9), my = c.h * r.uniform(0.15, 0.35), mR = c.u * 0.08f;
        List<float[]> placed = new ArrayList<>();
        placed.add(new float[]{mx, my, mR * 1.6f, mR * 1.6f});
        int want = c.n(r.ci(2, 3), 2, 1, 14), tries = 0;
        List<float[]> planets = new ArrayList<>();
        while (planets.size() < want && tries < 600) {
            tries++;
            float R = c.u * r.uniform(0.1, 0.3) * (tries < 200 ? 1 : 0.7f), x = c.w * r.uniform(0.1, 0.9), y = c.h * r.uniform(0.15, 0.85);
            float rx = R * 2f, ry = R * 1.25f;
            boolean hit = false;
            for (float[] p : placed) {
                if (Math.abs(x - p[0]) < rx + p[2] && Math.abs(y - p[1]) < ry + p[3]
                        && Math.pow((x - p[0]) / (rx + p[2]), 2) + Math.pow((y - p[1]) / (ry + p[3]), 2) < 1) { hit = true; break; }
            }
            if (hit) continue;
            placed.add(new float[]{x, y, rx, ry});
            planets.add(new float[]{x, y, R});
        }
        for (float[] p : planets) {
            float x = p[0], y = p[1], R = p[2];
            int col = r.pick(c.fgList());
            float tilt = r.uniform(-25, 25);
            List<Integer> others = new ArrayList<>();
            for (int k : c.fg) if (k != col) others.add(k);
            int ring = r.pick(others.isEmpty() ? c.fgList() : others);
            Path oval = new Path();
            oval.addOval(new RectF(x - R * 1.8f, y - R * 0.42f, x + R * 1.8f, y + R * 0.42f), Path.Direction.CW);
            Path front = new Path();
            front.moveTo(x - R * 1.8f, y);
            front.arcTo(new RectF(x - R * 1.8f, y - R * 0.42f, x + R * 1.8f, y + R * 0.42f), 180, -180);
            c.node(new Item[]{line(oval, ring, R * 0.12f), fill(circ(x, y, R), col), line(front, ring, R * 0.12f, true)}, tilt, x, y,
                    sway(c, c.mr.uniform(3, 6), 14, 22), bob(c, 0, R * 0.1, 10, 16));
        }
        c.node(new Item[]{fill(circ(mx, my, mR), c.fg[0]), fill(circ(mx + mR * 0.35f, my - mR * 0.2f, mR * 0.85f), c.bg)}, 0, mx, my,
                orbit(c, c.u * 0.04, c.u * 0.02, 50, 70));
        c.scene.comets = true;
        c.scene.cometColor = c.fg[c.fg.length - 1];
    }

    private static void truchet(C c, Rng r) {
        int cols = c.nl(r.ci(10, 12, 14), 3);
        float s = c.w / cols;
        int rows = (int) Math.ceil(c.h / s);
        float oy = (c.h - rows * s) / 2;
        int a, b;
        if (c.fg.length > 1) {
            List<Integer> two = r.sample(c.fgList(), 2);
            a = two.get(0);
            b = two.get(1);
        } else {
            a = c.fg[0];
            b = c.bg;
        }
        float q = s / 2 + 0.75f;
        c.rect(-2, -2, c.w + 4, c.h + 4, a, 0, 0);
        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < cols; i++) {
                float x = i * s, y = oy + j * s;
                int turn = r.chance(0.5) ? 0 : 90;
                Path wedges = new Path();
                wedges.moveTo(x, y);
                wedges.lineTo(x + q, y);
                wedges.arcTo(new RectF(x - q, y - q, x + q, y + q), 0, 90);
                wedges.close();
                wedges.moveTo(x + s, y + s);
                wedges.lineTo(x + s - q, y + s);
                wedges.arcTo(new RectF(x + s - q, y + s - q, x + s + q, y + s + q), 180, 90);
                wedges.close();
                c.node(new Item[]{fill(wedges, b)}, turn, x + s / 2, y + s / 2, flip(c, 22, 65));
            }
        }
        List<Integer> accent = new ArrayList<>();
        for (int k : c.fg) if (k != a && k != b) accent.add(k);
        if (accent.isEmpty()) accent.add(c.bg);
        int count = cols * Math.max(1, rows / 6);
        for (int q2 = 0; q2 < count; q2++) {
            int i = r.below(cols), j = r.below(rows);
            c.circle(i * s + s / 2, oy + j * s + s / 2, s * 0.14f, r.pick(accent), breathe(c, 0.2, 3, 5));
        }
    }

    private static void memphis(C c, Rng r) {
        List<float[]> placed = new ArrayList<>();
        int items = 0, tries = 0;
        float u = c.u / 14;
        while (items < c.n(34, 2, 1, 300) && tries < c.n(3000, 2, 1, 40000)) {
            tries++;
            float x = r.uniform(0, c.w), y = r.uniform(0, c.h);
            boolean ok = true;
            for (float[] p : placed) if (Math.hypot(x - p[0], y - p[1]) <= u * 2.6f) { ok = false; break; }
            if (!ok) continue;
            placed.add(new float[]{x, y});
            items++;
            int col = r.pick(c.fgList());
            float rot = r.uniform(0, 360), sw = u * 0.28f;
            int kind = r.below(7);
            M m0 = sway(c, c.mr.uniform(8, 16), 2.5, 5), m1 = bob(c, 0, u * 0.25, 3, 6);
            if (kind == 0) {
                List<float[]> pts = new ArrayList<>();
                for (int k = 0; k < 9; k++) pts.add(new float[]{x - 1.6f * u + k * u * 0.4f, y + (k % 2 == 1 ? u * 0.35f : -u * 0.35f)});
                c.node(new Item[]{line(smooth(pts, false), col, sw)}, rot, x, y, m0, m1);
            } else if (kind == 1) {
                float[] xy = new float[16];
                for (int k = 0; k < 8; k++) {
                    xy[2 * k] = x - 1.4f * u + k * u * 0.4f;
                    xy[2 * k + 1] = y + (k % 2 == 1 ? u * 0.3f : -u * 0.3f);
                }
                c.node(new Item[]{line(poly(false, xy), col, sw)}, rot, x, y, m0, m1);
            } else if (kind == 2) {
                float R = u * 0.9f;
                Path d = poly(true, x + R, y, x + R * (float) Math.cos(2.094), y + R * (float) Math.sin(2.094),
                        x + R * (float) Math.cos(4.189), y + R * (float) Math.sin(4.189));
                c.node(new Item[]{fill(d, col), line(d, col, u * 0.25f)}, rot, x, y, m0, m1);
            } else if (kind == 3) {
                Path d = new Path();
                for (int dx = 0; dx < 3; dx++) for (int dy = 0; dy < 3; dy++) d.addCircle(x + (dx - 1) * u * 0.55f, y + (dy - 1) * u * 0.55f, u * 0.12f, Path.Direction.CW);
                c.node(new Item[]{fill(d, col)}, rot, x, y, m0, m1);
            } else if (kind == 4) {
                float R = u * 0.9f;
                Path d = new Path();
                d.moveTo(x - R, y);
                d.arcTo(new RectF(x - R, y - R, x + R, y + R), 180, 180);
                d.close();
                c.node(new Item[]{fill(d, col)}, rot, x, y, m0, m1);
            } else if (kind == 5) {
                c.node(new Item[]{line(circ(x, y, u * 0.7f), col, sw)}, 0, x, y, breathe(c, 0.12, 2.5, 4.5), m1);
            } else {
                c.node(new Item[]{fill(rrect(x - u, y - u * 0.18f, 2 * u, u * 0.36f, u * 0.18f), col)}, rot, x, y, m0, m1);
            }
        }
    }

    private static void sunset(C c, Rng r) {
        List<Integer> ink = sortedByLum(c.fgList(), false);
        boolean darkBg = luminance(c.bg) < 0.5f;
        int sun = darkBg ? ink.get(ink.size() - 1) : luminance(ink.get(0)) < luminance(c.bg) - 0.25f ? ink.get(0) : ink.get(ink.size() - 1);
        List<Integer> rest = new ArrayList<>();
        for (int k : c.fg) if (k != sun) rest.add(k);
        if (rest.isEmpty()) rest.add(c.bg);
        List<Integer> sky = new ArrayList<>();
        sky.add(c.bg);
        List<Integer> sortedRest = sortedByLum(rest, !darkBg);
        sky.addAll(sortedRest.subList(0, Math.min(2, sortedRest.size())));
        float horizon = c.h * r.uniform(0.6, 0.66);
        List<Integer> cand = new ArrayList<>();
        for (int k : c.all()) if (k != sun && k != sky.get(sky.size() - 1)) cand.add(k);
        if (cand.isEmpty()) cand = rest;
        int water = cand.get(0);
        for (int k : cand) if (Math.abs(luminance(k) - luminance(sun)) > Math.abs(luminance(water) - luminance(sun))) water = k;
        float bandH = horizon / sky.size();
        for (int k = 0; k < sky.size(); k++) c.rect(0, k * bandH, c.w, bandH + 1, sky.get(k), 0, 0);
        float cx = c.w * r.uniform(0.35, 0.65), R = c.u * r.uniform(0.24, 0.3);
        c.circle(cx, horizon, R, sun, bob(c, 0, R * 0.09, 26, 34, 0f));
        float y = horizon - R * 0.6f;
        for (int k = 0; k < 5; k++) {
            float gap = R * 0.03f * (k + 1.5f);
            int band = sky.get(Math.min(sky.size() - 1, Math.max(0, (int) Math.floor(y / bandH))));
            c.rect(cx - R - 2, y, 2 * R + 4, gap, band, 0, 0);
            y += gap + R * 0.08f;
        }
        c.rect(0, horizon, c.w, c.h - horizon + 1, water, 0, 0);
        for (int k = 0; k < 6; k++) {
            float w = 2 * R * (0.9f - k * 0.13f), yy = horizon + c.u * 0.035f + k * c.u * 0.045f;
            c.rect(cx - w / 2, yy, w, c.u * 0.014f, sun, c.u * 0.007f, 0, stretch(c, 0.1 + k * 0.03, 2.2, 4));
        }
    }

    private static void peaks(C c, Rng r) {
        List<Integer> layers = sortedByLum(c.fgList(), luminance(c.bg) < 0.5f);
        while (layers.size() < 4) layers.add(layers.get(layers.size() - 1));
        int snow = maxLum(c.all());
        int moon = luminance(c.bg) < 0.5f ? snow : layers.get(0);
        c.circle(c.w * r.uniform(0.15, 0.85), c.h * r.uniform(0.14, 0.26), c.u * 0.07f, moon, orbit(c, c.u * 0.06, c.u * 0.025, 60, 90));
        for (int k = 0; k < 4; k++) {
            float base = c.h * (0.5f + 0.14f * k);
            int n = c.n(r.ci(3, 4, 5) + k, 1, 2, 60);
            float step = c.w * 1.1f / n;
            List<float[]> pts = new ArrayList<>();
            for (int i = 0; i <= n; i++) {
                float x = -c.w * 0.05f + i * step;
                pts.add(new float[]{x + step * r.uniform(0.3, 0.7), base - c.u * r.uniform(0.14, 0.3) / (1 + k * 0.35f)});
                pts.add(new float[]{x + step, base - c.h * r.uniform(0.0, 0.06)});
            }
            Path d = new Path();
            d.moveTo(-c.w * 0.05f, c.h + 10);
            d.lineTo(-c.w * 0.05f, base);
            for (float[] p : pts) d.lineTo(p[0], p[1]);
            d.lineTo(c.w * 1.1f, c.h + 10);
            d.close();
            c.path(d, layers.get(k));
            if (k < 2) {
                float lx = -c.w * 0.05f, ly = base;
                for (int i = 0; i + 1 < pts.size(); i += 2) {
                    float px = pts.get(i)[0], py = pts.get(i)[1], vx = pts.get(i + 1)[0], vy = pts.get(i + 1)[1], t = 0.24f;
                    float ax = px + (lx - px) * t, ay = py + (ly - py) * t, bx = px + (vx - px) * t, by = py + (vy - py) * t;
                    float mx = px + (ax + bx - 2 * px) * 0.25f, my = (ay + by) / 2 + (by - py) * 0.1f;
                    c.path(poly(true, px, py, bx, by, mx, my, ax, ay), snow);
                    lx = vx;
                    ly = vy;
                }
            }
        }
    }

    private static Radius petalRadius(float R, int n, float a) {
        return t -> R * (1 - a + a * (float) Math.pow(Math.abs(Math.cos(n * t / 2)), 0.7));
    }

    private static void flowers(C c, Rng r) {
        List<float[]> placed = new ArrayList<>();
        int tries = 0;
        while (placed.size() < c.np(7, 2) && tries < c.n(800, 2, 1, 20000)) {
            tries++;
            float R = c.u * r.uniform(0.18, 0.34), x = r.uniform(-R * 0.3, c.w + R * 0.3), y = r.uniform(-R * 0.3, c.h + R * 0.3);
            boolean ok = true;
            for (float[] p : placed) if (Math.hypot(x - p[0], y - p[1]) <= (R + p[2]) * 0.8f) { ok = false; break; }
            if (!ok) continue;
            placed.add(new float[]{x, y, R});
            int which = r.below(4);
            int petals = new int[]{5, 6, 8, 4}[which];
            float depth = new float[]{0.22f, 0.2f, 0.14f, 0.3f}[which];
            int col = r.pick(c.fgList());
            Path petal = polar(x, y, petalRadius(R, petals, depth), 160, r.uniform(0, 6));
            List<Integer> others = c.all();
            others.remove((Integer) col);
            c.node(new Item[]{fill(petal, col), fill(circ(x, y, R * 0.3f), r.pick(others))}, 0, x, y, spin(c, 2.5, 7), breathe(c, 0.03, 6, 9));
        }
    }

    private static void rings(C c, Rng r) {
        int k = c.n(r.ci(2, 3), 1, 1, 12);
        float[][] centres = new float[k][];
        for (int i = 0; i < k; i++) centres[i] = new float[]{c.w * (i + r.uniform(0.2, 0.8)) / k, c.h * r.uniform(0.1, 0.9)};
        for (float[] ctr : centres) {
            int n = r.ci(5, 6, 7);
            float R = c.u * r.uniform(0.45, 0.75);
            List<Integer> seq = r.sample(c.fgList(), c.fg.length);
            float p = c.mr.uniform(6, 9);
            for (int q = 0; q < n; q++) {
                M m = new M();
                m.k = BREATHE;
                m.a = 0.05f / (1 + q * 0.3f);
                m.p = p;
                m.ph = -q * 0.6f;
                c.node(new Item[]{fill(circ(ctr[0], ctr[1], R * (1 - (float) q / n)), q % 2 == 0 ? seq.get(q % seq.size()) : c.bg)}, 0, ctr[0], ctr[1], m);
            }
        }
    }

    private static void stripes(C c, Rng r) {
        float ang = r.ci(-30, 30, -45, 45), w = c.u * r.uniform(0.07, 0.1), step = w * 1.5f, L = (float) Math.hypot(c.w, c.h);
        c.group(new float[]{0, c.w / 2, c.h / 2}, new float[]{2, ang, 0, 0});
        float y = -L / 2;
        int k = 0, lane = 0;
        while (y < L / 2) {
            float x = -L / 2;
            List<float[]> segs = new ArrayList<>();
            while (x < L / 2) {
                float seg = r.uniform(0.15, 0.5) * L * Math.min(1.5f, c.k);
                if (r.chance(0.8)) segs.add(new float[]{x, seg, c.fg[k % c.fg.length]});
                x += seg + w;
                k++;
            }
            float P = x + L / 2;
            List<Item> ps = new ArrayList<>();
            for (float dx : new float[]{-P, 0, P}) {
                for (float[] s : segs) ps.add(fill(rrect(s[0] + dx, y, s[1], w, w / 2), (int) s[2]));
            }
            float speed = c.mr.uniform(14, 30) * c.w / 2880 * (lane % 2 == 1 ? 1 : -1);
            c.node(ps.toArray(new Item[0]), 0, 0, y, scroll(speed, P));
            y += step;
            lane++;
        }
        c.end();
    }

    private static void bands(C c, Rng r) {
        List<Integer> colors = c.all();
        for (int i = 0; i < colors.size(); i++) {
            float y0 = c.h * i / colors.size(), y1 = c.h * (i + 1) / colors.size();
            c.rect(0, y0, c.w, y1 - y0 + 0.5f, colors.get(i), 0, 0);
        }
    }

    private static void garden(C c, Rng r) {
        List<Integer> ink = sortedByLum(c.fgList(), false);
        int stem = luminance(c.bg) > 0.5f ? ink.get(0) : ink.get(ink.size() - 1);
        float groundY = c.h * r.uniform(0.76, 0.84);
        if (r.chance(0.6)) {
            List<float[]> hill = new ArrayList<>();
            for (int t = -1; t < 7; t++) hill.add(new float[]{c.w * t / 5f, groundY - c.h * r.uniform(0.04, 0.12)});
            Path d = smooth(hill, false);
            d.lineTo(c.w + 40, c.h + 40);
            d.lineTo(-40, c.h + 40);
            d.close();
            c.path(d, r.pick(c.fgList()));
        }
        if (r.chance(0.6)) c.circle(c.w * r.uniform(0.12, 0.88), c.h * r.uniform(0.12, 0.24), c.u * r.uniform(0.07, 0.11), r.pick(c.fgList()), breathe(c, 0.03, 8, 12));
        int n = c.n(r.ci(5, 6, 7, 8), 1, 2, 60);
        List<Integer> heads = new ArrayList<>();
        for (int i = 0; i < n; i++) heads.add(c.fg[i % c.fg.length]);
        Collections.shuffle(heads, r.r);
        float gust = c.mr.uniform(4.5, 6.5);
        for (int i = 0; i < n; i++) {
            float x = c.w * (0.08f + 0.84f * (i + r.uniform(-0.3, 0.3)) / (n - 1)), hgt = Math.min(c.h * 0.7f, c.u * r.uniform(0.2, 0.46));
            float bend = hgt * r.uniform(-0.14, 0.14), sw = c.u * r.uniform(0.012, 0.018);
            float tx = x + bend, ty = groundY - hgt;
            List<Item> ps = new ArrayList<>();
            Path st = new Path();
            st.moveTo(x, groundY + 40);
            st.quadTo(x + bend * 0.2f, groundY - hgt * 0.5f, tx, ty);
            ps.add(line(st, stem, sw));
            int leaves = r.ci(1, 2);
            for (int q = 0; q < leaves; q++) {
                float kk = r.uniform(0.3, 0.6), lx = x + bend * kk * kk, ly = groundY - hgt * kk;
                int side = r.flag() ? 1 : -1;
                float L = c.u * r.uniform(0.06, 0.09);
                ps.add(fill(rrect(side > 0 ? lx : lx - L, ly - L * 0.18f, L, L * 0.36f, L * 0.18f, side * r.uniform(-50, -25), lx, ly), stem));
            }
            float R = c.u * r.uniform(0.06, 0.11);
            int which = r.below(4);
            int petals = new int[]{5, 6, 8, 12}[which];
            float depth = new float[]{0.25f, 0.2f, 0.14f, 0.08f}[which];
            int col = heads.get(i);
            ps.add(fill(polar(tx, ty, petalRadius(R, petals, depth), 120, r.uniform(0, 6)), col));
            List<Integer> others = c.all();
            others.remove((Integer) col);
            ps.add(fill(circ(tx, ty, R * r.uniform(0.25, 0.35)), r.pick(others)));
            c.node(ps.toArray(new Item[0]), 0, x, groundY, sway(c, c.mr.uniform(1.8, 3.5), gust, gust, -x / c.w * 4));
        }
        c.rect(-20, groundY, c.w + 40, c.h - groundY + 20, r.chance(0.3) ? stem : r.pick(c.fgList()), 0, 0);
    }

    private static void objects(C c, Rng r) {
        int light = maxLum(c.all());
        float u = c.u;
        float[][] two = {{0.1f, 0.8f, 0.22f}, {0.86f, 0.26f, 0.28f}};
        for (float[] t : two) {
            float x = c.w * t[0] + c.w * r.uniform(-0.04, 0.04), y = c.h * t[1] + c.h * r.uniform(-0.05, 0.05);
            int c1, c2;
            if (c.fg.length > 1) {
                List<Integer> s = r.sample(c.fgList(), 2);
                c1 = s.get(0);
                c2 = s.get(1);
            } else {
                c1 = c.fg[0];
                c2 = c.bg;
            }
            float R = u * t[2], rot = r.uniform(0, 360);
            Path top = new Path();
            top.moveTo(x - R, y);
            top.arcTo(new RectF(x - R, y - R, x + R, y + R), 180, 180);
            top.close();
            Path bottom = new Path();
            bottom.moveTo(x + R, y);
            bottom.arcTo(new RectF(x - R, y - R, x + R, y + R), 0, 180);
            bottom.close();
            c.node(new Item[]{fill(top, c1), fill(bottom, c2)}, rot, x, y, spin(c, 4, 8));
            Item halo = line(circ(x, y, R * 2.4f), light, u * 0.005f);
            halo.alpha = Math.round(255 * 0.55f);
            c.node(new Item[]{halo}, 0, x, y, breathe(c, 0.03, 9, 13));
        }
        List<float[]> placed = new ArrayList<>();
        int tries = 0;
        while (placed.size() < c.np(11, 2) && tries < c.n(900, 2, 1, 20000)) {
            tries++;
            float x = c.w * r.uniform(0.22, 0.74), y = c.h * r.uniform(0.1, 0.9);
            boolean ok = true;
            for (float[] p : placed) if (Math.hypot(x - p[0], y - p[1]) <= u * 0.16f) { ok = false; break; }
            if (!ok) continue;
            placed.add(new float[]{x, y});
            int col = r.pick(c.fgList());
            List<Integer> others = c.all();
            others.remove((Integer) col);
            int other = r.pick(others);
            float rot = r.uniform(0, 360), k = u * r.uniform(0.9, 1.2);
            int kind = r.below(5);
            M m0 = sway(c, c.mr.uniform(10, 22), 4, 8), m1 = bob(c, 0, k * 0.02, 5, 9);
            if (kind == 0) {
                List<float[]> pts = new ArrayList<>();
                pts.add(new float[]{x - 0.07f * k, y + 0.1f * k});
                pts.add(new float[]{x - 0.07f * k, y - 0.03f * k});
                pts.add(new float[]{x, y - 0.1f * k});
                pts.add(new float[]{x + 0.07f * k, y - 0.03f * k});
                pts.add(new float[]{x + 0.07f * k, y + 0.05f * k});
                Path dots = circ(pts.get(0)[0], pts.get(0)[1], k * 0.028f);
                dots.addPath(circ(pts.get(4)[0], pts.get(4)[1], k * 0.028f));
                c.node(new Item[]{line(smooth(pts, false), col, k * 0.04f), fill(dots, other)}, rot, x, y, m0, m1);
            } else if (kind == 1) {
                List<float[]> pts = new ArrayList<>();
                pts.add(new float[]{x - 0.08f * k, y + 0.02f * k});
                pts.add(new float[]{x, y - 0.025f * k});
                pts.add(new float[]{x + 0.08f * k, y - 0.01f * k});
                c.node(new Item[]{line(smooth(pts, false), col, k * 0.07f), fill(circ(x + 0.07f * k, y - 0.01f * k, k * 0.018f), other)}, rot, x, y, m0, m1);
            } else if (kind == 2) {
                c.node(new Item[]{fill(rrect(x - 0.0375f * k, y - 0.14f * k, 0.075f * k, 0.28f * k, 0.0375f * k), col),
                        fill(rrect(x + 0.04f * k, y - 0.14f * k, 0.024f * k, 0.28f * k, 0.012f * k), other)}, rot, x, y, m0, m1);
            } else if (kind == 3) {
                c.circle(x, y, k * r.uniform(0.025, 0.045), col, breathe(c, 0.25, 2.5, 4), m1);
            } else {
                final float RR = k * 0.1f;
                c.node(new Item[]{fill(polar(x, y, t -> RR * (1 + 0.1f * (float) Math.cos(6 * t)), 72, (float) Math.toRadians(rot)), col)}, 0, x, y,
                        spin(c, 10, 25), m1);
            }
        }
    }

    private static void pair(C c, Rng r) {
        float ax = c.w * r.uniform(0.18, 0.32), ay = c.h * r.uniform(0.68, 0.92), bx = c.w * r.uniform(0.66, 0.8), by = c.h * r.uniform(0.08, 0.32);
        int ca, cb;
        if (c.fg.length > 1) {
            List<Integer> s = r.sample(c.fgList(), 2);
            ca = s.get(0);
            cb = s.get(1);
        } else {
            ca = c.fg[0];
            cb = c.bg;
        }
        float ang = (float) Math.toDegrees(Math.atan2(by - ay, bx - ax)), dist = (float) Math.hypot(bx - ax, by - ay);
        List<Integer> cands = new ArrayList<>();
        for (int k : c.fg) if (k != ca && k != cb) cands.add(k);
        int cc = r.pick(cands.isEmpty() ? java.util.Collections.singletonList(c.bg) : cands);
        float L = dist * r.uniform(0.5, 0.65), T = c.u * r.uniform(0.2, 0.28), mx = (ax + bx) / 2, my = (ay + by) / 2;
        int way = c.mr.flag() ? 1 : -1;
        float[][] ends = {{ax, ay, ca, r.ci(9, 12)}, {bx, by, cb, r.ci(7, 9, 12)}};
        for (int idx = 0; idx < 2; idx++) {
            float x = ends[idx][0], y = ends[idx][1];
            int col = (int) ends[idx][2], n = (int) ends[idx][3];
            float R = c.u * r.uniform(0.42, 0.55);
            final float RR = R;
            final int nn = n;
            c.node(new Item[]{fill(polar(x, y, t -> RR * (1 + 0.07f * (float) Math.cos(nn * t)), 160, r.uniform(0, 6)), col)}, 0, x, y,
                    spin(c, 2, 4, idx == 0 ? way : -way), breathe(c, 0.02, 9, 12));
        }
        c.node(new Item[]{fill(rrect(mx - L / 2, my - T / 2, L, T, T / 2), cc)}, ang, mx, my);
        float k = r.uniform(0.25, 0.45), beadR = c.u * r.uniform(0.11, 0.15);
        float ex = ax + (bx - ax) * (0.5f + k * 0.5f), ey = ay + (by - ay) * (0.5f + k * 0.5f), A = Math.max(1f, L / 2 - T / 2);
        float off = (float) Math.hypot(ex - mx, ey - my), ph = (float) Math.asin(Math.max(-1, Math.min(1, off / A))), ux = (bx - ax) / dist, uy = (by - ay) / dist;
        List<Integer> beads = new ArrayList<>();
        for (int q : c.all()) if (q != cc && q != cb) beads.add(q);
        M m = new M();
        m.k = BOB;
        m.dx = ux * A;
        m.dy = uy * A;
        m.p = c.mr.uniform(9, 13);
        m.ph = ph;
        c.circle(ex, ey, beadR, r.pick(beads.isEmpty() ? java.util.Collections.singletonList(c.bg) : beads), m);
    }

    /** A scene of one style, in a w x h canvas. seed picks the composition, scale (0.3 to 3) the size of the pattern. */
    static Scene build(String style, int[] palette, long seed, float w, float h, float scale) {
        C c = new C(w, h, palette, scale);
        c.mr = new Rng(seed * 31 + style.hashCode() + 7);
        Rng r = new Rng(seed ^ ((long) style.hashCode() << 20));
        switch (style) {
            case "bauhaus": bauhaus(c, r); break;
            case "arches": arches(c, r); break;
            case "waves": waves(c, r); break;
            case "hills": hills(c, r); break;
            case "pebbles": pebbles(c, r); break;
            case "ribbons": ribbons(c, r); break;
            case "rainbow": rainbow(c, r); break;
            case "dots": dots(c, r); break;
            case "cosmos": cosmos(c, r); break;
            case "truchet": truchet(c, r); break;
            case "memphis": memphis(c, r); break;
            case "sunset": sunset(c, r); break;
            case "peaks": peaks(c, r); break;
            case "flowers": flowers(c, r); break;
            case "rings": rings(c, r); break;
            case "stripes": stripes(c, r); break;
            case "bands": bands(c, r); break;
            case "garden": garden(c, r); break;
            case "objects": objects(c, r); break;
            case "pair": pair(c, r); break;
            default: shapes(c, r); break;
        }
        return c.scene;
    }

    // ---- player ------------------------------------------------------------------------------------------

    private static float wobble(M m, float t, float amp) {
        float lead = (((m.ph % TAU) + TAU) % TAU) / TAU * m.p, tt = t - lead;
        return tt <= 0 ? 0 : amp * (float) Math.sin(TAU * tt / m.p);
    }

    private static float outBack(float x) {
        float s = 2.2f;
        x -= 1f;
        return x * x * ((s + 1) * x + s) + 1f;
    }

    /** One frame of the scene at time t seconds, cover-fitted into a vw x vh canvas. */
    static void draw(Canvas cv, Scene s, float t, float vw, float vh, Paint p) {
        float k = Math.max(vw / s.w, vh / s.h);
        cv.save();
        cv.translate((vw - s.w * k) / 2, (vh - s.h * k) / 2);
        cv.scale(k, k);
        p.setStyle(Paint.Style.FILL);
        p.setColor(s.bg);
        cv.drawRect(0, 0, s.w, s.h, p);
        drawNodes(cv, s.nodes, t, p);
        if (s.comets) drawComet(cv, s, t, p);
        cv.restore();
    }

    private static void drawComet(Canvas cv, Scene s, float t, Paint p) {
        int n = (int) Math.floor(t / s.cometEvery);
        float local = t - n * s.cometEvery, life = 1.1f, q = Math.min(1f, local / life);
        if (local >= life || rnd(n, 3) >= 0.75f) return;
        float x = s.w * (0.15f + 0.7f * rnd(n, 1)) + q * s.w * 0.22f, y = s.h * (0.05f + 0.4f * rnd(n, 2)) + q * s.w * 0.22f * 0.45f;
        cv.save();
        cv.translate(x, y);
        cv.rotate(24);
        p.setStyle(Paint.Style.FILL);
        p.setColor(s.cometColor);
        p.setAlpha(Math.round(255 * (float) Math.sin(Math.PI * q)));
        float cw = s.h * 0.12f, ch = s.h * 0.006f;
        cv.drawRoundRect(-cw, -ch / 2, 0, ch / 2, ch / 2, ch / 2, p);
        cv.restore();
    }

    private static float rnd(int n, int i) {
        double v = Math.sin((n + 1) * 12.9898 + i * 78.233) * 43758.5453;
        return (float) (v - Math.floor(v));
    }

    private static void drawNodes(Canvas cv, List<N> nodes, float t, Paint p) {
        for (int i = 0; i < nodes.size(); i++) {
            N n = nodes.get(i);
            if (n.group != null) {
                cv.save();
                for (float[] g : n.group) {
                    if (g[0] == 0) cv.translate(g[1], g[2]);
                    else if (g[0] == 1) cv.scale(g[1], g[1]);
                    else cv.rotate(g[1], g[2], g[3]);
                }
                drawNodes(cv, n.kids, t, p);
                cv.restore();
                continue;
            }
            float ox = n.dot ? n.x : n.ox, oy = n.dot ? n.y : n.oy;
            float angle = n.r, tx = 0, ty = 0, grow = 1, widen = 1, alpha = 1;
            boolean hidden = false;
            for (M m : n.m) {
                switch (m.k) {
                    case SPIN: angle += (t * m.v) % 360f; break;
                    case SWAY: angle += wobble(m, t, m.a); break;
                    case BOB: tx += wobble(m, t, m.dx); ty += wobble(m, t, m.dy); break;
                    case BREATHE: grow *= 1 + wobble(m, t, m.a); break;
                    case STRETCH: widen *= 1 + wobble(m, t, m.a); break;
                    case TWINKLE: {
                        float ph = (float) Math.sin(Math.PI * t / m.p);
                        alpha = 1 - m.a * ph * ph;
                        break;
                    }
                    case FLIP: {
                        float tt = t - m.ph;
                        if (tt > 0) {
                            int cnt = (int) Math.floor(tt / m.e);
                            float local = tt - cnt * m.e, f = local < 0.62f ? outBack(local / 0.62f) : 1f;
                            angle += m.st * (cnt + f);
                        }
                        break;
                    }
                    case SCROLL: {
                        float v = Math.abs(m.v), x = (t * v) % m.period;
                        tx += m.v > 0 ? -x : x;
                        break;
                    }
                    case DRIFT: {
                        float far = m.w + 40 - m.x0, near = -(m.x0 + m.bw + 40), v = Math.abs(m.v), span = far - near;
                        if (v < 0.001f) break;
                        if (m.v > 0) {
                            float first = far / v;
                            tx += t < first ? v * t : near + ((t - first) * v) % span;
                        } else {
                            float first = -near / v;
                            tx += t < first ? -v * t : far - ((t - first) * v) % span;
                        }
                        break;
                    }
                    case ORBIT: {
                        float a = m.ph + (m.p > 0 ? 1 : -1) * TAU * t / Math.abs(m.p);
                        tx += m.rx * ((float) Math.cos(a) - (float) Math.cos(m.ph));
                        ty += m.ry * ((float) Math.sin(a) - (float) Math.sin(m.ph));
                        break;
                    }
                    case SWELL: grow *= m.s0 + (1 - m.s0) * ((t % m.p) / m.p); break;
                    case RIPPLE: {
                        float ph = (((m.d * m.n) % TAU) + TAU) % TAU;
                        float sc = 0.5f + 0.5f * (float) Math.cos(ph + m.w * t);
                        if (sc < m.min) hidden = true;
                        grow *= sc;
                        break;
                    }
                    default: break;
                }
            }
            if (hidden || grow <= 0.002f) continue;
            cv.save();
            if (tx != 0 || ty != 0) cv.translate(tx, ty);
            if (angle != 0) cv.rotate(angle, ox, oy);
            if (grow != 1 || widen != 1) {
                cv.translate(ox, oy);
                cv.scale(widen * grow, grow);
                cv.translate(-ox, -oy);
            }
            if (n.dot) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(n.color);
                p.setAlpha(Math.round(255 * alpha));
                cv.drawCircle(n.x, n.y, n.rad, p);
            } else {
                for (Item it : n.items) {
                    p.setColor(it.color);
                    p.setAlpha(it.alpha);
                    if (it.stroke) {
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(it.sw);
                        p.setStrokeCap(it.butt ? Paint.Cap.BUTT : Paint.Cap.ROUND);
                        p.setStrokeJoin(Paint.Join.ROUND);
                    } else {
                        p.setStyle(Paint.Style.FILL);
                    }
                    cv.drawPath(it.path, p);
                }
            }
            cv.restore();
        }
        p.setAlpha(255);
    }
}
