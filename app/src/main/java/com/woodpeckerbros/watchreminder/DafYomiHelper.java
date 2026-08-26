package com.woodpeckerbros.watchreminder;

import android.content.Context;

import com.kosherjava.zmanim.hebrewcalendar.Daf;
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;
import com.kosherjava.zmanim.hebrewcalendar.YomiCalculator;
import com.kosherjava.zmanim.hebrewcalendar.YerushalmiYomiCalculator;

import java.util.Calendar;
import java.util.Locale;

public class DafYomiHelper {
    private DafYomiHelper() {
    }

    public static Item itemForEpochDay(Context context, long epochDay) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(DafYomiStore.millisForEpochDay(epochDay));
        JewishCalendar jewishCalendar = JewishCalendarHelper.calendar(context, calendar);
        Daf daf = YomiCalculator.getDafYomiBavli(jewishCalendar);
        String label = daf.getMasechta() + " דף " + dafLabel(daf.getDaf());
        return new Item(epochDay, daf.getMasechta(), daf.getDaf(), label);
    }

    public static String bavliLabel(Context context, long dateMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(dateMillis);
        Daf daf = YomiCalculator.getDafYomiBavli(JewishCalendarHelper.calendar(context, calendar));
        String masechta = AppLanguage.isEnglish(context) ? daf.getMasechtaTransliterated() : daf.getMasechta();
        String page = AppLanguage.isEnglish(context) ? String.valueOf(daf.getDaf()) : dafLabel(daf.getDaf());
        return masechta + " " + page;
    }

    public static String yerushalmiLabel(Context context, long dateMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(dateMillis);
        Daf daf = YerushalmiYomiCalculator.getDafYomiYerushalmi(
                JewishCalendarHelper.calendar(context, calendar));
        if (daf == null) {
            return AppLanguage.isEnglish(context) ? "No daf today" : "אין דף היום";
        }
        String masechta = AppLanguage.isEnglish(context)
                ? daf.getYerushalmiMasechtaTransliterated()
                : daf.getYerushalmiMasechta();
        String page = AppLanguage.isEnglish(context) ? String.valueOf(daf.getDaf()) : dafLabel(daf.getDaf());
        return masechta + " " + page;
    }

    public static String dafLabel(int daf) {
        if (daf <= 0) {
            return String.valueOf(daf);
        }
        String[] hundreds = {"", "ק", "ר"};
        String[] tens = {"", "י", "כ", "ל", "מ", "נ", "ס", "ע", "פ", "צ"};
        String[] ones = {"", "א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט"};
        int value = daf;
        StringBuilder builder = new StringBuilder();
        while (value >= 100) {
            builder.append(hundreds[Math.min(2, value / 100)]);
            value -= Math.min(2, value / 100) * 100;
        }
        if (value == 15) {
            return builder.append("טו").toString();
        }
        if (value == 16) {
            return builder.append("טז").toString();
        }
        builder.append(tens[value / 10]);
        builder.append(ones[value % 10]);
        return builder.length() == 0 ? String.format(Locale.US, "%d", daf) : builder.toString();
    }

    public static class Item {
        public final long epochDay;
        public final String masechta;
        public final int daf;
        public final String label;

        public Item(long epochDay, String masechta, int daf, String label) {
            this.epochDay = epochDay;
            this.masechta = masechta;
            this.daf = daf;
            this.label = label;
        }
    }
}
