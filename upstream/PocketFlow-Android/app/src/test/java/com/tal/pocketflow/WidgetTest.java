package com.tal.pocketflow;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.*;
import java.io.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,qualifiers="he-rIL-w411dp-h891dp-xxhdpi")
public class WidgetTest {
    Context context;
    @Before public void before(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("pocketflow.db");for(String name:new String[]{"prefs","security","google-drive","widget-pages"})context.getSharedPreferences(name,0).edit().clear().commit();}
    @After public void after(){context.getSharedPreferences("security",0).edit().clear().commit();}
    void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
    void bank(){try(Store db=new Store(context)){Store.Account a=new Store.Account();a.name="בנק";a.type="bank";a.start="2026-01-01";a.closing=31;a.charge=10;db.save(a);}}
    String widgetTitle(View widget){ViewFlipper flip=widget.findViewById(R.id.widget_flip_up);if(flip.getVisibility()!=View.VISIBLE)flip=widget.findViewById(R.id.widget_flip_down);return ((TextView)flip.getCurrentView().findViewById(R.id.widget_page_title)).getText().toString();}
    String dialogTitle(){AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();if(dialog==null)return "";for(String title:new String[]{"תנועה חדשה","הוראת קבע חדשה","חשבון / כרטיס חדש"})if(StoreTest.find(dialog.getWindow().getDecorView(),title)!=null)return title;return "";}
    @Test public void smallWidgetClickOpensMovementAndPendingIntentIsUnique()throws Exception{
        ShadowAppWidgetManager host=Shadows.shadowOf(AppWidgetManager.getInstance(context));int id=host.createWidget(AddMovementWidget.class,R.layout.widget_add);host.getViewFor(id).findViewById(R.id.widget_add_action).performClick();idle();Intent open=Shadows.shadowOf((Application)context).getNextStartedActivity();assertEquals(WidgetActions.MOVEMENT,open.getStringExtra(WidgetActions.EXTRA));assertEquals(MainActivity.class.getName(),open.getComponent().getClassName());
        assertNotEquals(WidgetActions.open(context,WidgetActions.MOVEMENT,10),WidgetActions.open(context,WidgetActions.ACCOUNT,10));assertNotEquals(WidgetActions.open(context,WidgetActions.MOVEMENT,10),WidgetActions.open(context,WidgetActions.MOVEMENT,11));assertTrue(Shadows.shadowOf(WidgetActions.open(context,WidgetActions.MOVEMENT,10)).isImmutable());
    }
    @Test public void defaultPageIsMovementWithAccountAboveAndRuleBelow(){
        assertEquals(1,ActionsWidget.page(context,123));assertEquals(WidgetActions.ACCOUNT,ActionsWidget.action(0));assertEquals(WidgetActions.MOVEMENT,ActionsWidget.action(1));assertEquals(WidgetActions.RULE,ActionsWidget.action(2));
        View widget=ActionsWidget.views(context,123).apply(context,new FrameLayout(context));assertEquals("הוספת תנועה",widgetTitle(widget));assertEquals("2 / 3",((TextView)widget.findViewById(R.id.widget_page_indicator)).getText());assertEquals(View.VISIBLE,widget.findViewById(R.id.widget_up).getVisibility());assertEquals(View.VISIBLE,widget.findViewById(R.id.widget_down).getVisibility());
    }

