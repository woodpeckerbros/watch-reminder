package com.woodpeckerbros.watchreminder.calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;
import com.kosherjava.zmanim.hebrewcalendar.JewishDate;

import org.junit.Test;

public class JewishDailyHalachaTest {
    @Test public void nissanAndRoshChodeshOmitTachanun() {
        assertTrue(JewishDailyHalacha.omitsTachanun(new JewishCalendar(5787, JewishDate.NISSAN, 8)));
        assertTrue(JewishDailyHalacha.omitsTachanun(new JewishCalendar(5787, JewishDate.IYAR, 1)));
        assertFalse(JewishDailyHalacha.omitsTachanun(new JewishCalendar(5787, JewishDate.IYAR, 9)));
    }

    @Test public void firstPartOfAseresYemeiTeshuvaStillUsesTachanun() {
        assertFalse(JewishDailyHalacha.omitsTachanun(new JewishCalendar(5787, JewishDate.TISHREI, 3)));
        assertFalse(JewishDailyHalacha.omitsTachanun(new JewishCalendar(5787, JewishDate.TISHREI, 7)));
        assertTrue(JewishDailyHalacha.omitsTachanun(new JewishCalendar(5787, JewishDate.TISHREI, 11)));
    }

    @Test public void ordinaryNightGetsRachelAndLeah() {
        assertEquals(JewishDailyHalacha.TikkunChatzot.RACHEL_AND_LEAH,
                JewishDailyHalacha.tikkunForNight(new JewishCalendar(5787, JewishDate.IYAR, 9)));
    }

    @Test public void shabbatAndYomTovNightsDoNotGetTikkun() {
        JewishCalendar yomKippur = new JewishCalendar(5787, JewishDate.TISHREI, 10);
        assertEquals(JewishDailyHalacha.TikkunChatzot.NONE,
                JewishDailyHalacha.tikkunForNight(yomKippur));
    }

    @Test public void recognizesTheSupportedErevHolidayMarkers() {
        assertTrue(JewishDailyHalacha.isErevMajorHoliday(JewishCalendar.EREV_YOM_KIPPUR));
        assertTrue(JewishDailyHalacha.isErevMajorHoliday(JewishCalendar.EREV_SUCCOS));
        assertFalse(JewishDailyHalacha.isErevMajorHoliday(JewishCalendar.CHANUKAH));
    }
}
