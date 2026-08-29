package com.woodpeckerbros.watchreminder;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.woodpeckerbros.watchreminder.smartwake.SmartAlarmScheduler;
import com.woodpeckerbros.watchreminder.smartwake.SmartAlarmStore;

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter;
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;
import com.kosherjava.zmanim.hebrewcalendar.JewishDate;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Calendar;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private static final String EDITOR_HELP_PREFS = "editor_help";
    private static final String EDITOR_HELP_SHOWN = "tour_shown";
    public static final String EXTRA_FOCUS_REMINDER_ID = "focus_reminder_id";
    public static final String EXTRA_FOCUS_NEXT_REMINDER = "focus_next_reminder";
    public static final String EXTRA_OPEN_BLESSING_REMINDER = "open_blessing_reminder";
    public static final String EXTRA_OPEN_PENDING_RESTORE = "open_pending_restore";
    public static final String EXTRA_OPEN_ZMANIM_DAY = "open_zmanim_day";
    public static final String EXTRA_OPEN_FASTING_SETTINGS = "open_fasting_settings";

    private static final int REQUEST_POST_NOTIFICATIONS = 10;
    private static final int REQUEST_FINE_LOCATION = 11;
    private static final int REQUEST_ACTIVITY_RECOGNITION = 12;
    private static final int REQUEST_BODY_SENSORS = 13;
    private static final int REQUEST_CREATE_BACKUP_FILE = 30;
    private static final int REQUEST_OPEN_BACKUP_FILE = 31;
    private static final int REQUEST_PICK_RINGTONE = 32;
    private AlertFeedback smartAlarmPreview;
    private static final int COLOR_BG = 0xFF0B2133;
    private static final int COLOR_SURFACE = 0xFF142A3A;
    private static final int COLOR_SURFACE_2 = 0xFF1A3042;
    private static final int COLOR_TEXT = 0xFFF4EBDD;
    private static final int COLOR_MUTED = 0xFFB8B7AE;
    private static final int COLOR_ACCENT = 0xFFC77B58;
    private static final int COLOR_ACCENT_DARK = 0xFF747D63;
    private static final int COLOR_WARNING = 0xFFFFC857;
    private static final int COLOR_DANGER = 0xFFE15B64;
    private static final int COLOR_LUXURY_GOLD = 0xFFE2C89C;
    private static final int COLOR_EMERALD = 0xFF747D63;
    private static final int COLOR_EMERALD_DEEP = 0xFF344A43;
    private static final int COLOR_CARD = 0xC9263936;
    private static final int COLOR_CARD_TEXT = 0xFFF4EBDD;
    private static final int COLOR_CARD_MUTED = 0xFFC5C8BA;
    private static final String STARTUP_PREFS_NAME = "startup_reliability";
    private static final String KEY_LAST_MISSED_PROMPT_DAY = "last_missed_prompt_day";
    private static final long LATE_ALERT_THRESHOLD_MS = 2 * 60_000L;
    private static final long MISSED_ALERT_LOOKBACK_MS = 24 * 60 * 60_000L;
    private static final long GEOCODER_TIMEOUT_MS = 8_000L;
    private static final long LOCATION_RESCHEDULE_DEBOUNCE_MS = 750L;
    private static final ExecutorService LOCATION_RESCHEDULER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "zmanim-location-rescheduler");
        thread.setDaemon(true);
        return thread;
    });

    private ReminderStore store;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingLocationReschedule;
    private Reminder editingReminder;
    private int selectedHour = 9;
    private int selectedMinute = 0;
    private int selectedYear;
    private int selectedMonth;
    private int selectedDayOfMonth;
    private Set<Integer> selectedDays = new HashSet<>();
    private boolean selectedOneTime;
    private boolean selectedPeriodic;
    private boolean selectedPeriodicHebrew;
    private boolean selectedAnnual;
    private boolean selectedAnnualHebrew;
    private int selectedPeriodicInterval = 1;
    private String selectedPeriodicUnit = Reminder.PERIOD_UNIT_DAYS;
    private int selectedPeriodicEndHour = 23;
    private int selectedPeriodicEndMinute = 59;
    private int selectedAnnualAdvanceHours;
    private int selectedAnnualCounter = 1;
    private boolean selectedEnabled = true;
    private boolean selectedCritical;
    private boolean selectedUseZmanim;
    private String selectedZmanimKey = ZmanimHelper.KEY_CHATZOS;
    private int selectedZmanimOffsetMinutes;
    private EditText nameInput;
    private EditText descriptionInput;
    private EditText annualCounterInput;
    private ScrollView activeScrollView;
    private View smartAlarmSettingsSavedView;
    private ScrollView smartAlarmSettingsSavedScroll;
    private RingtoneChoiceListener smartAlarmRingtoneListener;
    private String smartAlarmRingtoneSelectedUri = "";
    private String ringtoneReturnScreen = "smart_alarm_settings";
    private int editingSmartAlarmId;
    private boolean editingSmartAlarmNew;
    private boolean exactAlarmRequestStarted;
    private boolean fullScreenIntentRequestStarted;
    private boolean permissionRequestInFlight;
    private boolean permissionExplanationVisible;
    private boolean askedPostNotificationsThisSession;
    private boolean askedLocationThisSession;
    private boolean askedActivityRecognitionThisSession;
    private boolean askedBodySensorsThisSession;
    private boolean askedExactAlarmThisSession;
    private boolean askedFullScreenThisSession;
    private String currentScreen = "list";
    private float swipeStartX;
    private float swipeStartY;
    private long swipeStartTime;
    private boolean horizontalBackSwipeCandidate;
    private boolean horizontalBackSwipeTracking;
    private String pendingFocusReminderId;
    private boolean pendingFocusNextReminder;
    private boolean pendingBlessingReminder;
    private boolean pendingRestoreFromPhone;
    private boolean pendingZmanimDay;
    private boolean pendingFastingSettings;
    private boolean zmanimBackToSettings = true;
    private long lastForegroundDueCheckAt;
    private String lastReminderListFingerprint = "";
    private int lastHomeIllustrationPeriod = -1;
    private long createdAt;
    private boolean startupMaintenancePending;
    private boolean startupMaintenanceRunning;
    private boolean startupMaintenanceDone;
    private int startupListPass;
    private int homeUpcomingLoadGeneration;
    private LinearLayout homeUpcomingContainer;
    private static final String[] BLESSING_NAMES = {
            "אשר יצר",
            "קריאת שמע בזמנה",
            "בורא נפשות",
            "מעין שלוש - על המחיה",
            "מעין שלוש - על הגפן",
            "מעין שלוש - על העץ",
            "ברכת המזון"
    };
    private static final String BLESSING_SHEMA_ON_TIME = "קריאת שמע בזמנה";

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLanguage.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NotificationChannelMaintenance.run(this);
        createdAt = System.currentTimeMillis();
        store = new ReminderStore(this);
        handleIntent(getIntent());
        ReminderSettings onboardingSettings = new ReminderSettings(this);
        if (onboardingSettings.onboardingComplete()) {
            showList();
        } else if (ReminderSettings.LANGUAGE_ENGLISH.equals(onboardingSettings.language())) {
            onboardingSettings.markOnboardingStarted();
            showJewishModeOnboarding();
        } else {
            onboardingSettings.markOnboardingStarted();
            showLanguageOnboarding();
        }
        mainHandler.postDelayed(() -> {
            if (!new ReminderSettings(MainActivity.this).onboardingComplete()) {
                return;
            }
            openPendingBlessingReminder();
            openPendingRestoreFromPhone();
            openPendingZmanimDay();
            openPendingFastingSettings();
        }, 260L);
        if (new ReminderSettings(this).onboardingComplete()) {
            scheduleStartupMaintenance();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ReminderAlertQueueStore deferredQueue = new ReminderAlertQueueStore(this);
        if (deferredQueue.hasDeferredAlerts()) {
            AppLog.d(this, "MainActivity resumed by user with deferred alerts, marking available");
            new WearStateStore(this).markAvailable();
            DeferredWearRetryReceiver.cancel(this);
        }
        if (store != null && exactAlarmRequestStarted && ReminderScheduler.canScheduleExactAlarms(this)) {
            exactAlarmRequestStarted = false;
            store.rescheduleAll();
            DafYomiScheduler.schedule(this);
            OmerScheduler.schedule(this);
            TekufaScheduler.schedule(this);
            ReminderScheduler.scheduleWatchdog(this);
            showList();
        } else if (exactAlarmRequestStarted) {
            exactAlarmRequestStarted = false;
        }
        if (store != null && fullScreenIntentRequestStarted && canUseFullScreenIntent()) {
            fullScreenIntentRequestStarted = false;
            showList();
        } else if (fullScreenIntentRequestStarted) {
            fullScreenIntentRequestStarted = false;
        }
        if (startupMaintenancePending || startupMaintenanceRunning || System.currentTimeMillis() - createdAt < 2_500L) {
            mainHandler.postDelayed(() -> ReminderReceiver.dispatchNextQueued(MainActivity.this), 900L);
        } else {
            ReminderReceiver.dispatchNextQueued(this);
        }
        if (!startupMaintenancePending && !startupMaintenanceRunning) {
            runForegroundDueCheckIfNeeded();
            refreshVisibleScreenIfRemindersChanged();
            if ("list".equals(currentScreen)
                    && lastHomeIllustrationPeriod != homeIllustrationPeriod()) {
                showList(activeScrollView == null ? 0 : activeScrollView.getScrollY());
            }
            requestMissingAccessIfNeeded();
        }
    }

    private void scheduleStartupMaintenance() {
        if (startupMaintenancePending || startupMaintenanceRunning || startupMaintenanceDone) {
            return;
        }
        startupMaintenancePending = true;
        Runnable task = this::runStartupMaintenance;
        if (activeScrollView == null) {
            mainHandler.postDelayed(task, 900L);
            return;
        }
        activeScrollView.postDelayed(task, 900L);
    }

    private void runStartupMaintenance() {
        if (startupMaintenanceRunning || startupMaintenanceDone) {
            return;
        }
        startupMaintenancePending = false;
        startupMaintenanceRunning = true;
        new Thread(() -> {
            ReminderSettings settings = new ReminderSettings(MainActivity.this);
            settings.applyPowerSaveDefaultOnce();
            long now = System.currentTimeMillis();
            AppLog.d(MainActivity.this, "startup maintenance begin");
            HealthStateRegistrar.register(MainActivity.this);
            ReminderDueChecker.dispatchDue(MainActivity.this, now - ReminderDueChecker.CATCH_UP_LOOKBACK_MS, now);
            ReminderAudit.run(MainActivity.this);
            store.rescheduleAll();
            DafYomiScheduler.schedule(MainActivity.this);
            OmerScheduler.schedule(MainActivity.this);
            TekufaScheduler.schedule(MainActivity.this);
            IntermittentFastingScheduler.schedule(MainActivity.this);
            ReminderScheduler.scheduleWatchdog(MainActivity.this);
            ComplicationRefresh.requestAll(MainActivity.this);
            if (settings.serviceEnabled()) {
                ReminderForegroundService.start(MainActivity.this);
            } else {
                ReminderForegroundService.stop(MainActivity.this);
            }
            AppLog.d(MainActivity.this, "startup maintenance end");
            mainHandler.post(() -> {
                startupMaintenanceRunning = false;
                startupMaintenanceDone = true;
                lastForegroundDueCheckAt = System.currentTimeMillis();
                ReminderReceiver.dispatchNextQueued(MainActivity.this);
                DafYomiScheduler.dispatchIfDueNow(MainActivity.this);
                OmerScheduler.dispatchIfDueNow(MainActivity.this);
                refreshVisibleScreenAfterStartupMaintenance();
                if (!showMissedReminderReliabilityPromptIfNeeded()) {
                    requestMissingAccessIfNeeded();
                }
            });
        }, "wr-startup-maintenance").start();
    }

    private void runForegroundDueCheckIfNeeded() {
        if (store == null) {
            return;
        }
        if (startupMaintenancePending || startupMaintenanceRunning || System.currentTimeMillis() - createdAt < 2_500L) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastForegroundDueCheckAt < 20_000L) {
            return;
        }
        lastForegroundDueCheckAt = now;
        AppLog.d(this, "MainActivity foreground due check");
        ReminderDueChecker.dispatchDue(this, now - ReminderDueChecker.CATCH_UP_LOOKBACK_MS, now);
        ReminderAudit.run(this);
        store.rescheduleAll();
        DafYomiScheduler.dispatchIfDueNow(this);
        OmerScheduler.dispatchIfDueNow(this);
        ReminderScheduler.scheduleWatchdog(this);
        ReminderReceiver.dispatchNextQueued(this);
        IntermittentFastingScheduler.schedule(this);
    }

    private boolean showMissedReminderReliabilityPromptIfNeeded() {
        ReminderSettings settings = new ReminderSettings(this);
        if (settings.serviceEnabled()) {
            return false;
        }
        SharedPreferences prefs = getSharedPreferences(STARTUP_PREFS_NAME, MODE_PRIVATE);
        String todayKey = todayPromptKey();
        if (todayKey.equals(prefs.getString(KEY_LAST_MISSED_PROMPT_DAY, ""))) {
            return false;
        }
        MissedReminderSummary summary = missedReminderSummary();
        if (summary.count <= 0) {
            return false;
        }
        prefs.edit().putString(KEY_LAST_MISSED_PROMPT_DAY, todayKey).apply();
        String title = summary.count == 1
                ? "ייתכן שתזכורת לא הופיעה בזמן"
                : "ייתכן שתזכורות לא הופיעו בזמן";
        String message;
        if (AppLanguage.isEnglish(this)) {
            message = "The app detected " + (summary.count == 1 ? "a recent reminder" : "several recent reminders")
                    + " that did not appear at the scheduled time.\n\n"
                    + "This can happen because of Wear OS battery management, temporary system load, or restricted background activity.\n\n"
                    + "We recommend enabling Background Check Active. The app will periodically wake and check for reminders that should be shown.\n\n"
                    + "Note: this may use more battery. You can change the interval later in Advanced Settings.\n\n"
                    + "Latest detected reminder: " + summary.latestName + " at " + NextReminderCalculator.formatTime(summary.latestScheduledAt);
        } else {
            message = "האפליקציה זיהתה "
                    + (summary.count == 1 ? "שתזכורת אחרונה" : "שכמה תזכורות אחרונות")
                    + " לא הוצגה בזמן המתוכנן.\n\n"
                    + "זה יכול לקרות בגלל ניהול סוללה של Wear OS, עומס זמני של המערכת, או חסימה של פעילות ברקע.\n\n"
                    + "מומלץ להפעיל \"בדיקת רקע פעילה\". במצב זה האפליקציה תעיר את עצמה מדי פעם ותבדוק אם יש תזכורות שצריך להציג.\n\n"
                    + "שימו לב: הפעלת האפשרות עשויה לצרוך יותר סוללה. אפשר לשנות אחר כך את מספר הדקות בין בדיקות במסך ההגדרות המתקדמות.\n\n"
                    + "התזכורת האחרונה שזוהתה: " + summary.latestName + " בשעה " + NextReminderCalculator.formatTime(summary.latestScheduledAt);
        }
        new AlertDialog.Builder(this)
                .setTitle(UiText.t(this, title))
                .setMessage(message)
                .setPositiveButton(UiText.t(this, "הפעל בדיקת רקע"), (dialog, which) -> enableBackgroundReliabilityCheck())
                .setNegativeButton(UiText.t(this, "לא עכשיו"), null)
                .show();
        return true;
    }

    private void enableBackgroundReliabilityCheck() {
        ReminderSettings settings = new ReminderSettings(this);
        settings.setServiceEnabled(true);
        ReminderScheduler.scheduleWatchdog(this);
        ReminderForegroundService.start(this);
        Toast.makeText(
                this,
                UiText.t(this, "בדיקת רקע פעילה הופעלה. ניתן לשנות את מרווח הדקות בהגדרות המתקדמות."),
                Toast.LENGTH_LONG
        ).show();
        refreshVisibleScreen();
    }

    private MissedReminderSummary missedReminderSummary() {
        long now = System.currentTimeMillis();
        long lookbackStart = now - MISSED_ALERT_LOOKBACK_MS;
        int count = 0;
        ReminderEventStore.Event latest = null;
        for (ReminderEventStore.Event event : new ReminderEventStore(this).getAll()) {
            if (event.scheduledAt < lookbackStart || event.scheduledAt > now) {
                continue;
            }
            if (!missedWithoutUserDeferral(event)) {
                continue;
            }
            count++;
            if (latest == null || event.scheduledAt > latest.scheduledAt) {
                latest = event;
            }
        }
        if (latest == null) {
            return new MissedReminderSummary(0, "", 0L);
        }
        return new MissedReminderSummary(count, latest.reminderName, latest.scheduledAt);
    }

    private boolean missedWithoutUserDeferral(ReminderEventStore.Event event) {
        if (!ReminderEventStore.STATUS_FIRED.equals(event.status)) {
            return false;
        }
        if ("תזכורת שנדחתה".equals(event.note) || ReminderEventStore.NOTE_EARLY_DONE.equals(event.note)) {
            return false;
        }
        if ("עבר הזמן".equals(event.note)) {
            return true;
        }
        return event.firedAt - event.scheduledAt >= LATE_ALERT_THRESHOLD_MS;
    }

    private String todayPromptKey() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.YEAR) + "-" + calendar.get(Calendar.DAY_OF_YEAR);
    }

    private static class MissedReminderSummary {
        final int count;
        final String latestName;
        final long latestScheduledAt;

        MissedReminderSummary(int count, String latestName, long latestScheduledAt) {
            this.count = count;
            this.latestName = latestName == null || latestName.trim().isEmpty() ? "תזכורת" : latestName;
            this.latestScheduledAt = latestScheduledAt;
        }
    }

    @Override
    protected void onPause() {
        if ("settings".equals(currentScreen) || "alert_settings".equals(currentScreen)) {
            stopVibrationPreview();
        }
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
        showList();
        openPendingBlessingReminder();
        openPendingRestoreFromPhone();
        openPendingZmanimDay();
        openPendingFastingSettings();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_POST_NOTIFICATIONS) {
            permissionRequestInFlight = false;
            store.rescheduleAll();
            ReminderScheduler.scheduleWatchdog(this);
        } else if (requestCode == REQUEST_ACTIVITY_RECOGNITION) {
            permissionRequestInFlight = false;
            HealthStateRegistrar.register(this);
        } else if (requestCode == REQUEST_BODY_SENSORS) {
            permissionRequestInFlight = false;
            DeferredReminderDispatcher.run(this);
        } else if (requestCode == REQUEST_FINE_LOCATION) {
            permissionRequestInFlight = false;
            if ("settings".equals(currentScreen) || "jewish_settings".equals(currentScreen)) {
                if ("jewish_settings".equals(currentScreen)) {
                    showJewishSettings();
                } else {
                    showSettings();
                }
            }
        }
        requestMissingAccessIfNeeded();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        if (requestCode == REQUEST_PICK_RINGTONE) {
            Uri picked = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            new ReminderSettings(this).setAlertSoundUri(picked == null ? "" : picked.toString());
            showSettings();
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        if (requestCode == REQUEST_CREATE_BACKUP_FILE) {
            try {
                ReminderBackup.writeToUri(this, uri);
                Toast.makeText(this, UiText.t(this, "הגיבוי נשמר"), Toast.LENGTH_SHORT).show();
                showSettings();
            } catch (Exception exception) {
                AppLog.e(this, "backup write uri failed", exception);
                Toast.makeText(this, UiText.t(this, "לא הצלחתי לשמור את הקובץ"), Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_OPEN_BACKUP_FILE) {
            confirmRestoreBackupUri(uri);
        }
    }

    @Override
    public void onBackPressed() {
        if (navigateBack()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (activeScrollView != null
                && event.getAction() == MotionEvent.ACTION_SCROLL
                && (event.getSource() & InputDevice.SOURCE_ROTARY_ENCODER) == InputDevice.SOURCE_ROTARY_ENCODER) {
            float delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL);
            activeScrollView.smoothScrollBy(0, Math.round(delta * dp(42)));
            return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

    private void showList() {
        if (pendingFocusReminderId != null || pendingFocusNextReminder) {
            showAllReminders();
            return;
        }
        currentScreen = "list";
        editingReminder = null;
        boolean jewishMode = new ReminderSettings(this).jewishMode();
        LinearLayout content = baseContent();
        addTitle(content, homeGreeting(), null);
        lastHomeIllustrationPeriod = homeIllustrationPeriod();
        int homeBackgroundResource = homeIllustrationResource(lastHomeIllustrationPeriod);

        LinearLayout primaryActions = actionRow();
        Button addButton = pillButton("תזכורת", COLOR_EMERALD);
        addButton.setOnClickListener(v -> showEditor(null));
        Button historyButton = pillButton("היסטוריה", COLOR_SURFACE_2);
        historyButton.setOnClickListener(v -> showHistory());
        primaryActions.addView(addButton);
        primaryActions.addView(historyButton);
        content.addView(primaryActions);

        if (!ReminderScheduler.canScheduleExactAlarms(this)) {
            TextView warning = infoPill("לחץ כאן כדי לאשר Alarms & reminders לתזכורות מדויקות", COLOR_WARNING);
            warning.setOnClickListener(v -> requestExactAlarmAccessIfNeeded(true));
            content.addView(warning);
        }
        if (!canUseFullScreenIntent()) {
            TextView warning = infoPill("צריך לאשר התראות במסך מלא כדי שהתזכורת תיפתח על המסך", COLOR_WARNING);
            warning.setOnClickListener(v -> requestFullScreenIntentAccessIfNeeded(true));
            content.addView(warning);
        }

        LinearLayout upcomingContainer = new LinearLayout(this);
        upcomingContainer.setOrientation(LinearLayout.VERTICAL);
        upcomingContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        upcomingContainer.addView(infoPill("טוען תזכורות...", COLOR_MUTED));
        content.addView(upcomingContainer, matchParams());
        homeUpcomingContainer = upcomingContainer;
        int loadGeneration = ++homeUpcomingLoadGeneration;

        Button allReminders = pillButton("כל התזכורות", COLOR_SURFACE_2);
        allReminders.setTextColor(COLOR_TEXT);
        allReminders.setOnClickListener(v -> showAllReminders());
        content.addView(allReminders, matchParams());

        TextView shortcutsTitle = text("גישה מהירה", 12, COLOR_LUXURY_GOLD);
        AppFont.bold(shortcutsTitle);
        shortcutsTitle.setPadding(0, dp(12), 0, dp(2));
        content.addView(shortcutsTitle);
        Button smartAlarmButton = pillButton("שעון מעורר חכם", COLOR_SURFACE_2);
        smartAlarmButton.setOnClickListener(v -> showSmartAlarmSettings());
        content.addView(smartAlarmButton, matchParams());
        if (jewishMode) {
            LinearLayout jewishActions = actionRow();
            Button zmanimButton = pillButton("זמני היום", COLOR_EMERALD_DEEP);
            zmanimButton.setOnClickListener(v -> openZmanimDayFromMain());
            Button blessingButton = pillButton("תזכורת לברכה", COLOR_EMERALD_DEEP);
            blessingButton.setOnClickListener(v -> showBlessingReminder());
            jewishActions.addView(zmanimButton);
            jewishActions.addView(blessingButton);
            content.addView(jewishActions);
        }
        if (new ReminderSettings(this).intermittentFastingEnabled()) {
            Button fastingButton = pillButton("צום לסירוגין", COLOR_SURFACE_2);
            fastingButton.setOnClickListener(v -> showFastingSettings());
            content.addView(fastingButton, matchParams());
        }
        Button settingsButton = pillButton("הגדרות", COLOR_SURFACE_2);
        settingsButton.setOnClickListener(v -> showSettings());
        content.addView(settingsButton, matchParams());
        View bottomSafeArea = new View(this);
        content.addView(bottomSafeArea, new LinearLayout.LayoutParams(dp(1), dp(6)));
        setScrollableContent(content, homeBackgroundResource);
        rememberReminderListFingerprint();
        loadHomeUpcomingAsync(upcomingContainer, loadGeneration);
    }

    private String homeGreeting() {
        int period = homeIllustrationPeriod();
        if (AppLanguage.isEnglish(this)) {
            if (period == 0) return "Good morning";
            if (period == 1) return "Good afternoon";
            if (period == 2) return "Good evening";
            return "Good night";
        }
        if (period == 0) return "בוקר טוב";
        if (period == 1) return "צהריים טובים";
        if (period == 2) return "ערב טוב";
        return "לילה טוב";
    }

    private int homeIllustrationPeriod() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 5 && hour < 11) return 0;
        if (hour >= 11 && hour < 17) return 1;
        if (hour >= 17 && hour < 21) return 2;
        return 3;
    }

    private int homeIllustrationResource(int period) {
        if (period == 0) return R.drawable.home_horizon_morning;
        if (period == 1) return R.drawable.home_horizon_noon;
        if (period == 2) return R.drawable.home_horizon_evening;
        return R.drawable.home_horizon_night;
    }

    private List<HomeReminderItem> nearestHomeReminderItems() {
        List<HomeReminderItem> items = new java.util.ArrayList<>();
        ReminderSnoozeStore snoozeStore = new ReminderSnoozeStore(this);
        ReminderEventStore eventStore = new ReminderEventStore(this);
        for (Reminder reminder : new ReminderStore(this).getAll()) {
            NextReminderCalculator.NextReminder next = NextReminderCalculator.nextForReminder(
                    this, reminder, snoozeStore, eventStore, true);
            if (next != null) {
                items.add(new HomeReminderItem(reminder, next));
                items.sort((left, right) -> Long.compare(left.next.scheduledAt, right.next.scheduledAt));
                if (items.size() > 3) {
                    items.remove(items.size() - 1);
                }
            }
        }
        return items;
    }

    private void loadHomeUpcomingAsync(LinearLayout container, int generation) {
        new Thread(() -> {
            List<HomeReminderItem> upcoming = nearestHomeReminderItems();
            mainHandler.post(() -> {
                if (!"list".equals(currentScreen)
                        || generation != homeUpcomingLoadGeneration
                        || container != homeUpcomingContainer
                        || isFinishing()) {
                    return;
                }
                renderHomeUpcoming(container, upcoming);
            });
        }, "home-upcoming").start();
    }

    private void renderHomeUpcoming(LinearLayout container, List<HomeReminderItem> upcoming) {
        container.removeAllViews();
        if (upcoming.isEmpty()) {
            LinearLayout emptyCard = card();
            TextView emptyTitle = text("אין תזכורות קרובות", 16, COLOR_TEXT);
            AppFont.bold(emptyTitle);
            TextView emptySubtitle = text("אפשר להוסיף תזכורת חדשה בלחיצה אחת", 11, COLOR_MUTED);
            emptySubtitle.setPadding(0, dp(4), 0, 0);
            emptyCard.addView(emptyTitle);
            emptyCard.addView(emptySubtitle);
            container.addView(emptyCard, cardParams());
            return;
        }
        container.addView(homeNextReminderCard(upcoming.get(0)), homeHeroParams());
        if (upcoming.size() <= 1) {
            return;
        }
        TextView following = text("אחר כך", 12, COLOR_LUXURY_GOLD);
        AppFont.bold(following);
        following.setPadding(0, dp(7), 0, dp(2));
        container.addView(following);
        for (int i = 1; i < upcoming.size(); i++) {
            container.addView(homeCompactReminderCard(upcoming.get(i)), compactCardParams());
        }
    }

    private LinearLayout homeNextReminderCard(HomeReminderItem item) {
        LinearLayout card = card(false);
        card.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        card.setBackground(new GlowingReminderCardDrawable(dp(18)));
        card.setPadding(dp(16), dp(13), dp(16), dp(13));
        card.setOnClickListener(v -> showEditor(item.reminder));
        card.setOnLongClickListener(v -> {
            showReminderActions(item.reminder);
            return true;
        });
        TextView label = text("התזכורת הבאה", 11, GlowingReminderCardDrawable.PROMINENT_BORDER_COLOR);
        AppFont.bold(label);
        TextView time = text(NextReminderCalculator.formatTime(item.next.scheduledAt), 34, COLOR_TEXT);
        AppFont.bold(time);
        time.setPadding(0, dp(2), 0, 0);
        TextView name = text(item.next.reminderName, 18, COLOR_TEXT);
        AppFont.bold(name);
        TextView date = text(homeDateLabel(item.next.scheduledAt), 11, COLOR_MUTED);
        date.setPadding(0, dp(3), 0, dp(4));
        card.addView(label);
        card.addView(time);
        card.addView(name);
        card.addView(date);
        LinearLayout actions = actionRow();
        Button done = smallWideButton("בוצע", COLOR_EMERALD);
        done.setOnClickListener(v -> completeUpcoming(item.reminder));
        Button snooze = smallWideButton("דחה", COLOR_SURFACE_2);
        snooze.setOnClickListener(v -> showSnoozeUpcomingOptions(item.reminder));
        actions.addView(done);
        actions.addView(snooze);
        card.addView(actions);
        return card;
    }

    private LinearLayout homeCompactReminderCard(HomeReminderItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(9), dp(12), dp(9));
        card.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        card.setBackground(new GlowingReminderCardDrawable(dp(12), false));
        card.setOnClickListener(v -> showEditor(item.reminder));
        card.setOnLongClickListener(v -> {
            showReminderActions(item.reminder);
            return true;
        });
        TextView time = text(NextReminderCalculator.formatTime(item.next.scheduledAt), 18, COLOR_LUXURY_GOLD);
        AppFont.bold(time);
        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setGravity(Gravity.CENTER);
        TextView name = text(item.next.reminderName, 14, COLOR_TEXT);
        AppFont.bold(name);
        TextView date = text(homeDateLabel(item.next.scheduledAt), 10, COLOR_MUTED);
        textColumn.addView(name);
        textColumn.addView(date);
        card.addView(time, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.34f));
        card.addView(textColumn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.66f));
        return card;
    }

    private String homeDateLabel(long time) {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(time);
        boolean today = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR);
        if (today) {
            return UiText.t(this, "היום");
        }
        now.add(Calendar.DAY_OF_YEAR, 1);
        boolean tomorrow = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR);
        return tomorrow ? UiText.t(this, "מחר") : NextReminderCalculator.formatDateTime(time);
    }

    private static class HomeReminderItem {
        final Reminder reminder;
        final NextReminderCalculator.NextReminder next;

        HomeReminderItem(Reminder reminder, NextReminderCalculator.NextReminder next) {
            this.reminder = reminder;
            this.next = next;
        }
    }

    private void showAllReminders() {
        currentScreen = "all_reminders";
        editingReminder = null;
        boolean jewishMode = new ReminderSettings(this).jewishMode();
        boolean fastStartupList = shouldUseFastStartupList();
        LinearLayout content = baseContent();
        addTitle(content, "כל התזכורות", "לחיצה לעריכה · לחיצה ארוכה לפעולות");

        Button backHome = pillButton("חזרה לבית", COLOR_SURFACE_2);
        backHome.setOnClickListener(v -> showList());
        content.addView(backHome, matchParams());

        LinearLayout topActions = actionRow();
        Button addButton = pillButton("הוספה", COLOR_ACCENT_DARK);
        addButton.setOnClickListener(v -> showEditor(null));
        Button historyButton = pillButton("היסטוריה", COLOR_SURFACE_2);
        historyButton.setOnClickListener(v -> showHistory());
        topActions.addView(addButton);
        topActions.addView(historyButton);
        content.addView(topActions);

        if (jewishMode) {
            Button zmanimButton = pillButton("זמני היום בהלכה", COLOR_SURFACE_2);
            zmanimButton.setOnClickListener(v -> openZmanimDayFromMain());
            content.addView(zmanimButton, matchParams());

            Button blessingButton = pillButton("תזכורת לברכה", COLOR_SURFACE_2);
            blessingButton.setOnClickListener(v -> showBlessingReminder());
            content.addView(blessingButton, matchParams());
        }

        if (new ReminderSettings(this).intermittentFastingEnabled()) {
            Button fastingButton = pillButton("צום לסירוגין", COLOR_SURFACE_2);
            fastingButton.setOnClickListener(v -> showFastingSettings());
            content.addView(fastingButton, matchParams());
        }

        if (!ReminderScheduler.canScheduleExactAlarms(this)) {
            TextView warning = infoPill("לחץ כאן כדי לאשר Alarms & reminders לתזכורות מדויקות", COLOR_WARNING);
            warning.setOnClickListener(v -> requestExactAlarmAccessIfNeeded(true));
            content.addView(warning);
        }
        if (!canUseFullScreenIntent()) {
            TextView warning = infoPill("צריך לאשר התראות במסך מלא כדי שהתזכורת תיפתח על המסך", COLOR_WARNING);
            warning.setOnClickListener(v -> requestFullScreenIntentAccessIfNeeded(true));
            content.addView(warning);
        }

        if (fastStartupList && startupListPass == 1) {
            TextView loading = infoPill("טוען תזכורות...", COLOR_MUTED);
            content.addView(loading);
            setScrollableContent(content);
            rememberReminderListFingerprint();
            activeScrollView.postDelayed(() -> {
                if ("all_reminders".equals(currentScreen) && !startupMaintenanceDone && startupListPass == 1) {
                    showAllReminders();
                }
            }, 220L);
            return;
        }

        List<Reminder> reminders = new java.util.ArrayList<>(store.getAll());
        ReminderSnoozeStore snoozeStore = new ReminderSnoozeStore(this);
        ReminderEventStore eventStore = new ReminderEventStore(this);
        java.util.Map<String, NextReminderCalculator.NextReminder> nextByReminder = new java.util.HashMap<>();
        NextReminderCalculator.NextReminder nextReminder = null;
        if (!fastStartupList) {
            for (Reminder reminder : reminders) {
                NextReminderCalculator.NextReminder candidate = NextReminderCalculator.nextForReminder(this, reminder, snoozeStore, eventStore, true);
                nextByReminder.put(reminder.id, candidate);
                if (candidate != null && (nextReminder == null || candidate.scheduledAt < nextReminder.scheduledAt)) {
                    nextReminder = candidate;
                }
            }
        }
        String nextReminderId = nextReminder == null ? null : nextReminder.reminderId;
        if (!fastStartupList && nextReminderId != null) {
            moveReminderToTop(reminders, nextReminderId);
        }
        boolean shouldFocus = pendingFocusNextReminder || pendingFocusReminderId != null;
        String focusReminderId = pendingFocusReminderId;
        if (shouldFocus && (pendingFocusNextReminder || focusReminderId == null || !containsReminder(reminders, focusReminderId))) {
            focusReminderId = nextReminderId;
        }
        final View[] focusTarget = new View[1];
        if (reminders.isEmpty()) {
            content.addView(emptyState("אין תזכורות"));
        }

        int renderedReminders = 0;
        int maxInitialReminders = fastStartupList ? 6 : Integer.MAX_VALUE;
        for (Reminder reminder : reminders) {
            if (renderedReminders >= maxInitialReminders) {
                break;
            }
            renderedReminders++;
            long snoozeAt = NextReminderCalculator.pendingSnoozeAt(reminder.id, snoozeStore);
            boolean next = reminder.id.equals(nextReminderId);
            LinearLayout card = card(next);
            card.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            card.setBackground(new GlowingReminderCardDrawable(dp(18), next));
            card.setOnClickListener(v -> showEditor(reminder));
            card.setOnLongClickListener(v -> {
                showReminderActions(reminder);
                return true;
            });

            TextView time = text(reminder.useZmanim ? ZmanimHelper.label(reminder.zmanimKey) : formatTime(reminder.hour, reminder.minute), 24, reminder.enabled ? COLOR_ACCENT : COLOR_MUTED);
            AppFont.bold(time);
            TextView name = text(reminder.name, 16, COLOR_TEXT);
            AppFont.bold(name);
            TextView description = text(reminder.description, 12, COLOR_MUTED);
            description.setPadding(dp(8), dp(2), dp(8), 0);
            TextView details = text(fastStartupList ? reminderDetailsFast(reminder) : reminderDetails(reminder, nextByReminder.get(reminder.id)), 12, COLOR_MUTED);
            details.setPadding(0, dp(2), 0, 0);
            TextView nextTime = text(fastStartupList ? "הבא: מחשב..." : nextReminderLine(reminder, nextByReminder.get(reminder.id)), 11, next ? COLOR_WARNING : COLOR_MUTED);
            nextTime.setPadding(0, dp(3), 0, 0);
            if (next) {
                TextView nextLabel = text("ההתראה הקרובה", 11, COLOR_WARNING);
                AppFont.bold(nextLabel);
                nextLabel.setPadding(0, 0, 0, dp(3));
                card.addView(nextLabel);
            }
            card.addView(time);
            card.addView(name);
            if (!reminder.description.isEmpty()) {
                card.addView(description);
            }
            card.addView(details);
            card.addView(nextTime);
            Switch activeSwitch = activeSwitch(reminder);
            card.addView(activeSwitch);
            boolean blessingReminder = isBlessingReminder(reminder);
            boolean showQuickActions = !fastStartupList && reminder.enabled && (next || snoozeAt != Long.MAX_VALUE || blessingReminder);
            if (showQuickActions) {
                LinearLayout quickActions = actionRow();
                quickActions.setPadding(0, dp(3), 0, 0);
                Button done = smallWideButton("בוצע", COLOR_ACCENT_DARK);
                done.setOnClickListener(v -> completeUpcoming(reminder));
                quickActions.addView(done);
                if (next || snoozeAt != Long.MAX_VALUE) {
                    Button snooze = smallWideButton("דחה", COLOR_SURFACE_2);
                    snooze.setOnClickListener(v -> showSnoozeUpcomingOptions(reminder));
                    quickActions.addView(snooze);
                }
                card.addView(quickActions);
            }

            content.addView(card, cardParams());
            if (focusReminderId != null && focusReminderId.equals(reminder.id)) {
                focusTarget[0] = card;
            }
        }
        if (fastStartupList && reminders.size() > renderedReminders) {
            TextView loadingMore = infoPill("טוען עוד תזכורות...", COLOR_MUTED);
            content.addView(loadingMore);
        }
        LinearLayout settingsRow = actionRow();
        settingsRow.setPadding(0, dp(10), 0, dp(2));
        Button settingsButton = pillButton("הגדרות", COLOR_SURFACE_2);
        settingsButton.setOnClickListener(v -> showSettings());
        settingsRow.addView(settingsButton);
        content.addView(settingsRow);
        setScrollableContent(content);
        if (shouldFocus) {
            scrollToFocusedReminder(focusTarget[0]);
        }
        rememberReminderListFingerprint();
    }

    private boolean shouldUseFastStartupList() {
        if (startupMaintenanceDone
                || System.currentTimeMillis() - createdAt >= 5_000L
                || pendingFocusReminderId != null
                || pendingFocusNextReminder
                || !"list".equals(currentScreen)) {
            return false;
        }
        if (startupListPass < 2) {
            startupListPass++;
            return true;
        }
        return true;
    }

    private boolean containsReminder(List<Reminder> reminders, String reminderId) {
        for (Reminder reminder : reminders) {
            if (reminder.id.equals(reminderId)) {
                return true;
            }
        }
        return false;
    }

    private void moveReminderToTop(List<Reminder> reminders, String reminderId) {
        for (int i = 0; i < reminders.size(); i++) {
            if (reminders.get(i).id.equals(reminderId)) {
                if (i > 0) {
                    reminders.add(0, reminders.remove(i));
                }
                return;
            }
        }
    }

    private boolean isBlessingReminder(Reminder reminder) {
        if (reminder == null || !reminder.isOneTime() || !reminder.critical) {
            return false;
        }
        String name = reminder.name == null ? "" : reminder.name;
        String[] blessingPrefixes = {
                "אשר יצר",
                "Asher Yatzar",
                "קריאת שמע בזמנה",
                "Shema on Time",
                "בורא נפשות",
                "Borei Nefashot",
                "מעין שלוש - על המחיה",
                "Me'ein Shalosh - Al Hamichya",
                "מעין שלוש - על הגפן",
                "Me'ein Shalosh - Al Hagefen",
                "מעין שלוש - על העץ",
                "Me'ein Shalosh - Al Ha'etz",
                "ברכת המזון",
                "Birkat Hamazon"
        };
        for (String prefix : blessingPrefixes) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void showList(int scrollY) {
        showList();
        restoreScrollY(scrollY);
    }

    private void showHistory() {
        currentScreen = "history";
        LinearLayout content = baseContent();
        addOutlinedTitle(content, "היסטוריה", "לחיצה ארוכה על תזכורת פותחת פעולות אפשריות");

        LinearLayout topActions = actionRow();
        Button back = pillButton("חזרה", COLOR_SURFACE_2);
        back.setOnClickListener(v -> showList());

        ReminderEventStore eventStore = new ReminderEventStore(this);
        List<ReminderEventStore.Event> events = eventStore.getAll();
        if (events.isEmpty()) {
            topActions.addView(back);
            content.addView(topActions);
            TextView empty = shadowedText("אין התראות שחלפו", 15, COLOR_MUTED);
            empty.setPadding(0, dp(28), 0, 0);
            content.addView(empty);
        } else {
            Button clearHistory = pillButton("ניקוי הכל", 0xFF7E2A35);
            clearHistory.setOnClickListener(v -> clearHistoryInBackground());
            topActions.addView(clearHistory);
            topActions.addView(back);
            content.addView(topActions);
        }

        for (ReminderEventStore.Event event : events) {
            LinearLayout card = card();
            card.setOnClickListener(v -> showHistoryActions(eventStore, event));
            card.setOnLongClickListener(v -> {
                showHistoryActions(eventStore, event);
                return true;
            });

            TextView name = text(event.reminderName, 15, COLOR_TEXT);
            AppFont.bold(name);
            String eventDescription = historyDescription(event);
            TextView description = text(eventDescription, 12, COLOR_MUTED);
            description.setPadding(dp(8), dp(2), dp(8), 0);
            TextView status = text(event.status, 14, eventStatusColor(event.status));
            TextView time = text(event.displayTime() + (event.note.isEmpty() ? "" : " | " + UiText.t(this, event.note)), 12, COLOR_MUTED);
            card.addView(name);
            if (!eventDescription.isEmpty()) {
                card.addView(description);
            }
            card.addView(status);
            card.addView(time);
            if (!ReminderEventStore.STATUS_DONE.equals(event.status)) {
                TextView nextTime = text(nextHistoryLine(event), 11, COLOR_WARNING);
                nextTime.setPadding(0, dp(3), 0, 0);
                card.addView(nextTime);
            } else if (canUndoEarlyDone(event)) {
                Button undoDone = smallWideButton("ביטול בוצע", COLOR_SURFACE_2);
                undoDone.setOnClickListener(v -> undoEarlyDone(eventStore, event));
                card.addView(undoDone);
            }

            content.addView(card, cardParams());
        }

        setScrollableContent(content);
    }

    private void addEditorHelp(
            FrameLayout root,
            Button help,
            View name,
            View description,
            View state,
            View recurringType,
            View oneTimeType,
            View periodicType,
            View annualType,
            View zmanim
    ) {
        help.setOnClickListener(view -> {
            hideKeyboard(nameInput);
            help.setVisibility(View.INVISIBLE);
            List<ScreenHelpTour.Step> steps = new java.util.ArrayList<>();
            steps.add(new ScreenHelpTour.Step(
                    name,
                    getString(R.string.ui_editor_help_name_title),
                    getString(R.string.ui_editor_help_name_text)
            ));
            steps.add(new ScreenHelpTour.Step(
                    description,
                    getString(R.string.ui_editor_help_description_title),
                    getString(R.string.ui_editor_help_description_text)
            ));
            steps.add(new ScreenHelpTour.Step(
                    state,
                    getString(R.string.ui_editor_help_status_title),
                    getString(R.string.ui_editor_help_status_text)
            ));
            steps.add(new ScreenHelpTour.Step(
                    oneTimeType,
                    getString(R.string.ui_editor_help_one_time_title),
                    getString(R.string.ui_editor_help_one_time_text)
            ));
            steps.add(new ScreenHelpTour.Step(
                    recurringType,
                    getString(R.string.ui_editor_help_fixed_title),
                    getString(R.string.ui_editor_help_fixed_text)
            ));
            steps.add(new ScreenHelpTour.Step(
                    periodicType,
                    getString(R.string.ui_editor_help_periodic_title),
                    getString(R.string.ui_editor_help_periodic_text)
            ));
            steps.add(new ScreenHelpTour.Step(
                    annualType,
                    getString(R.string.ui_editor_help_annual_title),
                    getString(R.string.ui_editor_help_annual_text)
            ));
            if (zmanim != null) {
                steps.add(new ScreenHelpTour.Step(
                        zmanim,
                        getString(R.string.ui_editor_help_zmanim_title),
                        getString(R.string.ui_editor_help_zmanim_text)
                ));
            }
            new ScreenHelpTour(
                    root,
                    activeScrollView,
                    steps,
                    getString(R.string.ui_help_previous),
                    getString(R.string.ui_help_next),
                    getString(R.string.ui_help_finish),
                    getString(R.string.ui_help_close),
                    () -> help.setVisibility(View.VISIBLE)
            ).start();
        });
    }

    private void startEditorHelpAutomaticallyIfNeeded(Button help) {
        SharedPreferences preferences = getSharedPreferences(EDITOR_HELP_PREFS, MODE_PRIVATE);
        if (preferences.getBoolean(EDITOR_HELP_SHOWN, false)) {
            return;
        }
        preferences.edit().putBoolean(EDITOR_HELP_SHOWN, true).apply();
        help.postDelayed(help::performClick, 500L);
    }

    private void clearHistoryInBackground() {
        currentScreen = "history";
        LinearLayout content = baseContent();
        addTitle(content, "היסטוריה", "");
        LinearLayout loadingCard = card();
        ProgressBar progress = new ProgressBar(this);
        loadingCard.addView(progress);
        TextView loadingText = text("מנקה היסטוריה...", 13, COLOR_MUTED);
        loadingText.setPadding(0, dp(8), 0, 0);
        loadingCard.addView(loadingText);
        content.addView(loadingCard, cardParams());
        setScrollableContent(content);

        new Thread(() -> {
            try {
                new ReminderEventStore(MainActivity.this).clear();
                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this, UiText.t(MainActivity.this, "ההיסטוריה נמחקה"), Toast.LENGTH_SHORT).show();
                    showHistory();
                });
            } catch (Exception exception) {
                AppLog.e(MainActivity.this, "clear history failed", exception);
                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this, UiText.t(MainActivity.this, "לא הצלחתי לנקות את ההיסטוריה"), Toast.LENGTH_SHORT).show();
                    showHistory();
                });
            }
        }, "wr-clear-history").start();
    }

    private void showHistory(int scrollY) {
        showHistory();
        restoreScrollY(scrollY);
    }

    private void showSmartAlarmSettings() {
        stopSmartAlarmPreview();
        editingSmartAlarmId = 0;
        editingSmartAlarmNew = false;
        currentScreen = "smart_alarm_list";
        LinearLayout content = baseContent();
        addTitle(content, "שעונים מעוררים חכמים", "");
        ArrayList<Integer> alarmIds = SmartAlarmStore.ids(this);
        if (alarmIds.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("עדיין לא הוגדר שעון מעורר חכם", 13, COLOR_CARD_MUTED));
            content.addView(empty, cardParams());
        }
        for (int alarmId : alarmIds) {
            SmartAlarmStore alarm = new SmartAlarmStore(this, alarmId);
            LinearLayout alarmCard = card(true);
            TextView time = text(String.format(Locale.US, "%02d:%02d", alarm.hour(), alarm.minute()), 25, COLOR_CARD_TEXT);
            AppFont.bold(time);
            alarmCard.addView(time);
            TextView days = text(smartAlarmDaysLabel(alarm.daysMask()), 11, COLOR_CARD_MUTED);
            alarmCard.addView(days);
            Switch enabled = new Switch(this);
            setSwitchText(enabled, "פעיל");
            enabled.setChecked(alarm.enabled());
            enabled.setOnClickListener(v -> {
                alarm.setEnabled(enabled.isChecked());
                SmartAlarmScheduler.reschedule(this, alarmId);
            });
            alarmCard.addView(enabled);
            Button edit = pillButton("עריכה", COLOR_SURFACE_2);
            edit.setOnClickListener(v -> showSmartAlarmEditor(alarmId, false));
            alarmCard.addView(edit);
            content.addView(alarmCard, cardParams());
        }
        Button add = pillButton("הוספת שעון מעורר", COLOR_ACCENT_DARK);
        add.setOnClickListener(v -> {
            int alarmId = SmartAlarmStore.create(this);
            showSmartAlarmEditor(alarmId, true);
        });
        content.addView(add, matchParams());
        Button back = pillButton("חזרה", COLOR_SURFACE_2);
        back.setOnClickListener(v -> showList());
        content.addView(back, matchParams());
        content.addView(new View(this), new LinearLayout.LayoutParams(1, dp(16)));
        setScrollableContent(content);
    }

    private String smartAlarmDaysLabel(int mask) {
        String[] labels = AppLanguage.isEnglish(this)
                ? new String[]{"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"}
                : new String[]{"א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳"};
        ArrayList<String> selected = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            if ((mask & (1 << (Calendar.SUNDAY + index))) != 0) selected.add(labels[index]);
        }
        return android.text.TextUtils.join(" · ", selected);
    }

    private void showSmartAlarmEditor(int alarmId, boolean newAlarm) {
        stopSmartAlarmPreview();
        currentScreen = "smart_alarm_editor";
        editingSmartAlarmId = alarmId;
        editingSmartAlarmNew = newAlarm;
        SmartAlarmStore smart = new SmartAlarmStore(this, alarmId);
        final String[] selectedSoundUri = {smart.soundUri()};
        LinearLayout content = baseContent();
        addTitle(content, "שעון מעורר חכם", "");

        LinearLayout enabledCard = card(true);
        Switch enabledSwitch = new Switch(this);
        setSwitchText(enabledSwitch, "שעון מעורר פעיל");
        enabledSwitch.setChecked(smart.enabled());
        enabledCard.addView(enabledSwitch);
        content.addView(enabledCard, cardParams());

        LinearLayout timeCard = card();
        TextView timeTitle = text("שעת השכמה", 15, COLOR_CARD_TEXT);
        AppFont.bold(timeTitle);
        timeCard.addView(timeTitle);
        NumberPicker hourPicker = numberPicker(0, 23, smart.hour());
        NumberPicker minutePicker = numberPicker(0, 59, smart.minute());
        timeCard.addView(timePickerRow(hourPicker, minutePicker));
        content.addView(timeCard, cardParams());

        final int[] selectedDaysMask = {smart.daysMask()};
        LinearLayout daysCard = card();
        TextView daysTitle = text("ימי פעילות", 15, COLOR_CARD_TEXT);
        AppFont.bold(daysTitle);
        daysCard.addView(daysTitle);
        String[] dayLabels = AppLanguage.isEnglish(this)
                ? new String[]{"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"}
                : new String[]{"א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳"};
        LinearLayout firstDays = compactDayRow();
        LinearLayout secondDays = compactDayRow();
        for (int index = 0; index < 7; index++) {
            int calendarDay = Calendar.SUNDAY + index;
            Button day = dayButton(dayLabels[index], COLOR_SURFACE_2);
            styleDayButton(day, (selectedDaysMask[0] & (1 << calendarDay)) != 0);
            day.setOnClickListener(v -> {
                selectedDaysMask[0] ^= 1 << calendarDay;
                styleDayButton(day, (selectedDaysMask[0] & (1 << calendarDay)) != 0);
            });
            (index < 4 ? firstDays : secondDays).addView(day);
        }
        daysCard.addView(firstDays);
        daysCard.addView(secondDays);
        content.addView(daysCard, cardParams());

        LinearLayout smartWindowCard = card();
        TextView smartWindowTitle = text("חלון השכמה חכם", 15, COLOR_CARD_TEXT);
        AppFont.bold(smartWindowTitle);
        smartWindowCard.addView(smartWindowTitle);
        NumberPicker windowPicker = numberPicker(5, 60, smart.windowMinutes());
        smartWindowCard.addView(pickerColumn("דקות לפני שעת ההשכמה", windowPicker));
        content.addView(smartWindowCard, cardParams());

        LinearLayout snoozeCard = card();
        TextView snoozeTitle = text("נודניק", 15, COLOR_CARD_TEXT);
        AppFont.bold(snoozeTitle);
        snoozeCard.addView(snoozeTitle);
        LinearLayout snoozeRow = new LinearLayout(this);
        snoozeRow.setGravity(Gravity.CENTER);
        snoozeRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        NumberPicker snoozeInterval = numberPicker(1, 30, smart.snoozeMinutes());
        NumberPicker snoozeCount = numberPicker(0, 10, smart.snoozeCount());
        snoozeRow.addView(pickerColumn("כל כמה דקות", snoozeInterval));
        snoozeRow.addView(pickerColumn("מספר פעמים", snoozeCount));
        snoozeCard.addView(snoozeRow);
        TextView snoozeHint = text("0 פעמים מבטל את אפשרות הנודניק", 10, COLOR_CARD_MUTED);
        snoozeCard.addView(snoozeHint);
        content.addView(snoozeCard, cardParams());

        LinearLayout feedbackCard = card();
        TextView feedbackTitle = text("רטט וצלצול", 15, COLOR_CARD_TEXT);
        AppFont.bold(feedbackTitle);
        feedbackCard.addView(feedbackTitle);

        Switch vibrationSwitch = new Switch(this);
        setSwitchText(vibrationSwitch, "רטט");
        vibrationSwitch.setChecked(smart.vibrationEnabled());
        feedbackCard.addView(vibrationSwitch);
        String[] vibrationLabels = {"רגיל", "עדין", "חזק", "ארוך"};
        String[] vibrationValues = {SmartAlarmStore.VIBRATION_NORMAL, SmartAlarmStore.VIBRATION_GENTLE,
                SmartAlarmStore.VIBRATION_STRONG, SmartAlarmStore.VIBRATION_LONG};
        Spinner vibrationSpinner = new Spinner(this);
        vibrationSpinner.setAdapter(spinnerAdapter(translated(vibrationLabels)));
        vibrationSpinner.setSelection(indexOf(vibrationValues, smart.vibrationStyle()));
        feedbackCard.addView(vibrationSpinner, matchParams());
        LevelControl vibrationStrength = levelControl("עוצמת רטט", 9, smart.vibrationStrength() - 1);
        feedbackCard.addView(vibrationStrength.view, wideLevelParams());

        final boolean[] initialVibrationSelection = {true};
        android.widget.AdapterView.OnItemSelectedListener vibrationPreviewListener = new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (initialVibrationSelection[0]) {
                    initialVibrationSelection[0] = false;
                    return;
                }
                if (vibrationSwitch.isChecked()) {
                    previewSmartAlarm(vibrationValues[vibrationSpinner.getSelectedItemPosition()],
                            vibrationStrength.slider.getProgress() + 1, false, 0, selectedSoundUri[0]);
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        };
        vibrationSpinner.setOnItemSelectedListener(vibrationPreviewListener);
        vibrationStrength.slider.setOnSeekBarChangeListener(levelPreviewListener(vibrationStrength, progress -> {
            if (vibrationSwitch.isChecked()) {
                previewSmartAlarm(vibrationValues[vibrationSpinner.getSelectedItemPosition()],
                        progress + 1, false, 0, selectedSoundUri[0]);
            }
        }));
        vibrationSwitch.setOnClickListener(v -> {
            if (vibrationSwitch.isChecked()) {
                previewSmartAlarm(vibrationValues[vibrationSpinner.getSelectedItemPosition()],
                        vibrationStrength.slider.getProgress() + 1, false, 0, selectedSoundUri[0]);
            } else {
                stopSmartAlarmPreview();
            }
        });

        Switch soundSwitch = new Switch(this);
        setSwitchText(soundSwitch, "צלצול");
        soundSwitch.setChecked(smart.soundEnabled());
        feedbackCard.addView(soundSwitch);
        TextView ringtoneValue = text(ringtoneTitle(selectedSoundUri[0]), 11, COLOR_CARD_MUTED);
        feedbackCard.addView(ringtoneValue);
        final LevelControl[] volumeControlRef = {null};
        Button chooseRingtone = pillButton("בחירת צלצול", COLOR_SURFACE_2);
        chooseRingtone.setOnClickListener(v -> showSmartAlarmRingtoneScreen(selectedSoundUri[0],
                volumeControlRef[0] == null ? smart.soundVolumePercent() : volumeControlRef[0].slider.getProgress() * 10,
                (uri, title) -> {
            selectedSoundUri[0] = uri;
            ringtoneValue.setText(title);
        }));
        feedbackCard.addView(chooseRingtone);
        LevelControl soundVolume = levelControl("עוצמת צלצול", 10, smart.soundVolumePercent() / 10);
        volumeControlRef[0] = soundVolume;
        feedbackCard.addView(soundVolume.view, wideLevelParams());
        soundVolume.slider.setOnSeekBarChangeListener(levelPreviewListener(soundVolume, progress -> {
            if (soundSwitch.isChecked()) {
                previewSmartAlarm(vibrationValues[vibrationSpinner.getSelectedItemPosition()],
                        vibrationStrength.slider.getProgress() + 1, true, progress * 10, selectedSoundUri[0]);
            }
        }));
        soundSwitch.setOnClickListener(v -> {
            if (soundSwitch.isChecked()) {
                previewSmartAlarm(vibrationValues[vibrationSpinner.getSelectedItemPosition()],
                        vibrationStrength.slider.getProgress() + 1, true,
                        soundVolume.slider.getProgress() * 10, selectedSoundUri[0]);
            } else {
                stopSmartAlarmPreview();
            }
        });
        NumberPicker durationPicker = numberPicker(5, 120, smart.alertDurationSeconds());
        feedbackCard.addView(pickerColumn("משך ההתראה בשניות", durationPicker));
        content.addView(feedbackCard, cardParams());

        LinearLayout actions = actionRow();
        Button save = pillButton("שמירה", COLOR_ACCENT_DARK);
        save.setOnClickListener(v -> {
            if (selectedDaysMask[0] == 0 && enabledSwitch.isChecked()) {
                Toast.makeText(this, UiText.t(this, "צריך לבחור לפחות יום אחד"), Toast.LENGTH_SHORT).show();
                return;
            }
            smart.save(enabledSwitch.isChecked(), hourPicker.getValue(), minutePicker.getValue(),
                    selectedDaysMask[0], windowPicker.getValue(), snoozeInterval.getValue(), snoozeCount.getValue(),
                    vibrationSwitch.isChecked(), vibrationValues[vibrationSpinner.getSelectedItemPosition()],
                    vibrationStrength.slider.getProgress() + 1, soundSwitch.isChecked(), soundVolume.slider.getProgress() * 10,
                    selectedSoundUri[0], durationPicker.getValue());
            stopSmartAlarmPreview();
            SmartAlarmScheduler.reschedule(this, alarmId);
            Toast.makeText(this, UiText.t(this, "השעון המעורר נשמר"), Toast.LENGTH_SHORT).show();
            showSmartAlarmSettings();
        });
        Button back = pillButton("חזרה", COLOR_SURFACE_2);
        back.setOnClickListener(v -> {
            stopSmartAlarmPreview();
            if (newAlarm) SmartAlarmStore.delete(this, alarmId);
            showSmartAlarmSettings();
        });
        actions.addView(save);
        actions.addView(back);
        content.addView(actions);
        if (!newAlarm) {
            Button delete = pillButton("מחיקת שעון מעורר", 0xFF7E2A35);
            delete.setOnClickListener(v -> {
                SmartAlarmScheduler.cancel(this, alarmId);
                SmartAlarmStore.delete(this, alarmId);
                showSmartAlarmSettings();
            });
            content.addView(delete, matchParams());
        }
        View bottomSafeArea = new View(this);
        content.addView(bottomSafeArea, new LinearLayout.LayoutParams(dp(1), dp(16)));
        setScrollableContent(content);
    }

    private void showSettings() {
        currentScreen = "settings";
        ReminderSettings settings = new ReminderSettings(this);
        LinearLayout content = baseContent();
        addTitle(content, "הגדרות", "");

        LinearLayout languageCard = card();
        TextView languageTitle = text("שפה", 15, COLOR_TEXT);
        AppFont.bold(languageTitle);
        languageCard.addView(languageTitle);
        TextView languageHint = text("שפת הממשק", 11, COLOR_MUTED);
        languageHint.setPadding(0, dp(3), 0, dp(5));
        languageCard.addView(languageHint);
        String[] languageLabels = {"אוטומטי לפי השעון", "עברית", "אנגלית"};
        String[] languageValues = {
                ReminderSettings.LANGUAGE_AUTO,
                ReminderSettings.LANGUAGE_HEBREW,
                ReminderSettings.LANGUAGE_ENGLISH
        };
        Spinner languageSpinner = new Spinner(this);
        ArrayAdapter<String> languageAdapter = spinnerAdapter(translated(languageLabels));
        languageSpinner.setAdapter(languageAdapter);
        languageSpinner.setSelection(indexOf(languageValues, settings.language()));
        final boolean[] languageSelectionReady = {false};
        languageSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (!languageSelectionReady[0]) {
                    languageSelectionReady[0] = true;
                    return;
                }
                String selectedLanguage = languageValues[position];
                if (selectedLanguage.equals(settings.language())) {
                    return;
                }
                settings.setLanguage(selectedLanguage);
                if (settings.jewishMode()) {
                    settings.setJewishDayRemindersEnabled(true);
                    settings.setTekufaRemindersEnabled(true);
                    JewishDayScheduler.schedule(MainActivity.this);
                    TekufaScheduler.schedule(MainActivity.this);
                } else {
                    JewishDayScheduler.cancel(MainActivity.this);
                    JewishDayReceiver.cancelNotification(MainActivity.this);
                    TekufaScheduler.cancel(MainActivity.this);
                    TekufaReceiver.cancelNotification(MainActivity.this);
                }
                recreate();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        languageCard.addView(languageSpinner, matchParams());
        content.addView(languageCard, cardParams());

        LinearLayout jewishModeCard = card();
        Switch jewishModeSwitch = new Switch(this);
        setSwitchText(jewishModeSwitch, "מצב יהודי");
        jewishModeSwitch.setChecked(settings.jewishMode());
        jewishModeSwitch.setOnClickListener(v -> applyJewishModeChange(jewishModeSwitch.isChecked()));
        jewishModeCard.addView(jewishModeSwitch);
        TextView jewishModeHint = text("מציג תאריך עברי, זמני הלכה, ברכות, דף יומי וספירת העומר", 11, COLOR_MUTED);
        jewishModeHint.setPadding(0, dp(4), 0, 0);
        jewishModeCard.addView(jewishModeHint);
        content.addView(jewishModeCard, cardParams());

        if (settings.jewishMode()) {
            Button jewishSettings = pillButton("זמנים יהודיים", COLOR_SURFACE_2);
            jewishSettings.setOnClickListener(v -> showJewishSettings());
            content.addView(jewishSettings, matchParams());
        }
        Button alertSettings = pillButton("רטט וצלילים", COLOR_SURFACE_2);
        alertSettings.setOnClickListener(v -> showAlertSettings());
        content.addView(alertSettings, matchParams());

        Button advancedSettings = pillButton("הגדרות מתקדמות", COLOR_SURFACE_2);
        advancedSettings.setOnClickListener(v -> showAdvancedSettings());
        content.addView(advancedSettings, matchParams());

        Button fastingSettings = pillButton("צום לסירוגין", COLOR_SURFACE_2);
        fastingSettings.setOnClickListener(v -> showFastingSettings());
        content.addView(fastingSettings, matchParams());

        QuietTimeRuleStore quietStore = new QuietTimeRuleStore(this);
        LinearLayout quietCard = card();
        Switch quietSwitch = new Switch(this);
        setSwitchText(quietSwitch, "זמני שקט - לא להפריע");
        quietSwitch.setChecked(settings.quietMinchaMaariv());
        quietCard.addView(quietSwitch);
        TextView quietHint = text(quietTimesSummary(quietStore), 11, COLOR_MUTED);
        quietHint.setPadding(0, dp(4), 0, 0);
        quietCard.addView(quietHint);
        quietCard.setOnClickListener(v -> showQuietTimes());
        quietSwitch.setOnClickListener(v -> {
            boolean enabled = quietSwitch.isChecked();
            settings.setQuietMinchaMaariv(enabled);
            if (enabled) {
                quietStore.ensureDefaultMinchaMaarivRule();
                showQuietTimes();
            } else {
                store.rescheduleAll();
                ComplicationRefresh.request(this);
                quietHint.setText(quietTimesSummary(quietStore));
            }
        });
        content.addView(quietCard, cardParams());

        LinearLayout backupCard = card();
        TextView backupTitle = text("גיבוי ושחזור", 15, COLOR_TEXT);
        AppFont.bold(backupTitle);
        backupCard.addView(backupTitle);
        TextView backupHint = text("גיבוי ושחזור מתבצעים דרך אפליקציית הטלפון", 11, COLOR_MUTED);
        backupHint.setPadding(0, dp(3), 0, dp(6));
        backupCard.addView(backupHint);
        LinearLayout backupActions = actionRow();
        Button backupToPhone = pillButton("גיבוי לטלפון", COLOR_ACCENT_DARK);
        backupToPhone.setText(AppLanguage.isEnglish(this) ? "Back Up\nto Phone" : "גיבוי\nלטלפון");
        backupToPhone.setOnClickListener(v -> sendBackupToPhone());
        Button restoreFromPhone = pillButton("שחזור מהטלפון", COLOR_SURFACE_2);
        restoreFromPhone.setOnClickListener(v -> showRestoreFromPhoneStatus());
        backupActions.addView(backupToPhone);
        backupActions.addView(restoreFromPhone);
        backupCard.addView(backupActions);
        content.addView(backupCard, cardParams());

        Button licenses = pillButton("אודות ורישיונות", COLOR_SURFACE_2);
        licenses.setTextSize(12);
        licenses.setOnClickListener(v -> showAboutAndLicenses());
        content.addView(licenses, matchParams());

        LinearLayout actions = actionRow();
        Button back = pillButton("חזרה", COLOR_SURFACE_2);
        back.setOnClickListener(v -> showList());
        actions.addView(back);
        content.addView(actions);

        setScrollableContent(content);
    }

    private void showLanguageOnboarding() {
        currentScreen = "onboarding_language";
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(18), dp(55), dp(18), dp(18));
        content.setBackgroundColor(COLOR_BG);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER);
        titleRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        TextView englishTitle = outlinedExactText("Language", 17, COLOR_TEXT, View.TEXT_DIRECTION_LTR);
        TextView titleSeparator = outlinedExactText(" · ", 17, COLOR_TEXT, View.TEXT_DIRECTION_LTR);
        TextView hebrewTitle = outlinedExactText("שפה", 17, COLOR_TEXT, View.TEXT_DIRECTION_RTL);
        AppFont.bold(englishTitle);
        AppFont.bold(titleSeparator);
        AppFont.bold(hebrewTitle);
        titleRow.addView(englishTitle);
        titleRow.addView(titleSeparator);
        titleRow.addView(hebrewTitle);
        content.addView(titleRow, matchParams());
        TextView englishHint = outlinedExactText(
                "You can change this later in Settings",
                12,
                COLOR_BG,
                View.TEXT_DIRECTION_LTR);
        englishHint.setPadding(0, dp(10), 0, 0);
        content.addView(englishHint, matchParams());
        TextView hebrewHint = outlinedExactText(
                "אפשר לשנות את השפה גם בהגדרות",
                12,
                COLOR_BG,
                View.TEXT_DIRECTION_RTL);
        hebrewHint.setPadding(0, 0, 0, dp(18));
        content.addView(hebrewHint, matchParams());
        Button english = pillButton("English", COLOR_SURFACE_2);
        english.setOnClickListener(v -> {
            new ReminderSettings(this).setLanguage(ReminderSettings.LANGUAGE_ENGLISH);
            recreate();
        });
        content.addView(english, matchParams());
        Button hebrew = pillButton("עברית", COLOR_ACCENT_DARK);
        hebrew.setText("עברית");
        hebrew.setOnClickListener(v -> {
            ReminderSettings settings = new ReminderSettings(this);
            settings.setLanguage(ReminderSettings.LANGUAGE_HEBREW);
            settings.setJewishMode(true);
            completeOnboarding();
        });
        content.addView(hebrew, matchParams());
        setScrollableContent(content);
    }

    private void showJewishModeOnboarding() {
        currentScreen = "onboarding_jewish_mode";
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(18), dp(55), dp(18), dp(18));
        content.setBackgroundColor(COLOR_BG);
        TextView title = text("Jewish Mode", 20, COLOR_TEXT);
        title.setGravity(Gravity.CENTER);
        AppFont.bold(title);
        content.addView(title, matchParams());
        TextView hint = outlinedText(
                "Would you like Hebrew dates, halachic times, blessings, Daf Yomi, and Omer features?",
                12,
                COLOR_BG);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(10), 0, dp(18));
        content.addView(hint, matchParams());
        Button enable = pillButton("Enable Jewish Mode", COLOR_ACCENT_DARK);
        enable.setOnClickListener(v -> {
            applyOnboardingJewishMode(true);
            completeOnboarding();
        });
        content.addView(enable, matchParams());
        Button skip = pillButton("Not now", COLOR_SURFACE_2);
        skip.setOnClickListener(v -> {
            applyOnboardingJewishMode(false);
            completeOnboarding();
        });
        content.addView(skip, matchParams());
        setScrollableContent(content);
    }

    private void applyOnboardingJewishMode(boolean enabled) {
        ReminderSettings settings = new ReminderSettings(this);
        settings.setJewishMode(enabled);
        if (enabled) {
            settings.setJewishDayRemindersEnabled(true);
            settings.setTekufaRemindersEnabled(true);
        }
    }

    private void completeOnboarding() {
        new ReminderSettings(this).setOnboardingComplete(true);
        store.rescheduleAll();
        JewishDayScheduler.schedule(this);
        TekufaScheduler.schedule(this);
        recreate();
    }

    private void showAboutAndLicenses() {
        currentScreen = "about_licenses";
        LinearLayout content = baseContent();
        addTitle(content, "אודות ורישיונות", "רכיבי צד שלישי ב-Zmanio");

        LinearLayout intro = card(true);
        TextView appName = text("Zmanio", 17, COLOR_CARD_TEXT);
        AppFont.bold(appName);
        appName.setGravity(Gravity.CENTER);
        intro.addView(appName);
        TextView explanation = text("האפליקציה משתמשת בתוכנות קוד פתוח וברכיבי צד שלישי. תודה ליוצרים ולתורמים שלהם.", 11, COLOR_CARD_MUTED);
        explanation.setGravity(Gravity.CENTER);
        explanation.setPadding(0, dp(5), 0, 0);
        intro.addView(explanation);
        content.addView(intro, cardParams());

        addLicenseCard(content, "KosherJava Zmanim 2.5.0",
                "Copyright © Eliyahu Hershfeld and contributors\nGNU Lesser General Public License 2.1",
                "https://github.com/KosherJava/zmanim", "licenses/lgpl-2.1.txt");
        addLicenseCard(content, "Frank Ruhl Libre",
                "Copyright 2015 The Frank Ruhl Libre Project Authors\nSIL Open Font License 1.1",
                "https://github.com/fontef/frankruhllibre", "licenses/frank_ruhl_libre_ofl.txt");
        addLicenseCard(content, "AndroidX / Wear OS / Health Services",
                "AndroidX libraries, Wear Watch Face complications and Health Services Client\nApache License 2.0",
                "https://source.android.com/docs/setup/about/licenses", "licenses/apache-2.0.txt");
        addLicenseCard(content, "Google Guava 33.4.0",
                "Guava and its Google helper annotations\nApache License 2.0",
                "https://github.com/google/guava", "licenses/apache-2.0.txt");
        addLicenseCard(content, "Kotlin & kotlinx.coroutines",
                "Kotlin standard library, coroutines and JetBrains annotations\nApache License 2.0",
                "https://github.com/JetBrains/kotlin", "licenses/apache-2.0.txt");
        addLicenseCard(content, "Additional open-source annotations",
                "JSR-305, Checker Framework annotations and JSpecify\nBSD 3-Clause, MIT and Apache 2.0 licenses",
                "https://github.com/jspecify/jspecify", "licenses/additional_notices.txt");
        addLicenseCard(content, "Google Play services for Wear OS 18.0.0",
                "Google Play services wearable APIs\nGoogle APIs Terms of Service",
                "https://developers.google.com/terms", "licenses/google_play_services_notice.txt");

        Button back = pillButton("חזרה", COLOR_SURFACE_2);
        back.setOnClickListener(v -> showSettings());
        content.addView(back, matchParams());
        View bottomSafeArea = new View(this);
        content.addView(bottomSafeArea, new LinearLayout.LayoutParams(dp(1), dp(6)));
        setScrollableContent(content);
    }

    private void addLicenseCard(LinearLayout content, String titleValue, String detailsValue,
                                String sourceUrl, String assetPath) {
        LinearLayout licenseCard = card();
        TextView title = text(titleValue, 14, COLOR_CARD_TEXT);
        AppFont.bold(title);
        licenseCard.addView(title);
        TextView details = text(detailsValue, 10, COLOR_CARD_MUTED);
        details.setPadding(0, dp(4), 0, 0);
        licenseCard.addView(details);
        TextView source = text(sourceUrl, 8, COLOR_CARD_MUTED);
        source.setTextDirection(View.TEXT_DIRECTION_LTR);
        source.setGravity(Gravity.START);
        source.setPadding(0, dp(5), 0, 0);
        licenseCard.addView(source);
        Button viewLicense = smallWideButton("הצג רישיון", COLOR_EMERALD_DEEP);
        viewLicense.setOnClickListener(v -> showLicenseText(titleValue, assetPath));
        licenseCard.addView(viewLicense);
        content.addView(licenseCard, cardParams());
    }

    private void showLicenseText(String titleValue, String assetPath) {
        currentScreen = "license_text";
        LinearLayout content = baseContent();
        addTitle(content, titleValue, "נוסח הרישיון וההודעות");
        LinearLayout licenseCard = card();
        TextView licenseText = text(readAssetText(assetPath), 9, COLOR_CARD_TEXT);
        licenseText.setTextDirection(View.TEXT_DIRECTION_LTR);
        licenseText.setGravity(Gravity.START);
        licenseText.setTypeface(Typeface.MONOSPACE);
        licenseCard.addView(licenseText);
        content.addView(licenseCard, cardParams());
        Button back = pillButton("חזרה לרישיונות", COLOR_SURFACE_2);
        back.setOnClickListener(v -> showAboutAndLicenses());
        content.addView(back, matchParams());
        View bottomSafeArea = new View(this);
        content.addView(bottomSafeArea, new LinearLayout.LayoutParams(dp(1), dp(6)));
        setScrollableContent(content);
    }

    private String readAssetText(String assetPath) {
        StringBuilder value = new StringBuilder();
        try (InputStream input = getAssets().open(assetPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                value.append(line).append('\n');
            }
        } catch (Exception exception) {
            AppLog.e(this, "license asset could not be read: " + assetPath, exception);
            return UiText.t(this, "לא ניתן לטעון את נוסח הרישיון");
        }
        return value.toString().trim();
    }

    private void showJewishSettings() {
        currentScreen = "jewish_settings";
        ReminderSettings settings = new ReminderSettings(this);
        LinearLayout content = baseContent();
        addTitle(content, "זמנים יהודיים", "");
        if (!settings.jewishMode()) {
            content.addView(emptyState("מצב יהודי כבוי"));
            Button back = pillButton("חזרה", COLOR_SURFACE_2);
            back.setOnClickListener(v -> showSettings());
            content.addView(back);
            setScrollableContent(content);
            return;
        }

        LinearLayout jewishDayCard = card();
        Switch jewishDaySwitch = new Switch(this);
        setSwitchText(jewishDaySwitch, "לתזכר ימים יהודיים");
        jewishDaySwitch.setChecked(settings.jewishDayRemindersEnabled());
        jewishDayCard.addView(jewishDaySwitch);
        TextView jewishDayHint = text(jewishDayReminderSummary(settings), 11, COLOR_MUTED);
        jewishDayHint.setPadding(0, dp(4), 0, 0);
        jewishDayCard.addView(jewishDayHint);
        jewishDaySwitch.setOnClickListener(v -> {
            settings.setJewishDayRemindersEnabled(jewishDaySwitch.isChecked());
            if (jewishDaySwitch.isChecked()) {
                JewishDayScheduler.schedule(this);
            } else {
                JewishDayScheduler.cancel(this);
                JewishDayReceiver.cancelNotification(this);
            }
            jewishDayHint.setText(jewishDayReminderSummary(settings));
        });
        if (settings.jewishMode()) {
            content.addView(jewishDayCard, cardParams());
        }

        LinearLayout tekufaCard = card();
        Switch tekufaSwitch = new Switch(this);
        setSwitchText(tekufaSwitch, "לתזכר זמן תקופה");
        tekufaSwitch.setChecked(settings.tekufaRemindersEnabled());
        tekufaCard.addView(tekufaSwitch);
        TextView tekufaHint = text(tekufaReminderSummary(settings), 11, COLOR_MUTED);
        tekufaHint.setPadding(0, dp(4), 0, 0);
        tekufaCard.addView(tekufaHint);
        tekufaSwitch.setOnClickListener(v -> {
            settings.setTekufaRemindersEnabled(tekufaSwitch.isChecked());
            if (tekufaSwitch.isChecked()) {
                TekufaScheduler.schedule(this);
            } else {
                TekufaScheduler.cancel(this);
                TekufaReceiver.cancelNotification(this);
            }
            tekufaHint.setText(tekufaReminderSummary(settings));
        });
        if (settings.jewishMode()) {
            content.addView(tekufaCard, cardParams());
        }

        LinearLayout blessingCard = card();
        TextView blessingTitle = text("תזכורת לברכה", 15, COLOR_TEXT);
        AppFont.bold(blessingTitle);
        blessingCard.addView(blessingTitle);
        TextView blessingHint = text("כמה דקות אחרי החיוב להזכיר לברך", 11, COLOR_MUTED);
        blessingHint.setPadding(0, dp(3), 0, dp(5));
        blessingCard.addView(blessingHint);
        NumberPicker blessingMinutesPicker = numberPicker(1, 71, settings.blessingReminderMinutes());
        blessingCard.addView(pickerColumn("דקות", blessingMinutesPicker));
        TextView shemaTitle = text("קריאת שמע של ערבית בזמנה", 13, COLOR_TEXT);
        AppFont.bold(shemaTitle);
        shemaTitle.setPadding(0, dp(10), 0, dp(2));
        blessingCard.addView(shemaTitle);
        TextView shemaHint = text("כמה דקות אחרי צאת הכוכבים לתזכר. אם הזמן כבר עבר, התזכורת תוגדר לפי ההגדרה הכללית למעלה.", 10, COLOR_MUTED);
        shemaHint.setPadding(0, 0, 0, dp(5));
        blessingCard.addView(shemaHint);
        NumberPicker shemaOffsetPicker = numberPicker(0, 60, settings.shemaOnTimeOffsetMinutes());
        blessingCard.addView(pickerColumn("דקות אחרי צאת הכוכבים", shemaOffsetPicker));
        if (settings.jewishMode()) {
            content.addView(blessingCard, cardParams());
        }

        LinearLayout moonCard = card();
        Switch moonSwitch = new Switch(this);
        setSwitchText(moonSwitch, "ברכת הלבנה");
        moonSwitch.setChecked(settings.moonBlessingEnabled());
        moonCard.addView(moonSwitch);
        TextView moonHint = text(moonBlessingSummary(settings), 11, COLOR_MUTED);
        moonHint.setPadding(0, dp(4), 0, 0);
        moonCard.addView(moonHint);
        Button moonHandled = smallWideButton("סמן שבירכתי", COLOR_SURFACE_2);
        moonHandled.setVisibility(canMarkMoonBlessingHandled(settings) ? View.VISIBLE : View.GONE);
        moonHandled.setOnClickListener(v -> {
            markMoonBlessingHandled(moonHint);
            moonHandled.setVisibility(View.GONE);
        });
        moonCard.addView(moonHandled);
        moonSwitch.setOnClickListener(v -> {
            settings.setMoonBlessingEnabled(moonSwitch.isChecked());
            if (moonSwitch.isChecked()) {
                MoonBlessingScheduler.schedule(this);
            } else {
                MoonBlessingScheduler.cancel(this);
                MoonBlessingReceiver.cancelNotification(this);
            }
            moonHandled.setVisibility(canMarkMoonBlessingHandled(settings) ? View.VISIBLE : View.GONE);
            moonHint.setText(moonBlessingSummary(settings));
        });
        if (settings.jewishMode()) {
            content.addView(moonCard, cardParams());
        }

        LinearLayout dafCard = card();
        Switch dafSwitch = new Switch(this);
        setSwitchText(dafSwitch, "דף היומי");
        dafSwitch.setChecked(settings.dafYomiEnabled());
        dafCard.addView(dafSwitch);
        TextView dafHint = text(dafYomiSummary(settings), 11, COLOR_MUTED);
        dafHint.setPadding(0, dp(4), 0, 0);
        dafCard.addView(dafHint);
        dafCard.setOnClickListener(v -> showDafYomiSettings());
        dafSwitch.setOnClickListener(v -> {
            settings.setDafYomiEnabled(dafSwitch.isChecked());
            if (dafSwitch.isChecked()) {
                new DafYomiStore(this).ensureStartToday();
                DafYomiScheduler.schedule(this);
            } else {
                DafYomiScheduler.cancel(this);
            }
            dafHint.setText(dafYomiSummary(settings));
        });
        if (settings.jewishMode()) {
            content.addView(dafCard, cardParams());
        }

        LinearLayout omerCard = card();
        Switch omerSwitch = new Switch(this);
        setSwitchText(omerSwitch, "ספירת העומר");
        omerSwitch.setChecked(settings.omerEnabled());
        omerCard.addView(omerSwitch);
        TextView omerHint = text(omerSummary(settings), 11, COLOR_MUTED);
        omerHint.setPadding(0, dp(4), 0, 0);
        omerCard.addView(omerHint);
        omerCard.setOnClickListener(v -> showOmerSettings());
        omerSwitch.setOnClickListener(v -> {
            settings.setOmerEnabled(omerSwitch.isChecked());
            if (omerSwitch.isChecked()) {
                OmerScheduler.schedule(this);
                showOmerSettings();
            } else {
                OmerScheduler.cancel(this);
                omerHint.setText(omerSummary(settings));
            }
        });
        if (settings.jewishMode()) {
            content.addView(omerCard, cardParams());
        }

        LinearLayout locationCard = card();
        TextView locationTitle = text("מיקום זמני הלכה", 15, COLOR_TEXT);
        AppFont.bold(locationTitle);
        locationCard.addView(locationTitle);
        TextView locationValue = text(zmanimLocationLine(), 12, COLOR_MUTED);
        locationValue.setPadding(dp(8), dp(4), dp(8), dp(6));
        locationCard.addView(locationValue);
        Button refreshLocation = pillButton("מיקום חדש", COLOR_SURFACE_2);
        refreshLocation.setOnClickListener(v -> requestFreshZmanimLocation(locationValue));
        locationCard.addView(refreshLocation);
        Button resolveLocationName = pillButton("זהה שם עיר", COLOR_SURFACE_2);
        resolveLocationName.setOnClickListener(v -> resolveStoredZmanimLocationName(locationValue, true));
        locationCard.addView(resolveLocationName);
        if (settings.jewishMode()) {
            content.addView(locationCard, cardParams());
        }

        LinearLayout actions = actionRow();
        Button save = pillButton("שמירה", COLOR_ACCENT_DARK);
        save.setOnClickListener(v -> {
            settings.setBlessingReminderMinutes(blessingMinutesPicker.getValue());
            settings.setShemaOnTimeOffsetMinutes(shemaOffsetPicker.getValue());
            settings.setJewishDayRemindersEnabled(settings.jewishMode() && jewishDaySwitch.isChecked());
            if (settings.jewishMode() && settings.jewishDayRemindersEnabled()) {
                JewishDayScheduler.schedule(this);
            } else {
                JewishDayScheduler.cancel(this);
                JewishDayReceiver.cancelNotification(this);
            }
            settings.setTekufaRemindersEnabled(settings.jewishMode() && tekufaSwitch.isChecked());
            if (settings.jewishMode() && settings.tekufaRemindersEnabled()) {
                TekufaScheduler.schedule(this);
            } else {
                TekufaScheduler.cancel(this);
                TekufaReceiver.cancelNotification(this);
            }
            settings.setMoonBlessingEnabled(settings.jewishMode() && moonSwitch.isChecked());
            if (settings.jewishMode() && settings.moonBlessingEnabled()) {
                MoonBlessingScheduler.schedule(this);
            } else {
                MoonBlessingScheduler.cancel(this);
                MoonBlessingReceiver.cancelNotification(this);
            }
            settings.setDafYomiEnabled(settings.jewishMode() && dafSwitch.isChecked());
            if (settings.jewishMode() && settings.dafYomiEnabled()) {
                new DafYomiStore(this).ensureStartToday();
                DafYomiScheduler.schedule(this);
            } else {
                DafYomiScheduler.cancel(this);
            }
            settings.setOmerEnabled(settings.jewishMode() && omerSwitch.isChecked());
            if (settings.jewishMode() && settings.omerEnabled()) {
                OmerScheduler.schedule(this);
            } else {
                OmerScheduler.cancel(this);
            }
            store.rescheduleAll();
            ComplicationRefresh.request(this);
            showSettings();
        });
        Button back = pillButton("חזרה", COLOR_SURFACE_2);
        back.setOnClickListener(v -> showSettings());
        actions.addView(save);
        actions.addView(back);
        content.addView(actions);

        setScrollableContent(content);
    }

    private void showAlertSettings() {
        currentScreen = "alert_settings";
        ReminderSettings settings = new ReminderSettings(this);
        LinearLayout content = baseContent();
        addTitle(content, "רטט וצלילים", "");

        LinearLayout vibrationCard = card();
        TextView vibrationTitle = text("התראה", 15, COLOR_TEXT);
        AppFont.bold(vibrationTitle);
        vibrationCard.addView(vibrationTitle);

        Switch soundSwitch = new Switch(this);
        setSwitchText(soundSwitch, "צלצול");
        soundSwitch.setChecked(settings.alertSoundEnabled());
        soundSwitch.setOnClickListener(v -> settings.setAlertSoundEnabled(soundSwitch.isChecked()));
        vibrationCard.addView(soundSwitch);

        final String[] selectedSoundUri = {settings.alertSoundUri()};
        TextView ringtoneValue = text(ringtoneTitle(selectedSoundUri[0]), 11, COLOR_MUTED);
        ringtoneValue.setPadding(0, dp(3), 0, dp(5));
        vibrationCard.addView(ringtoneValue);
        Button chooseRingtone = pillButton("בחירת צלצול", COLOR_SURFACE_2);
        LevelControl soundVolume = levelControl("עוצמת צלצול", 10, settings.alertVolumePercent() / 10);
        chooseRingtone.setOnClickListener(v -> showSmartAlarmRingtoneScreen(selectedSoundUri[0],
                soundVolume.slider.getProgress() * 10, (uri, title) -> {
                    selectedSoundUri[0] = uri;
                    ringtoneValue.setText(title);
                }));
        vibrationCard.addView(chooseRingtone);
        vibrationCard.addView(soundVolume.view, wideLevelParams());

        Switch vibrationSwitch = new Switch(this);
        setSwitchText(vibrationSwitch, "רטט");
        vibrationSwitch.setChecked(settings.vibrationEnabled());
        vibrationCard.addView(vibrationSwitch);

        String[] vibrationLabels = {"רגיל", "עדין", "חזק", "ארוך"};
        String[] vibrationValues = {
                ReminderSettings.VIBRATION_NORMAL,
                ReminderSettings.VIBRATION_GENTLE,
                ReminderSettings.VIBRATION_STRONG,
                ReminderSettings.VIBRATION_LONG
        };
        Spinner vibrationSpinner = new Spinner(this);
        ArrayAdapter<String> vibrationAdapter = spinnerAdapter(translated(vibrationLabels));
        vibrationSpinner.setAdapter(vibrationAdapter);
        vibrationSpinner.setSelection(indexOf(vibrationValues, settings.vibrationStyle()));
        vibrationCard.addView(vibrationSpinner, matchParams());

        LevelControl vibrationStrength = levelControl("עוצמת רטט", 9, settings.vibrationStrength() - 1);
        vibrationCard.addView(vibrationStrength.view, wideLevelParams());

        NumberPicker alertDuration = numberPicker(1, 10, Math.max(1, Math.round(settings.alertDurationMs() / 1000f)));
        vibrationCard.addView(pickerColumn("אורך התראה בשניות", alertDuration));
        final boolean[] vibrationSelectionReady = {false};
        vibrationSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (!vibrationSelectionReady[0]) {
                    vibrationSelectionReady[0] = true;
                    return;
                }
                if (vibrationSwitch.isChecked()) {
                    previewVibration(vibrationValues[position], alertDuration.getValue() * 1000,
                            vibrationStrength.slider.getProgress() + 1);
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        vibrationSwitch.setOnClickListener(v -> {
            settings.setVibrationEnabled(vibrationSwitch.isChecked());
            if (vibrationSwitch.isChecked()) {
                previewVibration(vibrationValues[vibrationSpinner.getSelectedItemPosition()],
                        alertDuration.getValue() * 1000, vibrationStrength.slider.getProgress() + 1);
            } else {
                stopVibrationPreview();
            }
        });
        alertDuration.setOnValueChangedListener((picker, oldValue, newValue) -> {
            if (vibrationSwitch.isChecked()) {
                previewVibration(vibrationValues[vibrationSpinner.getSelectedItemPosition()], newValue * 1000,
                        vibrationStrength.slider.getProgress() + 1);
            }
        });
        vibrationStrength.slider.setOnSeekBarChangeListener(levelPreviewListener(vibrationStrength, progress -> {
            if (vibrationSwitch.isChecked()) {
                previewVibration(vibrationValues[vibrationSpinner.getSelectedItemPosition()],
                        alertDuration.getValue() * 1000, progress + 1);
            }
        }));
        soundVolume.slider.setOnSeekBarChangeListener(levelPreviewListener(soundVolume, progress -> {
            if (soundSwitch.isChecked()) {
                previewSmartAlarm(ReminderSettings.VIBRATION_NORMAL, 10, true,
                        progress * 10, selectedSoundUri[0]);
            }
        }));
        content.addView(vibrationCard, cardParams());

        LinearLayout actions = actionRow();
        Button save = pillButton("שמירה", COLOR_ACCENT_DARK);
        save.setOnClickListener(v -> {
            stopVibrationPreview();
            stopSmartAlarmPreview();
            settings.setAlertSoundEnabled(soundSwitch.isChecked());
            settings.setAlertSoundUri(selectedSoundUri[0]);
            settings.setAlertVolumePercent(soundVolume.slider.getProgress() * 10);
            settings.setVibrationEnabled(vibrationSwitch.isChecked());
            settings.setVibrationStyle(vibrationValues[vibrationSpinner.getSelectedItemPosition()]);
            settings.setVibrationStrength(vibrationStrength.slider.getProgress() + 1);
            settings.setAlertDurationMs(alertDuration.getValue() * 1000);
            showSettings();
        });
        Button back = pillButton("חזרה", COLOR_SURFACE_2);
        back.setOnClickListener(v -> {
            stopVibrationPreview();
            stopSmartAlarmPreview();
            showSettings();
        });
        actions.addView(save);
        actions.addView(back);
        content.addView(actions);
        setScrollableContent(content);
    }

    private void showAdvancedSettings() {
        currentScreen = "advanced_settings";
        ReminderSettings settings = new ReminderSettings(this);
        LinearLayout content = baseContent();
        addTitle(content, "הגדרות מתקדמות", "");

        LinearLayout serviceCard = card();
        Switch serviceSwitch = new Switch(this);
        setSwitchText(serviceSwitch, "בדיקת רקע פעילה");
        serviceSwitch.setChecked(settings.serviceEnabled());
        serviceSwitch.setOnClickListener(v -> {
            settings.setServiceEnabled(serviceSwitch.isChecked());
            if (settings.serviceEnabled()) {
                ReminderScheduler.scheduleWatchdog(this);
                ReminderForegroundService.start(this);
            } else {
                ReminderForegroundService.stop(this);
                ReminderScheduler.scheduleWatchdog(this);
            }
        });
        serviceCard.addView(serviceSwitch);
        TextView serviceHint = text("אם ההתראות לא מתקבלות בזמן, אפשר להפעיל בדיקת רקע ולבחור כל כמה דקות לדגום. הגדרה זו עלולה לצרוך יותר סוללה.", 11, COLOR_MUTED);
        serviceHint.setPadding(0, dp(4), 0, dp(4));
        serviceCard.addView(serviceHint);
        int intervalSeconds = settings.checkIntervalSeconds();
        LinearLayout intervalRow = new LinearLayout(this);
        intervalRow.setGravity(Gravity.CENTER);
        NumberPicker intervalMinutesPicker = numberPicker(0, 60, intervalSeconds / 60);
        NumberPicker intervalSecondsPicker = numberPicker(0, 59, intervalSeconds % 60);
        intervalRow.addView(pickerColumn("דקות", intervalMinutesPicker));
        intervalRow.addView(pickerColumn("שניות", intervalSecondsPicker));
        serviceCard.addView(intervalRow);
        content.addView(serviceCard, cardParams());

        LinearLayout autoCard = card();
        TextView autoTitle = text("דחייה אוטומטית", 15, COLOR_TEXT);
        AppFont.bold(autoTitle);
        autoCard.addView(autoTitle);
        TextView autoHint = text("אם אין תגובה, המסך נסגר והתזכורת נדחית", 11, COLOR_MUTED);
        autoHint.setPadding(0, dp(3), 0, dp(5));
        autoCard.addView(autoHint);
        NumberPicker autoDelayPicker = numberPicker(5, 600, settings.autoSnoozeDelaySeconds());
        autoCard.addView(pickerColumn("המתנה לתגובה לפני סגירה (שניות)", autoDelayPicker));
        NumberPicker autoSnoozePicker = numberPicker(1, 240, settings.autoSnoozeMinutes());
        autoCard.addView(pickerColumn("הקפצה חוזרת לאחר סגירה (דקות)", autoSnoozePicker));
        content.addView(autoCard, cardParams());

        LinearLayout actions = actionRow();
        Button save = pillButton("שמירה", COLOR_ACCENT_DARK);
        save.setOnClickListener(v -> {
            settings.setServiceEnabled(serviceSwitch.isChecked());
            int seconds = intervalMinutesPicker.getValue() * 60 + intervalSecondsPicker.getValue();
            settings.setCheckIntervalSeconds(seconds);
            settings.setAutoSnoozeDelaySeconds(autoDelayPicker.getValue());
            settings.setAutoSnoozeMinutes(autoSnoozePicker.getValue());
            if (settings.serviceEnabled()) {
                ReminderScheduler.scheduleWatchdog(this);
                ReminderForegroundService.start(this);
            } else {
                ReminderForegroundService.stop(this);
                ReminderScheduler.scheduleWatchdog(this);
            }
            showSettings();
        });
        Button back = pillButton("חזרה", COLOR_SURFACE_2);
        back.setOnClickListener(v -> showSettings());
        actions.addView(save);
        actions.addView(back);
        content.addView(actions);
        setScrollableContent(content);
    }

    private void showFastingSettings() {
        currentScreen = "fasting_settings";
        ReminderSettings settings = new ReminderSettings(this);
        LinearLayout content = baseContent();
        addTitle(content, "צום לסירוגין", "");

        if (settings.intermittentFastingEnabled()) {
            LinearLayout stateCard = card();
            TextView stateTitle = text("מצב נוכחי", 15, COLOR_TEXT);
            AppFont.bold(stateTitle);
            stateCard.addView(stateTitle);
            stateCard.addView(text(fastingStateLine(), 12, COLOR_MUTED));
            stateCard.addView(fastingActionRow());
            stateCard.addView(fastingManualTimeSection());
            content.addView(stateCard, cardParams());
        }

        LinearLayout enabledCard = card();
        Switch enabledSwitch = new Switch(this);
        setSwitchText(enabledSwitch, "פעיל");
        enabledSwitch.setChecked(settings.intermittentFastingEnabled());
        enabledCard.addView(enabledSwitch);
        content.addView(enabledCard, cardParams());

        LinearLayout hoursCard = card();
        TextView hoursTitle = text("שעות צום וחלון אכילה", 15, COLOR_TEXT);
        AppFont.bold(hoursTitle);
        hoursCard.addView(hoursTitle);
        TextView hoursHint = text("חלון האכילה מחושב אוטומטית מתוך 24 שעות", 11, COLOR_MUTED);
        hoursHint.setPadding(0, dp(3), 0, dp(5));
        hoursCard.addView(hoursHint);
        NumberPicker fastingHoursPicker = numberPicker(1, 23, settings.fastingHours());
        TextView eatingHours = text(fastingEatingWindowText(settings.fastingEatingHours()), 13, COLOR_ACCENT);
        eatingHours.setPadding(0, dp(4), 0, 0);
        fastingHoursPicker.setOnValueChangedListener((picker, oldValue, newValue) ->
                eatingHours.setText(fastingEatingWindowText(24 - newValue)));
        hoursCard.addView(pickerColumn("שעות צום", fastingHoursPicker));
        hoursCard.addView(eatingHours);
        content.addView(hoursCard, cardParams());

        LinearLayout startCard = card();
        TextView startTitle = text("התחלת אכילה ראשונית", 15, COLOR_TEXT);
        AppFont.bold(startTitle);
        startCard.addView(startTitle);
        TextView startHint = text("לדוגמה 12:00 עם 19 שעות צום יוצר חלון 12:00-17:00", 11, COLOR_MUTED);
        startHint.setPadding(0, dp(3), 0, dp(5));
        startCard.addView(startHint);
        NumberPicker hourPicker = numberPicker(0, 23, settings.fastingStartHour());
        NumberPicker minutePicker = numberPicker(0, 59, settings.fastingStartMinute());
        startCard.addView(timePickerRow(hourPicker, minutePicker));
        content.addView(startCard, cardParams());

        LinearLayout actions = actionRow();
        Button save = pillButton("שמירה", COLOR_ACCENT_DARK);
        save.setOnClickListener(v -> {
            boolean wasEnabled = settings.intermittentFastingEnabled();
            int oldHours = settings.fastingHours();
            int oldHour = settings.fastingStartHour();
            int oldMinute = settings.fastingStartMinute();
            settings.setIntermittentFastingEnabled(enabledSwitch.isChecked());
            settings.setFastingHours(fastingHoursPicker.getValue());
            settings.setFastingStartTime(hourPicker.getValue(), minutePicker.getValue());
            if (!enabledSwitch.isChecked()) {
                IntermittentFastingScheduler.cancel(this);
                IntermittentFastingReceiver.cancelNotification(this);
            } else {
                if (!wasEnabled
                        || oldHours != fastingHoursPicker.getValue()
                        || oldHour != hourPicker.getValue()
                        || oldMinute != minutePicker.getValue()) {
                    new IntermittentFastingStore(this).resetToInitialStart();
                }
                IntermittentFastingScheduler.schedule(this);
            }
            ComplicationRefresh.requestFasting(this);
            showSettings();
        });
        Button back = pillButton("חזרה", COLOR_SURFACE_2);
        back.setOnClickListener(v -> showSettings());
        actions.addView(save);
        actions.addView(back);
        content.addView(actions);
        setScrollableContent(content);
    }

    private LinearLayout fastingActionRow() {
        LinearLayout actions = actionRow();
        actions.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        Button finishNow = pillButton("סיימתי לאכול", COLOR_SURFACE_2);
        finishNow.setOnClickListener(v -> markFastingFinishedNow());
        Button startNow = pillButton("התחלתי לאכול", COLOR_ACCENT_DARK);
        startNow.setOnClickListener(v -> markFastingStartedNow());
        actions.addView(finishNow);
        actions.addView(startNow);
        return actions;
    }

    private void markFastingStartedNow() {
        new IntermittentFastingStore(this).startEatingNow();
        IntermittentFastingReceiver.cancelNotification(this);
        IntermittentFastingScheduler.schedule(this);
        ComplicationRefresh.requestFasting(this);
        Toast.makeText(this, UiText.t(this, "חלון האכילה התחיל עכשיו"), Toast.LENGTH_SHORT).show();
        refreshVisibleScreen();
    }

    private void markFastingStartedAt(int hour, int minute) {
        long startedAt = manualTime(hour, minute, System.currentTimeMillis());
        new IntermittentFastingStore(this).startEatingAt(startedAt);
        IntermittentFastingReceiver.cancelNotification(this);
        IntermittentFastingScheduler.schedule(this);
        ComplicationRefresh.requestFasting(this);
        Toast.makeText(this, UiText.t(this, "סומן שהתחלת לאכול ב-") + NextReminderCalculator.formatTime(startedAt), Toast.LENGTH_SHORT).show();
        refreshVisibleScreen();
    }

    private void markFastingFinishedNow() {
        IntermittentFastingStore store = new IntermittentFastingStore(this);
        IntermittentFastingStore.Window window = store.window();
        long now = System.currentTimeMillis();
        long finishedAt = ReminderScheduler.floorToMinute(now);
        long sessionStartAt = sessionStartForFinish(window, finishedAt, now);
        if (finishedAt < sessionStartAt) {
            Toast.makeText(this, UiText.t(this, "אפשר לבחור זמן סיום רק אחרי פתיחת חלון האכילה"), Toast.LENGTH_SHORT).show();
            return;
        }
        store.finishEatingAt(finishedAt, sessionStartAt);
        IntermittentFastingReceiver.cancelNotification(this);
        IntermittentFastingScheduler.schedule(this);
        ComplicationRefresh.requestFasting(this);
        Toast.makeText(this, UiText.t(this, "סומן שסיימת לאכול. חלון האכילה הבא יתעדכן לפי זמן הסיום."), Toast.LENGTH_SHORT).show();
        refreshVisibleScreen();
    }

    private LinearLayout fastingManualTimeSection() {
        LinearLayout section = section();
        TextView title = text("בחירת זמן ידנית", 13, COLOR_MUTED);
        title.setPadding(0, dp(8), 0, dp(3));
        section.addView(title);

        LinearLayout row = actionRow();
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        Button manualFinish = pillButton("זמן סיום", COLOR_SURFACE_2);
        manualFinish.setOnClickListener(v -> showFastingManualTimeDialog(false));
        Button manualStart = pillButton("זמן התחלה", COLOR_ACCENT_DARK);
        manualStart.setOnClickListener(v -> showFastingManualTimeDialog(true));
        row.addView(manualFinish);
        row.addView(manualStart);
        section.addView(row);
        return section;
    }

    private void showFastingManualTimeDialog(boolean startEating) {
        Calendar now = Calendar.getInstance();
        NumberPicker hourPicker = numberPicker(0, 23, now.get(Calendar.HOUR_OF_DAY));
        NumberPicker minutePicker = numberPicker(0, 59, now.get(Calendar.MINUTE));
        LinearLayout content = section();
        TextView title = text(startEating ? "התחלתי לאכול בשעה" : "סיימתי לאכול בשעה", 13, COLOR_TEXT);
        AppFont.bold(title);
        title.setPadding(0, dp(8), 0, dp(4));
        content.addView(title);
        content.addView(timePickerRow(hourPicker, minutePicker));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .create();
        content.addView(fastingManualDialogActions(dialog, startEating, hourPicker, minutePicker));
        dialog.show();
    }

    private LinearLayout fastingManualDialogActions(AlertDialog dialog, boolean startEating, NumberPicker hourPicker, NumberPicker minutePicker) {
        LinearLayout actions = actionRow();
        Button apply = pillButton(startEating ? "בחר זמן התחלה" : "בחר זמן סיום", startEating ? COLOR_ACCENT_DARK : COLOR_SURFACE_2);
        apply.setOnClickListener(v -> {
            dialog.dismiss();
            if (startEating) {
                markFastingStartedAt(hourPicker.getValue(), minutePicker.getValue());
            } else {
                markFastingFinishedAt(hourPicker.getValue(), minutePicker.getValue());
            }
        });
        Button cancel = pillButton("ביטול", COLOR_SURFACE_2);
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(apply);
        actions.addView(cancel);
        return actions;
    }

    private void markFastingFinishedAt(int hour, int minute) {
        IntermittentFastingStore store = new IntermittentFastingStore(this);
        IntermittentFastingStore.Window window = store.window();
        long now = System.currentTimeMillis();
        long[] finishTime = manualFinishTime(window, hour, minute, now);
        long finishedAt = finishTime[0];
        long sessionStartAt = finishTime[1];
        if (finishedAt > now) {
            Toast.makeText(this, UiText.t(this, "אי אפשר לבחור זמן סיום עתידי"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (finishedAt < sessionStartAt) {
            Toast.makeText(this, UiText.t(this, "אפשר לבחור זמן סיום רק אחרי פתיחת חלון האכילה"), Toast.LENGTH_SHORT).show();
            return;
        }
        store.finishEatingAt(finishedAt, sessionStartAt);
        IntermittentFastingReceiver.cancelNotification(this);
        IntermittentFastingScheduler.schedule(this);
        ComplicationRefresh.requestFasting(this);
        Toast.makeText(this, UiText.t(this, "סומן שסיימת לאכול ב-") + NextReminderCalculator.formatTime(finishedAt), Toast.LENGTH_SHORT).show();
        refreshVisibleScreen();
    }

    private long[] manualFinishTime(IntermittentFastingStore.Window window, int hour, int minute, long now) {
        long candidate = manualTime(hour, minute, now);
        return new long[]{candidate, sessionStartForFinish(window, candidate, now)};
    }

    private long manualTime(int hour, int minute, long now) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long candidate = calendar.getTimeInMillis();
        if (candidate > now) {
            calendar.add(Calendar.DAY_OF_YEAR, -1);
            candidate = calendar.getTimeInMillis();
        }
        return ReminderScheduler.floorToMinute(candidate);
    }

    private long sessionStartForFinish(IntermittentFastingStore.Window window, long finishedAt, long now) {
        long sessionStartAt = window.startAt;
        if (finishedAt < sessionStartAt && window.startAt > now) {
            return sessionStartAt - 24L * 60L * 60_000L;
        }
        return sessionStartAt;
    }

    private String fastingStateLine() {
        ReminderSettings settings = new ReminderSettings(this);
        IntermittentFastingStore.Window window = new IntermittentFastingStore(this).window();
        long now = System.currentTimeMillis();
        boolean english = AppLanguage.isEnglish(this);
        if (window.eatingOpen(now)) {
            return (english ? "Eating window is open until " : "חלון האכילה פתוח עד ") + formatDateTime(window.endAt);
        }
        if (window.finished) {
            return (english ? "Finished eating at " : "סיימת לאכול ב-") + formatDateTime(window.finishedAt)
                    + (english ? " | Next opening: " : " | פתיחה הבאה: ") + formatDateTime(window.nextStartAt);
        }
        if (now < window.startAt) {
            return (english ? "Fasting now | You can start eating at " : "בצום עכשיו | אפשר להתחיל לאכול ב-")
                    + formatDateTime(window.startAt);
        }
        return (english ? "Fasting now | Next window: " : "בצום עכשיו | החלון הבא: ") + formatDateTime(window.nextStartAt)
                + " | " + settings.fastingHours() + "/" + settings.fastingEatingHours();
    }

    private String fastingEatingWindowText(int hours) {
        return AppLanguage.isEnglish(this)
                ? "Eating window: " + hours + " hours"
                : "חלון אכילה: " + hours + " שעות";
    }

    private void applyJewishModeChange(boolean enabled) {
        ReminderSettings settings = new ReminderSettings(this);
        if (settings.jewishMode() == enabled) {
            return;
        }
        settings.setJewishMode(enabled);
        if (!enabled) {
            settings.setMoonBlessingEnabled(false);
            settings.setDafYomiEnabled(false);
            settings.setOmerEnabled(false);
            settings.setJewishDayRemindersEnabled(false);
            settings.setTekufaRemindersEnabled(false);
            MoonBlessingScheduler.cancel(this);
            MoonBlessingReceiver.cancelNotification(this);
            DafYomiScheduler.cancel(this);
            OmerScheduler.cancel(this);
            JewishDayScheduler.cancel(this);
            JewishDayReceiver.cancelNotification(this);
            TekufaScheduler.cancel(this);
            TekufaReceiver.cancelNotification(this);
        } else {
            settings.setJewishDayRemindersEnabled(true);
            JewishDayScheduler.schedule(this);
            settings.setTekufaRemindersEnabled(true);
            TekufaScheduler.schedule(this);
        }
        store.rescheduleAll();
        ComplicationRefresh.request(this);
        if (enabled && requestLocationAccessIfNeeded()) {
            return;
        }
        showSettings();
    }

    private void showQuietTimes() {
        currentScreen = "quiet_times";
        ReminderSettings settings = new ReminderSettings(this);
        QuietTimeRuleStore quietStore = new QuietTimeRuleStore(this);
        if (settings.quietMinchaMaariv()) {
            quietStore.ensureDefaultMinchaMaarivRule();
        }

        LinearLayout content = baseContent();
        addTitle(content, "זמני שקט", "בתקופות האלו רק תזכורות חיוניות יקפצו");

        Switch enabledSwitch = new Switch(this);
        setSwitchText(enabledSwitch, "פעיל");
        enabledSwitch.setChecked(settings.quietMinchaMaariv());
        enabledSwitch.setOnClickListener(v -> {
            settings.setQuietMinchaMaariv(enabledSwitch.isChecked());
            if (enabledSwitch.isChecked()) {
                quietStore.ensureDefaultMinchaMaarivRule();
            }
            store.rescheduleAll();
            ComplicationRefresh.request(this);
            showQuietTimes();
        });
        LinearLayout enabledCard = card();
        enabledCard.addView(enabledSwitch);
        content.addView(enabledCard, cardParams());

        List<QuietTimeRuleStore.Rule> rules = quietStore.getAll();
        if (rules.isEmpty()) {
            content.addView(emptyState("אין עדיין זמני שקט"));
        } else {
            for (QuietTimeRuleStore.Rule rule : rules) {
                LinearLayout ruleCard = card();
                ruleCard.setOnLongClickListener(v -> {
                    showQuietRuleActions(rule);
                    return true;
                });
                ruleCard.setOnClickListener(v -> showQuietRuleEditor(rule));
                TextView title = text(UiText.t(this, rule.name), 15, rule.enabled ? COLOR_TEXT : COLOR_MUTED);
                AppFont.bold(title);
                TextView details = text(quietRuleDetails(rule), 11, COLOR_MUTED);
                details.setPadding(dp(8), dp(3), dp(8), 0);
                ruleCard.addView(title);
                ruleCard.addView(details);
                if (!rule.enabled) {
                    TextView off = text("כבוי", 11, COLOR_WARNING);
                    off.setPadding(0, dp(3), 0, 0);
                    ruleCard.addView(off);
                }
                content.addView(ruleCard, cardParams());
            }
        }

        LinearLayout actions = actionRow();
        Button add = pillButton("הוספה", COLOR_ACCENT_DARK);
        add.setOnClickListener(v -> showQuietRuleEditor(null));
        Button back = pillButton("חזרה", COLOR_SURFACE_2);
        back.setOnClickListener(v -> showSettings());
        actions.addView(add);
        actions.addView(back);
        content.addView(actions);

        setScrollableContent(content);
    }

    private void showDafYomiSettings() {
        currentScreen = "daf_yomi";
        ReminderSettings settings = new ReminderSettings(this);
        DafYomiStore dafStore = new DafYomiStore(this);
        LinearLayout content = baseContent();
        addTitle(content, "דף היומי", "בדיקה יומית ורשימת דפים להשלמה");

        LinearLayout timeCard = card();
        Switch enabled = new Switch(this);
        setSwitchText(enabled, "תזכורת דף היומי פעילה");
        enabled.setChecked(settings.dafYomiEnabled());
        timeCard.addView(enabled);
        NumberPicker hour = numberPicker(0, 23, settings.dafYomiHour());
        NumberPicker minute = numberPicker(0, 59, settings.dafYomiMinute());
        timeCard.addView(timePickerRow(hour, minute));
        content.addView(timeCard, cardParams());

        LinearLayout correctionCard = card();
        TextView correctionTitle = text("תיקון סימון", 15, COLOR_TEXT);
        AppFont.bold(correctionTitle);
        correctionCard.addView(correctionTitle);
        TextView correctionHint = text("אם סימנת בטעות שלמדת, אפשר להחזיר דף לרשימת ההשלמות", 11, COLOR_MUTED);
        correctionHint.setPadding(0, dp(3), 0, dp(5));
        correctionCard.addView(correctionHint);
        List<DafYomiHelper.Item> learnedRecent = dafStore.recentlyLearnedItems(this, 7);
        if (learnedRecent.isEmpty()) {
            correctionCard.addView(text("אין דפים שסומנו כלמדתי בימים האחרונים", 12, COLOR_MUTED));
        } else {
            for (DafYomiHelper.Item item : learnedRecent) {
                TextView itemText = text(item.label, 14, COLOR_TEXT);
                itemText.setPadding(0, dp(6), 0, dp(2));
                correctionCard.addView(itemText);
                Button undoLearned = pillButton("סמן כלא למדתי", COLOR_SURFACE_2);
                undoLearned.setOnClickListener(v -> {
                    new DafYomiStore(this).markMissed(item);
                    Toast.makeText(this, UiText.t(this, "הדף הועבר להשלמה"), Toast.LENGTH_SHORT).show();
                    showDafYomiSettings();
                });
                correctionCard.addView(undoLearned);
            }
        }
        content.addView(correctionCard, cardParams());

        LinearLayout missedCard = card();
        TextView missedTitle = text("דפים להשלמה", 15, COLOR_TEXT);
        AppFont.bold(missedTitle);
        missedCard.addView(missedTitle);
        List<DafYomiHelper.Item> missed = dafStore.missedItems(this);
        if (missed.isEmpty()) {
            missedCard.addView(text("אין דפים שמסומנים כלא למדתי", 12, COLOR_MUTED));
        } else {
            Button markAllLearned = pillButton("סמן הכל כהושלם", COLOR_ACCENT_DARK);
            markAllLearned.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle(UiText.t(this, "סימון כל הדפים כהושלמו"))
                        .setMessage(UiText.t(this, "האם לסמן את כל הדפים שברשימת ההשלמה כנלמדו? פעולה זו תסיר את כולם מהרשימה."))
                        .setPositiveButton(UiText.t(this, "המשך"), (dialog, which) -> {
                            new DafYomiStore(this).markLearned(missed);
                            showDafYomiSettings();
                        })
                        .setNegativeButton(UiText.t(this, "ביטול"), null)
                        .show();
            });
            missedCard.addView(markAllLearned);
            for (DafYomiHelper.Item item : missed) {
                TextView itemText = text(item.label, 14, COLOR_TEXT);
                itemText.setPadding(0, dp(6), 0, dp(2));
                missedCard.addView(itemText);
                Button learned = pillButton("למדתי את הדף", COLOR_ACCENT_DARK);
                learned.setOnClickListener(v -> {
                    new DafYomiStore(this).markMissedLearned(item.epochDay);
                    showDafYomiSettings();
                });
                missedCard.addView(learned);
            }
        }
        content.addView(missedCard, cardParams());

        LinearLayout actions = actionRow();
        Button save = pillButton("שמירה", COLOR_ACCENT_DARK);
        save.setOnClickListener(v -> {
            settings.setDafYomiEnabled(enabled.isChecked());
            settings.setDafYomiTime(hour.getValue(), minute.getValue());
            if (enabled.isChecked()) {
                dafStore.ensureStartToday();
                DafYomiScheduler.schedule(this);
            } else {
                DafYomiScheduler.cancel(this);
            }
            showJewishSettings();
        });
        Button back = pillButton("חזרה", COLOR_SURFACE_2);
        back.setOnClickListener(v -> showJewishSettings());
        actions.addView(save);
        actions.addView(back);
        content.addView(actions);
        setScrollableContent(content);
    }

    private void showOmerSettings() {
        currentScreen = "omer";
        ReminderSettings settings = new ReminderSettings(this);
        LinearLayout content = baseContent();
        addTitle(content, "ספירת העומר", "תזכורת בערבי הספירה לפי צאת הכוכבים");

        LinearLayout card = card();
        Switch enabled = new Switch(this);
        setSwitchText(enabled, "תזכורת ספירת העומר פעילה");
        enabled.setChecked(settings.omerEnabled());
        card.addView(enabled);

        TextView hint = text("כמה דקות אחרי צאת הכוכבים להזכיר", 11, COLOR_MUTED);
        hint.setPadding(0, dp(6), 0, dp(5));
        card.addView(hint);
        NumberPicker offset = numberPicker(0, 240, settings.omerOffsetMinutes());
        card.addView(pickerColumn("דקות אחרי צאת הכוכבים", offset));

        OmerHelper.Item next = OmerHelper.next(this, settings.omerOffsetMinutes());
        String nextLine = next == null
                ? UiText.t(this, "מחוץ לימי הספירה כרגע")
                : UiText.t(this, "התזכורת הקרובה:") + " "
                + NextReminderCalculator.formatDateTime(next.triggerAt) + " | "
                + UiText.t(this, "יום") + " " + next.day;
        TextView nextText = text(nextLine, 12, COLOR_MUTED);
        nextText.setPadding(0, dp(8), 0, 0);
        card.addView(nextText);
        content.addView(card, cardParams());

        LinearLayout actions = actionRow();
        Button save = pillButton("שמירה", COLOR_ACCENT_DARK);
        save.setOnClickListener(v -> {
            settings.setOmerEnabled(enabled.isChecked());
            settings.setOmerOffsetMinutes(offset.getValue());
            if (enabled.isChecked()) {
                OmerScheduler.schedule(this);
            } else {
                OmerScheduler.cancel(this);
            }
            showJewishSettings();
        });
        Button back = pillButton("חזרה", COLOR_SURFACE_2);
        back.setOnClickListener(v -> showJewishSettings());
        actions.addView(save);
        actions.addView(back);
        content.addView(actions);
        setScrollableContent(content);
    }

    private void showZmanimDay(long dateMillis) {
        showZmanimDay(dateMillis, false);
    }

    private void openZmanimDayFromSettings() {
        zmanimBackToSettings = true;
        showZmanimDay(System.currentTimeMillis());
    }

    private void openZmanimDayFromMain() {
        zmanimBackToSettings = false;
        showZmanimDay(System.currentTimeMillis());
    }

    private void showZmanimDay(long dateMillis, boolean showDatePickers) {
        showZmanimDay(dateMillis, showDatePickers, 0, 0);
    }

    private void showZmanimDay(long dateMillis, boolean showDatePickers, int scrollY) {
        showZmanimDay(dateMillis, showDatePickers, scrollY, 0);
    }

    private void showZmanimDay(long dateMillis, boolean showDatePickers, int scrollY, boolean scrollTimesToTop) {
        showZmanimDay(dateMillis, showDatePickers, scrollY, scrollTimesToTop ? 1 : 0);
    }

    private void showZmanimDay(long dateMillis, boolean showDatePickers, int scrollY, int scrollTarget) {
        currentScreen = "zmanim_day";
        long dayMillis = zmanimStartOfDay(dateMillis);
        ZmanimSettings settings = new ZmanimSettings(this);
        LinearLayout content = baseContent();
        addTitle(content, "זמני היום", displayLocationName(settings.name()) + "\n" + ZmanimSettings.coordinatesName(settings.latitude(), settings.longitude()));
        int zmanimBackgroundResource = zmanimBackgroundResource(dayMillis);
        addShabbatTimesCard(content, dayMillis);

        LinearLayout timesCard = card();
        final TextView[] gregorianPickerTitle = new TextView[1];

        Button chooseDate = pillButton("בחירת תאריך", COLOR_SURFACE_2);
        chooseDate.setOnClickListener(v -> {
            scrollToViewTop(gregorianPickerTitle[0], dp(8));
        });
        timesCard.addView(chooseDate);

        LinearLayout navRow = actionRow();
        Button previous = smallWideButton("יום לפני", COLOR_SURFACE_2);
        setZmanimNavButtonParams(previous);
        previous.setOnClickListener(v -> showZmanimDay(zmanimDayOffset(dayMillis, -1), showDatePickers, 0, 2));
        Button today = smallWideButton("היום", COLOR_ACCENT_DARK);
        setZmanimNavButtonParams(today);
        today.setOnClickListener(v -> showZmanimDay(System.currentTimeMillis(), showDatePickers, 0, 2));
        Button next = smallWideButton("יום אחרי", COLOR_SURFACE_2);
        setZmanimNavButtonParams(next);
        next.setOnClickListener(v -> showZmanimDay(zmanimDayOffset(dayMillis, 1), showDatePickers, 0, 2));
        navRow.addView(previous);
        navRow.addView(today);
        navRow.addView(next);
        timesCard.addView(navRow);

        TextView dateTitle = text(zmanimDateLine(dayMillis), 13, COLOR_TEXT);
        AppFont.bold(dateTitle);
        dateTitle.setPadding(0, dp(2), 0, dp(8));
        timesCard.addView(dateTitle);
        addZmanimParshaRows(timesCard, dayMillis);

        for (int i = 0; i < ZmanimHelper.KEYS.length; i++) {
            timesCard.addView(zmanimTimeRow(ZmanimHelper.LABELS[i], ZmanimHelper.timeForKey(this, ZmanimHelper.KEYS[i], dayMillis)));
            if (ZmanimHelper.KEY_TZAIS.equals(ZmanimHelper.KEYS[i])) {
                timesCard.addView(zmanimTimeRow("צ.כוכבים ר״ת",
                        ZmanimHelper.timeForKey(this, ZmanimHelper.KEY_RABBEINU_TAM, dayMillis)));
            }
        }
        timesCard.addView(zmanimTimeRow("דף היומי בבלי", DafYomiHelper.bavliLabel(this, dayMillis)));
        timesCard.addView(zmanimTimeRow("דף היומי ירושלמי", DafYomiHelper.yerushalmiLabel(this, dayMillis)));
        timesCard.addView(moonBlessingRow(dayMillis));
        content.addView(timesCard, cardParams());

        gregorianPickerTitle[0] = addZmanimGregorianPicker(content, dayMillis, showDatePickers);
        addZmanimHebrewPicker(content, dayMillis, showDatePickers);

        LinearLayout backRow = actionRow();
        Button back = pillButton("חזרה", COLOR_SURFACE_2);
        back.setOnClickListener(v -> {
            if (zmanimBackToSettings) {
                showSettings();
            } else {
                showList();
            }
        });
        backRow.addView(back);
        content.addView(backRow);
        setScrollableContent(content, zmanimBackgroundResource);
        if (scrollTarget == 1) {
            scrollToViewTop(dateTitle, dp(8));
        } else if (scrollTarget == 2) {
            scrollToViewTop(navRow, dp(8));
        } else {
            restoreScrollY(scrollY);
        }
    }

    private TextView addZmanimGregorianPicker(LinearLayout content, long dayMillis, boolean showDatePickers) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(new ZmanimSettings(this).timeZoneId()));
        calendar.setTimeInMillis(dayMillis);
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        LinearLayout pickerCard = card();
        TextView title = text("בחירה לפי תאריך לועזי", 13, COLOR_MUTED);
        title.setPadding(0, 0, 0, dp(4));
        pickerCard.addView(title);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        applyDatePickerDirection(row, false);
        NumberPicker day = dateNumberPicker(1, 31, calendar.get(Calendar.DAY_OF_MONTH), 42);
        NumberPicker month = dateNumberPicker(1, 12, calendar.get(Calendar.MONTH) + 1, 44);
        NumberPicker year = dateNumberPicker(currentYear - 10, currentYear + 10, calendar.get(Calendar.YEAR), 64);
        row.addView(pickerColumn("יום", day));
        row.addView(pickerColumn("חודש", month));
        row.addView(pickerColumn("שנה", year));
        pickerCard.addView(row);
        Button show = pillButton("הצג", COLOR_SURFACE_2);
        show.setOnClickListener(v -> {
            showZmanimDay(gregorianZmanimDate(year.getValue(), month.getValue(), day.getValue()), showDatePickers, 0, true);
        });
        pickerCard.addView(show);
        content.addView(pickerCard, cardParams());
        return title;
    }

    private void addZmanimHebrewPicker(LinearLayout content, long dayMillis, boolean showDatePickers) {
        JewishDate jewishDate = new JewishDate(zmanimCalendar(dayMillis));
        int currentYear = new JewishDate(Calendar.getInstance()).getJewishYear();
        LinearLayout pickerCard = card();
        TextView title = text("בחירה לפי תאריך עברי", 13, COLOR_MUTED);
        title.setPadding(0, 0, 0, dp(4));
        pickerCard.addView(title);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        applyDatePickerDirection(row, true);
        NumberPicker day = dateNumberPicker(1, 30, jewishDate.getJewishDayOfMonth(), 42);
        applyDisplayedValues(day, hebrewDayLabels());
        NumberPicker month = dateNumberPicker(1, 13, jewishDate.getJewishMonth(), 44);
        applyDisplayedValues(month, hebrewMonthLabels());
        NumberPicker year = dateNumberPicker(currentYear - 10, currentYear + 10, jewishDate.getJewishYear(), 64);
        row.addView(pickerColumn("יום", day));
        row.addView(pickerColumn("חודש", month));
        row.addView(pickerColumn("שנה", year));
        pickerCard.addView(row);
        Button show = pillButton("הצג", COLOR_SURFACE_2);
        show.setOnClickListener(v -> {
            showZmanimDay(hebrewZmanimDate(year.getValue(), month.getValue(), day.getValue()), showDatePickers, 0, true);
        });
        pickerCard.addView(show);
        content.addView(pickerCard, cardParams());
    }

    private void showQuietRuleEditor(QuietTimeRuleStore.Rule rule) {
        currentScreen = "quiet_rule_editor";
        boolean isNew = rule == null;
        QuietTimeRuleStore.Rule initial = isNew
                ? new QuietTimeRuleStore.Rule(
                UUID.randomUUID().toString(),
                "זמן שקט",
                true,
                QuietTimeRuleStore.Rule.MODE_FIXED,
                13,
                0,
                ZmanimHelper.KEY_SUNSET,
                -20,
                QuietTimeRuleStore.Rule.MODE_FIXED,
                14,
                0,
                ZmanimHelper.KEY_TZAIS,
                10
        )
                : rule;

        LinearLayout content = baseContent();
        addTitle(content, isNew ? "זמן שקט חדש" : "עריכת זמן שקט", "אפשר לבחור שעה רגילה או זמן הלכה");

        EditText name = new EditText(this);
        AppFont.apply(name);
        name.setSingleLine(true);
        name.setText(initial.name);
        name.setHint(UiText.t(this, "שם"));
        name.setTextColor(COLOR_TEXT);
        name.setHintTextColor(0xFF9AA39D);
        name.setGravity(Gravity.CENTER);
        name.setTextSize(15);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setBackground(rounded(COLOR_SURFACE_2, dp(8), 0));
        name.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout.LayoutParams nameParams = matchParams();
        nameParams.height = dp(48);
        content.addView(name, nameParams);

        Switch enabled = new Switch(this);
        setSwitchText(enabled, "פעיל");
        enabled.setChecked(initial.enabled);
        LinearLayout stateCard = card();
        stateCard.addView(enabled);
        content.addView(stateCard, cardParams());

        QuietBoundaryViews start = quietBoundaryCard("התחלה", initial.startMode, initial.startHour, initial.startMinute, initial.startZmanimKey, initial.startOffsetMinutes);
        content.addView(start.card, cardParams());
        QuietBoundaryViews end = quietBoundaryCard("סיום", initial.endMode, initial.endHour, initial.endMinute, initial.endZmanimKey, initial.endOffsetMinutes);
        content.addView(end.card, cardParams());

        LinearLayout actions = actionRow();
        Button save = pillButton("שמירה", COLOR_ACCENT_DARK);
        save.setOnClickListener(v -> {
            QuietTimeRuleStore.Rule updated = new QuietTimeRuleStore.Rule(
                    initial.id,
                    name.getText().toString(),
                    enabled.isChecked(),
                    start.useZmanim.isChecked() ? QuietTimeRuleStore.Rule.MODE_ZMANIM : QuietTimeRuleStore.Rule.MODE_FIXED,
                    start.hour.getValue(),
                    start.minute.getValue(),
                    ZmanimHelper.KEYS[start.zmanim.getSelectedItemPosition()],
                    start.offset.getValue() - 180,
                    end.useZmanim.isChecked() ? QuietTimeRuleStore.Rule.MODE_ZMANIM : QuietTimeRuleStore.Rule.MODE_FIXED,
                    end.hour.getValue(),
                    end.minute.getValue(),
                    ZmanimHelper.KEYS[end.zmanim.getSelectedItemPosition()],
                    end.offset.getValue() - 180
            );
            new QuietTimeRuleStore(this).upsert(updated);
            new ReminderSettings(this).setQuietMinchaMaariv(true);
            store.rescheduleAll();
            ComplicationRefresh.request(this);
            showQuietTimes();
        });
        Button cancel = pillButton("ביטול", COLOR_SURFACE_2);
        cancel.setOnClickListener(v -> showQuietTimes());
        actions.addView(save);
        actions.addView(cancel);
        content.addView(actions);

        setScrollableContent(content);
    }

    private QuietBoundaryViews quietBoundaryCard(String title, String mode, int hourValue, int minuteValue, String zmanimKey, int offsetMinutes) {
        QuietBoundaryViews views = new QuietBoundaryViews();
        views.card = card();
        TextView heading = text(title, 15, COLOR_TEXT);
        AppFont.bold(heading);
        views.card.addView(heading);

        views.useZmanim = new Switch(this);
        setSwitchText(views.useZmanim, "לפי זמני הלכה");
        views.useZmanim.setChecked(QuietTimeRuleStore.Rule.MODE_ZMANIM.equals(mode));
        views.card.addView(views.useZmanim);

        views.hour = numberPicker(0, 23, hourValue);
        views.minute = numberPicker(0, 59, minuteValue);
        views.fixedRow = timePickerRow(views.hour, views.minute);
        views.card.addView(views.fixedRow);

        views.zmanimSection = new LinearLayout(this);
        views.zmanimSection.setOrientation(LinearLayout.VERTICAL);
        views.zmanimSection.setGravity(Gravity.CENTER);
        views.zmanim = new Spinner(this);
        ArrayAdapter<String> adapter = spinnerAdapter(translated(ZmanimHelper.LABELS));
        views.zmanim.setAdapter(adapter);
        views.zmanim.setSelection(ZmanimHelper.indexOf(zmanimKey));
        views.zmanimSection.addView(views.zmanim, matchParams());
        views.offset = quietOffsetPicker(offsetMinutes);
        views.zmanimSection.addView(pickerColumn("לפני / אחרי", views.offset));
        views.card.addView(views.zmanimSection);

        Runnable updateVisibility = () -> {
            int zmanimVisibility = views.useZmanim.isChecked() ? View.VISIBLE : View.GONE;
            int fixedVisibility = views.useZmanim.isChecked() ? View.GONE : View.VISIBLE;
            views.zmanimSection.setVisibility(zmanimVisibility);
            views.fixedRow.setVisibility(fixedVisibility);
        };
        views.useZmanim.setOnClickListener(v -> updateVisibility.run());
        updateVisibility.run();
        return views;
    }

    private void showQuietRuleActions(QuietTimeRuleStore.Rule rule) {
        showActionDialog(
                rule.name,
                translated(new String[]{"עריכה", "מחיקה", "ביטול"}),
                new int[]{COLOR_SURFACE_2, 0xFF7E2A35, COLOR_SURFACE_2},
                new Runnable[]{
                        () -> showQuietRuleEditor(rule),
                        () -> {
                            new QuietTimeRuleStore(this).delete(rule.id);
                            store.rescheduleAll();
                            ComplicationRefresh.request(this);
                            showQuietTimes();
                        },
                        null
                }
        );
    }

    private String quietTimesSummary(QuietTimeRuleStore quietStore) {
        List<QuietTimeRuleStore.Rule> rules = quietStore.getAll();
        int enabled = 0;
        for (QuietTimeRuleStore.Rule rule : rules) {
            if (rule.enabled) {
                enabled++;
            }
        }
        if (rules.isEmpty()) {
            if (AppLanguage.isEnglish(this)) {
                return "Tap to configure quiet times";
            }
            return "לחיצה לפתיחת הגדרת זמני שקט";
        }
        if (AppLanguage.isEnglish(this)) {
            return enabled + " of " + rules.size() + " quiet times active";
        }
        return enabled + " מתוך " + rules.size() + " זמני שקט פעילים";
    }

    private String dafYomiSummary(ReminderSettings settings) {
        if (!settings.dafYomiEnabled()) {
            return UiText.t(this, "כבוי");
        }
        int missed = new DafYomiStore(this).missedItems(this).size();
        if (AppLanguage.isEnglish(this)) {
            String suffix = missed == 0 ? "" : " | To catch up: " + missed;
            return "Active at " + formatTime(settings.dafYomiHour(), settings.dafYomiMinute()) + suffix;
        }
        String suffix = missed == 0 ? "" : " | להשלמה: " + missed;
        return "פעיל בשעה " + formatTime(settings.dafYomiHour(), settings.dafYomiMinute()) + suffix;
    }

    private String moonBlessingSummary(ReminderSettings settings) {
        if (!settings.moonBlessingEnabled()) {
            return UiText.t(this, "כבוי");
        }
        MoonBlessingScheduler.Event next = MoonBlessingScheduler.nextEvent(this, System.currentTimeMillis());
        if (next == null) {
            return UiText.t(this, "פעיל");
        }
        String label = MoonBlessingScheduler.KIND_PRE_START.equals(next.kind)
                ? UiText.t(this, "התראה מקדימה")
                : UiText.t(this, "שאלת ברכת");
        return label + ": " + NextReminderCalculator.formatDateTime(next.triggerAt);
    }

    private void markMoonBlessingHandled(TextView moonHint) {
        MoonBlessingHelper.Window window = MoonBlessingHelper.windowFor(this, System.currentTimeMillis());
        String monthKey = MoonBlessingHelper.monthKey(window);
        new MoonBlessingStore(this).markHandled(monthKey);
        MoonBlessingReceiver.cancelNotification(this);
        MoonBlessingScheduler.schedule(this);
        moonHint.setText(moonBlessingSummary(new ReminderSettings(this)));
        Toast.makeText(this, UiText.t(this, "סומן שבירכת ברכת הלבנה החודש"), Toast.LENGTH_SHORT).show();
    }

    private boolean canMarkMoonBlessingHandled(ReminderSettings settings) {
        if (!settings.jewishMode() || !settings.moonBlessingEnabled()) {
            return false;
        }
        long now = System.currentTimeMillis();
        MoonBlessingHelper.Window window = MoonBlessingHelper.windowFor(this, now);
        if (now < window.startAt || now > window.endAt) {
            return false;
        }
        return !new MoonBlessingStore(this).isHandled(MoonBlessingHelper.monthKey(window));
    }

    private String omerSummary(ReminderSettings settings) {
        if (!settings.omerEnabled()) {
            return UiText.t(this, "כבוי");
        }
        OmerHelper.Item next = OmerHelper.next(this, settings.omerOffsetMinutes());
        if (AppLanguage.isEnglish(this)) {
            String nextLine = next == null ? "Outside the Omer period" : " | Next: " + NextReminderCalculator.formatDateTime(next.triggerAt);
            return "Active " + settings.omerOffsetMinutes() + " minutes after nightfall" + nextLine;
        }
        String nextLine = next == null ? "מחוץ לימי הספירה" : " | הבא: " + NextReminderCalculator.formatDateTime(next.triggerAt);
        return "פעיל " + settings.omerOffsetMinutes() + " דקות אחרי צאת הכוכבים" + nextLine;
    }

    private String jewishDayReminderSummary(ReminderSettings settings) {
        if (!settings.jewishDayRemindersEnabled()) {
            return UiText.t(this, "כבוי");
        }
        JewishDayScheduler.Event next = JewishDayScheduler.nextEvent(this, System.currentTimeMillis());
        if (next == null) {
            return UiText.t(this, "פעיל");
        }
        String prefix = JewishDayScheduler.KIND_TODAY_EREV.equals(next.kind)
                ? UiText.t(this, "היום")
                : UiText.t(this, "מחר");
        return prefix + " " + next.label + ": " + NextReminderCalculator.formatDateTime(next.triggerAt);
    }

    private String tekufaReminderSummary(ReminderSettings settings) {
        if (!settings.tekufaRemindersEnabled()) {
            return UiText.t(this, "כבוי");
        }
        return UiText.t(this, "פעיל") + " | " + TekufaHelper.summary(this, System.currentTimeMillis());
    }

    private String quietRuleDetails(QuietTimeRuleStore.Rule rule) {
        String details = quietBoundaryLabel(rule.startMode, rule.startHour, rule.startMinute, rule.startZmanimKey, rule.startOffsetMinutes)
                + " " + UiText.t(this, "עד") + " "
                + quietBoundaryLabel(rule.endMode, rule.endHour, rule.endMinute, rule.endZmanimKey, rule.endOffsetMinutes);
        if (QuietTimeRuleStore.Rule.MODE_ZMANIM.equals(rule.startMode)
                || QuietTimeRuleStore.Rule.MODE_ZMANIM.equals(rule.endMode)) {
            details += "\n" + quietRuleActualLine(rule);
        }
        return details;
    }

    private String quietBoundaryLabel(String mode, int hour, int minute, String zmanimKey, int offsetMinutes) {
        if (QuietTimeRuleStore.Rule.MODE_ZMANIM.equals(mode)) {
            return formatZmanimOffset(offsetMinutes) + " " + UiText.t(this, ZmanimHelper.label(zmanimKey));
        }
        return formatTime(hour, minute);
    }

    private String quietRuleActualLine(QuietTimeRuleStore.Rule rule) {
        long now = System.currentTimeMillis();
        QuietWindow best = null;
        for (int offset = -1; offset <= 7; offset++) {
            long day = zmanimDayOffset(now, offset);
            long start = quietBoundaryTime(day, rule.startMode, rule.startHour, rule.startMinute, rule.startZmanimKey, rule.startOffsetMinutes);
            long end = quietBoundaryTime(day, rule.endMode, rule.endHour, rule.endMinute, rule.endZmanimKey, rule.endOffsetMinutes);
            if (start == Long.MAX_VALUE || end == Long.MAX_VALUE) {
                continue;
            }
            if (end <= start) {
                end = addDaysPreservingTime(end, 1);
            }
            if (end < now) {
                continue;
            }
            QuietWindow window = new QuietWindow(start, end);
            if (best == null || window.start < best.start) {
                best = window;
            }
        }
        if (best == null) {
            return UiText.t(this, "זמן בפועל:") + " " + UiText.t(this, "לא זמין");
        }
        String prefix = best.start <= now && now <= best.end
                ? UiText.t(this, "חל עכשיו:") + " "
                : UiText.t(this, "הקרוב:") + " ";
        return prefix + relativeDayLabel(best.start) + " " + NextReminderCalculator.formatTime(best.start)
                + "-" + NextReminderCalculator.formatTime(best.end);
    }

    private long quietBoundaryTime(long dayMillis, String mode, int hour, int minute, String zmanimKey, int offsetMinutes) {
        if (QuietTimeRuleStore.Rule.MODE_ZMANIM.equals(mode)) {
            long base = ZmanimHelper.timeForKey(this, zmanimKey, dayMillis);
            return base == Long.MAX_VALUE ? Long.MAX_VALUE : ReminderScheduler.floorToMinute(base + offsetMinutes * 60_000L);
        }
        Calendar calendar = zmanimCalendar(dayMillis);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long addDaysPreservingTime(long time, int days) {
        Calendar calendar = zmanimCalendar(time);
        calendar.add(Calendar.DAY_OF_YEAR, days);
        return calendar.getTimeInMillis();
    }

    private void showBackupExport() {
        currentScreen = "backup_export";
        LinearLayout content = baseContent();
        addTitle(content, "ייצוא גיבוי", "שמירה מחוץ לשעון");
        TextView hint = infoPill("שמירה יוצרת קובץ TXT בתיקיית Documents וגם ב-Download, כדי שיהיה קל למצוא אותו באפליקציית קבצים.", COLOR_WARNING);
        content.addView(hint);

        LinearLayout actions = actionRow();
        Button saveLocal = pillButton("שמירה", COLOR_ACCENT_DARK);
        saveLocal.setOnClickListener(v -> saveBackupToDocuments());
        Button saveAs = pillButton("קובץ", COLOR_SURFACE_2);
        saveAs.setOnClickListener(v -> createBackupFile());
        actions.addView(saveLocal);
        actions.addView(saveAs);
        content.addView(actions);

        LinearLayout actions2 = actionRow();
        Button phone = pillButton("לטלפון", COLOR_ACCENT_DARK);
        phone.setOnClickListener(v -> sendBackupToPhone());
        Button share = pillButton("שליחה", COLOR_SURFACE_2);
        share.setOnClickListener(v -> ReminderBackup.share(this));
        Button copy = pillButton("העתקה", COLOR_SURFACE_2);
        copy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Zmanio backup", ReminderBackup.exportText(this)));
                Toast.makeText(this, UiText.t(this, "הגיבוי הועתק"), Toast.LENGTH_SHORT).show();
            }
        });
        actions2.addView(phone);
        actions2.addView(share);
        actions2.addView(copy);
        content.addView(actions2);

        LinearLayout backRow = actionRow();
        Button back = pillButton("חזרה", COLOR_SURFACE_2);
        back.setOnClickListener(v -> showSettings());
        backRow.addView(back);
        content.addView(backRow);
        setScrollableContent(content);
    }

    private void showBackupImport() {
        currentScreen = "backup_import";
        LinearLayout content = baseContent();
        addTitle(content, "ייבוא גיבוי", "בחירת קובץ לשחזור");
        TextView warning = infoPill("השחזור יחליף את כל התזכורות הקיימות ויתזמן אותן מחדש", COLOR_WARNING);
        content.addView(warning);

        LinearLayout actions = actionRow();
        Button localFiles = pillButton("מהתיקייה", COLOR_ACCENT_DARK);
        localFiles.setOnClickListener(v -> showBackupFileList());
        Button chooseFile = pillButton("בחר קובץ", COLOR_SURFACE_2);
        chooseFile.setOnClickListener(v -> openBackupFile());
        actions.addView(localFiles);
        actions.addView(chooseFile);
        content.addView(actions);

        LinearLayout pasteRow = actionRow();
        Button paste = pillButton("שחזור מהלוח", COLOR_SURFACE_2);
        paste.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null && clipboard.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
                confirmRestoreBackup(text == null ? "" : text.toString());
            } else {
                Toast.makeText(this, UiText.t(this, "אין גיבוי בלוח"), Toast.LENGTH_SHORT).show();
            }
        });
        pasteRow.addView(paste);
        content.addView(pasteRow);

        LinearLayout backRow = actionRow();
        Button back = pillButton("חזרה", COLOR_SURFACE_2);
        back.setOnClickListener(v -> showSettings());
        backRow.addView(back);
        content.addView(backRow);
        setScrollableContent(content);
    }

    private void saveBackupToDocuments() {
        try {
            String fileName = ReminderBackup.saveToPublicDocuments(this);
            Toast.makeText(this, (AppLanguage.isEnglish(this) ? "Saved in Documents and Download: " : "נשמר ב-Documents וב-Download: ") + fileName, Toast.LENGTH_LONG).show();
        } catch (Exception exception) {
            AppLog.e(this, "backup save local failed", exception);
            Toast.makeText(this, UiText.t(this, "לא הצלחתי לשמור בתיקייה"), Toast.LENGTH_SHORT).show();
        }
    }

    private void sendBackupToPhone() {
        Toast.makeText(this, UiText.t(this, "שולח לטלפון..."), Toast.LENGTH_SHORT).show();
        PhoneBackupSender.send(this, new PhoneBackupSender.Callback() {
            @Override
            public void onSuccess(int count) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, UiText.t(MainActivity.this, "נשלח לטלפון"), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void sendLogsToPhone() {
        Toast.makeText(this, UiText.t(this, "שולח לוגים לטלפון..."), Toast.LENGTH_SHORT).show();
        AppLog.d(this, "user requested send logs to phone");
        PhoneLogSender.send(this, new PhoneLogSender.Callback() {
            @Override
            public void onSuccess(int count) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, UiText.t(MainActivity.this, "הלוגים נשלחו לטלפון"), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void confirmClearLogs() {
        new AlertDialog.Builder(this)
                .setMessage(UiText.t(this, "אתה בטוח שאתה רוצה למחוק את הלוגים?"))
                .setPositiveButton(UiText.t(this, "כן"), (dialog, which) -> {
                    AppLog.clear(this);
                    Toast.makeText(this, UiText.t(this, "הלוגים נמחקו"), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(UiText.t(this, "לא"), null)
                .show();
    }

    private void showRestoreFromPhoneStatus() {
        if (RestoreFromPhoneStore.hasPending(this)) {
            pendingRestoreFromPhone = true;
            openPendingRestoreFromPhone();
            return;
        }
        Toast.makeText(this, UiText.t(this, "פותח את אפליקציית הגיבוי בטלפון..."), Toast.LENGTH_SHORT).show();
        PhoneAppOpener.openRestore(this, new PhoneAppOpener.Callback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, UiText.t(MainActivity.this, "פתחתי בטלפון. בחר גיבוי ושלח לשעון."), Toast.LENGTH_LONG).show());
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void createBackupFile() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/octet-stream")
                .putExtra(Intent.EXTRA_TITLE, ReminderBackup.suggestedFileName());
        try {
            startActivityForResult(intent, REQUEST_CREATE_BACKUP_FILE);
        } catch (Exception exception) {
            AppLog.e(this, "backup create document failed", exception);
            Toast.makeText(this, UiText.t(this, "בורר הקבצים לא זמין בשעון"), Toast.LENGTH_SHORT).show();
        }
    }

    private void openBackupFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_OPEN_BACKUP_FILE);
        } catch (Exception exception) {
            AppLog.e(this, "backup open document failed", exception);
            Toast.makeText(this, UiText.t(this, "בורר הקבצים לא זמין בשעון"), Toast.LENGTH_SHORT).show();
        }
    }

    private void showBackupFileList() {
        List<ReminderBackup.BackupEntry> files = ReminderBackup.listAllDocumentBackups(this);
        if (files.isEmpty()) {
            Toast.makeText(this, UiText.t(this, "לא נמצאו קבצי גיבוי בתיקייה"), Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[files.size()];
        for (int i = 0; i < files.size(); i++) {
            names[i] = files.get(i).name;
        }
        new AlertDialog.Builder(this)
                .setTitle(UiText.t(this, "בחר גיבוי"))
                .setItems(names, (dialog, which) -> confirmRestoreBackupEntry(files.get(which)))
                .setNegativeButton(UiText.t(this, "ביטול"), null)
                .show();
    }

    private void confirmRestoreBackup(String backupText) {
        if (backupText == null || backupText.trim().isEmpty()) {
            Toast.makeText(this, UiText.t(this, "אין טקסט גיבוי לייבוא"), Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(UiText.t(this, "לשחזר גיבוי?"))
                .setMessage(UiText.t(this, "כל התזכורות הקיימות יוחלפו בתזכורות מהגיבוי."))
                .setPositiveButton(UiText.t(this, "שחזור"), (dialog, which) -> restoreBackup(backupText))
                .setNegativeButton(UiText.t(this, "ביטול"), null)
                .show();
    }

    private void confirmRestoreBackupFile(File file) {
        new AlertDialog.Builder(this)
                .setTitle(UiText.t(this, "לשחזר גיבוי?"))
                .setMessage(restoreFileConfirmation(file.getName()))
                .setPositiveButton(UiText.t(this, "שחזור"), (dialog, which) -> restoreBackupFile(file))
                .setNegativeButton(UiText.t(this, "ביטול"), null)
                .show();
    }

    private void confirmRestoreBackupEntry(ReminderBackup.BackupEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle(UiText.t(this, "לשחזר גיבוי?"))
                .setMessage(restoreFileConfirmation(entry.name))
                .setPositiveButton(UiText.t(this, "שחזור"), (dialog, which) -> restoreBackupEntry(entry))
                .setNegativeButton(UiText.t(this, "ביטול"), null)
                .show();
    }

    private void confirmRestoreBackupUri(Uri uri) {
        new AlertDialog.Builder(this)
                .setTitle(UiText.t(this, "לשחזר גיבוי?"))
                .setMessage(AppLanguage.isEnglish(this)
                        ? "The selected file will replace all existing reminders."
                        : "הקובץ שבחרת יחליף את כל התזכורות הקיימות.")
                .setPositiveButton(UiText.t(this, "שחזור"), (dialog, which) -> restoreBackupUri(uri))
                .setNegativeButton(UiText.t(this, "ביטול"), null)
                .show();
    }

    private void restoreBackup(String backupText) {
        try {
            int count = ReminderBackup.importText(this, backupText);
            store = new ReminderStore(this);
            Toast.makeText(this, restoredReminderCount(count), Toast.LENGTH_SHORT).show();
            showList();
        } catch (Exception exception) {
            AppLog.e(this, "backup import failed", exception);
            Toast.makeText(this, UiText.t(this, "הגיבוי לא תקין"), Toast.LENGTH_SHORT).show();
        }
    }

    private void restoreBackupFile(File file) {
        try {
            int count = ReminderBackup.importFile(this, file);
            store = new ReminderStore(this);
            Toast.makeText(this, restoredReminderCount(count), Toast.LENGTH_SHORT).show();
            showList();
        } catch (Exception exception) {
            AppLog.e(this, "backup file import failed", exception);
            Toast.makeText(this, UiText.t(this, "הקובץ לא תקין"), Toast.LENGTH_SHORT).show();
        }
    }

    private void restoreBackupEntry(ReminderBackup.BackupEntry entry) {
        try {
            int count = ReminderBackup.importEntry(this, entry);
            store = new ReminderStore(this);
            Toast.makeText(this, restoredReminderCount(count), Toast.LENGTH_SHORT).show();
            showList();
        } catch (Exception exception) {
            AppLog.e(this, "backup entry import failed", exception);
            Toast.makeText(this, UiText.t(this, "הקובץ לא תקין"), Toast.LENGTH_SHORT).show();
        }
    }

    private void restoreBackupUri(Uri uri) {
        try {
            int count = ReminderBackup.importUri(this, uri);
            store = new ReminderStore(this);
            Toast.makeText(this, restoredReminderCount(count), Toast.LENGTH_SHORT).show();
            showList();
        } catch (Exception exception) {
            AppLog.e(this, "backup uri import failed", exception);
            Toast.makeText(this, UiText.t(this, "הקובץ לא תקין"), Toast.LENGTH_SHORT).show();
        }
    }

    private String restoreFileConfirmation(String fileName) {
        return AppLanguage.isEnglish(this)
                ? "The file " + fileName + " will replace all existing reminders."
                : "הקובץ " + fileName + " יחליף את כל התזכורות הקיימות.";
    }

    private String restoredReminderCount(int count) {
        return AppLanguage.isEnglish(this)
                ? "Restored " + count + " reminders"
                : "שוחזרו " + count + " תזכורות";
    }

    private void showBlessingReminder() {
        currentScreen = "blessing";
        LinearLayout content = baseContent();
        int blessingMinutes = new ReminderSettings(this).blessingReminderMinutes();
        addTitle(content, "תזכורת לברכה", blessingReminderSubtitle(blessingMinutes));

        TextView hint = infoPill(blessingReminderHint(blessingMinutes), COLOR_WARNING);
        content.addView(hint);

        for (String blessing : BLESSING_NAMES) {
            if (BLESSING_SHEMA_ON_TIME.equals(blessing) && !isShemaOnTimeButtonVisible()) {
                continue;
            }
            Button button = pillButton(blessing, COLOR_SURFACE_2);
            clearButtonIcon(button);
            button.setTextSize(13);
            button.setOnClickListener(v -> createBlessingReminder(blessing));
            content.addView(button, matchParams());
        }

        LinearLayout actions = actionRow();
        actions.setPadding(0, dp(12), 0, dp(8));
        Button back = pillButton("חזרה", COLOR_ACCENT_DARK);
        back.setOnClickListener(v -> showList());
        actions.addView(back);
        content.addView(actions);
        setScrollableContent(content);
    }

    private void createBlessingReminder(String blessing) {
        if (requestExactAlarmAccessIfNeeded(true)) {
            return;
        }
        if (requestFullScreenIntentAccessIfNeeded(true)) {
            return;
        }
        if (BLESSING_SHEMA_ON_TIME.equals(blessing)) {
            if (createShemaOnTimeReminder()) {
                return;
            }
        }
        long now = System.currentTimeMillis();
        int blessingMinutes = new ReminderSettings(this).blessingReminderMinutes();
        long remindAt = ReminderScheduler.floorToMinute(now) + blessingMinutes * 60_000L;
        long maxAt = remindAt;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(remindAt);
        String displayBlessing = UiText.t(this, blessing);
        String name = displayBlessing + (AppLanguage.isEnglish(this) ? " | until " : " | עד ") + NextReminderCalculator.formatTime(maxAt);
        Reminder reminder = new Reminder(
                UUID.randomUUID().toString(),
                name,
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                new HashSet<>(),
                true,
                remindAt,
                false,
                ZmanimHelper.KEY_CHATZOS,
                0,
                true
        );
        store.upsert(reminder);
        Toast.makeText(this, UiText.t(this, "נוצרה תזכורת ל-") + NextReminderCalculator.formatTime(remindAt), Toast.LENGTH_SHORT).show();
        showList();
    }

    private boolean createShemaOnTimeReminder() {
        long targetDay = shemaOnTimeTargetDayMillis();
        if (targetDay == Long.MAX_VALUE) {
            Toast.makeText(this, UiText.t(this, "לא ניתן לחשב את זמן ההלכה להיום"), Toast.LENGTH_LONG).show();
            return true;
        }
        long tzeis = ZmanimHelper.timeForKey(this, ZmanimHelper.KEY_TZAIS, targetDay);
        if (tzeis == Long.MAX_VALUE) {
            Toast.makeText(this, UiText.t(this, "לא ניתן לחשב את זמן ההלכה להיום"), Toast.LENGTH_LONG).show();
            return true;
        }
        int shemaOffsetMinutes = new ReminderSettings(this).shemaOnTimeOffsetMinutes();
        long remindAt = tzeis + shemaOffsetMinutes * 60_000L;
        if (remindAt <= System.currentTimeMillis()) {
            return false;
        }
        remindAt = ReminderScheduler.floorToMinute(remindAt);
        Calendar calendar = zmanimCalendar(remindAt);
        String displayBlessing = UiText.t(this, BLESSING_SHEMA_ON_TIME);
        String name = displayBlessing + (AppLanguage.isEnglish(this) ? " | at " : " | בשעה ") + NextReminderCalculator.formatTime(remindAt);
        Reminder reminder = new Reminder(
                UUID.randomUUID().toString(),
                name,
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                new HashSet<>(),
                true,
                remindAt,
                false,
                ZmanimHelper.KEY_TZAIS,
                shemaOffsetMinutes,
                true
        );
        store.upsert(reminder);
        Toast.makeText(this, UiText.t(this, "נוצרה תזכורת ל-") + NextReminderCalculator.formatTime(remindAt), Toast.LENGTH_SHORT).show();
        showList();
        return true;
    }

    private boolean isShemaOnTimeButtonVisible() {
        return shemaOnTimeTargetDayMillis() != Long.MAX_VALUE;
    }

    private long shemaOnTimeTargetDayMillis() {
        long now = System.currentTimeMillis();
        long today = zmanimStartOfDay(now);
        long plagToday = ZmanimHelper.timeForKey(this, ZmanimHelper.KEY_PLAG, today);
        long alosTomorrow = ZmanimHelper.timeForKey(this, ZmanimHelper.KEY_ALOS, zmanimDayOffset(today, 1));
        if (plagToday != Long.MAX_VALUE && alosTomorrow != Long.MAX_VALUE && now >= plagToday && now < alosTomorrow) {
            return today;
        }
        long yesterday = zmanimDayOffset(today, -1);
        long plagYesterday = ZmanimHelper.timeForKey(this, ZmanimHelper.KEY_PLAG, yesterday);
        long alosToday = ZmanimHelper.timeForKey(this, ZmanimHelper.KEY_ALOS, today);
        if (plagYesterday != Long.MAX_VALUE && alosToday != Long.MAX_VALUE && now >= plagYesterday && now < alosToday) {
            return yesterday;
        }
        return Long.MAX_VALUE;
    }

    private String blessingReminderSubtitle(int minutes) {
        if (AppLanguage.isEnglish(this)) {
            return "Critical reminder in " + minutes + " minutes";
        }
        return "תזכורת חיונית לעוד " + minutes + " דקות";
    }

    private String blessingReminderHint(int minutes) {
        if (AppLanguage.isEnglish(this)) {
            return "The blessing time will be shown as now + " + minutes + " minutes";
        }
        return "זמן הברכה יוצג לפי עכשיו + " + minutes + " דקות";
    }

    private String zmanimLocationLine() {
        ZmanimSettings zmanimSettings = new ZmanimSettings(this);
        String coordinates = ZmanimSettings.coordinatesName(zmanimSettings.latitude(), zmanimSettings.longitude());
        String name = displayLocationName(zmanimSettings.name());
        return coordinates.equals(name) ? coordinates : name + "\n" + coordinates;
    }

    private String displayLocationName(String name) {
        return IsraeliCityResolver.localizedName(name, AppLanguage.isEnglish(this));
    }

    private void requestFreshZmanimLocation(TextView locationValue) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissions();
            Toast.makeText(this, UiText.t(this, "צריך לאשר מיקום ואז ללחוץ שוב"), Toast.LENGTH_SHORT).show();
            return;
        }
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager == null) {
            Toast.makeText(this, UiText.t(this, "לא ניתן לקבל מיקום"), Toast.LENGTH_SHORT).show();
            return;
        }
        Location last = bestLastKnownLocation(manager);
        if (last != null) {
            saveZmanimLocation(last, locationValue);
        }
        LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                saveZmanimLocation(location, locationValue);
                manager.removeUpdates(this);
            }

            @Override
            public void onProviderDisabled(String provider) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }
        };
        boolean requested = requestSingleLocation(manager, LocationManager.GPS_PROVIDER, listener)
                || requestSingleLocation(manager, LocationManager.NETWORK_PROVIDER, listener);
        if (!requested && last == null) {
            Toast.makeText(this, UiText.t(this, "לא נמצא ספק מיקום פעיל"), Toast.LENGTH_SHORT).show();
        } else if (last == null) {
            Toast.makeText(this, UiText.t(this, "מבקש מיקום חדש..."), Toast.LENGTH_SHORT).show();
        }
    }

    private Location bestLastKnownLocation(LocationManager manager) {
        Location best = null;
        best = fresher(best, lastKnown(manager, LocationManager.GPS_PROVIDER));
        best = fresher(best, lastKnown(manager, LocationManager.NETWORK_PROVIDER));
        return best;
    }

    private Location lastKnown(LocationManager manager, String provider) {
        try {
            return manager.isProviderEnabled(provider) ? manager.getLastKnownLocation(provider) : null;
        } catch (SecurityException ignored) {
            return null;
        }
    }

    private Location fresher(Location current, Location candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.getTime() > current.getTime()) {
            return candidate;
        }
        return current;
    }

    private boolean requestSingleLocation(LocationManager manager, String provider, LocationListener listener) {
        try {
            if (!manager.isProviderEnabled(provider)) {
                return false;
            }
            manager.requestSingleUpdate(provider, listener, null);
            return true;
        } catch (SecurityException ignored) {
            return false;
        }
    }

    private void saveZmanimLocation(Location location, TextView locationValue) {
        double latitude = ZmanimSettings.sanitizeLatitude(location.getLatitude());
        double longitude = ZmanimSettings.sanitizeLongitude(location.getLongitude());
        double elevation = ZmanimSettings.sanitizeElevation(location.hasAltitude() ? location.getAltitude() : 0);
        ZmanimSettings settings = new ZmanimSettings(this);
        boolean changed = Math.abs(settings.latitude() - latitude) > 0.000001
                || Math.abs(settings.longitude() - longitude) > 0.000001
                || Math.abs(settings.elevation() - elevation) > 0.1
                || !TimeZone.getDefault().getID().equals(settings.timeZoneId());
        String coordinates = ZmanimSettings.coordinatesName(latitude, longitude);
        settings.update(
                coordinates,
                latitude,
                longitude,
                elevation,
                TimeZone.getDefault().getID()
        );
        locationValue.setText(coordinates + "\n" + UiText.t(this, "מזהה שם עיר..."));
        AppLog.d(this, "zmanim location received lat=" + latitude + " lon=" + longitude
                + " elevation=" + elevation + " changed=" + changed);
        if (changed) {
            scheduleAfterLocationChange();
        }
        Toast.makeText(this, UiText.t(this, "המיקום עודכן"), Toast.LENGTH_SHORT).show();
        resolveLocationName(latitude, longitude, locationValue, false);
    }

    private void scheduleAfterLocationChange() {
        if (pendingLocationReschedule != null) {
            mainHandler.removeCallbacks(pendingLocationReschedule);
        }
        Context appContext = getApplicationContext();
        pendingLocationReschedule = () -> LOCATION_RESCHEDULER.execute(() -> {
            ZmanimRescheduler.rescheduleNow(appContext);
        });
        mainHandler.postDelayed(pendingLocationReschedule, LOCATION_RESCHEDULE_DEBOUNCE_MS);
    }

    private void resolveStoredZmanimLocationName(TextView locationValue, boolean notifyUser) {
        ZmanimSettings settings = new ZmanimSettings(this);
        locationValue.setText(ZmanimSettings.coordinatesName(settings.latitude(), settings.longitude())
                + "\n" + UiText.t(this, "מזהה שם עיר..."));
        resolveLocationName(settings.latitude(), settings.longitude(), locationValue, notifyUser);
    }

    private void resolveLocationName(double latitude, double longitude, TextView locationValue, boolean notifyUser) {
        if (!Geocoder.isPresent()) {
            applyResolvedLocationName(latitude, longitude, null, locationValue, notifyUser);
            return;
        }
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        AtomicBoolean completed = new AtomicBoolean(false);
        Runnable timeout = () -> {
            if (completed.compareAndSet(false, true)) {
                applyResolvedLocationName(latitude, longitude, null, locationValue, notifyUser);
            }
        };
        mainHandler.postDelayed(timeout, GEOCODER_TIMEOUT_MS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                geocoder.getFromLocation(latitude, longitude, 1, new Geocoder.GeocodeListener() {
                    @Override
                    public void onGeocode(List<Address> addresses) {
                        completeLocationNameResolution(
                                completed,
                                timeout,
                                latitude,
                                longitude,
                                readableLocationName(addresses),
                                locationValue,
                                notifyUser
                        );
                    }

                    @Override
                    public void onError(String errorMessage) {
                        completeLocationNameResolution(
                                completed,
                                timeout,
                                latitude,
                                longitude,
                                null,
                                locationValue,
                                notifyUser
                        );
                    }
                });
            } catch (RuntimeException ignored) {
                completeLocationNameResolution(
                        completed,
                        timeout,
                        latitude,
                        longitude,
                        null,
                        locationValue,
                        notifyUser
                );
            }
            return;
        }
        new Thread(() -> {
            String name = null;
            try {
                name = readableLocationName(geocoder.getFromLocation(latitude, longitude, 1));
            } catch (Exception ignored) {
            }
            String resolvedName = name;
            completeLocationNameResolution(
                    completed,
                    timeout,
                    latitude,
                    longitude,
                    resolvedName,
                    locationValue,
                    notifyUser
            );
        }, "zmanim-geocoder").start();
    }

    private void completeLocationNameResolution(AtomicBoolean completed, Runnable timeout, double latitude, double longitude, String serverName, TextView locationValue, boolean notifyUser) {
        mainHandler.post(() -> {
            if (!completed.compareAndSet(false, true)) {
                return;
            }
            mainHandler.removeCallbacks(timeout);
            applyResolvedLocationName(latitude, longitude, serverName, locationValue, notifyUser);
        });
    }

    private String readableLocationName(List<Address> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return null;
        }
        Address address = addresses.get(0);
        String city = firstNonEmpty(address.getLocality(), address.getSubAdminArea(), address.getAdminArea());
        String country = address.getCountryName();
        if (city != null && country != null) {
            return city + ", " + country;
        }
        return city == null ? country : city;
    }

    private void applyResolvedLocationName(double latitude, double longitude, String serverName, TextView locationValue, boolean notifyUser) {
        ZmanimSettings settings = new ZmanimSettings(this);
        if (Math.abs(settings.latitude() - latitude) > 0.000001
                || Math.abs(settings.longitude() - longitude) > 0.000001) {
            return;
        }
        boolean serverResolved = serverName != null && !serverName.trim().isEmpty();
        String resolvedName = serverResolved ? serverName : IsraeliCityResolver.nearestName(latitude, longitude);
        if (resolvedName == null) {
            resolvedName = ZmanimSettings.coordinatesName(latitude, longitude);
        }
        settings.update(resolvedName, latitude, longitude, settings.elevation(), settings.timeZoneId());
        if (locationValue != null && locationValue.isAttachedToWindow()) {
            locationValue.setText(zmanimLocationLine());
        }
        if (notifyUser) {
            String message = serverResolved
                    ? "שם העיר עודכן"
                    : resolvedName.endsWith("(בקירוב)")
                    ? "אין תשובה מהשרת; הוצג יישוב קרוב"
                    : "אין תשובה מהשרת; נשמרו הקואורדינטות";
            Toast.makeText(this, UiText.t(this, message), Toast.LENGTH_LONG).show();
        }
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private void completeUpcoming(Reminder reminder) {
        ReminderSnoozeStore snoozeStore = new ReminderSnoozeStore(this);
        NextReminderCalculator.NextReminder next = NextReminderCalculator.nextForReminder(this, reminder, snoozeStore, new ReminderEventStore(this));
        if (next == null) {
            return;
        }
        ReminderEventStore eventStore = new ReminderEventStore(this);
        if (next.snoozed) {
            ReminderScheduler.cancelSnooze(this, reminder.id, reminder.name);
            eventStore.markLatestPendingDone(reminder.id);
            if (reminder.isOneTime()) {
                store.delete(reminder);
            }
        } else {
            ReminderScheduler.skipOccurrence(this, reminder, next.scheduledAt);
            eventStore.markUpcomingDone(reminder.id, next.reminderName, reminder.description, next.scheduledAt);
            if (reminder.isOneTime()) {
                store.delete(reminder);
            }
        }
        showList();
    }

    private void showSnoozeUpcomingOptions(Reminder reminder) {
        ReminderSnoozeStore snoozeStore = new ReminderSnoozeStore(this);
        NextReminderCalculator.NextReminder next = NextReminderCalculator.nextForReminder(this, reminder, snoozeStore, new ReminderEventStore(this));
        if (next == null) {
            Toast.makeText(this, UiText.t(this, "אין תזכורת קרובה לדחייה"), Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout dialogContent = new LinearLayout(this);
        dialogContent.setOrientation(LinearLayout.VERTICAL);
        dialogContent.setGravity(Gravity.CENTER);
        dialogContent.setPadding(dp(14), dp(14), dp(14), dp(14));
        dialogContent.setBackground(rounded(COLOR_SURFACE, dp(8), 0x334D5A52));

        TextView title = text("דחיית התזכורת", 15, COLOR_TEXT);
        AppFont.bold(title);
        TextView subtitle = text("המופע הקרוב יידחה מהזמן שלו", 11, COLOR_MUTED);
        subtitle.setPadding(0, dp(2), 0, dp(8));
        dialogContent.addView(title);
        dialogContent.addView(subtitle);

        AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout row1 = actionRow();
        Button snooze30 = pillButton("30 דקות", COLOR_SURFACE_2);
        snooze30.setOnClickListener(v -> {
            dialog.dismiss();
            snoozeUpcoming(reminder, next, 30);
        });
        Button snooze15 = pillButton("15 דקות", COLOR_SURFACE_2);
        snooze15.setOnClickListener(v -> {
            dialog.dismiss();
            snoozeUpcoming(reminder, next, 15);
        });
        row1.addView(snooze30);
        row1.addView(snooze15);
        dialogContent.addView(row1);

        LinearLayout row2 = actionRow();
        Button snooze120 = pillButton("שעתיים", COLOR_SURFACE_2);
        snooze120.setOnClickListener(v -> {
            dialog.dismiss();
            snoozeUpcoming(reminder, next, 120);
        });
        Button snooze60 = pillButton("שעה", COLOR_SURFACE_2);
        snooze60.setOnClickListener(v -> {
            dialog.dismiss();
            snoozeUpcoming(reminder, next, 60);
        });
        row2.addView(snooze120);
        row2.addView(snooze60);
        dialogContent.addView(row2);

        LinearLayout customRow = new LinearLayout(this);
        customRow.setGravity(Gravity.CENTER);
        customRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        NumberPicker customMinutes = numberPicker(1, 240, new ReminderSettings(this).autoSnoozeMinutes());
        customRow.addView(pickerColumn("דקות", customMinutes));
        Button custom = pillButton("דחייה", COLOR_ACCENT_DARK);
        custom.setOnClickListener(v -> {
            dialog.dismiss();
            snoozeUpcoming(reminder, next, customMinutes.getValue());
        });
        customRow.addView(custom);
        dialogContent.addView(customRow);

        Button cancel = pillButton("ביטול", COLOR_SURFACE_2);
        cancel.setOnClickListener(v -> dialog.dismiss());
        dialogContent.addView(cancel);

        dialog.setView(dialogContent);
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
        });
        dialog.show();
    }

    private void snoozeUpcoming(Reminder reminder, NextReminderCalculator.NextReminder next, int minutes) {
        ReminderSnoozeStore snoozeStore = new ReminderSnoozeStore(this);
        long originalScheduledAt = next.scheduledAt;
        for (ReminderSnoozeStore.Snooze snooze : snoozeStore.getAll()) {
            if (snooze.reminderId.equals(reminder.id)) {
                originalScheduledAt = snooze.originalScheduledAt;
                break;
            }
        }
        if (!next.snoozed) {
            ReminderScheduler.skipOccurrence(this, reminder, next.scheduledAt);
        }
        long baseAt = Math.max(System.currentTimeMillis(), next.scheduledAt);
        long nextScheduledAt = ReminderScheduler.scheduleSnoozeAt(this, reminder.id, next.reminderName, baseAt + minutes * 60_000L, originalScheduledAt);
        new ReminderEventStore(this).markUpcomingSnoozed(reminder.id, next.reminderName, reminder.description, originalScheduledAt, minutes, nextScheduledAt);
        AppLog.d(this, "main snooze upcoming id=" + reminder.id + " minutes=" + minutes + " at=" + NextReminderCalculator.formatDateTime(nextScheduledAt));
        Toast.makeText(this,
                (AppLanguage.isEnglish(this) ? "Snoozed until " : "נדחה ל-") + NextReminderCalculator.formatTime(nextScheduledAt),
                Toast.LENGTH_SHORT).show();
        showList();
    }

    private void showEditor(Reminder reminder) {
        currentScreen = "editor";
        boolean jewishMode = new ReminderSettings(this).jewishMode();
        editingReminder = reminder;
        Calendar initialDate = Calendar.getInstance();
        if (reminder == null) {
            initialDate.add(Calendar.MINUTE, 1);
        } else if (reminder.isOneTime()) {
            initialDate.setTimeInMillis(reminder.oneTimeAt);
        } else if (reminder.isPeriodic()) {
            initialDate.setTimeInMillis(PeriodicReminderHelper.startMillis(reminder));
        }
        selectedHour = reminder == null ? initialDate.get(Calendar.HOUR_OF_DAY) : reminder.hour;
        selectedMinute = reminder == null ? initialDate.get(Calendar.MINUTE) : reminder.minute;
        selectedYear = initialDate.get(Calendar.YEAR);
        selectedMonth = initialDate.get(Calendar.MONTH) + 1;
        selectedDayOfMonth = initialDate.get(Calendar.DAY_OF_MONTH);
        if (reminder != null && reminder.isPeriodic() && reminder.periodicHebrew) {
            selectedYear = reminder.periodicStartYear;
            selectedMonth = reminder.periodicStartMonth;
            selectedDayOfMonth = reminder.periodicStartDay;
        }
        if (reminder != null && reminder.isAnnualEvent()) {
            selectedMonth = reminder.annualMonth;
            selectedDayOfMonth = reminder.annualDay;
        }
        selectedOneTime = reminder == null || reminder.isOneTime();
        selectedPeriodic = reminder != null && reminder.isPeriodic();
        selectedAnnual = reminder != null && reminder.isAnnualEvent();
        if (selectedPeriodic || selectedAnnual) {
            selectedOneTime = false;
        }
        selectedEnabled = reminder == null || reminder.enabled;
        selectedCritical = reminder != null && reminder.critical;
        selectedUseZmanim = jewishMode && reminder != null && reminder.useZmanim;
        selectedZmanimKey = reminder == null ? ZmanimHelper.KEY_CHATZOS : reminder.zmanimKey;
        selectedZmanimOffsetMinutes = reminder == null ? 0 : reminder.zmanimOffsetMinutes;
        selectedPeriodicHebrew = jewishMode && reminder != null && reminder.periodicHebrew;
        selectedPeriodicInterval = reminder == null ? 1 : reminder.periodicInterval;
        selectedPeriodicUnit = reminder == null ? Reminder.PERIOD_UNIT_DAYS : reminder.periodicUnit;
        selectedPeriodicEndHour = reminder == null ? 23 : reminder.periodicEndHour;
        selectedPeriodicEndMinute = reminder == null ? 59 : reminder.periodicEndMinute;
        selectedAnnualHebrew = jewishMode && reminder != null && reminder.annualHebrew;
        selectedAnnualAdvanceHours = reminder == null ? 0 : reminder.annualAdvanceHours;
        selectedAnnualCounter = reminder == null ? 0 : reminder.annualCounter;
        selectedDays = reminder == null
                ? new HashSet<>(Arrays.asList(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY))
                : new HashSet<>(reminder.days);

        LinearLayout content = baseContent();
        FrameLayout editorHeader = new FrameLayout(this);
        LinearLayout editorTitles = new LinearLayout(this);
        editorTitles.setOrientation(LinearLayout.VERTICAL);
        editorTitles.setGravity(Gravity.CENTER_HORIZONTAL);
        addTitle(editorTitles, reminder == null ? "תזכורת חדשה" : "עריכת תזכורת", "שם, זמן, סוג ופעילות");
        editorHeader.addView(editorTitles, new FrameLayout.LayoutParams(-1, -2));

        Button editorHelp = new Button(this);
        AppFont.bold(editorHelp);
        editorHelp.setText("?");
        editorHelp.setTextSize(21);
        editorHelp.setTextColor(COLOR_LUXURY_GOLD);
        editorHelp.setShadowLayer(dp(2), 0f, dp(1), 0xCC000000);
        editorHelp.setAllCaps(false);
        editorHelp.setPadding(0, 0, 0, 0);
        editorHelp.setMinWidth(0);
        editorHelp.setMinHeight(0);
        editorHelp.setContentDescription(getString(R.string.ui_editor_help_button));
        editorHelp.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        editorHelp.setBackgroundColor(Color.TRANSPARENT);
        editorHelp.setStateListAnimator(null);
        FrameLayout.LayoutParams helpParams = new FrameLayout.LayoutParams(dp(28), dp(28));
        helpParams.gravity = Gravity.TOP | Gravity.LEFT;
        helpParams.setMargins(0, dp(8), 0, 0);
        editorHeader.addView(editorHelp, helpParams);
        content.addView(editorHeader, matchParams());

        nameInput = new EditText(this);
        AppFont.apply(nameInput);
        nameInput.setSingleLine(true);
        nameInput.setText(reminder == null ? "" : reminder.name);
        nameInput.setHint(UiText.t(this, "שם"));
        nameInput.setTextColor(COLOR_TEXT);
        nameInput.setHintTextColor(0xFF9AA39D);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        nameInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        nameInput.setGravity(Gravity.CENTER);
        nameInput.setTextSize(15);
        nameInput.setBackground(rounded(COLOR_SURFACE_2, dp(8), 0));
        nameInput.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout.LayoutParams nameParams = matchParams();
        nameParams.height = dp(60);
        content.addView(nameInput, nameParams);

        descriptionInput = new EditText(this);
        AppFont.apply(descriptionInput);
        descriptionInput.setSingleLine(false);
        descriptionInput.setMinLines(2);
        descriptionInput.setMaxLines(4);
        descriptionInput.setText(reminder == null ? "" : reminder.description);
        descriptionInput.setHint(UiText.t(this, "תיאור (אופציונלי)"));
        descriptionInput.setTextColor(COLOR_TEXT);
        descriptionInput.setHintTextColor(0xFF9AA39D);
        descriptionInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        descriptionInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        descriptionInput.setOnEditorActionListener((view, actionId, event) -> {
            boolean done = actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP);
            if (!done) {
                return false;
            }
            hideKeyboard(descriptionInput);
            descriptionInput.clearFocus();
            return true;
        });
        descriptionInput.setGravity(Gravity.CENTER);
        descriptionInput.setTextSize(14);
        descriptionInput.setBackground(rounded(COLOR_SURFACE_2, dp(8), 0));
        descriptionInput.setPadding(dp(10), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams descriptionParams = matchParams();
        descriptionParams.height = dp(78);
        descriptionParams.setMargins(0, dp(6), 0, dp(4));
        content.addView(descriptionInput, descriptionParams);

        LinearLayout stateCard = card();
        LinearLayout stateControls = new LinearLayout(this);
        stateControls.setOrientation(LinearLayout.VERTICAL);
        Switch editorSwitch = new Switch(this);
        setSwitchText(editorSwitch, "פעילה");
        editorSwitch.setChecked(selectedEnabled);
        editorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> selectedEnabled = isChecked);
        stateControls.addView(editorSwitch);

        Switch criticalSwitch = new Switch(this);
        setSwitchText(criticalSwitch, "חיונית");
        criticalSwitch.setChecked(selectedCritical);
        criticalSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> selectedCritical = isChecked);
        stateControls.addView(criticalSwitch);
        stateCard.addView(stateControls, matchParams());

        LinearLayout modeRow = actionRow();
        modeRow.setPadding(0, dp(4), 0, dp(12));
        Button recurringButton = smallWideButton("קבועה", (!selectedOneTime && !selectedPeriodic && !selectedAnnual) ? COLOR_ACCENT_DARK : COLOR_SURFACE_2);
        Button oneTimeButton = smallWideButton("חד פעמית", selectedOneTime ? COLOR_ACCENT_DARK : COLOR_SURFACE_2);
        Button periodicButton = smallWideButton("מחזורית", selectedPeriodic ? COLOR_ACCENT_DARK : COLOR_SURFACE_2);
        Button annualButton = smallWideButton("שנתית", selectedAnnual ? COLOR_ACCENT_DARK : COLOR_SURFACE_2);
        setModeChoiceParams(oneTimeButton);
        setModeChoiceParams(recurringButton);
        setModeChoiceParams(periodicButton);
        setModeChoiceParams(annualButton);
        modeRow.addView(oneTimeButton);
        modeRow.addView(recurringButton);
        LinearLayout modeRow2 = actionRow();
        modeRow2.setPadding(0, 0, 0, dp(8));
        modeRow2.addView(periodicButton);
        modeRow2.addView(annualButton);
        modeRow.setLayoutParams(matchParams());
        modeRow2.setLayoutParams(matchParams());
        stateCard.addView(modeRow);
        stateCard.addView(modeRow2);
        content.addView(stateCard, cardParams());

        LinearLayout timeCard = card();
        LinearLayout zmanimCard = card();
        NumberPicker hourPicker = numberPicker(0, 23, selectedHour);
        hourPicker.setOnValueChangedListener((picker, oldValue, newValue) -> selectedHour = newValue);
        NumberPicker minutePicker = numberPicker(0, 59, selectedMinute);
        minutePicker.setOnValueChangedListener((picker, oldValue, newValue) -> selectedMinute = newValue);

        LinearLayout timePickers = timePickerRow(hourPicker, minutePicker);
        timePickers.setPadding(0, dp(2), 0, 0);
        timeCard.addView(timePickers);

        Switch zmanimSwitch = new Switch(this);
        setSwitchText(zmanimSwitch, "זמני הלכה");
        zmanimSwitch.setChecked(selectedUseZmanim);
        zmanimCard.addView(zmanimSwitch);

        Spinner zmanimSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = spinnerAdapter(translated(ZmanimHelper.LABELS));
        zmanimSpinner.setAdapter(adapter);
        zmanimSpinner.setSelection(ZmanimHelper.indexOf(selectedZmanimKey));
        zmanimSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                selectedZmanimKey = ZmanimHelper.KEYS[position];
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        zmanimCard.addView(zmanimSpinner, matchParams());

        NumberPicker offsetPicker = offsetNumberPicker(selectedZmanimOffsetMinutes);
        zmanimCard.addView(pickerColumn("לפני / אחרי", offsetPicker));

        TextView location = text(UiText.t(this, "מיקום") + ": "
                + displayLocationName(new ZmanimSettings(this).name()), 11, COLOR_MUTED);
        location.setPadding(0, dp(4), 0, 0);
        zmanimCard.addView(location);
        if (jewishMode) {
            content.addView(zmanimCard, cardParams());
        }

        LinearLayout dateSection = section();
        addDatePicker(dateSection);
        LinearLayout daysSection = section();
        addDaysPicker(daysSection);
        LinearLayout periodicSection = section();
        addPeriodicPicker(periodicSection);
        LinearLayout annualSection = section();
        addAnnualPicker(annualSection);
        content.addView(dateSection);
        content.addView(timeCard, cardParams());
        content.addView(daysSection);
        content.addView(periodicSection);
        content.addView(annualSection);
        updateModeSections(dateSection, daysSection, periodicSection, annualSection, recurringButton, oneTimeButton, periodicButton, annualButton);
        updateZmanimSections(timeCard, zmanimSpinner, offsetPicker, location);
        recurringButton.setOnClickListener(v -> {
            selectedOneTime = false;
            selectedPeriodic = false;
            selectedAnnual = false;
            updateModeSections(dateSection, daysSection, periodicSection, annualSection, recurringButton, oneTimeButton, periodicButton, annualButton);
        });
        oneTimeButton.setOnClickListener(v -> {
            selectedOneTime = true;
            selectedPeriodic = false;
            selectedAnnual = false;
            updateModeSections(dateSection, daysSection, periodicSection, annualSection, recurringButton, oneTimeButton, periodicButton, annualButton);
        });
        periodicButton.setOnClickListener(v -> {
            selectedOneTime = false;
            selectedPeriodic = true;
            selectedAnnual = false;
            updateModeSections(dateSection, daysSection, periodicSection, annualSection, recurringButton, oneTimeButton, periodicButton, annualButton);
        });
        annualButton.setOnClickListener(v -> {
            selectedOneTime = false;
            selectedPeriodic = false;
            selectedAnnual = true;
            updateModeSections(dateSection, daysSection, periodicSection, annualSection, recurringButton, oneTimeButton, periodicButton, annualButton);
        });
        zmanimSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            selectedUseZmanim = isChecked;
            updateZmanimSections(timeCard, zmanimSpinner, offsetPicker, location);
        });

        LinearLayout actions = actionRow();
        actions.setPadding(0, dp(12), 0, dp(10));
        Button save = pillButton("שמירה", COLOR_ACCENT_DARK);
        save.setOnClickListener(v -> saveReminder());
        Button cancel = pillButton("ביטול", COLOR_SURFACE_2);
        cancel.setOnClickListener(v -> showList());
        actions.addView(save);
        actions.addView(cancel);
        content.addView(actions);

        FrameLayout editorRoot = setScrollableContent(content);
        addEditorHelp(
                editorRoot,
                editorHelp,
                nameInput,
                descriptionInput,
                stateControls,
                recurringButton,
                oneTimeButton,
                periodicButton,
                annualButton,
                jewishMode ? zmanimCard : null
        );
        startEditorHelpAutomaticallyIfNeeded(editorHelp);
    }

    private void saveReminder() {
        if (requestExactAlarmAccessIfNeeded(true)) {
            return;
        }
        if (requestFullScreenIntentAccessIfNeeded(true)) {
            return;
        }
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            name = getString(R.string.ui_default_reminder_name);
        }
        String description = descriptionInput == null ? "" : descriptionInput.getText().toString().trim();
        if (!selectedOneTime && !selectedPeriodic && !selectedAnnual && selectedDays.isEmpty()) {
            selectedDays.add(Calendar.SUNDAY);
        }
        if (selectedAnnual && annualCounterInput != null) {
            selectedAnnualCounter = parseAnnualCounter(annualCounterInput.getText().toString());
        }
        if (selectedPeriodic
                && Reminder.PERIOD_UNIT_HOURS.equals(selectedPeriodicUnit)
                && !periodicEndTimeAfterStart()) {
            Toast.makeText(this, getString(R.string.ui_periodic_end_after_start_error), Toast.LENGTH_LONG).show();
            return;
        }
        String id = editingReminder == null ? UUID.randomUUID().toString() : editingReminder.id;
        long oneTimeAt = selectedOneTime ? selectedOneTimeAt() : 0;
        int annualCounterYear = selectedAnnual ? AnnualReminderHelper.baseCounterYear(tempAnnualReminder(id, name)) : 0;
        Reminder reminder = new Reminder(
                id,
                name,
                selectedHour,
                selectedMinute,
                new HashSet<>(selectedDays),
                selectedEnabled,
                oneTimeAt,
                selectedUseZmanim,
                selectedZmanimKey,
                selectedZmanimOffsetMinutes,
                selectedCritical,
                selectedPeriodic,
                selectedPeriodicHebrew,
                Calendar.SUNDAY,
                selectedPeriodicInterval,
                selectedPeriodicUnit,
                selectedYear,
                selectedMonth,
                selectedDayOfMonth,
                selectedPeriodicEndHour,
                selectedPeriodicEndMinute,
                selectedAnnual,
                selectedAnnualHebrew,
                selectedMonth,
                selectedDayOfMonth,
                selectedAnnualAdvanceHours,
                selectedAnnualCounter,
                annualCounterYear,
                description
        );
        if (!isFutureReminder(reminder)) {
            Toast.makeText(this, getString(R.string.ui_choose_future_time), Toast.LENGTH_SHORT).show();
            return;
        }
        store.upsert(reminder);
        showList();
    }

    private int parseAnnualCounter(String text) {
        try {
            String value = text == null ? "" : text.trim();
            if (value.isEmpty()) {
                return 0;
            }
            return Math.max(1, Math.min(1000, Integer.parseInt(value)));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private boolean periodicEndTimeAfterStart() {
        int startMinutes = selectedHour * 60 + selectedMinute;
        int endMinutes = selectedPeriodicEndHour * 60 + selectedPeriodicEndMinute;
        return endMinutes > startMinutes;
    }

    private boolean isFutureReminder(Reminder reminder) {
        if (reminder.isOneTime() && reminder.oneTimeAt <= System.currentTimeMillis()) {
            return false;
        }
        return NextReminderCalculator.nextRegularAt(this, reminder, new ReminderEventStore(this)) != Long.MAX_VALUE;
    }

    private Reminder tempAnnualReminder(String id, String name) {
        return new Reminder(
                id,
                name,
                selectedHour,
                selectedMinute,
                new HashSet<>(),
                selectedEnabled,
                0,
                false,
                selectedZmanimKey,
                selectedZmanimOffsetMinutes,
                selectedCritical,
                false,
                false,
                Calendar.SUNDAY,
                1,
                Reminder.PERIOD_UNIT_DAYS,
                0,
                0,
                0,
                23,
                59,
                true,
                selectedAnnualHebrew,
                selectedMonth,
                selectedDayOfMonth,
                selectedAnnualAdvanceHours,
                selectedAnnualCounter,
                0
        );
    }

    private LinearLayout section() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setGravity(Gravity.CENTER_HORIZONTAL);
        return section;
    }

    private void updateModeSections(View dateSection, View daysSection, View periodicSection, View annualSection,
                                    Button recurringButton, Button oneTimeButton, Button periodicButton, Button annualButton) {
        dateSection.setVisibility(selectedOneTime ? View.VISIBLE : View.GONE);
        daysSection.setVisibility((!selectedOneTime && !selectedPeriodic && !selectedAnnual) ? View.VISIBLE : View.GONE);
        periodicSection.setVisibility(selectedPeriodic ? View.VISIBLE : View.GONE);
        annualSection.setVisibility(selectedAnnual ? View.VISIBLE : View.GONE);
        recurringButton.setBackground(new ElegantButtonDrawable((!selectedOneTime && !selectedPeriodic && !selectedAnnual) ? COLOR_ACCENT_DARK : COLOR_SURFACE_2, dp(8)));
        oneTimeButton.setBackground(new ElegantButtonDrawable(selectedOneTime ? COLOR_ACCENT_DARK : COLOR_SURFACE_2, dp(8)));
        periodicButton.setBackground(new ElegantButtonDrawable(selectedPeriodic ? COLOR_ACCENT_DARK : COLOR_SURFACE_2, dp(8)));
        annualButton.setBackground(new ElegantButtonDrawable(selectedAnnual ? COLOR_ACCENT_DARK : COLOR_SURFACE_2, dp(8)));
    }

    private void updateZmanimSections(View timeCard, Spinner zmanimSpinner, NumberPicker offsetPicker, TextView location) {
        timeCard.setVisibility(selectedUseZmanim ? View.GONE : View.VISIBLE);
        int visibility = selectedUseZmanim ? View.VISIBLE : View.GONE;
        zmanimSpinner.setVisibility(visibility);
        offsetPicker.setVisibility(visibility);
        location.setVisibility(visibility);
    }

    private void addPeriodicPicker(LinearLayout content) {
        LinearLayout card = card();
        TextView title = text("מחזוריות", 13, COLOR_MUTED);
        title.setPadding(0, 0, 0, dp(5));
        card.addView(title);

        LinearLayout repeatRow = new LinearLayout(this);
        repeatRow.setGravity(Gravity.CENTER);
        NumberPicker intervalPicker = numberPicker(1, 365, selectedPeriodicInterval);
        intervalPicker.setOnValueChangedListener((picker, oldValue, newValue) -> selectedPeriodicInterval = newValue);
        repeatRow.addView(pickerColumn("כל", intervalPicker));
        NumberPicker endHourPicker = numberPicker(0, 23, selectedPeriodicEndHour);
        NumberPicker endMinutePicker = numberPicker(0, 59, selectedPeriodicEndMinute);
        endHourPicker.setOnValueChangedListener((picker, oldValue, newValue) -> selectedPeriodicEndHour = newValue);
        endMinutePicker.setOnValueChangedListener((picker, oldValue, newValue) -> selectedPeriodicEndMinute = newValue);
        final LinearLayout[] endTimeSectionRef = new LinearLayout[1];
        Spinner unitSpinner = new Spinner(this);
        String[] unitLabels = {"שעות", "ימים", "שבועות", "חודשים", "שנים"};
        String[] unitValues = {Reminder.PERIOD_UNIT_HOURS, Reminder.PERIOD_UNIT_DAYS, Reminder.PERIOD_UNIT_WEEKS, Reminder.PERIOD_UNIT_MONTHS, Reminder.PERIOD_UNIT_YEARS};
        ArrayAdapter<String> unitAdapter = spinnerAdapter(translated(unitLabels));
        unitSpinner.setAdapter(unitAdapter);
        unitSpinner.setSelection(unitIndex(selectedPeriodicUnit));
        unitSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                selectedPeriodicUnit = unitValues[position];
                if (endTimeSectionRef[0] != null) {
                    endTimeSectionRef[0].setVisibility(Reminder.PERIOD_UNIT_HOURS.equals(selectedPeriodicUnit) ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        repeatRow.addView(unitSpinner, new LinearLayout.LayoutParams(dp(100), LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(repeatRow);
        TextView endTimeTitle = text("תזכורות עד שעה", 12, COLOR_MUTED);
        endTimeTitle.setPadding(0, dp(8), 0, 0);
        LinearLayout endTimeRow = timePickerRow(endHourPicker, endMinutePicker);
        LinearLayout endTimeSection = new LinearLayout(this);
        endTimeSection.setOrientation(LinearLayout.VERTICAL);
        endTimeSection.setGravity(Gravity.CENTER_HORIZONTAL);
        endTimeSection.addView(endTimeTitle);
        endTimeSection.addView(endTimeRow);
        endTimeSection.setVisibility(Reminder.PERIOD_UNIT_HOURS.equals(selectedPeriodicUnit) ? View.VISIBLE : View.GONE);
        endTimeSectionRef[0] = endTimeSection;
        card.addView(endTimeSection);

        LinearLayout dateHolder = section();
        card.addView(dateHolder);
        renderPeriodicDatePickers(dateHolder);
        if (new ReminderSettings(this).jewishMode()) {
            Switch hebrewSwitch = new Switch(this);
            setSwitchText(hebrewSwitch, "תאריך עברי");
            hebrewSwitch.setChecked(selectedPeriodicHebrew);
            card.addView(hebrewSwitch);
            hebrewSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                convertSelectedPeriodicDate(isChecked);
                selectedPeriodicHebrew = isChecked;
                renderPeriodicDatePickers(dateHolder);
            });

            TextView hint = text("התאריך הוא נקודת ההתחלה. בשנים/חודשים לפי תאריך עברי, האפליקציה ממירה בכל פעם לתאריך לועזי מתאים.", 11, COLOR_MUTED);
            hint.setPadding(0, dp(5), 0, 0);
            card.addView(hint);
        }
        content.addView(card, cardParams());
    }

    private void addAnnualPicker(LinearLayout content) {
        LinearLayout card = card();
        TextView title = text("אירוע שנתי", 13, COLOR_MUTED);
        title.setPadding(0, 0, 0, dp(5));
        card.addView(title);

        LinearLayout dateHolder = section();
        card.addView(dateHolder);
        renderAnnualDatePickers(dateHolder);
        if (new ReminderSettings(this).jewishMode()) {
            Switch hebrewSwitch = new Switch(this);
            setSwitchText(hebrewSwitch, "תאריך עברי");
            hebrewSwitch.setChecked(selectedAnnualHebrew);
            card.addView(hebrewSwitch);
            hebrewSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                convertSelectedAnnualDate(isChecked);
                selectedAnnualHebrew = isChecked;
                renderAnnualDatePickers(dateHolder);
            });
        }

        NumberPicker advancePicker = numberPicker(0, 240, Math.max(0, Math.min(240, selectedAnnualAdvanceHours)));
        advancePicker.setOnValueChangedListener((picker, oldValue, newValue) -> selectedAnnualAdvanceHours = newValue);
        card.addView(pickerColumn("שעות לפני", advancePicker));

        annualCounterInput = new EditText(this);
        AppFont.apply(annualCounterInput);
        annualCounterInput.setSingleLine(true);
        annualCounterInput.setText(selectedAnnualCounter <= 0 ? "" : String.valueOf(selectedAnnualCounter));
        annualCounterInput.setHint(UiText.t(this, "מספר התחלתי"));
        annualCounterInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        annualCounterInput.setTextColor(COLOR_TEXT);
        annualCounterInput.setHintTextColor(0xFF9AA39D);
        annualCounterInput.setGravity(Gravity.CENTER);
        annualCounterInput.setTextSize(14);
        annualCounterInput.setBackground(rounded(COLOR_SURFACE_2, dp(8), 0));
        annualCounterInput.setPadding(dp(8), 0, dp(8), 0);
        LinearLayout.LayoutParams counterParams = matchParams();
        counterParams.height = dp(44);
        card.addView(annualCounterInput, counterParams);

        TextView hint = text("אם הוזן מספר התחלתי, הוא יוצג בסוגריים ויעלה ב-1 בכל שנה.", 11, COLOR_MUTED);
        hint.setPadding(0, dp(5), 0, 0);
        card.addView(hint);
        content.addView(card, cardParams());
    }

    private void renderAnnualDatePickers(LinearLayout holder) {
        holder.removeAllViews();
        LinearLayout datePickers = new LinearLayout(this);
        datePickers.setGravity(Gravity.CENTER);
        applyDatePickerDirection(datePickers, selectedAnnualHebrew);
        int maxMonth = selectedAnnualHebrew ? 13 : 12;
        int maxDay = selectedAnnualHebrew ? 30 : 31;
        NumberPicker dayPicker = compactDateNumberPicker(1, maxDay, Math.max(1, Math.min(maxDay, selectedDayOfMonth)));
        dayPicker.setOnValueChangedListener((picker, oldValue, newValue) -> selectedDayOfMonth = newValue);
        NumberPicker monthPicker = numberPicker(1, maxMonth, Math.max(1, Math.min(maxMonth, selectedMonth)));
        monthPicker.setOnValueChangedListener((picker, oldValue, newValue) -> selectedMonth = newValue);
        if (selectedAnnualHebrew) {
            applyDisplayedValues(dayPicker, hebrewDayLabels());
            applyDisplayedValues(monthPicker, hebrewMonthLabels());
        }
        selectedMonth = monthPicker.getValue();
        selectedDayOfMonth = dayPicker.getValue();
        datePickers.addView(pickerColumn("יום", dayPicker));
        datePickers.addView(pickerColumn("חודש", monthPicker));
        holder.addView(datePickers);
    }

    private void renderPeriodicDatePickers(LinearLayout holder) {
        holder.removeAllViews();
        LinearLayout datePickers = new LinearLayout(this);
        datePickers.setGravity(Gravity.CENTER);
        applyDatePickerDirection(datePickers, selectedPeriodicHebrew);
        int currentYear = selectedPeriodicHebrew ? new JewishDate(Calendar.getInstance()).getJewishYear() : Calendar.getInstance().get(Calendar.YEAR);
        int maxDay = selectedPeriodicHebrew ? 30 : 31;
        NumberPicker dayPicker = dateNumberPicker(1, maxDay, Math.max(1, Math.min(maxDay, selectedDayOfMonth)), 42);
        dayPicker.setOnValueChangedListener((picker, oldValue, newValue) -> selectedDayOfMonth = newValue);
        NumberPicker monthPicker = dateNumberPicker(1, selectedPeriodicHebrew ? 13 : 12, Math.max(1, Math.min(selectedPeriodicHebrew ? 13 : 12, selectedMonth)), 44);
        monthPicker.setOnValueChangedListener((picker, oldValue, newValue) -> selectedMonth = newValue);
        NumberPicker yearPicker = dateNumberPicker(currentYear, currentYear + 10, Math.max(currentYear, Math.min(currentYear + 10, selectedYear)), 64);
        yearPicker.setOnValueChangedListener((picker, oldValue, newValue) -> selectedYear = newValue);
        if (selectedPeriodicHebrew) {
            applyDisplayedValues(dayPicker, hebrewDayLabels());
            applyDisplayedValues(monthPicker, hebrewMonthLabels());
        }
        selectedYear = yearPicker.getValue();
        selectedMonth = monthPicker.getValue();
        selectedDayOfMonth = dayPicker.getValue();
        datePickers.addView(pickerColumn("יום", dayPicker));
        datePickers.addView(pickerColumn("חודש", monthPicker));
        datePickers.addView(pickerColumn("שנה", yearPicker));
        holder.addView(datePickers);
    }

    private void applyDatePickerDirection(LinearLayout row, boolean hebrewCalendar) {
        row.setLayoutDirection(hebrewCalendar
                ? View.LAYOUT_DIRECTION_RTL
                : View.LAYOUT_DIRECTION_LTR);
    }

    private void applyDisplayedValues(NumberPicker picker, String[] values) {
        picker.setDisplayedValues(null);
        picker.setDisplayedValues(values);
        picker.setFormatter(null);
    }

    private String[] hebrewDayLabels() {
        String[] labels = new String[30];
        for (int i = 1; i <= 30; i++) {
            labels[i - 1] = hebrewDayLabel(i);
        }
        return labels;
    }

    private String[] hebrewMonthLabels() {
        String[] labels = new String[13];
        for (int i = 1; i <= 13; i++) {
            labels[i - 1] = hebrewMonthLabel(i);
        }
        return labels;
    }

    private void convertSelectedPeriodicDate(boolean targetHebrew) {
        try {
            if (selectedPeriodicHebrew == targetHebrew) {
                return;
            }
            if (targetHebrew) {
                Calendar calendar = calendarFromSelectedGregorianDate(selectedYear, selectedMonth, selectedDayOfMonth);
                JewishDate jewishDate = new JewishDate(calendar);
                selectedYear = jewishDate.getJewishYear();
                selectedMonth = jewishDate.getJewishMonth();
                selectedDayOfMonth = jewishDate.getJewishDayOfMonth();
            } else {
                Calendar calendar = calendarFromSelectedHebrewDate(selectedYear, selectedMonth, selectedDayOfMonth);
                selectedYear = calendar.get(Calendar.YEAR);
                selectedMonth = calendar.get(Calendar.MONTH) + 1;
                selectedDayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
            }
        } catch (Exception ignored) {
        }
    }

    private void convertSelectedAnnualDate(boolean targetHebrew) {
        try {
            if (selectedAnnualHebrew == targetHebrew) {
                return;
            }
            if (targetHebrew) {
                int year = Calendar.getInstance().get(Calendar.YEAR);
                Calendar calendar = calendarFromSelectedGregorianDate(year, selectedMonth, selectedDayOfMonth);
                JewishDate jewishDate = new JewishDate(calendar);
                selectedMonth = jewishDate.getJewishMonth();
                selectedDayOfMonth = jewishDate.getJewishDayOfMonth();
            } else {
                int year = new JewishDate(Calendar.getInstance()).getJewishYear();
                Calendar calendar = calendarFromSelectedHebrewDate(year, selectedMonth, selectedDayOfMonth);
                selectedMonth = calendar.get(Calendar.MONTH) + 1;
                selectedDayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
            }
        } catch (Exception ignored) {
        }
    }

    private Calendar calendarFromSelectedGregorianDate(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, Math.max(1, year));
        calendar.set(Calendar.MONTH, Math.max(1, Math.min(12, month)) - 1);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.DAY_OF_MONTH, Math.min(Math.max(1, day), calendar.getActualMaximum(Calendar.DAY_OF_MONTH)));
        calendar.set(Calendar.HOUR_OF_DAY, 12);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    private Calendar calendarFromSelectedHebrewDate(int year, int month, int day) {
        int safeYear = Math.max(1, year);
        int safeMonth = safeHebrewMonthForYear(safeYear, month);
        JewishDate jewishDate = new JewishDate();
        jewishDate.setJewishDate(safeYear, safeMonth, 1);
        jewishDate.setJewishDate(safeYear, safeMonth, Math.min(Math.max(1, day), jewishDate.getDaysInJewishMonth()));
        Calendar calendar = jewishDate.getGregorianCalendar();
        calendar.set(Calendar.HOUR_OF_DAY, 12);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    private int safeHebrewMonthForYear(int year, int month) {
        JewishDate yearProbe = new JewishDate(year, JewishDate.TISHREI, 1);
        if (month == JewishDate.ADAR_II && !yearProbe.isJewishLeapYear()) {
            return JewishDate.ADAR;
        }
        return Math.max(JewishDate.NISSAN, Math.min(JewishDate.ADAR_II, month));
    }

    private int unitIndex(String unit) {
        if (Reminder.PERIOD_UNIT_HOURS.equals(unit)) return 0;
        if (Reminder.PERIOD_UNIT_DAYS.equals(unit)) return 1;
        if (Reminder.PERIOD_UNIT_WEEKS.equals(unit)) return 2;
        if (Reminder.PERIOD_UNIT_MONTHS.equals(unit)) return 3;
        if (Reminder.PERIOD_UNIT_YEARS.equals(unit)) return 4;
        return 1;
    }

    private void addDatePicker(LinearLayout content) {
        LinearLayout dateCard = card();
        TextView dateTitle = text("תאריך", 13, COLOR_MUTED);
        dateTitle.setPadding(0, 0, 0, dp(4));
        dateCard.addView(dateTitle);
        LinearLayout datePickers = new LinearLayout(this);
        datePickers.setGravity(Gravity.CENTER);
        applyDatePickerDirection(datePickers, false);
        NumberPicker dayPicker = dateNumberPicker(1, 31, selectedDayOfMonth, 42);
        dayPicker.setOnValueChangedListener((picker, oldValue, newValue) -> selectedDayOfMonth = newValue);
        NumberPicker monthPicker = dateNumberPicker(1, 12, selectedMonth, 44);
        monthPicker.setOnValueChangedListener((picker, oldValue, newValue) -> selectedMonth = newValue);
        NumberPicker yearPicker = dateNumberPicker(Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.YEAR) + 3, selectedYear, 64);
        yearPicker.setOnValueChangedListener((picker, oldValue, newValue) -> selectedYear = newValue);
        datePickers.addView(pickerColumn("יום", dayPicker));
        datePickers.addView(pickerColumn("חודש", monthPicker));
        datePickers.addView(pickerColumn("שנה", yearPicker));
        dateCard.addView(datePickers);
        content.addView(dateCard, cardParams());
    }

    private void addDaysPicker(LinearLayout content) {
        TextView daysLabel = text("ימים", 14, COLOR_MUTED);
        daysLabel.setPadding(0, dp(10), 0, dp(2));
        content.addView(daysLabel);

        int[] days = {Calendar.WEDNESDAY, Calendar.TUESDAY, Calendar.MONDAY, Calendar.SUNDAY, Calendar.SATURDAY, Calendar.FRIDAY, Calendar.THURSDAY};
        String[] labels = {
                getString(R.string.ui_day_wed_short),
                getString(R.string.ui_day_tue_short),
                getString(R.string.ui_day_mon_short),
                getString(R.string.ui_day_sun_short),
                getString(R.string.ui_day_sat_short),
                getString(R.string.ui_day_fri_short),
                getString(R.string.ui_day_thu_short)
        };
        LinearLayout dayRows = new LinearLayout(this);
        dayRows.setOrientation(LinearLayout.VERTICAL);
        dayRows.setGravity(Gravity.CENTER);
        LinearLayout dayRow = compactDayRow();
        for (int i = 0; i < days.length; i++) {
            if (i == 4) {
                dayRows.addView(dayRow);
                dayRow = compactDayRow();
            }
            int day = days[i];
            Button dayButton = dayButton(labels[i], selectedDays.contains(day) ? COLOR_ACCENT_DARK : COLOR_SURFACE_2);
            dayButton.setOnClickListener(v -> {
                if (selectedDays.contains(day)) {
                    selectedDays.remove(day);
                } else {
                    selectedDays.add(day);
                }
                styleDayButton(dayButton, selectedDays.contains(day));
            });
            dayRow.addView(dayButton);
        }
        dayRows.addView(dayRow);
        content.addView(dayRows);
    }

    private long selectedOneTimeAt() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, selectedYear);
        calendar.set(Calendar.MONTH, selectedMonth - 1);
        calendar.set(Calendar.DAY_OF_MONTH, Math.min(selectedDayOfMonth, calendar.getActualMaximum(Calendar.DAY_OF_MONTH)));
        calendar.set(Calendar.HOUR_OF_DAY, selectedHour);
        calendar.set(Calendar.MINUTE, selectedMinute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private void requestMissingAccessIfNeeded() {
        if (!new ReminderSettings(this).onboardingComplete()
                || permissionRequestInFlight || permissionExplanationVisible
                || exactAlarmRequestStarted || fullScreenIntentRequestStarted) {
            return;
        }
        if (requestNotificationAccessIfNeeded()) {
            return;
        }
        if (requestLocationAccessIfNeeded()) {
            return;
        }
        if (requestActivityRecognitionIfNeeded()) {
            return;
        }
        if (requestBodySensorsIfNeeded()) {
            return;
        }
        requestExactAlarmAccessIfNeeded(false);
    }

    private boolean requestNotificationAccessIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !askedPostNotificationsThisSession) {
            askedPostNotificationsThisSession = true;
            showPermissionExplanation(
                    R.string.ui_permission_notifications_title,
                    R.string.ui_permission_notifications_message,
                    () -> {
                        permissionRequestInFlight = true;
                        AppLog.w(this, "request permission POST_NOTIFICATIONS");
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_POST_NOTIFICATIONS);
                    });
            return true;
        }
        return false;
    }

    private boolean requestLocationAccessIfNeeded() {
        if (!new ReminderSettings(this).jewishMode()) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && !askedLocationThisSession) {
            askedLocationThisSession = true;
            showPermissionExplanation(
                    R.string.ui_permission_location_title,
                    R.string.ui_permission_location_message,
                    () -> {
                        permissionRequestInFlight = true;
                        AppLog.w(this, "request permission ACCESS_FINE_LOCATION");
                        requestLocationPermissions();
                    });
            return true;
        }
        return false;
    }

    private void requestLocationPermissions() {
        requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        }, REQUEST_FINE_LOCATION);
    }

    private boolean requestActivityRecognitionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
                && !askedActivityRecognitionThisSession) {
            askedActivityRecognitionThisSession = true;
            showPermissionExplanation(
                    R.string.ui_permission_activity_title,
                    R.string.ui_permission_activity_message,
                    () -> {
                        permissionRequestInFlight = true;
                        AppLog.w(this, "request permission ACTIVITY_RECOGNITION");
                        requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, REQUEST_ACTIVITY_RECOGNITION);
                    });
            return true;
        }
        return false;
    }

    private boolean requestBodySensorsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED
                && !askedBodySensorsThisSession) {
            askedBodySensorsThisSession = true;
            showPermissionExplanation(
                    R.string.ui_permission_sensors_title,
                    R.string.ui_permission_sensors_message,
                    () -> {
                        permissionRequestInFlight = true;
                        AppLog.w(this, "request permission BODY_SENSORS");
                        requestPermissions(new String[]{Manifest.permission.BODY_SENSORS}, REQUEST_BODY_SENSORS);
                    });
            return true;
        }
        return false;
    }

    private void requestCriticalAlertAccessIfNeeded(boolean force) {
        if (requestExactAlarmAccessIfNeeded(force)) {
            return;
        }
        requestFullScreenIntentAccessIfNeeded(force);
    }

    private boolean requestExactAlarmAccessIfNeeded(boolean force) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ReminderScheduler.canScheduleExactAlarms(this)) {
            return false;
        }
        if ((exactAlarmRequestStarted || askedExactAlarmThisSession) && !force) {
            return false;
        }
        exactAlarmRequestStarted = true;
        askedExactAlarmThisSession = true;
        AppLog.w(this, "request setting SCHEDULE_EXACT_ALARM");
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.ui_enable_alarms_reminders_title))
                .setMessage(getString(R.string.ui_enable_alarms_reminders_message))
                .setPositiveButton(getString(R.string.ui_open_settings), (dialog, which) -> openExactAlarmSettings())
                .setNegativeButton(getString(R.string.ui_later), null)
                .show();
        return true;
    }

    private void openExactAlarmSettings() {
        Toast.makeText(this, getString(R.string.ui_enable_alarms_reminders_hint), Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (Exception ignored) {
            Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + getPackageName()));
            startActivity(fallback);
        }
    }

    private boolean requestFullScreenIntentAccessIfNeeded(boolean force) {
        if (canUseFullScreenIntent()) {
            return false;
        }
        if ((fullScreenIntentRequestStarted || askedFullScreenThisSession) && !force) {
            return false;
        }
        fullScreenIntentRequestStarted = true;
        askedFullScreenThisSession = true;
        showPermissionExplanation(
                R.string.ui_permission_full_screen_title,
                R.string.ui_permission_full_screen_message,
                this::openFullScreenIntentSettings);
        return true;
    }

    private void openFullScreenIntentSettings() {
        AppLog.w(this, "request setting FULL_SCREEN_INTENT");
        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                .setData(Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (Exception ignored) {
            Intent fallback = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(fallback);
        }
    }

    private void showPermissionExplanation(int titleRes, int messageRes, Runnable request) {
        permissionExplanationVisible = true;
        new AlertDialog.Builder(this)
                .setTitle(getString(titleRes))
                .setMessage(getString(messageRes))
                .setPositiveButton(R.string.ui_continue, (dialog, which) -> {
                    permissionExplanationVisible = false;
                    request.run();
                })
                .setNegativeButton(R.string.ui_not_now, (dialog, which) -> {
                    permissionExplanationVisible = false;
                    if (fullScreenIntentRequestStarted) {
                        fullScreenIntentRequestStarted = false;
                    }
                })
                .setOnCancelListener(dialog -> permissionExplanationVisible = false)
                .show();
    }

    private boolean canUseFullScreenIntent() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return true;
        }
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        return manager.canUseFullScreenIntent();
    }

    private LinearLayout baseContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(12), dp(22), dp(12), dp(22));
        content.setBackgroundColor(COLOR_BG);
        TextView today = text(todayDateLine(), 10, COLOR_MUTED);
        today.setPadding(0, 0, 0, dp(6));
        content.addView(today);
        return content;
    }

    private String todayDateLine() {
        Calendar calendar = Calendar.getInstance();
        String gregorian = String.format(
                Locale.US,
                "%02d/%02d/%04d",
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.YEAR)
        );
        if (!new ReminderSettings(this).jewishMode()) {
            return gregorian;
        }
        JewishDate jewishDate = new JewishDate(calendar);
        return gregorian + " | " + hebrewDayLabel(jewishDate.getJewishDayOfMonth())
                + " " + hebrewMonthLabel(jewishDate.getJewishMonth())
                + " " + jewishDate.getJewishYear();
    }

    private void previewVibration(String style, int durationMs) {
        previewVibration(style, durationMs, 10);
    }

    private void previewVibration(String style, int durationMs, int strength) {
        stopVibrationPreview();
        if (ReminderSettings.VIBRATION_OFF.equals(style)) {
            return;
        }
        Vibrator vibrator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
            vibrator = manager.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        }
        if (vibrator != null) {
            long[] pattern = ReminderSettings.vibrationPattern(style, durationMs);
            int amplitude = Math.round(Math.max(1, Math.min(10, strength)) * 255f / 10f);
            int[] amplitudes = new int[pattern.length];
            for (int index = 0; index < amplitudes.length; index++) {
                amplitudes[index] = index % 2 == 1 ? amplitude : 0;
            }
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1));
        }
    }

    private void openRingtonePicker(String currentUriText) {
        openRingtonePicker(currentUriText, REQUEST_PICK_RINGTONE);
    }

    private interface RingtoneChoiceListener {
        void onChoice(String uri, String title);
    }

    private static final class RingtoneChoice {
        final String uri;
        final String title;
        final String group;

        RingtoneChoice(String uri, String title, String group) {
            this.uri = uri;
            this.title = title;
            this.group = group;
        }
    }

    private void showSmartAlarmRingtoneScreen(String currentUri, int volumePercent,
                                              RingtoneChoiceListener listener) {
        stopSmartAlarmPreview();
        ringtoneReturnScreen = currentScreen;
        smartAlarmSettingsSavedView = ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        smartAlarmSettingsSavedScroll = activeScrollView;
        currentScreen = "smart_alarm_ringtone";
        final String[] selectedUri = {currentUri == null ? "" : currentUri};
        smartAlarmRingtoneSelectedUri = selectedUri[0];
        smartAlarmRingtoneListener = listener;
        ArrayList<RingtoneChoice> choices = new ArrayList<>();
        choices.add(new RingtoneChoice("", UiText.t(this, "צלצול ברירת מחדל"), "ברירת מחדל"));

        LinearLayout content = baseContent();
        addTitle(content, "בחירת צלצול", "לחיצה משמיעה דוגמה");
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ProgressBar loading = new ProgressBar(this);
        list.addView(loading, new LinearLayout.LayoutParams(-1, dp(72)));
        content.addView(list);

        LinearLayout actions = actionRow();
        Button back = pillButton("חזרה", COLOR_SURFACE_2);
        back.setOnClickListener(v -> finishSmartAlarmRingtoneSelection());
        actions.addView(back);
        content.addView(actions);
        content.addView(new View(this), new LinearLayout.LayoutParams(1, dp(16)));
        setScrollableContent(content);

        new Thread(() -> {
            RingtoneManager manager = new RingtoneManager(getApplicationContext());
            manager.setType(RingtoneManager.TYPE_ALARM | RingtoneManager.TYPE_NOTIFICATION);
            android.database.Cursor cursor = null;
            try {
                cursor = manager.getCursor();
                while (cursor.moveToNext()) {
                    int position = cursor.getPosition();
                    Uri uri = manager.getRingtoneUri(position);
                    String ringtoneTitle = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX);
                    if (uri != null && ringtoneTitle != null && !ringtoneTitle.trim().isEmpty()) {
                        choices.add(new RingtoneChoice(uri.toString(), ringtoneTitle,
                                ringtoneGroup(ringtoneTitle)));
                    }
                }
            } catch (Exception exception) {
                AppLog.e(this, "smart alarm ringtone list failed", exception);
            } finally {
                if (cursor != null) cursor.close();
            }
            choices.sort((left, right) -> {
                int groupCompare = Integer.compare(ringtoneGroupOrder(left.group), ringtoneGroupOrder(right.group));
                return groupCompare != 0 ? groupCompare : left.title.compareToIgnoreCase(right.title);
            });
            mainHandler.post(() -> {
                if (!"smart_alarm_ringtone".equals(currentScreen)) return;
                renderRingtoneChoices(list, selectedUri, volumePercent, choices);
            });
        }, "wr-ringtone-list").start();
    }

    private void renderRingtoneChoices(LinearLayout list, String[] selectedUri, int volumePercent,
                                       ArrayList<RingtoneChoice> choices) {
        list.removeAllViews();
        String previousGroup = null;
        for (RingtoneChoice choice : choices) {
            if (!choice.group.equals(previousGroup)) {
                TextView groupTitle = text(choice.group, 14, COLOR_LUXURY_GOLD);
                AppFont.bold(groupTitle);
                groupTitle.setPadding(dp(8), dp(12), dp(8), dp(4));
                list.addView(groupTitle, matchParams());
                previousGroup = choice.group;
            }
            addRingtoneChoice(list, selectedUri, choice, volumePercent, choices);
        }
        AppTextStyle.apply(list);
    }

    private void addRingtoneChoice(LinearLayout list, String[] selectedUri, RingtoneChoice choice,
                                   int volumePercent, ArrayList<RingtoneChoice> choices) {
        String uri = choice.uri;
        String name = choice.title;
        boolean selected = selectedUri[0].equals(uri);
        LinearLayout option = new LinearLayout(this);
        option.setGravity(Gravity.CENTER_VERTICAL);
        option.setPadding(dp(15), 0, dp(11), 0);
        GradientDrawable optionBackground = new GradientDrawable();
        optionBackground.setColor(selected ? COLOR_ACCENT_DARK : COLOR_SURFACE_2);
        optionBackground.setCornerRadius(dp(24));
        optionBackground.setStroke(dp(selected ? 2 : 1), selected ? COLOR_LUXURY_GOLD : 0x55FFFFFF);
        option.setBackground(optionBackground);
        TextView optionTitle = text(name, 14, COLOR_TEXT);
        optionTitle.setGravity(AppLanguage.isRtl(this) ? Gravity.RIGHT : Gravity.LEFT);
        option.addView(optionTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        android.widget.RadioButton radio = new android.widget.RadioButton(this);
        radio.setChecked(selected);
        radio.setClickable(false);
        radio.setFocusable(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            radio.setButtonTintList(new android.content.res.ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{COLOR_LUXURY_GOLD, COLOR_MUTED}));
        }
        option.addView(radio, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.setMargins(dp(4), dp(3), dp(4), dp(3));
        option.setLayoutParams(params);
        option.setOnClickListener(v -> {
            selectedUri[0] = uri;
            smartAlarmRingtoneSelectedUri = uri;
            previewSmartAlarm(SmartAlarmStore.VIBRATION_NORMAL, 2, true, volumePercent, uri);
            renderRingtoneChoices(list, selectedUri, volumePercent, choices);
        });
        list.addView(option);
    }

    private String ringtoneGroup(String title) {
        String normalized = title == null ? "" : title.toLowerCase(Locale.US).replace("_", "");
        String natureWords = "rain forest wind breeze cloud snow lake night sky star sunrise sunset dawn cicada firefl morning pond";
        for (String word : natureWords.split(" ")) if (normalized.contains(word)) return "טבע ורגוע";
        String classicWords = "alarm beep classic marimba telephone oxygen buzzer bell clock trinket";
        for (String word : classicWords.split(" ")) if (normalized.contains(word)) return "קלאסי";
        return "מנגינות";
    }

    private int ringtoneGroupOrder(String group) {
        if ("ברירת מחדל".equals(group)) return 0;
        if ("קלאסי".equals(group)) return 1;
        if ("מנגינות".equals(group)) return 2;
        return 3;
    }

    private void restoreSmartAlarmSettingsView() {
        if (smartAlarmSettingsSavedView == null) {
            showSmartAlarmSettings();
            return;
        }
        setContentView(smartAlarmSettingsSavedView);
        activeScrollView = smartAlarmSettingsSavedScroll;
        smartAlarmSettingsSavedView = null;
        smartAlarmSettingsSavedScroll = null;
        currentScreen = ringtoneReturnScreen;
    }

    private void finishSmartAlarmRingtoneSelection() {
        stopSmartAlarmPreview();
        if (smartAlarmRingtoneListener != null) {
            smartAlarmRingtoneListener.onChoice(
                    smartAlarmRingtoneSelectedUri,
                    ringtoneTitle(smartAlarmRingtoneSelectedUri));
        }
        smartAlarmRingtoneListener = null;
        restoreSmartAlarmSettingsView();
    }

    private void previewSmartAlarm(String vibrationStyle, int vibrationStrength, boolean sound,
                                   int volumePercent, String soundUri) {
        stopSmartAlarmPreview();
        smartAlarmPreview = AlertFeedback.preview(this, !sound, vibrationStyle, vibrationStrength,
                sound, volumePercent, soundUri);
    }

    private void stopSmartAlarmPreview() {
        if (smartAlarmPreview != null) {
            smartAlarmPreview.stop();
            smartAlarmPreview = null;
        }
    }

    private void openRingtonePicker(String currentUriText, int requestCode) {
        stopVibrationPreview();
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM | RingtoneManager.TYPE_NOTIFICATION)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, UiText.t(this, "בחירת צלצול"));
        Uri existing = currentUriText == null || currentUriText.trim().isEmpty()
                ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                : Uri.parse(currentUriText);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing);
        try {
            startActivityForResult(intent, requestCode);
        } catch (Exception exception) {
            AppLog.e(this, "ringtone picker failed", exception);
            Toast.makeText(this, UiText.t(this, "לא הצלחתי לפתוח בחירת צלצול"), Toast.LENGTH_SHORT).show();
        }
    }

    private String ringtoneTitle(String uriText) {
        Uri uri = uriText == null || uriText.trim().isEmpty()
                ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                : Uri.parse(uriText);
        if (uri == null) {
            return UiText.t(this, "צלצול ברירת מחדל");
        }
        try {
            Ringtone ringtone = RingtoneManager.getRingtone(this, uri);
            String title = ringtone == null ? "" : ringtone.getTitle(this);
            return title == null || title.trim().isEmpty() ? UiText.t(this, "צלצול ברירת מחדל") : title;
        } catch (Exception ignored) {
            return UiText.t(this, "צלצול ברירת מחדל");
        }
    }

    private void stopVibrationPreview() {
        Vibrator vibrator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
            vibrator = manager.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    private void hideKeyboard(View view) {
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private FrameLayout setScrollableContent(LinearLayout content) {
        int period = homeIllustrationPeriod();
        return setScrollableContent(content, homeIllustrationResource(period));
    }

    private FrameLayout setScrollableContent(LinearLayout content, int backgroundDrawableRes) {
        AppTextStyle.apply(content);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BG);

        if (backgroundDrawableRes != 0) {
            ImageView background = new ImageView(this);
            background.setImageResource(backgroundDrawableRes);
            background.setScaleType(ImageView.ScaleType.CENTER_CROP);
            background.setContentDescription(null);
            root.addView(background, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
            View readabilityScrim = new View(this);
            readabilityScrim.setBackgroundColor(0x33061522);
            root.addView(readabilityScrim, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
            content.setBackgroundColor(Color.TRANSPARENT);
        }

        ScrollView scrollView = new ScrollView(this);
        activeScrollView = scrollView;
        scrollView.setFillViewport(true);
        scrollView.setFocusable(true);
        scrollView.setFocusableInTouchMode(true);
        scrollView.setBackgroundColor(backgroundDrawableRes == 0 ? COLOR_BG : Color.TRANSPARENT);
        scrollView.setOnTouchListener((view, event) -> {
            if (handleHorizontalBackSwipe(event)) {
                return true;
            }
            return false;
        });
        scrollView.addView(content);
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        );
        root.addView(scrollView, scrollParams);
        TopArcClockView.addTo(root);
        setContentView(root);
        scrollView.requestFocus();
        return root;
    }

    private boolean handleHorizontalBackSwipe(MotionEvent event) {
        if (event.getPointerCount() > 1) {
            horizontalBackSwipeCandidate = false;
            horizontalBackSwipeTracking = false;
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            swipeStartX = event.getX();
            swipeStartY = event.getY();
            swipeStartTime = event.getEventTime();
            horizontalBackSwipeCandidate = true;
            horizontalBackSwipeTracking = false;
            return false;
        }
        if (!horizontalBackSwipeCandidate) {
            return false;
        }
        float dx = event.getX() - swipeStartX;
        float dy = Math.abs(event.getY() - swipeStartY);
        float absDx = Math.abs(dx);
        int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        if (action == MotionEvent.ACTION_MOVE) {
            if (!horizontalBackSwipeTracking && dy > touchSlop && dy > absDx * 0.55f) {
                horizontalBackSwipeCandidate = false;
                horizontalBackSwipeTracking = false;
                return false;
            }
            if (dx > touchSlop * 1.5f && dx > dy * 1.35f) {
                horizontalBackSwipeTracking = true;
                return true;
            }
            return false;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            horizontalBackSwipeCandidate = false;
            horizontalBackSwipeTracking = false;
            return false;
        }
        if (action != MotionEvent.ACTION_UP) {
            return false;
        }
        boolean wasTracking = horizontalBackSwipeTracking;
        horizontalBackSwipeCandidate = false;
        horizontalBackSwipeTracking = false;
        long duration = Math.max(1, event.getEventTime() - swipeStartTime);
        float velocity = dx / duration;
        int width = getResources().getDisplayMetrics().widthPixels;
        boolean longSwipe = dx > Math.max(dp(48), width * 0.28f) && dx > dy * 1.55f;
        boolean quickSwipe = dx > dp(32) && dx > dy * 1.7f && velocity > 0.55f;
        if (longSwipe || quickSwipe) {
            return navigateBack();
        }
        return wasTracking;
    }

    private boolean navigateBack() {
        if ("smart_alarm_ringtone".equals(currentScreen)) {
            finishSmartAlarmRingtoneSelection();
            return true;
        }
        if ("license_text".equals(currentScreen)) {
            showAboutAndLicenses();
            return true;
        }
        if ("about_licenses".equals(currentScreen)) {
            showSettings();
            return true;
        }
        if ("smart_alarm_list".equals(currentScreen)) {
            showList();
            return true;
        }
        if ("smart_alarm_editor".equals(currentScreen)) {
            stopSmartAlarmPreview();
            if (editingSmartAlarmNew && editingSmartAlarmId > 0) SmartAlarmStore.delete(this, editingSmartAlarmId);
            showSmartAlarmSettings();
            return true;
        }
        if ("settings".equals(currentScreen)
                || "jewish_settings".equals(currentScreen)
                || "alert_settings".equals(currentScreen)
                || "advanced_settings".equals(currentScreen)
                || "backup_export".equals(currentScreen)
                || "backup_import".equals(currentScreen)) {
            stopVibrationPreview();
            stopSmartAlarmPreview();
            if ("settings".equals(currentScreen) || "backup_export".equals(currentScreen) || "backup_import".equals(currentScreen)) {
                showList();
            } else {
                showSettings();
            }
            return true;
        }
        if ("quiet_times".equals(currentScreen)) {
            showSettings();
            return true;
        }
        if ("fasting_settings".equals(currentScreen)) {
            showSettings();
            return true;
        }
        if ("daf_yomi".equals(currentScreen)) {
            showJewishSettings();
            return true;
        }
        if ("omer".equals(currentScreen)) {
            showJewishSettings();
            return true;
        }
        if ("zmanim_day".equals(currentScreen)) {
            if (zmanimBackToSettings) {
                showSettings();
            } else {
                showList();
            }
            return true;
        }
        if ("quiet_rule_editor".equals(currentScreen)) {
            showQuietTimes();
            return true;
        }
        if ("history".equals(currentScreen) || "editor".equals(currentScreen) || "blessing".equals(currentScreen)) {
            showList();
            return true;
        }
        if ("all_reminders".equals(currentScreen)) {
            showList();
            return true;
        }
        if ("list".equals(currentScreen)) {
            finish();
            return true;
        }
        return false;
    }

    private void restoreScrollY(int scrollY) {
        if (activeScrollView == null || scrollY <= 0) {
            return;
        }
        activeScrollView.post(() -> activeScrollView.scrollTo(0, scrollY));
    }

    private void scrollToViewTop(View target) {
        scrollToViewTop(target, dp(10));
    }

    private void scrollToViewTop(View target, int topInset) {
        if (activeScrollView == null || target == null) {
            return;
        }
        Runnable scroll = () -> {
            int targetY = 0;
            View view = target;
            while (view != null && view != activeScrollView) {
                targetY += view.getTop();
                if (!(view.getParent() instanceof View)) {
                    break;
                }
                view = (View) view.getParent();
            }
            activeScrollView.scrollTo(0, Math.max(0, targetY - topInset));
        };
        activeScrollView.post(scroll);
        activeScrollView.postDelayed(scroll, 150);
    }

    private void scrollToFocusedReminder(View focusTarget) {
        if (activeScrollView == null || focusTarget == null) {
            return;
        }
        Runnable scroll = () -> {
            int targetY = Math.max(0, focusTarget.getTop() - dp(18));
            activeScrollView.scrollTo(0, targetY);
            activeScrollView.postDelayed(() -> activeScrollView.smoothScrollTo(0, targetY), 120);
            pendingFocusReminderId = null;
            pendingFocusNextReminder = false;
        };
        activeScrollView.post(scroll);
        activeScrollView.postDelayed(scroll, 350);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String focusReminderId = intent.getStringExtra(EXTRA_FOCUS_REMINDER_ID);
        if (intent.getBooleanExtra(EXTRA_FOCUS_NEXT_REMINDER, false)) {
            pendingFocusNextReminder = true;
            pendingFocusReminderId = null;
            AppLog.d(this, "handleIntent focus next reminder");
        } else if (focusReminderId != null && !focusReminderId.isEmpty()) {
            pendingFocusReminderId = focusReminderId;
            pendingFocusNextReminder = false;
            AppLog.d(this, "handleIntent focus reminder id=" + focusReminderId);
        } else if (intent.hasExtra(EXTRA_FOCUS_REMINDER_ID)) {
            pendingFocusNextReminder = true;
            pendingFocusReminderId = null;
            AppLog.d(this, "handleIntent focus next reminder fallback");
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_BLESSING_REMINDER, false)) {
            pendingBlessingReminder = true;
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_PENDING_RESTORE, false)) {
            pendingRestoreFromPhone = true;
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_ZMANIM_DAY, false)) {
            pendingZmanimDay = true;
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_FASTING_SETTINGS, false)) {
            pendingFastingSettings = true;
        }
    }

    private void openPendingBlessingReminder() {
        if (pendingBlessingReminder) {
            pendingBlessingReminder = false;
            showBlessingReminder();
        }
    }

    private void openPendingZmanimDay() {
        if (pendingZmanimDay) {
            pendingZmanimDay = false;
            zmanimBackToSettings = false;
            showZmanimDay(System.currentTimeMillis());
        }
    }

    private void openPendingFastingSettings() {
        if (pendingFastingSettings) {
            pendingFastingSettings = false;
            showFastingSettings();
        }
    }

    private void openPendingRestoreFromPhone() {
        if (!pendingRestoreFromPhone && !RestoreFromPhoneStore.hasPending(this)) {
            return;
        }
        pendingRestoreFromPhone = false;
        String backupText = RestoreFromPhoneStore.pendingText(this);
        if (backupText.isEmpty()) {
            return;
        }
        if (RestoreFromPhoneStore.MODE_PATCH.equals(RestoreFromPhoneStore.pendingMode(this))) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.ui_apply_phone_changes_title))
                    .setMessage(getString(R.string.ui_apply_phone_changes_message))
                    .setPositiveButton(getString(R.string.ui_apply), (dialog, which) -> {
                        applyPhonePatch(backupText);
                        RestoreFromPhoneStore.clear(this);
                    })
                    .setNegativeButton(getString(R.string.ui_cancel), (dialog, which) -> RestoreFromPhoneStore.clear(this))
                    .setOnCancelListener(dialog -> RestoreFromPhoneStore.clear(this))
                    .show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.ui_restore_from_phone_title))
                .setMessage(getString(R.string.ui_restore_from_phone_message))
                .setPositiveButton(getString(R.string.ui_restore), (dialog, which) -> {
                    restoreBackup(backupText);
                    RestoreFromPhoneStore.clear(this);
                })
                .setNegativeButton(getString(R.string.ui_cancel), (dialog, which) -> RestoreFromPhoneStore.clear(this))
                .setOnCancelListener(dialog -> RestoreFromPhoneStore.clear(this))
                .show();
    }

    private void applyPhonePatch(String patchText) {
        try {
            int count = ReminderPatchApplier.apply(this, patchText);
            store = new ReminderStore(this);
            Toast.makeText(this, getString(R.string.ui_reminder_changes_applied, count), Toast.LENGTH_SHORT).show();
            showList();
        } catch (Exception exception) {
            AppLog.e(this, "phone patch failed", exception);
            Toast.makeText(this, getString(R.string.ui_phone_changes_invalid), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshVisibleScreen() {
        if (store == null || pendingFocusReminderId != null || "editor".equals(currentScreen)) {
            return;
        }
        int scrollY = activeScrollView == null ? 0 : activeScrollView.getScrollY();
        if ("list".equals(currentScreen)) {
            showList(scrollY);
        } else if ("all_reminders".equals(currentScreen)) {
            showAllReminders();
            restoreScrollY(scrollY);
        } else if ("history".equals(currentScreen)) {
            showHistory(scrollY);
        } else if ("fasting_settings".equals(currentScreen)) {
            showFastingSettings();
        }
    }

    private void refreshVisibleScreenIfRemindersChanged() {
        String currentFingerprint = reminderListFingerprint();
        if (currentFingerprint.equals(lastReminderListFingerprint)) {
            return;
        }
        lastReminderListFingerprint = currentFingerprint;
        store = new ReminderStore(this);
        refreshVisibleScreen();
    }

    private void refreshVisibleScreenAfterStartupMaintenance() {
        if ("list".equals(currentScreen) || "all_reminders".equals(currentScreen)) {
            refreshVisibleScreenIfRemindersChanged();
            return;
        }
        if ("history".equals(currentScreen) || "fasting_settings".equals(currentScreen)) {
            refreshVisibleScreen();
        }
    }

    private void rememberReminderListFingerprint() {
        lastReminderListFingerprint = reminderListFingerprint();
    }

    private String reminderListFingerprint() {
        StringBuilder builder = new StringBuilder();
        for (Reminder reminder : new ReminderStore(this).getAll()) {
            try {
                builder.append(reminder.toJson()).append('\n');
            } catch (Exception exception) {
                builder.append(reminder.id).append('|').append(reminder.name).append('\n');
            }
        }
        return builder.toString();
    }

    private void addTitle(LinearLayout content, String title, String subtitle) {
        TextView titleView = text(title, 22, COLOR_TEXT);
        AppFont.bold(titleView);
        titleView.setPadding(0, 0, 0, dp(2));
        content.addView(titleView);
        if (subtitle == null || subtitle.trim().isEmpty()) {
            titleView.setPadding(0, 0, 0, dp(12));
            return;
        }
        TextView subtitleView = text(subtitle, 11, COLOR_MUTED);
        subtitleView.setPadding(dp(18), 0, dp(18), dp(12));
        content.addView(subtitleView);
    }

    private void addOutlinedTitle(LinearLayout content, String title, String subtitle) {
        TextView titleView = shadowedText(title, 22, COLOR_TEXT);
        AppFont.bold(titleView);
        titleView.setPadding(0, 0, 0, dp(2));
        content.addView(titleView);
        if (subtitle == null || subtitle.trim().isEmpty()) {
            titleView.setPadding(0, 0, 0, dp(12));
            return;
        }
        TextView subtitleView = shadowedText(subtitle, 11, COLOR_MUTED);
        subtitleView.setPadding(dp(18), 0, dp(18), dp(12));
        content.addView(subtitleView);
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        AppFont.apply(view);
        view.setText(UiText.t(this, value));
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setTextDirection(AppLanguage.isRtl(this) ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        AppTextStyle.apply(view);
        return view;
    }

    private interface LevelChangeListener { void onChanged(int progress); }

    private static final class LevelControl {
        final LinearLayout view;
        final SeekBar slider;
        final TextView value;
        final int max;

        LevelControl(LinearLayout view, SeekBar slider, TextView value, int max) {
            this.view = view;
            this.slider = slider;
            this.value = value;
            this.max = max;
        }
    }

    private LevelControl levelControl(String label, int max, int progress) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(4), 0, dp(4));
        TextView title = text(label, 12, COLOR_CARD_TEXT);
        root.addView(title);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        row.setPadding(dp(5), dp(3), dp(5), dp(3));
        row.setBackground(rounded(COLOR_SURFACE_2, dp(26), 0x55FFFFFF));
        Button minus = levelButton("−");
        SeekBar slider = new SeekBar(this);
        slider.setMax(max);
        slider.setProgress(Math.max(0, Math.min(max, progress)));
        slider.setProgressDrawable(levelProgressDrawable());
        slider.setThumb(new ColorDrawable(Color.TRANSPARENT));
        slider.setPadding(dp(8), 0, dp(8), 0);
        Button plus = levelButton("+");
        row.addView(minus);
        row.addView(slider, new LinearLayout.LayoutParams(0, dp(40), 1f));
        row.addView(plus);
        root.addView(row, new LinearLayout.LayoutParams(-1, dp(52)));
        TextView value = text(levelPercent(max, slider.getProgress()), 11, COLOR_CARD_TEXT);
        root.addView(value);

        minus.setOnClickListener(v -> slider.setProgress(Math.max(0, slider.getProgress() - 1)));
        plus.setOnClickListener(v -> slider.setProgress(Math.min(max, slider.getProgress() + 1)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(6), 0, dp(6));
        root.setLayoutParams(params);
        return new LevelControl(root, slider, value, max);
    }

    private LinearLayout.LayoutParams wideLevelParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(2), dp(6), dp(2), dp(6));
        return params;
    }

    private android.graphics.drawable.Drawable levelProgressDrawable() {
        GradientDrawable track = new GradientDrawable();
        track.setColor(0xFF4A4D4F);
        track.setCornerRadius(dp(4));
        track.setSize(1, dp(7));
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(0xFFFFD981);
        fill.setCornerRadius(dp(4));
        fill.setSize(1, dp(7));
        android.graphics.drawable.ClipDrawable clipped = new android.graphics.drawable.ClipDrawable(
                fill, Gravity.LEFT, android.graphics.drawable.ClipDrawable.HORIZONTAL);
        android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{track, clipped});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        layers.setLayerGravity(0, Gravity.CENTER_VERTICAL);
        layers.setLayerGravity(1, Gravity.CENTER_VERTICAL);
        layers.setLayerHeight(0, dp(7));
        layers.setLayerHeight(1, dp(7));
        return layers;
    }

    private Button levelButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(COLOR_TEXT);
        button.setTextSize(25);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        button.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        button.setBackground(new ElegantButtonDrawable(COLOR_SURFACE, dp(20)));
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(38), dp(42)));
        AppTextStyle.apply(button);
        return button;
    }

    private SeekBar.OnSeekBarChangeListener levelPreviewListener(LevelControl control,
                                                                 LevelChangeListener listener) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                control.value.setText(levelPercent(control.max, progress));
                listener.onChanged(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        };
    }

    private String levelPercent(int max, int progress) {
        int percent = max == 9 ? (progress + 1) * 10 : Math.round(progress * 100f / max);
        return percent + "%";
    }

    private TextView shadowedText(String value, int sp, int color) {
        TextView view = text(value, sp, color);
        AppTextStyle.apply(view);
        return view;
    }

    private Button pillButton(String value, int color) {
        Button button = new IconButton(this);
        AppFont.apply(button);
        button.setText(UiText.t(this, value));
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        button.setBackground(new ElegantButtonDrawable(color, dp(18)));
        button.setAllCaps(false);
        button.setMinHeight(dp(38));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), 0, dp(8), 0);
        applyButtonIcon(button, value);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(100), dp(40));
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        button.setLayoutParams(params);
        AppTextStyle.apply(button);
        return button;
    }

    private void applyButtonIcon(Button button, String value) {
        String translatedValue = UiText.t(this, value);
        if (buttonShouldHideIcon(value, translatedValue)
                || "בוצע".equals(value) || "דחה".equals(value)
                || "Done".equalsIgnoreCase(translatedValue) || "Snooze".equalsIgnoreCase(translatedValue)) {
            clearButtonIcon(button);
            return;
        }
        AppButtonIconDrawable.Kind kind = buttonIconKind(value);
        if (kind == null) return;
        AppButtonIconDrawable icon = new AppButtonIconDrawable(kind, 0xFFFFD3BD, dp(2));
        icon.setBounds(0, 0, dp(13), dp(13));
        button.setCompoundDrawables(null, null, null, null);
        if (button instanceof IconButton) {
            ((IconButton) button).setAppIcon(icon, dp(3));
        }
    }

    private void clearButtonIcon(Button button) {
        button.setCompoundDrawables(null, null, null, null);
        if (button instanceof IconButton) ((IconButton) button).setAppIcon(null, 0);
    }

    private boolean buttonShouldHideIcon(String value, String translatedValue) {
        String v = value == null ? "" : value;
        String translated = translatedValue == null ? "" : translatedValue;
        return v.contains("חזרה") || translated.toLowerCase(Locale.ROOT).contains("back")
                || v.equals("שמירה") || translated.equalsIgnoreCase("Save")
                || v.equals("ביטול") || translated.equalsIgnoreCase("Cancel")
                || v.equals("עריכה") || translated.equalsIgnoreCase("Edit")
                || v.contains("הוספת שעון מעורר") || translated.toLowerCase(Locale.ROOT).contains("add alarm")
                || v.contains("אודות") || v.contains("רישיונות")
                || translated.toLowerCase(Locale.ROOT).contains("licenses")
                || v.equals("קבועה") || v.equals("חד פעמית") || v.equals("מחזורית") || v.equals("שנתית")
                || translated.equalsIgnoreCase("Fixed") || translated.equalsIgnoreCase("One Time")
                || translated.equalsIgnoreCase("Repeating") || translated.equalsIgnoreCase("Annual");
    }

    private AppButtonIconDrawable.Kind buttonIconKind(String value) {
        String v = value == null ? "" : value;
        if (v.equals("גיבוי לטלפון")) return AppButtonIconDrawable.Kind.PHONE_IN;
        if (v.equals("שחזור מהטלפון")) return AppButtonIconDrawable.Kind.PHONE_OUT;
        if (v.equals("תזכורת")) return AppButtonIconDrawable.Kind.PLUS;
        if ((v.contains("תזכורת") && (v.contains("+") || v.contains("הוס") || v.contains("חדש")))
                || (v.contains("שעון מעורר") && (v.contains("הוס") || v.contains("חדש")))) return AppButtonIconDrawable.Kind.ADD;
        if (v.equals("הוספה")) return AppButtonIconDrawable.Kind.ADD;
        if (v.contains("היסטוריה")) return AppButtonIconDrawable.Kind.HISTORY;
        if (v.contains("כל התזכורות")) return AppButtonIconDrawable.Kind.LIST;
        if (v.contains("שעון מעורר")) return AppButtonIconDrawable.Kind.ALARM;
        if (v.contains("זמני") || v.contains("זמן ") || v.contains("דקות") || v.equals("שעה") || v.equals("שעתיים") || v.contains("דחייה")) return AppButtonIconDrawable.Kind.CLOCK;
        if (v.contains("ברכה")) return AppButtonIconDrawable.Kind.BELL;
        if (v.contains("הגדרות")) return AppButtonIconDrawable.Kind.SETTINGS;
        if (v.contains("חזרה") || v.equals("ביטול") || v.equals("Back") || v.equals("Cancel") || v.equals("Not now")) return AppButtonIconDrawable.Kind.BACK;
        if (v.contains("שמירה") || v.equals("Save") || v.contains("הושלם") || v.contains("למדתי") || v.contains("סיימתי")) return AppButtonIconDrawable.Kind.SAVE;
        if (v.contains("מחיק") || v.contains("ניקוי")) return AppButtonIconDrawable.Kind.DELETE;
        if (v.contains("קובץ") || v.contains("תיקייה") || v.contains("לוח") || v.contains("העתקה")) return AppButtonIconDrawable.Kind.FILE;
        if (v.contains("טלפון") || v.contains("שליחה")) return AppButtonIconDrawable.Kind.PHONE;
        if (v.contains("מיקום") || v.contains("עיר")) return AppButtonIconDrawable.Kind.LOCATION;
        if (v.contains("צלצול") || v.contains("רטט וצלילים")) return AppButtonIconDrawable.Kind.SOUND;
        if (v.contains("עריכה")) return AppButtonIconDrawable.Kind.FILE;
        if (v.contains("צום") || v.contains("אוכל")) return AppButtonIconDrawable.Kind.CLOCK;
        if (v.contains("אודות") || v.contains("רישיונות")) return AppButtonIconDrawable.Kind.INFO;
        return AppButtonIconDrawable.Kind.INFO;
    }

    private TextView outlinedText(String value, int size, int fillColor) {
        return outlinedExactText(UiText.t(this, value), size, fillColor,
                AppLanguage.isRtl(this) ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
    }

    private TextView outlinedExactText(String value, int size, int fillColor, int textDirection) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER);
        view.setTextDirection(textDirection);
        AppTextStyle.apply(
                view,
                fillColor,
                AppTextStyle.contrastingColor(fillColor),
                AppTextStyle.DEFAULT_BORDER_WIDTH_PX);
        return view;
    }

    private void setSwitchText(Switch view, String value) {
        AppFont.apply(view);
        view.setText(UiText.t(this, value));
        view.setTextColor(COLOR_TEXT);
        view.setTextSize(14);
        view.setTextDirection(AppLanguage.isRtl(this) ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        AppTextStyle.apply(view);
    }

    private String[] translated(String[] values) {
        return UiText.translate(this, values);
    }

    private ArrayAdapter<String> spinnerAdapter(String[] values) {
        return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                return styleSpinnerText(super.getView(position, convertView, parent), false);
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return styleSpinnerText(super.getDropDownView(position, convertView, parent), true);
            }
        };
    }

    private View styleSpinnerText(View view, boolean dropDown) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            AppFont.apply(textView);
            textView.setTextColor(dropDown ? COLOR_TEXT : COLOR_CARD_TEXT);
            textView.setTextSize(14);
            textView.setGravity(Gravity.CENTER);
            textView.setPadding(dp(10), dp(8), dp(10), dp(8));
            textView.setBackgroundColor(dropDown ? COLOR_SURFACE : Color.TRANSPARENT);
        }
        return view;
    }

    private Button smallButton(String value, int color) {
        Button button = pillButton(value, color);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(54), dp(38));
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        button.setLayoutParams(params);
        button.setPadding(0, 0, 0, 0);
        button.setTextSize(12);
        return button;
    }

    private Button smallWideButton(String value, int color) {
        Button button = pillButton(value, color);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(76), dp(34));
        params.setMargins(dp(3), dp(6), dp(3), 0);
        button.setLayoutParams(params);
        button.setPadding(0, 0, 0, 0);
        button.setTextSize(12);
        return button;
    }

    private void setZmanimNavButtonParams(Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1f);
        params.setMargins(dp(2), dp(6), dp(2), dp(4));
        button.setLayoutParams(params);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setTextSize(10);
    }

    private void setModeChoiceParams(Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(36), 1f);
        params.setMargins(dp(3), dp(4), dp(3), dp(2));
        button.setLayoutParams(params);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setTextSize(11);
        button.setSingleLine(false);
        button.setGravity(Gravity.CENTER);
    }

    private Button modeButton(String value, int color) {
        Button button = pillButton(value, color);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(92), dp(38));
        params.setMargins(dp(3), dp(6), dp(3), dp(2));
        button.setLayoutParams(params);
        button.setPadding(0, 0, 0, 0);
        button.setTextSize(12);
        return button;
    }

    private Switch activeSwitch(Reminder reminder) {
        Switch activeSwitch = new Switch(this);
        AppFont.apply(activeSwitch);
        activeSwitch.setText(UiText.t(this, reminder.enabled ? "פעיל" : "כבוי"));
        activeSwitch.setTextSize(12);
        activeSwitch.setTextColor(reminder.enabled ? COLOR_ACCENT : COLOR_MUTED);
        activeSwitch.setTextDirection(AppLanguage.isRtl(this) ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        activeSwitch.setChecked(reminder.enabled);
        activeSwitch.setPadding(0, dp(4), 0, 0);
        activeSwitch.setOnClickListener(v -> {
            int scrollY = activeScrollView == null ? 0 : activeScrollView.getScrollY();
            boolean enabled = activeSwitch.isChecked();
            Reminder updated = new Reminder(
                    reminder.id,
                    reminder.name,
                    reminder.hour,
                    reminder.minute,
                    new HashSet<>(reminder.days),
                    enabled,
                    reminder.oneTimeAt,
                    reminder.useZmanim,
                    reminder.zmanimKey,
                    reminder.zmanimOffsetMinutes,
                    reminder.critical,
                    reminder.periodic,
                    reminder.periodicHebrew,
                    reminder.periodicDayOfWeek,
                    reminder.periodicInterval,
                    reminder.periodicUnit,
                    reminder.periodicStartYear,
                    reminder.periodicStartMonth,
                    reminder.periodicStartDay,
                    reminder.periodicEndHour,
                    reminder.periodicEndMinute,
                    reminder.annualEvent,
                    reminder.annualHebrew,
                    reminder.annualMonth,
                    reminder.annualDay,
                    reminder.annualAdvanceHours,
                    reminder.annualCounter,
                    reminder.annualCounterYear,
                    reminder.description
            );
            store.upsert(updated);
            showList(scrollY);
        });
        return activeSwitch;
    }

    private Button dayButton(String value, int color) {
        Button button = pillButton(value, color);
        clearButtonIcon(button);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(38), dp(34));
        params.setMargins(dp(3), dp(2), dp(3), dp(2));
        button.setLayoutParams(params);
        button.setPadding(0, 0, 0, 0);
        button.setTextSize(13);
        return button;
    }

    private void styleDayButton(Button button, boolean selected) {
        int color = selected ? COLOR_ACCENT_DARK : COLOR_SURFACE_2;
        button.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        button.setBackground(new ElegantButtonDrawable(color, dp(18)));
    }

    private LinearLayout compactDayRow() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private NumberPicker numberPicker(int min, int max, int value) {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(Math.max(min, Math.min(max, value)));
        picker.setWrapSelectorWheel(true);
        picker.setFormatter(number -> String.format(Locale.US, "%02d", number));
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(68), dp(86));
        params.setMargins(dp(3), 0, dp(3), 0);
        picker.setLayoutParams(params);
        return picker;
    }

    private NumberPicker compactDateNumberPicker(int min, int max, int value) {
        return dateNumberPicker(min, max, value, 50);
    }

    private NumberPicker dateNumberPicker(int min, int max, int value, int widthDp) {
        NumberPicker picker = numberPicker(min, max, value);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(widthDp), dp(86));
        params.setMargins(dp(2), 0, dp(2), 0);
        picker.setLayoutParams(params);
        return picker;
    }

    private NumberPicker offsetNumberPicker(int offsetMinutes) {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(0);
        picker.setMaxValue(360);
        picker.setValue(Math.max(0, Math.min(360, offsetMinutes + 180)));
        picker.setWrapSelectorWheel(true);
        picker.setFormatter(number -> {
            int offset = number - 180;
            if (offset == 0) return AppLanguage.isEnglish(this) ? "On time" : "בזמן";
            return (offset > 0 ? "+" : "") + offset
                    + (AppLanguage.isEnglish(this) ? " min" : " דק׳");
        });
        picker.setOnValueChangedListener((view, oldValue, newValue) -> selectedZmanimOffsetMinutes = newValue - 180);
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(88), dp(86));
        params.setMargins(dp(3), 0, dp(3), 0);
        picker.setLayoutParams(params);
        return picker;
    }

    private NumberPicker quietOffsetPicker(int offsetMinutes) {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(0);
        picker.setMaxValue(360);
        picker.setValue(Math.max(0, Math.min(360, offsetMinutes + 180)));
        picker.setWrapSelectorWheel(true);
        picker.setFormatter(number -> {
            int offset = number - 180;
            if (offset == 0) return AppLanguage.isEnglish(this) ? "On time" : "בזמן";
            return (offset > 0 ? "+" : "") + offset
                    + (AppLanguage.isEnglish(this) ? " min" : " דק׳");
        });
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(88), dp(86));
        params.setMargins(dp(3), 0, dp(3), 0);
        picker.setLayoutParams(params);
        return picker;
    }

    private LinearLayout pickerColumn(String label, NumberPicker picker) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        TextView title = text(label, 12, COLOR_MUTED);
        column.addView(title);
        column.addView(picker);
        return column;
    }

    private LinearLayout timePickerRow(NumberPicker hourPicker, NumberPicker minutePicker) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        row.addView(pickerColumn("שעה", hourPicker));
        row.addView(pickerColumn("דקה", minutePicker));
        return row;
    }

    private LinearLayout actionRow() {
        LinearLayout row = new LinearLayout(this) {
            @Override
            public void addView(View child) {
                applyActionButtonParams(child);
                super.addView(child);
            }

            @Override
            public void addView(View child, int index) {
                applyActionButtonParams(child);
                super.addView(child, index);
            }

            @Override
            public void addView(View child, ViewGroup.LayoutParams params) {
                child.setLayoutParams(params);
                applyActionButtonParams(child);
                super.addView(child);
            }
        };
        row.setGravity(Gravity.CENTER);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        row.setPadding(0, 0, 0, dp(8));
        row.setLayoutParams(matchParams());
        return row;
    }

    private void applyActionButtonParams(View child) {
        if (!(child instanceof Button)) {
            return;
        }
        LinearLayout.LayoutParams existing = child.getLayoutParams() instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) child.getLayoutParams()
                : null;
        if (existing != null && existing.width == 0 && existing.weight > 0) {
            return;
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        child.setLayoutParams(params);
        Button button = (Button) child;
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setSingleLine(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(4), 0, dp(4), 0);
        if (button.getText() != null && button.getText().length() > 9) {
            button.setTextSize(11);
        }
    }

    private LinearLayout card() {
        return card(false);
    }

    private LinearLayout card(boolean highlighted) {
        LinearLayout card = new LinearLayout(this) {
            @Override
            public void addView(View child) {
                styleCardContent(child);
                super.addView(child);
            }

            @Override
            public void addView(View child, ViewGroup.LayoutParams params) {
                styleCardContent(child);
                super.addView(child, params);
            }
        };
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(rounded(COLOR_CARD, dp(18), highlighted ? 0xFFC77B58 : 0x33747D63));
        return card;
    }

    private void styleCardContent(View child) {
        if (child instanceof Button || child instanceof EditText) {
            return;
        }
        if (child instanceof Switch) {
            ((Switch) child).setTextColor(COLOR_CARD_TEXT);
        } else if (child instanceof TextView) {
            TextView textView = (TextView) child;
            int currentColor = textView.getCurrentTextColor();
            if (currentColor == COLOR_TEXT) {
                textView.setTextColor(COLOR_CARD_TEXT);
            } else if (currentColor == COLOR_MUTED) {
                textView.setTextColor(COLOR_CARD_MUTED);
            } else if (currentColor == COLOR_ACCENT || currentColor == COLOR_LUXURY_GOLD) {
                textView.setTextColor(COLOR_ACCENT);
            }
        }
        if (child instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) child;
            for (int i = 0; i < group.getChildCount(); i++) {
                styleCardContent(group.getChildAt(i));
            }
        }
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(dp(4), dp(5), dp(4), dp(5));
        return params;
    }

    private LinearLayout.LayoutParams compactCardParams() {
        LinearLayout.LayoutParams params = cardParams();
        params.setMargins(dp(6), dp(3), dp(6), dp(3));
        return params;
    }

    private LinearLayout.LayoutParams homeHeroParams() {
        LinearLayout.LayoutParams params = cardParams();
        params.setMargins(dp(3), dp(7), dp(3), dp(5));
        return params;
    }

    private ImageView illustration(int drawableRes, int heightDp) {
        ImageView image = new ImageView(this);
        image.setImageResource(drawableRes);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setAdjustViewBounds(false);
        image.setContentDescription(null);
        image.setAlpha(0.9f);
        image.setBackground(rounded(COLOR_SURFACE, dp(14), 0x443F594C));
        image.setClipToOutline(true);
        image.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(heightDp)
        ));
        return image;
    }

    private LinearLayout.LayoutParams illustrationParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(108)
        );
        params.setMargins(dp(3), 0, dp(3), dp(7));
        return params;
    }

    private TextView infoPill(String value, int color) {
        TextView view = text(value, 11, color);
        view.setPadding(dp(10), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams params = matchParams();
        view.setLayoutParams(params);
        view.setBackground(rounded(0xFF1A1812, dp(8), 0x44FFC857));
        return view;
    }

    private TextView emptyState(String value) {
        TextView empty = text(value, 15, COLOR_MUTED);
        empty.setPadding(0, dp(28), 0, 0);
        return empty;
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != 0) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private void showReminderActions(Reminder reminder) {
        showActionDialog(
                reminder.name,
                translated(new String[]{"עריכה", "מחיקה", "ביטול"}),
                new int[]{COLOR_SURFACE_2, 0xFF7E2A35, COLOR_SURFACE_2},
                new Runnable[]{
                        () -> showEditor(reminder),
                        () -> {
                            store.delete(reminder);
                            showList();
                        },
                        null
                }
        );
    }

    private void showHistoryActions(ReminderEventStore eventStore, ReminderEventStore.Event event) {
        if (canReactivateTodayEvent(event)) {
            showActionDialog(
                    event.reminderName,
                    translated(new String[]{"החזר כלא בוצע", "מחיקה", "ביטול"}),
                    new int[]{COLOR_ACCENT_DARK, 0xFF7E2A35, COLOR_SURFACE_2},
                    new Runnable[]{
                            () -> reactivateTodayEvent(eventStore, event),
                            () -> deleteHistoryEvent(eventStore, event),
                            null
                    }
            );
            return;
        }
        if (canUndoEarlyDone(event)) {
            showActionDialog(
                    event.reminderName,
                    translated(new String[]{"ביטול בוצע", "מחיקה", "ביטול"}),
                    new int[]{COLOR_SURFACE_2, 0xFF7E2A35, COLOR_SURFACE_2},
                    new Runnable[]{
                            () -> undoEarlyDone(eventStore, event),
                            () -> deleteHistoryEvent(eventStore, event),
                            null
                    }
            );
            return;
        }
        showActionDialog(
                event.reminderName,
                translated(new String[]{"מחיקה", "ביטול"}),
                new int[]{0xFF7E2A35, COLOR_SURFACE_2},
                new Runnable[]{
                        () -> deleteHistoryEvent(eventStore, event),
                        null
                }
        );
    }

    private boolean canReactivateTodayEvent(ReminderEventStore.Event event) {
        return event != null
                && isToday(event.scheduledAt)
                && !ReminderEventStore.STATUS_FIRED.equals(event.status)
                && store.find(event.reminderId) != null;
    }

    private void reactivateTodayEvent(ReminderEventStore eventStore, ReminderEventStore.Event event) {
        Reminder reminder = store.find(event.reminderId);
        if (reminder == null || !reminder.enabled) {
            Toast.makeText(this, UiText.t(this, "התזכורת כבר לא קיימת"), Toast.LENGTH_LONG).show();
            showHistory(activeScrollView == null ? 0 : activeScrollView.getScrollY());
            return;
        }
        new ReminderOccurrenceStateStore(this).deleteOccurrence(event.reminderId, event.scheduledAt);
        new ReminderSnoozeStore(this).delete(event.reminderId);
        ReminderScheduler.cancelSnooze(this, event.reminderId, event.reminderName);
        ReminderScheduler.cancelDeferredRetry(this, event.reminderId);
        eventStore.delete(event.occurrenceId);
        ReminderReceiver.fire(this, event.reminderId, event.reminderName, event.scheduledAt, event.scheduledAt, -1, false);
        ComplicationRefresh.request(this);
        showHistory(activeScrollView == null ? 0 : activeScrollView.getScrollY());
    }

    private boolean isToday(long timeMillis) {
        Calendar today = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(timeMillis);
        return today.get(Calendar.YEAR) == target.get(Calendar.YEAR)
                && today.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR);
    }

    private boolean canUndoEarlyDone(ReminderEventStore.Event event) {
        return ReminderEventStore.STATUS_DONE.equals(event.status)
                && ReminderEventStore.NOTE_EARLY_DONE.equals(event.note)
                && event.scheduledAt > System.currentTimeMillis()
                && store.find(event.reminderId) != null;
    }

    private void undoEarlyDone(ReminderEventStore eventStore, ReminderEventStore.Event event) {
        Reminder reminder = store.find(event.reminderId);
        if (reminder != null && reminder.enabled) {
            ReminderScheduler.scheduleNearest(this);
        }
        new ReminderOccurrenceStateStore(this).deleteOccurrence(event.reminderId, event.scheduledAt);
        eventStore.delete(event.occurrenceId);
        ComplicationRefresh.request(this);
        showHistory(activeScrollView == null ? 0 : activeScrollView.getScrollY());
    }

    private void deleteHistoryEvent(ReminderEventStore eventStore, ReminderEventStore.Event event) {
        int scrollY = activeScrollView == null ? 0 : activeScrollView.getScrollY();
        eventStore.delete(event.occurrenceId);
        showHistory(scrollY);
    }

    private void showActionDialog(String title, String[] labels, int[] colors, Runnable[] actions) {
        LinearLayout dialogContent = new LinearLayout(this);
        dialogContent.setOrientation(LinearLayout.VERTICAL);
        dialogContent.setGravity(Gravity.CENTER);
        dialogContent.setPadding(dp(14), dp(14), dp(14), dp(14));
        dialogContent.setBackground(rounded(COLOR_SURFACE, dp(8), 0x334D5A52));

        TextView titleView = text(title, 15, COLOR_TEXT);
        AppFont.bold(titleView);
        titleView.setPadding(0, 0, 0, dp(8));
        dialogContent.addView(titleView);

        AlertDialog dialog = new AlertDialog.Builder(this).create();
        for (int i = 0; i < labels.length; i++) {
            int index = i;
            Button button = pillButton(labels[i], colors[i]);
            button.setOnClickListener(v -> {
                dialog.dismiss();
                if (actions[index] != null) {
                    actions[index].run();
                }
            });
            dialogContent.addView(button);
        }

        dialog.setView(dialogContent);
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
        });
        dialog.show();
    }

    private LinearLayout.LayoutParams matchParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(dp(14), dp(4), dp(14), dp(4));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int eventStatusColor(String status) {
        if (ReminderEventStore.STATUS_DONE.equals(status)) return 0xFF7CE38B;
        if (ReminderEventStore.STATUS_SNOOZED.equals(status)) return 0xFFFFC857;
        if (ReminderEventStore.STATUS_AUTO_SNOOZED.equals(status)) return 0xFFFFA047;
        return 0xFFFF6B6B;
    }

    private String formatTime(int hour, int minute) {
        return String.format(Locale.US, "%02d:%02d", hour, minute);
    }

    private long zmanimStartOfDay(long dateMillis) {
        Calendar calendar = zmanimCalendar(dateMillis);
        calendar.set(Calendar.HOUR_OF_DAY, 12);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private Calendar zmanimCalendar(long dateMillis) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(new ZmanimSettings(this).timeZoneId()));
        calendar.setTimeInMillis(dateMillis);
        return calendar;
    }

    private long zmanimDayOffset(long dayMillis, int days) {
        Calendar calendar = zmanimCalendar(dayMillis);
        calendar.add(Calendar.DAY_OF_YEAR, days);
        return zmanimStartOfDay(calendar.getTimeInMillis());
    }

    private long gregorianZmanimDate(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(new ZmanimSettings(this).timeZoneId()));
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month - 1);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.DAY_OF_MONTH, Math.min(day, calendar.getActualMaximum(Calendar.DAY_OF_MONTH)));
        calendar.set(Calendar.HOUR_OF_DAY, 12);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private String relativeDayLabel(long time) {
        long targetDay = zmanimStartOfDay(time);
        long today = zmanimStartOfDay(System.currentTimeMillis());
        if (targetDay == today) {
            return UiText.t(this, "היום");
        }
        if (targetDay == zmanimDayOffset(today, 1)) {
            return UiText.t(this, "מחר");
        }
        if (targetDay == zmanimDayOffset(today, -1)) {
            return UiText.t(this, "אתמול");
        }
        Calendar calendar = zmanimCalendar(time);
        return String.format(
                Locale.US,
                "%02d/%02d",
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.MONTH) + 1
        );
    }

    private long hebrewZmanimDate(int year, int month, int day) {
        try {
            JewishDate yearProbe = new JewishDate(year, JewishDate.TISHREI, 1);
            if (month == JewishDate.ADAR_II && !yearProbe.isJewishLeapYear()) {
                month = JewishDate.ADAR;
            }
            JewishDate jewishDate = new JewishDate();
            jewishDate.setJewishDate(year, month, 1);
            jewishDate.setJewishDate(year, month, Math.min(day, jewishDate.getDaysInJewishMonth()));
            return zmanimStartOfDay(jewishDate.getGregorianCalendar().getTimeInMillis());
        } catch (Exception ignored) {
            return System.currentTimeMillis();
        }
    }

    private String zmanimDateLine(long dayMillis) {
        Calendar calendar = zmanimCalendar(dayMillis);
        JewishDate jewishDate = new JewishDate(calendar);
        return String.format(
                Locale.US,
                "%02d/%02d/%04d",
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.YEAR)
        ) + " | " + hebrewDayLabel(jewishDate.getJewishDayOfMonth())
                + " " + hebrewMonthLabel(jewishDate.getJewishMonth())
                + " " + jewishDate.getJewishYear();
    }

    private void addZmanimParshaRows(LinearLayout timesCard, long dayMillis) {
        Calendar shabbos = upcomingShabbos(dayMillis);
        JewishCalendar jewishCalendar = JewishCalendarHelper.calendar(this, shabbos);
        HebrewDateFormatter formatter = JewishCalendarHelper.formatter(this);
        JewishCalendar.Parsha parsha = jewishCalendar.getParshah();
        if (parsha == JewishCalendar.Parsha.NONE) {
            parsha = jewishCalendar.getUpcomingParshah();
        }
        if (parsha != JewishCalendar.Parsha.NONE) {
            timesCard.addView(zmanimTimeRow("פרשת השבוע", formatter.formatParsha(jewishCalendar)));
        }
        JewishCalendar.Parsha special = jewishCalendar.getSpecialShabbos();
        if (special != JewishCalendar.Parsha.NONE) {
            timesCard.addView(zmanimTimeRow("שבת מיוחדת", formatter.formatSpecialParsha(jewishCalendar)));
        }
    }

    private Calendar upcomingShabbos(long dayMillis) {
        Calendar calendar = zmanimCalendar(dayMillis);
        int distance = Calendar.SATURDAY - calendar.get(Calendar.DAY_OF_WEEK);
        if (distance < 0) {
            distance += 7;
        }
        calendar.add(Calendar.DAY_OF_YEAR, distance);
        calendar.set(Calendar.HOUR_OF_DAY, 12);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    private View zmanimTimeRow(String label, long time) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(3), 0, dp(3));
        TextView name = text(label, 13, COLOR_MUTED);
        TextView value = text(time == Long.MAX_VALUE ? "לא זמין" : NextReminderCalculator.formatTime(time), 18, time == Long.MAX_VALUE ? COLOR_MUTED : COLOR_TEXT);
        AppFont.bold(value);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(name, nameParams);
        row.addView(value, valueParams);
        return row;
    }

    private void addShabbatTimesCard(LinearLayout content, long dayMillis) {
        Calendar selectedDay = zmanimCalendar(dayMillis);
        int dayOfWeek = selectedDay.get(Calendar.DAY_OF_WEEK);
        if (dayOfWeek != Calendar.FRIDAY && dayOfWeek != Calendar.SATURDAY) {
            return;
        }
        long friday = dayOfWeek == Calendar.FRIDAY ? dayMillis : zmanimDayOffset(dayMillis, -1);
        long shabbat = dayOfWeek == Calendar.SATURDAY ? dayMillis : zmanimDayOffset(dayMillis, 1);

        LinearLayout shabbatCard = card();
        TextView heading = text(UiText.t(this, "זמני שבת"), 16, COLOR_ACCENT);
        AppFont.bold(heading);
        heading.setGravity(Gravity.CENTER);
        heading.setPadding(0, 0, 0, dp(5));
        shabbatCard.addView(heading);
        shabbatCard.addView(zmanimTimeRow(UiText.t(this, "הדלקת נרות"),
                ZmanimHelper.shabbatTimeForKey(this, ZmanimHelper.KEY_CANDLE_LIGHTING, friday)));
        shabbatCard.addView(zmanimTimeRow(UiText.t(this, "צאת שבת"),
                ZmanimHelper.shabbatTimeForKey(this, ZmanimHelper.KEY_SHABBAT_END, shabbat)));
        shabbatCard.addView(zmanimTimeRow(UiText.t(this, "צאת שבת ר״ת"),
                ZmanimHelper.shabbatTimeForKey(this, ZmanimHelper.KEY_RABBEINU_TAM, shabbat)));
        content.addView(shabbatCard, cardParams());
    }

    private int zmanimBackgroundResource(long dayMillis) {
        int dayOfWeek = zmanimCalendar(dayMillis).get(Calendar.DAY_OF_WEEK);
        if (dayOfWeek == Calendar.FRIDAY || dayOfWeek == Calendar.SATURDAY) {
            return R.drawable.shabbat_horizon;
        }
        return R.drawable.zmanim_horizon;
    }

    private View moonBlessingRow(long dayMillis) {
        MoonBlessingHelper.Window window = MoonBlessingHelper.windowFor(this, dayMillis);
        String label = UiText.t(this, "ברכת הלבנה") + " " + hebrewMonthLabel(window.jewishMonth);
        String value = AppLanguage.isEnglish(this)
                ? "From " + formatMoonBlessingTime(window.startAt) + " until " + formatMoonBlessingTime(window.endAt)
                : "מ-" + formatMoonBlessingTime(window.startAt) + " עד " + formatMoonBlessingTime(window.endAt);
        if (window.startAdjusted || window.endAdjusted) {
            value += " *";
        }
        return zmanimTimeRow(label, value);
    }

    private String formatMoonBlessingTime(long time) {
        Calendar calendar = zmanimCalendar(time);
        return String.format(
                Locale.US,
                "%02d/%02d %02d:%02d",
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE)
        );
    }

    private View zmanimTimeRow(String label, String valueText) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(3), 0, dp(3));
        TextView name = text(label, 13, COLOR_MUTED);
        TextView value = text(valueText, 15, COLOR_TEXT);
        AppFont.bold(value);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(name, nameParams);
        row.addView(value, valueParams);
        return row;
    }

    private String nextReminderId(List<Reminder> reminders) {
        ReminderSnoozeStore snoozeStore = new ReminderSnoozeStore(this);
        long bestTime = Long.MAX_VALUE;
        String bestId = null;
        for (Reminder reminder : reminders) {
            NextReminderCalculator.NextReminder nextReminder = nextReminderAt(reminder, snoozeStore);
            if (nextReminder == null) {
                continue;
            }
            long nextAt = nextReminder.scheduledAt;
            if (nextAt < bestTime) {
                bestTime = nextAt;
                bestId = reminder.id;
            }
        }
        return bestId;
    }

    private NextReminderCalculator.NextReminder nextReminderAt(Reminder reminder, ReminderSnoozeStore snoozeStore) {
        return NextReminderCalculator.nextForReminder(this, reminder, snoozeStore, new ReminderEventStore(this));
    }

    private NextReminderCalculator.NextReminder nextReminderAt(Reminder reminder, ReminderSnoozeStore snoozeStore, ReminderEventStore eventStore) {
        return NextReminderCalculator.nextForReminder(this, reminder, snoozeStore, eventStore);
    }

    private long nextReminderAt(Reminder reminder) {
        return NextReminderCalculator.nextRegularAt(this, reminder, new ReminderEventStore(this));
    }

    private long nextReminderAt(Reminder reminder, ReminderEventStore eventStore) {
        return NextReminderCalculator.nextRegularAt(this, reminder, eventStore);
    }

    private String nextReminderLine(Reminder reminder, ReminderSnoozeStore snoozeStore) {
        return nextReminderLine(reminder, snoozeStore, new ReminderEventStore(this));
    }

    private String nextReminderLine(Reminder reminder, ReminderSnoozeStore snoozeStore, ReminderEventStore eventStore) {
        if (!reminder.enabled) {
            return UiText.t(this, "כבוי");
        }
        long snoozeAt = NextReminderCalculator.pendingSnoozeAt(reminder.id, snoozeStore);
        long regularAt = nextReminderAt(reminder, eventStore);
        if (snoozeAt <= regularAt) {
            return (AppLanguage.isEnglish(this) ? "Snoozed to: " : "נדחה ל: ") + formatDateTime(snoozeAt);
        }
        if (regularAt == Long.MAX_VALUE) {
            return reminder.isOneTime()
                    ? (AppLanguage.isEnglish(this) ? "Time has passed" : "עבר הזמן")
                    : (AppLanguage.isEnglish(this) ? "No future time" : "אין מועד עתידי");
        }
        return (AppLanguage.isEnglish(this) ? "Next: " : "הבא: ") + formatDateTime(regularAt);
    }

    private String nextReminderLine(Reminder reminder, NextReminderCalculator.NextReminder nextReminder) {
        if (!reminder.enabled) {
            return UiText.t(this, "כבוי");
        }
        if (nextReminder == null) {
            return reminder.isOneTime()
                    ? (AppLanguage.isEnglish(this) ? "Time has passed" : "עבר הזמן")
                    : (AppLanguage.isEnglish(this) ? "No future time" : "אין מועד עתידי");
        }
        return (nextReminder.snoozed
                ? (AppLanguage.isEnglish(this) ? "Snoozed to: " : "נדחה ל: ")
                : (AppLanguage.isEnglish(this) ? "Next: " : "הבא: "))
                + formatDateTime(nextReminder.scheduledAt);
    }

    private String reminderDetails(Reminder reminder) {
        return reminderDetails(reminder, null);
    }

    private String reminderDetailsFast(Reminder reminder) {
        String prefix = reminder.useZmanim
                ? formatZmanimOffset(reminder.zmanimOffsetMinutes)
                + (AppLanguage.isEnglish(this) ? " | Actual next: calculating... | " : " | הקרוב בפועל: מחשב... | ")
                : "";
        if (reminder.isOneTime()) {
            return prefix + (AppLanguage.isEnglish(this) ? "One time: " : "חד פעמית: ") + formatDateTime(reminder.oneTimeAt);
        }
        if (reminder.isPeriodic()) {
            return prefix
                    + (AppLanguage.isEnglish(this) ? "Repeats every " : "מחזורית: כל ")
                    + reminder.periodicInterval + " " + periodicUnitLabel(reminder.periodicUnit)
                    + (AppLanguage.isEnglish(this) ? " | Starts: " : " | התחלה: ")
                    + periodicStartLabel(reminder)
                    + periodicHourlyEndLabel(reminder);
        }
        if (reminder.isAnnualEvent()) {
            return (AppLanguage.isEnglish(this) ? "Annual event: " : "אירוע שנתי: ") + annualDateLabel(reminder)
                    + " | " + annualTimingLabel(reminder.annualAdvanceHours)
                    + (AppLanguage.isEnglish(this) ? " | Next: calculating..." : " | הבא: מחשב...");
        }
        return prefix + formatDays(reminder.days);
    }

    private String reminderDetails(Reminder reminder, NextReminderCalculator.NextReminder nextReminder) {
        String prefix = reminder.useZmanim
                ? formatZmanimOffset(reminder.zmanimOffsetMinutes)
                + (AppLanguage.isEnglish(this) ? " | Actual next: " : " | הקרוב בפועל: ")
                + nextZmanimReminderActualLine(reminder, nextReminder)
                + " | "
                : "";
        if (reminder.isOneTime()) {
            return prefix + (AppLanguage.isEnglish(this) ? "One time: " : "חד פעמית: ") + formatDateTime(reminder.oneTimeAt);
        }
        if (reminder.isPeriodic()) {
            return prefix
                    + (AppLanguage.isEnglish(this) ? "Repeats every " : "מחזורית: כל ")
                    + reminder.periodicInterval + " " + periodicUnitLabel(reminder.periodicUnit)
                    + (AppLanguage.isEnglish(this) ? " | Starts: " : " | התחלה: ")
                    + periodicStartLabel(reminder)
                    + periodicHourlyEndLabel(reminder);
        }
        if (reminder.isAnnualEvent()) {
            return (AppLanguage.isEnglish(this) ? "Annual event: " : "אירוע שנתי: ") + annualDateLabel(reminder)
                    + " | " + annualTimingLabel(reminder.annualAdvanceHours)
                    + (AppLanguage.isEnglish(this) ? " | Next: " : " | הבא: ")
                    + annualNextDisplayName(reminder);
        }
        return prefix + formatDays(reminder.days);
    }

    private String nextZmanimReminderActualLine(Reminder reminder, NextReminderCalculator.NextReminder next) {
        if (next == null) {
            next = NextReminderCalculator.nextForReminder(
                    this,
                    reminder,
                    new ReminderSnoozeStore(this),
                    new ReminderEventStore(this)
            );
        }
        if (next == null || next.scheduledAt == Long.MAX_VALUE) {
            return "לא זמין";
        }
        return relativeDayLabel(next.scheduledAt) + " " + NextReminderCalculator.formatTime(next.scheduledAt);
    }

    private String historyDescription(ReminderEventStore.Event event) {
        if (!event.description.isEmpty()) {
            return event.description;
        }
        Reminder reminder = store.find(event.reminderId);
        return reminder == null ? "" : reminder.description;
    }

    private String annualDateLabel(Reminder reminder) {
        if (reminder.annualHebrew) {
            return hebrewDayLabel(reminder.annualDay) + " " + hebrewMonthLabel(reminder.annualMonth)
                    + (AppLanguage.isEnglish(this) ? " Hebrew" : " עברי");
        }
        return reminder.annualDay + "/" + reminder.annualMonth + (AppLanguage.isEnglish(this) ? " Gregorian" : " לועזי");
    }

    private String annualNextDisplayName(Reminder reminder) {
        AnnualReminderHelper.Occurrence occurrence = AnnualReminderHelper.next(this, reminder, new ReminderEventStore(this), true);
        return occurrence == null ? reminder.name : AnnualReminderHelper.displayName(reminder, occurrence.originalAt);
    }

    private String periodicUnitLabel(String unit) {
        if (Reminder.PERIOD_UNIT_HOURS.equals(unit)) return UiText.t(this, "שעות");
        if (Reminder.PERIOD_UNIT_WEEKS.equals(unit)) return UiText.t(this, "שבועות");
        if (Reminder.PERIOD_UNIT_MONTHS.equals(unit)) return UiText.t(this, "חודשים");
        if (Reminder.PERIOD_UNIT_YEARS.equals(unit)) return UiText.t(this, "שנים");
        return UiText.t(this, "ימים");
    }

    private String periodicStartLabel(Reminder reminder) {
        if (reminder.periodicHebrew) {
            return hebrewDayLabel(reminder.periodicStartDay) + " " + hebrewMonthLabel(reminder.periodicStartMonth) + " " + reminder.periodicStartYear
                    + (AppLanguage.isEnglish(this) ? " Hebrew" : " עברי");
        }
        return String.format(Locale.US, "%02d/%02d/%04d", reminder.periodicStartDay, reminder.periodicStartMonth, reminder.periodicStartYear);
    }

    private String periodicHourlyEndLabel(Reminder reminder) {
        if (!Reminder.PERIOD_UNIT_HOURS.equals(reminder.periodicUnit)) {
            return "";
        }
        String time = String.format(Locale.US, "%02d:%02d", reminder.periodicEndHour, reminder.periodicEndMinute);
        return AppLanguage.isEnglish(this) ? " | Until: " + time : " | עד: " + time;
    }

    private String annualTimingLabel(int advanceHours) {
        if (advanceHours <= 0) {
            return AppLanguage.isEnglish(this) ? "On time" : "בזמן";
        }
        return AppLanguage.isEnglish(this)
                ? advanceHours + " hours before + on time"
                : advanceHours + " שעות לפני + בזמן";
    }

    private String hebrewDayLabel(int day) {
        if (AppLanguage.isEnglish(this)) {
            return String.valueOf(day);
        }
        String[] labels = {
                "", "א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט",
                "י", "יא", "יב", "יג", "יד", "טו", "טז", "יז", "יח", "יט",
                "כ", "כא", "כב", "כג", "כד", "כה", "כו", "כז", "כח", "כט", "ל"
        };
        if (day < 1 || day >= labels.length) {
            return String.valueOf(day);
        }
        return labels[day];
    }

    private String hebrewMonthLabel(int month) {
        if (AppLanguage.isEnglish(this)) {
            switch (month) {
                case JewishDate.NISSAN:
                    return "Nissan";
                case JewishDate.IYAR:
                    return "Iyar";
                case JewishDate.SIVAN:
                    return "Sivan";
                case JewishDate.TAMMUZ:
                    return "Tammuz";
                case JewishDate.AV:
                    return "Av";
                case JewishDate.ELUL:
                    return "Elul";
                case JewishDate.TISHREI:
                    return "Tishrei";
                case JewishDate.CHESHVAN:
                    return "Cheshvan";
                case JewishDate.KISLEV:
                    return "Kislev";
                case JewishDate.TEVES:
                    return "Tevet";
                case JewishDate.SHEVAT:
                    return "Shevat";
                case JewishDate.ADAR:
                    return "Adar";
                case JewishDate.ADAR_II:
                    return "Adar II";
                default:
                    return String.valueOf(month);
            }
        }
        switch (month) {
            case JewishDate.NISSAN:
                return "ניסן";
            case JewishDate.IYAR:
                return "אייר";
            case JewishDate.SIVAN:
                return "סיוון";
            case JewishDate.TAMMUZ:
                return "תמוז";
            case JewishDate.AV:
                return "אב";
            case JewishDate.ELUL:
                return "אלול";
            case JewishDate.TISHREI:
                return "תשרי";
            case JewishDate.CHESHVAN:
                return "חשוון";
            case JewishDate.KISLEV:
                return "כסלו";
            case JewishDate.TEVES:
                return "טבת";
            case JewishDate.SHEVAT:
                return "שבט";
            case JewishDate.ADAR:
                return "אדר";
            case JewishDate.ADAR_II:
                return "אדר ב׳";
            default:
                return String.valueOf(month);
        }
    }

    private String formatZmanimOffset(int minutes) {
        if (minutes == 0) return AppLanguage.isEnglish(this) ? "On time" : "בזמן";
        if (AppLanguage.isEnglish(this)) {
            return Math.abs(minutes) + " min " + (minutes < 0 ? "before" : "after");
        }
        return Math.abs(minutes) + " דק׳ " + (minutes < 0 ? "לפני" : "אחרי");
    }

    private String nextHistoryLine(ReminderEventStore.Event event) {
        ReminderSnoozeStore snoozeStore = new ReminderSnoozeStore(this);
        long snoozeAt = NextReminderCalculator.pendingSnoozeAt(event.reminderId, snoozeStore);
        if (snoozeAt != Long.MAX_VALUE) {
            return (AppLanguage.isEnglish(this) ? "Snoozed to: " : "נדחה ל: ") + formatDateTime(snoozeAt);
        }
        Reminder reminder = store.find(event.reminderId);
        if (reminder == null) {
            return AppLanguage.isEnglish(this) ? "No future time" : "אין מועד עתידי";
        }
        return (AppLanguage.isEnglish(this) ? "Next: " : "הבא: ") + formatDateTime(nextReminderAt(reminder));
    }

    private String formatDateTime(long time) {
        return NextReminderCalculator.formatDateTime(time);
    }

    private int indexOf(String[] values, String target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(target)) {
                return i;
            }
        }
        return 0;
    }

    private String formatDays(Set<Integer> days) {
        int[] order = {Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY};
        String[] labels = AppLanguage.isEnglish(this)
                ? new String[]{"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"}
                : new String[]{"א", "ב", "ג", "ד", "ה", "ו", "ש"};
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < order.length; i++) {
            if (days.contains(order[i])) {
                if (builder.length() > 0) builder.append(' ');
                builder.append(labels[i]);
            }
        }
        return builder.toString();
    }

    private static class QuietBoundaryViews {
        LinearLayout card;
        Switch useZmanim;
        LinearLayout fixedRow;
        LinearLayout zmanimSection;
        NumberPicker hour;
        NumberPicker minute;
        Spinner zmanim;
        NumberPicker offset;
    }

    private static class QuietWindow {
        final long start;
        final long end;

        QuietWindow(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }
}