    @Test public void arrowNavigationShowsWholePagesAndUpdatesTheCentralActionIndependently()throws Exception{
        ShadowAppWidgetManager host=Shadows.shadowOf(AppWidgetManager.getInstance(context));int[] ids=host.createWidgets(ActionsWidget.class,R.layout.widget_actions,2);
        ActionsWidget.arrow(context,ids[0],-1).send();idle();assertEquals(0,ActionsWidget.page(context,ids[0]));assertEquals(1,ActionsWidget.page(context,ids[1]));View widget=host.getViewFor(ids[0]);assertEquals("חשבון/כרטיס",widgetTitle(widget));assertEquals(View.INVISIBLE,widget.findViewById(R.id.widget_up).getVisibility());widget.findViewById(R.id.widget_page_action).performClick();idle();assertEquals(WidgetActions.ACCOUNT,Shadows.shadowOf((Application)context).getNextStartedActivity().getStringExtra(WidgetActions.EXTRA));
        ActionsWidget.arrow(context,ids[0],1).send();idle();assertEquals(1,ActionsWidget.page(context,ids[0]));ActionsWidget.arrow(context,ids[0],1).send();idle();assertEquals(2,ActionsWidget.page(context,ids[0]));widget=host.getViewFor(ids[0]);assertEquals("הוראת קבע",widgetTitle(widget));assertEquals(View.INVISIBLE,widget.findViewById(R.id.widget_down).getVisibility());widget.findViewById(R.id.widget_page_action).performClick();idle();assertEquals(WidgetActions.RULE,Shadows.shadowOf((Application)context).getNextStartedActivity().getStringExtra(WidgetActions.EXTRA));ActionsWidget.arrow(context,ids[0],1).send();idle();assertEquals(2,ActionsWidget.page(context,ids[0]));
    }

