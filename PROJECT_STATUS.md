# QUICK RESUME

- תיקון קריטי 09/09 — מסך Smart Alarm: לוגי השעון הוכיחו ש־OnePlus SystemUI סיווג את ערוץ ה־full-screen השקט כ־`not noisy` ולא הציג אותו; ניסיונות ה־FGS לפתוח Activity נחסמו שוב ושוב ב־`BAL_BLOCK`. גם לחיצה על „פתיחת התראה” נחסמה כ־notification trampoline.
- התיקון: ערוץ `smart_alarm_alert_v9_attention` נשאר ללא צליל מערכת אך כולל רטט attention של 1ms, ולכן SystemUI שולח את ה־full-screen. ה־contentIntent ופעולת „פתיחת התראה” הם כעת PendingIntent ישיר ל־`SmartAlarmAlertActivity`, עם opt-in ל־BAL ב־Android 15.
- שומר המסך בודק גם window focus ולא רק lifecycle; אם מסך OnePlus כגון Sleep report מכסה את האזעקה בלי `onPause`, משימת ההתראה נוצרת מחדש ומוחזרת לחזית.
- אימות פיזי 09/09: מהמסך הכבוי (`mWakefulness=Asleep`) SystemUI רשם `shouldVibrate=true`, פתח את `SmartAlarmAlertActivity`, והיא הייתה `RESUMED`, visible ו־focused. לחיצה אמיתית על „כיבוי” סגרה את המסך, עצרה צליל/רטט ושירות הצלצול, והסירה את notification.
- תיקון Smart Wake 09/09: ערך `PASSIVE` שמור אינו נספר עוד כתצפית Health Services חדשה. הלילה דגימה שמורה יחידה גרמה ל־`SYSTEM_AWAKE_PERSISTENT` ול־WAKE ללא candidate/clearly-awake; מעתה רק callback חי מתחיל debounce, בעוד מסלולי Smart Score ו־Clearly Awake נשארו ללא שינוי.
- תיקון שרשרת snooze ‏09/09: יעד המופע המקורי נשמר גם כשה־state עובר לזמני נודניק. לאחר מיצוי כל הנודניקים, התזמון הבא מחושב אחרי יעד הבוקר המקורי ולכן אינו יכול להתחיל שוב את אותו חלון Smart Wake.
- שורת `SmartWake summary` קוצרה לפורמט: `timestamp → WAKE_SCORE → groups → candidate → clearly_awake → decision`; telemetry המפורט נשאר בנפרד.
- בדיקות: `:app:compileDebugJavaWithJavac :app:testDebugUnitTest :app:assembleDebug` עברו. גרסת debug ‏1.26 (code 128) הסופית הותקנה בהצלחה על OnePlus Watch 3; מיד אחר כך ADB האלחוטי התנתק לפני פתיחה חוזרת, ולכן יש לפתוח ידנית פעם אחת את Zmanio.
- לפני ההתקנה הסופית `stopped=false` והתזמונים אומתו: תזכורת רגילה 09/09 07:15; Smart Wake למחר 10/09 מתחיל ניטור 05:43:45, חלון 06:00, deadline ‏06:40. מופע debug ‏9999 בוטל. לאחר פתיחה ידנית יש לוודא שוב שהתזמונים שוחזרו.
- תיקוני 08/09 שנשארים פעילים: UI cache למסכי התזכורות, startup/catch-up אסינכרוני למניעת ANR, ארגון מחלקות לפי תחומים, ודילוג למופע הבא לאחר כיבוי Smart Alarm שהופעל מוקדם.
- `ReminderMonitoringService` הוא FGS שקט מסוג specialUse, פעיל רק כאשר ניטור מופעל ויש תזכורות; AlarmManager ממשיך למסור את ההתראות.
- force-stop אמיתי עדיין חוסם את החבילה ומוחק alarms; אין מנגנון חוקי באותו package שעוקף זאת. מניעת ANR ופתיחת האפליקציה לאחר התקנה/עדכון נשארות קריטיות.
- worktree כולל שינויים קיימים של המשתמש בגרסאות `app`/`phone`, קבצי IDE ו־DS_Store; אין לכלול אותם ב־commit של תיקון זה.
- Build: `./gradlew :app:testDebugUnitTest :app:assembleDebug`. התקנה: `adb install -r app/build/outputs/apk/debug/app-debug.apk` ואז פתיחת `MainActivity`.
- אין לשנות מבנה תיקיות, Gradle/AGP/SDK, dependencies או package names ללא אישור.

