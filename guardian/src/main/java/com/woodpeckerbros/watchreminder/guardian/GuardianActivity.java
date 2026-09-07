package com.woodpeckerbros.watchreminder.guardian;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class GuardianActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        GuardianStore.rescheduleStored(this);
        GuardianBootReceiver.requestFreshPlan(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(36, 36, 36, 36);
        root.setBackgroundColor(0xFF091C2B);
        TextView title = text(getString(R.string.guardian_title), 21, 0xFFC77B58);
        TextView message = text(getString(R.string.guardian_message), 15, Color.WHITE);
        message.setPadding(0, 24, 0, 16);
        TextView count = text("(" + GuardianStore.plannedCount(this) + ")", 14, 0xFFBEC4BD);
        root.addView(title);
        root.addView(message);
        root.addView(count);
        setContentView(root);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        return view;
    }
}
