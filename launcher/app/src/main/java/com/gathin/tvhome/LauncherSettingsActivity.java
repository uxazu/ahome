package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Launcher settings in Material 3 Expressive: a navigation rail of sections on the left (full-shape pills, the chosen
 * one in secondary-container), the section's rows on the right (large-increased corners on surface-container, a
 * primary-container pill with the 3dp primary ring on focus). Every choice is saved at once, and Home rebuilds when
 * it next resumes.
 */
public final class LauncherSettingsActivity extends Activity {
    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private static final String[] ACCENT_NAMES = {"Green", "Purple", "Blue", "Teal", "Red", "Orange", "Pink", "Yellow"};
    private static final String[] ACCENT_HEX = {"#4CAF50", "#6750A4", "#2196F3", "#009688", "#E53935", "#FB8C00", "#E91E63", "#FDD835"};

    private SharedPreferences choices;
    private Config config;
    private float density;
    private LinearLayout rail, content;
    private int section;
    private final List<TextView> railItems = new ArrayList<>();

    private String t(int id) {
        return getString(id);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Expressive.init(this);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        config = Config.load(this);
        density = getResources().getDisplayMetrics().density;
        int pad = Math.round(56 * density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Expressive.SURFACE);
        root.setPadding(pad, Math.round(40 * density), pad, Math.round(24 * density));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(t(R.string.ls_settings), 36, 44, Expressive.ON_SURFACE);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.bottomMargin = Math.round(28 * density);
        left.addView(title, tp);
        rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        left.addView(rail);
        root.addView(left, new LinearLayout.LayoutParams(Math.round(300 * density), ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        sp.setMarginStart(Math.round(32 * density));
        root.addView(scroller, sp);
        setContentView(root);

        String[] names = {t(R.string.ls_appearance), t(R.string.ls_wallpaper), t(R.string.ls_home), t(R.string.ls_system)};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            TextView item = text(names[i], 20, 28, Expressive.ON_SURFACE_VARIANT);
            item.setFocusable(true);
            item.setClickable(true);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(Math.round(24 * density), 0, Math.round(24 * density), 0);
            item.setOnFocusChangeListener((v, focused) -> {
                if (focused) selectSection(index);
                paintRail();
            });
            item.setOnClickListener(v -> {
                if (content.getChildCount() > 0) content.getChildAt(0).requestFocus();
            });
            railItems.add(item);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(60 * density));
            lp.bottomMargin = Math.round(8 * density);
            rail.addView(item, lp);
        }
        selectSection(0);
        railItems.get(0).requestFocus();
        Fonts.apply(this, root);
    }

