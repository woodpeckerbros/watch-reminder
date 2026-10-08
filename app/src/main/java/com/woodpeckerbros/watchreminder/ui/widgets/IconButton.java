package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.Layout;
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
        if (appIcon == null || getText() == null) {
            super.onDraw(canvas);
            return;
        }
        int iconWidth = appIcon.getBounds().width();
        int iconHeight = appIcon.getBounds().height();
        float groupShift = (iconWidth + iconGap) / 2f;
        canvas.save();
        canvas.translate(groupShift, 0f);
        super.onDraw(canvas);
        canvas.restore();
        float textWidth = widestLineWidth();
        int right = Math.round(getWidth() / 2f + groupShift - textWidth / 2f - iconGap);
        int left = right - iconWidth;
        int top = Math.round((getHeight() - iconHeight) / 2f);
        appIcon.setBounds(left, top, right, top + iconHeight);
        appIcon.draw(canvas);
    }

    private float widestLineWidth() {
        Layout layout = getLayout();
        if (layout == null || layout.getLineCount() == 0) {
            return getPaint().measureText(getText().toString());
        }
        float widest = 0f;
        for (int line = 0; line < layout.getLineCount(); line++) {
            widest = Math.max(widest, layout.getLineWidth(line));
        }
        return widest;
    }
}
