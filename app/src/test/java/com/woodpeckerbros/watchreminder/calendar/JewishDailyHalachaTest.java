package com.woodpeckerbros.watchreminder.calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;
import com.kosherjava.zmanim.hebrewcalendar.JewishDate;

import org.junit.Test;

import java.util.Calendar;

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

    @Test public void recognizesAllMajorYomTovDays() {
        assertTrue(JewishDailyHalacha.isMajorYomTov(JewishCalendar.ROSH_HASHANA));
        assertTrue(JewishDailyHalacha.isMajorYomTov(JewishCalendar.PESACH));
        assertTrue(JewishDailyHalacha.isMajorYomTov(JewishCalendar.SHAVUOS));
        assertTrue(JewishDailyHalacha.isMajorYomTov(JewishCalendar.SUCCOS));
        assertTrue(JewishDailyHalacha.isMajorYomTov(JewishCalendar.SHEMINI_ATZERES));
        assertTrue(JewishDailyHalacha.isMajorYomTov(JewishCalendar.SIMCHAS_TORAH));
        assertFalse(JewishDailyHalacha.isMajorYomTov(JewishCalendar.CHOL_HAMOED_SUCCOS));
    }

    @Test public void cholHamoedOrdinalFollowsIsraelAndDiasporaCalendars() {
        JewishCalendar israelPesach = new JewishCalendar(5787, JewishDate.NISSAN, 20);
        israelPesach.setInIsrael(true);
        assertEquals(5, JewishDailyHalacha.cholHamoedDay(israelPesach));
        JewishCalendar diasporaPesach = new JewishCalendar(5787, JewishDate.NISSAN, 20);
        diasporaPesach.setInIsrael(false);
        assertEquals(4, JewishDailyHalacha.cholHamoedDay(diasporaPesach));
        JewishCalendar israelHoshanaRabba = new JewishCalendar(5787, JewishDate.TISHREI, 21);
        israelHoshanaRabba.setInIsrael(true);
        assertEquals(6, JewishDailyHalacha.cholHamoedDay(israelHoshanaRabba));
        JewishCalendar diasporaHoshanaRabba = new JewishCalendar(5787, JewishDate.TISHREI, 21);
        diasporaHoshanaRabba.setInIsrael(false);
        assertEquals(5, JewishDailyHalacha.cholHamoedDay(diasporaHoshanaRabba));
    }

    @Test public void hoshanaRabbaNightPrecedesItsFridayDayIn5787() {
        JewishCalendar thursday = new JewishCalendar(5787, JewishDate.TISHREI, 20);
        JewishCalendar friday = new JewishCalendar(5787, JewishDate.TISHREI, 21);
        assertEquals(Calendar.THURSDAY, thursday.getDayOfWeek());
        assertEquals(Calendar.FRIDAY, friday.getDayOfWeek());
        assertTrue(JewishDailyHalacha.isErevHoshanaRabba(thursday));
        assertFalse(JewishDailyHalacha.isHoshanaRabba(thursday));
        assertTrue(JewishDailyHalacha.isHoshanaRabba(friday));
        assertFalse(JewishDailyHalacha.isErevHoshanaRabba(friday));
    }

    @Test public void displaysVezosHabrachaForIsraeliSheminiAtzeresOnShabbat() {
        JewishCalendar sheminiAtzeres5787 = new JewishCalendar(5787, JewishDate.TISHREI, 22);
        sheminiAtzeres5787.setInIsrael(true);
        assertEquals(Calendar.SATURDAY, sheminiAtzeres5787.getDayOfWeek());
        assertEquals(2026, sheminiAtzeres5787.getGregorianYear());
        assertEquals(Calendar.OCTOBER, sheminiAtzeres5787.getGregorianMonth());
        assertEquals(3, sheminiAtzeres5787.getGregorianDayOfMonth());
        assertEquals(JewishCalendar.Parsha.VZOS_HABERACHA,
                JewishDailyHalacha.yomTovShabbatParsha(sheminiAtzeres5787.getYomTovIndex(), true));
        assertEquals(JewishCalendar.Parsha.NONE,
                JewishDailyHalacha.yomTovShabbatParsha(JewishCalendar.SHEMINI_ATZERES, false));
        assertEquals(JewishCalendar.Parsha.NONE,
                JewishDailyHalacha.yomTovShabbatParsha(JewishCalendar.SUCCOS, true));
    }

}
