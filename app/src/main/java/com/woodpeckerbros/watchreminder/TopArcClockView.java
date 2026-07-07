package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TopArcClockView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            invalidate();
            handler.postDelayed(this, 30_000L);
        }
    };

    public TopArcClockView(Context context) {
        super(context);
        paint.setColor(0xFFE6E6E6);
        paint.setTextSize(dp(17));
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setShadowLayer(dp(2), 0, dp(2), 0xEE000000);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.post(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width == 0) {
            width = dp(220);
        }
        setMeasuredDimension(width, dp(56));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        String text = timeFormat.format(new Date());
        path.reset();
        float width = getWidth();
        RectF oval = new RectF(-dp(6), dp(14), width + dp(6), dp(238));
        path.addArc(oval, 238, 64);
        canvas.drawTextOnPath(text, path, 0, 0, paint);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
