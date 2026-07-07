package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import java.util.Locale;

public class AppLanguage {
    private AppLanguage() {
    }

    public static Context wrap(Context context) {
        Locale locale = localeFor(context);
        Locale.setDefault(locale);
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale);
        return context.createConfigurationContext(configuration);
    }

    public static boolean isEnglish(Context context) {
        return Locale.ENGLISH.getLanguage().equals(localeFor(context).getLanguage());
    }

    public static boolean isRtl(Context context) {
        return !isEnglish(context);
    }

    public static boolean isHebrew(Context context) {
        return isHebrewLocale(localeFor(context));
    }

    public static boolean isHebrewLanguageSetting(String language) {
        if (ReminderSettings.LANGUAGE_HEBREW.equals(language)) {
            return true;
        }
        return !ReminderSettings.LANGUAGE_ENGLISH.equals(language) && isHebrewLocale(deviceLocale());
    }

    private static Locale localeFor(Context context) {
        String language = new ReminderSettings(context).language();
        if (ReminderSettings.LANGUAGE_HEBREW.equals(language)) {
            return new Locale("he", "IL");
        }
        if (ReminderSettings.LANGUAGE_ENGLISH.equals(language)) {
            return Locale.ENGLISH;
        }
        if (isHebrewLocale(deviceLocale())) {
            return new Locale("he", "IL");
        }
        return Locale.ENGLISH;
    }

    public static Locale deviceLocale() {
        Configuration configuration = Resources.getSystem().getConfiguration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return configuration.getLocales().isEmpty() ? Locale.ENGLISH : configuration.getLocales().get(0);
        }
        return configuration.locale == null ? Locale.ENGLISH : configuration.locale;
    }

    public static boolean isHebrewLocale(Locale locale) {
        String language = locale == null ? "" : locale.getLanguage();
        return "he".equals(language) || "iw".equals(language);
    }
}
