package com.woodpeckerbros.watchreminder.smartwake;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.woodpeckerbros.watchreminder.AppLanguage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

final class WakeTaskController implements SensorEventListener {
    private final Activity activity;
    private final LinearLayout body;
    private final SmartAlarmStore settings;
    private final Runnable completed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private SensorManager sensors;
    private int progress;
    private int target;
    private long lastMotionAt;
    private float gravity = SensorManager.GRAVITY_EARTH;

    WakeTaskController(Activity activity, LinearLayout body, SmartAlarmStore settings, Runnable completed) {
        this.activity = activity;
        this.body = body;
        this.settings = settings;
        this.completed = completed;
    }

    void start(String requested) {
        queue.clear();
        if (SmartAlarmStore.DISMISS_COMBINATION.equals(requested)) {
            List<String> choices = taskChoices();
            Collections.shuffle(choices);
            queue.add(choices.get(0));
            queue.add(choices.get(1));
        } else if (SmartAlarmStore.DISMISS_RANDOM.equals(requested)) {
            List<String> choices = taskChoices();
            queue.add(choices.get(random.nextInt(choices.size())));
        } else queue.add(requested);
        next();
    }

    void stop() {
        handler.removeCallbacksAndMessages(null);
        if (sensors != null) sensors.unregisterListener(this);
    }

    private void next() {
        stop();
        if (queue.isEmpty()) { completed.run(); return; }
        String method = queue.removeFirst();
        body.removeAllViews();
        body.addView(title(isEnglish() ? "Wake-up task" : "משימת השכמה", 22));
        if (!queue.isEmpty()) body.addView(title(isEnglish() ? "First of two tasks" : "משימה ראשונה מתוך שתיים", 12));
        if (SmartAlarmStore.DISMISS_SHAKE.equals(method)) startMotion(false);
        else if (SmartAlarmStore.DISMISS_STEPS.equals(method)) startMotion(true);
        else if (SmartAlarmStore.DISMISS_MATH.equals(method)) startMath();
        else if (SmartAlarmStore.DISMISS_MEMORY.equals(method)) startMemory();
        else startAlternating();
    }

    private List<String> taskChoices() {
        ArrayList<String> values = new ArrayList<>();
        values.add(SmartAlarmStore.DISMISS_SHAKE);
        values.add(SmartAlarmStore.DISMISS_STEPS);
        values.add(SmartAlarmStore.DISMISS_MATH);
        values.add(SmartAlarmStore.DISMISS_MEMORY);
        values.add(SmartAlarmStore.DISMISS_ALTERNATING);
        return values;
    }

    private void startMotion(boolean steps) {
        progress = 0;
        target = steps ? settings.stepCount() : settings.shakeCount();
        TextView instruction = title(steps
                ? (isEnglish() ? "Walk " + target + " steps" : "לכו " + target + " צעדים")
                : (isEnglish() ? "Shake your wrist " + target + " times" : "נערו את היד " + target + " פעמים"), 17);
        TextView counter = title("0 / " + target, 30);
        body.addView(instruction); body.addView(counter);
        sensors = (SensorManager) activity.getSystemService(Activity.SENSOR_SERVICE);
        Sensor sensor = sensors == null ? null : sensors.getDefaultSensor(steps ? Sensor.TYPE_STEP_DETECTOR : Sensor.TYPE_ACCELEROMETER);
        if (sensor == null && steps && sensors != null) sensor = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (sensor == null) {
            body.addView(title(isEnglish() ? "Sensor unavailable — tap to continue" : "החיישן לא זמין — לחצו להמשך", 12));
            Button fallback = action(isEnglish() ? "Continue" : "המשך");
            fallback.setOnClickListener(v -> next()); body.addView(fallback); return;
        }
        sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
        counter.setTag(Boolean.valueOf(steps));
        body.setTag(counter);
    }

    @Override public void onSensorChanged(SensorEvent event) {
        TextView counter = body.getTag() instanceof TextView ? (TextView) body.getTag() : null;
        if (counter == null) return;
        boolean steps = Boolean.TRUE.equals(counter.getTag());
        long now = SystemClock.uptimeMillis();
        boolean hit = event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR;
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && event.values.length >= 3) {
            float magnitude = (float) Math.sqrt(event.values[0] * event.values[0]
                    + event.values[1] * event.values[1] + event.values[2] * event.values[2]);
            gravity = gravity * .82f + magnitude * .18f;
            float movement = Math.abs(magnitude - gravity);
            hit = movement > (steps ? 2.8f : 2.1f) && now - lastMotionAt > (steps ? 380L : 220L);
        }
        if (!hit) return;
        lastMotionAt = now;
        progress++;
        counter.setText(progress + " / " + target);
        if (progress >= target) next();
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private int mathRemaining;
    private void startMath() {
        mathRemaining = settings.mathDifficulty();
        showMathQuestion();
    }

