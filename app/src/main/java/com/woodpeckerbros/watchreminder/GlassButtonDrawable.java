package com.woodpeckerbros.watchreminder;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Warm matte terracotta pill with a subtle textile grain and soft inset depth. */
final class GlassButtonDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int tint;
    private final float radius;
    private boolean pressed;

    GlassButtonDrawable(int tint, float radius) {
        this.tint = tint;
        this.radius = radius;
    }

    @Override public void draw(Canvas canvas) {
        RectF box = new RectF(getBounds());
        float down = pressed ? 2f : 0f;
        box.offset(0, down);
        RectF face = new RectF(box.left, box.top, box.right, box.bottom - 4);
        int terracotta = blend(tint, 0xFF9B7465, 0.84f);
        int top = blend(terracotta, 0xFFFFD2BF, 0.28f);
        int middle = terracotta;
        int bottom = blend(terracotta, 0xFF4C302B, 0.24f);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, face.top, 0, face.bottom,
                new int[]{top, middle, bottom},
                new float[]{0f, 0.54f, 1f}, Shader.TileMode.CLAMP));
        paint.setShadowLayer(6, 1.5f, 3.5f, 0x99000000);
        canvas.drawRoundRect(face, radius, radius, paint);
        paint.clearShadowLayer();
        paint.setShader(null);

        // Fine crossed fibers give a restrained woven/matte surface without visual noise.
        Path clip = new Path();
        clip.addRoundRect(face, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.55f);
        paint.setColor(0x12FFF2E8);
        for (float x = face.left - face.height(); x < face.right; x += 5f) {
            canvas.drawLine(x, face.bottom, x + face.height(), face.top, paint);
        }
        paint.setColor(0x0E2B1715);
        for (float x = face.left; x < face.right + face.height(); x += 7f) {
            canvas.drawLine(x, face.top, x - face.height(), face.bottom, paint);
        }
        canvas.restore();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.25f);
        paint.setColor(0xA8FFE1D2);
        canvas.drawRoundRect(new RectF(face.left + 1, face.top + 1, face.right - 1, face.bottom - 1), radius, radius, paint);
        paint.setStrokeWidth(1.1f);
        paint.setColor(0x70FFFFFF);
        canvas.drawArc(new RectF(face.left + 3, face.top + 2, face.right - 3, face.bottom - 4), 195, 150, false, paint);
        // Soft inset shadow along the lower half creates depth without a glossy plastic look.
        paint.setStrokeWidth(2.1f);
        paint.setColor(0x57241210);
        canvas.drawArc(new RectF(face.left + 2, face.top + 2, face.right - 2, face.bottom - 2), 5, 170, false, paint);
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

    private static int blend(int a, int b, float t) { return Color.rgb(Math.round(Color.red(a) * (1 - t) + Color.red(b) * t), Math.round(Color.green(a) * (1 - t) + Color.green(b) * t), Math.round(Color.blue(a) * (1 - t) + Color.blue(b) * t)); }
}
