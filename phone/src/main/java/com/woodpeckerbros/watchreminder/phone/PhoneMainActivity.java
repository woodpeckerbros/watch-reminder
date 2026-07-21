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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PhoneMainActivity extends Activity {
    private static final int REQUEST_PICK_BACKUP = 201;
    private static final int REQUEST_PICK_LOG = 202;
    private static final int BG = 0xFFF4F7F5;
    private static final int SURFACE = 0xFFFFFFFF;
    private static final int TEXT = 0xFF101615;
    private static final int MUTED = 0xFF637069;
    private static final int ACCENT = 0xFF136F45;
    private static final int SOFT = 0xFFE7EFEA;
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
        addHeader(content, "Watch Reminder", "ניהול תזכורות שעון");

        LinearLayout actions = row();
        Button sync = button("סנכרון מהשעון", ACCENT);
        sync.setOnClickListener(v -> requestSync());
        Button push = button("שליחה לשעון", 0xFF234D3C);
        push.setOnClickListener(v -> pushToWatch());
        actions.addView(sync);
        actions.addView(push);
        content.addView(actions);

        LinearLayout actions2 = row();
        Button add = button("הוספה", ACCENT);
        add.setOnClickListener(v -> showEditor(null, -1));
        Button settings = button("הגדרות", SOFT, TEXT);
        settings.setOnClickListener(v -> showSettings());
        actions2.addView(add);
        actions2.addView(settings);
        content.addView(actions2);

        TextView status = text(statusLine(), 13, MUTED);
        status.setPadding(0, 6, 0, 12);
        content.addView(status);

        JSONArray reminders = reminders();
        if (reminders.length() == 0) {
            TextView empty = text("אין תזכורות בטלפון. לחץ סנכרון מהשעון.", 16, MUTED);
            empty.setPadding(0, 50, 0, 0);
            content.addView(empty);
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
                TextView time = text(timeTitle(reminder), 21, reminder.optBoolean("enabled", true) ? ACCENT : MUTED);
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

        Spinner type = spinner(TYPE_LABELS);
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
            days[i].setText(dayLabels[i]);
            days[i].setTextColor(TEXT);
            days[i].setTextDirection(View.TEXT_DIRECTION_RTL);
            days[i].setChecked(contains(dayArray, dayValues[i]));
            daysCard.addView(days[i]);
        }
        content.addView(daysCard, wideParams());

        LinearLayout zmanimCard = card();
        Switch useZmanim = switchView("לפי זמני הלכה", reminder.optBoolean("useZmanim", false));
        Spinner zman = spinner(ZMANIM_LABELS);
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
                reminder.put("name", valueOrDefault(name.getText().toString(), "תזכורת"));
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
                Toast.makeText(this, "לא הצלחתי לשמור", Toast.LENGTH_LONG).show();
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
        addHeader(content, "הגדרות", "גיבויים, לוגים והגדרות כלליות");

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
                Toast.makeText(this, "נשמר בטלפון. שלח לשעון כדי לעדכן.", Toast.LENGTH_LONG).show();
            } catch (Exception ignored) {
            }
        });
        settingsCard.addView(saveSettings);
        content.addView(settingsCard, wideParams());

        addBackupSection(content);
        addLogSection(content);

        Button back = button("חזרה", SOFT, TEXT);
        back.setOnClickListener(v -> showMain());
        content.addView(back, wideParams());
        setScroll(content);
    }

    private void addBackupSection(LinearLayout content) {
        LinearLayout section = card();
        section.addView(text("גיבויים", 18, TEXT));
        List<BackupStorage.BackupEntry> backups = BackupStorage.listBackups(this);
        if (backups.isEmpty()) {
            section.addView(text("אין קבצי ‎.wrbu‎ זמינים", 14, MUTED));
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
            Toast.makeText(this, "לא הצלחתי לפתוח בוחר קבצים", Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, "לא הצלחתי לפתוח בוחר קבצים", Toast.LENGTH_LONG).show();
        }
    }

    private void requestSync() {
        Toast.makeText(this, "מבקש סנכרון מהשעון...", Toast.LENGTH_SHORT).show();
        WatchSyncRequester.request(this, new WatchSyncRequester.Callback() {
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(PhoneMainActivity.this, "נשלחה בקשה. הנתונים יופיעו כשהשעון ישלח.", Toast.LENGTH_LONG).show();
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(PhoneMainActivity.this::showMain, 1800L);
                });
            }

            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(PhoneMainActivity.this, message, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void pushToWatch() {
        Toast.makeText(this, "שולח לשעון...", Toast.LENGTH_SHORT).show();
        WatchPatchSender.send(this, new WatchPatchSender.Callback() {
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(PhoneMainActivity.this, "השינויים נשלחו לשעון. אשר בשעון.", Toast.LENGTH_LONG).show();
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
                .setTitle(reminder.optString("name", "תזכורת"))
                .setItems(new String[]{"עריכה", "מחיקה"}, (dialog, which) -> {
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
        if (reminder.optBoolean("critical", false)) return typeLabel(reminder) + " | חיונית";
        return typeLabel(reminder);
    }

    private String typeLabel(JSONObject reminder) {
        int type = typeIndex(reminder);
        if (type == 0) return "חד פעמית";
        if (type == 2) return "מחזורית: כל " + reminder.optInt("periodicInterval", 1) + " " + UNIT_LABELS[unitIndex(reminder.optString("periodicUnit", "days"))];
        if (type == 3) return "אירוע שנתי";
        return "קבועה";
    }

    private String timeTitle(JSONObject reminder) {
        if (reminder.optBoolean("useZmanim", false)) {
            return ZMANIM_LABELS[zmanIndex(reminder.optString("zmanimKey", "CHATZOS"))] + " " + offsetText(reminder.optInt("zmanimOffsetMinutes", 0));
        }
        return String.format(Locale.US, "%02d:%02d", reminder.optInt("hour", 9), reminder.optInt("minute", 0));
    }

    private String offsetText(int offset) {
        if (offset == 0) return "";
        return offset > 0 ? "+" + offset : String.valueOf(offset);
    }

    private String statusLine() {
        long updated = LocalReminderDocument.updatedAt(this);
        if (updated == 0) return "טרם בוצע סנכרון.";
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
        root.setPadding(28, 28, 28, 36);
        root.setBackgroundColor(BG);
        return root;
    }

    private void addHeader(LinearLayout content, String title, String subtitle) {
        TextView titleView = text(title, 28, TEXT);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(titleView);
        TextView sub = text(subtitle, 14, MUTED);
        sub.setPadding(0, 4, 0, 18);
        content.addView(sub);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(24, 20, 24, 20);
        card.setBackground(round(SURFACE, 22, 0xFFE0E7E2));
        return card;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, 4, 0, 8);
        row.setLayoutParams(wideParams());
        return row;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setTextDirection(View.TEXT_DIRECTION_RTL);
        return view;
    }

    private EditText input(String hint, String value) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setText(value);
        edit.setTextSize(16);
        edit.setTextColor(TEXT);
        edit.setHintTextColor(MUTED);
        edit.setGravity(Gravity.CENTER);
        edit.setTextDirection(View.TEXT_DIRECTION_RTL);
        edit.setBackground(round(SURFACE, 18, 0xFFDCE5DF));
        edit.setPadding(24, 8, 24, 8);
        return edit;
    }

    private Switch switchView(String label, boolean checked) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setChecked(checked);
        sw.setTextSize(15);
        sw.setTextColor(TEXT);
        sw.setTextDirection(View.TEXT_DIRECTION_RTL);
        return sw;
    }

    private Spinner spinner(String[] labels) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return spinner;
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

    private NumberPicker numberPicker(int min, int max, int value) {
        NumberPicker picker = new NumberPicker(this);
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
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(textColor);
        button.setBackground(round(color, 18, 0));
        button.setSingleLine(false);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(8, 0, 8, 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, 62, 1f);
        params.setMargins(6, 6, 6, 6);
        button.setLayoutParams(params);
        return button;
    }

    private LinearLayout.LayoutParams wideParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 8, 0, 8);
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
        scroll.addView(content);
        setContentView(scroll);
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
            Toast.makeText(this, "הגיבוי נטען לעריכה", Toast.LENGTH_SHORT).show();
            showMain();
        } catch (Exception exception) {
            Toast.makeText(this, "לא הצלחתי לטעון גיבוי", Toast.LENGTH_LONG).show();
        }
    }

    private void importPickedBackup(Uri uri) {
        try {
            String fileName = BackupStorage.importBackup(this, uri, displayName(uri, ""));
            Toast.makeText(this, "הגיבוי נוסף לרשימה: " + fileName, Toast.LENGTH_LONG).show();
            showSettings();
        } catch (Exception exception) {
            Toast.makeText(this, "לא הצלחתי להוסיף את קובץ הגיבוי", Toast.LENGTH_LONG).show();
        }
    }

    private void shareBackup(BackupStorage.BackupEntry backup) {
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType(BackupStorage.MIME_TYPE)
                .putExtra(Intent.EXTRA_STREAM, backup.uri)
                .putExtra(Intent.EXTRA_SUBJECT, backup.name)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(intent, "שיתוף גיבוי"));
        } catch (Exception exception) {
            Toast.makeText(this, "אין אפליקציה זמינה לשיתוף", Toast.LENGTH_LONG).show();
        }
    }

    private void shareLog(LogStorage.LogEntry log) {
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType(LogStorage.MIME_TYPE)
                .putExtra(Intent.EXTRA_STREAM, log.uri)
                .putExtra(Intent.EXTRA_SUBJECT, log.name)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(intent, "שיתוף לוג"));
        } catch (Exception exception) {
            Toast.makeText(this, "אין אפליקציה זמינה לשיתוף", Toast.LENGTH_LONG).show();
        }
    }

    private void copyLog(LogStorage.LogEntry log) {
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText(log.name, LogStorage.readLog(this, log)));
                Toast.makeText(this, "הלוג הועתק", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception exception) {
            Toast.makeText(this, "לא הצלחתי להעתיק את הלוג", Toast.LENGTH_LONG).show();
        }
    }

    private void showPickedLog(Uri uri) {
        LogStorage.LogEntry log = new LogStorage.LogEntry(displayName(uri, "WatchReminder log"), uri, System.currentTimeMillis());
        new AlertDialog.Builder(this)
                .setTitle("קובץ לוג")
                .setMessage(log.name)
                .setItems(new String[]{"שיתוף", "העתקה"}, (dialog, which) -> {
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
        Toast.makeText(this, "שולח לשעון...", Toast.LENGTH_SHORT).show();
        WatchRestoreSender.sendBackup(this, backup, new WatchRestoreSender.Callback() {
            public void onSuccess() {
                runOnUiThread(() -> Toast.makeText(PhoneMainActivity.this, "נשלח לשעון. אשר בשעון.", Toast.LENGTH_LONG).show());
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
