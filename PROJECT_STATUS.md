# QUICK RESUME

- כוונון Smart Wake 08/09: `PASSIVE`/`EXERCISE` מ־Health Services הם כעת מסלול יקיצה ראשי אך עם debounce: WAKE רק לאחר 30 שניות non-ASLEEP רציף, או שתי תצפיות non-ASLEEP בתוך 90 שניות; `ASLEEP`/`UNKNOWN` מאפסים מיד את הרצף. המסלול הקיים של Smart Score נשאר עצמאי ובאותם ספים/משקלים, ומסוגל להעיר גם כאשר המערכת עדיין ASLEEP. summary/telemetry מציגים מצב שינה, משך/מספר תצפיות non-ASLEEP, persistence ו־`WAKE_REASON`/`CONTINUE_REASON`; ה־deadline רושם `WAKE_REASON=FINAL_DEADLINE`. נוספו בדיקות ל־transient, persistence ולמסלול score; `:app:testDebugUnitTest :app:assembleDebug` עברו. APK מוכן אך התקנת השעון ממתינה לחיבור ADB שהתנתק.
- תיקון קריטי Smart Alarm 08/09 (מסך foreground): ב־OnePlus נשמע צלצול של Smart Alarm אך ה־full-screen נשאר לעתים במגירת ההתראות. `SmartAlarmRingingService` נשאר כעת FGS שומר־מסך עד לסיום ההתראה, בודק כל 2 שניות שה־`SmartAlarmAlertActivity` בחזית ומבקש להחזיר אותו אם לא. הוא אינו מפעיל feedback כפול כאשר ה־Activity כבר נראית. להתראה הראשית ולהתראת השירות נוספה פעולת „פתיחת התראה”; פעולה זו מקבלת user-initiated launch ומפעילה ישירות את מסך ההתראה. build ו־unit tests עברו; ה־APK הותקן על OnePlus Watch 3 ו־`stopped=false` אומת.
- תיקון קריטי Smart Alarm 08/09: כיבוי מופע שהופעל מוקדם בתוך חלון ה־Smart Wake (למשל 06:59 עבור יעד 07:10) כבר אינו מתזמן מחדש את אותו יעד. `scheduleNextAfterHandled` מדלג במפורש למופע הבא בלוח הזמנים, וכל מסלולי הכיבוי מבטלים קודם receivers וניטור פעיל. כך כיבוי עוצר מיד sampling/רטט/צליל ומונע צלצול חוזר באותו בוקר. התראת ה־full-screen של Android הוגדרה שקטה; הרטט והצליל הראשיים נשארים בבעלות מסך/שירות Zmanio, כדי שלא תורגש התראת מערכת לפני הממשק שלנו. נוסף unit test לתרחיש, ו־`:app:testDebugUnitTest :app:assembleDebug` עברו. ה־APK הותקן על OnePlus Watch 3.
- שינוי ארכיטקטורה 07/09: מודול `:guardian` הוסר לחלוטין. הוא היה APK נפרד שנדרש רק כדי לקבל UID שונה ולהישאר חי לאחר force-stop של Zmanio; זה אינו פתרון הפצה סביר משום שמשתמש היה נדרש להתקין אפליקציה נוספת. אין מנגנון מקביל שניתן להעביר לאותו package—force-stop של Zmanio חוסם כל receiver/service/alarm שלה. נשמרו החיזוקים התקינים באפליקציה היחידה: AlarmClock, FGS מסוג specialUse עם התחלה מיידית ו־`stopWithTask=false`, שחזור לאחר boot/update/time, watchdog, due-check, ומניעת הסתמכות שגויה על PendingIntent ישן אחרי process חדש.
- אבחון 07/09 (פער התראות עד פתיחה סביב 13:00): `ApplicationExitInfo` בשעון קבע שב־11:49:20 המערכת ביצעה `FORCE STOP` לחבילה (`reason=USER_REQUESTED`, `subreason=FORCE_STOP`, יוזם pid 1290/ActivityManager). עד אז נמסרו התראות ב־11:15, 11:35 ו־11:40; ה־snooze של 11:50 וההתראה של 13:00 לא יכלו להימסר, מפני ש־force-stop מבטל את כל AlarmManager entries וחוסם הפעלה של רכיבי האפליקציה. פתיחת האפליקציה ב־13:08 הסירה `stopped`, הפעילה מחדש את FGS ושחזרה/מסרה את שתי ההתראות בפספוס; מאז alarmClock של 17:00 והתראות נוספות עובדים. זו אינה בעיית Doze או הרשאות התראה. נדרש איתור מקור ה־force-stop של OnePlus/system או הימנעות מהפעולה; קוד לא שונה באבחון זה.
- תגובתיות 07/09: פעולות „בוצע”/דחייה במסך הבית ובמסך ההתראה הרגיל סוגרות את הממשק מיד ומבצעות את עיבוד היסטוריית התזכורות, ביטולי AlarmManager ורענון complications ב־worker יחיד. כך נמנעת חסימת UI על היסטוריה גדולה. רענון השעון המעוגל מתוזמן לגבול הדקה הבא (אחרי שניות `00`) ולאחר מכן כל דקה. כפתורי „לוגים לטלפון” ו„ניקוי לוגים” הוגדרו לשתי שורות, ללא אייקון. build/tests עברו והגרסה הותקנה; אימות פעולת התראה פיזית עדיין דרוש.
- שיפור ביצועים 07/09: הוסר רענון חסר־תועלת של השעון המעוגל כל 5 שניות (כעת מתעדכן בתחילת כל דקה), והוסרו צללי software יקרים מהכפתורים. כרטיסי התזכורות הזוהרים נשארו בדיוק בעיצוב המקורי שלהם, כולל blur ושכבת software. רק מסך הבית וזמני היום טוענים את רקע ה־bitmap; מסכי הגדרות ועורך משתמשים ברקע רגיל. build ו־unit tests עברו; הגרסה הותקנה על OnePlus Watch 3. מדידת post-install הראתה 27 Views, ללא slow bitmap upload; השירות נשאר FGS ב־adj=50 ו־cached=false לאחר יציאה למסך השעון.
- שינוי 07/09: התראת שתייה קיבלה גלילה מלאה יותר עם padding תחתון ו־`clipToPadding=false`, כך שכפתור הפעולה התחתון נגיש.
- שינוי 07/09: שחזור גיבוי/patch מהטלפון רץ כעת ב־`RESTORE_EXECUTOR` ולא ב־main thread. בזמן השחזור מוצג `ProgressDialog` בלתי ניתן לסגירה, המסך מוחזק דלוק זמנית, ובסיום/שגיאה ה־UI מתעדכן בבטחה. `:app:testDebugUnitTest :app:assembleDebug` בבדיקה.
- כלל עבודה: לאחר כל שינוי במשימה זו יבוצעו build, commit ו־push ממוקד לקבצים ששונו; אין לכלול שינויים לא קשורים שכבר קיימים ב־worktree.

