package com.woodpeckerbros.watchreminder.calendar;

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;
import com.kosherjava.zmanim.hebrewcalendar.JewishDate;
import com.kosherjava.zmanim.hebrewcalendar.TefilaRules;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Four Sephardic prayer-text transitions. Dates describe the FIRST actual prayer, not the notice. */
public final class PrayerSeasonChanges {
    private static final TefilaRules RULES = new TefilaRules();

    public enum Kind {
        MASHIV_HARUACH, MORID_HATAL, BARECH_ALEINU, BARCHENU
    }

    public enum Prayer {
        MUSAF, MAARIV
    }

    public static final class Change {
        public final Kind kind;
        public final Prayer prayer;
        public final JewishCalendar startDay;

        Change(Kind kind, Prayer prayer, JewishCalendar startDay) {
            this.kind = kind;
            this.prayer = prayer;
            this.startDay = startDay;
        }
    }

    private PrayerSeasonChanges() { }

    public static List<Change> startingOn(JewishCalendar day) {
        List<Change> result = new ArrayList<>();
        if (RULES.isMashivHaruachStartDate(day)) {
            result.add(new Change(Kind.MASHIV_HARUACH, Prayer.MUSAF, day));
        }
        if (RULES.isMashivHaruachEndDate(day)) {
            result.add(new Change(Kind.MORID_HATAL, Prayer.MUSAF, day));
        }
        if (RULES.isVeseinTalUmatarStartingTonight(day)) {
            result.add(new Change(Kind.BARECH_ALEINU, Prayer.MAARIV, day));
        }
        if (isFirstSummerWeekdayMaariv(day)) {
            result.add(new Change(Kind.BARCHENU, Prayer.MAARIV, day));
        }
        return result;
    }

    /** Last workday no later than the civil date of the first prayer. */
    public static JewishCalendar reminderDay(JewishCalendar startDay) {
        JewishCalendar result = copy(startDay);
        while (isRestDay(result)) result = adjacent(result, -1);
        return result;
    }

    public static boolean isRestDay(JewishCalendar day) {
        return day.getDayOfWeek() == Calendar.SATURDAY || day.isYomTovAssurBemelacha();
    }

    private static boolean isFirstSummerWeekdayMaariv(JewishCalendar day) {
        if (day.getJewishMonth() != JewishDate.NISSAN) return false;
        int firstPossible = day.getInIsrael() ? 15 : 16;
        int date = day.getJewishDayOfMonth();
        if (date < firstPossible || date > 22) return false;
        for (int candidateDate = firstPossible; candidateDate <= date; candidateDate++) {
            JewishCalendar candidate = new JewishCalendar(day.getJewishYear(), JewishDate.NISSAN,
                    candidateDate);
            candidate.setInIsrael(day.getInIsrael());
            // Birkas Hashanim occurs in the weekday Amidah of the following Jewish day.
            if (!isRestDay(adjacent(candidate, 1))) return date == candidateDate;
        }
        return false;
    }

    private static JewishCalendar copy(JewishCalendar day) {
        JewishCalendar result = new JewishCalendar(day.getGregorianCalendar());
        result.setInIsrael(day.getInIsrael());
        return result;
    }

    private static JewishCalendar adjacent(JewishCalendar day, int offset) {
        Calendar calendar = day.getGregorianCalendar();
        calendar.add(Calendar.DAY_OF_YEAR, offset);
        JewishCalendar result = new JewishCalendar(calendar);
        result.setInIsrael(day.getInIsrael());
        return result;
    }
}
