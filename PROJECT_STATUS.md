# סטטוס פרויקט - WatchReminder

עדכון אחרון: 2026-07-09

## 1. מטרת הפרויקט הנוכחית

אפליקציית Wear OS בשם Watch Reminder לניהול תזכורות בשעון, כולל תזכורות רגילות, תזכורות יהודיות/הלכתיות, זמני היום, דף יומי, ספירת העומר, קידוש לבנה, תקופות, קומפליקציות לשעון, וגיבוי/סנכרון מול אפליקציית טלפון.

## 2. מבנה תיקיות נוכחי

```text
WatchReminder/
├── app/                         # מודול Wear OS הראשי
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/woodpeckerbros/watchreminder/
│       └── res/
├── phone/                       # מודול טלפון לגיבוי/שחזור/סנכרון
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/woodpeckerbros/watchreminder/phone/
│       └── res/
├── gradle/wrapper/              # Gradle Wrapper
├── build.gradle.kts             # הגדרות Gradle ברמת root
├── settings.gradle.kts          # כולל את :app ואת :phone
├── gradle.properties            # flags קיימים ל-Android Gradle Plugin
├── local.properties             # נתיב SDK מקומי
└── PROJECT_STATUS.md            # קובץ סטטוס זה
```

תיקיות build קיימות תחת `app/build`, `phone/build`, ו-`build`.

## 3. תיקיית Android הפעילה

תיקיית הפרויקט הפעילה היא:

```text
/Users/refaelnakar/Documents/Work/TEMP/Android Temp AI Workspace/WatchReminder
```

המודול הפעיל לשעון הוא `:app`.

מודול הטלפון הוא `:phone`.

## 4. מה השתנה בסשן הזה

- נוצר `PROJECT_STATUS.md`.
- נוצר `AGENTS.md` עם הנחיות עבודה קצרות לסשנים הבאים.
- עודכן `AGENTS.md` עם הנחיה לבצע commit עם הערה ברורה ו-push אחרי כל שינוי.
- עודכן `AGENTS.md` עם הנחיה לענות למשתמש תמיד בעברית.
- תוקן מירוץ אפשרי בניקוי היסטוריה: `ReminderEventStore` מוסיף דור ניקוי ומונע ממופע ישן לשמור בחזרה אירועי היסטוריה אחרי ניקוי.
- נוספה אפשרות צום לסירוגין: הגדרה של שעות צום ושעת התחלת אכילה ראשונית, חישוב חלון אכילה אוטומטי, תזכורת לפתיחת חלון, תזכורת חצי שעה לפני סיום, תזכורת בסיום, וכפתורי "התחלתי לאכול" / "סיימתי לאכול".
- עודכנו גיבוי/שחזור ו-patch מהטלפון לשמירת הגדרות הצום לסירוגין.
- כרטיס הצום לסירוגין הוסר מהמסך הראשי; פעולות הצום נמצאות בתוך מסך הגדרות הצום.
- נוספה complication לצום לסירוגין שפותחת ישירות את מסך הגדרות הצום.
- תצוגת ה-complication של צום לסירוגין צומצמה לשעת התחלה ושעת סיום בלבד, ללא כותרת/תאריך.
- כפתור צום לסירוגין נוסף למסך הראשי כשהצום פעיל; מצב נוכחי הועלה לראש מסך הגדרות הצום; כפתורי ניווט במסך ההגדרות הוצגו ללא כרטיסים; ה-complication של הצום מוצגת ללא אייקון כדי לאפשר מקום לשעות.
- ה-complication של צום לסירוגין מציגה את שעת ההתחלה למעלה עם `מ-` ואת שעת הסיום למטה עם `עד-`.
- הוחזר אייקון ל-Short Text של ה-complication של צום לסירוגין לצד שורת ההתחלה.
- סדר שדות ה-complication של צום לסירוגין הותאם ל-renderer של Wear כך ש-`מ-` מוצג בשורה העליונה ו-`עד-` בשורה התחתונה.
- סודר סדר שורות בחירת שעה/דקה כך שהשעה תמיד בצד שמאל והדקות בצד ימין, גם ב-RTL וגם ב-LTR.
- תוקן מנגנון התראות צום לסירוגין: ההתראה נפתחת במסך full-screen ייעודי עם כפתור "הבנתי", והמערכת מתזמנת retry לפי הגדרת ה-snooze עד שהמשתמש מאשר.
- קוצר ועוצב מחדש מסך התראת צום לסירוגין כך שטקסט ההתראה של "חצי שעה לפני" לא ייחתך בשעון עגול.
- תוקן חישוב זמן לאירוע שנתי לפי זמני הלכה: באירוע שנתי שמוגדר לפי זמן הלכתי, שעת האירוע מחושבת לפי `ZmanimHelper` בתאריך האירוע ורק אחר כך מופחתות "שעות לפני".
- נוספה במסך הגדרות צום לסירוגין אפשרות להזין ידנית שעה ודקה שבהן המשתמש סיים לאכול; מותר לבחור זמן שכבר עבר גם אם גלש אחרי חלון האכילה המתוכנן, אך לא זמן עתידי.
- הורץ build מקומי לאימות מצב הפרויקט: `./gradlew assembleDebug`.
- ניסיון התקנה דרך `adb` לא הושלם כי לא היה מכשיר/אמולטור מחובר בזמן הבדיקה.

