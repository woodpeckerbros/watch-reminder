package com.woodpeckerbros.watchreminder.calendar;

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;
import com.kosherjava.zmanim.hebrewcalendar.JewishDate;

/**
 * Compact day-status rules for the daily-zmanim screen, following the common
 * Sephardic practice.  They intentionally describe only calendar-dependent
 * rules; a minyan with a chatan, brit or mourner still has its own local rule.
 */
public final class JewishDailyHalacha {
    public enum TikkunChatzot { NONE, LEAH, RACHEL_AND_LEAH }

    private JewishDailyHalacha() {
    }

    /** Whether tachanun is omitted at Shacharit for this calendar day. */
    public static boolean omitsTachanun(JewishCalendar day) {
        if (day.getDayOfWeek() == java.util.Calendar.SATURDAY
                || day.isRoshChodesh()
                || day.getJewishMonth() == JewishDate.NISSAN
                || day.isYomTov()
                || day.isCholHamoed()
                || day.isChanukah()) {
            return true;
        }
        int index = day.getYomTovIndex();
        return index == JewishCalendar.PESACH_SHENI
                || index == JewishCalendar.TU_BEAV
                || index == JewishCalendar.TU_BESHVAT
                || index == JewishCalendar.PURIM
                || index == JewishCalendar.SHUSHAN_PURIM
                || index == JewishCalendar.PURIM_KATAN
                || index == JewishCalendar.SHUSHAN_PURIM_KATAN
                || index == JewishCalendar.LAG_BAOMER
                || index == JewishCalendar.ISRU_CHAG
                || index == JewishCalendar.TISHA_BEAV;
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
