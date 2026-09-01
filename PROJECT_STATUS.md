# QUICK RESUME

קראו קודם רק את הסעיף הזה. קראו את `FULL PROJECT STATUS` רק אם המשימה דורשת פרטים נוספים; קראו את `PROJECT_HISTORY.md` רק כאשר היסטוריה רלוונטית.

- פרויקט: WatchReminder / Zmanio — אפליקציית Wear OS לתזכורות, זמני הלכה וגיבוי מול טלפון.
- נתיב פעיל: `/Users/refaelnakar/Documents/Work/TEMP/Android Temp AI Workspace/WatchReminder`
- מודולים: `:app` לשעון ו־`:phone` לטלפון.
- מצב נוכחי: תזכורות רגילות, AlarmManager, Smart Alarm, נודניק, גיבוי ו־complications פעילים.
- מכשיר בדיקה: OnePlus Watch 3 פיזי ב־ADB אלחוטי; אמולטור Wear זמין לפי הצורך.
- שורש תקלה קריטית 01–02/09: לאחר שחזור כ־35 תזכורות, `MainActivity` ביצעה catch-up כבד על ה־UI thread. במקביל עבר הפוקוס מ־`ReminderAlertActivity` לדף היומי, נוצר ANR של 5 שניות, ואז Android ביצע `FORCE STOP` שמחק את כל ה־alarms.
- תיקון 02/09: בדיקת foreground הועברה ל־worker, `BootReceiver` משתמש ב־`goAsync`, ודף יומי/עומר נדחים בדקה כאשר תזכורת רגילה פעילה. בכך נמנעים main-thread ANR ומסכי full-screen מתחרים.
- מצב השעון: גרסת debug ‏1.16 מותקנת, `stopped=false`, Full-Screen Intent=`allow`, והחבילה ב־Doze allowlist. הנתונים ו־35 התזכורות נשמרו.
- אימות פיזי 02/09: שתי תזכורות catch-up הוצגו ברצף; בדיקת `setAlarmClock` העירה את השעון משינה עמוקה ב־00:42:09 ופתחה את מסך ההתראה. אין ANR חדש; תזכורת 07:15 ו־watchdog 07:17 רשומים.
- סיכון מרכזי: force-stop אמיתי עדיין חוסם את כל מנגנוני Android עד פתיחת האפליקציה, אך האירוע שנצפה נגרם מ־ANR באפליקציה ולא מהגדרת OnePlus מסתורית. OnePlus עדיין עלול לחסום רטט בזמן Bedtime/DND.
- החרגת סוללה: קיימת בקשת Android בקוד; ב־OnePlus `FakeSettingsActivity` אינה מציגה אישור. בשעון הבדיקה ההחרגה הוחזרה דרך Doze allowlist של ADB.
- Build: `./gradlew :app:testDebugUnitTest :app:assembleDebug`; לטלפון `./gradlew :phone:assembleDebug`.
- התקנה: `adb install -r app/build/outputs/apk/debug/app-debug.apk`, ואז לפתוח את `com.woodpeckerbros.watchreminder/.MainActivity`.
- כללי בטיחות: אין לשנות מבנה, package name, Gradle/AGP/SDK או dependencies ללא אישור; לא לבצע force-stop בלי לשחזר alarms.
- שינוי אחרון: אבחון `ApplicationExitInfo` ו־`lastanr` הוכיח שה־force-stop הגיע אחרי ANR בזמן catch-up, ולא ממנהל הסוללה. התיקון נבנה, הותקן ונבדק על השעון.
- שינוי אחרון (01/09): כל גיבוי חדש נשמר אוטומטית ב־`Downloads/Zmanio`; הוסר כפתור `שמירה` המיותר כדי למנוע עותקים כפולים. הסריקה כוללת MediaStore, fallback ל־Downloads וסריקה ישירה של תיקיות ישנות.
- שינוי אחרון נוסף (01/09): סומן `BIND_LISTENER` כחריגת Lint מאושרת בשני ה־Manifest-ים; הפעולה נשארה כדי לא לשבור את קבלת הודעות Wearable. שני מודולי debug נבנו בהצלחה.
- שינוי אחרון נוסף (01/09): דף יומי שפוספס ביום קודם מוצג כעת מיד ב־`dispatchIfDueNow`, גם אם שעת ההתראה של היום טרם הגיעה; לפני כן הקוד המתין להתראה הבאה.
- אבחון 01/09 13:56: עדכון APK/force-stop הותיר את האפליקציה `stopped=true` וביטל את כל ה־AlarmManager entries ב־13:55. לאחר פתיחת האפליקציה השחזור הפעיל מיד תזכורות שפוספסו (13:00, 13:20), ורשם מחדש אזעקה ל־17:00, watchdog ו־recovery job; exact alarms, התראות ו־full-screen תקינים. בכל התקנה יש לפתוח מיד את `MainActivity` כדי להסיר `stopped` ולשחזר תזמונים.
- משימה מיידית: להשאיר את השעון כרגיל ולאמת את התזכורת האמיתית הקרובה ב־07:15; אם תוחמץ, לא לפתוח את האפליקציה לפני איסוף `exit-info`, `dumpsys alarm` ולוג האפליקציה.
- שינוי 02/09: במסך יצירת Smart Alarm חלון ההשכמה החכם הועבר בין שעת ההשכמה לימי הפעילות; כיבוי ללא בדיקת ערנות מבטל נודניקים ובדיקות ערנות ממתינות ומסמן את המופע כבוצע. `BODY_SENSORS_BACKGROUND` הוסר כי אינו בשימוש או מבוקש.
- שינוי 02/09: נוספה בקשת משתמש אופציונלית ל־Do Not Disturb policy access. רק לאחר אישור ידני במסך המערכת ערוץ Smart Alarm החדש רשאי לבקש עקיפת DND; אין הענקה כפויה והגבלות Bedtime של היצרן עדיין עשויות לחול.

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
- Smart Alarm כולל חלון חכם, זיהוי ערנות, משימות כיבוי, צליל, רטט, snooze ו־fallback.
- תזמון מחדש קיים לאחר boot, timezone, שינוי מיקום ושינוי הגדרות.
- הרשאות notifications, location, activity recognition, body sensors, exact alarm ו־full-screen מטופלות ברצף onboarding.
- יש תמיכה בעברית ובאנגלית, עיצוב Wear עגול, רקעים, complications וגיבוי טלפון.
- `:app:testDebugUnitTest` ו־`:app:assembleDebug` עברו בבדיקה האחרונה.

