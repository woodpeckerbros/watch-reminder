package com.woodpeckerbros.watchreminder;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public final class AppTextStyle {
    /** Shared typography defaults for every TextView-derived UI element. */
    public static final float DEFAULT_BORDER_WIDTH_PX = 1f;

    private AppTextStyle() {
    }

    public static void apply(TextView view) {
        AppFont.apply(view);
        int textColor = view.getCurrentTextColor();
        applyBorder(view, contrastingColor(textColor), DEFAULT_BORDER_WIDTH_PX);
    }

    public static void apply(TextView view, int textColor, int borderColor, float borderWidthPx) {
        AppFont.apply(view);
        view.setTextColor(textColor);
        applyBorder(view, borderColor, borderWidthPx);
    }

    private static void applyBorder(TextView view, int borderColor, float borderWidthPx) {
        float width = Math.max(0f, borderWidthPx);
        if (width == 0f) {
            view.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
            return;
        }
        view.setShadowLayer(
                width,
                0f,
                0f,
                borderColor);
    }

    public static void apply(View view) {
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

    public static int contrastingColor(int textColor) {
        double red = Color.red(textColor) / 255.0;
        double green = Color.green(textColor) / 255.0;
        double blue = Color.blue(textColor) / 255.0;
        double luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue;
        return luminance < 0.5 ? Color.WHITE : Color.BLACK;
    }
}