קראו קודם רק את הסעיף הזה. קראו את `FULL PROJECT STATUS` רק כשנדרש פירוט; את `PROJECT_HISTORY.md` רק להקשר היסטורי.

# FULL PROJECT STATUS

## מטרה וארכיטקטורה

Zmanio היא אפליקציית Wear OS עצמאית לניהול תזכורות רגילות, תזכורות הלכתיות/יהודיות, Smart Alarm, זמני היום, דף יומי, ספירת העומר, קידוש לבנה, תקופות, צום לסירוגין ו־complications. מודול הטלפון מספק גיבוי, שחזור וסנכרון.

התזכורות נשמרות ב־`ReminderStore`, מחושבות ב־`NextReminderCalculator` ומתוזמנות דרך `ReminderScheduler`. התזכורת הקרובה משתמשת ב־`setAlarmClock`; אירועי עזר ו־watchdog משתמשים ב־exact alarms המתאימים. `ReminderReceiver`, בדיקת due בהפעלה ו־watchdog מספקים שכבות התאוששות.

Smart Alarm נמצא תחת `app/src/main/java/com/woodpeckerbros/watchreminder/smartwake/`: חלון ניטור חיישנים, deadline, מסך התראה ושירות משוב. מסכי ההתראה משתמשים ב־full-screen notifications; שירות הצלצול הוא fail-safe בלבד.

## קבצים ותיקיות חשובים

- `app/src/main/AndroidManifest.xml` — הרשאות, receivers, services ופעילויות השעון.
- `app/src/main/java/com/woodpeckerbros/watchreminder/MainActivity.java` — UI, onboarding ורצף הרשאות.
- `ReminderScheduler.java`, `ReminderReceiver.java`, `ReminderDueChecker.java` — תזמון והפעלת תזכורות.
- `smartwake/` — Smart Alarm וניטור ערנות.
- `phone/src/main/java/...` — גיבוי/שחזור/סנכרון בטלפון.
- `app/src/test/` — בדיקות יחידה.
- `PROJECT_HISTORY.md` — היסטוריה מלאה; אין לקרוא אוטומטית.

## מה עובד

- תזכורות רגילות נבדקו על OnePlus Watch: AlarmManager, full-screen notification, מסך התראה ונודניק.
- Smart Alarm כולל חלון חכם, זיהוי ערנות, משימות כיבוי, צליל, רטט, snooze ו־fallback. ב־09/09 אומתו פיזית מהמסך הכבוי פתיחת full-screen, פוקוס מלא וכיבוי שעוצר את כל ה־feedback.
- תזמון מחדש קיים לאחר boot, timezone, שינוי מיקום ושינוי הגדרות.
- הרשאות notifications, location, activity recognition, body sensors, exact alarm ו־full-screen מטופלות ברצף onboarding.
- יש תמיכה בעברית ובאנגלית, עיצוב Wear עגול, רקעים, complications וגיבוי טלפון.
- `:app:testDebugUnitTest` ו־`:app:assembleDebug` עברו בבדיקה האחרונה.

## שבור, לא גמור או מסוכן

