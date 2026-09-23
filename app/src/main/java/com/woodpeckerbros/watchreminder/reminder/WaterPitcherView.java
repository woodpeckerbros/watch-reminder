package com.woodpeckerbros.watchreminder.reminder;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** A small, code-drawn water vessel that reflects today's hydration progress. */
public final class WaterPitcherView extends View {
    private final Paint waterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float progress;
    private float wavePhase;
    private ValueAnimator animator;

    public WaterPitcherView(Context context) { this(context, null); }

    public WaterPitcherView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        waterPaint.setColor(0xFF38BDE8);
        outlinePaint.setColor(0xFFB7E9F5);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(dp(2));
        glowPaint.setColor(0x6638BDE8);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(dp(7));
    }

    public void setProgress(float value) {
        progress = Math.max(0f, Math.min(1f, value));
        setContentDescription(Math.round(progress * 100f) + "%");
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(2600L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            wavePhase = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = dp(12);
        RectF body = new RectF(inset, dp(10), getWidth() - inset, getHeight() - dp(8));
        Path vessel = vesselPath(body);
        canvas.drawPath(vessel, glowPaint);
        canvas.save();
        canvas.clipPath(vessel);
        float waterTop = body.bottom - body.height() * progress;
        Path wave = new Path();
        wave.moveTo(body.left, waterTop);
        float width = body.width();
        for (float x = body.left; x <= body.right + dp(4); x += dp(3)) {
            float relative = (x - body.left) / width;
            float y = waterTop + (float) Math.sin((relative * Math.PI * 2.6d) + wavePhase * Math.PI * 2d) * dp(3);
            wave.lineTo(x, y);
        }
        wave.lineTo(body.right, body.bottom);
        wave.lineTo(body.left, body.bottom);
        wave.close();
        canvas.drawPath(wave, waterPaint);
        canvas.restore();
        canvas.drawPath(vessel, outlinePaint);

        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setColor(0xFFEAFBFF);
        rim.setStrokeWidth(dp(2));
        rim.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(body.left + body.width() * .18f, body.top + dp(2),
                body.right - body.width() * .18f, body.top + dp(2), rim);
    }

    private Path vesselPath(RectF body) {
        float shoulder = body.width() * .12f;
        float corner = dp(14);
        Path path = new Path();
        path.moveTo(body.left + shoulder, body.top);
        path.lineTo(body.right - shoulder, body.top);
        path.lineTo(body.right, body.top + body.height() * .18f);
        path.lineTo(body.right - dp(5), body.bottom - corner);
        path.quadTo(body.right - dp(5), body.bottom, body.right - corner, body.bottom);
        path.lineTo(body.left + corner, body.bottom);
        path.quadTo(body.left + dp(5), body.bottom, body.left + dp(5), body.bottom - corner);
        path.lineTo(body.left, body.top + body.height() * .18f);
        path.close();
        return path;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
