package com.woodpeckerbros.watchreminder;

import android.content.Context;

import com.kosherjava.zmanim.ComplexZmanimCalendar;
import com.kosherjava.zmanim.util.GeoLocation;

import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

public class ZmanimHelper {
    public static final String KEY_ALOS = "ALOS";
    public static final String KEY_SUNRISE = "SUNRISE";
    public static final String KEY_SHMA_MGA = "SHMA_MGA";
    public static final String KEY_SHMA_GRA = "SHMA_GRA";
    public static final String KEY_TFILA_GRA = "TFILA_GRA";
    public static final String KEY_CHATZOS = "CHATZOS";
    public static final String KEY_CHATZOS_NIGHT = "CHATZOS_NIGHT";
    public static final String KEY_MINCHA_GEDOLA = "MINCHA_GEDOLA";
    public static final String KEY_MINCHA_KETANA = "MINCHA_KETANA";
    public static final String KEY_PLAG = "PLAG";
    public static final String KEY_SUNSET = "SUNSET";
    public static final String KEY_TZAIS = "TZAIS";

    public static final String[] KEYS = {
            KEY_ALOS,
            KEY_SUNRISE,
            KEY_SHMA_MGA,
            KEY_SHMA_GRA,
            KEY_TFILA_GRA,
            KEY_CHATZOS,
            KEY_MINCHA_GEDOLA,
            KEY_MINCHA_KETANA,
            KEY_PLAG,
            KEY_SUNSET,
            KEY_TZAIS,
            KEY_CHATZOS_NIGHT
    };

    public static final String[] LABELS = {
            "עלות השחר",
            "זריחה",
            "סוף זמן שמע מג״א",
            "סוף זמן שמע גר״א",
            "סוף זמן ברכות ק״ש",
            "חצות יום",
            "מנחה גדולה",
            "מנחה קטנה",
            "פלג המנחה",
            "שקיעה",
            "צאת הכוכבים",
            "חצות לילה"
    };

    private ZmanimHelper() {
    }

    private static final long SECOND_MILLIS = 1_000L;
    private static final long MINUTE_MILLIS = 60_000L;
    private static final long ALOS_OHR_HACHAIM_OFFSET = -100 * SECOND_MILLIS;
    private static final long SHMA_MGA_OHR_HACHAIM_OFFSET = -65 * SECOND_MILLIS;
    private static final long SHMA_GRA_OHR_HACHAIM_OFFSET = -52 * SECOND_MILLIS;
    private static final long TFILA_GRA_OHR_HACHAIM_OFFSET = -34 * SECOND_MILLIS;
    private static final long MINCHA_GEDOLA_OHR_HACHAIM_OFFSET = 42 * SECOND_MILLIS;
    private static final long MINCHA_KETANA_OHR_HACHAIM_OFFSET = 75 * SECOND_MILLIS;
    private static final long PLAG_OHR_HACHAIM_OFFSET = 75 * SECOND_MILLIS;