## 5. מה עובד כרגע

- `./gradlew assembleDebug` מסתיים בהצלחה.
- שני המודולים נבנים:
  - `:app:assembleDebug`
  - `:phone:assembleDebug`
- קיימים manifests נפרדים לשעון ולטלפון.
- מודול השעון כולל Activity ראשי, services, receivers, foreground services, boot/time receivers, קומפליקציות, Health Services, והתראות full-screen.
- מודול הטלפון כולל Activity ראשי ו-service לקבלת הודעות Wearable.
- קיימים resources בעברית ובאנגלית למודול השעון, ו-resource בסיסי למודול הטלפון.

## 6. מה עדיין שבור או מסוכן

- לא הורצו בדיקות אוטומטיות מעבר ל-build.
- לא בוצעה בדיקת התקנה/הרצה בפועל על שעון או אמולטור בסשן הזה.
- `:app` ו-`:phone` משתמשים באותו `applicationId`:  
  `com.woodpeckerbros.watchreminder`  
  זה עלול להפריע להתקנה במקביל או לזיהוי בין מודול השעון למודול הטלפון, וצריך לטפל בזה רק בזהירות ובאישור.
- קיימות אזהרות Gradle/AGP על flags deprecated/experimental ב-`gradle.properties`, למשל `android.builtInKotlin=false`, `android.newDsl=false`, ו-`android.aapt2FromMavenOverride`.
- הפרויקט משתמש ב-Gradle Wrapper `9.4.1` וב-Android Gradle Plugin `9.2.1`. לא לשדרג בלי אישור מפורש.
- יש קובץ `app/src/main/Thumbs.db`; הוא לא בהכרח מזיק, אבל הוא קובץ מערכת מיותר בתוך source set.
- הרשאות רגישות קיימות במודול השעון: exact alarms, full-screen intent, foreground service, sensors, location, activity recognition. כל שינוי סביבן צריך בדיקה על מכשיר אמיתי.

## 7. הוראות build/run מדויקות

מתיקיית root של הפרויקט:

```bash
cd "/Users/refaelnakar/Documents/Work/TEMP/Android Temp AI Workspace/WatchReminder"
./gradlew assembleDebug
```

בניית מודול השעון בלבד:

```bash
./gradlew :app:assembleDebug
```

בניית מודול הטלפון בלבד:

```bash
./gradlew :phone:assembleDebug
```

APK debug של השעון:

```text
app/build/outputs/apk/debug/app-debug.apk
```

APK debug של הטלפון:

```text
phone/build/outputs/apk/debug/phone-debug.apk
```

התקנה ידנית לשעון/אמולטור מחובר:

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

התקנה ידנית לטלפון/אמולטור מחובר:

```bash
adb devices
adb install -r phone/build/outputs/apk/debug/phone-debug.apk
```

אם מחוברים כמה מכשירים, להשתמש ב-serial:

```bash
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE_SERIAL install -r phone/build/outputs/apk/debug/phone-debug.apk
```

## 8. הערות Device/Emulator

- מודול `:app` מיועד ל-Wear OS ודורש מכשיר/אמולטור שעון.
- ב-`app/src/main/AndroidManifest.xml` מוגדר:
  `android.hardware.type.watch` עם `required="true"`.
- מינימום SDK לשעון: `minSdk = 30`.
- מודול `:phone` מיועד לטלפון Android.
- מינימום SDK לטלפון: `minSdk = 26`.
- כדי לבדוק סנכרון/גיבוי, צריך גם שעון וגם טלפון מחוברים/paired עם Google Play Services Wearable.
- עבור תזכורות אמינות צריך לבדוק הרשאות runtime, במיוחד notifications, exact alarms, full-screen intent, location/sensors לפי הצורך.

## 9. אזהרות חשובות

- לא להזיז או לארגן מחדש תיקיות בלי לשאול קודם.
- לא לשדרג Gradle או Android Gradle Plugin בלי אישור מפורש.
- להעדיף שינויים קטנים וממוקדים.
- לא לשנות `applicationId`, namespace, signing, או manifests רגישים בלי להבין את השפעת ההתקנה על שעון וטלפון.
- לא להסיר flags מ-`gradle.properties` רק כדי לנקות אזהרות, אלא אם בודקים build והרצה אחרי כל שינוי.

## 10. משימות מומלצות להמשך

1. להריץ את `:app` על שעון/אמולטור Wear OS ולבדוק פתיחה, יצירת תזכורת, התראה, snooze, ו-boot/time reschedule.
2. להריץ את `:phone` על טלפון/אמולטור ולבדוק גיבוי, שחזור, ושליחת שינויים לשעון.
3. להחליט האם להפריד `applicationId` בין `:app` ו-`:phone`, ורק לאחר אישור לבצע שינוי קטן ומבוקר.
4. להוסיף בדיקות ממוקדות ללוגיקת חישוב תזכורות, תאריכים עבריים, recurrence, ו-import/export.
5. לבדוק את אזהרות Gradle/AGP אחת-אחת, בלי שדרוגים גורפים.
6. לשקול להסיר את `app/src/main/Thumbs.db` בהמשך, אחרי אישור.
