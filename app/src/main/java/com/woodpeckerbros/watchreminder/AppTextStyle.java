package com.woodpeckerbros.watchreminder;

import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

final class AppTextStyle {
    private static final int DEFAULT_TEXT_COLOR = 0xFFE6E6E6;
    private static final int DEFAULT_BORDER_COLOR = Color.BLACK;
    private static final float DEFAULT_BORDER_WIDTH_PX = 1.5f;
    private static final float SHADOW_RADIUS_PX = 1.5f;
    private static final float SHADOW_OFFSET_X_PX = 1f;
    private static final float SHADOW_OFFSET_Y_PX = 1f;

    private AppTextStyle() {
    }

    static void initialize(Paint fillPaint, Paint borderPaint) {
        initialize(
                fillPaint,
                borderPaint,
                DEFAULT_TEXT_COLOR,
                DEFAULT_BORDER_COLOR,
                DEFAULT_BORDER_WIDTH_PX
        );
    }

    static void initialize(
            Paint fillPaint,
            Paint borderPaint,
            int textColor,
            int borderColor,
            float borderWidthPx
    ) {
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(textColor);

        borderPaint.set(fillPaint);
        borderPaint.clearShadowLayer();
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeJoin(Paint.Join.ROUND);
        borderPaint.setStrokeWidth(Math.max(0f, borderWidthPx));
        borderPaint.setColor(borderColor);
    }

    static void apply(TextView view) {
        AppFont.apply(view);
        view.setShadowLayer(
                SHADOW_RADIUS_PX,
                SHADOW_OFFSET_X_PX,
                SHADOW_OFFSET_Y_PX,
                Color.BLACK);
    }

    static void apply(View view) {
        if (view instanceof TextView) {
            apply((TextView) view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                apply(group.getChildAt(index));
            }
        }
    }
}
