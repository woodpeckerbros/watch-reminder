package com.woodpeckerbros.watchreminder.calendar;

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JewishFastInfoTest {
    @Test public void identifiesGedaliaAndAllStandardPublicFasts() {
        assertTrue(JewishFastInfo.isFastIndex(JewishCalendar.FAST_OF_GEDALYAH));
        assertTrue(JewishFastInfo.isFastIndex(JewishCalendar.TENTH_OF_TEVES));
        assertTrue(JewishFastInfo.isFastIndex(JewishCalendar.FAST_OF_ESTHER));
        assertTrue(JewishFastInfo.isFastIndex(JewishCalendar.SEVENTEEN_OF_TAMMUZ));
        assertTrue(JewishFastInfo.isFastIndex(JewishCalendar.TISHA_BEAV));
        assertTrue(JewishFastInfo.isFastIndex(JewishCalendar.YOM_KIPPUR));
    }

    @Test public void doesNotClassifyRoshHashanaAsFast() {
        assertFalse(JewishFastInfo.isFastIndex(JewishCalendar.ROSH_HASHANA));
    }

    @Test public void onlyYomKippurAndTishaBeavStartThePreviousEvening() {
        assertTrue(JewishFastInfo.startsPreviousEvening(JewishCalendar.TISHA_BEAV));
        assertTrue(JewishFastInfo.startsPreviousEvening(JewishCalendar.YOM_KIPPUR));
        assertFalse(JewishFastInfo.startsPreviousEvening(JewishCalendar.FAST_OF_GEDALYAH));
    }
}
