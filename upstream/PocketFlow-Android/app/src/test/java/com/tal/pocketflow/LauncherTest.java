package com.tal.pocketflow;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "he-rIL-w411dp-h891dp-xxhdpi")
public class LauncherTest {
    @Test public void horizontalSwipeCancelsWidgetTapAndCanReturn() {
        try (ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            AtomicInteger current = new AtomicInteger();
            AtomicInteger taps = new AtomicInteger();
            LauncherPager pager = new LauncherPager(activity, 0, current::set);
            for (int i = 0; i < 3; i++) {
                View widget = new View(activity);
                widget.setOnClickListener(view -> taps.incrementAndGet());
                pager.addView(widget, new FrameLayout.LayoutParams(-1, -1));
            }
            activity.setContentView(pager);
            pager.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
            pager.layout(0, 0, 1000, 600);

            swipe(pager, 200, 700, 10);
            assertEquals(1, current.get());
            assertEquals(0, taps.get());
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350));
            swipe(pager, 700, 200, 500);
            assertEquals(0, current.get());
            assertEquals(0, taps.get());
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350));
            touch(pager, 1000, 1000, MotionEvent.ACTION_DOWN, 400);
            touch(pager, 1000, 1040, MotionEvent.ACTION_UP, 401);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, taps.get());
        }
    }

    @Test public void allGraphsAppearAndReorderingSurvivesRecreation() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("prefs", 0).edit().clear().commit();
        context.getSharedPreferences("security", 0).edit().clear().commit();
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            StoreTest.navigate(activity, 2);
            View decor = activity.getWindow().getDecorView();
            for (int i = 0; i < 10; i++) assertNotNull(decor.findViewWithTag("graph-section-" + i));

            decor.findViewWithTag("graph-reorder").performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            AlertDialog order = ShadowAlertDialog.getLatestAlertDialog();
            order.getWindow().getDecorView().findViewWithTag("graph-up-1").performClick();
            order.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertFalse(org.robolectric.shadows.ShadowToast.getTextOfLatestToast(),order.isShowing());
            controller.recreate();

            decor = controller.get().getWindow().getDecorView();
            View first = decor.findViewWithTag("graph-section-1");
            View second = decor.findViewWithTag("graph-section-0");
            LinearLayout sections = (LinearLayout) first.getParent();
            assertTrue(sections.indexOfChild(first) < sections.indexOfChild(second));
            assertEquals(Arrays.asList(1, 0, 2, 3, 4, 5, 6, 7, 8, 9),
                    ChartCatalog.order(Tools.read(context).optString("chartOrder")));
        }
    }

    @Test public void launcherHasTwoAppPagesAndTwoDedicatedWidgets() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("prefs", 0).edit().clear().commit();
        context.getSharedPreferences("security", 0).edit().clear().commit();
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            View decor = controller.get().getWindow().getDecorView();
            LauncherPager pager = decor.findViewWithTag("launcher-pager");
            assertEquals(4, pager.getChildCount());
            assertNotNull(pager.getChildAt(0).findViewWithTag("home-section-summary"));
            assertNotNull(pager.getChildAt(1).findViewWithTag("launcher-slot-17"));
            assertNull(pager.getChildAt(2).findViewWithTag("launcher-slot-17"));
            pager.showPage(2);
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
            android.widget.Spinner picker = pager.getChildAt(2).findViewWithTag("launcher-chart-selector");
            assertNotNull(picker);
            assertEquals(10, picker.getCount());
            picker.setSelection(1);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            pager.showPage(3);
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
            assertNotNull(pager.getChildAt(3).findViewWithTag("launcher-second-graph-widget"));
            assertNull(pager.getChildAt(3).findViewWithTag("launcher-forecast-widget"));
            android.widget.Spinner second = pager.getChildAt(3).findViewWithTag("launcher-second-chart-selector");
            assertEquals(10, second.getCount());
            second.setSelection(2);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, picker.getSelectedItemPosition());
            controller.recreate();
            View restored = controller.get().getWindow().getDecorView();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            LauncherPager restoredPager = restored.findViewWithTag("launcher-pager");
            android.widget.Spinner restoredSecond = restoredPager.getChildAt(3).findViewWithTag("launcher-second-chart-selector");
            assertEquals(2, restoredSecond.getSelectedItemPosition());
            restoredPager.showPage(2);
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
            android.widget.Spinner restoredFirst = restoredPager.getChildAt(2).findViewWithTag("launcher-chart-selector");
            assertEquals(1, restoredFirst.getSelectedItemPosition());
            try (Store store = new Store(context)) {
                org.json.JSONObject backup = store.backup();
                assertEquals(1, backup.getJSONObject("settings").getInt("homeChart"));
                assertEquals(2, backup.getJSONObject("settings").getInt("homeChartSecond"));
                context.getSharedPreferences("prefs",0).edit().putInt("homeChartSecond",9).commit();
                store.restore(backup);
                assertEquals(2,context.getSharedPreferences("prefs",0).getInt("homeChartSecond",-1));
                org.json.JSONObject invalid = new org.json.JSONObject(backup.toString());
                invalid.getJSONObject("settings").put("homeChartSecond",99);
                try { store.restore(invalid); fail("Invalid chart accepted"); }
                catch (IllegalArgumentException expected) {}
                assertEquals(2,context.getSharedPreferences("prefs",0).getInt("homeChartSecond",-1));
                backup.getJSONObject("settings").remove("homeChartSecond");
                store.restore(backup);
                assertEquals(0,context.getSharedPreferences("prefs",0).getInt("homeChartSecond",-1));
            }
        }
    }

    @Test public void launcherFooterStaysFixedAndTopControlsStayInsideSections() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("security", 0).edit().clear().commit();
        try (Store store = new Store(context)) {
            store.edit("בדיקת ביטול", () -> {
                org.json.JSONObject settings = Tools.read(context).put("periodStart", 12);
                Tools.write(context, settings, false);
            });
            try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
                MainActivity activity = controller.get();
                View decor = activity.getWindow().getDecorView();
                assertNull(decor.findViewWithTag("menu-toggle"));
                assertNull(decor.findViewWithTag("undo-last"));

                LauncherPager pager = decor.findViewWithTag("launcher-pager");
                View add = decor.findViewWithTag("add-movement");
                android.widget.TextView credit = decor.findViewWithTag("launcher-credit");
                assertNotNull(add);
                assertSame(pager.getParent(), add.getParent());
                assertEquals("Created by Tal Azulay", credit.getText().toString());
                android.view.ViewGroup launcher = (android.view.ViewGroup) credit.getParent();
                View labels = (View) decor.findViewWithTag("launcher-page-0").getParent();
                assertTrue(launcher.indexOfChild(credit) > launcher.indexOfChild(labels));

                for (int index = 0; index < 4; index++) {
                    pager.showPage(index);
                    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
                    assertSame(add, decor.findViewWithTag("add-movement"));
                    assertTrue(add.isShown());
                    assertTrue(add.performClick());
                    AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
                    assertTrue(dialog.isShowing());
                    dialog.dismiss();
                }
                StoreTest.navigate(activity, 1);
                assertNotNull(activity.getWindow().getDecorView().findViewWithTag("undo-last"));
            }
        }
    }

    @Test public void launcherSlotsSwapMoveAndRoundTripWithBackup() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        LauncherLayout layout = new LauncherLayout("");
        layout.move(0, 17);
        layout.move(1, 9);
        assertEquals(LauncherLayout.EMPTY, layout.at(0));
        assertEquals(11, layout.at(17));
        assertEquals(8, layout.at(1));
        assertEquals(1, layout.at(9));
        assertEquals(layout.encode(), new LauncherLayout(layout.encode()).encode());
        context.getSharedPreferences("prefs", 0).edit().putString(LauncherLayout.PREFERENCE, layout.encode()).commit();
        try (Store store = new Store(context)) {
            org.json.JSONObject backup = store.backup();
            context.getSharedPreferences("prefs", 0).edit().remove(LauncherLayout.PREFERENCE).commit();
            store.restore(backup);
            assertEquals(layout.encode(), context.getSharedPreferences("prefs", 0).getString(LauncherLayout.PREFERENCE, ""));
        }
    }

    private void swipe(LauncherPager pager, float start, float end, long time) {
        touch(pager, time, time, MotionEvent.ACTION_DOWN, start);
        touch(pager, time, time + 80, MotionEvent.ACTION_MOVE, (start + end) / 2);
        touch(pager, time, time + 140, MotionEvent.ACTION_MOVE, end);
        touch(pager, time, time + 180, MotionEvent.ACTION_UP, end);
    }

    private void touch(LauncherPager pager, long down, long time, int action, float x) {
        MotionEvent event = MotionEvent.obtain(down, time, action, x, 220, 0);
        pager.dispatchTouchEvent(event);
        event.recycle();
    }
}
