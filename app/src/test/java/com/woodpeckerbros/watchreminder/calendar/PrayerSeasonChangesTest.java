package com.woodpeckerbros.watchreminder.calendar;

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;
import com.kosherjava.zmanim.hebrewcalendar.JewishDate;

import org.junit.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PrayerSeasonChangesTest {
    @Test public void rainMentionBeginsAtMusafOnSheminiAtzeretAndNotEarlier() {
        JewishCalendar eve = jewish(5787, JewishDate.TISHREI, 21, true);
        JewishCalendar first = jewish(5787, JewishDate.TISHREI, 22, true);
        assertFalse(has(eve, PrayerSeasonChanges.Kind.MASHIV_HARUACH));
        assertPrayer(first, PrayerSeasonChanges.Kind.MASHIV_HARUACH,
                PrayerSeasonChanges.Prayer.MUSAF);
        assertEquals(Calendar.SATURDAY, first.getDayOfWeek());
        assertEquals(21, PrayerSeasonChanges.reminderDay(first).getJewishDayOfMonth());
    }

    @Test public void dewBeginsAtPesachMusafAndAdvanceNoticeIsErevPesach() {
        for (boolean israel : new boolean[] {true, false}) {
            JewishCalendar first = jewish(5787, JewishDate.NISSAN, 15, israel);
            assertPrayer(first, PrayerSeasonChanges.Kind.MORID_HATAL,
                    PrayerSeasonChanges.Prayer.MUSAF);
            assertEquals(14, PrayerSeasonChanges.reminderDay(first).getJewishDayOfMonth());
        }
    }

    @Test public void israelRainRequestBeginsAtMaarivOnSixCheshvanEvening() {
        JewishCalendar sixth = jewish(5787, JewishDate.CHESHVAN, 6, true);
        JewishCalendar seventh = jewish(5787, JewishDate.CHESHVAN, 7, true);
        assertPrayer(sixth, PrayerSeasonChanges.Kind.BARECH_ALEINU,
                PrayerSeasonChanges.Prayer.MAARIV);
        assertFalse(has(seventh, PrayerSeasonChanges.Kind.BARECH_ALEINU));
    }

    @Test public void diasporaRainRequestCanMoveToSaturdayEvening() {
        JewishCalendar friday = civil(2026, Calendar.DECEMBER, 4, false);
        JewishCalendar saturday = civil(2026, Calendar.DECEMBER, 5, false);
        assertFalse(has(friday, PrayerSeasonChanges.Kind.BARECH_ALEINU));
        assertPrayer(saturday, PrayerSeasonChanges.Kind.BARECH_ALEINU,
                PrayerSeasonChanges.Prayer.MAARIV);
        assertEquals(Calendar.FRIDAY, PrayerSeasonChanges.reminderDay(saturday).getDayOfWeek());
    }

    @Test public void summerRequestBeginsAtFirstWeekdayMaarivAfterFirstPesachDay() {
        JewishCalendar israelFirst = jewish(5787, JewishDate.NISSAN, 15, true);
        JewishCalendar diasporaFirst = jewish(5787, JewishDate.NISSAN, 15, false);
        JewishCalendar diasporaSecond = jewish(5787, JewishDate.NISSAN, 16, false);
        JewishCalendar diasporaSaturday = jewish(5787, JewishDate.NISSAN, 17, false);
        assertPrayer(israelFirst, PrayerSeasonChanges.Kind.BARCHENU,
                PrayerSeasonChanges.Prayer.MAARIV);
        assertFalse(has(diasporaFirst, PrayerSeasonChanges.Kind.BARCHENU));
        assertFalse(has(diasporaSecond, PrayerSeasonChanges.Kind.BARCHENU));
        assertPrayer(diasporaSaturday, PrayerSeasonChanges.Kind.BARCHENU,
                PrayerSeasonChanges.Prayer.MAARIV);
        assertEquals(14, PrayerSeasonChanges.reminderDay(diasporaSaturday).getJewishDayOfMonth());
    }

    @Test public void diasporaSummerRequestWaitsUntilAfterShabbatWhenSecondDayIsFriday() {
        boolean found = false;
        for (int year = 5787; year < 5830; year++) {
            JewishCalendar second = jewish(year, JewishDate.NISSAN, 16, false);
            if (second.getDayOfWeek() != Calendar.FRIDAY) continue;
            found = true;
            assertFalse(has(second, PrayerSeasonChanges.Kind.BARCHENU));
            JewishCalendar firstMaariv = jewish(year, JewishDate.NISSAN, 17, false);
            assertPrayer(firstMaariv, PrayerSeasonChanges.Kind.BARCHENU,
                    PrayerSeasonChanges.Prayer.MAARIV);
            assertEquals(14, PrayerSeasonChanges.reminderDay(firstMaariv).getJewishDayOfMonth());
            break;
        }
        assertTrue(found);
    }

    private static boolean has(JewishCalendar day, PrayerSeasonChanges.Kind kind) {
        for (PrayerSeasonChanges.Change change : PrayerSeasonChanges.startingOn(day)) {
            if (change.kind == kind) return true;
        }
        return false;
    }

    private static void assertPrayer(JewishCalendar day, PrayerSeasonChanges.Kind kind,
                                     PrayerSeasonChanges.Prayer prayer) {
        List<PrayerSeasonChanges.Change> changes = PrayerSeasonChanges.startingOn(day);
        for (PrayerSeasonChanges.Change change : changes) {
            if (change.kind == kind) {
                assertEquals(prayer, change.prayer);
                return;
            }
        }
        throw new AssertionError("Missing " + kind + " on " + day.getJewishMonth()
                + "/" + day.getJewishDayOfMonth());
    }

    private static JewishCalendar jewish(int year, int month, int day, boolean israel) {
        JewishCalendar result = new JewishCalendar(year, month, day);
        result.setInIsrael(israel);
        return result;
    }

    private static JewishCalendar civil(int year, int month, int day, boolean israel) {
        JewishCalendar result = new JewishCalendar(new GregorianCalendar(year, month, day));
        result.setInIsrael(israel);
        return result;
    }
}
