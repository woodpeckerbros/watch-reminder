package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.widget.Button;

/** Button that places its icon immediately to the physical left of the centered label. */
final class IconButton extends Button {
    private Drawable appIcon;
    private int iconGap;

    IconButton(Context context) {
        super(context);
    }

    void setAppIcon(Drawable icon, int gap) {
        appIcon = icon;
        iconGap = gap;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (appIcon == null || getText() == null) return;
        int iconWidth = appIcon.getBounds().width();
        int iconHeight = appIcon.getBounds().height();
        float textWidth = getPaint().measureText(getText().toString());
        int right = Math.round(getWidth() / 2f - textWidth / 2f - iconGap);
        int left = right - iconWidth;
        int top = Math.round((getHeight() - iconHeight) / 2f);
        appIcon.setBounds(left, top, right, top + iconHeight);
        appIcon.draw(canvas);
    }
}
