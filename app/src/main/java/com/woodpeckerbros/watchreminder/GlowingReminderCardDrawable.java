package com.woodpeckerbros.watchreminder;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Dark translucent card with a warm persistent halo for the nearest reminder. */
final class GlowingReminderCardDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float radius;
    GlowingReminderCardDrawable(float radius) { this.radius = radius; }
    @Override public void draw(Canvas canvas) {
        RectF b = new RectF(getBounds());
        b.inset(5, 5);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0,b.top,0,b.bottom,0xC9655548,0xD7142528, Shader.TileMode.CLAMP));
        paint.setShadowLayer(9,0,0,0xFFFFB98F);
        canvas.drawRoundRect(b,radius,radius,paint);
        paint.clearShadowLayer(); paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.3f); paint.setColor(0xFFFFE1C4);
        canvas.drawRoundRect(b,radius,radius,paint);
        paint.setStrokeWidth(1f); paint.setColor(0x99FF9E76);
        RectF inner=new RectF(b); inner.inset(3,3); canvas.drawRoundRect(inner,radius-2,radius-2,paint);
    }
    @Override public void setAlpha(int a){paint.setAlpha(a);} @Override public void setColorFilter(android.graphics.ColorFilter f){paint.setColorFilter(f);} @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
}
