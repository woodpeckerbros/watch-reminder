# סטטוס פרויקט - WatchReminder

עדכון אחרון: 2026-07-08

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
- לא בוצעו שינויי קוד.
- הורץ build מקומי לאימות מצב הפרויקט: `./gradlew assembleDebug`.

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
