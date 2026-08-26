package com.woodpeckerbros.watchreminder;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

final class AppTextStyle {
    private static final float SHADOW_RADIUS_PX = 1.5f;
    private static final float SHADOW_OFFSET_X_PX = 1f;
    private static final float SHADOW_OFFSET_Y_PX = 1f;

    private AppTextStyle() {
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
