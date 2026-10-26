package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Choices made in Launcher settings, kept as one JSON object that Config lays over the theme's config.json, so any
 * key a theme can set can be set here too. A key that is absent follows the theme.
 */
final class UserOptions {
    static final String PREF = "user_overrides";

    /** One setting: a switch (no values) or a list of values to step through. */
    static final class Option {
        final String key, label;
        final String[] values;

        Option(String key, String label, String... values) {
            this.key = key;
            this.label = label;
            this.values = values;
        }

        boolean isSwitch() {
            return values.length == 0;
        }
    }

    static final Option[] OPTIONS = {
        new Option("m3_expressive", "Material 3 Expressive"),
        new Option("home_dock_only", "Home: only the dock (swipe down for apps)"),
        new Option("wall_style", "Wallpaper style", wallStyles()),
        new Option("wall_palette", "Wallpaper palette", wallPalettes()),
        new Option("ui_lang", "Interface language", "system", "en", "ru", "es", "de", "fr", "pt", "it", "zh-CN", "ja", "ko", "tr", "pl", "uk"),
        new Option("wall_blur", "Frosted blur of the wallpaper"),
        new Option("wall_anim_apps", "Wallpaper keeps moving behind the apps page"),
        new Option("wall_quality", "Wallpaper resolution", "0.33", "0.5", "0.75", "1"),
        new Option("wall_dark", "Wallpaper: dark version of the palette"),
        new Option("wall_scale", "Wallpaper pattern size", "0.6", "0.8", "1", "1.4", "2"),
        new Option("wall_dim", "Wallpaper dimming", "0", "15", "30", "50", "70"),
        new Option("weather_show", "Weather line", "full", "temp", "off"),
        new Option("clock_scale", "Clock size", "1.3", "1.6", "1.9", "2.2"),
        new Option("app_search", "Search bar under the dock"),
        new Option("animated_shapes", "Animated wallpaper (M3 shapes)"),
        new Option("glass_style", "Glass style", "glass", "rim", "halo", "liquid", "tonal"),
        new Option("focus_overshoot", "Bounce on focus"),
        new Option("advanced_effects", "Advanced effects"),
        new Option("reduced_motion", "Reduced motion"),
        new Option("cinematic_home", "Cinematic top shelf"),
        new Option("app_glow", "Glow in the app's colour"),
        new Option("ambient_glow", "Colour wash behind the dock"),
        new Option("clock_glow", "Clock glow"),
        new Option("dock_glass", "Light glass dock"),
        new Option("dock_band", "Dock band"),
        new Option("dock_even_padding", "Even dock padding"),
        new Option("dock_title", "Dock app title"),
        new Option("dock_row", "Dock as its own row"),
        new Option("single_row", "Single row of apps"),
        new Option("uniform_tiles", "Icon cards instead of banners"),
        new Option("always_show_names", "Always show names"),
        new Option("icon_only_buttons", "Round icon buttons"),
        new Option("icon_only_inputs", "Inputs as an icon"),
        new Option("top_menu", "Single gear menu"),
        new Option("show_network_pill", "Wi-Fi pill"),
        new Option("show_inputs_pill", "Inputs pill"),
        new Option("show_greeting", "Greeting"),
        new Option("header_white", "White date"),
        new Option("date_below_clock", "Date below clock"),
        new Option("lunar_date", "Lunar date"),
        new Option("show_stats", "CPU/RAM line"),
        new Option("wifi_status_color", "Wi-Fi status colour"),
        new Option("app_library", "App library"),
        new Option("tile_menu", "Hold-OK tile menu"),
        new Option("wallpaper", "Wallpaper"),
        new Option("wallpaper_dim", "Wallpaper dimming", "0", "10", "20", "30", "50", "70"),
        new Option("sky_intensity", "Sky backdrop", "0", "30", "60", "100"),
    };

    private static String[] wallStyles() {
        String[] out = new String[Wally.STYLES.length + 1];
        out[0] = "random";
        System.arraycopy(Wally.STYLES, 0, out, 1, Wally.STYLES.length);
        return out;
    }

    private static String[] wallPalettes() {
        String[] out = new String[Wallpaper.NAMES.length + 2];
        out[0] = "random";
        out[1] = "m3";
        System.arraycopy(Wallpaper.NAMES, 0, out, 2, Wallpaper.NAMES.length);
        return out;
    }

    private UserOptions() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }

    static JSONObject load(Context c) {
        try {
            return new JSONObject(prefs(c).getString(PREF, "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private static void save(Context c, JSONObject o) {
        prefs(c).edit().putString(PREF, o.toString())
                .putLong(Config.SETTINGS_REVISION_KEY, prefs(c).getLong(Config.SETTINGS_REVISION_KEY, 0) + 1)
                .commit();
    }

    /** What the option says now: "theme" when it follows the theme, otherwise its value. */
    static String state(JSONObject o, Option opt) {
        if (!o.has(opt.key)) return "theme";
        Object v = o.opt(opt.key);
        return opt.isSwitch() ? (Boolean.TRUE.equals(v) ? "on" : "off") : String.valueOf(v);
    }

    /** Steps theme, then on / off (a switch) or each value, then back to theme. */
    static void step(Context c, Option opt) {
        JSONObject o = load(c);
        try {
            if (!o.has(opt.key)) {
                o.put(opt.key, opt.isSwitch() ? (Object) Boolean.TRUE : opt.values[0]);
            } else if (opt.isSwitch()) {
                if (Boolean.TRUE.equals(o.opt(opt.key))) o.put(opt.key, false); else o.remove(opt.key);
            } else {
                String cur = String.valueOf(o.opt(opt.key));
                int i = java.util.Arrays.asList(opt.values).indexOf(cur);
                if (i + 1 >= opt.values.length) o.remove(opt.key);
                else o.put(opt.key, numeric(opt.values[i + 1]));
            }
        } catch (JSONException ignored) {
            // keys are plain strings
        }
        save(c, o);
    }

    private static Object numeric(String v) {
        try {
            return Integer.valueOf(v);
        } catch (NumberFormatException e) {
            return v;
        }
    }

    /** Saves one choice by key; a null value goes back to the theme. */
    static void set(Context c, String key, Object value) {
        JSONObject o = load(c);
        try {
            if (value == null) o.remove(key); else o.put(key, value);
        } catch (JSONException ignored) {
            // plain keys
        }
        save(c, o);
    }

    static void setDockApps(Context c, List<String> packages) {
        JSONObject o = load(c);
        try {
            o.put("dock_apps", new JSONArray(packages));
        } catch (JSONException ignored) {
            // nothing to do
        }
        save(c, o);
    }

    static void clear(Context c) {
        save(c, new JSONObject());
    }

    /** Lays the saved choices over a theme's JSON (or over nothing, when the theme set no key). */
    static JSONObject merge(Context c, JSONObject theme) {
        JSONObject mine = load(c);
        if (mine.length() == 0) return theme;
        JSONObject out = theme == null ? new JSONObject() : theme;
        java.util.Iterator<String> it = mine.keys();
        while (it.hasNext()) {
            String k = it.next();
            try {
                out.put(k, mine.get(k));
            } catch (JSONException ignored) {
                // skip an unreadable key
            }
        }
        return out;
    }
}
