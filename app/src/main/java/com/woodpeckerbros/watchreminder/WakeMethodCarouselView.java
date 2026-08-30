package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Three-card carousel whose large center card contains the selected method settings. */
public final class WakeMethodCarouselView extends ViewGroup {
    public interface Listener { void onSelectionChanged(int index); }
    public interface SettingsListener { void onSettingsRequested(int index); }
    private String[] labels = new String[0];
    private String[] descriptions = new String[0];
    private boolean[] configurable = new boolean[0];
    private int selected;
    private Listener listener;
    private SettingsListener settingsListener;
    private View previousCard, selectedCard, nextCard;
    private float downX, downY;
    private boolean horizontalDrag;

    public WakeMethodCarouselView(Context context) {
        super(context); setClipChildren(false); setClipToPadding(false);
    }

    public void configure(String[] values, String[] details, boolean[] hasSettings, int initial,
                          Listener selectionListener, SettingsListener configureListener) {
        labels = values == null ? new String[0] : values.clone();
        descriptions = details == null ? new String[0] : details.clone();
        configurable = hasSettings == null ? new boolean[0] : hasSettings.clone();
        selected = labels.length == 0 ? 0 : Math.max(0, Math.min(labels.length - 1, initial));
        listener = selectionListener; settingsListener = configureListener; rebuild(0);
    }

    private void rebuild(int direction) {
        if (labels.length == 0) return;
        removeAllViews();
        previousCard = sideCard(labelAt(selected - 1), false);
        nextCard = sideCard(labelAt(selected + 1), true);
        selectedCard = centerCard(labels[selected]);
        addView(previousCard); addView(nextCard); addView(selectedCard);
        if (direction != 0) {
            selectedCard.setAlpha(.72f);
            selectedCard.setScaleX(.9f);
            selectedCard.setScaleY(.9f);
            selectedCard.setTranslationX(dp(direction > 0 ? 42 : -42));
            selectedCard.animate().alpha(1f).scaleX(1f).scaleY(1f).translationX(0f)
                    .setDuration(220).start();
            previousCard.setAlpha(.25f);
            nextCard.setAlpha(.25f);
            previousCard.animate().alpha(.58f).setDuration(220).start();
            nextCard.animate().alpha(.58f).setDuration(220).start();
        }
        requestLayout();
    }