- שינוי 07/09: `ReminderMonitoringService` החליף את `ReminderForegroundService`. זהו FGS מסוג `specialUse` בתת־סוג `wear_os_reminder_monitoring`, עם התראה שקטה ב־IMPORTANCE_LOW. הוא מתחיל רק כאשר מתג הניטור פעיל וקיימת לפחות תזכורת פעילה; הוא מפסיק ומסיר רק את תחזוקת השירות כאשר התנאים אינם מתקיימים.
- השירות מבצע אימות מיידי אחד ולאחר מכן בדיקת תקינות קלה בערך פעם בשעה באמצעות `Handler.postDelayed` פנימי בלבד; הסריקה מתבצעת ב־worker ואין Alarm/WorkManager שמקים את המעבד, WakeLock, loop, חיישנים, מיקום או רשת. watchdog אינו נרשם מחדש אם לא תוקן AlarmClock. `AlarmManager.setAlarmClock` ממשיך למסור את התזכורות.
- `ReminderMonitoringState` שומר את זהות ותאריך ה־AlarmClock הבא ואת נתוני בדיקת הסוללה. scheduler אינו רושם מחדש אזעקה תקינה; אם ה־PendingIntent המתועד חסר הוא יוצר אותו מחדש. לוגים מיוצאים כוללים running/start/checks/repairs/last/next/active. recovery ב־Job משחזר רק alarms (אין לו חריגת FGS); פתיחת UI או BootReceiver המקורי מפעילים FGS במסלול מותר. `:app:testDebugUnitTest :app:assembleDebug` עבר.
- אימות פיזי 07/09: לאחר התקנה ויציאה ל־idle ב־OnePlus Watch 3, `ReminderMonitoringService` היה `isForeground=true`, ערוץ `reminder_monitoring` הוצג ללא קול/רטט, `stopped=false`, מצב `FGS`, `adj=50`, `cached=false`. AlarmClock לתזכורת 11:15 נשאר רשום; health check של 29 תזכורות הסתיים `repaired=false`. זוהתה תחילה התחלת FGS אסורה מתוך `ReminderRecoveryJobService` ב־background ואחריה force-stop ב־adj 900; תוקן בכך שה־Job אינו מתחיל FGS. לאחר התיקון לא נראה ProcessController force-stop בחלון הבדיקה.

