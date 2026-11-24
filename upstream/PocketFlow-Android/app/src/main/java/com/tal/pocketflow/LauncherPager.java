package com.tal.pocketflow;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import java.util.function.IntConsumer;

/** Moves the already-built pages and cancels a child's press when a horizontal drag wins. */
final class LauncherPager extends FrameLayout {
    private final int slop;
    private final int flingSpeed;
    private final IntConsumer onPage;
    private int selected;
    private float downX, downY, offset;
    private boolean dragging, vertical, blocked;
    private VelocityTracker velocity;
    private ValueAnimator settle;

    LauncherPager(Context context, int selected, IntConsumer onPage) {
        super(context);
        ViewConfiguration config = ViewConfiguration.get(context);
        slop = config.getScaledTouchSlop();
        flingSpeed = Math.max(config.getScaledMinimumFlingVelocity(), (int) (450 * getResources().getDisplayMetrics().density));
        this.selected = selected;
        this.onPage = onPage;
        setClipChildren(true);
    }

    int currentPage() { return selected; }

    boolean isSettling() { return settle != null && settle.isRunning(); }

    void showPage(int target) {
        target = Math.max(0, Math.min(target, getChildCount() - 1));
        if (settle != null) settle.cancel();
        int previous = selected;
        offset += (selected - target) * getWidth();
        selected = target;
        if (previous != selected) onPage.accept(selected);
        if (offset == 0 || getWidth() == 0 || !ValueAnimator.areAnimatorsEnabled()) {
            offset = 0;
            positionPages();
            return;
        }
        settle = ValueAnimator.ofFloat(offset, 0);
        settle.setDuration(230);
        settle.setInterpolator(new DecelerateInterpolator());
        settle.addUpdateListener(animation -> {
            offset = (float) animation.getAnimatedValue();
            positionPages();
        });
        settle.start();
    }

    private void positionPages() {
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            float x = (selected - index) * getWidth() + offset;
            child.setTranslationX(x);
            child.setVisibility(index == selected || Math.abs(x) < getWidth() ? VISIBLE : INVISIBLE);
            child.setImportantForAccessibility(index == selected
                    ? IMPORTANT_FOR_ACCESSIBILITY_AUTO : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        positionPages();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            blocked = isSettling();
            downX = event.getX();
            downY = event.getY();
            dragging = vertical = false;
            releaseVelocity();
            if (!blocked) velocity = VelocityTracker.obtain();
        }
        if (blocked) return true;
        if (velocity != null) velocity.addMovement(event);

        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            if (dragging) {
                showPage(selected);
                releaseVelocity();
                blocked = true;
                return true;
            }
            vertical = true;
        }
        if (action == MotionEvent.ACTION_MOVE && !vertical) {
            float dx = event.getX() - downX;
            float dy = event.getY() - downY;
            if (!dragging && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) {
                dragging = true;
                // A child may already be pressed or handling a vertical scroll.
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
            }
            if (dragging) {
                float distance = dx - Math.copySign(slop, dx);
                boolean beyondEdge = (selected == 0 && dx < 0)
                        || (selected == getChildCount() - 1 && dx > 0);
                offset = beyondEdge || Math.abs(dx) <= slop ? 0
                        : Math.max(-getWidth(), Math.min(getWidth(), distance));
                positionPages();
                return true;
            }
            vertical = Math.abs(dy) > slop;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (dragging) {
                int step = action == MotionEvent.ACTION_UP ? swipeStep(event.getX() - downX) : 0;
                showPage(selected + step);
                releaseVelocity();
                return true;
            }
            releaseVelocity();
        }
        boolean handled = super.dispatchTouchEvent(event);
        return handled || action == MotionEvent.ACTION_DOWN;
    }

    private int swipeStep(float distance) {
        velocity.computeCurrentVelocity(1000);
        float speed = velocity.getXVelocity();
        boolean fling = Math.abs(distance) > slop && Math.abs(speed) > flingSpeed
                && Math.signum(speed) == Math.signum(distance);
        return Math.abs(distance) > getWidth() * .24f || fling ? (distance > 0 ? 1 : -1) : 0;
    }

    private void releaseVelocity() {
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
    }

    @Override protected void onDetachedFromWindow() {
        if (settle != null) settle.cancel();
        offset = 0;
        releaseVelocity();
        super.onDetachedFromWindow();
    }
}
