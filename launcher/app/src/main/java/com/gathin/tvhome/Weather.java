package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * The weather line under the date (theme key weather_city: a town, "auto" for the TV's own town, "off"). The only
 * network use in the launcher: one lookup of the town's coordinates, kept on the TV, then the current conditions at
 * most every 30 minutes. Only the town name or the coordinates are sent. Services are tried in turn, because some
 * networks block one of them: Open-Meteo (CC BY 4.0), MET Norway, then wttr.in; towns are found with Open-Meteo's
 * geocoder, then OpenStreetMap's Nominatim; "auto" uses ipwho.is.
 */
final class Weather {
    interface Listener {
        void onWeather(String line);
    }

    private static final String TAG = "Weather";
    private static final String UA = "cleanyourtv-launcher/1.0 (github.com/Gathin23/cleanyourtv)";
    private static final long REFRESH_MS = 30 * 60 * 1000L;
    private static final String PREFS = "weather", KEY_CITY = "city", KEY_LAT = "lat", KEY_LON = "lon", KEY_NAME = "name",
            KEY_LINE = "line", KEY_AT = "at";

    private final Context context;
    private final String city;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean running;

    Weather(Context context, String city, Listener listener) {
        this.context = context.getApplicationContext();
        this.city = city == null ? "" : city.trim();
        this.listener = listener;
    }

    /** Shows the last line at once, and fetches a fresh one when it is older than 30 minutes. */
    void refresh() {
        if (city.isEmpty()) return;
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (city.equals(p.getString(KEY_CITY, ""))) {
            String line = p.getString(KEY_LINE, "");
            if (!line.isEmpty()) listener.onWeather(line);
            if (System.currentTimeMillis() - p.getLong(KEY_AT, 0) < REFRESH_MS) return;
        }
        if (running) return;
        running = true;
        new Thread(() -> {
            try {
                fetch(p);
            } catch (Exception e) {
                Log.w(TAG, "fetch failed: " + e);
            } finally {
                running = false;
            }
        }, "weather").start();
    }

    private String[] names() {
        return context.getResources().getStringArray(R.array.weather_conditions);
    }

    private void fetch(SharedPreferences p) throws Exception {
        String lang = Locale.getDefault().getLanguage();
        boolean ru = "ru".equals(lang);
        double lat, lon;
        String name;
        if (city.equals(p.getString(KEY_CITY, "")) && p.contains(KEY_LAT)) {
            lat = Double.longBitsToDouble(p.getLong(KEY_LAT, 0));
            lon = Double.longBitsToDouble(p.getLong(KEY_LON, 0));
            name = p.getString(KEY_NAME, city);
        } else if ("auto".equalsIgnoreCase(city)) {
            JSONObject g = new JSONObject(get("https://ipwho.is/?lang=" + lang + "&fields=success,city,latitude,longitude"));
            if (!g.optBoolean("success", false)) return;
            lat = g.getDouble("latitude");
            lon = g.getDouble("longitude");
            name = g.optString("city", "");
        } else {
            double[] at = geocode(lang);
            if (at == null) return;
            lat = at[0];
            lon = at[1];
            name = city;
        }
        String now = conditions(lat, lon, ru, lang);
        if (now == null) return;
        String line = name.isEmpty() ? now : name + "	" + now;
        p.edit().putString(KEY_CITY, city).putLong(KEY_LAT, Double.doubleToLongBits(lat))
                .putLong(KEY_LON, Double.doubleToLongBits(lon)).putString(KEY_NAME, name)
                .putString(KEY_LINE, line).putLong(KEY_AT, System.currentTimeMillis()).apply();
        main.post(() -> listener.onWeather(line));
    }

    private double[] geocode(String lang) {
        try {
            JSONArray r = new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=" + lang
                    + "&name=" + URLEncoder.encode(city, "UTF-8"))).optJSONArray("results");
            if (r != null && r.length() > 0) return new double[]{r.getJSONObject(0).getDouble("latitude"), r.getJSONObject(0).getDouble("longitude")};
        } catch (Exception e) {
            Log.w(TAG, "open-meteo geocoder: " + e);
        }
        try {
            JSONArray r = new JSONArray(get("https://nominatim.openstreetmap.org/search?format=json&limit=1&accept-language="
                    + lang + "&q=" + URLEncoder.encode(city, "UTF-8")));
            if (r.length() > 0) return new double[]{Double.parseDouble(r.getJSONObject(0).getString("lat")), Double.parseDouble(r.getJSONObject(0).getString("lon"))};
        } catch (Exception e) {
            Log.w(TAG, "nominatim: " + e);
        }
        return null;
    }

    /** "+8° Drizzle" from the first service that answers, or null. */
    private String conditions(double lat, double lon, boolean ru, String lang) {
        try {
            JSONObject cur = new JSONObject(get("https://api.open-meteo.com/v1/forecast?current=temperature_2m,weather_code"
                    + "&latitude=" + lat + "&longitude=" + lon)).getJSONObject("current");
            Log.i(TAG, "open-meteo ok");
            return degrees(Math.round(cur.getDouble("temperature_2m"))) + " " + describe(cur.getInt("weather_code"), names());
        } catch (Exception e) {
            Log.w(TAG, "open-meteo: " + e);
        }
        try {
            JSONObject t0 = new JSONObject(get("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=" + lat + "&lon=" + lon))
                    .getJSONObject("properties").getJSONArray("timeseries").getJSONObject(0).getJSONObject("data");
            double temp = t0.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature");
            String symbol = t0.has("next_1_hours") ? t0.getJSONObject("next_1_hours").getJSONObject("summary").getString("symbol_code") : "";
            Log.i(TAG, "met.no ok");
            return degrees(Math.round(temp)) + " " + describeSymbol(symbol, names());
        } catch (Exception e) {
            Log.w(TAG, "met.no: " + e);
        }
        try {
            JSONObject c = new JSONObject(get("https://wttr.in/" + lat + "," + lon + "?format=j1&lang=" + lang))
                    .getJSONArray("current_condition").getJSONObject(0);
            String desc = c.has("lang_" + lang) ? c.getJSONArray("lang_" + lang).getJSONObject(0).getString("value")
                    : c.getJSONArray("weatherDesc").getJSONObject(0).getString("value");
            Log.i(TAG, "wttr.in ok");
            return degrees(Math.round(Double.parseDouble(c.getString("temp_C")))) + " " + desc.trim();
        } catch (Exception e) {
            Log.w(TAG, "wttr.in: " + e);
        }
        return null;
    }

    /** The cached line as shown: "town · +8° Rain", or just "+8° Rain" when the town is hidden (weather_show temp). */
    static String format(String line, boolean withTown) {
        int tab = line.indexOf('	');
        if (tab < 0) return line.trim().replaceAll("\s{2,}", " ");
        return withTown ? line.substring(0, tab) + " " + line.substring(tab + 1) : line.substring(tab + 1);
    }

    private static String degrees(long t) {
        return (t > 0 ? "+" : "") + t + "°";
    }

    private static String get(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(7000);
        c.setReadTimeout(7000);
        c.setRequestProperty("User-Agent", UA);
        try (InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }


    /** WMO weather code to a short phrase. */
    static String describe(int code, String[] names) {
        int i;
        if (code == 0) i = 0;
        else if (code <= 2) i = 1;
        else if (code == 3) i = 2;
        else if (code == 45 || code == 48) i = 3;
        else if (code >= 51 && code <= 57) i = 4;
        else if (code >= 61 && code <= 67) i = 5;
        else if (code >= 71 && code <= 77) i = 6;
        else if (code >= 80 && code <= 82) i = 7;
        else if (code == 85 || code == 86) i = 6;
        else if (code >= 95) i = 8;
        else i = 2;
        return names[i];
    }

    /** MET Norway symbol code ("lightrain_day", "partlycloudy_night") to a short phrase. */
    static String describeSymbol(String symbol, String[] names) {
        int i;
        if (symbol.startsWith("clearsky") || symbol.startsWith("fair")) i = 0;
        else if (symbol.startsWith("partlycloudy")) i = 1;
        else if (symbol.startsWith("cloudy")) i = 2;
        else if (symbol.startsWith("fog")) i = 3;
        else if (symbol.contains("thunder")) i = 8;
        else if (symbol.contains("snow") || symbol.contains("sleet")) i = 6;
        else if (symbol.contains("showers")) i = 7;
        else if (symbol.contains("rain")) i = symbol.startsWith("light") ? 4 : 5;
        else i = 2;
        return names[i];
    }
}
