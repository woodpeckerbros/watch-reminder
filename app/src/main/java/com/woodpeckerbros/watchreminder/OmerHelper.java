package com.woodpeckerbros.watchreminder;

import android.content.Context;

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;

import java.util.Calendar;

public class OmerHelper {
    private OmerHelper() {
    }

    public static Item next(Context context, int offsetMinutes) {
        long now = System.currentTimeMillis();
        Calendar day = Calendar.getInstance();
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        for (int i = 0; i < 370; i++) {
            Item item = itemForEvening(context, day.getTimeInMillis(), offsetMinutes);
            if (item != null && item.triggerAt > now) {
                return item;
            }
            day.add(Calendar.DAY_OF_YEAR, 1);
        }
        return null;
    }

    public static Item dueNow(Context context, int offsetMinutes) {
        long now = System.currentTimeMillis();
        Item best = null;
        Calendar day = Calendar.getInstance();
        day.add(Calendar.DAY_OF_YEAR, -2);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        for (int i = 0; i < 4; i++) {
            Item item = itemForEvening(context, day.getTimeInMillis(), offsetMinutes);
            if (item != null && item.triggerAt <= now && (best == null || item.triggerAt > best.triggerAt)) {
                best = item;
            }
            day.add(Calendar.DAY_OF_YEAR, 1);
        }
        return best;
    }

    public static Item itemForTrigger(Context context, long triggerAt) {
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(triggerAt);
        long tzeis = ZmanimHelper.timeForKey(context, ZmanimHelper.KEY_TZAIS, day.getTimeInMillis());
        if (tzeis == Long.MAX_VALUE || triggerAt < tzeis) {
            day.add(Calendar.DAY_OF_YEAR, -1);
        }
        return itemForEvening(context, day.getTimeInMillis(), 0);
    }

    private static Item itemForEvening(Context context, long eveningDayMillis, int offsetMinutes) {
        long tzeis = ZmanimHelper.timeForKey(context, ZmanimHelper.KEY_TZAIS, eveningDayMillis);
        if (tzeis == Long.MAX_VALUE) {
            return null;
        }
        Calendar jewishDate = Calendar.getInstance();
        jewishDate.setTimeInMillis(eveningDayMillis);
        jewishDate.add(Calendar.DAY_OF_YEAR, 1);
        JewishCalendar jewishCalendar = JewishCalendarHelper.calendar(context, jewishDate);
        int omerDay = jewishCalendar.getDayOfOmer();
        if (omerDay < 1 || omerDay > 49) {
            return null;
        }
        long triggerAt = ReminderScheduler.ceilToMinute(tzeis + offsetMinutes * 60_000L);
        String key = jewishCalendar.getJewishYear() + ":" + omerDay;
        return new Item(key, omerDay, triggerAt, label(omerDay));
    }

    private static String label(int day) {
        int weeks = day / 7;
        int days = day % 7;
        StringBuilder builder = new StringBuilder("היום ");
        builder.append(day).append(day == 1 ? " יום לעומר" : " ימים לעומר");
        if (weeks > 0) {
            builder.append(" שהם ").append(weeks);
            builder.append(weeks == 1 ? " שבוע" : " שבועות");
            if (days > 0) {
                builder.append(" ו-").append(days);
                builder.append(days == 1 ? " יום" : " ימים");
            }
        }
        return builder.toString();
    }

    public static class Item {
        public final String key;
        public final int day;
        public final long triggerAt;
        public final String label;

        Item(String key, int day, long triggerAt, String label) {
            this.key = key;
            this.day = day;
            this.triggerAt = triggerAt;
            this.label = label;
        }
    }
}
