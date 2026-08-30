package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
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
    private View previousCard, selectedCard, nextCard, previousArrow, nextArrow;
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
        previousArrow = navigationArrow(-1);
        nextArrow = navigationArrow(1);
        addView(previousCard); addView(nextCard); addView(selectedCard); addView(previousArrow); addView(nextArrow);
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
        card.setPadding(dp(14), dp(8), dp(14), dp(7)); card.setBackground(background(true)); card.setElevation(dp(10));
        TextView icon = label(iconAt(selected), 24, 0xFFE2C89C);
        icon.setShadowLayer(dp(3), 0, dp(1), 0xAA0B2133);
        card.addView(icon, new LinearLayout.LayoutParams(-1, dp(29)));
        TextView title = label(value, 17, 0xFFF4EBDD); title.setMaxLines(2);
        title.setShadowLayer(dp(2), 0, dp(1), 0xBB0B2133);
        card.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView description = label(descriptionAt(selected), 10, 0xDFC5C8BA);
        description.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        description.setMaxLines(2);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, 0, 1);
        descriptionParams.setMargins(dp(3), dp(3), dp(3), dp(3));
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
            card.addView(configure, new LinearLayout.LayoutParams(dp(88), dp(32)));
        }
        TextView dots = label(positionDots(), 11, 0xFFE2C89C);
        dots.setLetterSpacing(.16f);
        card.addView(dots, new LinearLayout.LayoutParams(-1, dp(14)));
        return card;
    }

    private View sideCard(String value, boolean next) {
        FrameLayout card = new FrameLayout(getContext()); card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setBackground(background(false)); card.setAlpha(.82f); card.setElevation(dp(3));
        TextView title = label(value, 12, 0xFFC5C8BA); title.setMaxLines(3);
        title.setGravity(Gravity.CENTER_VERTICAL | (next ? Gravity.START : Gravity.END));
        card.addView(title, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        card.setOnClickListener(v -> move(next ? 1 : -1)); return card;
    }

    private View navigationArrow(int direction) {
        View arrow = new NavigationArrowView(getContext(), direction);
        arrow.setOnClickListener(v -> move(direction));
        return arrow;
    }

    private static final class NavigationArrowView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int direction;

        NavigationArrowView(Context context, int direction) {
            super(context); this.direction = direction;
            paint.setColor(0xFFFFE8BA); paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(context, 2.6f)); paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND); paint.setShadowLayer(dp(context, 4f), 0, dp(context, 1f), 0xFF06131E);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float centerX = getWidth() / 2f, centerY = getHeight() / 2f;
            float halfWidth = dp(getContext(), 5f), halfHeight = dp(getContext(), 8f);
            float tipX = centerX + direction * halfWidth;
            float backX = centerX - direction * halfWidth;
            canvas.drawLine(backX, centerY - halfHeight, tipX, centerY, paint);
            canvas.drawLine(tipX, centerY, backX, centerY + halfHeight, paint);
        }

        private static float dp(Context context, float value) {
            return value * context.getResources().getDisplayMetrics().density;
        }
    }

    private GradientDrawable background(boolean center) {
        GradientDrawable result = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                center ? new int[]{0xF0747D63, 0xF0344A43, 0xF0263936, 0xF05B6B58}
                        : new int[]{0xDD1A3042, 0xE00B2133, 0xDD263936});
        result.setCornerRadius(dp(center ? 19 : 17));
        result.setStroke(dp(center ? 2 : 1), center ? 0xFFE2C89C : 0x88747D63); return result;
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
        int centerWidth = Math.round(width * .64f), sideWidth = Math.round(width * .50f), sideHeight = Math.round(height * .70f);
        if (selectedCard != null) selectedCard.measure(MeasureSpec.makeMeasureSpec(centerWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height - dp(8), MeasureSpec.EXACTLY));
        int sw = MeasureSpec.makeMeasureSpec(sideWidth, MeasureSpec.EXACTLY), sh = MeasureSpec.makeMeasureSpec(sideHeight, MeasureSpec.EXACTLY);
        if (previousCard != null) previousCard.measure(sw, sh); if (nextCard != null) nextCard.measure(sw, sh);
        int arrowSpec = MeasureSpec.makeMeasureSpec(dp(40), MeasureSpec.EXACTLY);
        if (previousArrow != null) previousArrow.measure(arrowSpec, arrowSpec);
        if (nextArrow != null) nextArrow.measure(arrowSpec, arrowSpec);
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l, height = b - t, centerWidth = selectedCard == null ? 0 : selectedCard.getMeasuredWidth();
        int centerLeft = (width - centerWidth) / 2;
        if (previousCard != null) { int y = (height - previousCard.getMeasuredHeight()) / 2, right = centerLeft - dp(5); previousCard.layout(right - previousCard.getMeasuredWidth(), y, right, y + previousCard.getMeasuredHeight()); }
        if (nextCard != null) { int y = (height - nextCard.getMeasuredHeight()) / 2, left = centerLeft + centerWidth + dp(5); nextCard.layout(left, y, left + nextCard.getMeasuredWidth(), y + nextCard.getMeasuredHeight()); }
        if (selectedCard != null) selectedCard.layout(centerLeft, dp(4), centerLeft + centerWidth, height - dp(4));
        int arrowSize = dp(40), arrowY = (height - arrowSize) / 2;
        if (previousArrow != null) previousArrow.layout(0, arrowY, arrowSize, arrowY + arrowSize);
        if (nextArrow != null) nextArrow.layout(width - arrowSize, arrowY, width, arrowY + arrowSize);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = event.getX(); downY = event.getY(); horizontalDrag = false;
            getParent().requestDisallowInterceptTouchEvent(true);
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float dx = Math.abs(event.getX() - downX), dy = Math.abs(event.getY() - downY);
            if (dx > dp(12) && dx > dy * 1.25f) horizontalDrag = true;
            if (dy > dp(10) && dy > dx * 1.2f) getParent().requestDisallowInterceptTouchEvent(false);
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP && horizontalDrag) {
            MotionEvent cancel = MotionEvent.obtain(event);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel); cancel.recycle();
            getParent().requestDisallowInterceptTouchEvent(false);
            float dx = event.getX() - downX;
            horizontalDrag = false;
            if (Math.abs(dx) >= dp(32)) move(dx < 0 ? 1 : -1);
            return true;
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            getParent().requestDisallowInterceptTouchEvent(false); horizontalDrag = false;
        }
        return super.dispatchTouchEvent(event);
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
