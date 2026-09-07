package com.woodpeckerbros.watchreminder.guardian;

import android.app.Activity;
import android.app.NotificationManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class GuardianAlertActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        String key = getIntent().getStringExtra(GuardianContract.EXTRA_KEY);
        String name = getIntent().getStringExtra(GuardianContract.EXTRA_REMINDER_NAME);
        if (name == null || name.trim().isEmpty()) name = "תזכורת";
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER);
        root.setPadding(36, 36, 36, 36); root.setBackgroundColor(0xFF091C2B);
        TextView title = text(getString(R.string.guardian_alert_title), 20, 0xFFC77B58);
        TextView message = text(name, 25, Color.WHITE); message.setPadding(0, 28, 0, 28);
        TextView reason = text(getString(R.string.guardian_no_confirmation), 13, 0xFFBEC4BD);
        Button close = new Button(this); close.setText(R.string.close);
        String finalKey = key;
        close.setOnClickListener(view -> {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null && finalKey != null) manager.cancel(finalKey.hashCode());
            finishAndRemoveTask();
        });
        root.addView(title); root.addView(message); root.addView(reason); root.addView(close);
        setContentView(root);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        return view;
    }
}
