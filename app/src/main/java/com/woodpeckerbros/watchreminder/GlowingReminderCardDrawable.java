package com.woodpeckerbros.watchreminder;

import android.graphics.Canvas;
import android.graphics.BlurMaskFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Dark translucent card with a warm persistent halo for the nearest reminder. */
final class GlowingReminderCardDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float radius;
    private final boolean prominent;
    GlowingReminderCardDrawable(float radius) { this(radius, true); }
    GlowingReminderCardDrawable(float radius, boolean prominent) {
        this.radius = radius;
        this.prominent = prominent;
    }
    @Override public void draw(Canvas canvas) {
        RectF bounds = new RectF(getBounds());
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(prominent ? 8f : 3.5f);
        paint.setColor(prominent ? 0xFFFFB184 : 0x6CFFB184);
        paint.setMaskFilter(new BlurMaskFilter(prominent ? 15f : 8f, BlurMaskFilter.Blur.NORMAL));
        RectF halo = new RectF(bounds);
        halo.inset(prominent ? 16f : 8f, prominent ? 16f : 8f);
        canvas.drawRoundRect(halo, radius + 3f, radius + 3f, paint);
        paint.setMaskFilter(null);
        if (prominent) {
            paint.setStrokeWidth(6f);
            paint.setColor(0xE8FFD0A6);
            paint.setMaskFilter(new BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL));
            canvas.drawRoundRect(halo, radius + 3f, radius + 3f, paint);
            paint.setMaskFilter(null);
        }
        RectF b = new RectF(bounds);
        b.inset(prominent ? 17 : 9, prominent ? 17 : 9);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0,b.top,0,b.bottom,
                prominent ? 0xC9655548 : 0xB94A4B45,
                prominent ? 0xD7142528 : 0xC4142528, Shader.TileMode.CLAMP));
        paint.setShadowLayer(prominent ? 15 : 7, 0, 0,
                prominent ? 0xE8FFB98F : 0x70FFB98F);
        canvas.drawRoundRect(b,radius,radius,paint);
        paint.clearShadowLayer(); paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(prominent ? 2.3f : 1.2f);
        paint.setColor(prominent ? 0xFFFFE1C4 : 0x99FFD0AF);
        canvas.drawRoundRect(b,radius,radius,paint);
        if (prominent) {
            paint.setStrokeWidth(1f); paint.setColor(0x99FF9E76);
            RectF inner=new RectF(b); inner.inset(3,3); canvas.drawRoundRect(inner,radius-2,radius-2,paint);
        }
    }

    @Override public void setAlpha(int a){paint.setAlpha(a);} @Override public void setColorFilter(android.graphics.ColorFilter f){paint.setColorFilter(f);} @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
}
