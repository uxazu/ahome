package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;

/**
 * An optional typeface for a theme (config.json key "font": a file name next to it, for example Google Sans as a
 * variable font). It is read from the TV's files folder at run time, so it costs the APK nothing. Large text takes
 * the regular weight, everything else a medium one; with no font, or one that fails to load, nothing changes.
 */
final class Fonts {
    private static String loadedKey = "";
    private static Typeface large, small;

    private Fonts() {}

    static Typeface small() {
        return small;
    }

    /** An AlertDialog builder whose dialogs take the theme's font, in titles, buttons and lists alike. */
    static final class Builder extends android.app.AlertDialog.Builder {
        private final Context ctx;

        Builder(Context ctx) {
            super(ctx);
            this.ctx = ctx;
        }

        @Override
        public android.app.AlertDialog show() {
            android.app.AlertDialog d = create();
            d.show();
            watch(ctx, d);
            return d;
        }
    }

    /** Keeps the font on a dialog's views, including list rows that are created as it scrolls. */
    static void watch(Context ctx, android.app.Dialog d) {
        load(ctx);
        if (d.getWindow() == null) return;
        final View decor = d.getWindow().getDecorView();
        final float density = ctx.getResources().getDisplayMetrics().density;
        final float scaled = ctx.getResources().getDisplayMetrics().scaledDensity;
        if (Expressive.on) {
            // M3 dialog: surface-container-high, extra-large (28dp) corners.
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(Expressive.SURFACE_CONTAINER_HIGH);
            bg.setCornerRadius(28 * density);
            d.getWindow().setBackgroundDrawable(new android.graphics.drawable.InsetDrawable(bg, 0));
        }
        final Runnable style = () -> {
            if (small != null) walk(decor, scaled);
            if (Expressive.on) m3(decor, density, ctx.getResources().getIdentifier("alertTitle", "id", "android"));
        };
        style.run();
        decor.getViewTreeObserver().addOnGlobalLayoutListener(style::run);
    }

    /** Dialog parts in M3 roles: title on-surface (Headline Small), text buttons in primary, list rows with a focus pill. */
    private static void m3(View v, float density, int titleId) {
        if (v instanceof android.widget.ListView) {
            android.widget.ListView list = (android.widget.ListView) v;
            if (list.getDivider() != null) list.setDivider(null);
            if (!(list.getSelector() instanceof android.graphics.drawable.GradientDrawable)) {
                android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
                pill.setColor((Expressive.PRIMARY_CONTAINER & 0x00FFFFFF) | 0xCC000000);
                pill.setCornerRadius(100 * density);
                list.setSelector(pill);
            }
        }
        if (v instanceof android.widget.Button) {
            ((TextView) v).setTextColor(Expressive.PRIMARY);
            ((TextView) v).setAllCaps(false);
            v.setBackground(null);
        } else if (v instanceof android.widget.EditText) {
            ((TextView) v).setTextColor(Expressive.ON_SURFACE);
        } else if (v instanceof TextView) {
            TextView t = (TextView) v;
            boolean title = titleId != 0 && v.getId() == titleId;
            t.setTextColor(title ? Expressive.ON_SURFACE : Expressive.ON_SURFACE_VARIANT);
            if (title) t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) m3(g.getChildAt(i), density, titleId);
        }
    }

    private static void load(Context ctx) {
        String name = Config.extra(ctx, "font", "");
        File dir = Config.dir(ctx);
        File file = name.isEmpty() || name.contains("/") || name.contains("\\") || dir == null ? null : new File(dir, name);
        String key = file == null || !file.isFile() ? "" : file.getPath() + "@" + file.lastModified();
        if (key.equals(loadedKey)) return;
        loadedKey = key;
        large = small = null;
        if (key.isEmpty()) return;
        try {
            large = new Typeface.Builder(file).setFontVariationSettings((Expressive.on ? "'wght' 600" : "'wght' 400")).build();
            small = new Typeface.Builder(file).setFontVariationSettings("'wght' 500").build();
        } catch (RuntimeException | OutOfMemoryError e) {
            large = small = null;
        }
    }

    /** Puts the theme's font on every TextView under {@code root}. */
    static void apply(Context ctx, View root) {
        load(ctx);
        if (large == null || small == null || root == null) return;
        walk(root, ctx.getResources().getDisplayMetrics().scaledDensity);
    }

    private static void walk(View v, float scaled) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            Typeface want = t.getTextSize() / scaled >= 36f ? large : small;
            if (t.getTypeface() != want) t.setTypeface(want);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i), scaled);
        }
    }
}
