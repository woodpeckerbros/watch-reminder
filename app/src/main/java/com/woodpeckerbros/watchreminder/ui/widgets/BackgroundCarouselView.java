package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** Image-first carousel used to preview Smart Alarm alert backgrounds. */
public final class BackgroundCarouselView extends ViewGroup {
    public interface Listener { void onSelectionChanged(int index); }

    private String[] labels = new String[0];
    private int[] images = new int[0];
    private int selected;
    private Listener listener;
    private View previousCard, selectedCard, nextCard, previousArrow, nextArrow;
    private float downX, downY;
    private boolean horizontalDrag;

    public BackgroundCarouselView(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
    }

    public void configure(String[] values, int[] drawableIds, int initial, Listener selectionListener) {
        labels = values == null ? new String[0] : values.clone();
        images = drawableIds == null ? new int[0] : drawableIds.clone();
        selected = labels.length == 0 ? 0 : Math.max(0, Math.min(labels.length - 1, initial));
        listener = selectionListener;
        rebuild(0);
    }

    private void rebuild(int direction) {
        if (labels.length == 0) return;
        removeAllViews();
        previousCard = card(indexAt(selected - 1), false, false);
        nextCard = card(indexAt(selected + 1), false, true);
        selectedCard = card(selected, true, false);
        previousArrow = arrow(-1);
        nextArrow = arrow(1);
        addView(previousCard); addView(nextCard); addView(selectedCard); addView(previousArrow); addView(nextArrow);
        if (direction != 0) {
            selectedCard.setAlpha(.7f); selectedCard.setScaleX(.9f); selectedCard.setScaleY(.9f);
            selectedCard.setTranslationX(dp(direction > 0 ? 40 : -40));
            selectedCard.animate().alpha(1f).scaleX(1f).scaleY(1f).translationX(0).setDuration(220).start();
        }
        requestLayout();
    }

    private View card(int index, boolean center, boolean next) {
        FrameLayout card = new FrameLayout(getContext());
        card.setClipToOutline(true); card.setBackground(cardOutline(center));
        addPreview(card, index);
        View shade = new View(getContext()); shade.setBackgroundColor(center ? 0x35030B12 : 0x78030B12);
        card.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        TextView title = new TextView(getContext()); title.setText(labels[index]); title.setTextColor(0xFFFFE8BA);
        title.setTextSize(center ? 15 : 11); title.setGravity(Gravity.CENTER); title.setMaxLines(2);
        title.setPadding(dp(5), dp(5), dp(5), dp(4)); title.setBackground(titleBackground());
        AppTextStyle.apply(title); AppFont.bold(title);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        titleParams.setMargins(center ? dp(9) : dp(5), center ? dp(8) : dp(5), center ? dp(9) : dp(5), 0);
        card.addView(title, titleParams);
        if (!center) {
            card.setAlpha(.74f);
            card.setOnClickListener(v -> move(next ? 1 : -1));
        }
        return card;
    }

