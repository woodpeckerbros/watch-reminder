package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.TextView;

final class AppFont {
    private static Typeface base;

    private AppFont() {
    }

    static void apply(TextView view) {
        view.setTypeface(typeface(view.getContext()), Typeface.NORMAL);
    }

    static void bold(TextView view) {
        view.setTypeface(typeface(view.getContext()), Typeface.BOLD);
    }

    private static Typeface typeface(Context context) {
        if (base == null) {
            base = context.getResources().getFont(R.font.frank_ruhl_libre);
        }
        return base;
    }
}
