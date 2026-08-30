package com.woodpeckerbros.watchreminder.phone;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.kosherjava.zmanim.hebrewcalendar.JewishDate;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PhoneMainActivity extends Activity {
    private static final int REQUEST_PICK_BACKUP = 201;
    private static final int REQUEST_PICK_LOG = 202;
    private static final int BG = 0xFF091C2B;
    private static final int SURFACE = 0xF0142B3A;
    private static final int SURFACE_2 = 0xFF1B3445;
    private static final int TEXT = 0xFFF4EBDD;
    private static final int MUTED = 0xFFBEC4BD;
    private static final int ACCENT = 0xFF747D63;
    private static final int COPPER = 0xFFC77B58;
    private static final int SOFT = 0xFF20394A;
    private static final int BORDER = 0xFF53695F;
    private static final String[] ZMANIM_KEYS = {"ALOS", "SUNRISE", "SHMA_GRA", "TFILA_GRA", "CHATZOS", "MINCHA_GEDOLA", "MINCHA_KETANA", "PLAG", "SUNSET", "TZAIS"};
    private static final String[] ZMANIM_LABELS = {"עלות השחר", "זריחה", "סוף זמן שמע", "סוף זמן תפילה", "חצות", "מנחה גדולה", "מנחה קטנה", "פלג המנחה", "שקיעה", "צאת הכוכבים"};
    private static final String[] TYPE_LABELS = {"חד פעמית", "קבועה", "מחזורית", "אירוע שנתי"};
    private static final String[] UNIT_LABELS = {"שעות", "ימים", "שבועות", "חודשים", "שנים"};
    private static final String[] UNIT_VALUES = {"hours", "days", "weeks", "months", "years"};
    private String screen = "main";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
        showMain();
        handleExternalBackupIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleExternalBackupIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if ("main".equals(screen)) {
            showMain();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        if (requestCode == REQUEST_PICK_BACKUP) {
            importPickedBackup(uri);
        } else if (requestCode == REQUEST_PICK_LOG) {
            showPickedLog(uri);
        }
    }

    private void showMain() {
        screen = "main";
        LinearLayout content = base();
        addHeader(content, "Zmanio", "ניהול תזכורות שעון");

        LinearLayout actions = row();
        Button sync = button("סנכרון מהשעון", ACCENT);
        sync.setOnClickListener(v -> requestSync());
        Button push = button("שליחה לשעון", COPPER);
        push.setOnClickListener(v -> pushToWatch());
        actions.addView(sync);
        actions.addView(push);
        content.addView(actions);

        LinearLayout actions2 = row();
        Button add = button("הוספה", ACCENT);
        add.setOnClickListener(v -> showEditor(null, -1));
        Button settings = button("הגדרות", SOFT, TEXT);
        settings.setOnClickListener(v -> showSettings());
        Button smartAlarms = button("שעונים חכמים", SOFT, TEXT);
        smartAlarms.setOnClickListener(v -> showSmartAlarms());
        actions2.addView(add);
        actions2.addView(smartAlarms);
        actions2.addView(settings);
        content.addView(actions2);

        LinearLayout statusCard = card();
        TextView statusLabel = text("מצב החיבור", 12, MUTED);
        TextView status = text(statusLine(), 15, TEXT);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status.setPadding(0, dp(4), 0, 0);
        statusCard.addView(statusLabel);
        statusCard.addView(status);
        content.addView(statusCard, wideParams());

        JSONArray reminders = reminders();
        TextView remindersTitle = text("התזכורות שלי", 20, TEXT);
        remindersTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        remindersTitle.setGravity(PhoneUiText.isEnglish(this) ? Gravity.LEFT : Gravity.RIGHT);
        remindersTitle.setPadding(dp(6), dp(18), dp(6), dp(8));
        content.addView(remindersTitle, wideParams());
        if (reminders.length() == 0) {
            LinearLayout emptyCard = card();
            TextView emptyIcon = text("⌚", 30, COPPER);
            TextView empty = text("אין תזכורות בטלפון", 17, TEXT);
            empty.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            TextView emptyHint = text("לחץ על סנכרון מהשעון כדי להתחיל", 14, MUTED);
            emptyHint.setPadding(0, dp(6), 0, 0);
            emptyCard.addView(emptyIcon);
            emptyCard.addView(empty);
            emptyCard.addView(emptyHint);
            content.addView(emptyCard, wideParams());
        } else {
            for (DisplayReminder display : sortedDisplayReminders(reminders)) {
                JSONObject reminder = display.reminder;
                if (reminder == null) continue;
                LinearLayout card = card();
                card.setOnClickListener(v -> showEditor(reminder, display.index));
                card.setOnLongClickListener(v -> {
                    showReminderActions(reminder);
                    return true;
                });
                TextView time = text(timeTitle(reminder), 21, reminder.optBoolean("enabled", true) ? COPPER : MUTED);
                time.setTypeface(Typeface.DEFAULT_BOLD);
                TextView name = text(reminder.optString("name", "תזכורת"), 17, TEXT);
                name.setTypeface(Typeface.DEFAULT_BOLD);
                TextView details = text(details(reminder), 13, MUTED);
                details.setPadding(8, 4, 8, 0);
                card.addView(time);
                card.addView(name);
                String description = reminder.optString("description", "");
                if (!description.isEmpty()) {
                    card.addView(text(description, 13, MUTED));
                }
                card.addView(details);
                content.addView(card, wideParams());
            }
        }
        setScroll(content);
    }

    private void showSmartAlarms() {
        screen = "smart_alarms";
        LinearLayout content = base();
        addHeader(content, "שעונים מעוררים חכמים", "ניהול השעונים החכמים שמסונכרנים עם השעון");
        JSONArray alarms = smartAlarms();
        if (alarms.length() == 0) {
            LinearLayout empty = card();
            empty.addView(text("אין שעונים מעוררים חכמים", 17, TEXT));
            empty.addView(text("סנכרן מהשעון או הוסף שעון חדש", 14, MUTED));
            content.addView(empty, wideParams());
        }
        for (int index = 0; index < alarms.length(); index++) {
            JSONObject alarm = alarms.optJSONObject(index);
            if (alarm == null) continue;
            final int alarmIndex = index;
            LinearLayout alarmCard = card();
            alarmCard.setOnClickListener(v -> showSmartAlarmEditor(alarm, alarmIndex));
            alarmCard.setOnLongClickListener(v -> {
                showSmartAlarmActions(alarm, alarmIndex);
                return true;
            });
            TextView time = text(String.format(Locale.US, "%02d:%02d", alarm.optInt("hour", 6), alarm.optInt("minute", 30)),
                    24, alarm.optBoolean("enabled", true) ? COPPER : MUTED);
            time.setTypeface(Typeface.DEFAULT_BOLD);
            alarmCard.addView(time);
            alarmCard.addView(text(smartAlarmDays(alarm.optInt("daysMask", 126)), 13, MUTED));
            alarmCard.addView(text(alarm.optBoolean("enabled", true) ? "פעיל" : "כבוי", 13, TEXT));
            content.addView(alarmCard, wideParams());
        }
        LinearLayout actions = row();
        Button add = button("הוספת שעון מעורר", ACCENT);
        add.setOnClickListener(v -> showSmartAlarmEditor(null, -1));
        Button back = button("חזרה", SOFT, TEXT);
        back.setOnClickListener(v -> showMain());
        actions.addView(add); actions.addView(back); content.addView(actions);
        setScroll(content);
    }

    private void showSmartAlarmEditor(JSONObject source, int index) {
        screen = "smart_alarm_editor";
        JSONObject alarm = source == null ? newSmartAlarm() : copy(source);
        LinearLayout content = base();
        addHeader(content, source == null ? "שעון חכם חדש" : "עריכת שעון חכם",
                "הנתונים יישמרו בטלפון עד שליחה לשעון");
        LinearLayout stateCard = card();
        Switch enabled = switchView("פעיל", alarm.optBoolean("enabled", true));
        stateCard.addView(enabled); content.addView(stateCard, wideParams());

        TimePicker time = new TimePicker(this);
        time.setIs24HourView(true); time.setHour(alarm.optInt("hour", 6)); time.setMinute(alarm.optInt("minute", 30));
        content.addView(labeled("שעת השכמה", time), wideParams());

        LinearLayout daysCard = card();
        daysCard.addView(text("ימי פעילות", 15, TEXT));
        CheckBox[] days = new CheckBox[7];
        String[] dayLabels = {"ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת"};
        int initialMask = alarm.optInt("daysMask", 126);
        for (int day = 0; day < 7; day++) {
            days[day] = new CheckBox(this); days[day].setText(t(dayLabels[day])); days[day].setTextColor(TEXT);
            days[day].setChecked((initialMask & (1 << (Calendar.SUNDAY + day))) != 0); daysCard.addView(days[day]);
        }
        content.addView(daysCard, wideParams());

        NumberPicker window = numberPicker(5, 60, alarm.optInt("windowMinutes", 30));
        content.addView(labeled("חלון חכם בדקות", window), wideParams());
        String[] backgroundValues = {"morning", "noon", "evening", "night", "dynamic"};
        Spinner backgroundStyle = spinner(new String[]{"בוקר", "צהריים", "ערב", "לילה", "משתנה לפי השעה"});
        String currentBackground = alarm.optString("backgroundStyle", "morning");
        if ("sunrise".equals(currentBackground)) currentBackground = "morning";
        int backgroundIndex = 0;
        for (int i = 0; i < backgroundValues.length; i++) {
            if (backgroundValues[i].equals(currentBackground)) backgroundIndex = i;
        }
        backgroundStyle.setSelection(backgroundIndex);
        content.addView(labeled("רקע מסך ההתראה", backgroundStyle), wideParams());
        String[] dismissValues = {"tap", "hold", "double_tap", "shake", "steps", "math", "memory",
                "alternating", "random", "combination"};
        String[] dismissLabels = {"לחיצה רגילה", "לחיצה ארוכה", "לחיצה כפולה",
                "ניעור היד", "הליכה", "תרגיל חשבון", "תרגיל זיכרון", "לחיצות מתחלפות",
                "משימה אקראית", "משימות מרובות"};
        String currentDismiss = alarm.optString("dismissMethod", "tap");
        int dismissIndex = 0;
        for (int i = 0; i < dismissValues.length; i++) {
            if (dismissValues[i].equals(currentDismiss)) dismissIndex = i;
        }
        final int[] selectedDismissIndex = {dismissIndex};
        LinearLayout dismissCard = card();
        dismissCard.addView(text("אופן כיבוי השעון", 15, TEXT));
        Button[] dismissButtons = new Button[dismissLabels.length];
        LinearLayout dismissSettings = new LinearLayout(this); dismissSettings.setOrientation(LinearLayout.VERTICAL);
        int[] multipleTaskBits = {1, 2, 4, 8, 16};
        String[] multipleTaskLabels = {"ניעור היד", "הליכה", "תרגיל חשבון", "תרגיל זיכרון", "לחיצות מתחלפות"};
        CheckBox[] multipleTasks = new CheckBox[multipleTaskBits.length];
        LinearLayout multipleTaskOptions = new LinearLayout(this); multipleTaskOptions.setOrientation(LinearLayout.VERTICAL);
        multipleTaskOptions.addView(text("בחרו את משימות ההשכמה", 13, TEXT));
        int initialMultipleMask = alarm.optInt("multipleTaskMask", 31);
        for (int taskIndex = 0; taskIndex < multipleTasks.length; taskIndex++) {
            CheckBox task = new CheckBox(this); task.setText(t(multipleTaskLabels[taskIndex])); task.setTextColor(TEXT);
            task.setChecked((initialMultipleMask & multipleTaskBits[taskIndex]) != 0);
            multipleTasks[taskIndex] = task; multipleTaskOptions.addView(task);
        }
        dismissSettings.addView(multipleTaskOptions);
        NumberPicker dismissHoldSeconds = numberPicker(1, 10, alarm.optInt("dismissHoldSeconds", 3));
        View holdOptions = labeled("משך לחיצה ארוכה בשניות", dismissHoldSeconds); dismissSettings.addView(holdOptions);
        NumberPicker shakeCount = numberPicker(5, 40, alarm.optInt("shakeCount", 12));
        View shakeOptions = labeled("מספר ניעורים", shakeCount); dismissSettings.addView(shakeOptions);
        NumberPicker stepCount = numberPicker(5, 100, alarm.optInt("stepCount", 20));
        View stepOptions = labeled("מספר צעדים", stepCount); dismissSettings.addView(stepOptions);
        NumberPicker alternatingTaps = numberPicker(4, 30, alarm.optInt("alternatingTapCount", 10));
        View alternatingOptions = labeled("מספר לחיצות מתחלפות", alternatingTaps); dismissSettings.addView(alternatingOptions);
        NumberPicker mathDifficulty = numberPicker(1, 3, alarm.optInt("mathDifficulty", 1));
        View mathOptions = labeled("רמת חשבון", mathDifficulty); dismissSettings.addView(mathOptions);
        NumberPicker memoryDifficulty = numberPicker(1, 3, alarm.optInt("memoryDifficulty", 1));
        View memoryOptions = labeled("רמת זיכרון", memoryDifficulty); dismissSettings.addView(memoryOptions);
        Runnable updateDismissUi = () -> {
            String method = dismissValues[selectedDismissIndex[0]];
            boolean random = "random".equals(method), multiple = "combination".equals(method);
            multipleTaskOptions.setVisibility(multiple ? View.VISIBLE : View.GONE);
            holdOptions.setVisibility("hold".equals(method) ? View.VISIBLE : View.GONE);
            shakeOptions.setVisibility("shake".equals(method) || random || (multiple && multipleTasks[0].isChecked()) ? View.VISIBLE : View.GONE);
            stepOptions.setVisibility("steps".equals(method) || random || (multiple && multipleTasks[1].isChecked()) ? View.VISIBLE : View.GONE);
            mathOptions.setVisibility("math".equals(method) || random || (multiple && multipleTasks[2].isChecked()) ? View.VISIBLE : View.GONE);
            memoryOptions.setVisibility("memory".equals(method) || random || (multiple && multipleTasks[3].isChecked()) ? View.VISIBLE : View.GONE);
            alternatingOptions.setVisibility("alternating".equals(method) || random || (multiple && multipleTasks[4].isChecked()) ? View.VISIBLE : View.GONE);
            for (int i = 0; i < dismissButtons.length; i++) {
                dismissButtons[i].setBackground(round(i == selectedDismissIndex[0] ? ACCENT : SOFT, dp(20), BORDER));
                dismissButtons[i].setTextColor(i == selectedDismissIndex[0] ? Color.WHITE : TEXT);
            }
        };
        for (CheckBox task : multipleTasks) task.setOnCheckedChangeListener((button, checked) -> updateDismissUi.run());
        for (int start = 0; start < dismissLabels.length; start += 2) {
            LinearLayout choicesRow = row();
            for (int i = start; i < Math.min(start + 2, dismissLabels.length); i++) {
                final int choiceIndex = i;
                Button choice = button(dismissLabels[i], SOFT, TEXT); dismissButtons[i] = choice;
                choice.setOnClickListener(v -> { selectedDismissIndex[0] = choiceIndex; updateDismissUi.run(); });
                choicesRow.addView(choice);
            }
            dismissCard.addView(choicesRow);
        }
        dismissCard.addView(dismissSettings); updateDismissUi.run(); content.addView(dismissCard, wideParams());
        Switch wakeCheckEnabled = switchView("בדיקת ערנות לאחר הכיבוי", alarm.optBoolean("wakeCheckEnabled", false));
        content.addView(wakeCheckEnabled, wideParams());
        NumberPicker wakeCheckDelay = numberPicker(1, 30, alarm.optInt("wakeCheckDelayMinutes", 5));
        View wakeCheckDelayOptions = labeled("בדיקת ערנות אחרי דקות", wakeCheckDelay);
        wakeCheckDelayOptions.setVisibility(wakeCheckEnabled.isChecked() ? View.VISIBLE : View.GONE);
        wakeCheckEnabled.setOnClickListener(v -> wakeCheckDelayOptions.setVisibility(
                wakeCheckEnabled.isChecked() ? View.VISIBLE : View.GONE));
        content.addView(wakeCheckDelayOptions, wideParams());
        LinearLayout snooze = card();
        snooze.addView(text("נודניק", 15, TEXT));
        NumberPicker snoozeMinutes = numberPicker(1, 30, alarm.optInt("snoozeMinutes", 5));
        NumberPicker snoozeCount = numberPicker(0, 10, alarm.optInt("snoozeCount", 3));
        snooze.addView(labeled("כל כמה דקות", snoozeMinutes));
        snooze.addView(labeled("מספר פעמים", snoozeCount));
        content.addView(snooze, wideParams());

        LinearLayout feedback = card();
        feedback.addView(text("רטט וצלצול", 15, TEXT));
        Switch vibrationEnabled = switchView("רטט", alarm.optBoolean("vibrationEnabled", true));
        Spinner vibrationStyle = spinner(new String[]{"רגיל", "עדין", "חזק", "ארוך"});
        String[] vibrationValues = {"normal", "gentle", "strong", "long"};
        String currentStyle = alarm.optString("vibrationStyle", "normal");
        int styleIndex = 0; for (int i = 0; i < vibrationValues.length; i++) if (vibrationValues[i].equals(currentStyle)) styleIndex = i;
        vibrationStyle.setSelection(styleIndex);
        NumberPicker vibrationStrength = numberPicker(1, 10, alarm.optInt("vibrationStrength", 6));
        Switch soundEnabled = switchView("צלצול", alarm.optBoolean("soundEnabled", true));
        NumberPicker soundVolume = numberPicker(0, 10, alarm.optInt("soundVolumePercent", 80) / 10);
        NumberPicker duration = numberPicker(5, 120, alarm.optInt("alertDurationSeconds", 30));
        feedback.addView(vibrationEnabled); feedback.addView(labeled("סוג רטט", vibrationStyle));
        feedback.addView(labeled("עוצמת רטט", vibrationStrength)); feedback.addView(soundEnabled);
        feedback.addView(labeled("עוצמת צלצול", soundVolume));
        feedback.addView(labeled("משך ההתראה בשניות", duration));
        content.addView(feedback, wideParams());

        LinearLayout actions = row();
        Button save = button("שמירה", ACCENT);
        save.setOnClickListener(v -> {
            try {
                int daysMask = 0;
                for (int day = 0; day < days.length; day++) if (days[day].isChecked()) daysMask |= 1 << (Calendar.SUNDAY + day);
                if (enabled.isChecked() && daysMask == 0) {
                    Toast.makeText(this, t("צריך לבחור לפחות יום אחד"), Toast.LENGTH_LONG).show(); return;
                }
                int multipleTaskMask = 0;
                for (int taskIndex = 0; taskIndex < multipleTasks.length; taskIndex++) {
                    if (multipleTasks[taskIndex].isChecked()) multipleTaskMask |= multipleTaskBits[taskIndex];
                }
                if ("combination".equals(dismissValues[selectedDismissIndex[0]]) && Integer.bitCount(multipleTaskMask) < 2) {
                    Toast.makeText(this, t("יש לבחור לפחות שתי משימות"), Toast.LENGTH_LONG).show(); return;
                }
                alarm.put("enabled", enabled.isChecked()).put("hour", time.getHour()).put("minute", time.getMinute())
                        .put("daysMask", daysMask).put("windowMinutes", numberValue(window))
                        .put("snoozeMinutes", numberValue(snoozeMinutes)).put("snoozeCount", numberValue(snoozeCount))
                        .put("vibrationEnabled", vibrationEnabled.isChecked())
                        .put("vibrationStyle", vibrationValues[vibrationStyle.getSelectedItemPosition()])
                        .put("vibrationStrength", numberValue(vibrationStrength)).put("soundEnabled", soundEnabled.isChecked())
                        .put("soundVolumePercent", numberValue(soundVolume) * 10)
                        .put("soundUri", alarm.optString("soundUri", ""))
                        .put("alertDurationSeconds", numberValue(duration))
                        .put("backgroundStyle", backgroundValues[backgroundStyle.getSelectedItemPosition()])
                        .put("dismissMethod", dismissValues[selectedDismissIndex[0]])
                        .put("dismissHoldSeconds", numberValue(dismissHoldSeconds))
                        .put("mathDifficulty", numberValue(mathDifficulty))
                        .put("memoryDifficulty", numberValue(memoryDifficulty))
                        .put("shakeCount", numberValue(shakeCount)).put("stepCount", numberValue(stepCount))
                        .put("alternatingTapCount", numberValue(alternatingTaps))
                        .put("multipleTaskMask", multipleTaskMask)
                        .put("wakeCheckEnabled", wakeCheckEnabled.isChecked())
                        .put("wakeCheckDelayMinutes", numberValue(wakeCheckDelay));
                saveSmartAlarm(alarm, index); showSmartAlarms();
            } catch (Exception error) { Toast.makeText(this, t("לא הצלחתי לשמור"), Toast.LENGTH_LONG).show(); }
        });
        Button cancel = button("ביטול", SOFT, TEXT); cancel.setOnClickListener(v -> showSmartAlarms());
        actions.addView(save); actions.addView(cancel); content.addView(actions);
        setScroll(content);
    }

    private List<DisplayReminder> sortedDisplayReminders(JSONArray reminders) {
        ArrayList<DisplayReminder> items = new ArrayList<>();
        for (int i = 0; i < reminders.length(); i++) {
            JSONObject reminder = reminders.optJSONObject(i);
            if (reminder != null) {
                items.add(new DisplayReminder(reminder, i));
            }
        }
        items.sort((left, right) -> compareDisplayReminders(left.reminder, right.reminder));
        return items;
    }

    private int compareDisplayReminders(JSONObject left, JSONObject right) {
        int byGroup = Integer.compare(displayGroup(left), displayGroup(right));
        if (byGroup != 0) return byGroup;
        if (isAnnualEvent(left) && isAnnualEvent(right)) {
            int byMonth = Integer.compare(left.optInt("annualMonth", 0), right.optInt("annualMonth", 0));
            if (byMonth != 0) return byMonth;
            int byDay = Integer.compare(left.optInt("annualDay", 0), right.optInt("annualDay", 0));
            if (byDay != 0) return byDay;
        }
        int byHour = Integer.compare(sortHour(left), sortHour(right));
        if (byHour != 0) return byHour;
        int byMinute = Integer.compare(sortMinute(left), sortMinute(right));
        if (byMinute != 0) return byMinute;
        return left.optString("name", "").compareToIgnoreCase(right.optString("name", ""));
    }

    private int displayGroup(JSONObject reminder) {
        return isAnnualEvent(reminder) ? 1 : 0;
    }

    private boolean isAnnualEvent(JSONObject reminder) {
        return reminder.optBoolean("annualEvent", false);
    }

    private int sortHour(JSONObject reminder) {
        return reminder.optBoolean("useZmanim", false) ? 99 : reminder.optInt("hour", 0);
    }

    private int sortMinute(JSONObject reminder) {
        return reminder.optBoolean("useZmanim", false) ? 99 : reminder.optInt("minute", 0);
    }

    private void showEditor(JSONObject source, int index) {
        screen = "editor";
        JSONObject reminder = source == null ? newReminder() : copy(source);
        LinearLayout content = base();
        addHeader(content, source == null ? "תזכורת חדשה" : "עריכת תזכורת", "הנתונים יישמרו בטלפון עד שליחה לשעון");

        EditText name = input("שם", reminder.optString("name", ""));
        name.setSingleLine(true);
        name.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        content.addView(name, wideParams());

        EditText description = input("תיאור (אופציונלי)", reminder.optString("description", ""));
        description.setMinLines(2);
        description.setMaxLines(4);
        description.setSingleLine(false);
        description.setImeOptions(EditorInfo.IME_ACTION_DONE);
        content.addView(description, wideParams());

        LinearLayout state = card();
        Switch enabled = switchView("פעילה", reminder.optBoolean("enabled", true));
        Switch critical = switchView("חיונית", reminder.optBoolean("critical", false));
        state.addView(enabled);
        state.addView(critical);
        content.addView(state, wideParams());

        Spinner type = reminderTypeSpinner();
        type.setSelection(typeIndex(reminder));
        content.addView(labeled("סוג תזכורת", type), wideParams());

        Calendar calendar = Calendar.getInstance();
        long oneTimeAt = reminder.optLong("oneTimeAt", 0);
        if (oneTimeAt > 0) {
            calendar.setTimeInMillis(oneTimeAt);
        } else {
            calendar.set(Calendar.HOUR_OF_DAY, reminder.optInt("hour", calendar.get(Calendar.HOUR_OF_DAY)));
            calendar.set(Calendar.MINUTE, reminder.optInt("minute", calendar.get(Calendar.MINUTE)));
        }
        DatePicker date = new DatePicker(this);
        // Gregorian dates always progress from day on the left to year on the right,
        // independently of the selected app language.
        date.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        date.init(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH), null);
        TimePicker time = new TimePicker(this);
        time.setIs24HourView(true);
        time.setHour(calendar.get(Calendar.HOUR_OF_DAY));
        time.setMinute(calendar.get(Calendar.MINUTE));

        LinearLayout dateCard = card();
        dateCard.addView(text("תאריך", 15, TEXT));
        dateCard.addView(date);
        content.addView(dateCard, wideParams());

        LinearLayout timeCard = card();
        timeCard.addView(text("שעה", 15, TEXT));
        timeCard.addView(time);
        content.addView(timeCard, wideParams());

        LinearLayout daysCard = card();
        daysCard.addView(text("ימים קבועים", 15, TEXT));
        CheckBox[] days = new CheckBox[7];
        int[] dayValues = {1, 2, 3, 4, 5, 6, 7};
        String[] dayLabels = {"ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת"};
        JSONArray dayArray = reminder.optJSONArray("days");
        for (int i = 0; i < dayValues.length; i++) {
            days[i] = new CheckBox(this);
            days[i].setText(t(dayLabels[i]));
            days[i].setTextColor(TEXT);
            days[i].setTextDirection(PhoneUiText.isEnglish(this) ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
            days[i].setChecked(contains(dayArray, dayValues[i]));
            daysCard.addView(days[i]);
        }
        content.addView(daysCard, wideParams());

        LinearLayout zmanimCard = card();
        Switch useZmanim = switchView("לפי זמני הלכה", reminder.optBoolean("useZmanim", false));
        Spinner zman = prominentChoiceSpinner(ZMANIM_LABELS, "בחר זמן הלכה");
        zman.setSelection(zmanIndex(reminder.optString("zmanimKey", "CHATZOS")));
        NumberPicker offset = numberPicker(-180, 180, reminder.optInt("zmanimOffsetMinutes", 0));
        LinearLayout zmanimDetails = new LinearLayout(this);
        zmanimDetails.setOrientation(LinearLayout.VERTICAL);
        zmanimDetails.setGravity(Gravity.CENTER);
        zmanimDetails.addView(zman);
        zmanimDetails.addView(labeled("דקות לפני / אחרי", offset));
        zmanimCard.addView(useZmanim);
        zmanimCard.addView(zmanimDetails);
        content.addView(zmanimCard, wideParams());

        LinearLayout periodic = card();
        periodic.addView(text("מחזורית", 15, TEXT));
        boolean initialPeriodicHebrew = reminder.optBoolean("periodicHebrew", false);
        Switch periodicHebrew = switchView("תאריך עברי", initialPeriodicHebrew);
        Spinner unit = spinner(UNIT_LABELS);
        unit.setSelection(unitIndex(reminder.optString("periodicUnit", "days")));
        NumberPicker interval = numberPicker(1, 365, reminder.optInt("periodicInterval", 1));
        int initialPeriodicYear = reminder.optInt("periodicStartYear", initialPeriodicHebrew ? currentHebrewYear() : date.getYear());
        NumberPicker pDay = numberPicker(1, initialPeriodicHebrew ? 30 : 31, reminder.optInt("periodicStartDay", date.getDayOfMonth()));
        NumberPicker pMonth = numberPicker(1, initialPeriodicHebrew ? 13 : 12, reminder.optInt("periodicStartMonth", date.getMonth() + 1));
        NumberPicker pYear = numberPicker(Math.min(initialPeriodicYear, initialPeriodicHebrew ? currentHebrewYear() : 2024), Math.max(initialPeriodicYear + 10, initialPeriodicHebrew ? currentHebrewYear() + 10 : 2100), initialPeriodicYear);
        applyCalendarDisplay(pDay, pMonth, pYear, initialPeriodicHebrew);
        periodic.addView(periodicHebrew);
        periodic.addView(labeled("כל כמה", interval));
        periodic.addView(unit);
        periodic.addView(labeled("יום", pDay));
        periodic.addView(labeled("חודש", pMonth));
        periodic.addView(labeled("שנה", pYear));
        content.addView(periodic, wideParams());

        LinearLayout annual = card();
        annual.addView(text("אירוע שנתי", 15, TEXT));
        boolean initialAnnualHebrew = reminder.optBoolean("annualHebrew", false);
        Switch annualHebrew = switchView("תאריך עברי", initialAnnualHebrew);
        NumberPicker annualDay = numberPicker(1, initialAnnualHebrew ? 30 : 31, reminder.optInt("annualDay", date.getDayOfMonth()));
        NumberPicker annualMonth = numberPicker(1, initialAnnualHebrew ? 13 : 12, reminder.optInt("annualMonth", date.getMonth() + 1));
        applyCalendarDisplay(annualDay, annualMonth, null, initialAnnualHebrew);
        NumberPicker advance = numberPicker(0, 1000, reminder.optInt("annualAdvanceHours", 0));
        NumberPicker counter = numberPicker(1, 1000, reminder.optInt("annualCounter", 1));
        annual.addView(annualHebrew);
        annual.addView(labeled("יום", annualDay));
        annual.addView(labeled("חודש", annualMonth));
        annual.addView(labeled("שעות לפני", advance));
        annual.addView(labeled("מספר הבא", counter));
        content.addView(annual, wideParams());

        Runnable visibility = () -> {
            int selected = type.getSelectedItemPosition();
            dateCard.setVisibility(selected == 0 ? View.VISIBLE : View.GONE);
            timeCard.setVisibility(useZmanim.isChecked() ? View.GONE : View.VISIBLE);
            zmanimDetails.setVisibility(useZmanim.isChecked() ? View.VISIBLE : View.GONE);
            daysCard.setVisibility(selected == 1 ? View.VISIBLE : View.GONE);
            periodic.setVisibility(selected == 2 ? View.VISIBLE : View.GONE);
            annual.setVisibility(selected == 3 ? View.VISIBLE : View.GONE);
        };
        type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { visibility.run(); }
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        useZmanim.setOnCheckedChangeListener((buttonView, isChecked) -> visibility.run());
        periodicHebrew.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setDatePickersToToday(pDay, pMonth, pYear, isChecked);
            applyCalendarDisplay(pDay, pMonth, pYear, isChecked);
        });
        annualHebrew.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setDatePickersToToday(annualDay, annualMonth, null, isChecked);
            applyCalendarDisplay(annualDay, annualMonth, null, isChecked);
        });
        visibility.run();

        LinearLayout actions = row();
        Button save = button("שמירה", ACCENT);
        save.setOnClickListener(v -> {
            try {
                reminder.put("name", valueOrDefault(name.getText().toString(), t("תזכורת")));
                reminder.put("description", description.getText().toString().trim());
                reminder.put("enabled", enabled.isChecked());
                reminder.put("critical", critical.isChecked());
                reminder.put("useZmanim", useZmanim.isChecked());
                reminder.put("zmanimKey", ZMANIM_KEYS[zman.getSelectedItemPosition()]);
                reminder.put("zmanimOffsetMinutes", numberValue(offset));
                reminder.put("hour", time.getHour());
                reminder.put("minute", time.getMinute());
                reminder.put("days", selectedDays(days, dayValues));
                int selectedType = type.getSelectedItemPosition();
                Calendar selectedDate = Calendar.getInstance();
                selectedDate.set(date.getYear(), date.getMonth(), date.getDayOfMonth(), time.getHour(), time.getMinute(), 0);
                selectedDate.set(Calendar.MILLISECOND, 0);
                reminder.put("oneTimeAt", selectedType == 0 ? selectedDate.getTimeInMillis() : 0);
                reminder.put("periodic", selectedType == 2);
                reminder.put("annualEvent", selectedType == 3);
                reminder.put("periodicHebrew", periodicHebrew.isChecked());
                reminder.put("periodicInterval", numberValue(interval));
                reminder.put("periodicUnit", UNIT_VALUES[unit.getSelectedItemPosition()]);
                reminder.put("periodicStartDay", numberValue(pDay));
                reminder.put("periodicStartMonth", numberValue(pMonth));
                reminder.put("periodicStartYear", numberValue(pYear));
                reminder.put("annualHebrew", annualHebrew.isChecked());
                reminder.put("annualDay", numberValue(annualDay));
                reminder.put("annualMonth", numberValue(annualMonth));
                reminder.put("annualAdvanceHours", numberValue(advance));
                reminder.put("annualCounter", numberValue(counter));
                saveReminder(reminder, index);
                showMain();
            } catch (Exception exception) {
                Toast.makeText(this, t("לא הצלחתי לשמור"), Toast.LENGTH_LONG).show();
            }
        });
        Button cancel = button("ביטול", SOFT, TEXT);
        cancel.setOnClickListener(v -> showMain());
        actions.addView(save);
        actions.addView(cancel);
        content.addView(actions);
        setScroll(content);
    }

    private void showSettings() {
        screen = "settings";
        LinearLayout content = base();
        addHeader(content, "הגדרות", "גיבויים והגדרות כלליות");

        JSONObject root = LocalReminderDocument.root(this);
        JSONObject settings = root.optJSONObject("settings");
        if (settings == null) settings = new JSONObject();
        JSONObject finalSettings = settings;

        LinearLayout settingsCard = card();
        Switch quiet = switchView("זמני שקט פעילים", finalSettings.optBoolean("quietMinchaMaariv", false));
        NumberPicker blessing = numberPicker(1, 71, finalSettings.optInt("blessingReminderMinutes", 65));
        NumberPicker shemaOffset = numberPicker(0, 60, finalSettings.optInt("shemaOnTimeOffsetMinutes", 10));
        NumberPicker autoDelay = numberPicker(5, 600, finalSettings.optInt("autoSnoozeDelaySeconds", 30));
        NumberPicker autoMinutes = numberPicker(1, 240, finalSettings.optInt("autoSnoozeMinutes", 5));
        settingsCard.addView(quiet);
        settingsCard.addView(labeled("תזכורת ברכה בדקות", blessing));
        settingsCard.addView(labeled("קריאת שמע אחרי צאת הכוכבים", shemaOffset));
        settingsCard.addView(labeled("סגירה אוטומטית בשניות", autoDelay));
        settingsCard.addView(labeled("דחייה אוטומטית בדקות", autoMinutes));
        Button saveSettings = button("שמירת הגדרות", ACCENT);
        saveSettings.setOnClickListener(v -> {
            try {
                finalSettings.put("quietMinchaMaariv", quiet.isChecked());
                finalSettings.put("blessingReminderMinutes", numberValue(blessing));
                finalSettings.put("shemaOnTimeOffsetMinutes", numberValue(shemaOffset));
                finalSettings.put("autoSnoozeDelaySeconds", numberValue(autoDelay));
                finalSettings.put("autoSnoozeMinutes", numberValue(autoMinutes));
                root.put("settings", finalSettings);
                LocalReminderDocument.saveRoot(this, root);
                PendingPatchStore.updateSettings(this, root);
                Toast.makeText(this, t("נשמר בטלפון. שלח לשעון כדי לעדכן."), Toast.LENGTH_LONG).show();
            } catch (Exception ignored) {
            }
        });
        settingsCard.addView(saveSettings);
        content.addView(settingsCard, wideParams());

        addBackupSection(content);

        Button licenses = button("ⓘ  אודות ורישיונות", SOFT, TEXT);
        licenses.setOnClickListener(v -> showAboutAndLicenses());
        content.addView(licenses, wideParams());

        Button back = button("חזרה", SOFT, TEXT);
        back.setOnClickListener(v -> showMain());
        content.addView(back, wideParams());
        setScroll(content);
    }

    private void showAboutAndLicenses() {
        screen = "about_licenses";
        LinearLayout content = base();
        addHeader(content, "אודות ורישיונות", "רכיבי צד שלישי ב-Zmanio");

        LinearLayout intro = card();
        TextView explanation = text("אפליקציית הטלפון משתמשת ברכיבי צד שלישי. תודה ליוצרים ולתורמים שלהם.", 15, MUTED);
        explanation.setGravity(Gravity.CENTER);
        intro.addView(explanation);
        content.addView(intro, wideParams());

        addPhoneLicenseCard(content, "KosherJava Zmanim 2.5.0",
                "Eliyahu Hershfeld and contributors\nGNU Lesser General Public License 2.1",
                "licenses/lgpl-2.1.txt");
        addPhoneLicenseCard(content, "Google Play services for Wear OS 18.0.0",
                "Google Play services wearable APIs\nGoogle APIs Terms of Service",
                "licenses/google_play_services_notice.txt");

        Button back = button("חזרה להגדרות", SOFT, TEXT);
        back.setOnClickListener(v -> showSettings());
        content.addView(back, wideParams());
        setScroll(content);
    }

    private void addPhoneLicenseCard(LinearLayout content, String titleValue, String detailsValue, String assetPath) {
        LinearLayout licenseCard = card();
        TextView title = text(titleValue, 17, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        licenseCard.addView(title);
        TextView details = text(detailsValue, 13, MUTED);
        licenseCard.addView(details);
        Button view = button("הצגת נוסח הרישיון", SOFT, TEXT);
        view.setOnClickListener(v -> showPhoneLicenseText(titleValue, assetPath));
        licenseCard.addView(view);
        content.addView(licenseCard, wideParams());
    }

    private void showPhoneLicenseText(String titleValue, String assetPath) {
        screen = "license_text";
        LinearLayout content = base();
        addHeader(content, titleValue, "נוסח הרישיון וההודעות");
        LinearLayout licenseCard = card();
        TextView license = text(readAssetText(assetPath), 12, TEXT);
        license.setTextDirection(View.TEXT_DIRECTION_LTR);
        license.setGravity(Gravity.START);
        license.setTypeface(Typeface.MONOSPACE);
        licenseCard.addView(license);
        content.addView(licenseCard, wideParams());
        Button back = button("חזרה לרישיונות", SOFT, TEXT);
        back.setOnClickListener(v -> showAboutAndLicenses());
        content.addView(back, wideParams());
        setScroll(content);
    }

    private String readAssetText(String assetPath) {
        StringBuilder value = new StringBuilder();
        try (InputStream input = getAssets().open(assetPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) value.append(line).append('\n');
            return value.toString().trim();
        } catch (Exception exception) {
            return t("לא ניתן לטעון את נוסח הרישיון");
        }
    }

    private void addBackupSection(LinearLayout content) {
        LinearLayout section = card();
        section.addView(text("גיבויים", 18, TEXT));
        List<BackupStorage.BackupEntry> backups = BackupStorage.listBackups(this);
        if (backups.isEmpty()) {
            section.addView(text("אין קבצי ‎.zmbu‎ זמינים", 14, MUTED));
        } else {
            for (BackupStorage.BackupEntry backup : backups) {
                TextView info = text(backupInfoText(backup), 14, MUTED);
                section.addView(info);
                LinearLayout buttons = row();
                Button load = button("טעינה לעריכה", SOFT, TEXT);
                load.setOnClickListener(v -> loadBackup(backup));
                Button send = button("שלח לשעון", ACCENT);
                send.setOnClickListener(v -> sendRestore(backup));
                Button share = button("שיתוף", SOFT, TEXT);
                share.setOnClickListener(v -> shareBackup(backup));
                buttons.addView(load);
                buttons.addView(send);
                buttons.addView(share);
                section.addView(buttons);
            }
        }
        Button pick = button("בחר קובץ גיבוי", SOFT, TEXT);
        pick.setOnClickListener(v -> pickBackupFile());
        section.addView(pick, wideParams());
        content.addView(section, wideParams());
    }

    private void addLogSection(LinearLayout content) {
        LinearLayout section = card();
        section.addView(text("לוגים מהשעון", 18, TEXT));
        List<LogStorage.LogEntry> logs = LogStorage.listLogs(this);
        if (logs.isEmpty()) {
            section.addView(text("אין לוגים זמינים", 14, MUTED));
        } else {
            for (LogStorage.LogEntry log : logs) {
                section.addView(text(logInfoText(log), 14, MUTED));
                LinearLayout buttons = row();
                Button share = button("שיתוף", SOFT, TEXT);
                share.setOnClickListener(v -> shareLog(log));
                Button copy = button("העתקה", SOFT, TEXT);
                copy.setOnClickListener(v -> copyLog(log));
                buttons.addView(share);
                buttons.addView(copy);
                section.addView(buttons);
            }
        }
        Button pick = button("בחר קובץ לוג", SOFT, TEXT);
        pick.setOnClickListener(v -> pickLogFile());
        section.addView(pick, wideParams());
        content.addView(section, wideParams());
    }

    private void pickBackupFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_BACKUP);
        } catch (Exception exception) {
            Toast.makeText(this, t("לא הצלחתי לפתוח בוחר קבצים"), Toast.LENGTH_LONG).show();
        }
    }

    private void pickLogFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_LOG);
        } catch (Exception exception) {
            Toast.makeText(this, t("לא הצלחתי לפתוח בוחר קבצים"), Toast.LENGTH_LONG).show();
        }
    }

    private void requestSync() {
        Toast.makeText(this, t("מבקש סנכרון מהשעון..."), Toast.LENGTH_SHORT).show();
        WatchSyncRequester.request(this, new WatchSyncRequester.Callback() {
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(PhoneMainActivity.this, t("נשלחה בקשה. הנתונים יופיעו כשהשעון ישלח."), Toast.LENGTH_LONG).show();
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(PhoneMainActivity.this::showMain, 1800L);
                });
            }

            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(PhoneMainActivity.this, message, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void pushToWatch() {
        Toast.makeText(this, t("שולח לשעון..."), Toast.LENGTH_SHORT).show();
        WatchPatchSender.send(this, new WatchPatchSender.Callback() {
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(PhoneMainActivity.this, t("השינויים נשלחו לשעון. אשר בשעון."), Toast.LENGTH_LONG).show();
                    showMain();
                });
            }

            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(PhoneMainActivity.this, message, Toast.LENGTH_LONG).show());
            }
        });
    }

    private JSONArray reminders() {
        JSONArray array = LocalReminderDocument.reminders(this);
        return array == null ? new JSONArray() : array;
    }

    private JSONArray smartAlarms() {
        JSONArray array = LocalReminderDocument.smartAlarms(this);
        return array == null ? new JSONArray() : array;
    }

    private JSONObject newSmartAlarm() {
        JSONObject alarm = new JSONObject();
        int highestId = 0;
        JSONArray existing = smartAlarms();
        for (int index = 0; index < existing.length(); index++) {
            JSONObject item = existing.optJSONObject(index);
            if (item != null) highestId = Math.max(highestId, item.optInt("id", 0));
        }
        try {
            alarm.put("id", highestId + 1).put("enabled", true).put("hour", 6).put("minute", 30)
                    .put("daysMask", 126).put("windowMinutes", 30).put("snoozeMinutes", 5).put("snoozeCount", 3)
                    .put("vibrationEnabled", true).put("vibrationStyle", "normal").put("vibrationStrength", 6)
                    .put("soundEnabled", true).put("soundVolumePercent", 80).put("soundUri", "")
                    .put("alertDurationSeconds", 30).put("backgroundStyle", "morning")
                    .put("dismissMethod", "tap").put("dismissHoldSeconds", 3);
            alarm.put("mathDifficulty", 1).put("memoryDifficulty", 1).put("shakeCount", 12)
                    .put("stepCount", 20).put("alternatingTapCount", 10)
                    .put("wakeCheckEnabled", false).put("wakeCheckDelayMinutes", 5);
        } catch (Exception ignored) { }
        return alarm;
    }

    private void saveSmartAlarm(JSONObject alarm, int index) throws Exception {
        JSONObject root = LocalReminderDocument.root(this);
        JSONArray alarms = root.optJSONArray("smartAlarms");
        if (alarms == null) alarms = new JSONArray();
        if (index >= 0 && index < alarms.length()) alarms.put(index, alarm); else alarms.put(alarm);
        root.put("smartAlarms", alarms);
        LocalReminderDocument.saveRoot(this, root);
        PendingPatchStore.replaceSmartAlarms(this, alarms);
    }

    private void deleteSmartAlarm(int index) {
        try {
            JSONObject root = LocalReminderDocument.root(this);
            JSONArray source = root.optJSONArray("smartAlarms");
            JSONArray next = new JSONArray();
            for (int i = 0; source != null && i < source.length(); i++) if (i != index) next.put(source.opt(i));
            root.put("smartAlarms", next); LocalReminderDocument.saveRoot(this, root);
            PendingPatchStore.replaceSmartAlarms(this, next); showSmartAlarms();
        } catch (Exception ignored) { }
    }

    private void showSmartAlarmActions(JSONObject alarm, int index) {
        String title = String.format(Locale.US, "%02d:%02d", alarm.optInt("hour", 6), alarm.optInt("minute", 30));
        new AlertDialog.Builder(this).setTitle(title)
                .setItems(PhoneUiText.t(this, new String[]{"עריכה", "מחיקה"}), (dialog, which) -> {
                    if (which == 0) showSmartAlarmEditor(alarm, index);
                    if (which == 1) deleteSmartAlarm(index);
                }).show();
    }

    private String smartAlarmDays(int mask) {
        String[] labels = PhoneUiText.isEnglish(this)
                ? new String[]{"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"}
                : new String[]{"א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳"};
        ArrayList<String> selected = new ArrayList<>();
        for (int day = 0; day < 7; day++) if ((mask & (1 << (Calendar.SUNDAY + day))) != 0) selected.add(labels[day]);
        return android.text.TextUtils.join(" · ", selected);
    }

    private void saveReminder(JSONObject reminder, int index) throws Exception {
        JSONObject root = LocalReminderDocument.root(this);
        JSONArray reminders = root.optJSONArray("reminders");
        if (reminders == null) reminders = new JSONArray();
        if (index >= 0 && index < reminders.length()) {
            reminders.put(index, reminder);
        } else {
            reminders.put(reminder);
        }
        root.put("reminders", reminders);
        LocalReminderDocument.saveRoot(this, root);
        PendingPatchStore.upsertReminder(this, reminder);
    }

    private void deleteReminder(JSONObject reminder) {
        try {
            JSONObject root = LocalReminderDocument.root(this);
            JSONArray source = root.optJSONArray("reminders");
            JSONArray next = new JSONArray();
            String id = reminder.optString("id");
            for (int i = 0; source != null && i < source.length(); i++) {
                JSONObject item = source.optJSONObject(i);
                if (item != null && !id.equals(item.optString("id"))) {
                    next.put(item);
                }
            }
            root.put("reminders", next);
            LocalReminderDocument.saveRoot(this, root);
            PendingPatchStore.deleteReminder(this, id);
            showMain();
        } catch (Exception ignored) {
        }
    }

    private JSONObject newReminder() {
        JSONObject json = new JSONObject();
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 1);
        try {
            json.put("id", UUID.randomUUID().toString())
                    .put("name", "")
                    .put("description", "")
                    .put("hour", calendar.get(Calendar.HOUR_OF_DAY))
                    .put("minute", calendar.get(Calendar.MINUTE))
                    .put("days", new JSONArray())
                    .put("enabled", true)
                    .put("oneTimeAt", calendar.getTimeInMillis())
                    .put("useZmanim", false)
                    .put("zmanimKey", "CHATZOS")
                    .put("zmanimOffsetMinutes", 0)
                    .put("critical", false)
                    .put("periodic", false)
                    .put("periodicHebrew", false)
                    .put("periodicDayOfWeek", Calendar.SUNDAY)
                    .put("periodicInterval", 1)
                    .put("periodicUnit", "days")
                    .put("periodicStartYear", calendar.get(Calendar.YEAR))
                    .put("periodicStartMonth", calendar.get(Calendar.MONTH) + 1)
                    .put("periodicStartDay", calendar.get(Calendar.DAY_OF_MONTH))
                    .put("annualEvent", false)
                    .put("annualHebrew", false)
                    .put("annualMonth", calendar.get(Calendar.MONTH) + 1)
                    .put("annualDay", calendar.get(Calendar.DAY_OF_MONTH))
                    .put("annualAdvanceHours", 0)
                    .put("annualCounter", 1)
                    .put("annualCounterYear", 0);
        } catch (Exception ignored) {
        }
        return json;
    }

    private void showReminderActions(JSONObject reminder) {
        new AlertDialog.Builder(this)
                .setTitle(reminder.optString("name", t("תזכורת")))
                .setItems(PhoneUiText.t(this, new String[]{"עריכה", "מחיקה"}), (dialog, which) -> {
                    if (which == 0) showEditor(reminder, indexOfReminder(reminder.optString("id")));
                    if (which == 1) deleteReminder(reminder);
                })
                .show();
    }

    private int indexOfReminder(String id) {
        JSONArray reminders = reminders();
        for (int i = 0; i < reminders.length(); i++) {
            if (id.equals(reminders.optJSONObject(i).optString("id"))) return i;
        }
        return -1;
    }

    private int typeIndex(JSONObject reminder) {
        if (reminder.optBoolean("annualEvent", false)) return 3;
        if (reminder.optBoolean("periodic", false)) return 2;
        if (reminder.optLong("oneTimeAt", 0) > 0) return 0;
        return 1;
    }

    private String details(JSONObject reminder) {
        if (reminder.optBoolean("critical", false)) return typeLabel(reminder) + " | " + t("חיונית");
        return typeLabel(reminder);
    }

    private String typeLabel(JSONObject reminder) {
        int type = typeIndex(reminder);
        if (type == 0) return t("חד פעמית");
        if (type == 2) {
            int interval = reminder.optInt("periodicInterval", 1);
            return PhoneUiText.isEnglish(this)
                    ? "Every " + interval + " " + t(UNIT_LABELS[unitIndex(reminder.optString("periodicUnit", "days"))])
                    : "מחזורית: כל " + interval + " " + UNIT_LABELS[unitIndex(reminder.optString("periodicUnit", "days"))];
        }
        if (type == 3) return t("אירוע שנתי");
        return t("קבועה");
    }

    private String timeTitle(JSONObject reminder) {
        if (reminder.optBoolean("useZmanim", false)) {
            return t(ZMANIM_LABELS[zmanIndex(reminder.optString("zmanimKey", "CHATZOS"))]) + " " + offsetText(reminder.optInt("zmanimOffsetMinutes", 0));
        }
        return String.format(Locale.US, "%02d:%02d", reminder.optInt("hour", 9), reminder.optInt("minute", 0));
    }

    private String offsetText(int offset) {
        if (offset == 0) return "";
        return offset > 0 ? "+" + offset : String.valueOf(offset);
    }

    private String statusLine() {
        long updated = LocalReminderDocument.updatedAt(this);
        if (updated == 0) return t("טרם בוצע סנכרון.");
        if (PhoneUiText.isEnglish(this)) {
            String suffix = PendingPatchStore.hasPending(this) ? " | Pending changes: " + PendingPatchStore.count(this) : "";
            return "Updated: " + new SimpleDateFormat("MMM d · HH:mm", Locale.US).format(new Date(updated)) + suffix;
        }
        String suffix = PendingPatchStore.hasPending(this) ? " | שינויים ממתינים: " + PendingPatchStore.count(this) : "";
        return "עודכן: " + new SimpleDateFormat("dd/MM HH:mm", Locale.US).format(new Date(updated)) + suffix;
    }

    private JSONArray selectedDays(CheckBox[] boxes, int[] values) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < boxes.length; i++) {
            if (boxes[i].isChecked()) array.put(values[i]);
        }
        return array;
    }

    private boolean contains(JSONArray array, int value) {
        for (int i = 0; array != null && i < array.length(); i++) {
            if (array.optInt(i) == value) return true;
        }
        return false;
    }

    private int zmanIndex(String key) {
        for (int i = 0; i < ZMANIM_KEYS.length; i++) if (ZMANIM_KEYS[i].equals(key)) return i;
        return 4;
    }

    private int unitIndex(String unit) {
        for (int i = 0; i < UNIT_VALUES.length; i++) if (UNIT_VALUES[i].equals(unit)) return i;
        return 1;
    }

    private JSONObject copy(JSONObject object) {
        try {
            return new JSONObject(object.toString());
        } catch (Exception ignored) {
            return newReminder();
        }
    }

    private String valueOrDefault(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private LinearLayout base() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(40));
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF091C2B, 0xFF102A3B, 0xFF091C2B});
        root.setBackground(background);
        return root;
    }

    private void addHeader(LinearLayout content, String title, String subtitle) {
        TextView eyebrow = text("ZMANIO  •  COMPANION", 11, COPPER);
        eyebrow.setLetterSpacing(0.12f);
        eyebrow.setPadding(0, 0, 0, dp(7));
        content.addView(eyebrow);
        TextView titleView = text(title, 30, TEXT);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(titleView);
        TextView sub = text(subtitle, 14, MUTED);
        sub.setPadding(0, dp(5), 0, dp(12));
        content.addView(sub);
        View accent = new View(this);
        accent.setBackgroundColor(COPPER);
        LinearLayout.LayoutParams accentParams = new LinearLayout.LayoutParams(dp(48), dp(3));
        accentParams.setMargins(0, 0, 0, dp(18));
        accent.setLayoutParams(accentParams);
        content.addView(accent);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        card.setBackground(round(SURFACE, dp(24), BORDER));
        card.setElevation(dp(3));
        return card;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(3), 0, dp(7));
        row.setLayoutParams(wideParams());
        return row;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(t(value));
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setTextDirection(PhoneUiText.isEnglish(this) ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
        return view;
    }

    private EditText input(String hint, String value) {
        EditText edit = new EditText(this);
        edit.setHint(t(hint));
        edit.setText(value);
        edit.setTextSize(16);
        edit.setTextColor(TEXT);
        edit.setHintTextColor(MUTED);
        edit.setGravity(Gravity.CENTER);
        edit.setTextDirection(PhoneUiText.isEnglish(this) ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
        edit.setBackground(round(SURFACE_2, dp(18), BORDER));
        edit.setPadding(dp(18), dp(12), dp(18), dp(12));
        return edit;
    }

    private Switch switchView(String label, boolean checked) {
        Switch sw = new Switch(this);
        sw.setText(t(label));
        sw.setChecked(checked);
        sw.setTextSize(15);
        sw.setTextColor(TEXT);
        sw.setTextDirection(PhoneUiText.isEnglish(this) ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
        return sw;
    }

    private Spinner spinner(String[] labels) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, PhoneUiText.t(this, labels));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setBackground(round(SURFACE_2, dp(16), BORDER));
        spinner.setPadding(dp(10), dp(6), dp(10), dp(6));
        return spinner;
    }

    private Spinner reminderTypeSpinner() {
        return prominentChoiceSpinner(TYPE_LABELS, "בחר סוג תזכורת");
    }

    private Spinner prominentChoiceSpinner(String[] labels, String prompt) {
        String[] translatedLabels = PhoneUiText.t(this, labels);
        Spinner spinner = new Spinner(this) {
            @Override
            public boolean performClick() {
                ArrayAdapter<String> choices = new ArrayAdapter<String>(PhoneMainActivity.this,
                        android.R.layout.simple_list_item_1, translatedLabels) {
                    @Override
                    public View getView(int position, View convertView, android.view.ViewGroup parent) {
                        TextView view = (TextView) super.getView(position, convertView, parent);
                        styleProminentChoice(view, false);
                        return view;
                    }
                };
                new AlertDialog.Builder(PhoneMainActivity.this)
                        .setTitle(t(prompt))
                        .setAdapter(choices, (dialog, which) -> setSelection(which))
                        .setNegativeButton(t("ביטול"), null)
                        .show();
                return true;
            }
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, translatedLabels) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setText(translatedLabels[position] + "   ▼");
                styleProminentChoice(view, true);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setText(translatedLabels[position]);
                styleProminentChoice(view, false);
                return view;
            }
        };
        spinner.setAdapter(adapter);
        spinner.setBackground(round(SURFACE_2, dp(18), COPPER));
        spinner.setMinimumHeight(dp(56));
        spinner.setPadding(dp(16), dp(6), dp(16), dp(6));
        return spinner;
    }

    private void styleProminentChoice(TextView view, boolean selected) {
        view.setGravity(Gravity.CENTER);
        view.setTextDirection(PhoneUiText.isEnglish(this) ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
        view.setTextColor(TEXT);
        view.setTextSize(selected ? 17 : 16);
        view.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        view.setPadding(dp(18), dp(selected ? 12 : 16), dp(18), dp(selected ? 12 : 16));
        if (!selected) {
            view.setBackgroundColor(SURFACE_2);
        }
    }

    private void applyCalendarDisplay(NumberPicker day, NumberPicker month, NumberPicker year, boolean hebrew) {
        configureNumberPicker(day, 1, hebrew ? 30 : 31, Math.min(numberValue(day), hebrew ? 30 : 31), hebrew ? hebrewDayLabels() : null);
        configureNumberPicker(month, 1, hebrew ? 13 : 12, Math.min(numberValue(month), hebrew ? 13 : 12), hebrew ? hebrewMonthLabels() : null);
        if (year != null) {
            int value = numberValue(year);
            int min = hebrew ? Math.min(value, currentHebrewYear()) : Math.min(value, 2024);
            int max = hebrew ? Math.max(value + 10, currentHebrewYear() + 10) : Math.max(value + 10, 2100);
            configureNumberPicker(year, min, max, value, null);
        }
    }

    private void setDatePickersToToday(NumberPicker day, NumberPicker month, NumberPicker year, boolean hebrew) {
        if (hebrew) {
            JewishDate jewishDate = new JewishDate(Calendar.getInstance());
            configureNumberPicker(day, 1, 30, jewishDate.getJewishDayOfMonth(), hebrewDayLabels());
            configureNumberPicker(month, 1, 13, jewishDate.getJewishMonth(), hebrewMonthLabels());
            if (year != null) {
                int jewishYear = jewishDate.getJewishYear();
                configureNumberPicker(year, jewishYear, jewishYear + 10, jewishYear, null);
            }
            return;
        }
        Calendar now = Calendar.getInstance();
        configureNumberPicker(day, 1, 31, now.get(Calendar.DAY_OF_MONTH), null);
        configureNumberPicker(month, 1, 12, now.get(Calendar.MONTH) + 1, null);
        if (year != null) {
            int gregorianYear = now.get(Calendar.YEAR);
            configureNumberPicker(year, Math.min(2024, gregorianYear), gregorianYear + 10, gregorianYear, null);
        }
    }

    private void configureNumberPicker(NumberPicker picker, int min, int max, int value, String[] displayedValues) {
        picker.setDisplayedValues(null);
        if (min < picker.getMinValue()) {
            picker.setMinValue(min);
        }
        if (max > picker.getMaxValue()) {
            picker.setMaxValue(max);
        }
        int clamped = Math.max(min, Math.min(max, value));
        picker.setValue(clamped);
        if (min > picker.getMinValue()) {
            picker.setMinValue(min);
        }
        if (max < picker.getMaxValue()) {
            picker.setMaxValue(max);
        }
        picker.setValue(clamped);
        if (displayedValues != null) {
            picker.setDisplayedValues(displayedValues);
            picker.setFormatter(null);
        }
        picker.setTag(Integer.valueOf(min));
    }

    private int currentHebrewYear() {
        return new JewishDate(Calendar.getInstance()).getJewishYear();
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

    private String hebrewDayLabel(int day) {
        if (PhoneUiText.isEnglish(this)) return String.valueOf(day);
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
        switch (month) {
            case JewishDate.NISSAN:
                return PhoneUiText.isEnglish(this) ? "Nissan" : "ניסן";
            case JewishDate.IYAR:
                return PhoneUiText.isEnglish(this) ? "Iyar" : "אייר";
            case JewishDate.SIVAN:
                return PhoneUiText.isEnglish(this) ? "Sivan" : "סיוון";
            case JewishDate.TAMMUZ:
                return PhoneUiText.isEnglish(this) ? "Tammuz" : "תמוז";
            case JewishDate.AV:
                return PhoneUiText.isEnglish(this) ? "Av" : "אב";
            case JewishDate.ELUL:
                return PhoneUiText.isEnglish(this) ? "Elul" : "אלול";
            case JewishDate.TISHREI:
                return PhoneUiText.isEnglish(this) ? "Tishrei" : "תשרי";
            case JewishDate.CHESHVAN:
                return PhoneUiText.isEnglish(this) ? "Cheshvan" : "חשוון";
            case JewishDate.KISLEV:
                return PhoneUiText.isEnglish(this) ? "Kislev" : "כסלו";
            case JewishDate.TEVES:
                return PhoneUiText.isEnglish(this) ? "Tevet" : "טבת";
            case JewishDate.SHEVAT:
                return PhoneUiText.isEnglish(this) ? "Shevat" : "שבט";
            case JewishDate.ADAR:
                return PhoneUiText.isEnglish(this) ? "Adar" : "אדר";
            case JewishDate.ADAR_II:
                return PhoneUiText.isEnglish(this) ? "Adar II" : "אדר ב׳";
            default:
                return String.valueOf(month);
        }
    }

    private NumberPicker numberPicker(int min, int max, int value) {
        NumberPicker picker = new NumberPicker(this);
        picker.setLayoutParams(new LinearLayout.LayoutParams(
                dp(160), dp(120)));
        picker.setTag(Integer.valueOf(min));
        int clamped = Math.max(min, Math.min(max, value));
        if (min < 0) {
            picker.setMinValue(0);
            picker.setMaxValue(max - min);
            String[] values = new String[max - min + 1];
            for (int i = 0; i < values.length; i++) {
                values[i] = String.valueOf(min + i);
            }
            picker.setDisplayedValues(values);
            picker.setValue(clamped - min);
        } else {
            picker.setMinValue(min);
            picker.setMaxValue(max);
            picker.setValue(clamped);
        }
        picker.setWrapSelectorWheel(true);
        return picker;
    }

    private int numberValue(NumberPicker picker) {
        Object tag = picker.getTag();
        if (tag instanceof Integer && (Integer) tag < 0) {
            return (Integer) tag + picker.getValue();
        }
        return picker.getValue();
    }

    private LinearLayout labeled(String label, View view) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.addView(text(label, 13, MUTED));
        layout.addView(view);
        return layout;
    }

    private Button button(String label, int color) {
        return button(label, color, Color.WHITE);
    }

    private Button button(String label, int color, int textColor) {
        Button button = new Button(this);
        button.setText(t(label));
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(textColor);
        button.setBackground(round(color, dp(18), color == SOFT ? BORDER : 0));
        button.setSingleLine(false);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(52), 1f);
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        button.setLayoutParams(params);
        return button;
    }

    private LinearLayout.LayoutParams wideParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(7), 0, dp(7));
        return params;
    }

    private GradientDrawable round(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != 0) drawable.setStroke(2, strokeColor);
        return drawable;
    }

    private void setScroll(LinearLayout content) {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        scroll.addView(content);
        setContentView(scroll);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String t(String value) {
        return PhoneUiText.t(this, value);
    }

    private String backupInfoText(BackupStorage.BackupEntry backup) {
        return backup.name + "\n" + new SimpleDateFormat("dd/MM HH:mm", Locale.US).format(new Date(backup.modifiedAt));
    }

    private String logInfoText(LogStorage.LogEntry log) {
        return log.name + "\n" + new SimpleDateFormat("dd/MM HH:mm", Locale.US).format(new Date(log.modifiedAt));
    }

    private void loadBackup(BackupStorage.BackupEntry backup) {
        try {
            LocalReminderDocument.save(this, new String(BackupStorage.readBackup(this, backup), java.nio.charset.StandardCharsets.UTF_8));
            Toast.makeText(this, t("הגיבוי נטען לעריכה"), Toast.LENGTH_SHORT).show();
            showMain();
        } catch (Exception exception) {
            Toast.makeText(this, t("לא הצלחתי לטעון גיבוי"), Toast.LENGTH_LONG).show();
        }
    }

    private void importPickedBackup(Uri uri) {
        importBackupUri(uri, false);
    }

    private void handleExternalBackupIntent(Intent intent) {
        if (intent == null) return;
        Uri uri = null;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            uri = intent.getData();
        } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            } else {
                //noinspection deprecation
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            }
            if (uri == null && intent.getClipData() != null && intent.getClipData().getItemCount() > 0) {
                uri = intent.getClipData().getItemAt(0).getUri();
            }
        }
        intent.setAction(null);
        if (uri != null) importBackupUri(uri, true);
    }

    private void importBackupUri(Uri uri, boolean requireBackupExtension) {
        try {
            String displayName = displayName(uri, uri.getLastPathSegment() == null ? "" : uri.getLastPathSegment());
            if (requireBackupExtension && !hasBackupExtension(displayName)) {
                Toast.makeText(this, t("הקובץ שנבחר אינו קובץ גיבוי של Zmanio"), Toast.LENGTH_LONG).show();
                return;
            }
            String fileName = BackupStorage.importBackup(this, uri, displayName);
            Toast.makeText(this, PhoneUiText.isEnglish(this) ? "Backup added: " + fileName : "הגיבוי נוסף לרשימה: " + fileName, Toast.LENGTH_LONG).show();
            showSettings();
        } catch (Exception exception) {
            Toast.makeText(this, t("לא הצלחתי להוסיף את קובץ הגיבוי"), Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasBackupExtension(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.US);
        return lower.endsWith(".zmbu") || lower.endsWith(".wrbu")
                || lower.endsWith(".zmbu.txt") || lower.endsWith(".wrbu.txt");
    }

    private void shareBackup(BackupStorage.BackupEntry backup) {
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType(BackupStorage.MIME_TYPE)
                .putExtra(Intent.EXTRA_STREAM, backup.uri)
                .putExtra(Intent.EXTRA_SUBJECT, backup.name)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(intent, t("שיתוף גיבוי")));
        } catch (Exception exception) {
            Toast.makeText(this, t("אין אפליקציה זמינה לשיתוף"), Toast.LENGTH_LONG).show();
        }
    }

    private void shareLog(LogStorage.LogEntry log) {
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType(LogStorage.MIME_TYPE)
                .putExtra(Intent.EXTRA_STREAM, log.uri)
                .putExtra(Intent.EXTRA_SUBJECT, log.name)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(intent, t("שיתוף לוג")));
        } catch (Exception exception) {
            Toast.makeText(this, t("אין אפליקציה זמינה לשיתוף"), Toast.LENGTH_LONG).show();
        }
    }

    private void copyLog(LogStorage.LogEntry log) {
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText(log.name, LogStorage.readLog(this, log)));
                Toast.makeText(this, t("הלוג הועתק"), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception exception) {
            Toast.makeText(this, t("לא הצלחתי להעתיק את הלוג"), Toast.LENGTH_LONG).show();
        }
    }

    private void showPickedLog(Uri uri) {
        LogStorage.LogEntry log = new LogStorage.LogEntry(displayName(uri, "WatchReminder log"), uri, System.currentTimeMillis());
        new AlertDialog.Builder(this)
                .setTitle(t("קובץ לוג"))
                .setMessage(log.name)
                .setItems(PhoneUiText.t(this, new String[]{"שיתוף", "העתקה"}), (dialog, which) -> {
                    if (which == 0) {
                        shareLog(log);
                    } else {
                        copyLog(log);
                    }
                })
                .show();
    }

    private String displayName(Uri uri, String fallback) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
                if (name != null && !name.trim().isEmpty()) {
                    return name;
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private void sendRestore(BackupStorage.BackupEntry backup) {
        Toast.makeText(this, t("שולח לשעון..."), Toast.LENGTH_SHORT).show();
        WatchRestoreSender.sendBackup(this, backup, new WatchRestoreSender.Callback() {
            public void onSuccess() {
                runOnUiThread(() -> Toast.makeText(PhoneMainActivity.this, t("נשלח לשעון. אשר בשעון."), Toast.LENGTH_LONG).show());
            }
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(PhoneMainActivity.this, message, Toast.LENGTH_LONG).show());
            }
        });
    }

    private static class DisplayReminder {
        final JSONObject reminder;
        final int index;

        DisplayReminder(JSONObject reminder, int index) {
            this.reminder = reminder;
            this.index = index;
        }
    }
}