- תיקון 06/09: אזור הלוגים שהיה קיים בטלפון אך לא צורף למסך ההגדרות חובר מחדש, כולל הצגת לוגים, שיתוף, העתקה ובחירת קובץ. התראות מים עוברות כעת דרך `QuietTimeHelper` בתזמון רגיל, snooze ובמסירה בפועל; אם האזעקה נמסרת בתוך זמני שקט היא נדחית לסיומם. מסך התראת המים עטוף ב־ScrollView כדי שכל הפעולות, כולל snooze, יישארו נגישות. תצוגת פרשת השבוע תומכת כעת בלוח שבת עתידי אמיתי במקום להעביר formatter ליום ללא פרשה, ומציגה חג או fallback שאינו ריק. `./gradlew test assembleDebug` עבר.

- חדש 06/09: נוצר פרויקט עצמאי `Mykvah/` לתשתית התראות Wear OS בלבד. הוא משתמש ב-package/namespace `com.woodpeckerbros.mykvah`, כולל AlertStore/Scheduler/Receiver, recovery אחרי reboot/update/time change, watchdog, full-screen notification, snooze, duplicate guard ו-placeholder Activity. לא בוצע שינוי בקוד Zmanio.
- Mykvah כולל KosherJava `com.kosherjava:zmanim:2.5.0` באותה דרך כמו Zmanio. ה-Manifest מוסיף רק POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, USE_FULL_SCREEN_INTENT, RECEIVE_BOOT_COMPLETED, WAKE_LOCK ו-VIBRATE; אין FGS/DND/sensors/location.
- Mykvah משתמש ב-exact alarms כאשר `canScheduleExactAlarms()` מאשר זאת, וב-fallback חוקי `setAndAllowWhileIdle` אחרת. ה-receiver יוצר notification בערוץ HIGH עם full-screen intent; כל מחזור מתועד ב-Logcat תחת `MykvahAlerts`.
- Mykvah tests/build: `testDebugUnitTest` ו-`assembleDebug` עברו עם Java 17; APK: `Mykvah/app/build/outputs/apk/debug/app-debug.apk`. עדיין לא הותקן על שעון.
- Mykvah מבקש `POST_NOTIFICATIONS` בזמן פתיחת ה-placeholder ב-Android 13+; build/test חוזר עבר לאחר השינוי.

