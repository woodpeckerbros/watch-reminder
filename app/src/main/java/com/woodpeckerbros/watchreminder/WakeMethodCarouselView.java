package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/** Compact, swipeable selector designed for a round Wear OS screen. */
public final class WakeMethodCarouselView extends View {
    public interface Listener { void onSelectionChanged(int index); }

    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subtle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path chevron = new Path();
    private String[] labels = new String[0];
    private int selected;
    private float downX;
    private Listener listener;

    public WakeMethodCarouselView(Context context) {
        super(context);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(dp(1));
        border.setColor(0x88FFD9A0);
        subtle.setStrokeWidth(dp(1.5f));
        subtle.setStyle(Paint.Style.STROKE);
        subtle.setStrokeCap(Paint.Cap.ROUND);
        subtle.setColor(0xB8F8EBDD);
        text.setColor(0xFFF9EEE3);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
    }

    public void configure(String[] values, int initial, Listener selectionListener) {
        labels = values == null ? new String[0] : values.clone();
        selected = labels.length == 0 ? 0 : Math.max(0, Math.min(labels.length - 1, initial));
        listener = selectionListener;
        invalidate();
    }

    public int selectedIndex() { return selected; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (labels.length == 0) return;
        float cardLeft = dp(25), cardRight = getWidth() - dp(25);
        float cardTop = dp(5), cardBottom = getHeight() - dp(21);
        RectF card = new RectF(cardLeft, cardTop, cardRight, cardBottom);
        float radius = dp(24);
        glass.setShader(new LinearGradient(0, cardTop, 0, cardBottom,
                new int[]{0x803F4D4D, 0x66303B3B, 0x70303A3C}, null, Shader.TileMode.CLAMP));
        glass.setShadowLayer(dp(7), 0, dp(3), 0x66000000);
        canvas.drawRoundRect(card, radius, radius, glass);
        glass.clearShadowLayer();
        canvas.drawRoundRect(card, radius, radius, border);

        String value = labels[selected];
        float available = card.width() - dp(42);
        float size = sp(17);
        text.setTextSize(size);
        while (size > sp(11.5f) && text.measureText(value) > available) {
            size -= sp(.5f); text.setTextSize(size);
        }
        Paint.FontMetrics metrics = text.getFontMetrics();
        float centerY = (cardTop + cardBottom) / 2f - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(value, getWidth() / 2f, centerY, text);

        drawChevron(canvas, dp(12), (cardTop + cardBottom) / 2f, false);
        drawChevron(canvas, getWidth() - dp(12), (cardTop + cardBottom) / 2f, true);

        float dotGap = dp(6);
        float startX = getWidth() / 2f - (labels.length - 1) * dotGap / 2f;
        float dotY = getHeight() - dp(8);
        subtle.setStyle(Paint.Style.FILL);
        for (int i = 0; i < labels.length; i++) {
            subtle.setColor(i == selected ? 0xFFFFD58E : 0x557F898D);
            canvas.drawCircle(startX + i * dotGap, dotY, i == selected ? dp(2.2f) : dp(1.35f), subtle);
        }
        subtle.setStyle(Paint.Style.STROKE);
    }

    private void drawChevron(Canvas canvas, float centerX, float centerY, boolean right) {
        float direction = right ? 1f : -1f;
        chevron.reset();
        chevron.moveTo(centerX - direction * dp(2.5f), centerY - dp(5));
        chevron.lineTo(centerX + direction * dp(2.5f), centerY);
        chevron.lineTo(centerX - direction * dp(2.5f), centerY + dp(5));
        canvas.drawPath(chevron, subtle);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (labels.length == 0) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = event.getX(); setPressed(true); return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) { setPressed(false); return true; }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            setPressed(false);
            float delta = event.getX() - downX;
            int direction;
            if (Math.abs(delta) >= dp(28)) direction = delta < 0 ? 1 : -1;
            else direction = event.getX() >= getWidth() / 2f ? 1 : -1;
            selected = (selected + direction + labels.length) % labels.length;
            performClick(); invalidate();
            if (listener != null) listener.onSelectionChanged(selected);
            return true;
        }
        return true;
    }

    @Override public boolean performClick() { super.performClick(); return true; }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
}
