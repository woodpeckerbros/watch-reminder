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
    private static final TefilaRules TEFILA_RULES = createTefilaRules();

    private JewishDailyHalacha() {
    }

    /** Whether tachanun is omitted at Shacharit for this calendar day. */
    public static boolean omitsTachanun(JewishCalendar day) {
        return !TEFILA_RULES.isTachanunRecitedShacharis(day);
    }

    private static TefilaRules createTefilaRules() {
        TefilaRules rules = new TefilaRules();
        // Sephardic calendar display requested for this app: after Yom Kippur,
        // tachanun remains omitted through the end of Tishrei. This setting does
        // not suppress tachanun merely because a day is in the first part of the
        // Aseres Yemei Teshuva.
        rules.setTachanunRecitedEndOfTishrei(false);
        return rules;
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

    /** Major Yom Tov days that should receive their own status block. */
    public static boolean isMajorYomTov(int index) {
        return index == JewishCalendar.ROSH_HASHANA
                || index == JewishCalendar.PESACH
                || index == JewishCalendar.SHAVUOS
                || index == JewishCalendar.SUCCOS
                || index == JewishCalendar.SHEMINI_ATZERES
                || index == JewishCalendar.SIMCHAS_TORAH;
    }

    /** The ordinal within Chol Hamoed, including Hoshana Rabba for Sukkot. */
    public static int cholHamoedDay(JewishCalendar day) {
        int index = day.getYomTovIndex();
        boolean pesach = index == JewishCalendar.CHOL_HAMOED_PESACH;
        boolean sukkot = index == JewishCalendar.CHOL_HAMOED_SUCCOS
                || index == JewishCalendar.HOSHANA_RABBA;
        if (!pesach && !sukkot) return 0;
        int firstDay = day.getInIsrael() ? 16 : 17;
        return day.getJewishDayOfMonth() - firstDay + 1;
    }

    /** In Israel, Shemini Atzeres is Simchat Torah and Vezos Habracha is read. */
    public static JewishCalendar.Parsha yomTovShabbatParsha(int index, boolean inIsrael) {
        return inIsrael && index == JewishCalendar.SHEMINI_ATZERES
                ? JewishCalendar.Parsha.VZOS_HABERACHA
                : JewishCalendar.Parsha.NONE;
    }

}
