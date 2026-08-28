package com.woodpeckerbros.watchreminder;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Opaque, softly elevated button surface shared by the regular watch UI. */
final class ElegantButtonDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int baseColor;
    private final float radius;
    private boolean pressed;

    ElegantButtonDrawable(int baseColor, float radius) {
        this.baseColor = baseColor;
        this.radius = radius;
    }

    @Override public void draw(Canvas canvas) {
        RectF bounds = new RectF(getBounds());
        float pressOffset = pressed ? 2f : 0f;
        RectF face = new RectF(bounds.left + 2f, bounds.top + 2f + pressOffset,
                bounds.right - 2f, bounds.bottom - 5f + pressOffset);

        if (!pressed) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(0x52000000);
            paint.setShadowLayer(6f, 0f, 3f, 0xA0000000);
            RectF shadow = new RectF(face);
            shadow.offset(0f, 2f);
            canvas.drawRoundRect(shadow, radius, radius, paint);
            paint.clearShadowLayer();
        }

        int top = blend(baseColor, Color.WHITE, pressed ? 0.08f : 0.20f);
        int bottom = blend(baseColor, Color.BLACK, pressed ? 0.20f : 0.13f);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0f, face.top, 0f, face.bottom,
                top, bottom, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(face, radius, radius, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.4f);
        paint.setColor(blend(baseColor, Color.WHITE, pressed ? 0.24f : 0.48f));
        RectF rim = new RectF(face);
        rim.inset(0.7f, 0.7f);
        canvas.drawRoundRect(rim, radius, radius, paint);

    }

    private static int blend(int from, int to, float amount) {
        float keep = 1f - amount;
        return Color.argb(Color.alpha(from),
                Math.round(Color.red(from) * keep + Color.red(to) * amount),
                Math.round(Color.green(from) * keep + Color.green(to) * amount),
                Math.round(Color.blue(from) * keep + Color.blue(to) * amount));
    }

    @Override protected boolean onStateChange(int[] states) {
        boolean next = false;
        for (int state : states) {
            if (state == android.R.attr.state_pressed) next = true;
        }
        if (next == pressed) return false;
        pressed = next;
        invalidateSelf();
        return true;
    }

    @Override public boolean isStateful() { return true; }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
    @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
}
