package com.woodpeckerbros.watchreminder;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

final class ScreenHelpTour {
    static final class Step {
        final View target;
        final String title;
        final String explanation;

        Step(View target, String title, String explanation) {
            this.target = target;
            this.title = title;
            this.explanation = explanation;
        }
    }

    private final FrameLayout root;
    private final ScrollView scrollView;
    private final List<Step> steps;
    private final HighlightLayer highlightLayer;
    private final TextView titleView;
    private final TextView explanationView;
    private final TextView progressView;
    private final Button previousButton;
    private final Button nextButton;
    private final LinearLayout card;
    private final String nextText;
    private final String finishText;
    private final Runnable onClose;
    private final int originalPaddingLeft;
    private final int originalPaddingTop;
    private final int originalPaddingRight;
    private final int originalPaddingBottom;
    private final boolean originalClipToPadding;
    private int currentIndex;

    ScreenHelpTour(
            FrameLayout root,
            ScrollView scrollView,
            List<Step> steps,
            String previousText,
            String nextText,
            String finishText,
            String closeText,
            Runnable onClose
    ) {
        this.root = root;
        this.scrollView = scrollView;
        this.steps = steps;
        this.nextText = nextText;
        this.finishText = finishText;
        this.onClose = onClose;
        originalPaddingLeft = scrollView.getPaddingLeft();
        originalPaddingTop = scrollView.getPaddingTop();
        originalPaddingRight = scrollView.getPaddingRight();
        originalPaddingBottom = scrollView.getPaddingBottom();
        originalClipToPadding = scrollView.getClipToPadding();
        scrollView.setPadding(
                originalPaddingLeft,
                originalPaddingTop,
                originalPaddingRight,
                dp(145)
        );
        scrollView.setClipToPadding(false);

        highlightLayer = new HighlightLayer(root);
        root.addView(highlightLayer, new FrameLayout.LayoutParams(-1, -1));

        card = new LinearLayout(root.getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(10), dp(7), dp(10), dp(7));
        card.setBackground(rounded(0xF21A3042, dp(18), 0xFFE2C89C, dp(1)));

        progressView = label(9, 0xFFC5C8BA);
        progressView.setTextDirection(View.TEXT_DIRECTION_LTR);
        titleView = label(15, 0xFFF4EBDD);
        explanationView = label(11, 0xFFF4EBDD);
        explanationView.setPadding(0, dp(2), 0, dp(4));
        card.addView(progressView, new LinearLayout.LayoutParams(-1, -2));
        card.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
        card.addView(explanationView, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(root.getContext());
        actions.setGravity(Gravity.CENTER);
        actions.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        previousButton = actionButton(previousText, 0xFF344A43);
        Button closeButton = actionButton(closeText, 0xFF344A43);
        nextButton = actionButton(nextText, 0xFF747D63);
        previousButton.setOnClickListener(view -> showStep(currentIndex - 1));
        closeButton.setOnClickListener(view -> close());
        nextButton.setOnClickListener(view -> {
            if (currentIndex >= steps.size() - 1) {
                close();
            } else {
                showStep(currentIndex + 1);
            }
        });
        actions.addView(previousButton);
        actions.addView(closeButton);
        actions.addView(nextButton);
        card.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(-1, -2);
        cardParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        cardParams.setMargins(dp(22), 0, dp(22), dp(35));
        root.addView(card, cardParams);
    }

    void start() {
        if (steps.isEmpty()) {
            close();
            return;
        }
        showStep(0);
    }

    private void showStep(int index) {
        if (index < 0 || index >= steps.size()) {
            return;
        }
        currentIndex = index;
        Step step = steps.get(index);
        progressView.setText("\u200E" + (index + 1) + " / " + steps.size() + "\u200E");
        titleView.setText(step.title);
        explanationView.setText(step.explanation);
        previousButton.setVisibility(index == 0 ? View.INVISIBLE : View.VISIBLE);
        nextButton.setText(index == steps.size() - 1 ? finishText : nextText);

        int[] targetLocation = new int[2];
        int[] scrollLocation = new int[2];
        step.target.getLocationOnScreen(targetLocation);
        scrollView.getLocationOnScreen(scrollLocation);
        int targetTop = targetLocation[1] - scrollLocation[1] + scrollView.getScrollY();
        scrollView.smoothScrollTo(0, Math.max(0, targetTop - dp(15)));
        root.postDelayed(() -> updateHighlight(step.target), 350L);
    }

    private void updateHighlight(View target) {
        int[] rootLocation = new int[2];
        int[] targetLocation = new int[2];
        root.getLocationOnScreen(rootLocation);
        target.getLocationOnScreen(targetLocation);
        float left = targetLocation[0] - rootLocation[0] - dp(5);
        float top = targetLocation[1] - rootLocation[1] - dp(5);
        float right = left + target.getWidth() + dp(10);
        float bottom = top + target.getHeight() + dp(10);
        highlightLayer.setHighlight(new RectF(left, top, right, bottom));
    }

    private void close() {
        root.removeView(highlightLayer);
        root.removeView(card);
        scrollView.setPadding(
                originalPaddingLeft,
                originalPaddingTop,
                originalPaddingRight,
                originalPaddingBottom
        );
        scrollView.setClipToPadding(originalClipToPadding);
        if (onClose != null) {
            onClose.run();
        }
    }

    private TextView label(int sizeSp, int color) {
        TextView view = new TextView(root.getContext());
        AppFont.apply(view);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        AppTextStyle.apply(view);
        return view;
    }

    private Button actionButton(String text, int color) {
        Button button = new Button(root.getContext());
        AppFont.apply(button);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setPadding(dp(5), 0, dp(5), 0);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setBackground(rounded(color, dp(14), 0, 0));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(28), 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        button.setLayoutParams(params);
        AppTextStyle.apply(button);
        return button;
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != 0 && strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * root.getResources().getDisplayMetrics().density);
    }

    private static final class HighlightLayer extends View {
        private final Paint dimPaint = new Paint();
        private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private RectF highlight;

        HighlightLayer(View root) {
            super(root.getContext());
            dimPaint.setColor(0xB8000000);
            clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(3f);
            borderPaint.setColor(0xFFE2C89C);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void setHighlight(RectF highlight) {
            this.highlight = highlight;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);
            if (highlight != null) {
                canvas.drawRoundRect(highlight, 18f, 18f, clearPaint);
                canvas.drawRoundRect(highlight, 18f, 18f, borderPaint);
            }
        }
    }
}
