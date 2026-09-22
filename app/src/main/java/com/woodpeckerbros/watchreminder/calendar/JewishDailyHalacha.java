package com.woodpeckerbros.watchreminder.calendar;

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;
import com.kosherjava.zmanim.hebrewcalendar.JewishDate;
import com.kosherjava.zmanim.hebrewcalendar.TefilaRules;

/**
 * Compact day-status rules for the daily-zmanim screen, following the common
 * Sephardic practice.  They intentionally describe only calendar-dependent
 * rules; a minyan with a chatan, brit or mourner still has its own local rule.
 */
public final class JewishDailyHalacha {
    public enum TikkunChatzot { NONE, LEAH, RACHEL_AND_LEAH }
    private static final TefilaRules TEFILA_RULES = new TefilaRules();

    private JewishDailyHalacha() {
    }

    /** Whether tachanun is omitted at Shacharit for this calendar day. */
    public static boolean omitsTachanun(JewishCalendar day) {
        // KosherJava covers the standard calendar/minhag exceptions. Its default
        // Tishrei policy also covers the days after Yom Kippur; the explicit
        // Aseres-Yemei-Teshuva check fills the days 3-8 that are not included in
        // TefilaRules' end-of-Tishrei switch.
        return day.isAseresYemeiTeshuva()
                || !TEFILA_RULES.isTachanunRecitedShacharis(day);
    }

    /**
     * The calendar day after sunset governs the coming night's Tikkun Chatzot.
     * On Shabbat and Yom Tov it is not said; on days that omit tachanun, only
     * Tikkun Leah is said.  On ordinary weeknights both passages are said.
     */
    public static TikkunChatzot tikkunForNight(JewishCalendar nightDay) {
        if (nightDay.getDayOfWeek() == java.util.Calendar.SATURDAY
                || nightDay.isYomTovAssurBemelacha()) {
            return TikkunChatzot.NONE;
        }
        return omitsTachanun(nightDay) ? TikkunChatzot.LEAH : TikkunChatzot.RACHEL_AND_LEAH;
    }

    public static boolean isErevMajorHoliday(int index) {
        return index == JewishCalendar.EREV_PESACH
                || index == JewishCalendar.EREV_SHAVUOS
                || index == JewishCalendar.EREV_ROSH_HASHANA
                || index == JewishCalendar.EREV_YOM_KIPPUR
                || index == JewishCalendar.EREV_SUCCOS;
    }
}
