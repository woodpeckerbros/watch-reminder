package com.woodpeckerbros.watchreminder.smartalarm;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** A small looping illustration of the deliberate right-left wrist rotation. */
final class WristRotationGestureView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ValueAnimator animator;
    private float phase;

    WristRotationGestureView(Context context) {
        super(context);
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1_650L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(value -> { phase = (float) value.getAnimatedValue(); invalidate(); });
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); animator.start(); }
    @Override protected void onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) * .29f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(0xFFFFD27A);
        RectF arc = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawArc(arc, 205, 130, false, paint);
        canvas.drawArc(arc, 25, 130, false, paint);
        paint.setStyle(Paint.Style.FILL);
        float angle = (float) Math.sin(phase * Math.PI * 2) * 52f - 90f;
        float radians = (float) Math.toRadians(angle);
        float x = cx + (float) Math.cos(radians) * radius;
        float y = cy + (float) Math.sin(radians) * radius;
        paint.setColor(Color.WHITE);
        canvas.drawCircle(x, y, dp(5), paint);
        paint.setColor(0xFFFFD27A);
        canvas.rotate(angle + 90f, cx, cy);
        canvas.drawRoundRect(new RectF(cx - dp(8), cy - dp(20), cx + dp(8), cy + dp(20)), dp(8), dp(8), paint);
    }

    private float dp(int value) { return value * getResources().getDisplayMetrics().density; }
}
