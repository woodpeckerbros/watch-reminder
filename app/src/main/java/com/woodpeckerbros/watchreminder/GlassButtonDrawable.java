package com.woodpeckerbros.watchreminder;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Neutral 50%-transparent glass pill with a bright rim and an external drop shadow. */
final class GlassButtonDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float radius;
    private boolean pressed;

    GlassButtonDrawable(int ignoredTint, float radius) {
        this.radius = radius;
    }

    @Override public void draw(Canvas canvas) {
        RectF box = new RectF(getBounds());
        float down = pressed ? 2f : 0f;
        box.offset(0, down);
        RectF face = new RectF(box.left, box.top, box.right, box.bottom - 4);

        // Keep the shadow outside the translucent face so it cannot darken the glass itself.
        Path facePath = new Path();
        facePath.addRoundRect(face, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipOutPath(facePath);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(0xA8000000);
        RectF shadow = new RectF(face);
        shadow.offset(2.5f, 5f);
        paint.setShadowLayer(7, 1.5f, 3f, 0xB8000000);
        canvas.drawRoundRect(shadow, radius, radius, paint);
        paint.clearShadowLayer();
        canvas.restore();

        // One uniform 50%-opaque layer: no gradient, inset oval or internal decoration.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x80A8A3A0);
        canvas.drawRoundRect(face, radius, radius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(0xC8F3F0ED);
        canvas.drawRoundRect(new RectF(face.left + 0.75f, face.top + 0.75f,
                face.right - 0.75f, face.bottom - 0.75f), radius, radius, paint);
    }

    @Override protected boolean onStateChange(int[] states) {
        boolean next = false;
        for (int state : states) if (state == android.R.attr.state_pressed) next = true;
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
