package com.woodpeckerbros.watchreminder;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.RemoteException;

import androidx.wear.watchface.complications.data.ComplicationData;
import androidx.wear.watchface.complications.data.ComplicationType;
import androidx.wear.watchface.complications.data.LongTextComplicationData;
import androidx.wear.watchface.complications.data.MonochromaticImage;
import androidx.wear.watchface.complications.data.NoDataComplicationData;
import androidx.wear.watchface.complications.data.PlainComplicationText;
import androidx.wear.watchface.complications.data.ShortTextComplicationData;
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService;
import androidx.wear.watchface.complications.datasource.ComplicationRequest;

import java.util.Locale;

public final class WaterReminderComplicationService extends ComplicationDataSourceService {
    @Override
    public void onComplicationRequest(ComplicationRequest request, ComplicationRequestListener listener) {
        try {
            listener.onComplicationData(createData(request.getComplicationType(), false));
        } catch (RemoteException ignored) {
        }
    }

    @Override
    public ComplicationData getPreviewData(ComplicationType type) {
        return createData(type, true);
    }

    private ComplicationData createData(ComplicationType type, boolean preview) {
        Context localized = AppLanguage.wrap(this);
        WaterText value = waterText(localized, preview);
        PlainComplicationText description = new PlainComplicationText.Builder(value.description).build();
        if (type.equals(ComplicationType.SHORT_TEXT)) {
            return new ShortTextComplicationData.Builder(
                    new PlainComplicationText.Builder(value.shortText).build(), description)
                    .setTitle(new PlainComplicationText.Builder(value.secondLine).build())
                    .setMonochromaticImage(image())
                    .setTapAction(openSettingsIntent())
                    .build();
        }
        if (type.equals(ComplicationType.LONG_TEXT)) {
            return new LongTextComplicationData.Builder(
                    new PlainComplicationText.Builder(value.longText).build(), description)
                    .setMonochromaticImage(image())
                    .setTapAction(openSettingsIntent())
                    .build();
        }
        return new NoDataComplicationData();
    }

    private WaterText waterText(Context localized, boolean preview) {
        ReminderSettings settings = new ReminderSettings(localized);
        if (!preview && !settings.waterRemindersEnabled()) {
            String off = localized.getString(R.string.water_complication_off);
            return new WaterText(off, localized.getString(R.string.complication_water_reminder),
                    localized.getString(R.string.water_complication_disabled),
                    localized.getString(R.string.water_complication_disabled));
        }
        int consumed = preview ? 750 : new WaterReminderStore(localized).consumedTodayMl();
        int planned;
        if (preview || ReminderSettings.WATER_MODE_DAILY_TARGET.equals(settings.waterMode())) {
            planned = preview ? 2000 : settings.waterDailyTargetMl();
        } else {
            planned = WaterReminderScheduler.remindersPerDay(settings) * settings.waterAmountMl();
        }
        planned = Math.max(1, planned);
        String shortText = compactLiters(consumed) + "/" + compactLiters(planned) + "L";
        boolean targetReached = ReminderSettings.WATER_MODE_DAILY_TARGET.equals(settings.waterMode())
                && consumed >= settings.waterDailyTargetMl();
        String nextTime = NextReminderCalculator.formatTime(
                WaterReminderScheduler.nextTriggerAt(settings, System.currentTimeMillis(), targetReached));
        String secondLine = localized.getString(R.string.water_complication_next_time_short, nextTime);
        String longText = localized.getString(R.string.water_complication_long, consumed, planned, nextTime);
        String description = localized.getString(R.string.water_complication_description_with_next,
                consumed, planned, nextTime);
        return new WaterText(shortText, secondLine, longText, description);
    }

    private String compactLiters(int milliliters) {
        if (milliliters <= 0) {
            return "0";
        }
        if (milliliters % 1000 == 0) {
            return Integer.toString(milliliters / 1000);
        }
        return String.format(Locale.US, "%.1f", milliliters / 1000f);
    }

    private MonochromaticImage image() {
        return new MonochromaticImage.Builder(
                Icon.createWithResource(this, R.drawable.ic_water_drop_notification)).build();
    }

    private PendingIntent openSettingsIntent() {
        Intent intent = new Intent(this, MainActivity.class)
                .putExtra(MainActivity.EXTRA_OPEN_WATER_SETTINGS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, 8343, intent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static final class WaterText {
        final String shortText;
        final String secondLine;
        final String longText;
        final String description;

        WaterText(String shortText, String secondLine, String longText, String description) {
            this.shortText = shortText;
            this.secondLine = secondLine;
            this.longText = longText;
            this.description = description;
        }
    }
}
