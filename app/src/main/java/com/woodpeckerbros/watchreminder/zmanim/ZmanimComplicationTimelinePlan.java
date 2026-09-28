package com.woodpeckerbros.watchreminder.zmanim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure timeline planning for the zmanim complication; Android rendering stays in the service. */
final class ZmanimComplicationTimelinePlan {
    private ZmanimComplicationTimelinePlan() {
    }

    static List<Window> forInterval(long intervalStart, long intervalEnd, List<Event> source) {
        ArrayList<Event> events = new ArrayList<>(source);
        events.sort(Comparator.comparingLong(event -> event.at));

        ArrayList<Event> unique = new ArrayList<>();
        for (Event event : events) {
            if (event.at == Long.MAX_VALUE) continue;
            if (unique.isEmpty() || unique.get(unique.size() - 1).at != event.at) {
                unique.add(event);
            }
        }

        ArrayList<Window> windows = new ArrayList<>();
        for (int i = 0; i < unique.size(); i++) {
            Event event = unique.get(i);
            long start = i == 0 ? intervalStart : midpoint(unique.get(i - 1).at, event.at);
            long end = i == unique.size() - 1 ? intervalEnd : midpoint(event.at, unique.get(i + 1).at);
            start = Math.max(intervalStart, start);
            end = Math.min(intervalEnd, end);
            if (start < end) {
                windows.add(new Window(event, start, end));
            }
        }
        return windows;
    }

    private static long midpoint(long first, long second) {
        return first + (second - first) / 2L;
    }

    static final class Event {
        final String key;
        final long at;

        Event(String key, long at) {
            this.key = key;
            this.at = at;
        }
    }

    static final class Window {
        final Event event;
        final long startAt;
        final long endAt;

        Window(Event event, long startAt, long endAt) {
            this.event = event;
            this.startAt = startAt;
            this.endAt = endAt;
        }
    }
}
