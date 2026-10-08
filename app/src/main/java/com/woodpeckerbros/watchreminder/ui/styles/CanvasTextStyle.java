package com.woodpeckerbros.watchreminder;

import android.graphics.Color;
import android.graphics.Paint;

final class CanvasTextStyle {
    private static final int DEFAULT_TEXT_COLOR = 0xFFE6E6E6;
    private static final int DEFAULT_BORDER_COLOR = Color.BLACK;
    private static final float DEFAULT_BORDER_WIDTH_PX = 1.5f;

    private final int textColor;
    private final int borderColor;
    private final float borderWidthPx;

    CanvasTextStyle() {
        this(DEFAULT_TEXT_COLOR, DEFAULT_BORDER_COLOR, DEFAULT_BORDER_WIDTH_PX);
    }

    CanvasTextStyle(int textColor, int borderColor, float borderWidthPx) {
        this.textColor = textColor;
        this.borderColor = borderColor;
        this.borderWidthPx = Math.max(0f, borderWidthPx);
    }

    void apply(Paint fillPaint, Paint borderPaint) {
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(textColor);

        borderPaint.set(fillPaint);
        borderPaint.clearShadowLayer();
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeJoin(Paint.Join.ROUND);
        borderPaint.setStrokeWidth(borderWidthPx);
        borderPaint.setColor(borderColor);
    }
}
