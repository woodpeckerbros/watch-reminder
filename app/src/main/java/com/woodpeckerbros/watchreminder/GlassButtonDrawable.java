package com.woodpeckerbros.watchreminder;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Translucent, dimensional glass pill used by the main application UI. */
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
        int top = blend(tint, Color.WHITE, 0.78f);
        int middle = blend(tint, Color.WHITE, 0.62f);
        int bottom = blend(tint, Color.BLACK, 0.10f);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, face.top, 0, face.bottom,
                new int[]{withAlpha(top, 100), withAlpha(middle, 62), withAlpha(bottom, 74)},
                new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP));
        paint.setShadowLayer(7, 2, 4, 0xA8000000);
        canvas.drawRoundRect(face, radius, radius, paint);
        paint.clearShadowLayer();
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.25f);
        paint.setColor(0x8AF7DFD4);
        canvas.drawRoundRect(new RectF(face.left + 1, face.top + 1, face.right - 1, face.bottom - 1), radius, radius, paint);
        paint.setStrokeWidth(1f);
        paint.setColor(0x5AFFFFFF);
        canvas.drawArc(new RectF(face.left + 4, face.top + 3, face.right - 4, face.bottom - 5), 195, 150, false, paint);
        // The reference has a deliberately uneven dark rim: strongest on the right and bottom.
        paint.setStrokeWidth(1.35f);
        paint.setColor(0x78000000);
        canvas.drawArc(new RectF(face.left + 1.5f, face.top + 1.5f, face.right - 1.5f, face.bottom - 1.5f), 5, 150, false, paint);
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

    private static int withAlpha(int color, int alpha) { return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)); }
    private static int lighten(int color, float n) { return Color.rgb(Math.min(255, Math.round(Color.red(color) * n)), Math.min(255, Math.round(Color.green(color) * n)), Math.min(255, Math.round(Color.blue(color) * n))); }
    private static int darken(int color, float n) { return Color.rgb(Math.round(Color.red(color) * n), Math.round(Color.green(color) * n), Math.round(Color.blue(color) * n)); }
    private static int blend(int a, int b, float t) { return Color.rgb(Math.round(Color.red(a) * (1 - t) + Color.red(b) * t), Math.round(Color.green(a) * (1 - t) + Color.green(b) * t), Math.round(Color.blue(a) * (1 - t) + Color.blue(b) * t)); }
}