- אירוע ה־force-stop שנחקר ב־02/09 נבע מ־ANR: בדיקת foreground סינכרונית ארכה כחמש שניות בזמן מעבר בין מסך תזכורת רגילה לדף היומי. Android סגר את האפליקציה ומחק את ה־alarms. הבדיקה וה־BootReceiver הועברו לעבודה אסינכרונית, ומסכי דף יומי/עומר נדחים מאחורי תזכורת רגילה פעילה.
- החבילה לבדה אינה יכולה להתאושש מ־force-stop אמיתי; המיקוד הוא במניעה: שמירת FGS חוקי, AlarmClock, recovery לאחר boot/update/time, watchdog, due-check ותיקון כל נתיב שעלול לגרום ל־ANR או לסגירה יזומה.
- reboot מוחק רשומות AlarmManager. USER_UNLOCKED ו־JobScheduler persisted נוספו כגיבוי, אך אינם יכולים לרוץ כאשר OnePlus מסמן את החבילה stopped אחרי boot.
- שחזור טלפון→שעון: `BIND_LISTENER` הופרד ל־intent-filter ללא data constraints, בעוד `MESSAGE_RECEIVED` נשאר ב־filter של path. שני המודולים נבנו, וה־APK המתוקן הותקן ואומת בשעון; נדרש ניסיון שליחה נוסף מהטלפון.
- אבחון restore 01/09: לוג WearableService בשעון מדווח `Mismatched certificate` ואז `Failed to deliver message` עבור `/watch_reminder_restore`. הטלפון הוא Play-signed והשעון debug-signed; Google Data Layer דוחה את ההודעה לפני שהשירות מופעל. אין בפרויקט keystore של Play. פתרון: להתקין את שני הצדדים חתומים באותו מפתח (העלאה משותפת ל־Play), או להתקין את שני APK-ה־debug המקומיים לאחר גיבוי נתוני הטלפון.
- 01/09: `phone-debug.apk` הותקן בהצלחה מעל התקנת הטלפון ללא הסרה; חתימת הטלפון אומתה זהה לשעון (`dc11fc53`). נפתח מסך הרשאת התראות בטלפון, ויש לאשר אותו לפני בדיקת restore.
- רשימת Smart Alarm: לחיצה ארוכה על כרטיס שעון מעורר פותחת תפריט עריכה/מחיקה/ביטול זהה לתזכורת רגילה; מחיקה מבטלת תחילה את תזמוני השעון.
- OnePlus עשוי לדחות רטט בזמן Bedtime/DND גם כאשר הערוץ מוגדר כ־Alarm. מסך Smart Alarm אינו תלוי עוד בערוץ שקט לחלוטין: ערוץ attention של 1ms גורם ל־SystemUI למסור את ה־full-screen, והצליל/רטט המתמשך נשאר בבעלות האפליקציה.
- בקשת `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` על OnePlus מפנה למסך דמה; אין להניח שהמשתמש אושר אלא לבדוק `PowerManager`.
- ADB אלחוטי אינו תמיד מחובר. אין לבצע בדיקות התקנה/force-stop בלי לוודא serial ולשחזר תזמונים.
- worktree עשוי לכלול שינויים קיימים של המשתמש; אין לנקות או לדרוס אותם.

## הרצה ואימות

```text
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :phone:assembleDebug
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.woodpeckerbros.watchreminder/.MainActivity
adb shell dumpsys alarm
adb logcat -d -v threadtime | rg 'woodpeckerbros|Reminder|SmartAlarm'
```

יש לאמת תזכורת רגילה אמיתית על השעון, כולל מסך כבוי ו־Doze, לאחר כל שינוי בתזמון. אין לשנות גרסאות Gradle, Android Gradle Plugin, SDK, dependencies או package names ללא אישור.

## התקנים

היעד הפיזי האחרון: OnePlus Watch 3, Wear OS API 34/target 35, serial ADB אלחוטי משתנה. האמולטור העיקרי: `Wear_OS_Large_Round` (`emulator-5554`). התקנה מחדש או ניקוי נתונים עלולים לבטל נתוני תזכורות; יש לוודא גיבוי לפני פעולה כזו.

## נוהל עבודה

להעדיף שינוי קטן והפיך, לבנות ולבדוק, לעדכן את QUICK RESUME ואת הסטטוס המפורט, ליצור commit ברור ולדחוף ל־Git כאשר היעד מאושר. אין לבצע שינויים שאינם קשורים למשימה.