    private void addPreview(FrameLayout card, int index) {
        if (index == images.length - 1) {
            GridLayout mosaic = new GridLayout(getContext()); mosaic.setColumnCount(2); mosaic.setRowCount(2);
            int firstDayPartImage = Math.max(0, images.length - 5);
            for (int imageIndex = 0; imageIndex < Math.min(4, images.length - 1); imageIndex++) {
                ImageView image = image(images[firstDayPartImage + imageIndex]);
                GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                        GridLayout.spec(imageIndex / 2, 1f), GridLayout.spec(imageIndex % 2, 1f));
                params.width = 0; params.height = 0;
                mosaic.addView(image, params);
            }
            card.addView(mosaic, new FrameLayout.LayoutParams(-1, -1));
        } else {
            card.addView(image(images[Math.min(index, images.length - 1)]), new FrameLayout.LayoutParams(-1, -1));
        }
    }

    private ImageView image(int drawableId) {
        ImageView image = new ImageView(getContext()); image.setImageResource(drawableId);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP); return image;
    }

    private GradientDrawable cardOutline(boolean center) {
        GradientDrawable result = new GradientDrawable(); result.setColor(0x17000000);
        result.setCornerRadius(dp(center ? 20 : 17));
        result.setStroke(dp(center ? 2 : 1), center ? 0xFFFFD58A : 0xAA9F886D);
        return result;
    }

    private GradientDrawable titleBackground() {
        GradientDrawable result = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xD51A2630, 0xA5091722});
        result.setCornerRadius(dp(14)); result.setStroke(dp(1), 0x88FFE0A5); return result;
    }

    private View arrow(int direction) {
        View arrow = new ArrowView(getContext(), direction); arrow.setOnClickListener(v -> move(direction)); return arrow;
    }

    private void move(int direction) {
        selected = indexAt(selected + direction);
        if (listener != null) listener.onSelectionChanged(selected);
        rebuild(direction);
    }

    private int indexAt(int index) { return (index % labels.length + labels.length) % labels.length; }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = resolveSize(dp(170), heightSpec);
        int centerWidth = Math.round(width * .66f), sideWidth = Math.round(width * .48f);
        int sideHeight = Math.round(height * .72f);
        selectedCard.measure(MeasureSpec.makeMeasureSpec(centerWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height - dp(8), MeasureSpec.EXACTLY));
        int sw = MeasureSpec.makeMeasureSpec(sideWidth, MeasureSpec.EXACTLY);
        int sh = MeasureSpec.makeMeasureSpec(sideHeight, MeasureSpec.EXACTLY);
        previousCard.measure(sw, sh); nextCard.measure(sw, sh);
        int arrowSpec = MeasureSpec.makeMeasureSpec(dp(38), MeasureSpec.EXACTLY);
        previousArrow.measure(arrowSpec, arrowSpec); nextArrow.measure(arrowSpec, arrowSpec);
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left, height = bottom - top, centerWidth = selectedCard.getMeasuredWidth();
        int centerLeft = (width - centerWidth) / 2;
        int sideY = (height - previousCard.getMeasuredHeight()) / 2;
        int previousRight = centerLeft - dp(5), nextLeft = centerLeft + centerWidth + dp(5);
        previousCard.layout(previousRight - previousCard.getMeasuredWidth(), sideY, previousRight,
                sideY + previousCard.getMeasuredHeight());
        nextCard.layout(nextLeft, sideY, nextLeft + nextCard.getMeasuredWidth(), sideY + nextCard.getMeasuredHeight());
        selectedCard.layout(centerLeft, dp(4), centerLeft + centerWidth, height - dp(4));
        int arrowSize = dp(38), arrowY = (height - arrowSize) / 2;
        previousArrow.layout(0, arrowY, arrowSize, arrowY + arrowSize);
        nextArrow.layout(width - arrowSize, arrowY, width, arrowY + arrowSize);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getX(); downY = event.getY(); horizontalDrag = false;
            getParent().requestDisallowInterceptTouchEvent(true);
        } else if (action == MotionEvent.ACTION_MOVE) {
            float dx = Math.abs(event.getX() - downX), dy = Math.abs(event.getY() - downY);
            if (dx > dp(12) && dx > dy * 1.25f) horizontalDrag = true;
            if (dy > dp(10) && dy > dx * 1.2f) getParent().requestDisallowInterceptTouchEvent(false);
        } else if (action == MotionEvent.ACTION_UP && horizontalDrag) {
            MotionEvent cancel = MotionEvent.obtain(event); cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel); cancel.recycle();
            getParent().requestDisallowInterceptTouchEvent(false);
            float dx = event.getX() - downX; horizontalDrag = false;
            if (Math.abs(dx) >= dp(32)) move(dx < 0 ? 1 : -1);
            return true;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            getParent().requestDisallowInterceptTouchEvent(false); horizontalDrag = false;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) return horizontalDrag;
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent event) { return true; }

    private static final class ArrowView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int direction;
        ArrowView(Context context, int direction) {
            super(context); this.direction = direction; paint.setColor(0xFFFFE8BA);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(context, 2.6f));
            paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setShadowLayer(dp(context, 4), 0, dp(context, 1), 0xFF06131E);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }
        @Override protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, hx = dp(getContext(), 5), hy = dp(getContext(), 8);
            float tip = cx + direction * hx, back = cx - direction * hx;
            canvas.drawLine(back, cy - hy, tip, cy, paint); canvas.drawLine(tip, cy, back, cy + hy, paint);
        }
        private static float dp(Context context, float value) { return value * context.getResources().getDisplayMetrics().density; }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