    private static final int MAX_ZMAN_CACHE = 96;
    private static final Map<String, Long> ZMAN_CACHE = new LinkedHashMap<String, Long>(MAX_ZMAN_CACHE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > MAX_ZMAN_CACHE;
        }
    };

    public static int indexOf(String key) {
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equals(key)) {
                return i;
            }
        }
        return 4;
    }

    public static String label(String key) {
        return LABELS[indexOf(key)];
    }

    public static long timeFor(Context context, Reminder reminder, long dateMillis) {
        Date zman = rawZman(context, reminder.zmanimKey, dateMillis);
        if (zman == null) {
            return Long.MAX_VALUE;
        }
        return ReminderScheduler.floorToMinute(zman.getTime() + reminder.zmanimOffsetMinutes * 60_000L);
    }

    public static long timeForKey(Context context, String key, long dateMillis) {
        Date zman = rawZman(context, key, dateMillis);
        return zman == null ? Long.MAX_VALUE : ReminderScheduler.floorToMinute(zman.getTime());
    }

    private static Date rawZman(Context context, String key, long dateMillis) {
        try {
            ZmanimSettings settings = new ZmanimSettings(context);
            TimeZone timeZone = TimeZone.getTimeZone(settings.timeZoneId());
            Calendar day = Calendar.getInstance(timeZone);
            day.setTimeInMillis(dateMillis);
            String cacheKey = cacheKey(settings, key, day);
            synchronized (ZMAN_CACHE) {
                Long cached = ZMAN_CACHE.get(cacheKey);
                if (cached != null) {
                    return cached == Long.MAX_VALUE ? null : new Date(cached);
                }
            }
            GeoLocation location = new GeoLocation(
                    settings.name(),
                    settings.latitude(),
                    settings.longitude(),
                    settings.elevation(),
                    timeZone
            );
            ComplexZmanimCalendar calendar = new ComplexZmanimCalendar(location);
            calendar.getCalendar().setTimeZone(timeZone);
            calendar.getCalendar().set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH));
            Date result = ohrHachaimZman(calendar, key);
            putCache(cacheKey, result);
            return result;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void putCache(String key, Date value) {
        synchronized (ZMAN_CACHE) {
            ZMAN_CACHE.put(key, value == null ? Long.MAX_VALUE : value.getTime());
        }
    }

    private static String cacheKey(ZmanimSettings settings, String key, Calendar day) {
        return key
                + "|" + day.get(Calendar.YEAR)
                + "|" + day.get(Calendar.MONTH)
                + "|" + day.get(Calendar.DAY_OF_MONTH)
                + "|" + settings.name()
                + "|" + settings.latitude()
                + "|" + settings.longitude()
                + "|" + settings.elevation()
                + "|" + settings.timeZoneId();
    }

    private static Date ohrHachaimZman(ComplexZmanimCalendar calendar, String key) {
        Date sunrise = calendar.getSunrise();
        Date sunset = calendar.getSunset();
        if (sunrise == null || sunset == null) {
            return null;
        }

        if (KEY_ALOS.equals(key)) {
            return offset(calendar.getAlos72Zmanis(), ALOS_OHR_HACHAIM_OFFSET);
        }
        if (KEY_SUNRISE.equals(key)) {
            return visibleSunriseApproximation(sunrise, sunset);
        }
        if (KEY_SHMA_MGA.equals(key)) {
            return offset(calendar.getSofZmanShmaMGA72MinutesZmanis(), SHMA_MGA_OHR_HACHAIM_OFFSET);
        }
        if (KEY_SHMA_GRA.equals(key)) {
            return offset(calendar.getSofZmanShmaGRA(), SHMA_GRA_OHR_HACHAIM_OFFSET);
        }
        if (KEY_TFILA_GRA.equals(key)) {
            return offset(calendar.getSofZmanTfilaGRA(), TFILA_GRA_OHR_HACHAIM_OFFSET);
        }
        if (KEY_CHATZOS.equals(key)) {
            return calendar.getChatzos();
        }
        if (KEY_CHATZOS_NIGHT.equals(key)) {
            return calendar.getSolarMidnight();
        }
        if (KEY_MINCHA_GEDOLA.equals(key)) {
            return offset(calendar.getMinchaGedola(), MINCHA_GEDOLA_OHR_HACHAIM_OFFSET);
        }
        if (KEY_MINCHA_KETANA.equals(key)) {
            return offset(calendar.getMinchaKetana(), MINCHA_KETANA_OHR_HACHAIM_OFFSET);
        }
        if (KEY_PLAG.equals(key)) {
            long dayLength = sunset.getTime() - sunrise.getTime();
            Date plag = new Date(Math.round(sunset.getTime() - dayLength * 1.025 / 12.0));
            return offset(plag, PLAG_OHR_HACHAIM_OFFSET);
        }
        if (KEY_SUNSET.equals(key)) {
            return ceilToMinute(sunset);
        }
        if (KEY_TZAIS.equals(key)) {
            long shaahZmanis = (sunset.getTime() - sunrise.getTime()) / 12L;
            return roundToMinute(new Date(sunset.getTime() + shaahZmanis / 4L));
        }
        return calendar.getChatzos();
    }

    private static Date visibleSunriseApproximation(Date sunrise, Date sunset) {
        long dayLengthMinutes = (sunset.getTime() - sunrise.getTime()) / MINUTE_MILLIS;
        double offsetMinutes = 11.8 - 0.00924 * dayLengthMinutes;
        offsetMinutes = Math.max(3.9, Math.min(6.2, offsetMinutes));
        return new Date(sunrise.getTime() + Math.round(offsetMinutes * MINUTE_MILLIS));
    }

    private static Date offset(Date date, long offsetMillis) {
        return date == null ? null : new Date(date.getTime() + offsetMillis);
    }

    private static Date ceilToMinute(Date date) {
        if (date == null) {
            return null;
        }
        return new Date(((date.getTime() + MINUTE_MILLIS - 1) / MINUTE_MILLIS) * MINUTE_MILLIS);
    }

    private static Date roundToMinute(Date date) {
        if (date == null) {
            return null;
        }
        return new Date(((date.getTime() + MINUTE_MILLIS / 2) / MINUTE_MILLIS) * MINUTE_MILLIS);
    }
}
