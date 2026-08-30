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
import android.view.ViewParent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.woodpeckerbros.watchreminder.AppLanguage;
import com.woodpeckerbros.watchreminder.AppTextStyle;

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
    private boolean stepPeakActive;

    WakeTaskController(Activity activity, LinearLayout body, SmartAlarmStore settings, Runnable completed) {
        this.activity = activity;
        this.body = body;
        this.settings = settings;
        this.completed = completed;
    }

    void start(String requested) {
        queue.clear();
        if (SmartAlarmStore.DISMISS_COMBINATION.equals(requested)) {
            List<String> choices = selectedMultipleTasks();
            if (choices.size() < 2) choices = taskChoices();
            Collections.shuffle(choices);
            queue.addAll(choices);
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
        body.setPadding(dp(18), dp(32), dp(18), dp(8));
        body.addView(title(isEnglish() ? "Wake-up task" : "משימת השכמה", 18));
        if (!queue.isEmpty()) {
            int tasksIncludingCurrent = queue.size() + 1;
            body.addView(title(isEnglish() ? tasksIncludingCurrent + " tasks remaining"
                    : "נותרו " + tasksIncludingCurrent + " משימות", 12));
        }
        if (SmartAlarmStore.DISMISS_SHAKE.equals(method)) startMotion(false);
        else if (SmartAlarmStore.DISMISS_STEPS.equals(method)) startMotion(true);
        else if (SmartAlarmStore.DISMISS_MATH.equals(method)) startMath();
        else if (SmartAlarmStore.DISMISS_MEMORY.equals(method)) startMemory();
        else startAlternating();
        scrollTaskToTop();
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

    private List<String> selectedMultipleTasks() {
        int mask = settings.multipleTaskMask();
        ArrayList<String> values = new ArrayList<>();
        if ((mask & SmartAlarmStore.TASK_SHAKE) != 0) values.add(SmartAlarmStore.DISMISS_SHAKE);
        if ((mask & SmartAlarmStore.TASK_STEPS) != 0) values.add(SmartAlarmStore.DISMISS_STEPS);
        if ((mask & SmartAlarmStore.TASK_MATH) != 0) values.add(SmartAlarmStore.DISMISS_MATH);
        if ((mask & SmartAlarmStore.TASK_MEMORY) != 0) values.add(SmartAlarmStore.DISMISS_MEMORY);
        if ((mask & SmartAlarmStore.TASK_ALTERNATING) != 0) values.add(SmartAlarmStore.DISMISS_ALTERNATING);
        return values;
    }

    private void startMotion(boolean steps) {
        progress = 0;
        stepPeakActive = false;
        target = steps ? settings.stepCount() : settings.shakeCount();
        TextView instruction = title(steps
                ? (isEnglish() ? "Walk " + target + " steps" : "לכו " + target + " צעדים")
                : (isEnglish() ? "Shake your wrist " + target + " times" : "נערו את היד " + target + " פעמים"), 17);
        TextView counter = title("0 / " + target, 30);
        body.addView(instruction); body.addView(counter);
        sensors = (SensorManager) activity.getSystemService(Activity.SENSOR_SERVICE);
        Sensor primary = sensors == null ? null : sensors.getDefaultSensor(steps ? Sensor.TYPE_STEP_DETECTOR : Sensor.TYPE_ACCELEROMETER);
        Sensor accelerometer = steps && sensors != null ? sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;
        if (primary == null && accelerometer == null) {
            body.addView(title(isEnglish() ? "Sensor unavailable — tap to continue" : "החיישן לא זמין — לחצו להמשך", 12));
            Button fallback = action(isEnglish() ? "Continue" : "המשך");
            fallback.setOnClickListener(v -> next()); body.addView(fallback); return;
        }
        if (primary != null) sensors.registerListener(this, primary, SensorManager.SENSOR_DELAY_GAME);
        // Some Wear OS devices expose STEP_DETECTOR but deliver it late or inconsistently.
        // Keep a calibrated accelerometer detector active as a real-time fallback.
        if (accelerometer != null && accelerometer != primary) {
            sensors.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
        counter.setTag(Boolean.valueOf(steps));
        body.setTag(counter);
    }

    @Override public void onSensorChanged(SensorEvent event) {
        TextView counter = body.getTag() instanceof TextView ? (TextView) body.getTag() : null;
        if (counter == null) return;
        boolean steps = Boolean.TRUE.equals(counter.getTag());
        long now = SystemClock.uptimeMillis();
        boolean hit = event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR
                && now - lastMotionAt >= 260L;
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && event.values.length >= 3) {
            float magnitude = (float) Math.sqrt(event.values[0] * event.values[0]
                    + event.values[1] * event.values[1] + event.values[2] * event.values[2]);
            gravity = gravity * .90f + magnitude * .10f;
            float movement = Math.abs(magnitude - gravity);
            if (steps) {
                if (movement < .55f) stepPeakActive = false;
                hit = !stepPeakActive && movement > 1.25f && now - lastMotionAt >= 280L;
                if (hit) stepPeakActive = true;
            } else {
                hit = movement > 2.1f && now - lastMotionAt > 220L;
            }
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
        MathQuestion question = createMathQuestion(level);
        int answer = question.answer;
        TextView expression = title(question.expression + " = ?", 21);
        expression.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        expression.setTextDirection(View.TEXT_DIRECTION_LTR);
        body.addView(expression);
        body.addView(title((settings.mathDifficulty() - mathRemaining + 1) + " / " + settings.mathDifficulty(), 10));
        ArrayList<Integer> choices = new ArrayList<>(); choices.add(answer);
        int spread = Math.max(6, Math.min(18, answer / 3 + 4));
        while (choices.size() < 4) {
            int value = answer + random.nextInt(spread * 2 + 1) - spread;
            if (value >= 0 && !choices.contains(value)) choices.add(value);
        }
        Collections.shuffle(choices);
        LinearLayout grid = new LinearLayout(activity); grid.setOrientation(LinearLayout.VERTICAL);
        for (int rowIndex = 0; rowIndex < 2; rowIndex++) {
            LinearLayout row = new LinearLayout(activity); row.setGravity(Gravity.CENTER);
            for (int column = 0; column < 2; column++) {
                int value = choices.get(rowIndex * 2 + column);
                Button option = action(String.valueOf(value));
                option.setMinWidth(0); option.setMinimumWidth(0); option.setPadding(dp(2), 0, dp(2), 0);
            option.setOnClickListener(v -> {
                if (value == answer) { mathRemaining--; if (mathRemaining == 0) next(); else showMathQuestion(); }
                    else showMathQuestion();
            });
                LinearLayout.LayoutParams optionParams = new LinearLayout.LayoutParams(0, dp(38), 1f);
                optionParams.setMargins(dp(3), dp(1), dp(3), dp(1));
                row.addView(option, optionParams);
            }
            grid.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        body.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        // Keep the lower answer row above the narrow curved edge of round watches.
        // Without scrollable space after the grid, reaching the end still leaves the
        // buttons clipped by the physical bottom of the display.
        body.addView(new View(activity), new LinearLayout.LayoutParams(1, dp(52)));
        body.requestLayout();
        scrollTaskToTop();
    }

    private MathQuestion createMathQuestion(int level) {
        if (level <= 1) {
            int first = 3 + random.nextInt(10), second = 1 + random.nextInt(9);
            if (random.nextBoolean()) return new MathQuestion(first + " + " + second, first + second);
            int high = Math.max(first, second), low = Math.min(first, second);
            return new MathQuestion(high + " − " + low, high - low);
        }
        if (level == 2) {
            if (random.nextBoolean()) {
                int first = 3 + random.nextInt(10), second = 2 + random.nextInt(8);
                return new MathQuestion(first + " × " + second, first * second);
            }
            int first = 12 + random.nextInt(29), second = 4 + random.nextInt(16);
            int high = Math.max(first, second), low = Math.min(first, second);
            return new MathQuestion(high + " − " + low, high - low);
        }
        int type = random.nextInt(4);
        if (type == 0) {
            int a = 3 + random.nextInt(8), b = 2 + random.nextInt(8), c = 1 + random.nextInt(10);
            return new MathQuestion(a + " × " + b + " + " + c, a * b + c);
        }
        if (type == 1) {
            int a = 2 + random.nextInt(7), b = 2 + random.nextInt(7), c = 2 + random.nextInt(4);
            return new MathQuestion("(" + a + " + " + b + ") × " + c, (a + b) * c);
        }
        if (type == 2) {
            int divisor = 2 + random.nextInt(7), quotient = 2 + random.nextInt(9), c = 1 + random.nextInt(8);
            return new MathQuestion((divisor * quotient) + " ÷ " + divisor + " + " + c, quotient + c);
        }
        int a = 4 + random.nextInt(8), b = 3 + random.nextInt(7);
        int product = a * b, c = 1 + random.nextInt(Math.max(1, Math.min(15, product)));
        return new MathQuestion(a + " × " + b + " − " + c, product - c);
    }

    private static final class MathQuestion {
        final String expression;
        final int answer;
        MathQuestion(String expression, int answer) { this.expression = expression; this.answer = answer; }
    }

    private void startMemory() {
        body.removeViews(1, Math.max(0, body.getChildCount() - 1));
        handler.removeCallbacksAndMessages(null);
        int level = settings.memoryDifficulty();
        int length = level == 1 ? 3 : level == 2 ? 4 : 6;
        int[] sequence = new int[length]; for (int i = 0; i < length; i++) sequence[i] = random.nextInt(4);
        TextView display = title(isEnglish() ? "Watch the sequence" : "זכרו את הרצף", 18);
        body.addView(display);
        int[] colors = {0xFFFF6B6B, 0xFFFFD166, 0xFF4ED6A8, 0xFF6FA8FF};
        long initialDelay = level == 1 ? 1_200L : 1_000L;
        long visibleMs = level == 1 ? 1_000L : level == 2 ? 800L : 650L;
        long gapMs = level == 1 ? 450L : level == 2 ? 350L : 280L;
        long interval = visibleMs + gapMs;
        for (int i = 0; i < length; i++) {
            int index = i;
            handler.postDelayed(() -> { display.setText("●"); display.setTextSize(48); display.setTextColor(colors[sequence[index]]); }, initialDelay + i * interval);
            handler.postDelayed(() -> { display.setText("·"); display.setTextColor(Color.WHITE); }, initialDelay + visibleMs + i * interval);
        }
        handler.postDelayed(() -> showMemoryChoices(sequence, colors), initialDelay + length * interval);
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
                } else startMemory();
            });
            row.addView(choice, new LinearLayout.LayoutParams(0, dp(52), 1f));
        }
        body.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void startAlternating() {
        body.removeViews(1, Math.max(0, body.getChildCount() - 1));
        progress = 0; target = settings.alternatingTapCount();
        TextView status = title(isEnglish() ? "Tap left and right alternately" : "לחצו לסירוגין ימין ושמאל", 16);
        TextView counter = title("0 / " + target, 22); body.addView(status); body.addView(counter);
        LinearLayout row = new LinearLayout(activity); int[] expected = {0};
        Button left = action("←"); Button right = action("→");
        View.OnClickListener listener = v -> {
            int chosen = v == left ? 0 : 1;
            if (chosen != expected[0]) { startAlternating(); return; }
            expected[0] = 1 - expected[0]; progress++; counter.setText(progress + " / " + target);
            left.setAlpha(expected[0] == 0 ? 1f : .45f); right.setAlpha(expected[0] == 1 ? 1f : .45f);
            if (progress >= target) next();
        };
        left.setOnClickListener(listener); right.setOnClickListener(listener); right.setAlpha(.45f);
        row.addView(left, new LinearLayout.LayoutParams(0, dp(54), 1f)); row.addView(right, new LinearLayout.LayoutParams(0, dp(54), 1f)); body.addView(row);
    }

    private TextView title(String value, int size) {
        TextView view = new TextView(activity); view.setText(value); view.setTextColor(Color.WHITE); view.setTextSize(size);
        view.setGravity(Gravity.CENTER); view.setPadding(dp(4), dp(7), dp(4), dp(7));
        AppTextStyle.apply(view); return view;
    }

    private Button action(String value) {
        Button button = new Button(activity); button.setText(value); button.setTextSize(16); button.setTextColor(Color.WHITE);
        GradientDrawable background = new GradientDrawable(); background.setColor(0xCC234F5E); background.setCornerRadius(dp(24));
        background.setStroke(dp(1), 0xAAFFD77A); button.setBackground(background); button.setAllCaps(false);
        AppTextStyle.apply(button); return button;
    }

    private void scrollTaskToTop() {
        ViewParent parent = body.getParent();
        while (parent != null && !(parent instanceof ScrollView)) parent = parent.getParent();
        if (parent instanceof ScrollView) {
            ScrollView scroll = (ScrollView) parent;
            scroll.post(() -> scroll.scrollTo(0, 0));
        }
    }

    private boolean isEnglish() { return AppLanguage.isEnglish(activity); }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