    private void showMathQuestion() {
        body.removeViews(1, Math.max(0, body.getChildCount() - 1));
        int level = settings.mathDifficulty();
        int a, b, answer; String operator;
        if (level == 1) { a = 2 + random.nextInt(9); b = 1 + random.nextInt(9); answer = a + b; operator = "+"; }
        else if (level == 2) { a = 3 + random.nextInt(10); b = 2 + random.nextInt(8); answer = a * b; operator = "×"; }
        else { a = 20 + random.nextInt(60); b = 5 + random.nextInt(25); answer = a - b; operator = "−"; }
        body.addView(title((isEnglish() ? "Solve" : "פתרו") + "  " + a + " " + operator + " " + b, 25));
        body.addView(title((settings.mathDifficulty() - mathRemaining + 1) + " / " + settings.mathDifficulty(), 12));
        ArrayList<Integer> choices = new ArrayList<>(); choices.add(answer);
        while (choices.size() < 4) { int value = answer + random.nextInt(13) - 6; if (value >= 0 && !choices.contains(value)) choices.add(value); }
        Collections.shuffle(choices);
        LinearLayout row = new LinearLayout(activity); row.setGravity(Gravity.CENTER);
        for (int value : choices) {
            Button option = action(String.valueOf(value));
            option.setOnClickListener(v -> {
                if (value == answer) { mathRemaining--; if (mathRemaining == 0) next(); else showMathQuestion(); }
                else { v.animate().translationXBy(8).setDuration(70).withEndAction(() -> v.animate().translationX(0)); }
            });
            row.addView(option, new LinearLayout.LayoutParams(0, dp(48), 1f));
        }
        body.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void startMemory() {
        int length = settings.memoryDifficulty() == 1 ? 3 : settings.memoryDifficulty() == 2 ? 5 : 7;
        int[] sequence = new int[length]; for (int i = 0; i < length; i++) sequence[i] = random.nextInt(4);
        TextView display = title(isEnglish() ? "Watch the sequence" : "זכרו את הרצף", 18);
        body.addView(display);
        int[] colors = {0xFFFF6B6B, 0xFFFFD166, 0xFF4ED6A8, 0xFF6FA8FF};
        for (int i = 0; i < length; i++) {
            int index = i;
            handler.postDelayed(() -> { display.setText("●"); display.setTextSize(48); display.setTextColor(colors[sequence[index]]); }, 700L + i * 650L);
            handler.postDelayed(() -> { display.setText("·"); display.setTextColor(Color.WHITE); }, 1_100L + i * 650L);
        }
        handler.postDelayed(() -> showMemoryChoices(sequence, colors), 900L + length * 650L);
    }

    private void showMemoryChoices(int[] sequence, int[] colors) {
        body.removeViews(1, Math.max(0, body.getChildCount() - 1));
        TextView status = title(isEnglish() ? "Repeat the sequence" : "חזרו על הרצף", 16); body.addView(status);
        LinearLayout row = new LinearLayout(activity); row.setGravity(Gravity.CENTER);
        int[] entered = {0};
        for (int colorIndex = 0; colorIndex < 4; colorIndex++) {
            Button choice = action("●"); choice.setTextColor(colors[colorIndex]); final int selected = colorIndex;
            choice.setOnClickListener(v -> {
                if (sequence[entered[0]] == selected) {
                    entered[0]++; status.setText(entered[0] + " / " + sequence.length);
                    if (entered[0] == sequence.length) next();
                } else { entered[0] = 0; status.setText(isEnglish() ? "Try again · 0 / " + sequence.length : "נסו שוב · 0 / " + sequence.length); }
            });
            row.addView(choice, new LinearLayout.LayoutParams(0, dp(52), 1f));
        }
        body.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void startAlternating() {
        progress = 0; target = settings.alternatingTapCount();
        TextView status = title(isEnglish() ? "Tap left and right alternately" : "לחצו לסירוגין ימין ושמאל", 16);
        TextView counter = title("0 / " + target, 22); body.addView(status); body.addView(counter);
        LinearLayout row = new LinearLayout(activity); int[] expected = {0};
        Button left = action("←"); Button right = action("→");
        View.OnClickListener listener = v -> {
            int chosen = v == left ? 0 : 1;
            if (chosen != expected[0]) return;
            expected[0] = 1 - expected[0]; progress++; counter.setText(progress + " / " + target);
            left.setAlpha(expected[0] == 0 ? 1f : .45f); right.setAlpha(expected[0] == 1 ? 1f : .45f);
            if (progress >= target) next();
        };
        left.setOnClickListener(listener); right.setOnClickListener(listener); right.setAlpha(.45f);
        row.addView(left, new LinearLayout.LayoutParams(0, dp(54), 1f)); row.addView(right, new LinearLayout.LayoutParams(0, dp(54), 1f)); body.addView(row);
    }

    private TextView title(String value, int size) {
        TextView view = new TextView(activity); view.setText(value); view.setTextColor(Color.WHITE); view.setTextSize(size);
        view.setGravity(Gravity.CENTER); view.setPadding(dp(4), dp(7), dp(4), dp(7)); return view;
    }

    private Button action(String value) {
        Button button = new Button(activity); button.setText(value); button.setTextSize(16); button.setTextColor(Color.WHITE);
        GradientDrawable background = new GradientDrawable(); background.setColor(0xCC234F5E); background.setCornerRadius(dp(24));
        background.setStroke(dp(1), 0xAAFFD77A); button.setBackground(background); button.setAllCaps(false); return button;
    }

    private boolean isEnglish() { return AppLanguage.isEnglish(activity); }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
