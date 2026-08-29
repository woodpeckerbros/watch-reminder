package com.woodpeckerbros.watchreminder;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Small semantic line icons for text buttons; avoids font-dependent emoji rendering. */
final class AppButtonIconDrawable extends Drawable {
    enum Kind { PLUS, ADD, HISTORY, LIST, ALARM, CLOCK, BELL, SETTINGS, BACK, SAVE, DELETE, FILE, PHONE, LOCATION, SOUND, INFO }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Kind kind;

    AppButtonIconDrawable(Kind kind, int color, float strokeWidth) {
        this.kind = kind;
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override public void draw(Canvas c) {
        RectF b = new RectF(getBounds());
        float x = b.centerX(), y = b.centerY(), s = Math.min(b.width(), b.height()) / 24f;
        c.save(); c.translate(x, y); c.scale(s, s);
        switch (kind) {
            case PLUS:
                plus(c, 0, 0, 7f);
                break;
            case ADD:
                plus(c, -6, 2, 3.5f);
                c.drawCircle(4, -5, 3, paint);
                Path person = new Path();
                person.moveTo(-1, 8);
                person.quadTo(0, 1, 4, 1);
                person.quadTo(8, 1, 9, 8);
                c.drawPath(person, paint);
                break;
            case HISTORY: c.drawRoundRect(new RectF(-9,-7,9,8),3,3,paint); c.drawLine(-7,-3,7,-3,paint); c.drawLine(-5,-9,5,-9,paint); c.drawLine(-4,1,4,1,paint); break;
            case LIST: for(int i=-6;i<=6;i+=6){ c.drawCircle(-7,i,1.2f,paint); c.drawLine(-3,i,8,i,paint);} break;
            case ALARM: c.drawCircle(0,2,8,paint); c.drawLine(0,2,0,-3,paint); c.drawLine(0,2,4,4,paint); c.drawArc(new RectF(-10,-10,-2,-3),205,130,false,paint); c.drawArc(new RectF(2,-10,10,-3),205,130,false,paint); break;
            case CLOCK: c.drawCircle(0,0,9,paint); c.drawLine(0,0,0,-5,paint); c.drawLine(0,0,5,2,paint); break;
            case BELL: Path bell=new Path(); bell.moveTo(-8,6); bell.quadTo(-4,2,-4,-3); bell.quadTo(0,-10,4,-3); bell.quadTo(4,2,8,6); bell.close(); c.drawPath(bell,paint); c.drawCircle(0,8,1.4f,paint); break;
            case SETTINGS:
                Path gear = new Path();
                for (int i = 0; i < 24; i++) {
                    double a = -Math.PI / 2 + i * Math.PI * 2 / 24;
                    float gearRadius = i % 3 == 1 ? 7f : 9.5f;
                    float gx = (float) Math.cos(a) * gearRadius;
                    float gy = (float) Math.sin(a) * gearRadius;
                    if (i == 0) gear.moveTo(gx, gy); else gear.lineTo(gx, gy);
                }
                gear.close();
                c.drawPath(gear, paint);
                c.drawCircle(0, 0, 3f, paint);
                break;
            case BACK: c.drawLine(7,-7,-2,0,paint); c.drawLine(-2,0,7,7,paint); c.drawLine(-2,0,10,0,paint); break;
            case SAVE: c.drawRoundRect(new RectF(-8,-9,8,9),2,2,paint); c.drawRect(-4,-9,4,-3,paint); c.drawRect(-4,2,4,8,paint); break;
            case DELETE: c.drawRoundRect(new RectF(-6,-5,6,9),2,2,paint); c.drawLine(-8,-7,8,-7,paint); c.drawLine(-3,-10,3,-10,paint); break;
            case FILE: c.drawRect(-7,-9,7,9,paint); c.drawLine(-3,-3,4,-3,paint); c.drawLine(-3,2,4,2,paint); break;
            case PHONE: c.drawRoundRect(new RectF(-6,-10,6,10),2,2,paint); c.drawCircle(0,7,1,paint); break;
            case LOCATION: Path p=new Path(); p.moveTo(0,10); p.cubicTo(-12,-1,-7,-10,0,-10); p.cubicTo(7,-10,12,-1,0,10); c.drawPath(p,paint); c.drawCircle(0,-3,2.5f,paint); break;
            case SOUND: c.drawRect(-9,-3,-5,3,paint); c.drawLine(-5,-3,1,-8,paint); c.drawLine(1,-8,1,8,paint); c.drawLine(1,8,-5,3,paint); c.drawArc(new RectF(-1,-7,10,7),285,150,false,paint); break;
            case INFO: c.drawCircle(0,0,9,paint); c.drawCircle(0,-5,0.8f,paint); c.drawLine(0,-1,0,6,paint); break;
        }
        c.restore();
    }
    private void plus(Canvas c,float x,float y,float r){c.drawLine(x-r,y,x+r,y,paint);c.drawLine(x,y-r,x,y+r,paint);}
    @Override public void setAlpha(int a){paint.setAlpha(a);} @Override public void setColorFilter(android.graphics.ColorFilter f){paint.setColorFilter(f);} @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
}