- תיקון קריטי 06/09: לוגי 07:25 הראו ששתי התראות רגילות הגיעו ברצף, ו„סולה” נפתחה ב־07:25:11 ונלחצה „בוצע” ב־07:25:14. מנגנון הרטט השתמש קודם ב־waveform אינסופי (`repeat=0`) והסתמך על `cancel()` בלבד; הוא הוחלף למקטעים סופיים של עד 2 שניות שמתחדשים רק כשההתראה פעילה. גם הצליל אינו `looping` אינסופי יותר: הוא מסתיים טבעית ומתנגן מחדש רק עד סוף ההתראה, והתראה חדשה עוצרת צליל קודם. לכן כשל cancel/תהליך אינו יכול להשאיר צליל או רטט אינסופיים. נוספו לוגי start/stop, ו־Smart Alarm auto-snooze עוצר במפורש את feedback של ה־Activity לפני סגירה. build/tests עברו; ה־APK הותקן ונפתח על השעון וה־recovery התחיל.
- תיקון 06/09: התראות צום לסירוגין מנרמלות כעת כותרת/הודעה לפי שפת האפליקציה בתוך ה־receiver ובמסך עצמו, ולכן טקסטים כגון Minutes Remaining / The eating window אינם נשארים באנגלית במצב עברית. התראת מים קיבלה כפתור „הזכר לי בעוד 15 דקות” עם תזמון snooze אמיתי, והפעולות עברו לפריסה אנכית קומפקטית כדי למנוע חיתוך. במסך זמני היום נוסף fallback לשבת ללא פרשה: שם חג אם יש חג, או „שבת רגילה”; כפתור הדחייה בדף היומי קיבל margins/padding מצומצמים. `./gradlew test assembleDebug` עבר.
- שיפור 06/09: ניטור Smart Wake מתחיל כעת לפחות 16:15 דקות לפני חלון ההשכמה (11:15 דקות baseline מלאות + buffer של 5 דקות, ועד 15 דקות לפי קצב HR דליל שנמדד בלילות קודמים), במקום 11:15 דקות ללא מרווח. בתחילת החלון יש exact checkpoint שמעריך ורושם `baselineReady`; כשל אמיתי (למשל שירות שלא עלה או חוסר samples תנועה) נרשם במפורש. telemetry summary כולל baseline sample counts, גיל HR/HRV וכיסוי חלון תנועה, בנוסף לציון/קבוצות/מועמד/Clearly Awake/החלטה. הספים 23/42 והמשקלים לא השתנו; `./gradlew test assembleDebug` עבר.
- אבחון 06/09: בלילה 05–06/09 Smart Alarm התחיל ניטור ב־06:13:45 לחלון 06:25–07:10. עד 06:24:45 baseline היה `false`, ולכן scores 14/19/14/24 לא יכלו להפעיל מועמד; מ־06:30 ועד 07:09 לא נרשם מועמד או Clearly Awake, ללא צעדים וללא מעבר UserActivity טרי. ב־07:10 ההתראה הקשיחה הופעלה והרטט התחיל, אך Full-Screen לא היה visible; ב־07:15:30, לאחר snooze, המסך נפתח ואושר. הלקח: להבטיח baseline מוכן לפני תחילת החלון, להמשיך למדוד את פערי הסנסורים, ולבדוק בנפרד את מסלול פתיחת המסך.
- שיפור 06/09: כל evaluation של Smart Wake נרשם בנוסף בשורת summary קצרה בפורמט timestamp → WAKE_SCORE → groups → candidate → clearly_awake → decision, לפני הטלמטריה המפורטת, כדי לאפשר סקירת לילה מהירה. `./gradlew test assembleDebug` עבר.
- תיקון 04/09: כותרת התראת פתיחת חלון האכילה משתמשת כעת ב־localized context של האפליקציה, ולכן במצב עברית מוצג „אפשר להתחיל לאכול” גם כאשר שפת מערכת השעון באנגלית. `./gradlew test assembleDebug` עבר.
- שינוי 04/09: Smart Wake כולל כעת שני מסלולים נפרדים: Wake Opportunity בספים 23/42 עם זיכרון מועמד ל־90 שניות ואישור המשך מציון 12, ו־Clearly Awake בוליאני לצעדים/תנועה מתמשכת/מעבר UserActivityState. צעדים בלבד או חיישן תנועה יחיד בלבד אינם מעירים בלי signal מאשר. הטלמטריה מפרטת את שני המסלולים, מקטעי התנועה, span, צעדים ומעבר activity טרי; כל 30 בדיקות היחידה ו־`test assembleDebug` עברו.
- תיקון 04/09: שירות הצליל/רטט של Smart Alarm מתחיל כעת מיד בתוך exact-alarm receiver, לפני שפגה הרשאת הרקע הזמנית; כך נשמר feedback גם אם Bedtime/DND של OnePlus מדכא Full-Screen Intent. לאחר 2 שניות נרשם אם המסך אכן הופיע.
- תיקון 04/09: Smart Wake recovery שימר כעת חלון ניטור פעיל במקום לתזמן אותו מחדש בזמן המדידה; גם delivery כפול אינו מאפס את מחזור ההערכה. כך נמנעים פערים ב־telemetry ובזיהוי הזדמנות התעוררות. ספי 25/45 לא שונו.
- תיקון 03/09: אייקון „זמנים יהודיים” בהגדרות הוחלף למגן דוד; „זמני היום” משתמשים בסמל זריחה מעל אופק, וצום לסירוגין נשאר עם שעון.
- תיקון 03/09: כפתור המים נקרא כעת "תזכורת לשתיית מים" / "Water Reminder" ומציג אייקון טיפת מים במסך הראשי ובהגדרות.
- שינוי 03/09: אזור הלוגים הוחזר למסך ההגדרות עם שליחה לטלפון וניקוי לוגים, כולל תרגום עברית/אנגלית.

