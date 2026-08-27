package com.woodpeckerbros.watchreminder.phone;

import android.content.Context;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class PhoneUiText {
    private static final Map<String, String> EN = new HashMap<>();

    static {
        put("ניהול תזכורות שעון", "Manage reminders from your phone");
        put("סנכרון מהשעון", "Sync from watch");
        put("שליחה לשעון", "Send to watch");
        put("הוספה", "Add reminder");
        put("הגדרות", "Settings");
        put("שעונים חכמים", "Smart alarms");
        put("שעונים מעוררים חכמים", "Smart alarms");
        put("ניהול השעונים החכמים שמסונכרנים עם השעון", "Manage smart alarms synced with your watch");
        put("אין שעונים מעוררים חכמים", "No smart alarms");
        put("סנכרן מהשעון או הוסף שעון חדש", "Sync from the watch or add a new alarm");
        put("הוספת שעון מעורר", "Add alarm");
        put("שעון חכם חדש", "New smart alarm");
        put("עריכת שעון חכם", "Edit smart alarm");
        put("פעיל", "Enabled"); put("כבוי", "Disabled");
        put("שעת השכמה", "Wake-up time"); put("ימי פעילות", "Active days");
        put("חלון חכם בדקות", "Smart window in minutes"); put("נודניק", "Snooze");
        put("כל כמה דקות", "Interval in minutes"); put("מספר פעמים", "Number of times");
        put("רטט וצלצול", "Vibration and sound"); put("רטט", "Vibration"); put("צלצול", "Sound");
        put("סוג רטט", "Vibration pattern"); put("עוצמת רטט", "Vibration strength");
        put("עוצמת צלצול", "Sound volume"); put("משך ההתראה בשניות", "Alarm duration in seconds");
        put("רגיל", "Normal"); put("עדין", "Gentle"); put("חזק", "Strong"); put("ארוך", "Long");
        put("צריך לבחור לפחות יום אחד", "Choose at least one day");
        put("מצב החיבור", "Sync status");
        put("התזכורות שלי", "My reminders");
        put("אין תזכורות בטלפון", "No reminders on this phone");
        put("לחץ על סנכרון מהשעון כדי להתחיל", "Sync from your watch to get started");
        put("תזכורת", "Reminder");
        put("תזכורת חדשה", "New reminder");
        put("עריכת תזכורת", "Edit reminder");
        put("הנתונים יישמרו בטלפון עד שליחה לשעון", "Changes stay on your phone until sent to the watch");
        put("שם", "Name");
        put("תיאור (אופציונלי)", "Description (optional)");
        put("פעילה", "Enabled");
        put("חיונית", "Critical");
        put("סוג תזכורת", "Reminder type");
        put("תאריך", "Date");
        put("שעה", "Time");
        put("ימים קבועים", "Repeat on days");
        put("ראשון", "Sunday"); put("שני", "Monday"); put("שלישי", "Tuesday");
        put("רביעי", "Wednesday"); put("חמישי", "Thursday"); put("שישי", "Friday"); put("שבת", "Saturday");
        put("לפי זמני הלכה", "Use halachic time");
        put("בחר זמן הלכה", "Choose halachic time");
        put("דקות לפני / אחרי", "Minutes before / after");
        put("מחזורית", "Periodic");
        put("תאריך עברי", "Hebrew date");
        put("כל כמה", "Every");
        put("יום", "Day"); put("חודש", "Month"); put("שנה", "Year");
        put("אירוע שנתי", "Annual event");
        put("שעות לפני", "Hours before");
        put("מספר הבא", "Next number");
        put("שמירה", "Save"); put("ביטול", "Cancel");
        put("לא הצלחתי לשמור", "Could not save the reminder");
        put("גיבויים והגדרות כלליות", "Backups and general preferences");
        put("זמני שקט פעילים", "Quiet times enabled");
        put("תזכורת ברכה בדקות", "Blessing reminder (minutes)");
        put("קריאת שמע אחרי צאת הכוכבים", "Shema after nightfall (minutes)");
        put("סגירה אוטומטית בשניות", "Auto-close (seconds)");
        put("דחייה אוטומטית בדקות", "Auto-snooze (minutes)");
        put("שמירת הגדרות", "Save settings");
        put("נשמר בטלפון. שלח לשעון כדי לעדכן.", "Saved on the phone. Send to the watch to apply.");
        put("ⓘ  אודות ורישיונות", "ⓘ  About & licenses");
        put("חזרה", "Back");
        put("אודות ורישיונות", "About & licenses");
        put("רכיבי צד שלישי ב-Zmanio", "Third-party components in Zmanio");
        put("אפליקציית הטלפון משתמשת ברכיבי צד שלישי. תודה ליוצרים ולתורמים שלהם.", "The phone app uses third-party components. We thank their creators and contributors.");
        put("חזרה להגדרות", "Back to settings");
        put("הצגת נוסח הרישיון", "View license text");
        put("נוסח הרישיון וההודעות", "License text and notices");
        put("חזרה לרישיונות", "Back to licenses");
        put("לא ניתן לטעון את נוסח הרישיון", "Could not load the license text");
        put("גיבויים", "Backups");
        put("אין קבצי ‎.zmbu‎ זמינים", "No .zmbu backup files available");
        put("טעינה לעריכה", "Load for editing"); put("שלח לשעון", "Send to watch"); put("שיתוף", "Share");
        put("בחר קובץ גיבוי", "Choose backup file");
        put("לוגים מהשעון", "Watch logs"); put("אין לוגים זמינים", "No logs available");
        put("העתקה", "Copy"); put("בחר קובץ לוג", "Choose log file");
        put("לא הצלחתי לפתוח בוחר קבצים", "Could not open the file picker");
        put("מבקש סנכרון מהשעון...", "Requesting sync from watch…");
        put("נשלחה בקשה. הנתונים יופיעו כשהשעון ישלח.", "Request sent. Data will appear when the watch responds.");
        put("שולח לשעון...", "Sending to watch…");
        put("השינויים נשלחו לשעון. אשר בשעון.", "Changes sent. Confirm on your watch.");
        put("עריכה", "Edit"); put("מחיקה", "Delete");
        put("בחר סוג תזכורת", "Choose reminder type");
        put("חד פעמית", "One-time"); put("קבועה", "Fixed"); put("אירוע שנתי", "Annual event");
        put("שעות", "hours"); put("ימים", "days"); put("שבועות", "weeks"); put("חודשים", "months"); put("שנים", "years");
        put("עלות השחר", "Dawn"); put("זריחה", "Sunrise"); put("סוף זמן שמע", "Latest Shema");
        put("סוף זמן תפילה", "Latest Prayer"); put("חצות", "Midday"); put("מנחה גדולה", "Mincha Gedola");
        put("מנחה קטנה", "Mincha Ketana"); put("פלג המנחה", "Plag HaMincha"); put("שקיעה", "Sunset"); put("צאת הכוכבים", "Nightfall");
        put("טרם בוצע סנכרון.", "Not synced yet.");
        put("הגיבוי נטען לעריכה", "Backup loaded for editing");
        put("לא הצלחתי לטעון גיבוי", "Could not load the backup");
        put("לא הצלחתי להוסיף את קובץ הגיבוי", "Could not add the backup file");
        put("הקובץ שנבחר אינו קובץ גיבוי של Zmanio", "The selected file is not a Zmanio backup");
        put("שיתוף גיבוי", "Share backup"); put("שיתוף לוג", "Share log");
        put("אין אפליקציה זמינה לשיתוף", "No sharing app is available");
        put("הלוג הועתק", "Log copied"); put("לא הצלחתי להעתיק את הלוג", "Could not copy the log");
        put("קובץ לוג", "Log file"); put("נשלח לשעון. אשר בשעון.", "Sent to watch. Confirm on your watch.");
        put("לא נמצא שעון מחובר", "No connected watch was found");
        put("נשלחה בקשה לחלק מהשעונים", "The request was sent to some watches");
        put("בקשת הסנכרון נכשלה", "The sync request failed");
        put("לא הצלחתי למצוא שעון", "Could not find a watch");
        put("אין שינויים לשליחה", "There are no changes to send");
        put("שליחת השינויים לשעון נכשלה", "Sending changes to the watch failed");
        put("אין גיבוי לשליחה", "There is no backup to send");
        put("לא הצלחתי לקרוא את הגיבוי", "Could not read the backup");
        put("אין נתונים לשליחה. בצע סנכרון מהשעון קודם.", "There is no data to send. Sync from the watch first.");
        put("השליחה לשעון נכשלה", "Sending to the watch failed");
        put("עדיין לא התקבל גיבוי מהשעון.", "No backup has been received from the watch yet.");
    }

    private PhoneUiText() {}

    private static void put(String he, String en) { EN.put(he, en); }

    static String t(Context context, String hebrew) {
        if (!isEnglish(context) || hebrew == null) return hebrew;
        String translated = EN.get(hebrew);
        return translated == null ? hebrew : translated;
    }

    static String[] t(Context context, String[] values) {
        String[] translated = new String[values.length];
        for (int i = 0; i < values.length; i++) translated[i] = t(context, values[i]);
        return translated;
    }

    static boolean isEnglish(Context context) {
        String configured = "auto";
        try {
            String raw = context.getSharedPreferences("local_reminder_document", Context.MODE_PRIVATE)
                    .getString("text", "");
            if (!raw.isEmpty()) {
                JSONObject settings = new JSONObject(raw).optJSONObject("settings");
                if (settings != null) configured = settings.optString("language", "auto");
            }
        } catch (Exception ignored) {}
        if ("en".equalsIgnoreCase(configured) || "english".equalsIgnoreCase(configured)) return true;
        if ("he".equalsIgnoreCase(configured) || "iw".equalsIgnoreCase(configured) || "hebrew".equalsIgnoreCase(configured)) return false;
        return !"he".equalsIgnoreCase(Locale.getDefault().getLanguage())
                && !"iw".equalsIgnoreCase(Locale.getDefault().getLanguage());
    }
}
