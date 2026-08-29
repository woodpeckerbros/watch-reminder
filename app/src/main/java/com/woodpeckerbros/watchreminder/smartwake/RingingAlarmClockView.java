package com.woodpeckerbros.watchreminder.smartwake;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** A lightweight, static code-drawn alarm clock for the alarm screen. */
final class RingingAlarmClockView extends View {
    private static final int GOLD = 0xFFFFD27A;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    RingingAlarmClockView(Context context) {
        super(context);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = Math.min(getWidth() / 64f, getHeight() / 62f);
        float wave = 0f;

        canvas.save();
        canvas.translate(getWidth() / 2f, getHeight() / 2f + 2f * scale);
        canvas.rotate(wave * 7f);
        canvas.scale(scale, scale);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3.2f);
        paint.setColor(GOLD);

        // Bells and their supports.
        canvas.drawArc(new RectF(-29, -26, -9, -8), 205, 130, false, paint);
        canvas.drawArc(new RectF(9, -26, 29, -8), 205, 130, false, paint);
        canvas.drawLine(-18, -15, -12, -8, paint);
        canvas.drawLine(18, -15, 12, -8, paint);

        // Center hammer alternates between the two bells.
        float hammerX = wave * 6f;
        canvas.drawLine(0, -25, hammerX, -18, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(hammerX, -19, 3.2f, paint);

        // Clock body, legs and hands.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xDD102638);
        canvas.drawCircle(0, 5, 21, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(GOLD);
        paint.setStrokeWidth(3.2f);
        canvas.drawCircle(0, 5, 21, paint);
        canvas.drawLine(-12, 22, -18, 29, paint);
        canvas.drawLine(12, 22, 18, 29, paint);
        canvas.drawLine(0, 5, 0, -7, paint);
        canvas.drawLine(0, 5, 9, 10, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(0, 5, 2.2f, paint);

        canvas.restore();
    }
}