קראו קודם רק את הסעיף הזה. קראו את `FULL PROJECT STATUS` רק אם המשימה דורשת פרטים נוספים; קראו את `PROJECT_HISTORY.md` רק כאשר היסטוריה רלוונטית.

- פרויקט: WatchReminder / Zmanio — אפליקציית Wear OS לתזכורות, זמני הלכה וגיבוי מול טלפון.
- נתיב פעיל: `/Users/refaelnakar/Documents/Work/TEMP/Android Temp AI Workspace/WatchReminder`
- מודולים: `:app` לשעון ו־`:phone` לגיבוי/שחזור בלבד; מסירת התזכורות עצמאית על השעון ואינה תלויה בטלפון.
- מצב נוכחי: תזכורות רגילות, AlarmManager, Smart Alarm, נודניק, גיבוי ו־complications פעילים.
- מכשיר בדיקה: OnePlus Watch 3 פיזי ב־ADB אלחוטי; אמולטור Wear זמין לפי הצורך.
- שורש תקלה קריטית 01–02/09: לאחר שחזור כ־35 תזכורות, `MainActivity` ביצעה catch-up כבד על ה־UI thread. במקביל עבר הפוקוס מ־`ReminderAlertActivity` לדף היומי, נוצר ANR של 5 שניות, ואז Android ביצע `FORCE STOP` שמחק את כל ה־alarms.
- תיקון 02/09: בדיקת foreground הועברה ל־worker, `BootReceiver` משתמש ב־`goAsync`, ודף יומי/עומר נדחים בדקה כאשר תזכורת רגילה פעילה. בכך נמנעים main-thread ANR ומסכי full-screen מתחרים.
- מצב השעון: גרסת debug ‏1.16 מותקנת, `stopped=false`, Full-Screen Intent=`allow`, והחבילה ב־Doze allowlist. הנתונים ו־35 התזכורות נשמרו.
- אימות פיזי 02/09: שתי תזכורות catch-up הוצגו ברצף; בדיקת `setAlarmClock` העירה את השעון משינה עמוקה ב־00:42:09 ופתחה את מסך ההתראה. אין ANR חדש; תזכורת 07:15 ו־watchdog 07:17 רשומים.
- סיכון מרכזי: force-stop אמיתי חוסם את החבילה היחידה ואת כל מנגנוני Android שלה, כולל FGS ו־AlarmManager; אפליקציה רגילה אינה יכולה לעקוף זאת באותו package. OnePlus עדיין עלול לחסום רטט בזמן Bedtime/DND.
- החרגת סוללה: קיימת בקשת Android בקוד; ב־OnePlus `FakeSettingsActivity` אינה מציגה אישור. בשעון הבדיקה ההחרגה הוחזרה דרך Doze allowlist של ADB.
- Build: `./gradlew :app:testDebugUnitTest :app:assembleDebug`; לטלפון `./gradlew :phone:assembleDebug`.
- התקנה: `adb install -r app/build/outputs/apk/debug/app-debug.apk`, ואז לפתוח את `com.woodpeckerbros.watchreminder/.MainActivity`.
- כללי בטיחות: אין לשנות מבנה, package name, Gradle/AGP/SDK או dependencies ללא אישור; לא לבצע force-stop בלי לשחזר alarms.
- שינוי אחרון: אבחון `ApplicationExitInfo` ו־`lastanr` הוכיח שה־force-stop הגיע אחרי ANR בזמן catch-up, ולא ממנהל הסוללה. התיקון נבנה, הותקן ונבדק על השעון.
- שינוי אחרון (01/09): כל גיבוי חדש נשמר אוטומטית ב־`Downloads/Zmanio`; הוסר כפתור `שמירה` המיותר כדי למנוע עותקים כפולים. הסריקה כוללת MediaStore, fallback ל־Downloads וסריקה ישירה של תיקיות ישנות.
- שינוי 03/09: complications של תזכורת לברכה, זמני היום ותאריך עברי דורשים כעת מצב יהודי. בבחירה או בלחיצה כשהוא כבוי מוצג מסך הפעלה מפורש; שינוי מצב/שחזור מרעננים את כל ה־complications.
- שינוי 03/09: בוטלה בקשת החרגת אופטימיזציית הסוללה, בדיקתה וההרשאה המתאימה; מנגנוני התזמון האחרים נשארו ללא שינוי.
- שינוי 03/09: תוקנו תרגומי מסך השעון המעורר באנגלית, ומספר הגרסה בהגדרות תורגם והועבר לתוך התוכן הנגלל עם מרווח תחתון קטן.
- שינוי 03/09: כפתורי הגיבוי במסך ההגדרות הורחבו וגובהם הוגדל; כיתוב Restore from Phone באנגלית נשבר לשתי שורות כדי שלא ייחתך.
- תיקון 03/09: מסך הפעלת מצב יהודי עבור complications משתמש כעת בעיצוב כפתורי Zmanio וב־ScrollView, כך שהכפתורים אינם מוצגים ככפתורי מערכת לבנים.
 - שינוי 03/09: טבלת 436 תרגומי הממשק שהייתה בתוך `UiText.java` הועברה למשאבי `strings.xml` בעברית ובאנגלית; `UiText` משמש כעת רק כגשר למשאבים. build ו־unit tests עברו. נשאר audit נפרד לטקסטים דינמיים שנבנים בתנאי Java ודורשים placeholders/plurals.