    private View centerCard(String value) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL); card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(12), dp(9), dp(12), dp(8)); card.setBackground(background(true)); card.setElevation(dp(10));
        TextView icon = label(iconAt(selected), 27, 0xFFE2C89C);
        icon.setShadowLayer(dp(3), 0, dp(1), 0xAA0B2133);
        card.addView(icon, new LinearLayout.LayoutParams(-1, dp(34)));
        TextView title = label(value, 19, 0xFFF4EBDD); title.setMaxLines(2);
        title.setShadowLayer(dp(2), 0, dp(1), 0xBB0B2133);
        card.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView description = label(descriptionAt(selected), 10, 0xDFC5C8BA);
        description.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        description.setMaxLines(2);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, 0, 1);
        descriptionParams.setMargins(0, dp(4), 0, dp(3));
        card.addView(description, descriptionParams);
        if (isConfigurable(selected)) {
            Button configure = new Button(getContext());
            configure.setText(UiText.t(getContext(), "הגדר")); configure.setTextSize(12); configure.setTextColor(0xFFF4EBDD);
            configure.setAllCaps(false); configure.setGravity(Gravity.CENTER);
            GradientDrawable configureBackground = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xDD747D63, 0xDD344A43});
            configureBackground.setCornerRadius(dp(18)); configureBackground.setStroke(dp(1), 0xCCE2C89C);
            configure.setBackground(configureBackground); configure.setMinHeight(0); configure.setMinimumHeight(0);
            configure.setPadding(dp(14), 0, dp(14), 0);
            configure.setOnClickListener(v -> { if (settingsListener != null) settingsListener.onSettingsRequested(selected); });
            card.addView(configure, new LinearLayout.LayoutParams(dp(82), dp(34)));
        }
        TextView dots = label(positionDots(), 11, 0xFFE2C89C);
        dots.setLetterSpacing(.16f);
        card.addView(dots, new LinearLayout.LayoutParams(-1, dp(16)));
        return card;
    }

    private View sideCard(String value, boolean next) {
        FrameLayout card = new FrameLayout(getContext()); card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setBackground(background(false)); card.setAlpha(.7f); card.setElevation(dp(3));
        TextView title = label(value, 12, 0xFFC5C8BA); title.setMaxLines(3);
        title.setGravity(Gravity.CENTER_VERTICAL | (next ? Gravity.START : Gravity.END));
        card.addView(title, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        card.setOnClickListener(v -> move(next ? 1 : -1)); return card;
    }

    private GradientDrawable background(boolean center) {
        GradientDrawable result = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                center ? new int[]{0xF0747D63, 0xF0344A43, 0xF0263936, 0xF05B6B58}
                        : new int[]{0xDD1A3042, 0xE00B2133, 0xDD263936});
        result.setCornerRadius(dp(center ? 19 : 17));
        result.setStroke(dp(center ? 2 : 1), center ? 0xFFE2C89C : 0x55747D63); return result;
    }

    private TextView label(String value, int size, int color) {
        TextView result = new TextView(getContext()); result.setText(value); result.setTextColor(color);
        result.setTextSize(size); result.setGravity(Gravity.CENTER);
        result.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)); return result;
    }

    private String labelAt(int index) { return labels[(index % labels.length + labels.length) % labels.length]; }
    private String descriptionAt(int index) {
        return index >= 0 && index < descriptions.length ? descriptions[index] : "";
    }
    private boolean isConfigurable(int index) {
        return index >= 0 && index < configurable.length && configurable[index];
    }

    private String iconAt(int index) {
        String[] icons = {"◎", "◷", "×2", "↻", "⇧", "＋−", "◇", "⇄", "✦", "◆"};
        return icons[(index % icons.length + icons.length) % icons.length];
    }

    private String positionDots() {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < labels.length; index++) {
            if (index > 0) result.append(' ');
            result.append(index == selected ? '●' : '·');
        }
        return result.toString();
    }
    private void move(int direction) {
        selected = (selected + direction + labels.length) % labels.length;
        if (listener != null) listener.onSelectionChanged(selected); rebuild(direction);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = resolveSize(dp(300), heightSpec);
        int centerWidth = Math.round(width * .58f), sideWidth = Math.round(width * .48f), sideHeight = Math.round(height * .72f);
        if (selectedCard != null) selectedCard.measure(MeasureSpec.makeMeasureSpec(centerWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height - dp(8), MeasureSpec.EXACTLY));
        int sw = MeasureSpec.makeMeasureSpec(sideWidth, MeasureSpec.EXACTLY), sh = MeasureSpec.makeMeasureSpec(sideHeight, MeasureSpec.EXACTLY);
        if (previousCard != null) previousCard.measure(sw, sh); if (nextCard != null) nextCard.measure(sw, sh);
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l, height = b - t, centerWidth = selectedCard == null ? 0 : selectedCard.getMeasuredWidth();
        int centerLeft = (width - centerWidth) / 2;
        if (previousCard != null) { int y = (height - previousCard.getMeasuredHeight()) / 2, right = centerLeft + dp(14); previousCard.layout(right - previousCard.getMeasuredWidth(), y, right, y + previousCard.getMeasuredHeight()); }
        if (nextCard != null) { int y = (height - nextCard.getMeasuredHeight()) / 2, left = centerLeft + centerWidth - dp(14); nextCard.layout(left, y, left + nextCard.getMeasuredWidth(), y + nextCard.getMeasuredHeight()); }
        if (selectedCard != null) selectedCard.layout(centerLeft, dp(4), centerLeft + centerWidth, height - dp(4));
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) { downX = event.getX(); downY = event.getY(); horizontalDrag = false; }
        else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float dx = Math.abs(event.getX() - downX), dy = Math.abs(event.getY() - downY);
            if (dx > dp(12) && dx > dy * 1.25f) { horizontalDrag = true; return true; }
        }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP && horizontalDrag) {
            float dx = event.getX() - downX; if (Math.abs(dx) >= dp(32)) move(dx < 0 ? 1 : -1);
            horizontalDrag = false; return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) horizontalDrag = false;
        return true;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
