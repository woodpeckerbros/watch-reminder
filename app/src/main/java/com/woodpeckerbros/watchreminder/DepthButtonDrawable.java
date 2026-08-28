package com.woodpeckerbros.watchreminder;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Pill drawable with a lower bevel, soft highlight and pressed depth. */
final class DepthButtonDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int baseColor;
    private final float radius;

    DepthButtonDrawable(int baseColor, float radius) {
        this.baseColor = baseColor;
        this.radius = radius;
    }

    @Override public void draw(Canvas canvas) {
        RectF bounds = new RectF(getBounds());
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(darken(baseColor, 0.48f));
        canvas.drawRoundRect(new RectF(bounds.left, bounds.top + 4, bounds.right, bounds.bottom), radius, radius, paint);
        RectF face = new RectF(bounds.left, bounds.top, bounds.right, bounds.bottom - 4);
        paint.setShader(new LinearGradient(0, face.top, 0, face.bottom,
                lighten(baseColor, 1.22f), darken(baseColor, 0.76f), Shader.TileMode.CLAMP));
        paint.setShadowLayer(5, 0, 3, 0x99000000);
        canvas.drawRoundRect(face, radius, radius, paint);
        paint.clearShadowLayer();
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f);
        paint.setColor(0x99EDE1BF);
        canvas.drawRoundRect(new RectF(face.left + 1, face.top + 1, face.right - 1, face.bottom - 1), radius, radius, paint);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
    @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }

    private static int lighten(int color, float amount) {
        return Color.rgb(Math.min(255, Math.round(Color.red(color) * amount)),
                Math.min(255, Math.round(Color.green(color) * amount)),
                Math.min(255, Math.round(Color.blue(color) * amount)));
    }

    private static int darken(int color, float amount) {
        return Color.rgb(Math.round(Color.red(color) * amount),
                Math.round(Color.green(color) * amount), Math.round(Color.blue(color) * amount));
    }
}