- שינוי אחרון נוסף (01/09): סומן `BIND_LISTENER` כחריגת Lint מאושרת בשני ה־Manifest-ים; הפעולה נשארה כדי לא לשבור את קבלת הודעות Wearable. שני מודולי debug נבנו בהצלחה.
- שינוי אחרון נוסף (01/09): דף יומי שפוספס ביום קודם מוצג כעת מיד ב־`dispatchIfDueNow`, גם אם שעת ההתראה של היום טרם הגיעה; לפני כן הקוד המתין להתראה הבאה.
- אבחון 01/09 13:56: עדכון APK/force-stop הותיר את האפליקציה `stopped=true` וביטל את כל ה־AlarmManager entries ב־13:55. לאחר פתיחת האפליקציה השחזור הפעיל מיד תזכורות שפוספסו (13:00, 13:20), ורשם מחדש אזעקה ל־17:00, watchdog ו־recovery job; exact alarms, התראות ו־full-screen תקינים. בכל התקנה יש לפתוח מיד את `MainActivity` כדי להסיר `stopped` ולשחזר תזמונים.
- שינוי 03–04/09: Smart Wake מכוון ל־wake opportunity עם baseline אישי, ובנפרד מזה מפעיל Clearly Awake כשהמשתמש כבר קם. הניטור מתחיל 11:15 דקות לפני החלון; HR/HRV משתמשים ב־10% trimmed mean/std ותנועה ב־median של מקטעי 30 שניות. קבוצות הראיות הן Cardiovascular, Movement ו־UserActivityState, כאשר `ASLEEP` ניטרלי. Wake Opportunity: מועמד מ־23, אישור המשך מ־12 בתוך 90 שניות, ומיידי מ־42 עם Cardiovascular+Movement. מעבר ASLEEP→non-ASLEEP מקבל +10. telemetry מפורט לכל הערכה והפעלת ניטור חוזרת אינה מאפסת היסטוריה.
- תיקון 03/09: ה־Intermittent Fasting complication משתמש כעת ב־`AppLanguage.wrap`, כך שהטקסטים וה־preview מכבדים את שפת האפליקציה גם כאשר שפת מערכת השעון שונה. `:app:testDebugUnitTest :app:assembleDebug` עברו.
- תיקון 03/09: במסך הגדרות צום לסירוגין כפתורי הפעולה "סיימתי לאכול" ו־"התחלתי לאכול" מוצגים כעת בשתי שורות, ללא אייקון שתופס רוחב, כדי למנוע גלישת טקסט בעברית; `:app:testDebugUnitTest :app:assembleDebug` עברו.
- תיקון 03/09: גם כפתורי הזמן "זמן סיום" ו־"זמן התחלה" במסך הצום עוברים כעת לפריסה דו־שורתית ללא אייקון, כדי למנוע גלישה; ה־build והבדיקות עברו.
- תיקון 03/09: תוקנו פריסות ותרגומי UI באנגלית: סימן העזרה עבר לימין, כרטיסי Coming up מציגים טקסט משמאל ושעה מימין, זמני היום נשברים לשתי שורות, תוויות Smart Alarm ו־History מתורגמות, כפתורי הצום והכותרת Vibration and Sounds אינם נחתכים, והשם החדש של זמן שקט מתחיל ריק. build ובדיקות עברו.
- תיקון 03/09: כפתור Configure במסך בחירת אופן כיבוי השעון המעורר משתמש כעת בתרגום ייעודי באנגלית/עברית; build ובדיקות עברו.
- תיקון 03/09: תוקנה פריסת כרטיסי Coming up בעברית כך שהשעה בצד שמאל והטקסטים בצד ימין, בעוד שבאנגלית נשמר הסידור ההפוך; build ובדיקות עברו.
- שינוי 03/09: מספר הגרסה הועבר ממסך ההגדרות למסך אודות ורישיונות, ונוסף כפתור בדיקת עדכון שפותח את דף Zmanio ב־Google Play עם fallback לדפדפן; build ובדיקות עברו.
- תיקון 03/09: נוסף מרווח אופקי לכפתור „חזרה” בתחתית ההגדרות ונוסף מרווח גלילה תחתון, כדי שלא ייחתך בקצוות או בתחתית; build ובדיקות עברו.
- פיצ׳ר 03/09: נוספו תזכורות שתיית מים בהגדרות עם מצב יעד יומי המחלק ומתאים מחדש את היתרה, מצב כמות קבועה, חלון שעות ומרווח 30–240 דקות, מעקב/איפוס יומי, התראת מים ייעודית עם סימון שתיתי/דלג, שחזור אחרי reboot ושילוב מלא בגיבוי וב־phone patch. תרגומים עברית/אנגלית, unit tests ו־build לשעון/טלפון עברו.
- פיצ׳ר 03/09: נוסף complication של שתיית מים עם אייקון טיפה והתקדמות בשורה אחת (למשל `0.8/2L`), וב־LONG_TEXT כמות מלאה במ״ל. במצב יעד קבוע המכנה הוא היעד היומי ובמצב כמות קבועה הוא סך השתייה המתוכנן; לחיצה פותחת את הגדרות המים והרענון מתבצע לאחר שינוי/שתייה/איפוס. build ובדיקות עברו.
- תיקון 03/09: כאשר תזכורות המים פעילות, נוסף גם כפתור תזכורות המים למסך הבית מיד מתחת לכפתור צום לסירוגין; כשהן כבויות הוא מוסתר. build ובדיקות עברו.
- תיקון 03/09: ה־Water complication מציג בשורה השנייה את שעת התזכורת הבאה בפורמט הקומפקטי `→14:00`; ב־LONG_TEXT השעה מצטרפת לפירוט ההתקדמות. build ובדיקות עברו.
- משימה מיידית: להתקין את build ‏1.18 על OnePlus Watch 3, להריץ Smart Alarm קצר ולבחון את ה־SmartWake telemetry מהחלון האמיתי; לא לבצע force-stop.
- שינוי 02/09: במסך יצירת Smart Alarm חלון ההשכמה החכם הועבר בין שעת ההשכמה לימי הפעילות; כיבוי ללא בדיקת ערנות מבטל נודניקים ובדיקות ערנות ממתינות ומסמן את המופע כבוצע. `BODY_SENSORS_BACKGROUND` הוסר כי אינו בשימוש או מבוקש.
- שינוי 02/09: נוספה בקשת משתמש אופציונלית ל־Do Not Disturb policy access רק בשעונים שחושפים את מסך המערכת המתאים. לאחר אישור ידני ערוץ Smart Alarm החדש רשאי לבקש עקיפת DND; OnePlus Watch 3 אינו חושף מסך זה, ובו Bedtime כבר מאפשר אזעקות אך עדיין עלול לחסום רטט ברמת היצרן.
- שינוי 02/09: לפני הצגת כל בקשת גישת־מערכת מיוחדת (DND, exact alarms, מסך מלא והחרגת סוללה) האפליקציה בודקת שמסך המערכת הייעודי קיים. אם אינו קיים, הבקשה אינה מוצגת למשתמש.
- תיקון 02/09: ה־recovery job המחזורי היה מבטל Smart Alarm שנמצא בנודניק ומחשב בטעות את המופע הבא למחרת. כעת recovery שומר נודניק עתידי פעיל ורושם אותו מחדש, או משאיר התראה פעילה ללא דריסה; רק מופע לא פעיל מתוזמן מחדש.
- תיקון 02/09: לכל מופע משוב התראה יש בעלות ייחודית על מנוע הרטט. שירות גיבוי ישן שמסתיים לאחר פתיחת מסך Smart Alarm אינו יכול עוד לבטל את הרטט החדש; זה מונע מרוץ שאובחן במיוחד במעבר משירות ההתראה למסך בזמן שינה.
- שינוי 02/09: גיבוי הרטט האופציונלי חזר למנגנון שעבד ב־OnePlus Watch 3: בסיום התראת Smart Alarm שלא טופלה, Zmanio סוגרת את משוב ההתראה שלה ומפעילה טיימר מערכת חד־פעמי של שנייה דרך `ACTION_SET_TIMER`. הטיימר פועל לפי צליל/רטט מערכת; ניסוי `ACTION_SET_ALARM` הוסר משום שפתח Resolver או מסך הוספת אזעקה במקום פעולה שקטה.

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
- החבילה לבדה אינה יכולה להתאושש מ־force-stop אמיתי; המיקוד הוא במניעה: שמירת FGS חוקי, AlarmClock, recovery לאחר boot/update/time, watchdog, due-check ותיקון כל נתיב שעלול לגרום ל־ANR או לסגירה יזומה.
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
