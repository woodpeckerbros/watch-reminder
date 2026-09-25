# QUICK RESUME

- שפת התראות ו־Smart Wake ‏25/09: `UiText` פותר כעת גם טקסט שנוצר מ־BroadcastReceiver דרך שפת האפליקציה השמורה, ולא דרך שפת המכשיר; לכן התראת „ימים יהודיים” תציג „ימים יהודיים” ו„היום” בעברית כשהאפליקציה בעברית. במקביל, fallback ‏`WAKE_ALREADY_AWAKE` מזהה episode של Health Services לפי `state + stateChangeTime`, אינו סופר deliveries כפולים של `PASSIVE`, ודורש ראיית ערות עצמאית; `EXERCISE` טרי נשאר ראיה חזקה. נוספו בדיקות episode/replay. `:app:testDebugUnitTest :app:assembleDebug` ו־`git diff --check` עברו; commit/push יבוצעו כעת לפי בקשת המשתמש.
- אבחון בוקר ומחיקת קבצים ‏25/09: הלוג הראה שה־Smart Alarm לא קרס אלא הופיע ב־07:13, עבר שלושה נודניקים אוטומטיים ללא פעולת משתמש ומיצה אותם ב־07:30. „לשתות סולה” ו„להאכיל את הדגים” לא היו התראות שהוחמצו: הן תוזמנו רגיל ל־07:15 ול־07:25 ולכן נוצר פער של 10 דקות. מחיקת notification חיצונית משחררת כעת מיד את הפריט הבא בתור. באפליקציית הטלפון נוספו מחיקה בודדת ומחיקת כל הרשימה, עם אישור, ללוגים ולגיבויים. `:app:testDebugUnitTest :app:assembleDebug :phone:assembleDebug` עברו; אין התקן ADB מחובר.
- תור התראות שהוחמצו ‏24/09: כל מופע שהוחמץ נשמר כעת באופן אטומי במחסנית, מסודר כרונולוגית, וכל המופעים של אותו `reminderId` מתאחדים גם אם אחד מהם הגיע מנודניק; המסך מציג „פוספסו N התראות כאלה”. בעת חזרה לענידה/ערנות, הפריט הראשון מוצג לפני פעולות תחזוקה, ובפעולת משתמש הפריט הבא משתחרר מיד — בלי להמתין לכתיבת ההיסטוריה ברקע. נוספו בדיקות לאיחוד המופעים. `:app:testDebugUnitTest :app:assembleDebug` עברו; אין התקן ADB מחובר. commit/push: `7848161`.
- תכנון שתיית מים מותאם ‏23/09: במסך המים יש כעת בחירה מפורשת בין יעד+מרווח (כמות אוטומטית) לבין יעד+גודל כוס (מרווח אוטומטי, מיושר ל־15 דקות). היעד משותף לשני המסלולים, הכוס האחרונה מותאמת ליתרה והתזכורות נעצרות בהגעה ליעד. נוספה הוספה ידנית של 100–1000 מ״ל בקפיצות של 100, וכל רכיבי ההתקדמות מתעדכנים במקום. `:app:testDebugUnitTest :app:assembleDebug` עברו; התקנה על האמולטור נחסמה בגלל חתימת APK שונה ולא בוצעה מחיקה של נתוניו. commit/push: `cb4d5ed`.
- מסך מים אחוד ‏23/09: `WaterProgressActivity` הוסר; הכד המונפש, אחוז ההתקדמות, נשתה/נותר, הוספת כוס ואיפוס מוצגים בראש מסך הגדרות תזכורות המים. כל פעולה מעדכנת את רכיבי התצוגה במקום ללא יצירה מחדש של המסך או אובדן גלילה. ה־complication וכפתור המים חוזרים לאותו מסך. `:app:testDebugUnitTest :app:assembleDebug` עברו וה־APK הותקן באמולטור Wear OS. commit/push: `3cd5e96`.
- ברירת מחדל לתזכורת שתיית מים ‏23/09: `DEFAULT_WATER_INTERVAL_MINUTES` שונה מ־120 ל־60 דקות. הגדרה קיימת של משתמש נשמרת; השינוי חל כברירת מחדל למשתמשים שטרם שמרו מרווח. commit/push: `7c8763a`.
- שתיית מים ‏23/09: יעד יומי מחלק כעת כמות קבועה לאורך חלון השתייה ואינו מנסה "להשלים פערים" על ידי הגדלת הכוס בהתראה הבאה; הכוס האחרונה רק קטנה למה שנותר. נודניק 15 דקות מבטל במפורש את ההתראה המחזורית הקודמת, מתזמן תזכורת יחידה חדשה ומתעד requested/scheduled/quiet-adjustment בלוג. נוסף `WaterProgressActivity` עם מיכל מים מונפש, אחוז, נצרך/נותר והוספת כוס; נפתחת מהבית ומה־complication, והגדרות נשארות נגישות ממנה. `:app:testDebugUnitTest :app:assembleDebug` ו־`git diff --check` עברו; אין מכשיר ADB מחובר להתקנה.
- איחוד מסכי התראות יהודיות ‏23/09: נוסף `JewishAlertBaseActivity` כבסיס המשותף לדף היומי, עומר, ברכת הלבנה והתראות ימים יהודיים/תקופה. הוא מחזיק את כל המעטפת — שפה, מסך נעול, מסגרת, שעון עליון, מגן דוד, טיפוגרפיה, כפתורים ו־`ScrollView`; כל מסך בן מספק רק טקסטים ופעולות ייחודיות. `:app:testDebugUnitTest :app:assembleDebug` עברו. commit/push עדיין חסומים מקומית כי אין הרשאה ליצור `.git/index.lock`.
- גלילה במסכי התראות יהודיים ‏23/09: דף היומי וספירת העומר משתמשים כעת ב־`ScrollView` אמיתי עם תוכן בגובה טבעי וכפתורים בגובה קבוע, כך שטקסט ארוך אינו נחתך במסך העגול וניתן לגלול לכל התוכן. מסכי ימים יהודיים וברכת הלבנה כבר השתמשו בגלילה. `:app:testDebugUnitTest :app:assembleDebug` עברו; התקנה לאימות באימולטור לא בוצעה כי ADB לא הצליח להפעיל daemon (`Operation not permitted`).
- Smart Wake self-stimulus contamination ‏23/09 (ממתין לסקירת המשתמש, ללא commit/push): התראות פיזיות או full-screen של Zmanio מסמנות דגימות חיישן כ־tainted במקום להפסיק איסוף. צעדים מתנקים אחרי 60 שנ׳, תנועה ו־HR אחרי 75 שנ׳ — בדיוק חלונות הראיות שלהם. הדגימות המסומנות אינן יכולות ליצור `clearly_awake`, לאשר candidate או להזין trend; ההתראה עצמה מתועדת עם type/occurrence/start/end וטלמטריה ייעודית. רגרסיית 07:15 (5 צעדים ותנועה 55 שנ׳ אחרי תזכורת) נשארת `CONTINUE`; 07:09, temporal wake וה־deadline נשמרים בבדיקות. `:app:testDebugUnitTest :app:assembleDebug` עברו.
- איחוד עיצוב התראות יהודיות ‏23/09: דף היומי וספירת העומר עברו גם הם לעיצוב החדש עם מסגרת/שעון עליון, מדליון אייקון יהודי וכפתורי עומק; יחד עם ברכת הלבנה, ימים יהודיים, צום ותקופה אין עוד מסך התראה יהודי פעיל בעיצוב הישן. `:app:testDebugUnitTest :app:assembleDebug` עברו וה־APK הותקן באימולטור.
- עיצוב ברכת הלבנה ‏23/09: מסך `MoonBlessingAlertActivity` האמיתי עבר לאותו עיצוב חדש של ההתראות היהודיות — מסגרת/שעון עליון, מדליון אייקון יהודי, כותרת מודגשת וכפתורי עומק — תוך שמירת פעולות בוצע/כן/לא, דחייה וסגירה אוטומטית. שכבת הרקע המשותפת נפתחה לשימוש בין מסכי ההתראה. `:app:testDebugUnitTest :app:assembleDebug` עברו וה־APK הותקן באימולטור.
- זמני היום ואבחון קריאת שמע ‏23/09: פתיחת זמני היום ביום הנוכחי גוללת אוטומטית לשורה שזמנה הכי קרוב לעכשיו ומדגישה אותה ברקע ירוק־כהה, מסגרת זהובה ונקודה זהובה; בתאריכים אחרים נשארים בראש המסך. לוג 22/09 הוכיח שהתזכורת החד־פעמית „קריאת שמע בזמנה | עד 20:48” נוצרה ב־19:43:58, ללא שחזור או תזמון יהודי אוטומטי; לפי הקוד, שם זה נוצר רק דרך מסלול מסך „תזכורת לברכה”. תזכורת גם אחרי הזמן נשארת נתמכת לפי בקשת המשתמש, ונוסף לוג מקור פעולה מפורש לעתיד. `:app:testDebugUnitTest :app:assembleDebug` עברו וה־APK הותקן באימולטור.
- עידון כותרת התאריך ‏22/09: כותרת זמני היום קומפקטית יותר; יום השבוע, התאריך העברי והתאריך הלועזי מוצגים כעת בשלוש שורות נפרדות, עם פחות ריפוד ומרווחים. `:app:testDebugUnitTest :app:assembleDebug` עברו וה־APK הותקן באימולטור.
- תיקון תצוגת חגים ‏22/09: במסך זמני היום יום חג מקבל כעת שורה ייעודית כמו „חג: סוכות”, גם כאשר החג חל בשבת, ומתחתיה זמני כניסת החג, צאת החג וצאת ר״ת. הלוגיקה תומכת בראש השנה, פסח, שבועות, סוכות, שמיני עצרת ושמחת תורה, כולל רצף ימי חג. `:app:testDebugUnitTest :app:assembleDebug` עברו וה־APK הותקן באימולטור.
- עיצוב כותרת זמני היום ‏22/09: יום השבוע והתאריך מוצגים כעת בתוך כותרת נפרדת ומעוגלת, עם קו זהב עדין ורווח ברור לפני סטטוס התחנון, הצום ושאר הנתונים.
- סדר זמני היום ‏22/09: במסך זמני היום הסדר הוא כעת תאריך, מיד אחריו „לא אומרים תחנון” אם רלוונטי, אחריו צום/ערב צום/ערב חג, ורק לאחר מכן הפרשה ושאר הנתונים.
- תיקון כלל תשרי ‏22/09: לפי הבהרת המשתמש, עשרת ימי תשובה עצמם אינם מסומנים אוטומטית כ״לא אומרים תחנון״. `TefilaRules.setTachanunRecitedEndOfTishrei(false)` מוגדר כעת כך שלא אומרים תחנון מהיום שאחרי יום כיפור ועד סוף חודש תשרי; ימים ג׳–ז׳ בתשרי חוזרים להציג אמירת תחנון, עם חריגי צום/חג של `TefilaRules`. הבדיקות עודכנו, `:app:testDebugUnitTest :app:assembleDebug` עברו, וה־APK הותקן באימולטור.
- תיקון תחנון ‏22/09: במסך זמני היום מוצג כעת רק הכיתוב המרכזי האדום „לא אומרים תחנון” ללא שורת „תחנון”. חישוב התחנון משתמש ב־`kosherjava` `TefilaRules`, כולל חריגי הצומות והחגים וכלל סוף תשרי שהוגדר בהמשך. נוספו בדיקות. `:app:testDebugUnitTest :app:assembleDebug` עברו.
- תיקון תצוגת יום בשבוע ‏22/09: במסך זמני היום יום השבוע מוצג כעת בשורה נפרדת, ממורכזת, עם „יום ג׳” בעברית או `Day Tue` באנגלית; התאריך נשאר בשורה שמתחת. `:app:testDebugUnitTest :app:assembleDebug` עברו.
- עדכון זמני היום ואמינות התראות 22/09: תאריך זמני היום כולל יום בשבוע לפי שפת האפליקציה; ערב חג או צום מציג את שם הערב ואת כניסה/יציאה/ר״ת (והדלקת נרות בחג), ומוצגים „ל״א תחנון” ותיקון חצות של הלילה לפי כללי לוח ספרדיים בסיסיים. ״ניעור היד״ הוחלף בסיבובי פרק יד ימינה ושמאלה המצריכים שינוי כיוון בג׳ירוסקופ, עם אנימציית הדרכה. שומר מסך ה-Smart Alarm ממתין 12 שניות לאחר הסתרה רגעית לפני יצירת מסך חדש לאותו occurrence; מסירת תזכורות מסודרת תחת נעילה אחת כדי למנוע כפילויות בין boot, watchdog וענידה. לוגי deadline כוללים `SMART_WAKE_DEADLINE_FALLBACK`. הלוג מ-22/09 הראה שלא הייתה הזדמנות השכמה מאושרת עד 07:15, ושכפול מסך נגרם מ-screen guard אחרי איבוד פוקוס. `:app:testDebugUnitTest :app:assembleDebug` עברו (134 בדיקות); התקנה פיזית עדיין נדרשת.
- עיצוב התראות יהודיות ‏22/09: מסכי המידע היהודיים הותאמו לעיצוב ההתראה החדש עם רקע מדורג, מסגרת זהובה, שעון בקשת העליונה, כפתורי עומק ומדליון מגן דוד מוזהב במקום פעמון; גם ברכת הלבנה משתמשת באייקון החדש. הגדרות ה־debug המקוריות שוחזרו לצד כלי הדמו. `:app:testDebugUnitTest :app:assembleDebug` עברו; ה־APK הותקן והרצף נבדק חזותית באימולטור עגול.
- כלי דמו למסכי התראות יהודיות ‏22/09: נוסף launcher ב־debug בלבד שמפעיל בזה אחר זה ימים יהודיים, צום, דף יומי, עומר, קידוש לבנה ותקופה; הוא אינו נכנס ל־release ואינו משנה את אבטחת מסכי ההתראה. ה־APK נבנה, הותקן והופעל באימולטור.
- תיקון עיגול תחילת צום ‏22/09: תחילת צום מחושבת כעת מזמן ה־Zmanim הגולמי ומעוגלת למטה, בעוד צאת הצום ור״ת מעוגלות למעלה לפי שיטת החומרא. `:app:testDebugUnitTest :app:assembleDebug` עברו. ה־APK הותקן באימולטור; פתיחת מסכי התראות ישירים דרך ADB נחסמת כי ה־Activities אינן exported, ולכן המשך בדיקת המסכים ייעשה ידנית דרך ממשק האפליקציה.
- תיקון זמני צום לפי אור החיים ‏22/09: צומות שמתחילים בערב (יום כיפור ותשעה באב) משתמשים כעת בזמן הדלקת נרות, כלומר 20 דקות לפני השקיעה; צאת הצום הרגילה משתמשת בזמן צאת שבת, ור״ת נשאר לפי זמן ר״ת. בדיקת רגרסיה ליום כיפור תשפ״ז בתל אביב מכסה את הייחוס 18:21/19:11/19:54 עם סטיית עיגול של דקה לפי קואורדינטות/גובה. בפתח תקווה מתקבלים 18:21/19:09/19:52, פער סביר של עד שתי דקות ולא 18 דקות. `:app:testDebugUnitTest :app:assembleDebug` עברו; ה־APK הותקן ונפתח באימולטור Wear OS.
- תיקון צומות ושפות ‏22/09: מסך זמני היום מחשב צום לפי היום שנבחר בדפדוף, מציג יציאה רגילה ויציאה לפי ר״ת, ומשתמש בזמני Zmanim מבוססי מיקום; התראת ימים יהודיים מרעננת את שם החג לפי השפה שנבחרה בזמן המסירה כדי למנוע ערבוב עברית/אנגלית. `:app:testDebugUnitTest :app:assembleDebug` עברו. הועלה ב־commit `b4ba6a6`.
- חקירת Smart Alarm ‏17/09 הושלמה וממתינה לאישור לפני commit/push: אצל רפאל OPlus עצר את החבילה (`stopped=true`) ובכך מחק את AlarmManager alarms; אצל מוטי לא היה occurrence ל־17/09. תיקון Direct Boot שומר shadow device-protected מינימלי ומשחזר monitoring/deadline נעול. התיקון החדש מחיל את `ReminderMonitoringService` כעוגן FGS קל לכל חובת מסירה עתידית, כולל Smart Alarm לפני monitoringStart; אין חיישני Smart Wake עד תחילת החלון. הוא ישיר־boot-aware, idempotent, מתאחד אחרי unlock, ומתחיל גם אחרי `MY_PACKAGE_REPLACED`. אימות פיזי ‏17/09: Smart Alarm למחר ‏14:37 קיבל monitor ‏13:42, window ‏14:27, deadline ‏14:37 ו־hard-stop ‏14:38; לאחר 15 דקות מסך כבוי השירות נשאר FGS (`adj=200`), `stopped=false`, וכל ה־alarms נשארו, ללא OPlus force-stop. הבנייה הותקנה ב־debug ללא מחיקת נתונים. `:app:testDebugUnitTest :app:assembleDebug` עברו.
- תיקון ניווט מ־complications ‏16/09: כניסה מ־watch face מסומנת כעת ב־`EXTRA_FROM_COMPLICATION`; Back מתוך מסך ברכה/זמני היום/תזכורת/צום/מים, וגם מסך הגדרת Jewish Mode שנפתח ישירות, סוגר את מסלול האפליקציה ומחזיר ל־watch face. ניווט פנימי מתוך `MainActivity` נשאר עם היסטוריית המסכים הרגילה. `:app:testDebugUnitTest :app:assembleDebug` עברו.
- Smart Wake wakeability work in progress (uncommitted; user requested review before commit/push): final conservative review found and fixed three narrow issues. The 15-minute rolling baseline now freezes on interesting wakeability evidence and resumes only after measured quiet; independent HR/movement groups combine across evaluations only while each is fresh (75s); and a current multi-group frame may wake without a redundant post-candidate event only after a separately confirmed 150s trend (3 interesting frames, 2 distinct HR and 2 movement updates). An isolated `42+/2` observation and the `36/2 → 22/1` regression remain candidate-only/no-wake. Monitoring starts 45 minutes before the earliest allowed wake and ringing remains prohibited until that time. Health Connect sleep stages remain retrospective-only. `:app:testDebugUnitTest :app:assembleDebug` passed; see `docs/SMART_WAKE_ARCHITECTURE.md`.
- תיקון complications ‏16/09: הלוגים הוכיחו שב־OnePlus מנהל ה־complications נמצא ב־`com.google.wear.services`, בעוד AndroidX ‏1.3.0 שולחת ב־Android 14 בקשות update רק ל־`com.google.android.wearable.app`; לכן לא התקבלו activation/request וה־jobs בוטלו עד reboot. נוסף broadcast תאימות מאומת עם `PendingIntent` לחבילת OnePlus, וכל ספק ללא הגדרות מקבל config callback שקוף ומבקש רענון לכל הספקים אחרי commit. נוספו לוגי activation/request. בנייה ובדיקות עברו; אימות פיזי ממתין לחיבור ADB מחדש.
- תיקוני Smart Alarm מלוגי Refael/Moti ‏16/09: אצל רפאל Billing foreground recovery הפעיל `reschedule` אחרי כיבוי מוקדם והחזיר בטעות את אותו deadline; `EntitlementEnforcer` משתמש כעת ב־`recover` ושומר מופע שטופל/נודניק פעיל. אצל מוטי מסך „אני ער” של Wake Check לא עלה לפני escalation בגלל ערוץ שקט; הוא קיבל ערוץ attention של 1ms, lifecycle/logging וסגירה לפני escalation. כיבוי escalation אינו מבטל/מדלג עוד על השעון הבא, אין בו snooze, ו־timeout עוצר אותו בלי לשנות את occurrence הבא.
- תיקון זיהוי ערנות ‏16/09: Health Services בשעונים הפיזיים סיפקה callbacks מסוג `PASSIVE` בהפרשים של כעשר דקות, ולכן חלון debounce של 90 שניות לעולם לא אישר ערנות. נשמרות כעת שתי תצפיות non-asleep אמיתיות ורצופות עד 30 דקות; זוג טרי נשמר גם מעבר להפעלת שירות הניטור ומאפשר להעיר בפתיחת חלון ההשכמה. ערך `PASSIVE` שמור יחיד עדיין אינו ראיה ואינו מעיר. נוספו בדיקות לזוג טרי ולזוג ישן.
- תיקוני ממשק וטלפון ‏15/09: כותרות ארוכות ב־Wear ובטלפון מוגבלות לרוחב התוכן כדי שלא ייחתכו; ניסוח הלוגים מציין שליחה למפתח לצורך איתור ותיקון תקלות. Back בטלפון מחזיר למסך הקודם בכל מסכי ה־Activity, וקבלת לוג/גיבוי מרעננת מיד את המסכים הרלוונטיים וגם לאחר פתיחת ההתראה.

