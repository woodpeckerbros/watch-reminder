package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Compact bronze-and-patina bell drawn in code so it stays crisp on every Wear display. */
final class OrnateBellView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    OrnateBellView(Context context) { super(context); }

    @Override protected void onDraw(Canvas canvas) {
        float s = Math.min(getWidth() / 72f, getHeight() / 66f);
        canvas.save();
        canvas.translate(getWidth() / 2f, getHeight() / 2f + 2 * s);
        canvas.scale(s, s);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f);
        paint.setColor(0x88E8CF91);
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16d;
            canvas.drawLine((float) Math.cos(a) * 25, (float) Math.sin(a) * 25 - 5,
                    (float) Math.cos(a) * 31, (float) Math.sin(a) * 31 - 5, paint);
        }

        Path bell = new Path();
        bell.moveTo(-21, 16);
        bell.quadTo(-13, 7, -12, -10);
        bell.quadTo(-11, -25, 0, -27);
        bell.quadTo(11, -25, 12, -10);
        bell.quadTo(13, 7, 21, 16);
        bell.quadTo(0, 23, -21, 16);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(-18, -25, 18, 19,
                new int[]{0xFFD9BC76, 0xFF4F8B78, 0xFFB88A45, 0xFF355F55, 0xFFE2C47C},
                null, Shader.TileMode.CLAMP));
        paint.setShadowLayer(3, 0, 2, 0x99000000);
        setLayerType(LAYER_TYPE_SOFTWARE, paint);
        canvas.drawPath(bell, paint);
        paint.clearShadowLayer();
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.1f);
        paint.setColor(0xFFE6C985);
        canvas.drawPath(bell, paint);

        // Decorative scrolls.
        paint.setStrokeWidth(1.6f);
        paint.setColor(0xFFE2C47C);
        canvas.drawArc(new RectF(-9, -15, 1, -3), 30, 275, false, paint);
        canvas.drawArc(new RectF(-1, -15, 9, -3), 235, 275, false, paint);
        canvas.drawArc(new RectF(-7, -5, 7, 9), 190, 160, false, paint);
        canvas.drawLine(-17, 12, 17, 12, paint);
        canvas.drawLine(-19, 16, 19, 16, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFD8AD63);
        canvas.drawCircle(0, 22, 5, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xFFFFD993);
        canvas.drawCircle(0, 22, 5, paint);
        canvas.drawCircle(0, -30, 3.5f, paint);
        canvas.restore();
    }
}
