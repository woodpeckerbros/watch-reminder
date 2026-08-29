package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Fixed decorative background and rim for the full-screen reminder alert. */
final class ReminderAlertFrameView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean background;

    ReminderAlertFrameView(Context context) {
        this(context, true);
    }

    ReminderAlertFrameView(Context context, boolean background) {
        super(context);
        this.background = background;
        setClickable(false);
        setFocusable(false);
    }

    @Override protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() * 0.43f;
        float radius = Math.max(getWidth(), getHeight()) * 0.72f;
        if (background) {
            paint.setShader(new RadialGradient(
                    cx, cy, radius,
                    new int[]{0xFF153B54, 0xFF081D2D, 0xFF020B12},
                    new float[]{0f, 0.58f, 1f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setShader(null);
            return;
        }

        float inset = dp(1.1f);
        // Static concentric warm strokes create a sunrise-like fade without a
        // full-screen software blur or shadow layer on every frame.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(9f));
        paint.setColor(0x18F4C98E);
        canvas.drawOval(new RectF(inset + dp(4), inset + dp(4),
                getWidth() - inset - dp(4), getHeight() - inset - dp(4)), paint);
        paint.setStrokeWidth(dp(6f));
        paint.setColor(0x30F1C486);
        canvas.drawOval(new RectF(inset + dp(2.5f), inset + dp(2.5f),
                getWidth() - inset - dp(2.5f), getHeight() - inset - dp(2.5f)), paint);
        paint.setStrokeWidth(dp(3.5f));
        paint.setColor(0x62EBC58C);
        canvas.drawOval(new RectF(inset + dp(1.2f), inset + dp(1.2f),
                getWidth() - inset - dp(1.2f), getHeight() - inset - dp(1.2f)), paint);
        paint.setStrokeWidth(dp(1.7f));
        paint.setColor(0xFFE0C38D);
        canvas.drawOval(new RectF(inset, inset, getWidth() - inset, getHeight() - inset), paint);

        // A restrained sparkle near the lower rim, inspired by the reference without obscuring controls.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x99EFE4CB);
        float sx = getWidth() - dp(18);
        float sy = getHeight() - dp(31);
        android.graphics.Path star = new android.graphics.Path();
        star.moveTo(sx, sy - dp(5));
        star.quadTo(sx, sy, sx + dp(5), sy);
        star.quadTo(sx, sy, sx, sy + dp(5));
        star.quadTo(sx, sy, sx - dp(5), sy);
        star.quadTo(sx, sy, sx, sy - dp(5));
        canvas.drawPath(star, paint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
