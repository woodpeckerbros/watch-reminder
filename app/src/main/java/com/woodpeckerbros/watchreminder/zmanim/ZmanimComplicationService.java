package com.woodpeckerbros.watchreminder.zmanim;

import com.woodpeckerbros.watchreminder.reminder.*;

import com.woodpeckerbros.watchreminder.*;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.RemoteException;

import androidx.wear.watchface.complications.data.ComplicationData;
import androidx.wear.watchface.complications.data.ComplicationType;
import androidx.wear.watchface.complications.data.LongTextComplicationData;
import androidx.wear.watchface.complications.data.MonochromaticImage;
import androidx.wear.watchface.complications.data.NoDataComplicationData;
import androidx.wear.watchface.complications.data.PlainComplicationText;
import androidx.wear.watchface.complications.data.ShortTextComplicationData;
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline;
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService;
import androidx.wear.watchface.complications.datasource.ComplicationRequest;
import androidx.wear.watchface.complications.datasource.TimeInterval;
import androidx.wear.watchface.complications.datasource.TimelineEntry;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class ZmanimComplicationService extends ComplicationDataSourceService {
    private static final int TIMELINE_DAYS = 3;

    @Override
    public void onComplicationActivated(int complicationInstanceId, ComplicationType type) {
        ComplicationRefresh.requestActivated(this, ZmanimComplicationService.class,
                complicationInstanceId);
    }

    @Override
    public void onComplicationRequest(ComplicationRequest request, ComplicationRequestListener listener) {
        ComplicationRefresh.logDataRequest(this, ZmanimComplicationService.class,
                request.getComplicationInstanceId(), request.getComplicationType());
        try {
            if (new ReminderSettings(this).jewishMode()
                    && supportsZmanText(request.getComplicationType())) {
                listener.onComplicationDataTimeline(createTimeline(request.getComplicationType()));
            } else {
                listener.onComplicationData(createData(request.getComplicationType()));
            }
        } catch (RemoteException ignored) {
        }
    }

    @Override
    public ComplicationData getPreviewData(ComplicationType type) {
        if (!new ReminderSettings(this).jewishMode()) {
            return jewishModeOffData(type);
        }
        return zmanData(type, UiText.t(this, "שקיעה"), "18:15");
    }

    private ComplicationData createData(ComplicationType type) {
        if (!new ReminderSettings(this).jewishMode()) {
            return jewishModeOffData(type);
        }
        if (!supportsZmanText(type)) {
            return new NoDataComplicationData();
        }
        ZmanimComplicationTimelinePlan.Window current = currentWindow(System.currentTimeMillis());
        if (current == null) {
            return unavailableData(type);
        }
        return zmanData(type, localizedLabel(current.event.key), formatTime(current.event.at));
    }

    private ComplicationDataTimeline createTimeline(ComplicationType type) {
        long now = System.currentTimeMillis();
        List<ZmanimComplicationTimelinePlan.Window> windows = timelineWindows(now);
        ZmanimComplicationTimelinePlan.Window current = windowAt(now, windows);
        ComplicationData defaultData = current == null
                ? unavailableData(type)
                : zmanData(type, localizedLabel(current.event.key), formatTime(current.event.at));
        ArrayList<TimelineEntry> entries = new ArrayList<>();
        for (ZmanimComplicationTimelinePlan.Window window : windows) {
            if (window.endAt <= now) continue;
            long startAt = Math.max(now, window.startAt);
            if (startAt >= window.endAt) continue;
            ComplicationData data = zmanData(type,
                    localizedLabel(window.event.key), formatTime(window.event.at));
            entries.add(new TimelineEntry(
                    new TimeInterval(Instant.ofEpochMilli(startAt), Instant.ofEpochMilli(window.endAt)),
                    data
            ));
        }
        return new ComplicationDataTimeline(defaultData, entries);
    }

    private List<ZmanimComplicationTimelinePlan.Window> timelineWindows(long now) {
        ZmanimSettings settings = new ZmanimSettings(this);
        TimeZone timeZone = TimeZone.getTimeZone(settings.timeZoneId());
        Calendar intervalStart = Calendar.getInstance(timeZone);
        intervalStart.setTimeInMillis(now);
        intervalStart.set(Calendar.HOUR_OF_DAY, 0);
        intervalStart.set(Calendar.MINUTE, 0);
        intervalStart.set(Calendar.SECOND, 0);
        intervalStart.set(Calendar.MILLISECOND, 0);
        Calendar intervalEnd = (Calendar) intervalStart.clone();
        intervalEnd.add(Calendar.DAY_OF_YEAR, TIMELINE_DAYS);
        Calendar day = (Calendar) intervalStart.clone();
        day.add(Calendar.DAY_OF_YEAR, -1);

        ArrayList<ZmanimComplicationTimelinePlan.Event> events = new ArrayList<>();
        // Include yesterday because its solar midnight may fall just after today's 00:00.
        for (int offset = -1; offset < TIMELINE_DAYS; offset++) {
            for (String key : ZmanimHelper.KEYS) {
                addEvent(events, key, ZmanimHelper.timeForKey(this, key, day.getTimeInMillis()));
            }
            addEvent(events, ZmanimHelper.KEY_RABBEINU_TAM,
                    ZmanimHelper.timeForKey(this, ZmanimHelper.KEY_RABBEINU_TAM, day.getTimeInMillis()));
            day.add(Calendar.DAY_OF_YEAR, 1);
        }
        return ZmanimComplicationTimelinePlan.forInterval(
                intervalStart.getTimeInMillis(), intervalEnd.getTimeInMillis(), events);
    }

    private void addEvent(List<ZmanimComplicationTimelinePlan.Event> events, String key, long at) {
        if (at != Long.MAX_VALUE) {
            events.add(new ZmanimComplicationTimelinePlan.Event(key, at));
        }
    }

    private ZmanimComplicationTimelinePlan.Window currentWindow(long now) {
        return windowAt(now, timelineWindows(now));
    }

    private ZmanimComplicationTimelinePlan.Window windowAt(
            long now, List<ZmanimComplicationTimelinePlan.Window> windows) {
        for (ZmanimComplicationTimelinePlan.Window window : windows) {
            if (now >= window.startAt && now < window.endAt) return window;
        }
        return null;
    }

    private String localizedLabel(String key) {
        String label = ZmanimHelper.KEY_RABBEINU_TAM.equals(key)
                ? "צ.כוכבים ר״ת"
                : ZmanimHelper.label(key);
        return UiText.t(this, label);
    }

    private String formatTime(long time) {
        ZmanimSettings settings = new ZmanimSettings(this);
        SimpleDateFormat format = new SimpleDateFormat("HH:mm", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone(settings.timeZoneId()));
        return format.format(new Date(time));
    }

    private boolean supportsZmanText(ComplicationType type) {
        return type.equals(ComplicationType.SHORT_TEXT) || type.equals(ComplicationType.LONG_TEXT);
    }

    private ComplicationData unavailableData(ComplicationType type) {
        return zmanData(type, UiText.t(this, "זמני היום"), UiText.t(this, "לא זמין"));
    }

    private ComplicationData zmanData(ComplicationType type, String label, String time) {
        Context localized = AppLanguage.wrap(this);
        String description = AppLanguage.isEnglish(localized)
                ? "Nearest halachic time: " + label + " at " + time
                : "זמן ההלכה הקרוב ביותר: " + label + " בשעה " + time;
        if (type.equals(ComplicationType.SHORT_TEXT)) {
            return new ShortTextComplicationData.Builder(
                    new PlainComplicationText.Builder(time).build(),
                    new PlainComplicationText.Builder(description).build()
            )
                    .setTitle(new PlainComplicationText.Builder(label).build())
                    .setMonochromaticImage(image())
                    .setTapAction(openZmanimIntent())
                    .build();
        }
        if (type.equals(ComplicationType.LONG_TEXT)) {
            return new LongTextComplicationData.Builder(
                    new PlainComplicationText.Builder(time).build(),
                    new PlainComplicationText.Builder(description).build()
            )
                    .setTitle(new PlainComplicationText.Builder(label).build())
                    .setMonochromaticImage(image())
                    .setTapAction(openZmanimIntent())
                    .build();
        }
        return new NoDataComplicationData();
    }

    private ComplicationData jewishModeOffData(ComplicationType type) {
        String title = getString(R.string.ui_jewish_mode);
        String text = getString(R.string.ui_enable_mode);
        String description = getString(R.string.ui_enable_jewish_mode_halachic);
        if (type.equals(ComplicationType.SHORT_TEXT)) {
            return new ShortTextComplicationData.Builder(
                    new PlainComplicationText.Builder(text).build(),
                    new PlainComplicationText.Builder(description).build()
            )
                    .setTitle(new PlainComplicationText.Builder(title).build())
                    .setMonochromaticImage(image())
                    .setTapAction(openJewishModeIntent())
                    .build();
        }
        if (type.equals(ComplicationType.LONG_TEXT)) {
            return new LongTextComplicationData.Builder(
                    new PlainComplicationText.Builder(title + ": " + text).build(),
                    new PlainComplicationText.Builder(description).build()
            )
                    .setMonochromaticImage(image())
                    .setTapAction(openJewishModeIntent())
                    .build();
        }
        return new NoDataComplicationData();
    }

    private MonochromaticImage image() {
        return new MonochromaticImage.Builder(
                Icon.createWithResource(this, R.drawable.ic_complication_clock)
        ).build();
    }

    private PendingIntent openZmanimIntent() {
        Intent intent = new Intent(this, MainActivity.class)
                .setAction("com.woodpeckerbros.watchreminder.OPEN_ZMANIM_DAY")
                .setData(Uri.parse("watchreminder://zmanim/day"))
                .putExtra(MainActivity.EXTRA_OPEN_ZMANIM_DAY, true)
                .putExtra(MainActivity.EXTRA_FROM_COMPLICATION, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                this,
                8341,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private PendingIntent openJewishModeIntent() {
        return PendingIntent.getActivity(
                this,
                8343,
                JewishModeComplicationConfigActivity.createComplicationIntent(this),
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
