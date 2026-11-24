package com.tal.pocketflow;

import android.content.ClipData;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.DragEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import java.util.function.BiFunction;
import java.util.function.Consumer;

/** Handles native drag shadows, edge paging and slot drops on the app pages. */
final class LauncherGrid {
    private final View surface;
    private final LauncherPager pager;
    private final LauncherLayout layout;
    private final BiFunction<Integer, Integer, View> icon;
    private final Consumer<String> save;
    private final Drawable targetStyle, highlightStyle;
    private final int edgeSize;
    private final FrameLayout[] slots = new FrameLayout[LauncherLayout.APP_PAGES * LauncherLayout.SLOTS_PER_PAGE];
    private final ScrollView[] scrolls = new ScrollView[LauncherLayout.APP_PAGES];
    private final int[] origin = new int[2];
    private final int[] position = new int[2];
    private final Rect bounds = new Rect();
    private int source = -1, target = -1, edgePage = -1, scrollDirection;
    private float dragX, dragY;
    private final Runnable turnPage = this::turnPage;
    private final Runnable scrollPage = this::scrollPage;

    LauncherGrid(View surface, LauncherPager pager, String saved, BiFunction<Integer, Integer, View> icon,
                 Consumer<String> save, Drawable targetStyle, Drawable highlightStyle, int edgeSize) {
        this.surface = surface;
        this.pager = pager;
        this.layout = new LauncherLayout(saved);
        this.icon = icon;
        this.save = save;
        this.targetStyle = targetStyle;
        this.highlightStyle = highlightStyle;
        this.edgeSize = edgeSize;
        surface.setOnDragListener(this::handleDrag);
        surface.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}
            @Override public void onViewDetachedFromWindow(View v) { endDrag(); }
        });
    }

    void registerPage(int page, ScrollView scroll) { scrolls[page] = scroll; }

    void registerSlot(int index, FrameLayout slot) {
        slots[index] = slot;
        draw(index);
    }

    private void draw(int index) {
        FrameLayout slot = slots[index];
        slot.removeAllViews();
        int app = layout.at(index);
        if (app == LauncherLayout.EMPTY) return;
        View item = icon.apply(app, index % LauncherLayout.COLUMNS);
        item.setOnLongClickListener(view -> beginDrag(view, index));
        slot.addView(item, new FrameLayout.LayoutParams(-1, -2));
    }

    private boolean beginDrag(View view, int index) {
        if (source >= 0 || pager.isSettling()) return false;
        source = index;
        boolean started = view.startDragAndDrop(ClipData.newPlainText("PocketFlow", ""),
                new View.DragShadowBuilder(view), this, 0);
        if (started) view.setAlpha(.35f);
        else source = -1;
        return started;
    }

    private boolean handleDrag(View view, DragEvent event) {
        if (source < 0) return false;
        if (event.getAction() == DragEvent.ACTION_DRAG_ENDED) {
            endDrag();
            return true;
        }
        if (event.getLocalState() != this) return false;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                showTargets();
                return true;
            case DragEvent.ACTION_DRAG_LOCATION:
                dragX = event.getX();
                dragY = event.getY();
                updateHover();
                return true;
            case DragEvent.ACTION_DROP:
                int destination = findSlot(event.getX(), event.getY());
                clearHover();
                if (destination < 0) return false;
                if (destination != source) {
                    layout.move(source, destination);
                    save.accept(layout.encode());
                    draw(source);
                    draw(destination);
                }
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                clearHover();
                return true;
            default:
                return true;
        }
    }

    private void updateHover() {
        int page = pager.currentPage();
        int edge = Math.min(edgeSize, surface.getWidth() / 5);
        int nextPage = -1;
        // In RTL page 2 sits to the left of page 1.
        if (page == 0 && dragX < edge) nextPage = 1;
        if (page == 1 && dragX > surface.getWidth() - edge) nextPage = 0;
        if (nextPage != edgePage) {
            surface.removeCallbacks(turnPage);
            edgePage = nextPage;
            if (nextPage >= 0) surface.postDelayed(turnPage, 460);
        }
        updateTarget(findSlot(dragX, dragY));

        int direction = 0;
        if (page < scrolls.length && nextPage < 0 && !pager.isSettling()) {
            ScrollView scroll = scrolls[page];
            surface.getLocationOnScreen(origin);
            scroll.getLocationOnScreen(position);
            float localY = dragY + origin[1] - position[1];
            if (localY >= 0 && localY < edge) direction = -1;
            if (localY <= scroll.getHeight() && localY > scroll.getHeight() - edge) direction = 1;
        }
        if (direction != scrollDirection) {
            surface.removeCallbacks(scrollPage);
            scrollDirection = direction;
            if (direction != 0) surface.postDelayed(scrollPage, 60);
        }
    }

    private void turnPage() {
        if (edgePage < 0) return;
        updateTarget(-1);
        pager.showPage(edgePage);
        edgePage = -1;
        showTargets();
    }

    private void scrollPage() {
        int page = pager.currentPage();
        if (scrollDirection == 0 || page >= scrolls.length || pager.isSettling()) return;
        ScrollView scroll = scrolls[page];
        if (!scroll.canScrollVertically(scrollDirection)) return;
        scroll.scrollBy(0, scrollDirection * Math.max(1, edgeSize / 5));
        updateTarget(findSlot(dragX, dragY));
        surface.postDelayed(scrollPage, 35);
    }

    private int findSlot(float x, float y) {
        int page = pager.currentPage();
        if (page >= LauncherLayout.APP_PAGES || pager.isSettling()) return -1;
        surface.getLocationOnScreen(origin);
        int from = page * LauncherLayout.SLOTS_PER_PAGE;
        for (int index = from; index < from + LauncherLayout.SLOTS_PER_PAGE; index++) {
            FrameLayout slot = slots[index];
            if (!slot.getLocalVisibleRect(bounds)) continue;
            slot.getLocationOnScreen(position);
            int localX = (int) (origin[0] + x - position[0]);
            int localY = (int) (origin[1] + y - position[1]);
            if (bounds.contains(localX, localY)) return index;
        }
        return -1;
    }

    private void updateTarget(int next) {
        if (target == next) return;
        if (target >= 0) slots[target].setBackground(copy(targetStyle));
        target = next;
        if (target >= 0) slots[target].setBackground(copy(highlightStyle));
    }

    private void showTargets() {
        for (FrameLayout slot : slots) slot.setBackground(copy(targetStyle));
    }

    private Drawable copy(Drawable drawable) { return drawable.getConstantState().newDrawable().mutate(); }

    private void clearHover() {
        surface.removeCallbacks(turnPage);
        surface.removeCallbacks(scrollPage);
        edgePage = -1;
        scrollDirection = 0;
        updateTarget(-1);
    }

    private void endDrag() {
        if (source < 0) return;
        clearHover();
        View item = slots[source].getChildAt(0);
        if (item != null) item.setAlpha(1);
        for (FrameLayout slot : slots) slot.setBackground(null);
        source = -1;
    }
}
