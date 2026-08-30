package com.woodpeckerbros.watchreminder.smartwake;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.widget.Button;

/** A dismiss button that completes only after an uninterrupted press. */
final class HoldToDismissButton extends Button {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outline = new Path();
    private final Path progressPath = new Path();
    private long durationMs = 3_000L;
    private long pressedAt;
    private float progress;
    private boolean completed;
    private Runnable onComplete;

    HoldToDismissButton(Context context) {
        super(context);
        float width = getResources().getDisplayMetrics().density * 3f;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(width);
        track.setColor(0x55FFF0B5);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(width);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(0xFFFFD36A);
    }

    void configure(int seconds, Runnable completion) {
        durationMs = Math.max(1, seconds) * 1_000L;
        onComplete = completion;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressedAt = SystemClock.uptimeMillis();
                completed = false;
                progress = 0f;
                setPressed(true);
                handler.post(frame);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!inside(event.getX(), event.getY())) reset();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                reset();
                return true;
            default:
                return true;
        }
    }

    private final Runnable frame = new Runnable() {
        @Override public void run() {
            if (!isPressed() || completed) return;
            progress = Math.min(1f, (SystemClock.uptimeMillis() - pressedAt) / (float) durationMs);
            invalidate();
            if (progress >= 1f) {
                completed = true;
                setPressed(false);
                performClick();
                if (onComplete != null) onComplete.run();
            } else {
                handler.postDelayed(this, 16L);
            }
        }
    };

    private boolean inside(float x, float y) {
        return x >= 0 && y >= 0 && x <= getWidth() && y <= getHeight();
    }

    private void reset() {
        handler.removeCallbacks(frame);
        setPressed(false);
        if (!completed) progress = 0f;
        invalidate();
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = progressPaint.getStrokeWidth() * 1.4f;
        float radius = Math.max(0f, (getHeight() - inset * 2f) / 2f);
        outline.reset();
        outline.addRoundRect(inset, inset, getWidth() - inset, getHeight() - inset,
                radius, radius, Path.Direction.CW);
        canvas.drawPath(outline, track);
        if (progress > 0f) {
            PathMeasure measure = new PathMeasure(outline, false);
            progressPath.reset();
            measure.getSegment(0f, measure.getLength() * progress, progressPath, true);
            canvas.drawPath(progressPath, progressPaint);
        }
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }
}
