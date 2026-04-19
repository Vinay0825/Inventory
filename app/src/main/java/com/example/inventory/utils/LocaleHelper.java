package com.example.inventory.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import java.util.Locale;

public class LocaleHelper {

    private static final String PREFS = "settings";
    private static final String KEY   = "language";

    public static Context wrap(Context base) {
        String lang = getLang(base);
        return applyLocale(base, lang);
    }

    public static void setLang(Context context, String lang) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
               .edit().putString(KEY, lang).apply();
    }

    public static String getLang(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                      .getString(KEY, "en");
    }

    public static Context applyLocale(Context context, String lang) {
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Configuration config = new Configuration(
            context.getResources().getConfiguration());
        config.setLocale(locale);
        return context.createConfigurationContext(config);
    }
}