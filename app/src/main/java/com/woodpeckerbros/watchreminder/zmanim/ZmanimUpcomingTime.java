package com.woodpeckerbros.watchreminder.zmanim;

import java.util.List;

/** Chooses the next unpassed event; a previous event can never remain highlighted. */
public final class ZmanimUpcomingTime {
    private ZmanimUpcomingTime() { }

    public static final class Event {
        public final String key;
        public final long at;
        public final long calculationDay;

        public Event(String key, long at, long calculationDay) {
            this.key = key;
            this.at = at;
            this.calculationDay = calculationDay;
        }
    }

    public static Event nextAfter(long now, List<Event> events) {
        Event next = null;
        for (Event event : events) {
            if (event.at == Long.MAX_VALUE || event.at <= now) continue;
            if (next == null || event.at < next.at) next = event;
        }
        return next;
    }
}
