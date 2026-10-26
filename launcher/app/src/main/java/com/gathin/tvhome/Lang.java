package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

/** The interface language: the system's unless the settings pick one ("ui_lang"). */
final class Lang {
    static final String[] CODES = {"system", "en", "ru", "es", "de", "fr", "pt", "it", "zh-CN", "ja", "ko", "tr", "pl", "uk"};
    static final String[] NAMES = {"System", "English", "Русский", "Español", "Deutsch", "Français", "Português", "Italiano",
            "中文", "日本語", "한국어", "Türkçe", "Polski", "Українська"};

    private Lang() {}

    static String code(Context c) {
        return Config.extra(c, "ui_lang", "system");
    }

    /** The context an activity should use: its resources and the default locale follow the chosen language. */
    static Context wrap(Context base) {
        String code = code(base);
        if ("system".equals(code)) {
            Locale.setDefault(Resources.getSystem().getConfiguration().getLocales().get(0));
            return base;
        }
        Locale locale = Locale.forLanguageTag(code);
        Locale.setDefault(locale);
        Configuration conf = new Configuration(base.getResources().getConfiguration());
        conf.setLocale(locale);
        return base.createConfigurationContext(conf);
    }
}