- תיקון מסך תקופת הניסיון ‏15/09: נוסף מרווח גלילה תחתון למסך הרכישה בשעון, כדי שכפתור „שחזור רכישה” לא ייחתך במסכים עגולים/נמוכים.

- תיקון מסך Smart Alarm ‏15/09: ב־OnePlus מסך בוקר מקביל של המערכת (כגון מזג אוויר) יכול לזכות במירוץ ה־full-screen למרות שהתראת Zmanio פורסמה כ־`allowedAlarm`; מסך השמירה מפרסם מחדש full-screen notification טרי לכל היותר אחת ל־8 שניות כאשר מסך האזעקה אינו בפוקוס, במקום להסתמך רק על ניסיון `PendingIntent` שנחסם ברקע. ב־auto-snooze הצליל והרטט נעצרים במועד הרגיל, אך אם המשתמש נגע במסך או הקיש ממש לפני המועד, משימת הכיבוי נשארת מוצגת עד 5 שניות מהפעולה האחרונה; זה reset ולא צבירה, ואין הארכה לפעילות מוקדמת או מאוחרת. `:app:testDebugUnitTest :app:assembleDebug` עברו.
- התאוששות התראות מובְנות ‏14/09: הודעת „מחר …” של ימים יהודיים מתוזמנת כעת 3 שעות לפני צאת הכוכבים, ולא שעתיים לפני שקיעה. אם התראת יום יהודי/ברכת לבנה/תקופה אבדה בעת כיבוי השעון, היא נמסרת פעם אחת כשהיא עדיין רלוונטית — ב־boot, פתיחת האפליקציה או callback ערנות/ענידה — ונרשמת כדי לא לחזור; דף יומי ועומר כבר מסמנים תוכן שלא טופל ומופעלים במסלול זה. דף יומי ועומר משתמשים גם ב־full-screen notification בזמן catch-up במקום `startActivity` חסום ברקע. כל ערוצי ההתראות המובְנות (מידע/ימים יהודיים/תקופה, דף יומי, עומר, לבנה, מים וצום לסירוגין) הועברו לערוץ attention חדש עם רטט 1ms; זה מפעיל full-screen ב־OnePlus, בעוד הצליל והרטט בפועל נשארים ב־AlertActivity. יום כיפור ותשעה באב מוצגים עם כניסה בשקיעת הערב הקודם; שאר הצומות בעלות השחר. `:app:testDebugUnitTest :app:assembleDebug` עברו.
- תיקוני ממשק וצום ‏14/09: מסך השפה מציג hint משני קריא; בדף הראשי מופיעים ימי הניסיון שנותרו; במסך הניסיון יש „שדרג עכשיו” במקום „המשך”; לוגים עברו ל״אודות ורישיונות״; שוחזר אייקון שחזור מהטלפון וגודל הטקסט הושווה לגיבוי. מסך „זמני היום” מציג צום היום או מחר דרך `JewishFastInfo`.
- מונטיזציה וגיבוי ‏14/09: Zmanio חינמית עם ניסיון מלא של 14 יום ושחרור Lifetime חד-פעמי (`zmanio_lifetime`) דרך Billing ‏9.1.0. ה־entitlement נשמר ב־SharedPreferences, בודק rollback, מאמת רכישות ב־foreground, מונע מסירה/FGS אחרי פקיעה ומשחזר תזמון מיד אחרי רכישה. אין זיהוי אמין של לקוחות ה־paid הישנים ולכן אין grandfathering אוטומטי. תיעוד: `docs/BILLING_AND_TRIAL.md`.
- גיבויי Watch→Phone חדשים מוצפנים ומאומתים ב־AES-GCM (`ZMBU3`), כוללים `trialStartedAt`, ונתמכת קריאת גיבויים ישנים לשם מעבר בלבד. המפתח המשותף מגן מפני צפייה/עריכה ידנית אך אינו תחליף לשרת או סיסמת משתמש מול reverse engineering.
- הכנת Google Play Billing ‏14/09: שני ארטיפקטי ה־Play (`:app` ל־Wear OS ו־`:phone` לאפליקציית הליווי) כוללים `com.android.billingclient:billing:9.1.0`; הגרסאות הן Wear ‏1.28/code 130 וטלפון ‏1.13/code 1012. שתי מעטפות Kotlin מיושרות נקודתית ל־1.8.22 כדי למנוע כפילות עם תלויות AndroidX קיימות; ה־BillingClient ולוגיקת הרכישה ממומשים בשכבת ה־entitlement. ההרשאה `com.android.vending.BILLING` מגיעה ממיזוג manifest של הספרייה.
- עקרון Smart Wake מחייב: המטרה היא לזהות מעבר לשינה קלה/עוררות עדינה **לפני** קימה מודעת, כדי שההתראה תהיה קלה ונעימה יותר. צעדים, קימה ו־`clearly_awake` הם סימנים מאוחרים לכל היותר — לא הגדרת הצלחה ולא יעד שאליו יש להטות את האלגוריתם. אין להסיק משינה שקטה בלבד שהיא שינה עמוקה, כי Health Services במכשיר אינו מספק שלבי LIGHT/DEEP/REM.
- תיקון Smart Wake ‏14/09: candidate הוא הקשר בלבד, לא אישור. אישור דורש דגימת multi-group טרייה אחרי יצירתו; ראיות שנחלשו (למשל `36/2` ואז `22/1`) מבטלות את ה־candidate וממשיכות לנטר. `PASSIVE` נחשב `SYSTEM_AWAKE_PERSISTENT` רק אחרי שתי תצפיות Health Services אמיתיות, ולא רק לפי חלוף זמן מ־callback יחיד. סיכום הלוג מציג origin, גיל, סטטוס/מקור אישור ומצב system-awake טרי.
- תיקון קריטי 09/09 — מסך Smart Alarm: לוגי השעון הוכיחו ש־OnePlus SystemUI סיווג את ערוץ ה־full-screen השקט כ־`not noisy` ולא הציג אותו; ניסיונות ה־FGS לפתוח Activity נחסמו שוב ושוב ב־`BAL_BLOCK`. גם לחיצה על „פתיחת התראה” נחסמה כ־notification trampoline.
- התיקון: ערוץ `smart_alarm_alert_v9_attention` נשאר ללא צליל מערכת אך כולל רטט attention של 1ms, ולכן SystemUI שולח את ה־full-screen. ה־contentIntent ופעולת „פתיחת התראה” הם כעת PendingIntent ישיר ל־`SmartAlarmAlertActivity`, עם opt-in ל־BAL ב־Android 15.
- שומר המסך בודק גם window focus ולא רק lifecycle; אם מסך OnePlus כגון Sleep report מכסה את האזעקה בלי `onPause`, משימת ההתראה נוצרת מחדש ומוחזרת לחזית.
- אימות פיזי 09/09: מהמסך הכבוי (`mWakefulness=Asleep`) SystemUI רשם `shouldVibrate=true`, פתח את `SmartAlarmAlertActivity`, והיא הייתה `RESUMED`, visible ו־focused. לחיצה אמיתית על „כיבוי” סגרה את המסך, עצרה צליל/רטט ושירות הצלצול, והסירה את notification.
- אימות חוזר במצב שינה כפוי 09/09: במופע יחיד ונקי ה־full-screen נשלח ב־08:35:48, `SmartAlarmAlertActivity` נוצרה ב־08:35:49 וקיבלה focus ב־08:35:50. OnePlus דחה את שירות הצלצול ברקע באותו ניסיון, אך מסך ההתראה והצליל שנוצרו מה־Activity הוצגו כראוי. מופע הבדיקה ונודניק הבדיקה נוקו.
- תיקון Smart Wake 09/09: ערך `PASSIVE` שמור אינו נספר עוד כתצפית Health Services חדשה. הלילה דגימה שמורה יחידה גרמה ל־`SYSTEM_AWAKE_PERSISTENT` ול־WAKE ללא candidate/clearly-awake; מעתה רק callback חי מתחיל debounce, בעוד מסלולי Smart Score ו־Clearly Awake נשארו ללא שינוי.
- תיקון שרשרת snooze ‏09/09: יעד המופע המקורי נשמר גם כשה־state עובר לזמני נודניק. לאחר מיצוי כל הנודניקים, התזמון הבא מחושב אחרי יעד הבוקר המקורי ולכן אינו יכול להתחיל שוב את אותו חלון Smart Wake.
- שורת `SmartWake summary` קוצרה לפורמט: `timestamp → WAKE_SCORE → groups → candidate → clearly_awake → decision`; telemetry המפורט נשאר בנפרד.
- בדיקות: `:app:compileDebugJavaWithJavac :app:testDebugUnitTest :app:assembleDebug` עברו. גרסת debug ‏1.26 (code 128) הסופית הותקנה ונפתחה בהצלחה על OnePlus Watch 3; `stopped=false` אומת לאחר ההתקנה.
- תזמונים אומתו לאחר ההתקנה הסופית: התזכורת הרגילה הקרובה קיימת; Smart Wake למחר 10/09 מתחיל ניטור 05:43:45, חלון 06:00, deadline ‏06:40. לא נותר מופע debug או נודניק בדיקה.
- התראת ה־FGS של Zmanio (`reminder_monitoring`) משתמשת כעת בשני נוסחים מפורשים לפי בחירת השפה השמורה באפליקציה בלבד: „ניטור תזכורות פעיל” לעברית ו־`Active reminder monitoring` לאנגלית. במצב Auto היא נשארת באנגלית באופן דטרמיניסטי ואינה קוראת את שפת Wear OS; ההודעה מתרעננת מיד בעת החלפת השפה. ה־APK הותקן ואומת בפועל כשהטקסט העברי הופיע ב־`dumpsys notification`.
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
- כל ספקי ה־complication מרעננים בעת activation/configuration ושולחים גם את פרוטוקול ה־update המאומת ישירות למנהל `com.google.wear.services` של OnePlus; נדרש אימות פיזי נוסף אחרי התקנה נקייה.
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