## שבור, לא גמור או מסוכן

- אירוע ה־force-stop שנחקר ב־02/09 נבע מ־ANR: בדיקת foreground סינכרונית ארכה כחמש שניות בזמן מעבר בין מסך תזכורת רגילה לדף היומי. Android סגר את האפליקציה ומחק את ה־alarms. הבדיקה וה־BootReceiver הועברו לעבודה אסינכרונית, ומסכי דף יומי/עומר נדחים מאחורי תזכורת רגילה פעילה.
- אין API שמאפשר לאפליקציה להתאושש מ־force-stop אמיתי לפני שהמשתמש פותח אותה; יש להמשיך לעקוב אחר `ApplicationExitInfo` אם החבילה שוב תופיע כ־stopped.
- reboot מוחק רשומות AlarmManager. USER_UNLOCKED ו־JobScheduler persisted נוספו כגיבוי, אך אינם יכולים לרוץ כאשר OnePlus מסמן את החבילה stopped אחרי boot.
- שחזור טלפון→שעון: `BIND_LISTENER` הופרד ל־intent-filter ללא data constraints, בעוד `MESSAGE_RECEIVED` נשאר ב־filter של path. שני המודולים נבנו, וה־APK המתוקן הותקן ואומת בשעון; נדרש ניסיון שליחה נוסף מהטלפון.
- אבחון restore 01/09: לוג WearableService בשעון מדווח `Mismatched certificate` ואז `Failed to deliver message` עבור `/watch_reminder_restore`. הטלפון הוא Play-signed והשעון debug-signed; Google Data Layer דוחה את ההודעה לפני שהשירות מופעל. אין בפרויקט keystore של Play. פתרון: להתקין את שני הצדדים חתומים באותו מפתח (העלאה משותפת ל־Play), או להתקין את שני APK-ה־debug המקומיים לאחר גיבוי נתוני הטלפון.
- 01/09: `phone-debug.apk` הותקן בהצלחה מעל התקנת הטלפון ללא הסרה; חתימת הטלפון אומתה זהה לשעון (`dc11fc53`). נפתח מסך הרשאת התראות בטלפון, ויש לאשר אותו לפני בדיקת restore.
- רשימת Smart Alarm: לחיצה ארוכה על כרטיס שעון מעורר פותחת תפריט עריכה/מחיקה/ביטול זהה לתזכורת רגילה; מחיקה מבטלת תחילה את תזמוני השעון.
- OnePlus עשוי לדחות רטט בזמן Bedtime/DND גם כאשר הערוץ מוגדר כ־Alarm.
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
