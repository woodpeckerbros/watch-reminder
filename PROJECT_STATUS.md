# QUICK RESUME

קראו קודם רק את הסעיף הזה. קראו את `FULL PROJECT STATUS` רק אם המשימה דורשת פרטים נוספים; קראו את `PROJECT_HISTORY.md` רק כאשר היסטוריה רלוונטית.

- פרויקט: WatchReminder / Zmanio — אפליקציית Wear OS לתזכורות, זמני הלכה וגיבוי מול טלפון.
- נתיב פעיל: `/Users/refaelnakar/Documents/Work/TEMP/Android Temp AI Workspace/WatchReminder`
- מודולים: `:app` לשעון ו־`:phone` לטלפון.
- מצב נוכחי: תזכורות רגילות, AlarmManager, Smart Alarm, נודניק, גיבוי ו־complications פעילים.
- מכשיר בדיקה: OnePlus Watch 3 פיזי ב־ADB אלחוטי; אמולטור Wear זמין לפי הצורך.
- תקלה קריטית 01/09: reboot של השעון סביב 05:05 מחק את AlarmManager, ו־BootReceiver לא שיחזר את Smart Alarm 07:00 ואת התזכורת 07:15. נוסף שחזור גם ב־USER_UNLOCKED ו־JobScheduler מחזורי persisted כקו הגנה שני.
- סיכון מרכזי: firmware של OnePlus עלול לבצע force-stop או לחסום רטט בזמן Bedtime/DND. שום מנגנון Android אינו יכול להתאושש מ־force-stop אמיתי עד פתיחת האפליקציה.
- החרגת סוללה: קיימת בקשת Android בקוד; ב־OnePlus `FakeSettingsActivity` אינה מציגה אישור. בשעון הבדיקה ההחרגה ניתנת דרך Doze allowlist של ADB.
- Build: `./gradlew :app:testDebugUnitTest :app:assembleDebug`; לטלפון `./gradlew :phone:assembleDebug`.
- התקנה: `adb install -r app/build/outputs/apk/debug/app-debug.apk`, ואז לפתוח את `com.woodpeckerbros.watchreminder/.MainActivity`.
- כללי בטיחות: אין לשנות מבנה, package name, Gradle/AGP/SDK או dependencies ללא אישור; לא לבצע force-stop בלי לשחזר alarms.
- שינוי אחרון: נוסף `ReminderRecoveryJobService` מתמשך לאחר reboot, ו־BootReceiver מאזין גם ל־locked boot ול־user unlocked. Build ובדיקות עברו; לפני reboot ה־job רץ ושיחזר את alarms של 07:00 ו־07:15.
- משימה מיידית: לאחר חזרת ADB האלחוטי מה־reboot המבוקר, לאמת שה־job נשמר ושה־alarms שוחזרו בלי פתיחה ידנית; לאחר מכן לבצע בדיקת התראה קרובה עם מסך כבוי.

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

- OnePlus עשוי לבצע force-stop פנימי דרך מנהל החשמל; אין API לאפליקציית צד שלישי שמונע זאת לחלוטין.
- reboot מוחק רשומות AlarmManager. החל מתיקון 01/09, USER_UNLOCKED ו־JobScheduler persisted מספקים שני מסלולי שחזור בנוסף ל־BOOT_COMPLETED.
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