    private TextView text(String s, float sp, float lineSp, int color) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setIncludeFontPadding(false);
        Expressive.lineHeight(v, Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, lineSp, getResources().getDisplayMetrics())));
        v.setTextColor(color);
        return v;
    }

    private void paintRail() {
        for (int i = 0; i < railItems.size(); i++) {
            TextView item = railItems.get(i);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(100 * density);
            boolean on = i == section;
            bg.setColor(on ? Expressive.SECONDARY_CONTAINER : 0);
            if (item.isFocused()) bg.setStroke(Math.round(3 * density), Expressive.PRIMARY);
            item.setBackground(bg);
            item.setTextColor(on ? Expressive.ON_SECONDARY_CONTAINER : Expressive.ON_SURFACE_VARIANT);
        }
    }

    private void selectSection(int index) {
        if (index == section && content.getChildCount() > 0) {
            paintRail();
            return;
        }
        section = index;
        rebuild();
        paintRail();
    }

    /** Fills the right side with the section's rows, keeping the focused row's place. */
    private void rebuild() {
        int focusedAt = -1;
        for (int i = 0; i < content.getChildCount(); i++) if (content.getChildAt(i).isFocused()) focusedAt = i;
        content.removeAllViews();
        config = Config.load(this);
        switch (section) {
            case 0: appearance(); break;
            case 1: wallpaper(); break;
            case 2: home(); break;
            default: system(); break;
        }
        Fonts.apply(this, content);
        if (focusedAt >= 0 && focusedAt < content.getChildCount()) content.getChildAt(focusedAt).requestFocus();
    }

    private void row(String title, Supplier<String> value, Runnable action) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setPadding(Math.round(28 * density), 0, Math.round(28 * density), 0);
        final TextView head = text(title, 20, 28, Expressive.ON_SURFACE);
        r.addView(head);
        String v = value == null ? null : value.get();
        final TextView sub = v == null || v.isEmpty() ? null : text(v, 16, 22, Expressive.ON_SURFACE_VARIANT);
        if (sub != null) r.addView(sub);
        GradientDrawable rest = new GradientDrawable();
        rest.setColor(Expressive.SURFACE_CONTAINER);
        rest.setCornerRadius(20 * density);
        GradientDrawable focus = new GradientDrawable();
        focus.setColor(Expressive.PRIMARY_CONTAINER);
        focus.setCornerRadius(20 * density);
        focus.setStroke(Math.round(3 * density), Expressive.PRIMARY);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focus);
        states.addState(new int[]{}, rest);
        r.setBackground(states);
        r.setOnFocusChangeListener((view, focused) -> {
            head.setTextColor(focused ? Expressive.ON_PRIMARY_CONTAINER : Expressive.ON_SURFACE);
            if (sub != null) sub.setTextColor(focused ? Expressive.ON_PRIMARY_CONTAINER : Expressive.ON_SURFACE_VARIANT);
        });
        r.setOnClickListener(x -> action.run());
        r.setOnKeyListener((vw, code, ev) -> {
            if (ev.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_LEFT) {
                railItems.get(section).requestFocus();
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round((sub == null ? 64 : 80) * density));
        lp.bottomMargin = Math.round(8 * density);
        content.addView(r, lp);
    }

    private String onOff(boolean on) {
        return on ? t(R.string.ls_on) : t(R.string.ls_off);
    }

    private boolean flag(String key, boolean def) {
        return "true".equals(Config.extra(this, key, def ? "true" : "false"));
    }

    private void toggle(String key, boolean def) {
        UserOptions.set(this, key, !flag(key, def));
        setResult(RESULT_OK);
        rebuild();
    }

    /** A list of values for a theme key (any key a theme can set); the choice is saved over the theme's. */
    private void pickKey(String title, String key, String[] labels, String[] values, String def) {
        String cur = Config.extra(this, key, def);
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(cur)) checked = i;
        pick(title, labels, checked, i -> {
            UserOptions.set(this, key, values[i]);
            setResult(RESULT_OK);
            rebuild();
        });
    }

    private void pick(String title, String[] labels, int checked, IntConsumer done) {
        new Fonts.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    done.accept(which);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void appearance() {
        row(t(R.string.ls_theme), () -> t(R.string.ls_launcher_theme_library), this::showThemePicker);
        row(t(R.string.ls_accent_colour), () -> Config.extra(this, "m3_seed", "#4CAF50"), this::showAccentPicker);
        row("Material 3 Expressive", () -> onOff(flag("m3_expressive", false)), () -> toggle("m3_expressive", false));
        row(t(R.string.ls_animations), () -> onOff(config.animations), () -> {
            choices.edit().putBoolean("animations", !config.animations).apply();
            changed();
            rebuild();
        });
        row(t(R.string.ls_advanced_effects), () -> onOff(config.animations && config.advancedEffects), () -> {
            choices.edit().putBoolean("advanced_effects", !config.advancedEffects).apply();
            changed();
            rebuild();
        });
        String[] glass = {"glass", "rim", "halo", "liquid", "tonal"};
        row(t(R.string.ls_highlight_style), () -> Config.extra(this, "glass_style", "tonal"), () ->
                pickKey(t(R.string.ls_highlight_style), "glass_style", glass, glass, "tonal"));
        row(t(R.string.ls_all_theme_options), () -> t(R.string.ls_every_theme_key), this::showCustomize);
    }

    private void wallpaper() {
        row(t(R.string.ls_animated_wallpaper), () -> onOff(flag("animated_shapes", false)), () -> toggle("animated_shapes", false));
        String[] styles = new String[Wally.STYLES.length + 1];
        styles[0] = "random";
        System.arraycopy(Wally.STYLES, 0, styles, 1, Wally.STYLES.length);
        row(t(R.string.ls_style), () -> Config.extra(this, "wall_style", "shapes"), () -> showLook(true, styles));
        String[] pals = new String[Wallpaper.NAMES.length + 2];
        pals[0] = "random";
        pals[1] = "m3";
        System.arraycopy(Wallpaper.NAMES, 0, pals, 2, Wallpaper.NAMES.length);
        row(t(R.string.ls_palette), () -> Wallpaper.label(Config.extra(this, "wall_palette", "m3")), () -> showLook(false, pals));
        row(t(R.string.ls_dark_version_of_the_palette), () -> onOff(flag("wall_dark", false)), () -> toggle("wall_dark", false));
        String[] sizes = {"0.6", "0.8", "1", "1.4", "2"};
        row(t(R.string.ls_pattern_size), () -> "x" + Config.extra(this, "wall_scale", "1"), () ->
                pickKey(t(R.string.ls_pattern_size), "wall_scale", sizes, sizes, "1"));
        String[] dims = {"0", "15", "30", "50", "70"};
        row(t(R.string.ls_dimming), () -> Config.extra(this, "wall_dim", t(R.string.ls_per_palette)), () ->
                pickKey(t(R.string.ls_dimming), "wall_dim", dims, dims, "0"));
        row(t(R.string.ls_wallpaper_blur), () -> onOff(flag("wall_blur", true)), () -> toggle("wall_blur", true));
        row(t(R.string.ls_wallpaper_moves_behind_the_apps), () -> onOff(flag("wall_anim_apps", true)), () -> toggle("wall_anim_apps", true));
        String[] quality = {"0.33", "0.5", "0.75", "1"};
        String[] qualityNames = {t(R.string.ls_low), t(R.string.ls_medium), t(R.string.ls_high), t(R.string.ls_maximum)};
        row(t(R.string.ls_wallpaper_resolution), () -> {
            String q = Config.extra(this, "wall_quality", "0.5");
            for (int i = 0; i < quality.length; i++) if (quality[i].equals(q)) return qualityNames[i];
            return q;
        }, () -> pickKey(t(R.string.ls_wallpaper_resolution), "wall_quality", qualityNames, quality, "0.5"));
        row(t(R.string.ls_your_own_picture), () -> t(R.string.ls_pictures_from_the_theme_s_wallpaper_fo), this::showWallpaperPicker);
    }

    /** A grid of live previews, the name under each: the generator's own picture for every style or palette. */
    private void showLook(boolean styles, String[] values) {
        String key = styles ? "wall_style" : "wall_palette";
        String cur = Config.extra(this, key, styles ? "shapes" : "m3");
        String style = Config.extra(this, "wall_style", "shapes");
        if ("random".equals(style)) style = "shapes";
        String palName = Config.extra(this, "wall_palette", "m3");
        String variant = flag("wall_dark", false) ? "dark" : "";
        final String fixedStyle = style;
        final String fixedPal = palName;
        int cw = Math.round(150 * density), ch = Math.round(200 * density);
        ScrollView sv = new ScrollView(this);
        android.widget.GridLayout grid = new android.widget.GridLayout(this);
        grid.setColumnCount(5);
        grid.setPadding(Math.round(12 * density), Math.round(8 * density), Math.round(12 * density), Math.round(8 * density));
        sv.addView(grid);
        final android.app.AlertDialog[] dialog = new android.app.AlertDialog[1];
        final List<android.widget.ImageView> thumbs = new ArrayList<>();
        View first = null;
        for (int i = 0; i < values.length; i++) {
            final String v = values[i];
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setFocusable(true);
            cell.setClickable(true);
            cell.setPadding(Math.round(8 * density), Math.round(8 * density), Math.round(8 * density), Math.round(8 * density));
            GradientDrawable rest = new GradientDrawable();
            rest.setColor(v.equals(cur) ? Expressive.SECONDARY_CONTAINER : 0);
            rest.setCornerRadius(20 * density);
            GradientDrawable on = new GradientDrawable();
            on.setColor(Expressive.PRIMARY_CONTAINER);
            on.setCornerRadius(20 * density);
            on.setStroke(Math.round(3 * density), Expressive.PRIMARY);
            StateListDrawable st = new StateListDrawable();
            st.addState(new int[]{android.R.attr.state_focused}, on);
            st.addState(new int[]{}, rest);
            cell.setBackground(st);
            android.widget.ImageView img = new android.widget.ImageView(this);
            img.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
            GradientDrawable ph = new GradientDrawable();
            ph.setColor(Expressive.SURFACE_CONTAINER_HIGHEST);
            ph.setCornerRadius(22 * density);
            img.setBackground(ph);
            img.setClipToOutline(true);
            img.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 22 * density);
                }
            });
            android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
            frame.addView(img, new android.widget.FrameLayout.LayoutParams(cw, ch));
            if (v.equals(cur)) {
                // The chosen card carries a check in a translucent disc, as on a phone's picker.
                TextView check = text("\u2713", 26, 30, 0xFFFFFFFF);
                check.setGravity(Gravity.CENTER);
                GradientDrawable disc = new GradientDrawable();
                disc.setShape(GradientDrawable.OVAL);
                disc.setColor(0x66000000);
                check.setBackground(disc);
                int d = Math.round(48 * density);
                android.widget.FrameLayout.LayoutParams cp = new android.widget.FrameLayout.LayoutParams(d, d, Gravity.CENTER);
                frame.addView(check, cp);
            }
            cell.addView(frame, new LinearLayout.LayoutParams(cw, ch));
            TextView name = text("random".equals(v) ? t(R.string.ls_random) : "m3".equals(v) ? t(R.string.ls_theme_colours) : styles ? v : Wallpaper.label(v), 16, 22, Expressive.ON_SURFACE);
            name.setGravity(Gravity.CENTER);
            name.setSingleLine(true);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(cw, ViewGroup.LayoutParams.WRAP_CONTENT);
            np.topMargin = Math.round(6 * density);
            cell.addView(name, np);
            cell.setOnClickListener(x -> {
                UserOptions.set(this, key, v);
                setResult(RESULT_OK);
                if (dialog[0] != null) dialog[0].dismiss();
                rebuild();
            });
            grid.addView(cell);
            thumbs.add(img);
            if (first == null || v.equals(cur)) first = cell;
        }
        final View focusCell = first;
        // Pictures are drawn one by one off the UI thread: building and painting a scene costs a few milliseconds each.
        final String[] vals = values;
        new Thread(() -> {
            for (int i = 0; i < vals.length; i++) {
                String v = vals[i];
                if ("random".equals(v)) continue;
                String st = styles ? v : fixedStyle;
                String pn = styles ? fixedPal : v;
                int[] colors = "m3".equals(pn) || "random".equals(pn) ? null : Wallpaper.palette(pn, variant);
                if (colors == null) colors = Wallpaper.theme();
                android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(240, 320, android.graphics.Bitmap.Config.ARGB_8888);
                Wally.draw(new android.graphics.Canvas(bmp), Wally.build(st, colors, 11, 2880f, 3840f, 1f), 3f, 240f, 320f,
                        new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG));
                final android.widget.ImageView target = thumbs.get(i);
                target.post(() -> target.setImageBitmap(bmp));
            }
        }, "previews").start();
        dialog[0] = new Fonts.Builder(this)
                .setTitle(styles ? t(R.string.ls_wallpaper_style) : t(R.string.ls_wallpaper_palette))
                .setView(sv)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        if (dialog[0].getWindow() != null) {
            dialog[0].getWindow().setLayout(Math.round(getResources().getDisplayMetrics().widthPixels * 0.92f),
                    Math.round(getResources().getDisplayMetrics().heightPixels * 0.9f));
        }
        if (focusCell != null) focusCell.requestFocus();
    }

    private void home() {
        row(t(R.string.ls_dock_apps), () -> String.valueOf(config.dockApps.size()), this::showDockApps);
        row(t(R.string.ls_dock_only_on_home), () -> onOff(flag("home_dock_only", false)), () -> toggle("home_dock_only", false));
        row(t(R.string.ls_app_search), () -> onOff(flag("app_search", false)), () -> toggle("app_search", false));

        String[] cols = {"4", "5", "6", "7"};
        row(t(R.string.ls_columns), () -> String.valueOf(config.columns), () ->
                pick(t(R.string.ls_columns), cols, config.columns - 4, i -> {
                    choices.edit().putInt("columns", i + 4).apply();
                    changed();
                    rebuild();
                }));
        String[] rows = {"0", "1", "2", "3"};
        row(t(R.string.ls_rows_below_the_dock), () -> String.valueOf(config.rowsBelowDock), () ->
                pick(t(R.string.ls_rows_below_the_dock), rows, config.rowsBelowDock, i -> {
                    choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, i).apply();
                    changed();
                    rebuild();
                }));
        String[] opac = {"100%", "70%", "50%", "30%"};
        row(t(R.string.ls_dock_opacity), () -> config.dockOpacity + "%", () -> {
            int checked = 0;
            for (int i = 0; i < DOCK_OPACITIES.length; i++) if (DOCK_OPACITIES[i] == config.dockOpacity) checked = i;
            pick(t(R.string.ls_dock_opacity), opac, checked, i -> {
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, DOCK_OPACITIES[i]).apply();
                changed();
                rebuild();
            });
        });
        String[] clock = {"1.3", "1.6", "1.9", "2.2"};
        row(t(R.string.ls_clock_size), () -> "x" + Config.extra(this, "clock_scale", "1.6"), () ->
                pickKey(t(R.string.ls_clock_size), "clock_scale", clock, clock, "1.6"));
        row(t(R.string.ls_weather_town), () -> Config.extra(this, "weather_city", t(R.string.ls_off)), this::showWeatherCity);
        String[] showLabels = {t(R.string.ls_town_and_weather), t(R.string.ls_temperature_only), t(R.string.ls_hidden)};
        String[] showVals = {"full", "temp", "off"};
        row(t(R.string.ls_weather_line), () -> {
            String v = Config.extra(this, "weather_show", "full");
            return "temp".equals(v) ? showLabels[1] : "off".equals(v) ? showLabels[2] : showLabels[0];
        }, () -> pickKey(t(R.string.ls_weather_line), "weather_show", showLabels, showVals, "full"));
    }

    private static final String RELEASES_API = "https://api.github.com/repos/uxazu/ahome/releases/latest";

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    /** Numeric compare of dotted versions, ignoring a leading "v" and any suffix. */
    private static boolean isNewer(String latest, String current) {
        String[] a = latest.replaceFirst("^[vV]", "").split("[.\\-]");
        String[] b = current.replaceFirst("^[vV]", "").split("[.\\-]");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? digits(a[i]) : 0, y = i < b.length ? digits(b[i]) : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    private static int digits(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D.*$", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** One request, only when the person presses the row: nothing runs in the background. */
    private void checkUpdate() {
        Toast.makeText(this, t(R.string.ls_update_checking), Toast.LENGTH_SHORT).show();
        final String current = versionName();
        new Thread(() -> {
            String tag = null, url = null;
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(RELEASES_API).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                if (c.getResponseCode() == 200) {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    java.io.InputStream in = c.getInputStream();
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    org.json.JSONObject j = new org.json.JSONObject(out.toString("UTF-8"));
                    tag = j.optString("tag_name", null);
                    url = j.optString("html_url", null);
                }
                c.disconnect();
            } catch (Exception ignored) {
            }
            final String fTag = tag, fUrl = url;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (fTag == null || fTag.isEmpty()) {
                    new android.app.AlertDialog.Builder(this).setMessage(t(R.string.ls_update_failed))
                            .setPositiveButton(android.R.string.ok, null).show();
                } else if (isNewer(fTag, current)) {
                    new android.app.AlertDialog.Builder(this)
                            .setMessage(String.format(t(R.string.ls_update_new), fTag, current) + (fUrl == null ? "" : "\n\n" + fUrl))
                            .setPositiveButton(t(R.string.ls_update_open), (d, w) -> {
                                try {
                                    startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(fUrl)));
                                } catch (Exception e) {
                                    Toast.makeText(this, fUrl, Toast.LENGTH_LONG).show();
                                }
                            }).setNegativeButton(android.R.string.cancel, null).show();
                } else {
                    new android.app.AlertDialog.Builder(this).setMessage(String.format(t(R.string.ls_update_none), current))
                            .setPositiveButton(android.R.string.ok, null).show();
                }
            });
        }, "update-check").start();
    }

    private void system() {
        row(t(R.string.ls_language), () -> {
            String cur = Lang.code(this);
            for (int i = 0; i < Lang.CODES.length; i++) if (Lang.CODES[i].equals(cur)) return i == 0 ? t(R.string.ls_system) : Lang.NAMES[i];
            return cur;
        }, () -> {
            String[] names = Lang.NAMES.clone();
            names[0] = t(R.string.ls_system);
            String cur = Lang.code(this);
            int checked = 0;
            for (int i = 0; i < Lang.CODES.length; i++) if (Lang.CODES[i].equals(cur)) checked = i;
            pick(t(R.string.ls_language), names, checked, i -> {
                UserOptions.set(this, "ui_lang", Lang.CODES[i]);
                setResult(RESULT_OK);
                recreate();
            });
        });
        row(t(R.string.ls_check_update), this::versionName, this::checkUpdate);
        if (config.launcherSettingsButton) {
            row(t(R.string.ls_reorder_apps), null, () -> {
                setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
                finish();
            });
        }
        if (config.openOnStartChoice) {
            row(t(R.string.ls_open_on_start), this::openOnStartLabel, this::showOpenOnStartPicker);
        }
        row(t(R.string.ls_follow_the_theme_again), null, () -> {
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove(OpenOnStart.KEY).commit();
            UserOptions.clear(this);
            changed();
            Toast.makeText(this, t(R.string.ls_done), Toast.LENGTH_SHORT).show();
            rebuild();
        });
        row(t(R.string.ls_default_home), null, () -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        row(t(R.string.ls_tv_settings), null, () -> open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
    }

    /** Material 3 seed colour: every container, surface and ring of the Expressive theme is generated from it. */
    private void showAccentPicker() {
        String[] rows = new String[ACCENT_NAMES.length + 2];
        System.arraycopy(ACCENT_NAMES, 0, rows, 0, ACCENT_NAMES.length);
        rows[ACCENT_NAMES.length] = getString(R.string.accent_custom);
        rows[ACCENT_NAMES.length + 1] = getString(R.string.accent_theme);
        new Fonts.Builder(this)
                .setTitle(R.string.accent_label)
                .setItems(rows, (dialog, which) -> {
                    if (which < ACCENT_HEX.length) {
                        UserOptions.set(this, "m3_seed", ACCENT_HEX[which]);
                        setResult(RESULT_OK);
                        finish();
                    } else if (which == ACCENT_HEX.length) {
                        showCustomAccent();
                    } else {
                        UserOptions.set(this, "m3_seed", null);
                        setResult(RESULT_OK);
                        finish();
                    }
                })
                .show();
    }

    /** The weather line's place: a town name, "auto" for the TV's own town, "off" to hide it, empty for the theme's. */
    private void showWeatherCity() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(getString(R.string.weather_hint));
        input.setSingleLine(true);
        new Fonts.Builder(this)
                .setTitle(R.string.weather_label)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String city = input.getText().toString().trim();
                    UserOptions.set(this, "weather_city", city.isEmpty() ? null : city);
                    setResult(RESULT_OK);
                    rebuild();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showCustomAccent() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("#RRGGBB");
        input.setSingleLine(true);
        new Fonts.Builder(this)
                .setTitle(R.string.accent_custom)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String hex = input.getText().toString().trim();
                    if (!hex.startsWith("#")) hex = "#" + hex;
                    try {
                        android.graphics.Color.parseColor(hex);
                        UserOptions.set(this, "m3_seed", hex);
                        setResult(RESULT_OK);
                        finish();
                    } catch (IllegalArgumentException e) {
                        Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showCustomize() {
        UserOptions.Option[] all = UserOptions.OPTIONS;
        org.json.JSONObject mine = UserOptions.load(this);
        String[] rows = new String[all.length + 1];
        for (int i = 0; i < all.length; i++) rows[i] = all[i].label + ": " + UserOptions.state(mine, all[i]);
        rows[all.length] = getString(R.string.customize_reset);
        new Fonts.Builder(this)
                .setTitle(R.string.customize_label)
                .setItems(rows, (dialog, which) -> {
                    if (which == all.length) UserOptions.clear(this); else UserOptions.step(this, all[which]);
                    setResult(RESULT_OK);
                    showCustomize();
                })
                .setNegativeButton(android.R.string.ok, null)
                .show();
    }

    private void showDockApps() {
        PackageManager pm = getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(probe, 0);
        if (found.isEmpty()) found = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        List<String> pkgs = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (ResolveInfo r : found) {
            String p = r.activityInfo.packageName;
            if (p.equals(getPackageName()) || pkgs.contains(p)) continue;
            pkgs.add(p);
            labels.add(String.valueOf(r.loadLabel(pm)));
        }
        List<String> dock = new ArrayList<>(Config.load(this).dockApps);
        boolean[] on = new boolean[pkgs.size()];
        for (int i = 0; i < on.length; i++) on[i] = dock.contains(pkgs.get(i));
        new Fonts.Builder(this)
                .setTitle(R.string.dock_apps_label)
                .setMultiChoiceItems(labels.toArray(new String[0]), on, (d, i, checked) -> on[i] = checked)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    List<String> out = new ArrayList<>();
                    for (String p : dock) { int i = pkgs.indexOf(p); if (i >= 0 && on[i]) out.add(p); }
                    for (int i = 0; i < on.length; i++) if (on[i] && !out.contains(pkgs.get(i))) out.add(pkgs.get(i));
                    UserOptions.setDockApps(this, out.subList(0, Math.min(out.size(), 8)));
                    setResult(RESULT_OK);
                    rebuild();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showThemePicker() {
        File dir = Config.dir(this);
        List<String> names = ThemeLibrary.names(dir);
        if (names.isEmpty()) {
            Toast.makeText(this, R.string.theme_library_empty, Toast.LENGTH_LONG).show();
            return;
        }
        new Fonts.Builder(this)
                .setTitle(R.string.theme_library_label)
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    if (ThemeLibrary.apply(dir, names.get(which))) {
                        changed();
                        Toast.makeText(this, names.get(which), Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size() : indexOf(images, current);
        new Fonts.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, images.get(which).getName()).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    rebuild();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    rebuild();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        String current = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        new Fonts.Builder(this)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    rebuild();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
