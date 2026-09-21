package com.woodpeckerbros.watchreminder;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.woodpeckerbros.watchreminder.reminder.InformationalAlertActivity;

/** Debug-only launcher for visually checking every Jewish informational alert screen. */
public class DebugJewishAlertDemoActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int index;
    private final DemoAlert[] alerts = {
            new DemoAlert("demo-jewish-days", "ימים יהודיים", "היום יום כיפור"),
            new DemoAlert("demo-fast", "זמני צום", "יום כיפור\nתחילת הצום 18:20\nצאת הצום 19:09\nצאת ר״ת 19:52"),
            new DemoAlert("demo-daf", "דף היומי", "הדף היומי להיום"),
            new DemoAlert("demo-omer", "ספירת העומר", "ספירת העומר — דוגמה"),
            new DemoAlert("demo-moon", "קידוש לבנה", "זמן מתאים לקידוש לבנה"),
            new DemoAlert("demo-tekufa", "תקופה", "תזכורת לתקופה הקרובה")
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        showNext();
    }

    private void showNext() {
        if (index >= alerts.length) {
            finish();
            return;
        }
        DemoAlert alert = alerts[index++];
        Intent intent = new Intent(this, InformationalAlertActivity.class)
                .putExtra("info_alert_key", alert.key)
                .putExtra("info_alert_title", alert.title)
                .putExtra("info_alert_message", alert.message);
        startActivity(intent);
        handler.postDelayed(this::showNext, 3_500L);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private static final class DemoAlert {
        final String key;
        final String title;
        final String message;

        DemoAlert(String key, String title, String message) {
            this.key = key;
            this.title = title;
            this.message = message;
        }
    }
}