    @Test public void coldStartOpensCorrectFormAndDoesNotSaveAnything(){
        bank();String[] actions={WidgetActions.MOVEMENT,WidgetActions.RULE,WidgetActions.ACCOUNT},titles={"תנועה חדשה","הוראת קבע חדשה","חשבון / כרטיס חדש"};
        for(int i=0;i<actions.length;i++)try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class,WidgetActions.intent(context,actions[i],1)).setup()){
            idle();assertEquals(titles[i],dialogTitle());assertTrue(ShadowAlertDialog.getLatestAlertDialog().isShowing());assertFalse(c.get().getIntent().hasExtra(WidgetActions.EXTRA));try(Store db=new Store(context)){assertEquals(0,db.transactions().size());assertEquals(0,db.rules().size());assertEquals(3,db.accounts().size());}ShadowAlertDialog.getLatestAlertDialog().dismiss();
        }
    }
    @Test public void reusedActivityHandlesNewWidgetActionAndBackReturnsHome(){
        bank();try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){
            c.newIntent(WidgetActions.intent(context,WidgetActions.MOVEMENT,1));idle();assertEquals("תנועה חדשה",dialogTitle());AlertDialog first=ShadowAlertDialog.getLatestAlertDialog();c.newIntent(WidgetActions.intent(context,WidgetActions.ACCOUNT,1));idle();assertFalse(first.isShowing());assertEquals("חשבון / כרטיס חדש",dialogTitle());
            c.get().onBackPressed();idle();assertFalse(ShadowAlertDialog.getLatestAlertDialog().isShowing());c.get().onBackPressed();idle();assertNotNull(c.get().getWindow().getDecorView().findViewWithTag("home-section-summary"));assertFalse(c.get().isFinishing());
        }
    }
    @Test @Config(sdk=26) public void widgetActionWaitsForAuthenticationAndSurvivesRotation(){
        context.getSharedPreferences("security",0).edit().putBoolean("entryLock",true).commit();Shadows.shadowOf(context.getSystemService(KeyguardManager.class)).setIsDeviceSecure(true);
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class,WidgetActions.intent(context,WidgetActions.MOVEMENT,1)).setup()){
            idle();assertNotNull(c.get().getWindow().getDecorView().findViewWithTag("app-locked"));c.recreate();idle();assertNotNull(c.get().getWindow().getDecorView().findViewWithTag("app-locked"));c.get().onActivityResult(AppLock.CREDENTIAL_REQUEST,Activity.RESULT_CANCELED,null);idle();assertNotNull(c.get().getWindow().getDecorView().findViewWithTag("app-locked"));c.get().getWindow().getDecorView().findViewWithTag("unlock-app").performClick();idle();c.get().onActivityResult(AppLock.CREDENTIAL_REQUEST,Activity.RESULT_OK,null);idle();assertEquals("תנועה חדשה",dialogTitle());assertTrue(ShadowAlertDialog.getLatestAlertDialog().isShowing());assertTrue((ShadowAlertDialog.getLatestAlertDialog().getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0);
        }
    }
    @Test public void invalidActionDoesNotOpenDialogOrCreateData(){try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class,WidgetActions.intent(context,"unexpected",1)).setup()){idle();assertNotNull(c.get().getWindow().getDecorView().findViewWithTag("home-section-summary"));assertNull(ShadowAlertDialog.getLatestAlertDialog());}}
    @Test public void widgetSizesAndServiceProtectionAreDeclared()throws Exception{
        String ns="http://schemas.android.com/apk/res/android";int[] resources={R.xml.widget_add_info,R.xml.widget_actions_info};for(int i=0;i<2;i++){android.content.res.XmlResourceParser xml=context.getResources().getXml(resources[i]);while(xml.next()!=org.xmlpull.v1.XmlPullParser.START_TAG){}assertEquals(i+1,xml.getAttributeIntValue(ns,"targetCellWidth",0));assertEquals(i+1,xml.getAttributeIntValue(ns,"targetCellHeight",0));xml.close();}
        assertFalse(context.getPackageManager().getReceiverInfo(new ComponentName(context,ActionsWidget.class),0).exported);
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void renderWidgetSizes()throws Exception{
        View[] widgets={LayoutInflater.from(context).inflate(R.layout.widget_add,null),ActionsWidget.views(context,123).apply(context,new FrameLayout(context))};int[] sizes={64,144};for(int i=0;i<2;i++){View view=widgets[i];int size=sizes[i]*3;view.measure(View.MeasureSpec.makeMeasureSpec(size,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(size,View.MeasureSpec.EXACTLY));view.layout(0,0,size,size);android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(size,size,android.graphics.Bitmap.Config.ARGB_8888);view.draw(new android.graphics.Canvas(bitmap));File folder=new File("build/design-previews");folder.mkdirs();try(OutputStream out=new FileOutputStream(new File(folder,"widget-"+(i+1)+".png"))){assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();}
    }
    @Test public void unknownWidgetCannotNavigateAndDeletionRestoresMiddleDefault(){
        ActionsWidget provider=new ActionsWidget();provider.onReceive(context,new Intent(context,ActionsWidget.class).setAction(ActionsWidget.PAGE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,999).putExtra("direction",-1));assertEquals(1,ActionsWidget.page(context,999));
        context.getSharedPreferences("widget-pages",0).edit().putInt("page-123",0).putInt("page-456",2).commit();provider.onDeleted(context,new int[]{123});assertEquals(1,ActionsWidget.page(context,123));assertEquals(2,ActionsWidget.page(context,456));
    }


    @Test public void pageTransitionUsesNativeDirectionalAnimationsAndFinishesOnSelectedPage(){
        context.getSharedPreferences("widget-pages",0).edit().putInt("page-123",1).commit();View root=ActionsWidget.views(context,123).apply(context,new FrameLayout(context));
        context.getSharedPreferences("widget-pages",0).edit().putInt("page-123",0).commit();ActionsWidget.views(context,123,-1).reapply(context,root);ViewFlipper up=root.findViewById(R.id.widget_flip_up),down=root.findViewById(R.id.widget_flip_down);assertEquals(View.VISIBLE,up.getVisibility());assertEquals(View.GONE,down.getVisibility());assertEquals(0,up.getDisplayedChild());assertNotNull(up.getInAnimation());assertNotNull(up.getOutAnimation());assertEquals(220,up.getInAnimation().getDuration());assertFalse(up.isFlipping());assertEquals("חשבון/כרטיס",widgetTitle(root));
        context.getSharedPreferences("widget-pages",0).edit().putInt("page-123",1).commit();ActionsWidget.views(context,123,1).reapply(context,root);assertEquals(View.GONE,up.getVisibility());assertEquals(View.VISIBLE,down.getVisibility());assertEquals(1,down.getDisplayedChild());assertNotSame(up.getInAnimation(),down.getInAnimation());assertEquals(220,down.getOutAnimation().getDuration());assertFalse(down.isFlipping());assertEquals("הוספת תנועה",widgetTitle(root));
    }

}
