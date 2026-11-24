package com.tal.pocketflow;

import android.app.*;
import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.os.Bundle;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
    private AppTheme.Palette palette;private int BG,INK,MUTED,PRIMARY,RED,SURFACE,OUTLINE,CONTAINER;private Dialog drawer;
    private final String[] tabs={"בית","תנועות","גרפים","הוראות קבע","חשבונות וכרטיסים","הגדרות","גיבוי ושחזור","תובנות","מבט קדימה","תקציבים","קיצורי הזנה","תמונת מצב"};
    private static final int HOME = 0, GRAPHS = 2, SNAPSHOT = 11;
    private static final int GRAPH_WIDGET_PAGE = 2;
    private static final String[] HOME_PAGES = {"ראשי", "אפליקציות", "גרף 1", "גרף 2"};
    private static final int[] APP_ICONS = {
            R.drawable.nav_snapshot, R.drawable.nav_records, R.drawable.nav_graphs,
            R.drawable.nav_clock, R.drawable.nav_accounts, R.drawable.nav_settings, R.drawable.nav_backup,
            R.drawable.nav_insights, R.drawable.nav_clock, R.drawable.nav_accounts, R.drawable.nav_shortcuts,
            R.drawable.nav_snapshot
    };
    private int homePage;
    private final int[] homeScroll = new int[HOME_PAGES.length];
    private Rect launchAnchor;
    private boolean openingFromHome;
    private FrameLayout shell;
    private final String[] monthNames={"ינואר","פברואר","מרץ","אפריל","מאי","יוני","יולי","אוגוסט","ספטמבר","אוקטובר","נובמבר","דצמבר"};
    private final String[] categories={"מצרכים","אוכל ושתייה","בית","חשבונות","תחבורה ודלק","קניות ברשת","ביגוד והנעלה","בריאות","חינוך ולימודים","פנאי","מתנות","משפחה","אחר"};
    private final String[] typeNames={"חשבון בנק","דיירקט","אשראי רגיל","אשראי בחיוב קבוע","המחאות"};
    private final String[] typeIds={"bank","debit","credit","fixed","check"};
    private Store store;private YearMonth month=YearMonth.now();private int tab=0;private boolean chargesMode=false;
    private MovementFilter advancedFilter=new MovementFilter();private int movementLimit=60;private final android.os.Handler searchHandler=new android.os.Handler(android.os.Looper.getMainLooper());private Runnable pendingSearch;
    private String categoryFilter="",query="",merchantFilter="";private long accountFilter=0;
    private LinearLayout page,body,listHost;private SharedPreferences prefs;
    private AppLock appLock;private boolean unlocked,started,autoUnlockPending,unlockRetry;private final Set<Dialog> openDialogs=new LinkedHashSet<>();
    private final ArrayList<Bundle> navigationHistory=new ArrayList<>();private Bundle lastScreen;private boolean returningBack;private long exitBackAt=-1;private android.window.OnBackInvokedCallback backCallback;
    private boolean pendingRestore;private AlertDialog restoreDialog;private BackupTransfer backupTransfer;private DriveTask driveTask;private String pendingDriveAction="",pendingDriveFile="";private long pendingDriveGeneration;private boolean cloudListPending;private List<DriveApi.Remote> cloudFiles;
    private String widgetAction="";
    private String pendingExport="";private final DateTimeFormatter displayDate=DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override public void onCreate(Bundle state){
        setTheme(AppTheme.dark(this)?R.style.PocketDark:R.style.AppTheme);super.onCreate(state);getSharedPreferences("browser-sync",MODE_PRIVATE).edit().clear().apply();store=new Store(this);prefs=getSharedPreferences("prefs",MODE_PRIVATE);appLock=new AppLock(this);month=store.reporting().label(LocalDate.now());updateSecureWindow();
        pendingRestore=state!=null&&state.getBoolean("pendingRestore")&&restoreDraft().isFile();
        widgetAction=state==null?"":state.getString("widgetAction","");acceptWidgetIntent(getIntent());
        if(state!=null){pendingDriveAction=state.getString("driveAction","");pendingDriveFile=state.getString("driveFile","");pendingDriveGeneration=state.getLong("driveGeneration");cloudListPending=state.getBoolean("cloudListPending");}
        new DriveState(this).schedule();ReminderJob.schedule(this);
        if(state!=null){int[] scroll=state.getIntArray("homeScroll");if(scroll!=null&&scroll.length==homeScroll.length)System.arraycopy(scroll,0,homeScroll,0,scroll.length);homePage=Math.max(0,Math.min(HOME_PAGES.length-1,state.getInt("homePage")));tab=state.getInt("tab");month=YearMonth.parse(state.getString("month",month.toString()));chargesMode=state.getBoolean("chargesMode");}
        if(state!=null){Bundle screen=state.getBundle("navigation");if(screen!=null)restoreScreen(screen);ArrayList<Bundle> history=state.getParcelableArrayList("navigationHistory");if(history!=null)navigationHistory.addAll(history);}
        if(android.os.Build.VERSION.SDK_INT>=33){backCallback=this::handleBack;getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,backCallback);}
        Object retained=getLastNonConfigurationInstance();if(retained instanceof Object[]){Object[] tasks=(Object[])retained;backupTransfer=(BackupTransfer)tasks[0];driveTask=(DriveTask)tasks[1];}
        render();if(backupTransfer!=null)backupTransfer.attach(this::completeTransfer);if(driveTask!=null)driveTask.attach(this::completeDrive);
    }
    @Override protected void onStart(){super.onStart();started=true;if(appLock.enabled()&&!unlocked){showLockedScreen();autoUnlockPending=!appLock.busy()&&!unlockRetry;}else openWidgetAction();}
    @Override protected void onResume(){super.onResume();if(autoUnlockPending){autoUnlockPending=false;if(appLock.enabled()&&!unlocked)unlock();}}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);acceptWidgetIntent(intent);openWidgetAction();if(started&&appLock.enabled()&&!unlocked&&!appLock.busy())unlock();}
    private void acceptWidgetIntent(Intent intent){if(intent==null)return;String action=intent.getStringExtra(WidgetActions.EXTRA);intent.removeExtra(WidgetActions.EXTRA);if(WidgetActions.valid(action))widgetAction=action;}
    private void openWidgetAction(){
        if(widgetAction.isEmpty()||!started||(appLock.enabled()&&!unlocked)||page==null)return;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
            if(widgetAction.isEmpty()||!started||(appLock.enabled()&&!unlocked))return;
            String action=widgetAction;widgetAction=""; // Consume once, including across rotation and repeated renders.
            for(Dialog dialog:new ArrayList<>(openDialogs))if(dialog.isShowing())dialog.dismiss();if(drawer!=null)drawer.dismiss();
            clearRestore();cloudListPending=false;cloudFiles=null;categoryFilter="";merchantFilter="";query="";advancedFilter=new MovementFilter();accountFilter=0;chargesMode=false;month=store.reporting().label(LocalDate.now());
            tab=0;launchAnchor=null;navigationHistory.clear();lastScreen=captureScreen();tab=WidgetActions.RULE.equals(action)?3:WidgetActions.ACCOUNT.equals(action)?4:1;render();
            if(WidgetActions.RULE.equals(action))ruleDialog();else if(WidgetActions.ACCOUNT.equals(action))accountDialog();else transactionDialog(null);
        });
    }
    @Override protected void onStop(){
        started=false;autoUnlockPending=false;unlockRetry=false;exitBackAt=-1;appLock.background();
        if(appLock.enabled()){unlocked=false;for(Dialog dialog:new ArrayList<>(openDialogs))if(dialog.isShowing())dialog.dismiss();if(drawer!=null)drawer.dismiss();showLockedScreen();}
        super.onStop();
    }
    @Override public void onBackPressed(){handleBack();}
    private Bundle captureScreen(){Bundle state=new Bundle();state.putInt("tab",tab);state.putString("month",month.toString());state.putBoolean("chargesMode",chargesMode);state.putString("categoryFilter",categoryFilter);state.putString("query",query);state.putString("merchantFilter",merchantFilter);state.putLong("accountFilter",accountFilter);state.putString("advancedFilter",advancedFilter.json().toString());return state;}
    private void restoreScreen(Bundle state){tab=state.getInt("tab");month=YearMonth.parse(state.getString("month",month.toString()));chargesMode=state.getBoolean("chargesMode");categoryFilter=state.getString("categoryFilter","");query=state.getString("query","");merchantFilter=state.getString("merchantFilter","");accountFilter=state.getLong("accountFilter");try{advancedFilter=MovementFilter.from(new JSONObject(state.getString("advancedFilter","{}")));}catch(Exception e){advancedFilter=new MovementFilter();}}
    private void recordNavigation(){
        exitBackAt=-1;
        if(tab==0)navigationHistory.clear();
        else if(!returningBack&&lastScreen!=null&&(lastScreen.getInt("tab")!=tab||lastScreen.getBoolean("chargesMode")!=chargesMode)){
            if(navigationHistory.size()>=100)navigationHistory.remove(navigationHistory.get(0).getInt("tab")==0?1:0);
            navigationHistory.add(new Bundle(lastScreen));
        }
        returningBack=false;lastScreen=captureScreen();
    }
    private void handleBack(){
        long previousExitBack=exitBackAt;exitBackAt=-1;
        if(appLock.enabled()&&!unlocked){finish();return;}
        if(drawer!=null&&drawer.isShowing()){drawer.dismiss();return;}
        List<Dialog> dialogs=new ArrayList<>(openDialogs);for(int i=dialogs.size()-1;i>=0;i--)if(dialogs.get(i).isShowing()){dialogs.get(i).cancel();return;}
        if (tab == HOME && homePage != HOME) {
            LauncherPager pager = page.findViewWithTag("launcher-pager");
            if (pager != null) pager.showPage(HOME);
            return;
        }
        if(tab==0){long now=android.os.SystemClock.elapsedRealtime();if(previousExitBack>=0&&now-previousExitBack<=2500)finish();else{exitBackAt=now;toast("לחץ חזור שוב ליציאה");}return;}
        if(!navigationHistory.isEmpty())restoreScreen(navigationHistory.remove(navigationHistory.size()-1));else{tab=0;chargesMode=false;categoryFilter="";accountFilter=0;query="";advancedFilter=new MovementFilter();}
        returningBack=true;render();
    }
    private void updateSecureWindow(){if(appLock.enabled())getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);}
    private void unlock(){
        if(!started||appLock.busy())return;
        if(!appLock.secure()){toast("יש להגדיר קוד, תבנית או סיסמה לנעילת המכשיר בהגדרות Android");return;}
        unlockRetry=false;
        appLock.authenticate(()->{unlockRetry=false;unlocked=true;render();},()->{unlockRetry=true;if(started)showLockedScreen();});
    }
    private void showLockedScreen(){showLockedScreen(unlockRetry);}
    private void showLockedScreen(boolean retry){
        palette=AppTheme.palette(this);BG=palette.background;INK=palette.ink;MUTED=palette.muted;PRIMARY=palette.primary;RED=palette.error;SURFACE=palette.surface;OUTLINE=palette.outline;CONTAINER=palette.container;
        updateSecureWindow();LinearLayout locked=column();locked.setTag("app-locked");locked.setGravity(Gravity.CENTER);locked.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);locked.setBackgroundColor(BG);locked.setPadding(dp(30),dp(40),dp(30),dp(40));
        // Keep financial content hidden behind Android's prompt; offer a button only after cancellation.
        if(retry){
            TextView brand=bold("PocketFlow",30,INK);brand.setGravity(Gravity.CENTER);locked.addView(brand);space(locked,16);TextView message=text("האפליקציה נעולה",20,INK);message.setGravity(Gravity.CENTER);locked.addView(message);space(locked,22);
            TextView open=button("ניסיון אימות נוסף",true,this::unlock);open.setTag("unlock-app");locked.addView(open,new LinearLayout.LayoutParams(-1,-2));space(locked,12);
        }
        if(!appLock.secure()){TextView settings=button("הגדרות נעילת המכשיר",false,()->startActivity(new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)));locked.addView(settings);}
        shell=null;page=null;launchAnchor=null;openingFromHome=false;setContentView(locked);
    }
    private void changeEntryLock(){
        if(!appLock.secure()){toast("לפני הפעלת ההגנה יש להגדיר נעילת מסך במכשיר");return;}
        boolean enable=!appLock.enabled();
        appLock.authenticate(()->{appLock.enabled(enable);unlocked=true;updateSecureWindow();render();toast(enable?"הגנת הכניסה הופעלה":"הגנת הכניסה בוטלה");},()->toast("האימות בוטל. הגדרת ההגנה לא השתנתה"));
    }
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putInt("homePage",homePage);b.putIntArray("homeScroll",homeScroll.clone());b.putString("widgetAction",widgetAction);b.putString("driveAction",pendingDriveAction);b.putString("driveFile",pendingDriveFile);b.putLong("driveGeneration",pendingDriveGeneration);b.putBoolean("cloudListPending",cloudListPending);b.putBoolean("pendingRestore",pendingRestore);b.putInt("tab",tab);b.putString("month",month.toString());b.putBoolean("chargesMode",chargesMode);b.putBundle("navigation",captureScreen());b.putParcelableArrayList("navigationHistory",new ArrayList<>(navigationHistory));}
    @Override public Object onRetainNonConfigurationInstance(){return new Object[]{backupTransfer,driveTask};}
    @Override protected void onDestroy(){if(driveTask!=null)driveTask.detach();if(backupTransfer!=null)backupTransfer.detach();if(android.os.Build.VERSION.SDK_INT>=33&&backCallback!=null)getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);store.close();super.onDestroy();}
    private int dp(float x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private String money(long cents){return "\u200E₪ "+String.format(Locale.US,"%,.2f",cents/100d)+"\u200E";}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private GradientDrawable outlineShape(int color,int radius,int stroke){GradientDrawable d=shape(color,radius);d.setStroke(dp(1),stroke);return d;}
    private void styleInput(EditText e){
        e.setTextColor(INK);e.setHintTextColor(MUTED);e.setBackgroundTintList(null);e.setBackground(outlineShape(SURFACE,12,OUTLINE));e.setPadding(dp(14),dp(8),dp(14),dp(8));
        e.setOnFocusChangeListener((v,focused)->e.setBackground(outlineShape(SURFACE,12,focused?PRIMARY:OUTLINE)));
    }
    private <T extends Dialog> T trackDialog(T dialog){exitBackAt=-1;openDialogs.add(dialog);dialog.setOnDismissListener(d->openDialogs.remove(dialog));if(appLock.enabled()&&dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);return dialog;}
    private void styleDialog(AlertDialog dialog){
        exitBackAt=-1;
        openDialogs.add(dialog);dialog.setOnDismissListener(d->openDialogs.remove(dialog));if(appLock.enabled()&&dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawable(shape(SURFACE,24));dialog.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);}
        for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL})if(dialog.getButton(which)!=null)dialog.getButton(which).setTextColor(PRIMARY);
    }
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);t.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);t.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);t.setGravity(s.startsWith("\u200E")?Gravity.LEFT:Gravity.RIGHT);t.setPadding(0,dp(3),0,dp(3));return t;}
    private TextView bold(String s,int size,int color){TextView t=text(s,size,color);t.setTypeface(null,Typeface.BOLD);return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView button(String s,boolean fill,Runnable action){
        TextView b=bold(s,14,fill?palette.onPrimary:PRIMARY);b.setGravity(Gravity.CENTER);b.setPadding(dp(13),dp(11),dp(13),dp(11));b.setMinHeight(dp(48));b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf((PRIMARY&0x00ffffff)|0x22000000),outlineShape(fill?PRIMARY:SURFACE,24,fill?PRIMARY:OUTLINE),shape(Color.WHITE,24)));b.setOnClickListener(v->{action.run();});b.setFocusable(true);return b;
    }
    private void space(LinearLayout host,int h){View v=new View(this);host.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    private LinearLayout card(LinearLayout host){LinearLayout c=column();c.setPadding(dp(18),dp(15),dp(18),dp(15));c.setBackground(outlineShape(SURFACE,20,OUTLINE));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(13);host.addView(c,lp);return c;}
    private void hint(LinearLayout host,String s){host.addView(text(s,13,MUTED));}
    private void title(LinearLayout host,String s){host.addView(bold(s,19,INK));space(host,7);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private boolean incomes(){return prefs.getBoolean("income",false);}

    private void render(){
        if(pendingSearch!=null)searchHandler.removeCallbacks(pendingSearch);movementLimit=60;
        if(appLock.enabled()&&!unlocked){showLockedScreen();return;}
        int previousTab = lastScreen == null ? HOME : lastScreen.getInt("tab");
        LinearLayout previousPage = page;
        boolean animate = previousPage != null && previousPage.isLaidOut() && ValueAnimator.areAnimatorsEnabled();
        boolean opening = animate && openingFromHome && previousTab == HOME && tab != HOME;
        boolean closing = animate && previousTab != HOME && tab == HOME && launchAnchor != null;
        openingFromHome = false;
        if (previousPage != null) previousPage.animate().cancel();
        recordNavigation();
        palette=AppTheme.palette(this);BG=palette.background;INK=palette.ink;MUTED=palette.muted;PRIMARY=palette.primary;RED=palette.error;SURFACE=palette.surface;OUTLINE=palette.outline;CONTAINER=palette.container;
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);int flags=getWindow().getDecorView().getSystemUiVisibility();
        flags=palette.dark?flags&~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR:flags|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if(android.os.Build.VERSION.SDK_INT>=27)flags=palette.dark?flags&~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:flags|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;getWindow().getDecorView().setSystemUiVisibility(flags);
        if(drawer!=null&&drawer.isShowing())drawer.dismiss();
        store.ensureReportPeriod(month);
        page=column();page.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);page.setBackgroundColor(BG);
        attachPage(previousPage, opening, closing);
        // Respect system bars, including edge-to-edge enforcement on Android 15.
        page.setOnApplyWindowInsetsListener((v,in)->{
            v.setPadding(in.getSystemWindowInsetLeft(),in.getSystemWindowInsetTop(),in.getSystemWindowInsetRight(),in.getSystemWindowInsetBottom());
            return in.consumeSystemWindowInsets();
        });
        if (tab != HOME) renderSectionHeader();
        FrameLayout area=new FrameLayout(this);page.addView(area,new LinearLayout.LayoutParams(-1,0,1));
        if (tab == HOME) {
            launcher(area);
        } else {
            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            body = column();
            body.setPadding(dp(16), dp(8), dp(16), dp(tab == 1 || tab == 3 || tab == 4 ? 94 : 24));
            scroll.addView(body);
            area.addView(scroll);
            if (tab <= 4 || tab == 7 || tab == 8 || tab == 9 || tab == SNAPSHOT) periodChooser(body);
            if (tab == SNAPSHOT) home();
            else if (tab == GRAPHS) chartsPage(scroll);
            else if (tab == 1) movements();
            else if (tab == 3) recurring();
            else if (tab == 4) accounts();
            else if (tab == 7) insightsPage();
            else if (tab == 8) forecastPage();
            else if (tab == 9) budgetsPage();
            else if (tab == 10) shortcutsPage();
            else settingsPage(tab == 6);
            if (tab == 1 || tab == 3 || tab == 4) addButton(area);
        }
        openWidgetAction();
        if(pendingRestore)showRestorePreview();
        if(cloudListPending&&!backupBusy()){cloudListPending=false;if(cloudFiles!=null)showCloudFiles();else startDrive(DriveTask.LIST,"");}
    }
    private void renderSectionHeader() {
        LinearLayout header=row();header.setPadding(dp(12),dp(8),dp(16),dp(8));
        ImageView home = icon(R.drawable.nav_home, INK);
        home.setContentDescription("חזרה למסך הבית");
        home.setPadding(dp(12),dp(12),dp(12),dp(12));
        home.setOnClickListener(v -> { tab = HOME; render(); });
        header.addView(home,new LinearLayout.LayoutParams(dp(48),dp(48)));
        ImageView menu=icon(R.drawable.nav_menu,INK);menu.setTag("menu-toggle");menu.setContentDescription("פתיחת תפריט");menu.setPadding(dp(12),dp(12),dp(12),dp(12));menu.setOnClickListener(v->showDrawer());header.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout branding=column();branding.addView(bold(tabs[tab],21,INK));TextView appName=text("PocketFlow",12,MUTED);appName.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);branding.addView(appName);header.addView(branding,new LinearLayout.LayoutParams(0,-2,1));
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.pocketflow_logo);logo.setContentDescription("PocketFlow");logo.setBackground(shape(0xff04112B,10));logo.setClipToOutline(true);header.addView(logo,new LinearLayout.LayoutParams(dp(40),dp(40)));page.addView(header);
        String undo=store.undoLabel();if(!undo.isEmpty()){LinearLayout undoBar=row();undoBar.setPadding(dp(16),0,dp(16),dp(4));undoBar.addView(text("פעולה אחרונה: "+undo,12,MUTED),new LinearLayout.LayoutParams(0,-2,1));TextView undoButton=button("ביטול פעולה אחרונה",false,()->{try{int beforeDay=store.reporting().day;store.undo();if(beforeDay!=store.reporting().day)resetReportSelection();render();toast("הפעולה בוטלה");}catch(Exception e){toast("לא ניתן לבטל: "+e.getMessage());}});undoButton.setTag("undo-last");undoBar.addView(undoButton);page.addView(undoBar);}
    }

    private void attachPage(LinearLayout previous, boolean opening, boolean closing) {
        FrameLayout host = new FrameLayout(this);
        host.setBackgroundColor(BG);
        shell = host;
        if (opening || closing) {
            ViewGroup parent = (ViewGroup) previous.getParent();
            if (parent != null) parent.removeView(previous);
        }

        LinearLayout destination = page;
        if (opening) {
            host.addView(previous, new FrameLayout.LayoutParams(-1, -1));
            previous.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            View touchShield = new View(this);
            touchShield.setClickable(true);
            touchShield.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            host.addView(touchShield, new FrameLayout.LayoutParams(-1, -1));
            destination.setAlpha(0);
            host.addView(destination, new FrameLayout.LayoutParams(-1, -1));
            Rect anchor = new Rect(launchAnchor);
            destination.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                                     int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    destination.removeOnLayoutChangeListener(this);
                    float[] shift = anchorShift(destination, anchor);
                    destination.setScaleX(.27f);
                    destination.setScaleY(.27f);
                    destination.setTranslationX(shift[0]);
                    destination.setTranslationY(shift[1]);
                    destination.animate().alpha(1).scaleX(1).scaleY(1).translationX(0).translationY(0)
                            .setDuration(340).setInterpolator(new PathInterpolator(.17f, .86f, .24f, 1f))
                            .withEndAction(() -> { host.removeView(previous); host.removeView(touchShield); }).start();
                }
            });
        } else {
            host.addView(destination, new FrameLayout.LayoutParams(-1, -1));
        }
        destination.setFitsSystemWindows(true);
        setContentView(host);
        if (closing) {
            Rect anchor = new Rect(launchAnchor);
            previous.setAlpha(1);
            previous.setScaleX(1);
            previous.setScaleY(1);
            previous.setTranslationX(0);
            previous.setTranslationY(0);
            // Overlay draws the outgoing screen without giving it focus or touch events.
            host.getOverlay().add(previous);
            destination.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                                     int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    destination.removeOnLayoutChangeListener(this);
                    if (shell != host) return;
                    float[] shift = anchorShift(destination, anchor);
                    previous.animate().alpha(0).scaleX(.25f).scaleY(.25f)
                            .translationX(shift[0]).translationY(shift[1])
                            .setDuration(290).setInterpolator(new PathInterpolator(.28f, .05f, .47f, 1f))
                            .withEndAction(() -> host.getOverlay().remove(previous)).start();
                }
            });
        }
        if (tab == HOME) launchAnchor = null;
    }

    private float[] anchorShift(View screen, Rect anchor) {
        int[] position = new int[2];
        screen.getLocationOnScreen(position);
        return new float[]{anchor.exactCenterX() - position[0] - screen.getWidth() / 2f,
                anchor.exactCenterY() - position[1] - screen.getHeight() / 2f};
    }

    private void openHomeApp(int target, View anchor) {
        if (anchor.getWidth() > 0 && anchor.getHeight() > 0) {
            int[] position = new int[2];
            anchor.getLocationOnScreen(position);
            launchAnchor = new Rect(position[0], position[1], position[0] + anchor.getWidth(), position[1] + anchor.getHeight());
            openingFromHome = true;
        }
        if (target == 1) {
            chargesMode = false;
            categoryFilter = merchantFilter = query = "";
            accountFilter = 0;
            advancedFilter = new MovementFilter();
        }
        tab = target;
        render();
    }

    private TextView createAddButton() {
        TextView add = button("＋  " + (tab == 3 ? "הוראת קבע" : tab == 4 ? "חשבון / כרטיס" : "הוספת תנועה"), true,
                () -> { if (tab == 3) ruleDialog(); else if (tab == 4) accountDialog(); else transactionDialog(null); });
        add.setTag("add-movement");
        add.setElevation(dp(4));
        return add;
    }

    private void addButton(FrameLayout area) {
        TextView add = createAddButton();
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = dp(18);
        area.addView(add, params);
    }

    private void launcher(FrameLayout area) {
        LinearLayout wallpaper = column();
        wallpaper.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xff10213f, 0xff263d69, 0xff292345}));
        area.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));
        final int white = 0xfff4f7ff;
        final int muted = 0xffcbd4ef;
        List<TextView> indicators = new ArrayList<>();
        Runnable[] mountWidget = new Runnable[HOME_PAGES.length];
        LauncherPager pager = new LauncherPager(this, homePage, index -> {
            homePage = index;
            exitBackAt = -1;
            stylePageIndicators(indicators);
            // Prepare widget data after the page transition.
            if (mountWidget[index] != null) wallpaper.postDelayed(mountWidget[index], 240);
        });
        pager.setTag("launcher-pager");
        wallpaper.addView(pager, new LinearLayout.LayoutParams(-1, 0, 1));
        LauncherGrid grid = new LauncherGrid(wallpaper, pager, prefs.getString(LauncherLayout.PREFERENCE, ""),
                (target, position) -> launcherApp(target, position, white),
                order -> prefs.edit().putString(LauncherLayout.PREFERENCE, order).apply(),
                outlineShape(0x08ffffff, 18, 0x35d4e8ff),
                outlineShape(0x35d4e8ff, 18, 0xffd4e8ff), dp(52));
        LinearLayout launcherRoot = page;
        for (int index = 0; index < HOME_PAGES.length; index++) {
            final int number = index;
            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            scroll.setVerticalScrollBarEnabled(false);
            LinearLayout holder = column();
            holder.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            scroll.addView(holder);
            LinearLayout content = column();
            int available = getResources().getDisplayMetrics().widthPixels;
            content.setPadding(dp(18), dp(15), dp(18), dp(22));
            holder.addView(content, new LinearLayout.LayoutParams(Math.min(available, dp(580)), -2));
            String caption = index >= GRAPH_WIDGET_PAGE ? "גרף מתחלף" : HOME_PAGES[index];
            TextView heading = bold(caption, 22, white);
            heading.setPadding(0, dp(4), 0, dp(9));
            content.addView(heading);
            if (index == HOME || index >= LauncherLayout.APP_PAGES) launcherPeriod(content);
            if (index == HOME) {
                LinearLayout widget = chargeSummary(content, store.overview(month), true);
                widget.setOnClickListener(v -> openHomeApp(SNAPSHOT, widget));
                widget.setFocusable(true);
            }
            if (index < LauncherLayout.APP_PAGES) {
                grid.registerPage(index, scroll);
                launcherAppSlots(content, grid, index);
            } else {
                LinearLayout widget = column();
                content.addView(widget);
                TextView placeholder = text("הנתונים יוצגו בפתיחת הדף", 14, muted);
                widget.addView(placeholder);
                mountWidget[index] = () -> {
                    if (page != launcherRoot || homePage != number || !widget.isAttachedToWindow()
                            || placeholder.getParent() != widget) return;
                    int savedPosition = homeScroll[number];
                    widget.removeView(placeholder);
                    mountWidget[number] = null;
                    launcherGraph(widget, number == GRAPH_WIDGET_PAGE);
                    widget.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                        @Override public void onLayoutChange(View v, int l, int t, int r, int b, int oldL, int oldT, int oldR, int oldB) {
                            widget.removeOnLayoutChangeListener(this);
                            scroll.scrollTo(0, savedPosition);
                        }
                    });
                };
            }
            pager.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
            int savedScroll = homeScroll[number];
            scroll.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override public void onLayoutChange(View v, int l, int t, int r, int b, int oldL, int oldT, int oldR, int oldB) {
                    scroll.removeOnLayoutChangeListener(this);
                    if (number < LauncherLayout.APP_PAGES) scroll.scrollTo(0, savedScroll);
                    scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> {
                        if (number < LauncherLayout.APP_PAGES || mountWidget[number] == null) homeScroll[number] = y;
                    });
                }
            });
        }

        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(-2, -2);
        addParams.gravity = Gravity.CENTER_HORIZONTAL;
        addParams.setMargins(0, dp(6), 0, dp(2));
        wallpaper.addView(createAddButton(), addParams);

        LinearLayout footer = row();
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(8), 0, dp(8), 0);
        for (int index = 0; index < HOME_PAGES.length; index++) {
            final int target = index;
            TextView indicator = textAction(HOME_PAGES[index], () -> pager.showPage(target));
            indicator.setTag("launcher-page-" + index);
            indicator.setTextColor(muted);
            indicator.setTextSize(12);
            indicator.setPadding(dp(3), dp(9), dp(3), dp(9));
            indicators.add(indicator);
            footer.addView(indicator, new LinearLayout.LayoutParams(0, -2, 1));
        }
        stylePageIndicators(indicators);
        wallpaper.addView(footer);
        TextView credit = text("Created by Tal Azulay", 11, muted);
        credit.setTag("launcher-credit");
        credit.setGravity(Gravity.CENTER);
        credit.setPadding(0, 0, 0, dp(6));
        wallpaper.addView(credit);
        if (mountWidget[homePage] != null) wallpaper.post(mountWidget[homePage]);
    }

    private void launcherPeriod(LinearLayout content) {
        TextView period = button(monthNames[month.getMonthValue() - 1] + " " + month.getYear()
                + "  ·  " + periodRange(month) + "  ⌄", false, this::pickMonth);
        period.setTag("period-picker");
        content.addView(period);
        space(content, 10);
    }

    private void launcherAppSlots(LinearLayout content, LauncherGrid grid, int pageIndex) {
        for (int start = 0; start < LauncherLayout.SLOTS_PER_PAGE; start += LauncherLayout.COLUMNS) {
            LinearLayout shortcuts = row();
            shortcuts.setGravity(Gravity.TOP);
            for (int column = 0; column < LauncherLayout.COLUMNS; column++) {
                int index = pageIndex * LauncherLayout.SLOTS_PER_PAGE + start + column;
                FrameLayout slot = new FrameLayout(this);
                slot.setTag("launcher-slot-" + index);
                slot.setMinimumHeight(dp(90));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
                params.setMargins(dp(2), dp(2), dp(2), dp(2));
                shortcuts.addView(slot, params);
                grid.registerSlot(index, slot);
            }
            content.addView(shortcuts);
        }
    }

    private void launcherGraph(LinearLayout host, boolean first) {
        LinearLayout widget = card(host);
        widget.setTag(first ? "launcher-graph-widget" : "launcher-second-graph-widget");
        chartPicker(widget, store.overview(month),
                first ? "launcher-chart-selector" : "launcher-second-chart-selector",
                first ? "homeChart" : "homeChartSecond");
        TextView open = button("כל הגרפים", false, () -> {});
        open.setOnClickListener(v -> openHomeApp(GRAPHS, open));
        widget.addView(open);
    }

    private void stylePageIndicators(List<TextView> indicators) {
        for (int index = 0; index < indicators.size(); index++) {
            TextView indicator = indicators.get(index);
            boolean active = index == homePage;
            indicator.setSelected(active);
            indicator.setText((active ? "●  " : "○  ") + HOME_PAGES[index]);
            indicator.setTextColor(active ? 0xffffffff : 0xffcbd4ef);
        }
    }

    private LinearLayout launcherApp(int target, int position, int labelColor) {
        LinearLayout app = column();
        app.setTag("launcher-app-" + target);
        app.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        app.setPadding(dp(3), dp(9), dp(3), dp(9));
        app.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x30ffffff), null, shape(Color.WHITE, 19)));
        int[] colors = {0xffd4e8ff, 0xffd0f0e7, 0xffe9dcff};
        int[] ink = {0xff123356, 0xff17433f, 0xff3a2c63};
        ImageView glyph = icon(APP_ICONS[target], ink[position % ink.length]);
        glyph.setFocusable(false);
        glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        glyph.setBackground(shape(colors[position % colors.length], 19));
        glyph.setPadding(dp(14), dp(14), dp(14), dp(14));
        app.addView(glyph, new LinearLayout.LayoutParams(dp(55), dp(55)));
        space(app, 4);
        TextView label = text(tabs[target], 13, labelColor);
        label.setGravity(Gravity.CENTER);
        app.addView(label);
        app.setContentDescription(tabs[target]);
        app.setFocusable(true);
        app.setOnClickListener(v -> openHomeApp(target, glyph));
        return app;
    }

    private ImageView icon(int resource,int color){ImageView image=new ImageView(this);image.setImageResource(resource);image.setColorFilter(color);image.setFocusable(true);return image;}
    private void showDrawer(){
        exitBackAt=-1;
        drawer=new Dialog(this);drawer.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout sheet=column();sheet.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);sheet.setPadding(dp(12),dp(28),dp(12),dp(12));sheet.setBackground(shape(SURFACE,24));
        LinearLayout heading=row();heading.setPadding(dp(12),dp(12),dp(12),dp(20));ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.pocketflow_logo);heading.addView(logo,new LinearLayout.LayoutParams(dp(48),dp(48)));TextView brand=bold("PocketFlow",24,INK);brand.setPadding(dp(12),0,dp(12),0);heading.addView(brand,new LinearLayout.LayoutParams(0,-2,1));sheet.addView(heading);
        View divider=new View(this);divider.setBackgroundColor(OUTLINE);sheet.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        ScrollView scroll=new ScrollView(this);LinearLayout items=column();items.setPadding(0,dp(16),0,dp(16));scroll.addView(items);sheet.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        for(int i=0;i<tabs.length;i++){final int selected=i;LinearLayout item=row();item.setTag("nav-"+i);item.setContentDescription(tabs[i]);item.setFocusable(true);item.setPadding(dp(16),dp(10),dp(16),dp(10));if(i==tab)item.setBackground(shape(CONTAINER,28));ImageView glyph=icon(APP_ICONS[i],i==tab?palette.onContainer:MUTED);item.addView(glyph,new LinearLayout.LayoutParams(dp(24),dp(24)));TextView label=bold(tabs[i],16,i==tab?palette.onContainer:INK);label.setPadding(dp(14),0,dp(14),0);item.addView(label,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));lp.bottomMargin=dp(6);items.addView(item,lp);item.setOnClickListener(v->{drawer.dismiss();tab=selected;render();});}
        TextView powered=text("Powered by Azulay",12,MUTED);powered.setTag("drawer-credit");powered.setGravity(Gravity.CENTER);powered.setPadding(0,dp(18),0,dp(18));sheet.addView(powered);
        drawer.setContentView(sheet);drawer.setCanceledOnTouchOutside(true);Window window=drawer.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams attributes=window.getAttributes();attributes.dimAmount=.45f;attributes.gravity=Gravity.RIGHT;window.setAttributes(attributes);}
        drawer.show();if(window!=null)window.setLayout(Math.min(dp(360),getResources().getDisplayMetrics().widthPixels-dp(42)),WindowManager.LayoutParams.MATCH_PARENT);
    }
    private TextView textAction(String label,Runnable action){TextView v=button(label,false,action);v.setBackgroundColor(Color.TRANSPARENT);return v;}
    private void periodChooser(LinearLayout host){
        LinearLayout box=card(host);box.setPadding(dp(8),dp(5),dp(8),dp(8));LinearLayout controls=row();
        TextView previous=textAction("הקודמת",()->changeMonth(-1));previous.setTag("period-previous");previous.setEnabled(month.isAfter(YearMonth.of(1900,1)));controls.addView(previous);
        TextView selected=textAction(monthNames[month.getMonthValue()-1]+" "+month.getYear()+"  ⌄",this::pickMonth);selected.setTag("period-picker");controls.addView(selected,new LinearLayout.LayoutParams(0,-2,1));
        TextView next=textAction("הבאה",()->changeMonth(1));next.setTag("period-next");next.setEnabled(month.isBefore(YearMonth.of(2100,12)));controls.addView(next);box.addView(controls);
        TextView dates=text(periodRange(month),12,MUTED);dates.setGravity(Gravity.CENTER);dates.setTextDirection(View.TEXT_DIRECTION_LTR);box.addView(dates);
        if(!month.equals(store.reporting().label(LocalDate.now()))){TextView current=textAction("התקופה הנוכחית",()->{month=store.reporting().label(LocalDate.now());render();});current.setTag("period-current");box.addView(current);}
    }
    private String periodRange(YearMonth value){return store.reporting().start(value).format(displayDate)+" – "+store.reporting().end(value).format(displayDate);}
    private void changeMonth(int delta){YearMonth next=month.plusMonths(delta);if(next.getYear()>=1900&&next.getYear()<=2100){month=next;render();}}
    private void pickMonth(){
        LinearLayout f=form();hint(f,"בחר חודש ושנה להצגה. טווח התאריכים המדויק מופיע למטה.");EditText year=field(f,"שנה",String.valueOf(month.getYear()),true);year.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);year.setTag("period-year");
        int[] selected={month.getMonthValue()};List<TextView> chips=new ArrayList<>();TextView preview=bold("",16,INK);preview.setGravity(Gravity.CENTER);preview.setTextDirection(View.TEXT_DIRECTION_LTR);AlertDialog[] active={null};
        Runnable update=()->{int y=0;try{y=Integer.parseInt(year.getText().toString());}catch(NumberFormatException ignored){}boolean valid=y>=1900&&y<=2100;preview.setText(valid?periodRange(YearMonth.of(y,selected[0])):"יש להזין שנה בין 1900 ל־2100");preview.setTextColor(valid?INK:RED);if(active[0]!=null)active[0].getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(valid);for(int i=0;i<chips.size();i++){boolean checked=i+1==selected[0];chips.get(i).setBackground(outlineShape(checked?CONTAINER:SURFACE,12,checked?PRIMARY:OUTLINE));chips.get(i).setTextColor(checked?palette.onContainer:INK);chips.get(i).setSelected(checked);}};
        for(int r=0;r<4;r++){LinearLayout row=row();for(int c=0;c<3;c++){final int m=r*3+c+1;TextView chip=button(monthNames[m-1],false,()->{selected[0]=m;update.run();});chip.setTag("period-month-"+m);chip.setTextSize(13);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(chip,lp);chips.add(chip);}f.addView(row);}
        space(f,10);f.addView(preview);f.addView(textAction("התקופה הנוכחית",()->{month=store.reporting().label(LocalDate.now());render();if(active[0]!=null)active[0].dismiss();}));year.addTextChangedListener(changed(update));
        active[0]=dialog("בחירת חודש ושנה",f,()->{int y=Integer.parseInt(year.getText().toString());if(y<1900||y>2100)throw new IllegalArgumentException("יש להזין שנה בין 1900 ל־2100");month=YearMonth.of(y,selected[0]);render();});active[0].getButton(AlertDialog.BUTTON_POSITIVE).setText("הצגת התקופה");update.run();
    }
    private LinearLayout chargeSummary(LinearLayout host, Store.Overview overview, boolean widget) {
        LinearLayout summary=card(host);summary.setTag("home-section-summary");summary.setBackground(shape(CONTAINER,24));
        TextView occasional=text("סה״כ חיובים מזדמנים: "+money(overview.occasional),18,palette.onContainer);occasional.setTag("home-occasional");summary.addView(occasional);
        TextView recurring=text("סה״כ הוראות קבע: "+money(overview.recurring),18,palette.onContainer);recurring.setTag("home-recurring");summary.addView(recurring);space(summary,14);hint(summary,"סה״כ חיובים");
        TextView total=bold(money(overview.total()),36,palette.onContainer);total.setTag("home-total");summary.addView(total);if(!widget)total.setOnClickListener(v->{tab=1;chargesMode=false;categoryFilter="";accountFilter=0;query="";advancedFilter=new MovementFilter();merchantFilter="";render();});
        hint(summary,"לפי תאריך העסקה · כולל חיובים שבוצעו וצפויים בתקופה הנבחרת");if(overview.refunds>0)hint(summary,"קוזזו זיכויים בסך "+money(overview.refunds)+" · בחודש קבלת הזיכוי");
        if(overview.planned>0)hint(summary,"מתוכם "+money(overview.planned)+" חיובים צפויים שטרם אושרו");
        if(incomes()){long income=store.total(month,"income");hint(summary,"הכנסות שבוצעו: "+money(income));hint(summary,"סה״כ חיובים פחות הכנסות שבוצעו: "+money(overview.total()-income));}
        return summary;
    }

    private void home(){
        title(body,"תמונת המצב שלך");Set<String> visibleSections=new HashSet<>(Tools.cards(this));
        Store.Overview overview=store.overview(month);
        chargeSummary(body, overview, false);
        if (visibleSections.contains("graph")) {
            LinearLayout graph = card(body);
            graph.setTag("home-section-graph");
            title(graph, "החיובים שלך בגרף");
            chartPicker(graph, overview, "chart-selector", "homeChart");
        }
        if(visibleSections.contains("charges")){
        LinearLayout upcoming=card(body);upcoming.setTag("home-section-charges");upcoming.setBackground(shape(palette.secondaryContainer,20));title(upcoming,"לוח החיובים בתקופה");long totalCharges=0;List<Store.Charge> charges=store.periodCharges(month);for(Store.Charge c:charges)totalCharges+=c.amount;upcoming.addView(bold(money(totalCharges),26,PRIMARY));hint(upcoming,"חיובים לאחר זיכויים ופחות הכנסות לפי מועד התשלום. מזומן ודיירקט כלולים. זו אינה יתרת הבנק.");upcoming.addView(button("פתיחת לוח החיובים",false,()->{tab=1;chargesMode=true;render();}));
        }
        if(visibleSections.contains("recent")){
        LinearLayout recent=card(body);recent.setTag("home-section-recent");title(recent,"התנועות האחרונות");List<Store.Tx> tx=store.month(month);if(tx.isEmpty()){hint(recent,"מתחילים בקטן: הוסף הוצאה או הגדר את הכרטיסים שלך.");recent.addView(button("הגדרת חשבונות וכרטיסים",false,()->{tab=4;render();}));}else for(int i=0;i<Math.min(4,tx.size());i++)txRow(recent,tx.get(i));
        }
        if(visibleSections.contains("export")){
        LinearLayout export=card(body);export.setTag("home-section-export");hint(export,"ייצוא התנועות של החודש הנבחר לקובץ שנפתח ב־Excel");export.addView(button("ייצוא החודש · CSV",false,this::exportCsv));
        }
        if(visibleSections.contains("insights")){
        LinearLayout info=card(body);info.setTag("home-section-insights");title(info,"מידע שעוזר לתכנן");List<Analytics.Insight> insights=analytics().insights(month,LocalDate.now());if(!insights.isEmpty())hint(info,insights.get(0).title);List<String> alerts=analytics().alerts(LocalDate.now());for(int n=0;n<Math.min(3,alerts.size());n++)hint(info,alerts.get(n));for(int target:new int[]{7,8,9,10})info.addView(button(tabs[target],false,()->{tab=target;render();}));
        }
        Map<String,View> sections=new HashMap<>();for(String key:Arrays.asList("summary","insights","graph","charges","recent","export")){View v=body.findViewWithTag("home-section-"+key);if(v!=null){sections.put(key,v);body.removeView(v);}}for(String key:Tools.cards(this))if(sections.containsKey(key))body.addView(sections.get(key));body.addView(button("התאמת תמונת המצב",false,this::personalizeHome));
    }
    private void showCategory(String key){accountFilter=0;merchantFilter="";categoryFilter=key;tab=1;chargesMode=false;query="";advancedFilter=new MovementFilter();render();}
    private void showMerchant(String key){merchantFilter=key;categoryFilter="";accountFilter=0;query="";advancedFilter=new MovementFilter();tab=1;chargesMode=false;render();}
    private void legend(LinearLayout host,Map<String,Long> sums){legend(host,sums,this::showCategory);}
    private void legend(LinearLayout host,Map<String,Long> sums,ChartView.Pick pick){
        int i=0;for(Map.Entry<String,Long> e:sums.entrySet()){
            LinearLayout r=row();r.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);int color=ChartView.colors(this)[i++%ChartView.COLORS.length];
            TextView marker=bold("●",14,color);marker.setTag("legend-marker-"+e.getKey());marker.setGravity(Gravity.CENTER);marker.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);r.addView(marker,new LinearLayout.LayoutParams(dp(24),-2));
            TextView name=bold(e.getKey(),14,color);name.setTag("legend-label-"+e.getKey());name.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);name.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);name.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);name.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);r.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            r.addView(text(money(e.getValue()),14,INK));r.setPadding(0,dp(6),0,dp(6));if(pick!=null){r.setOnClickListener(v->pick.select(e.getKey()));r.setFocusable(true);}r.setContentDescription(e.getKey()+", "+money(e.getValue())+(pick==null?"":", לפתיחת התנועות"));host.addView(r);
        }
    }
    private void movements(){
        title(body,"התנועות שלך");LinearLayout switches=row();switches.addView(button("עסקאות",!chargesMode,()->{chargesMode=false;render();}),new LinearLayout.LayoutParams(0,-2,1));switches.addView(button("לוח חיובים",chargesMode,()->{chargesMode=true;render();}),new LinearLayout.LayoutParams(0,-2,1));body.addView(switches);space(body,12);
        if(chargesMode){
            LinearLayout explanation=card(body);hint(explanation,"לפי מועד ירידת הכסף. פירעון הכרטיס אינו הוצאה נוספת. חיוב קבוע הוא אומדן ללא חיבור לבנק, ריבית או עמלות שלא הוזנו.");
            List<Store.Charge> charges=store.periodCharges(month);long chargeTotal=0;for(Store.Charge charge:charges)chargeTotal=Math.addExact(chargeTotal,charge.amount);
            LinearLayout summary=card(body);summary.setBackground(shape(CONTAINER,24));hint(summary,"סה״כ בלוח החיובים");TextView total=bold(money(chargeTotal),32,palette.onContainer);total.setTag("charges-total");summary.addView(total);hint(summary,"חיובים פחות הכנסות · כולל חיובים צפויים ואומדנים · "+charges.size()+" רשומות");
            if(charges.isEmpty())hint(explanation,"אין חיובים רשומים לתקופה זו.");
            for(Store.Charge c:charges){LinearLayout block=card(body);block.addView(bold(c.name,16,INK));block.addView(bold(money(c.amount),21,c.amount<0?PRIMARY:INK));hint(block,c.date.format(displayDate)+" · "+store.account(c.account).name+(c.projected?" · צפוי":" · לפי העסקאות שנרשמו"));if(c.tx>0)block.setOnClickListener(v->{for(Store.Tx t:store.transactions())if(t.id==c.tx){transactionDialog(t);break;}});}
            return;
        }
        LinearLayout filters=card(body);if(!merchantFilter.isEmpty()){hint(filters,"בית עסק: "+merchantFilter);TextView clear=button("ניקוי סינון בית עסק",false,()->{merchantFilter="";render();});clear.setTag("clear-merchant-filter");filters.addView(clear);}EditText search=new EditText(this);search.setSingleLine(true);configureText(search);styleInput(search);search.setTextSize(15);search.setTag("movement-search");search.setHint("חיפוש בית עסק, תיאור, תגית או קטגוריה");search.setText(query);filters.addView(search);
        List<String> cat=categoryOptions();cat.add(0,"כל הקטגוריות");
        Spinner category=spinner(filters,"קטגוריה",cat);category.setSelection(categoryFilter.isEmpty()?0:Math.max(0,cat.indexOf(categoryFilter)));
        List<Store.Account> ac=paymentAccounts();List<String> names=new ArrayList<>();names.add("כל אמצעי התשלום");for(Store.Account a:ac)names.add(a.name);Spinner accounts=spinner(filters,"אמצעי תשלום",names);for(int i=0;i<ac.size();i++)if(ac.get(i).id==accountFilter)accounts.setSelection(i+1);
        LinearLayout filterActions=row();filterActions.addView(button("סינון מתקדם",false,this::advancedSearchDialog),new LinearLayout.LayoutParams(0,-2,1));filterActions.addView(button("שמירת חיפוש",false,this::saveSearchDialog),new LinearLayout.LayoutParams(0,-2,1));filterActions.addView(button("חיפושים שמורים",false,this::savedSearchDialog),new LinearLayout.LayoutParams(0,-2,1));filters.addView(filterActions);if(advancedFilter.active()){hint(filters,"סינון מתקדם פעיל · סכומי הסיכום מתייחסים לתוצאות בלבד");if(!advancedFilter.from.isEmpty()||!advancedFilter.to.isEmpty())hint(filters,"טווח התנועות: "+(advancedFilter.from.isEmpty()?"ללא תאריך התחלה":LocalDate.parse(advancedFilter.from).format(displayDate))+" עד "+(advancedFilter.to.isEmpty()?"ללא תאריך סיום":LocalDate.parse(advancedFilter.to).format(displayDate)));if(!advancedFilter.family.isEmpty())hint(filters,"בן משפחה: "+advancedFilter.family);filters.addView(button("ניקוי כל הסינונים",false,()->{advancedFilter=new MovementFilter();categoryFilter="";merchantFilter="";query="";advancedFilter=new MovementFilter();accountFilter=0;render();}));}
        listHost=column();body.addView(listHost);refreshList();
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString();lastScreen=captureScreen();movementLimit=60;if(pendingSearch!=null)searchHandler.removeCallbacks(pendingSearch);pendingSearch=()->{if(tab==1&&!chargesMode)refreshList();};searchHandler.postDelayed(pendingSearch,220);}public void afterTextChanged(Editable e){}});
        category.setOnItemSelectedListener(selection(()->{String next=category.getSelectedItemPosition()==0?"":category.getSelectedItem().toString();if(next.equals(categoryFilter))return;categoryFilter=next;movementLimit=60;refreshList();}));
        accounts.setOnItemSelectedListener(selection(()->{long next=accounts.getSelectedItemPosition()==0?0:ac.get(accounts.getSelectedItemPosition()-1).id;if(next==accountFilter)return;accountFilter=next;movementLimit=60;refreshList();}));
    }
    private AdapterView.OnItemSelectedListener selection(Runnable r){return new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){r.run();}public void onNothingSelected(AdapterView<?> p){}};}
    private void refreshList(){
        if(listHost==null)return;ReportPeriod period=store.reporting();listHost.removeAllViews();List<Store.Tx> found=new ArrayList<>();long expense=0,income=0,pendingExpense=0,pendingIncome=0;
        for(Store.Tx t:store.transactions()){
            if(!advancedFilter.matches(t,month,period,categoryFilter,accountFilter,merchantFilter,query))continue;
            found.add(t);if(t.status==1){if(t.kind.equals("income"))income=Math.addExact(income,t.amount);else expense=Math.addExact(expense,Store.netExpense(t));}else{if(t.kind.equals("income"))pendingIncome=Math.addExact(pendingIncome,t.amount);else pendingExpense=Math.addExact(pendingExpense,Store.netExpense(t));}
        }
        LinearLayout summary=card(listHost);summary.setBackground(shape(CONTAINER,24));hint(summary,"סה״כ הוצאות שבוצעו");TextView total=bold(money(expense),32,palette.onContainer);total.setTag("movements-total");summary.addView(total);hint(summary,found.size()+" תנועות · לפי טווח התאריכים והסינון הנבחרים");
        TextView expected=text("הוצאות צפויות: "+money(pendingExpense),15,palette.onContainer);expected.setTag("movements-pending");summary.addView(expected);
        if(incomes()||income>0||pendingIncome>0){TextView received=text("הכנסות שבוצעו: "+money(income),15,palette.onContainer);received.setTag("movements-income");summary.addView(received);TextView net=text("הוצאות פחות הכנסות שבוצעו: "+money(expense-income),15,palette.onContainer);net.setTag("movements-net");summary.addView(net);if(pendingIncome>0)summary.addView(text("הכנסות צפויות: "+money(pendingIncome),15,palette.onContainer));}
        if(found.isEmpty()){LinearLayout empty=card(listHost);hint(empty,"אין תנועות שמתאימות לבחירה.");}for(int n=0;n<Math.min(found.size(),movementLimit);n++)txRow(card(listHost),found.get(n));if(found.size()>movementLimit)listHost.addView(button("הצגת תנועות נוספות ("+Math.min(movementLimit,found.size())+" מתוך "+found.size()+")",false,()->{movementLimit+=60;refreshList();}));
        if(tab==1)lastScreen=captureScreen();
    }
    private void txRow(LinearLayout host,Store.Tx t){
        LinearLayout r=row();LinearLayout info=column();info.addView(bold(t.merchant.trim().isEmpty()?t.category:t.merchant,16,INK));hint(info,t.category+" · "+LocalDate.parse(t.date).format(displayDate));hint(info,store.account(t.account).name+(t.tag.trim().isEmpty()?"":" · "+t.tag)+(t.count>1?" · "+t.count+" תשלומים":""));r.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout amount=column();amount.addView(bold((t.kind.equals("income")?"+ ":t.kind.equals("refund")?"זיכוי − ":"")+money(t.amount),18,!t.kind.equals("expense")?PRIMARY:INK));if(!t.currency.equals("ILS"))amount.addView(text(Finance.decimal(t.originalAmount)+" "+t.currency,12,MUTED));if(t.status==0)amount.addView(text("צפוי · לאישור",12,(palette.dark?0xffDFBA77:0xff946513)));r.addView(amount);host.addView(r);r.setPadding(0,dp(8),0,dp(8));r.setOnClickListener(v->transactionDialog(t));r.setFocusable(true);
    }
    private void chartPicker(LinearLayout host, Store.Overview overview, String tag, String preferenceKey) {
        Spinner picker = spinner(host, "בחירת גרף", ChartCatalog.LABELS);
        picker.setTag(tag);
        int saved = prefs.getInt(preferenceKey, 0);
        int initial = saved >= 0 && saved < ChartCatalog.LABELS.size() ? saved : 0;
        picker.setSelection(initial);
        LinearLayout content = column();
        content.setTag("chart-content");
        host.addView(content);
        drawHomeChart(content, initial, overview);
        final int[] drawn = {initial};
        picker.setOnItemSelectedListener(selection(() -> {
            int selected = picker.getSelectedItemPosition();
            if (selected < 0 || selected == drawn[0]) return;
            drawn[0] = selected;
            exitBackAt = -1;
            prefs.edit().putInt(preferenceKey, selected).apply();
            drawHomeChart(content, selected, overview);
        }));
    }

    private void chartsPage(ScrollView scroll) {
        title(body, "כל הגרפים");
        TextView reorder = button("שינוי סדר הגרפים", false, this::chartOrderDialog);
        reorder.setTag("graph-reorder");
        body.addView(reorder);
        space(body, 12);
        Store.Overview overview = store.overview(month);
        List<Integer> order = ChartCatalog.order(Tools.read(this).optString("chartOrder"));
        Map<LinearLayout, Integer> pending = new LinkedHashMap<>();
        for (int id : order) {
            LinearLayout section = card(body);
            section.setTag("graph-section-" + id);
            title(section, ChartCatalog.LABELS.get(id));
            LinearLayout content = column();
            content.setMinimumHeight(dp(280));
            section.addView(content);
            pending.put(content, id);
        }
        Runnable drawVisible = () -> {
            if (!scroll.isAttachedToWindow() || scroll.getHeight() == 0) return;
            int cutoff = scroll.getScrollY() + scroll.getHeight() + dp(180);
            Iterator<Map.Entry<LinearLayout, Integer>> entries = pending.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<LinearLayout, Integer> entry = entries.next();
                LinearLayout content = entry.getKey();
                View section = (View) content.getParent();
                if (section.getTop() > cutoff) break;
                if (section.getBottom() >= scroll.getScrollY() - dp(180)) {
                    entries.remove();
                    content.setMinimumHeight(0);
                    drawHomeChart(content, entry.getValue(), overview);
                    break;
                }
            }
        };
        body.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> scroll.post(drawVisible));
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> drawVisible.run());
        scroll.post(drawVisible);
    }

    private void chartOrderDialog() {
        LinearLayout form = form();
        hint(form, "השתמש בחצים כדי לשנות את סדר הגרפים במסך.");
        LinearLayout rows = column();
        form.addView(rows);
        List<Integer> order = ChartCatalog.order(Tools.read(this).optString("chartOrder"));
        drawChartOrder(rows, order);
        dialog("סדר הגרפים", form, () -> {
            StringJoiner saved = new StringJoiner(",");
            for (int id : order) saved.add(String.valueOf(id));
            JSONObject data = Tools.read(this).put("chartOrder", saved.toString());
            edit("שינוי סדר הגרפים", () -> Tools.write(this, data, true));
            render();
        });
    }

    private void drawChartOrder(LinearLayout rows, List<Integer> order) {
        rows.removeAllViews();
        for (int index = 0; index < order.size(); index++) {
            final int position = index;
            String name = ChartCatalog.LABELS.get(order.get(index));
            LinearLayout row = row();
            row.addView(text((index + 1) + ". " + name, 15, INK), new LinearLayout.LayoutParams(0, -2, 1));
            TextView up = button("↑", false, () -> {
                ChartCatalog.move(order, position, -1);
                drawChartOrder(rows, order);
            });
            up.setTag("graph-up-" + order.get(index));
            up.setContentDescription("העלאת " + name);
            up.setEnabled(index > 0);
            TextView down = button("↓", false, () -> {
                ChartCatalog.move(order, position, 1);
                drawChartOrder(rows, order);
            });
            down.setTag("graph-down-" + order.get(index));
            down.setContentDescription("הורדת " + name);
            down.setEnabled(index < order.size() - 1);
            row.addView(up);
            row.addView(down);
            rows.addView(row);
        }
    }

    private void drawHomeChart(LinearLayout host,int choice,Store.Overview overview){
        host.removeAllViews();space(host,8);
        if(choice>=5){advancedChart(host,choice,overview);return;}
        if(choice==4){
            host.addView(bold(monthNames[month.getMonthValue()-1]+" "+month.getYear(),18,INK));Map<String,Long> values=new LinkedHashMap<>();values.put("הוראות קבע",overview.recurring);values.put("חיובים מזדמנים",overview.occasional);
            ChartView chart=new ChartView(this,values,true,null);chart.setTag("home-chart");host.addView(chart,new LinearLayout.LayoutParams(-1,dp(260)));legend(host,values,null);hint(host,"התקופה הנבחרת · כולל חיובים שבוצעו וצפויים. מעבר בין חודשים מציג כל חודש בנפרד.");return;
        }
        if(choice==3){
            Map<String,Long> values=new LinkedHashMap<>();Map<String,YearMonth> months=new HashMap<>();for(int i=5;i>=0;i--){YearMonth m=month.minusMonths(i);String label=m.getMonthValue()+"/"+(m.getYear()%100);values.put(label,store.overview(m).total());months.put(label,m);}
            ChartView chart=new ChartView(this,values,false,key->{month=months.get(key);tab=1;categoryFilter="";merchantFilter="";accountFilter=0;query="";advancedFilter=new MovementFilter();chargesMode=false;render();});chart.setTag("home-chart");host.addView(chart,new LinearLayout.LayoutParams(-1,dp(240)));hint(host,"חיובים שבוצעו וצפויים, כולל הוראות קבע. לחיצה על עמודה פותחת את החודש.");return;
        }
        Map<String,Long> values=choice==1?overview.merchants:choice==2?overview.payments:overview.categories;ChartView.Pick pick=choice==1?this::showMerchant:choice==2?this::showPayment:this::showCategory;
        hint(host,"כולל הוראות קבע וחיובים צפויים · זיכויים מקוזזים בסכומים. בטבעת מוצגות רק קבוצות עם הוצאה נטו חיובית.");ChartView chart=new ChartView(this,values,true,pick);chart.setTag("home-chart");host.addView(chart,new LinearLayout.LayoutParams(-1,dp(260)));legend(host,values,pick);if(values.isEmpty())hint(host,"כשתוסיף חיובים, הפילוח שלך יופיע כאן.");
    }
    private void openAnalyticsMonth(YearMonth selected,String category){month=selected;categoryFilter=category;merchantFilter="";accountFilter=0;query="";advancedFilter=new MovementFilter();tab=1;chargesMode=false;render();}
    private void advancedChart(LinearLayout host,int choice,Store.Overview overview){
        if(choice==5){
            Store.SeriesData data=store.cumulative(month,LocalDate.now());hint(host,"הוצאות נטו לאחר זיכויים · השוואה לאותם ימים בחודש הקודם · סכומים בשקלים");
            SeriesChartView chart=new SeriesChartView(this,data,true,null);chart.setTag("home-chart");host.addView(chart,new LinearLayout.LayoutParams(-1,dp(260)));int i=0;
            for(Map.Entry<String,long[]> entry:data.series.entrySet()){long[] amounts=entry.getValue();host.addView(bold("● "+entry.getKey()+": "+money(amounts.length==0?0:amounts[amounts.length-1]),15,ChartView.colors(this)[i++]));}
            if(data.labels.isEmpty())hint(host,"בחודש עתידי עדיין אין קצב הוצאות שבוצעו.");else{long[] selected=data.series.get("החודש הנבחר"),previous=data.series.get("החודש הקודם");long difference=selected[selected.length-1]-previous[previous.length-1];hint(host,"עד יום "+data.labels.size()+" בתקופה"+": "+(difference==0?"ללא שינוי לעומת החודש הקודם":money(Math.abs(difference))+(difference>0?" יותר":" פחות")+" לעומת אותם ימים בחודש הקודם"));}return;
        }
        if(choice==6){
            List<String> options=categoryOptions();Spinner category=spinner(host,"קטגוריה להצגה",options);category.setTag("trend-category");String saved=prefs.getString("trendCategory","");category.setSelection(Math.max(0,options.indexOf(saved)));LinearLayout content=column();host.addView(content);
            Runnable update=()->{String selected=category.getSelectedItem().toString();prefs.edit().putString("trendCategory",selected).apply();content.removeAllViews();Map<String,Long> values=new LinkedHashMap<>();Map<String,YearMonth> periods=new HashMap<>();for(int n=5;n>=0;n--){YearMonth period=month.minusMonths(n);String label=period.getMonthValue()+"/"+(period.getYear()%100);values.put(label,store.overview(period).categories.getOrDefault(selected,0L));periods.put(label,period);}ChartView chart=new ChartView(this,values,false,key->openAnalyticsMonth(periods.get(key),selected));chart.setTag("home-chart");content.addView(chart,new LinearLayout.LayoutParams(-1,dp(240)));hint(content,"חיובים שבוצעו וצפויים בקטגוריה, כולל הוראות קבע. לחיצה על חודש פותחת את הפירוט.");for(Map.Entry<String,Long> entry:values.entrySet()){TextView detail=text(entry.getKey()+" · "+money(entry.getValue()),14,INK);detail.setOnClickListener(v->openAnalyticsMonth(periods.get(entry.getKey()),selected));detail.setFocusable(true);content.addView(detail);}};
            update.run();category.setOnItemSelectedListener(selection(update));return;
        }
        if(choice==7){
            ChartView chart=new ChartView(this,overview.families,true,null);chart.setTag("home-chart");host.addView(chart,new LinearLayout.LayoutParams(-1,dp(260)));legend(host,overview.families,null);hint(host,"לפי בן המשפחה שהוזן בתנועה · כולל הוראות קבע וחיובים צפויים. תנועות ללא שם מופיעות תחת ללא שיוך.");return;
        }
        if(choice==8){
            List<Store.Account> cards=new ArrayList<>();List<String> names=new ArrayList<>();for(Store.Account account:paymentAccounts())if(account.type.equals("credit")||account.type.equals("fixed")){cards.add(account);names.add(account.name);}
            if(cards.isEmpty()){hint(host,"כדי להציג ניצול מסגרת, יש להגדיר כרטיס אשראי.");host.addView(button("הגדרת כרטיסי אשראי",false,()->{tab=4;render();}));return;}
            Spinner picker=spinner(host,"כרטיס להצגה",names);picker.setTag("utilization-account");long saved=prefs.getLong("chartAccount",cards.get(0).id);for(int n=0;n<cards.size();n++)if(cards.get(n).id==saved)picker.setSelection(n);LinearLayout content=column();host.addView(content);
            Runnable update=()->{Store.Account card=cards.get(picker.getSelectedItemPosition());prefs.edit().putLong("chartAccount",card.id).apply();content.removeAllViews();LocalDate asOf=store.reporting().cutoff(month,LocalDate.now()).isBefore(store.reporting().start(month))?store.reporting().end(month):store.reporting().cutoff(month,LocalDate.now());long used=store.creditUsage(card,asOf);hint(content,"אומדן ניצול ל־"+asOf.format(displayDate));content.addView(bold("ניצול משוער בפועל: "+money(used),20,used>card.limit&&card.limit>0?RED:INK));
                if(card.limit>0){Map<String,Long> values=new LinkedHashMap<>();values.put("ניצול בתוך המסגרת",Math.min(used,card.limit));values.put("מסגרת פנויה",Math.max(0,card.limit-used));ChartView chart=new ChartView(this,values,true,null);chart.centerLabel="מסגרת הכרטיס · ₪";chart.setTag("home-chart");content.addView(chart,new LinearLayout.LayoutParams(-1,dp(260)));legend(content,values,null);if(used>card.limit)content.addView(bold("חריגה משוערת: "+money(used-card.limit),17,RED));}else hint(content,"טרם הוגדרה מסגרת לכרטיס זה.");
                hint(content,"האומדן מחושב מהנתונים שהוזנו ואינו מאומת מול חברת האשראי. חוב ישן באשראי רגיל מתעדכן ידנית כשהוא נפרע.");TextView edit=button("עדכון מסגרת וניצול קודם",false,()->creditBalanceDialog(card));edit.setTag("chart-credit-edit");content.addView(edit);};
            update.run();picker.setOnItemSelectedListener(selection(update));return;
        }
        if(choice==9){
            Store.SeriesData data=store.incomeVsExpenses(month);hint(host,"הכנסות והוצאות שבוצעו בלבד, לפי תאריך העסקה · שישה חודשים · סכומים בשקלים");int color=0;for(String label:data.series.keySet())host.addView(bold("● "+label,14,ChartView.colors(this)[color++]));Map<String,YearMonth> periods=new HashMap<>();for(int n=0;n<6;n++)periods.put(data.labels.get(n),month.minusMonths(5-n));SeriesChartView chart=new SeriesChartView(this,data,false,key->openAnalyticsMonth(periods.get(key),""));chart.setTag("home-chart");host.addView(chart,new LinearLayout.LayoutParams(-1,dp(260)));
            long[] income=data.series.get("הכנסות"),expenses=data.series.get("הוצאות");for(int n=0;n<data.labels.size();n++){String label=data.labels.get(n);long net=income[n]-expenses[n];TextView detail=text(label+" · הכנסות: "+money(income[n])+" · הוצאות: "+money(expenses[n])+"\nהכנסות פחות הוצאות: "+money(net),14,net<0?RED:INK);detail.setPadding(0,dp(8),0,dp(8));detail.setOnClickListener(v->openAnalyticsMonth(periods.get(label),""));detail.setFocusable(true);host.addView(detail);}if(!incomes()){hint(host,"להזנת הכנסות יש להפעיל מעקב הכנסות בהגדרות. הכנסות שכבר הוזנו נכללות בגרף.");host.addView(button("הגדרות מעקב הכנסות",false,this::settings));}return;
        }
    }
    private void showPayment(String name){for(Store.Account account:store.accounts())if(account.name.equals(name)){accountFilter=account.id;merchantFilter="";categoryFilter="";query="";advancedFilter=new MovementFilter();tab=1;chargesMode=false;render();return;}}
    private void recurring(){
        title(body,"הוראות קבע");LinearLayout intro=card(body);hint(intro,"החיובים נוצרים אוטומטית כצפויים לכל חודש שנפתח. פותחים את התנועה כדי לאשר שהחיוב בוצע או לעדכן את הסכום בפועל. חיובים באשראי משויכים לכרטיס שבחרת.");
        List<Store.Rule> rules=store.rules();long total=0;int activeCount=0;
        for(Store.Rule r:rules)if(r.active==1){List<LocalDate> due=store.reporting().ruleDates(r,month);if(!due.isEmpty()){total=Math.addExact(total,Math.multiplyExact(store.expectedRuleAmount(r),due.size()));activeCount++;}}
        LinearLayout summary=card(body);summary.setBackground(shape(CONTAINER,24));hint(summary,"סה״כ הוראות קבע לתקופה הנבחרת");TextView sum=bold(money(total),32,palette.onContainer);sum.setTag("recurring-total");summary.addView(sum);TextView count=text(activeCount+" הוראות קבע פעילות בתקופה זו",14,palette.onContainer);count.setTag("recurring-count");summary.addView(count);hint(summary,"לפי הסכומים שהוגדרו · ללא הוראות מושהות, שטרם התחילו או שהסתיימו");
        if(rules.isEmpty())hint(intro,"אפשר להוסיף שכירות, תקשורת, מנויים וכל חיוב חודשי קבוע.");
        for(Store.Rule r:rules){LinearLayout block=card(body);block.addView(bold(r.name,18,INK));block.addView(bold(money(store.expectedRuleAmount(r))+(r.interval==12?" לשנה":r.interval==3?" לרבעון":" לחודש"),23,PRIMARY));hint(block,r.category+" · "+store.account(r.account).name);if(!"ILS".equals(r.currency))hint(block,Finance.decimal(r.originalAmount)+" "+r.currency+" · שער שמור "+r.fxRate+" · עמלת הכרטיס "+store.account(r.account).fxFee+"%");hint(block,"החל מ־"+LocalDate.parse(r.start).format(displayDate)+(r.end.trim().isEmpty()?"":" עד "+LocalDate.parse(r.end).format(displayDate)));hint(block,r.active==1?"פעילה":"מושהית");hint(block,"עלות שנתית בקצב הנוכחי: "+money(store.expectedRuleAmount(r)*12/r.interval));LocalDate next=Analytics.next(r,LocalDate.now());if(r.active==1&&next!=null)hint(block,"החיוב הבא: "+next.format(displayDate));List<Store.Tx> paid=new ArrayList<>();for(Store.Tx t:store.transactions())if(t.rule==r.id&&t.status==1)paid.add(t);if(paid.size()>1&&paid.get(0).amount!=paid.get(1).amount)hint(block,"שינוי בין שני חיובים שאושרו: "+money(paid.get(1).amount)+" ← "+money(paid.get(0).amount));TextView edit=button("עריכת הוראת הקבע",false,()->ruleDialog(r));edit.setTag("edit-rule-"+r.id);block.addView(edit);block.addView(button(r.active==1?"השהיית הוראת הקבע":"חידוש מהחודש הנבחר",false,()->{
                if(r.active==1)trackDialog(new AlertDialog.Builder(this).setTitle("השהיית "+r.name).setMessage("תנועות צפויות מהיום והלאה יוסרו. חיובים שבוצעו יישמרו.").setNegativeButton("ביטול",null).setPositiveButton("השהיה",(d,w)->{try{edit("השהיית הוראת קבע",()->store.setRuleActive(r,false));render();}catch(Exception e){toast(e.getMessage());}}).create()).show();
                else {LocalDate resume=Finance.day(YearMonth.from(store.reporting().start(month)),LocalDate.parse(r.start).getDayOfMonth());if(resume.isBefore(store.reporting().start(month)))resume=Finance.day(YearMonth.from(resume).plusMonths(1),LocalDate.parse(r.start).getDayOfMonth());r.start=resume.toString();if(!r.end.trim().isEmpty()&&LocalDate.parse(r.end).isBefore(LocalDate.parse(r.start))){toast("מועד הסיום עבר. יש ליצור הוראה חדשה.");return;}edit("הפעלת הוראת קבע",()->store.setRuleActive(r,true));render();}
            }));}
    }
    private String accountType(Store.Account a){if(a.type.equals("cash"))return "מזומן";if(a.type.equals("transfer"))return "העברה בנקאית";for(int i=0;i<typeIds.length;i++)if(typeIds[i].equals(a.type))return typeNames[i];return a.type;}
    private void accounts(){
        title(body,"חשבונות וכרטיסים");hint(card(body),"בכרטיס אשראי מוצגים מצב המסגרת להיום והחיוב הבא. לחיצה על פרטים תפתח את המחזור, יתרת הפתיחה ופירוט החישוב.");
        for(Store.Account a:paymentAccounts()){
            LinearLayout block=card(body);title(block,a.name);hint(block,accountType(a));if(!a.type.equals("cash")&&!a.type.equals("transfer"))hint(block,"עמלת מט״ח: "+a.fxFee+"%");
            if(a.type.equals("credit")||a.type.equals("fixed")){
                LocalDate now=LocalDate.now();long used=store.creditUsage(a,now);block.addView(bold("מסגרת כוללת: "+money(a.limit),20,INK));block.addView(bold((used<0?"יתרת זכות בכרטיס: ":"מסגרת תפוסה: ")+money(Math.abs(used)),24,PRIMARY));block.addView(bold("מסגרת פנויה: "+money(a.limit-used),24,used>a.limit?RED:PRIMARY));hint(block,"אומדן להיום · "+now.format(displayDate));
                LocalDate next=Finance.day(YearMonth.from(now),a.charge);if(!next.isAfter(now))next=Finance.day(YearMonth.from(now).plusMonths(1),a.charge);store.ensureMonth(YearMonth.from(next));long dueAmount=0;for(Store.Charge c:store.charges(YearMonth.from(next)))if(c.account==a.id&&c.date.equals(next))dueAmount+=c.amount;
                block.addView(bold("החיוב הבא: "+money(dueAmount),22,PRIMARY));hint(block,"ב־"+next.format(displayDate)+" · כולל חיובים צפויים");long release=Math.max(0,store.creditUsage(a,next.minusDays(1))-store.creditUsage(a,next));hint(block,"במועד זה צפויים להתפנות "+money(release)+" מהמסגרת לפי עסקאות שבוצעו בלבד");
                LinearLayout details=column();details.setVisibility(View.GONE);block.addView(button("פרטים ופירוט החישוב",false,()->details.setVisibility(details.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE)));block.addView(details);
                hint(details,"יום סגירה: "+a.closing+" · יום חיוב: "+a.charge);hint(details,"מחזור "+monthNames[month.getMonthValue()-1]+": "+Finance.creditCycleStart(month,a.closing).format(displayDate)+" עד "+Finance.creditCycleEnd(month,a.closing).format(displayDate));TextView cycle=button("עסקאות במחזור האשראי",false,()->creditCycleDialog(a));cycle.setTag("credit-cycle-"+a.id);details.addView(cycle);
                hint(details,"חוב התחלתי מעסקאות שלא הוזנו: "+money(a.opening)+" · נכון ל־"+LocalDate.parse(a.start).format(displayDate));hint(details,a.type.equals("fixed")?"חיוב חודשי קבוע: "+money(a.payment):"פירעון חודשי מתוך החוב ההתחלתי: "+money(a.openingPayment));hint(details,"החישוב מבוסס על הנתונים שהזנת ואינו אישור פירעון מהבנק. ריבית ועמלות נכללות רק אם הוזנו.");details.addView(button("עדכון מסגרת וניצול קודם",false,()->creditBalanceDialog(a)));
                if(a.type.equals("fixed")&&a.billingHistory.equals("[]")){TextView repair=button("תיקון מועד המעבר לחיוב קבוע",false,()->repairFixedStart(a));repair.setTag("repair-fixed-"+a.id);details.addView(repair);}
            }
            long actual=0;for(Store.Tx t:store.month(month))if(t.account==a.id&&t.status==1&&Store.isSpending(t))actual+=Store.netExpense(t);hint(block,"הוצאות שבוצעו בתקופה הנבחרת: "+money(actual));block.addView(button("הצגת תנועות",false,()->{accountFilter=a.id;categoryFilter="";merchantFilter="";query="";advancedFilter=new MovementFilter();tab=1;chargesMode=false;render();}));
            if(!a.type.equals("cash")&&!a.type.equals("transfer")){TextView edit=button("עריכת פרטים",false,()->accountDialog(a));edit.setTag("edit-account-"+a.id);block.addView(edit);}
            if(!a.type.equals("transfer"))block.addView(button("שינוי שם",false,()->{LinearLayout f=form();EditText name=field(f,"שם",a.name,false);dialog("שינוי שם",f,()->{a.name=required(name,"שם");edit("עדכון חשבון / כרטיס",()->store.save(a));render();});}));
        }
    }
    private void creditCycleDialog(Store.Account a){
        store.ensureMonth(YearMonth.from(Finance.creditCycleEnd(month,a.closing)));
        LinearLayout f=form();hint(f,"מ־"+Finance.creditCycleStart(month,a.closing).format(displayDate)+" עד "+Finance.creditCycleEnd(month,a.closing).format(displayDate));
        hint(f,"ירידת הכסף: "+store.creditCyclePaymentDate(a,month).format(displayDate));
        if(a.type.equals("fixed"))hint(f,"החיוב החודשי הקבוע שהוגדר: "+money(a.payment)+". הפירעון מוגבל לחוב שהגיע למועד חיובו; יתרת התשלומים נשארת במסגרת.");
        List<Store.Tx> txs=store.creditCycleTransactions(a,month);long actual=0;for(Store.Tx t:txs)if(Store.isSpending(t)&&t.status==1)actual=Math.addExact(actual,Store.netExpense(t));
        TextView total=bold("סך רכישות שבוצעו במחזור: "+money(actual),19,PRIMARY);total.setTag("credit-cycle-total");f.addView(total);
        hint(f,"הרשימה לפי תאריך הרכישה. בעסקת תשלומים מוצג סכום הרכישה המלא; זה אינו סכום ירידת הכסף או יתרת החוב.");
        if(txs.isEmpty())hint(f,"אין עסקאות במחזור זה");
        for(Store.Tx t:txs){TextView row=text(t.date+" · "+(t.merchant.trim().isEmpty()?t.category:t.merchant)+" · "+money(t.amount)+(t.count>1?" · "+t.count+" תשלומים":"")+(t.status==0?" · צפוי":""),15,INK);row.setPadding(0,dp(8),0,dp(8));f.addView(row);}
        ScrollView scroll=new ScrollView(this);scroll.addView(f);AlertDialog d=trackDialog(new AlertDialog.Builder(this).setTitle(a.name+" · מחזור "+monthNames[month.getMonthValue()-1]+" "+month.getYear()).setView(scroll).setPositiveButton("סגירה",null).create());d.show();styleDialog(d);
    }

    private LinearLayout form(){LinearLayout f=column();f.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);f.setPadding(dp(22),dp(10),dp(22),dp(15));return f;}
    private void configureText(EditText e){
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setImeHintLocales(android.os.LocaleList.forLanguageTags("he-IL,en-US"));
        e.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
        e.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);e.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);e.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);e.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
    }
    private AutoCompleteTextView historyField(LinearLayout f,String label,String initial,List<String> options){
        return editableDropdown(f,label+" · בחירה מהרשימה או הקלדה חופשית",label,initial,options,"history-dropdown-"+label);
    }
    private TextWatcher changed(Runnable r){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int before,int count){r.run();}public void afterTextChanged(Editable e){}};}
    private final class CurrencyEntry {
        final EditText amount,rate;final Spinner currency;final LinearLayout conversion;final TextView status,preview;
        final ExchangeRates rates=new ExchangeRates(MainActivity.this);String rateDate="",source="",feePercent="0";boolean filling=false;long request=0;
        final String[] codes={"USD","EUR","ILS"};
        CurrencyEntry(LinearLayout form,Store.Tx existing){
            amount=field(form,"סכום",existing==null?"":Finance.decimal(existing.originalAmount),true);amount.setTag("סכום");
            amount.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);amount.setTextDirection(View.TEXT_DIRECTION_LTR);amount.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);amount.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
            TextView calculation=text("",14,PRIMARY);calculation.setTag("amount-calculation");calculation.setVisibility(View.GONE);form.addView(calculation);hint(form,"אפשר לחבר סכומים, למשל: =67+22+33+55");
            amount.addTextChangedListener(changed(()->{String value=amount.getText().toString();boolean expression=value.contains("+")||value.trim().startsWith("=");calculation.setVisibility(expression?View.VISIBLE:View.GONE);if(expression)try{calculation.setText("סה״כ: "+Finance.decimal(Finance.sumMoney(value)));}catch(IllegalArgumentException error){calculation.setText("יש להשלים את החיבור עם סכומים תקינים");}}));
            amount.setOnFocusChangeListener((v,focused)->{amount.setBackground(outlineShape(SURFACE,12,focused?PRIMARY:OUTLINE));if(!focused){String value=amount.getText().toString();if(value.contains("+")||value.trim().startsWith("="))try{amount.setText(Finance.decimal(Finance.sumMoney(value)));}catch(IllegalArgumentException ignored){}}});
            currency=spinner(form,"מטבע",Arrays.asList("דולר · USD $","יורו · EUR €","שקל · ILS ₪"));currency.setTag("מטבע");
            conversion=column();form.addView(conversion);rate=field(conversion,"שער המרה · שקלים ליחידת מטבע",existing==null?"":existing.fxRate,true);rate.setTag("שער המרה");
            status=text("",12,MUTED);conversion.addView(status);preview=bold("",16,PRIMARY);conversion.addView(preview);
            conversion.addView(button("עדכון שער מבנק ישראל",false,()->refresh()));
            hint(conversion,"השער היציג האחרון שפורסם. אינו בהכרח השער ביום העסקה או שער החיוב בכרטיס. אפשר לערוך ידנית.");
            if(existing!=null){rateDate=existing.fxDate;source=existing.fxSource;}
            rate.addTextChangedListener(changed(()->{if(!filling){request++;rateDate="";source="manual";status.setText("שער שהוזן ידנית");}preview();}));
            amount.addTextChangedListener(changed(this::preview));
            int initial=2;if(existing!=null)for(int i=0;i<codes.length;i++)if(codes[i].equals(existing.currency))initial=i;currency.setSelection(initial);
            final boolean[] first={true};currency.setOnItemSelectedListener(selection(()->{
                request++;boolean foreign=!code().equals("ILS");conversion.setVisibility(foreign?View.VISIBLE:View.GONE);
                if(foreign){if(first[0]&&existing!=null&&!existing.currency.equals("ILS")){status.setText(source.equals("manual")?"שער ידני שנשמר בעסקה":"שער שנשמר בעסקה · "+rateDate);}
                else {ExchangeRates.Rate cached=rates.cached(code());setRate(cached,true);refresh();}}
                first[0]=false;preview();
            }));conversion.setVisibility(codes[initial].equals("ILS")?View.GONE:View.VISIBLE);
        }
        String code(){return codes[currency.getSelectedItemPosition()];}
        void setRate(ExchangeRates.Rate r,boolean cached){
            filling=true;rate.setText(r==null?"":r.value);filling=false;rateDate=r==null?"":r.date;source=r==null?"":"Bank of Israel";
            status.setText(r==null?"אין שער שמור. אפשר לעדכן או להזין ידנית.":(cached?"שער שמור · ":"בנק ישראל · ")+r.date);preview();
        }
        void refresh(){
            if(code().equals("ILS"))return;final String selected=code(),previous=rate.getText().toString();final long token=++request;status.setText("מעדכן שער…");
            new Thread(()->{try{ExchangeRates.Rate result=rates.fetch().get(selected);runOnUiThread(()->{if(!isDestroyed()&&token==request&&selected.equals(code())&&rate.getText().toString().equals(previous))setRate(result,false);});}
                catch(Exception e){runOnUiThread(()->{if(!isDestroyed()&&token==request&&selected.equals(code())&&rate.getText().toString().equals(previous))status.setText(rate.getText().toString().isEmpty()?"העדכון נכשל. נסה שוב או הזן שער ידנית.":"העדכון נכשל. נשמר השער המוצג · "+(rateDate.isEmpty()?"ידני":rateDate));});}},"PocketFlow-rates").start();
        }
        void selectAccount(Store.Account account, Store.Tx existing, boolean expense) {
            feePercent = expense ? account.fxFee : "0";
            if (expense && existing != null && existing.id > 0 && existing.account == account.id
                    && "expense".equals(existing.kind) && !"ILS".equals(existing.currency)) feePercent = existing.fxFee;
            preview();
        }
        void preview(){
            if(code().equals("ILS"))return;
            try {
                long original = Finance.sumMoney(amount.getText().toString());
                long converted = Finance.convert(original, rate.getText().toString());
                long total = Finance.convert(original, rate.getText().toString(), feePercent);
                preview.setText("סכום ההמרה: " + money(converted)
                        + "\nעמלת מט״ח " + feePercent + "%: " + money(total - converted)
                        + "\nסה״כ לחיוב: " + money(total));
            } catch(Exception e) { preview.setText("יש להזין סכום ושער המרה תקינים"); }
        }
        void apply(Store.Tx t){
            t.currency=code();t.originalAmount=Finance.sumMoney(amount.getText().toString());
            t.fxRate=code().equals("ILS")?"1":Finance.rate(rate.getText().toString()).toPlainString();
            t.fxDate=code().equals("ILS")?"":rateDate;t.fxSource=code().equals("ILS")?"":source;
            t.fxFee=code().equals("ILS")?"0":Finance.feePercent(feePercent);
            t.amount=Finance.convert(t.originalAmount,t.fxRate,t.fxFee);
        }
    }
    private EditText field(LinearLayout f,String label,String value,boolean number){
        f.addView(text(label,13,MUTED));EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(17);if(number){e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);e.setTextDirection(View.TEXT_DIRECTION_LTR);}if(!number)configureText(e);e.setTag(label);e.setText(value);styleInput(e);f.addView(e,new LinearLayout.LayoutParams(-1,dp(56)));return e;
    }
    private List<String> alphabetic(Collection<String> values){
        List<String> sorted=new ArrayList<>(values);sorted.sort(java.text.Collator.getInstance(Locale.forLanguageTag("he-IL")));return sorted;
    }
    private List<Store.Account> paymentAccounts(){
        List<Store.Account> accounts=store.accounts();java.text.Collator order=java.text.Collator.getInstance(Locale.forLanguageTag("he-IL"));accounts.sort((a,b)->order.compare(a.name,b.name));return accounts;
    }
    private List<String> categoryOptions(){
        Set<String> all=new LinkedHashSet<>(Arrays.asList(categories));
        for(Store.Tx t:store.transactions())if(!t.category.trim().isEmpty())all.add(t.category);
        for(Store.Rule r:store.rules())if(!r.category.trim().isEmpty())all.add(r.category);
        return alphabetic(all);
    }
    private ArrayAdapter<String> dropdownAdapter(List<String> options){
        return new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line,options){
            @Override public View getView(int position,View convert,ViewGroup parent){
                TextView value=text(getItem(position),16,INK);value.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);value.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);value.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);value.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);value.setPadding(dp(14),dp(8),dp(14),dp(8));value.setMinHeight(dp(48));return value;
            }
        };
    }
    private AutoCompleteTextView categoryField(LinearLayout f,String initial){
        return editableDropdown(f,"קטגוריה","קטגוריה",initial,categoryOptions(),"category-dropdown");
    }
    private AutoCompleteTextView editableDropdown(LinearLayout f,String heading,String label,String initial,List<String> savedOptions,String arrowTag){
        f.addView(text(heading,13,MUTED));
        List<String> values=new ArrayList<>(savedOptions);if(!initial.isEmpty()&&!values.contains(initial))values.add(initial);List<String> options=alphabetic(values);
        LinearLayout box=row();box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);box.setId(View.generateViewId());box.setBackground(outlineShape(SURFACE,12,OUTLINE));
        AutoCompleteTextView input=new AutoCompleteTextView(this);configureText(input);input.setSingleLine(true);input.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);input.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);input.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);input.setTextSize(17);input.setTag(label);input.setThreshold(1);input.setTextColor(INK);input.setHintTextColor(MUTED);input.setHint("בחירה או הקלדה");input.setBackgroundColor(Color.TRANSPARENT);input.setPadding(dp(14),dp(8),dp(14),dp(8));input.setAdapter(dropdownAdapter(options));input.setText(initial,false);
        input.setDropDownAnchor(box.getId());input.setDropDownBackgroundDrawable(shape(SURFACE,12));input.setOnFocusChangeListener((v,focused)->box.setBackground(outlineShape(SURFACE,12,focused?PRIMARY:OUTLINE)));
        box.addView(input,new LinearLayout.LayoutParams(0,dp(56),1));
        TextView arrow=text("▾",20,MUTED);arrow.setTag(arrowTag);arrow.setGravity(Gravity.CENTER);arrow.setContentDescription("פתיחת רשימת "+label);arrow.setFocusable(true);
        arrow.setOnClickListener(v->{if(input.isPopupShowing()){input.dismissDropDown();return;}input.requestFocus();if(options.isEmpty()){toast("אין עדיין ערכים שמורים. אפשר להקליד ערך חדש");return;}input.setAdapter(dropdownAdapter(options));if(box.getWidth()>0)input.setDropDownWidth(box.getWidth());input.showDropDown();});
        box.addView(arrow,new LinearLayout.LayoutParams(dp(48),dp(56)));f.addView(box,new LinearLayout.LayoutParams(-1,-2));return input;
    }
    private Spinner spinner(LinearLayout f,String label,List<String> items){
        f.addView(text(label,13,MUTED));Spinner s=new Spinner(this);s.setTag(label);ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,items){
            private TextView item(int position){TextView value=text(getItem(position),16,INK);value.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);value.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);value.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);value.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);value.setPadding(dp(14),dp(8),dp(14),dp(8));value.setMaxLines(1);value.setEllipsize(android.text.TextUtils.TruncateAt.END);value.setMinHeight(dp(48));value.setLayoutParams(new ViewGroup.LayoutParams(-1,-2));return value;}
            @Override public View getView(int position,View convert,ViewGroup parent){return item(position);}
            @Override public View getDropDownView(int position,View convert,ViewGroup parent){return item(position);}
        };s.setAdapter(adapter);s.setPopupBackgroundDrawable(shape(SURFACE,12));
        LinearLayout box=row();box.setBackground(outlineShape(SURFACE,12,OUTLINE));box.addView(s,new LinearLayout.LayoutParams(-1,dp(56)));f.addView(box,new LinearLayout.LayoutParams(-1,-2));return s;
    }
    private CheckBox check(LinearLayout f,String label,boolean checked){CheckBox c=new CheckBox(this);c.setText(label);c.setTextColor(INK);c.setChecked(checked);f.addView(c);return c;}
    private TextView dateField(LinearLayout f,String label,String initial){f.addView(text(label,13,MUTED));TextView b=button(initial,false,()->{});b.setOnClickListener(v->{LocalDate date=LocalDate.parse(b.getText());trackDialog(new DatePickerDialog(this,(picker,y,m,d)->b.setText(LocalDate.of(y,m+1,d).toString()),date.getYear(),date.getMonthValue()-1,date.getDayOfMonth())).show();});f.addView(b);space(f,5);return b;}
    private interface Save {void run() throws Exception;}
    private AlertDialog dialog(String title,LinearLayout form,Save save){
        ScrollView scroll=new ScrollView(this);scroll.addView(form);TextView heading=bold(title,22,INK);heading.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);heading.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);heading.setPadding(dp(22),dp(22),dp(22),dp(12));AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setCustomTitle(heading).setView(scroll).setNegativeButton("ביטול",null).setPositiveButton("שמירה",null).create();
        d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{try{save.run();d.dismiss();}catch(Exception e){toast(e instanceof NumberFormatException||e instanceof java.time.DateTimeException?"יש לבדוק את הסכום או התאריך שהוזנו":e.getMessage()==null?"לא ניתן לשמור. יש לבדוק את השדות.":e.getMessage());}}));d.show();styleDialog(d);return d;
    }
    private String required(EditText e,String label){String s=e.getText().toString().trim();if(s.isEmpty())throw new IllegalArgumentException("יש להזין "+label);if(s.length()>500)throw new IllegalArgumentException("הטקסט ארוך מדי");return s;}
    private long amount(EditText e){return Finance.money(e.getText().toString());}
    private void transactionDialog(Store.Tx existing){
        if(existing!=null&&existing.kind.equals("refund")){refundDialog(store.transaction(existing.refundOf),existing);return;}
        Store.Tx t=existing==null?new Store.Tx():existing;List<Store.Account> ac=paymentAccounts();LinearLayout f=form();
        CurrencyEntry fx=new CurrencyEntry(f,existing);EditText sum=fx.amount;
        AutoCompleteTextView category=categoryField(f,existing==null?"":t.category);
        List<String> names=new ArrayList<>();for(Store.Account a:ac)names.add(a.name);Spinner account=spinner(f,"אמצעי תשלום",names);
        long selected=existing==null?prefs.getLong("lastAccount",ac.get(0).id):t.account;for(int i=0;i<ac.size();i++)if(ac.get(i).id==selected)account.setSelection(i);
        TextView date=dateField(f,"תאריך העסקה",existing==null?LocalDate.now().toString():t.date);date.setTag("transaction-date");
        Spinner kind=spinner(f,"סוג תנועה",Arrays.asList("הוצאה","הכנסה"));kind.setSelection(existing!=null&&t.kind.equals("income")?1:0);if(!incomes()&&(existing==null||!t.kind.equals("income"))){View box=(View)kind.getParent();box.setVisibility(View.GONE);f.getChildAt(f.indexOfChild(box)-1).setVisibility(View.GONE);}
        LinearLayout extra=column();extra.setVisibility(View.GONE);TextView expand=button("פרטים נוספים",false,()->extra.setVisibility(extra.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));expand.setTag("extra-details");f.addView(expand);f.addView(extra);
        AutoCompleteTextView merchant=historyField(extra,"בית עסק",existing==null?"":t.merchant,store.suggestions(false));
        merchant.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int after){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int before,int count){if(existing!=null||!category.getText().toString().trim().isEmpty())return;for(Store.Tx old:store.transactions())if(!old.merchant.trim().isEmpty()&&old.merchant.equalsIgnoreCase(s.toString())){category.setText(old.category,false);break;}}});
        AutoCompleteTextView tag=historyField(extra,"בן משפחה",existing==null?"":t.tag,store.suggestions(true));
        EditText note=field(extra,"תיאור",existing==null?"":t.note,false);note.setSingleLine(false);note.setMinLines(2);note.setMaxLines(5);note.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        note.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(95)));
        LinearLayout installments=column();extra.addView(installments);EditText count=field(installments,"מספר תשלומים",existing==null?"1":""+t.count,true);count.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        LinearLayout chequeDate=column();installments.addView(chequeDate);TextView firstDue=dateField(chequeDate,"תאריך התשלום הראשון",existing!=null&&!t.firstDue.isEmpty()?t.firstDue:date.getText().toString());
        hint(chequeDate,"המחאה אחת לכל חודש. תאריך העסקה נשמר בנפרד ממועדי התשלום.");
        Runnable paymentVisibility=()->{Store.Account selectedAccount=ac.get(account.getSelectedItemPosition());fx.selectAccount(selectedAccount,existing,kind.getSelectedItemPosition()==0);String type=selectedAccount.type;boolean enabled=kind.getSelectedItemPosition()==0&&Finance.supportsInstallments(type);installments.setVisibility(enabled?View.VISIBLE:View.GONE);chequeDate.setVisibility(type.equals("check")?View.VISIBLE:View.GONE);};
        account.setOnItemSelectedListener(selection(paymentVisibility));kind.setOnItemSelectedListener(selection(paymentVisibility));paymentVisibility.run();
        CheckBox actual=check(f,"העסקה בוצעה בפועל",existing==null?!LocalDate.parse(date.getText()).isAfter(LocalDate.now()):t.status==1);
        if(existing!=null&&t.rule>0)hint(f,"השינוי חל על החיוב הזה בלבד. שאר חודשי הוראת הקבע נשארים כפי שהוגדרו.");
        if(existing!=null&&t.id>0){TextView delete=button("מחיקת התנועה",false,()->{});f.addView(delete);delete.setTextColor(RED);delete.setTag("delete-tx");delete.setOnClickListener(v->trackDialog(new AlertDialog.Builder(this).setTitle("מחיקת התנועה?").setMessage("הפעולה תמחק את התנועה שנבחרה בלבד.").setNegativeButton("ביטול",null).setPositiveButton("מחיקה",(d,w)->{try{edit("מחיקת תנועה",()->store.remove(t));}catch(Exception e){toast(e.getMessage());return;}if(v.getTag() instanceof AlertDialog)((AlertDialog)v.getTag()).dismiss();render();}).create()).show());}
        if(existing!=null&&t.id>0){if(t.kind.equals("expense")&&t.status==1){TextView refund=button("זיכוי / החזר לרכישה",false,()->refundDialog(t,null));refund.setTag("add-refund");f.addView(refund);long refunded=store.refunded(t.id,0);if(refunded>0)hint(f,"זיכויים מקושרים: "+money(refunded)+" · הוצאה נטו: "+money(t.amount-refunded));}f.addView(button("שימוש חוזר בפרטים",false,()->transactionDialog(reusable(t))));f.addView(button("שמירה כקיצור אישי",false,()->templateDialog(t)));}
        AlertDialog d=dialog(existing==null||t.id==0?"תנועה חדשה":"עריכת תנועה",f,()->{
            Store.Tx saved=new Store.Tx();saved.id=t.id;saved.rule=t.rule;fx.selectAccount(ac.get(account.getSelectedItemPosition()),existing,kind.getSelectedItemPosition()==0);fx.apply(saved);saved.category=required(category,"קטגוריה");saved.account=ac.get(account.getSelectedItemPosition()).id;saved.date=date.getText().toString();saved.kind=kind.getSelectedItemPosition()==0?"expense":"income";saved.merchant=merchant.getText().toString().trim();saved.tag=tag.getText().toString().trim();saved.note=note.getText().toString().trim();saved.count=saved.kind.equals("expense")&&Finance.supportsInstallments(ac.get(account.getSelectedItemPosition()).type)?Integer.parseInt(count.getText().toString()):1;saved.firstDue=ac.get(account.getSelectedItemPosition()).type.equals("check")?firstDue.getText().toString():"";saved.status=actual.isChecked()?1:0;
            if(saved.kind.equals("income")&&(store.account(saved.account).type.equals("credit")||store.account(saved.account).type.equals("fixed")))throw new IllegalArgumentException("הכנסה יש לשייך לבנק, דיירקט או מזומן. לזיכוי של רכישה יש להשתמש בכפתור זיכוי / החזר ברכישה המקורית.");
            edit("שמירת תנועה",()->store.save(saved));prefs.edit().putLong("lastAccount",saved.account).apply();render();
        });
        if(existing!=null&&t.id>0)f.findViewWithTag("delete-tx").setTag(d);
    }

    private void edit(String label,Store.Change change){try{store.edit(label,change);}catch(Exception e){throw new IllegalArgumentException(e.getMessage(),e);}}
    private void refundDialog(Store.Tx original,Store.Tx existing){
      for(Dialog current:new ArrayList<>(openDialogs))if(current.isShowing())current.dismiss();final AlertDialog[] open={null};
      LinearLayout f=form();long available=original.amount-store.refunded(original.id,existing==null?0:existing.id);hint(f,"רכישה: "+(original.merchant.isEmpty()?original.category:original.merchant)+" · "+money(original.amount));hint(f,"יתרה שניתן לזכות: "+money(available)+" · הזיכוי מקטין הוצאות בחודש קבלתו, ולא נרשם כהכנסה");
      EditText sum=field(f,"סכום הזיכוי בשקלים",Finance.decimal(existing==null?available:existing.amount),true);TextView date=dateField(f,"תאריך קבלת / רישום הזיכוי",existing==null?LocalDate.now().toString():existing.date);date.setTag("refund-date");Store.Account a=store.account(original.account);LocalDate posting=LocalDate.parse(date.getText());TextView settlement=dateField(f,"מועד הזיכוי בלוח החיובים",existing!=null&&!existing.firstDue.isEmpty()?existing.firstDue:Finance.due(posting,a.type,a.closing,a.charge).toString());settlement.setTag("refund-settlement");if(existing==null)date.addTextChangedListener(changed(()->settlement.setText(Finance.due(LocalDate.parse(date.getText()),a.type,a.closing,a.charge).toString())));hint(f,"באשראי המסגרת מתעדכנת בתאריך רישום הזיכוי שבוצע. הזיכוי בלוח החיובים מוצג במועד שבחרת. החזר אינו מבטל את יתרת לוח התשלומים של הרכישה; הוא מתקזז מולה.");
      EditText note=field(f,"תיאור",existing==null?"":existing.note,false);CheckBox actual=check(f,"הזיכוי התקבל בפועל",existing==null||existing.status==1);
      if(existing!=null)f.addView(button("מחיקת הזיכוי",false,()->{try{edit("מחיקת זיכוי",()->store.remove(existing));if(open[0]!=null)open[0].dismiss();render();toast("הזיכוי נמחק. ניתן לבטל את הפעולה.");}catch(Exception e){toast(e.getMessage());}}));
      open[0]=dialog(existing==null?"זיכוי / החזר":"עריכת זיכוי",f,()->{Store.Tx t=new Store.Tx();t.id=existing==null?0:existing.id;t.refundOf=original.id;t.rule=0;t.kind="refund";t.amount=amount(sum);t.originalAmount=t.amount;t.count=1;t.account=original.account;t.category=original.category;t.merchant=original.merchant;t.tag=original.tag;t.note=note.getText().toString();t.date=date.getText().toString();t.firstDue=settlement.getText().toString();t.status=actual.isChecked()?1:0;edit("שמירת זיכוי",()->store.save(t));render();});
    }
    private TextView optionalDateField(LinearLayout f,String label,String value){f.addView(text(label,13,MUTED));LinearLayout row=row();TextView date=button(value,false,()->{});date.setHint("ללא הגבלה");date.setTag(label);date.setOnClickListener(v->{LocalDate initial=date.getText().length()==0?LocalDate.now():LocalDate.parse(date.getText());trackDialog(new DatePickerDialog(this,(picker,y,m,d)->date.setText(LocalDate.of(y,m+1,d).toString()),initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth())).show();});row.addView(date,new LinearLayout.LayoutParams(0,-2,1));TextView clear=textAction("×",()->date.setText(""));clear.setContentDescription("ניקוי "+label);row.addView(clear);f.addView(row);return date;}
    private void resetReportSelection(){month=store.reporting().label(LocalDate.now());advancedFilter=new MovementFilter();query="";categoryFilter="";merchantFilter="";accountFilter=0;navigationHistory.clear();lastScreen=null;}
    private void reportPeriodDialog(){LinearLayout f=form();hint(f,"בחר יום התחלה בין 1 ל־31. לדוגמה: יום 10 יוצר תקופה מ־10 באוקטובר עד 9 בנובמבר בשם אוקטובר. יום 1 מציג חודש רגיל. בחודשים קצרים יום שאינו קיים עובר ליום האחרון בחודש.");EditText day=field(f,"יום תחילת תקופת המעקב",String.valueOf(store.reporting().day),true);day.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);day.setTag("report-period-day");TextView preview=text("",15,PRIMARY);preview.setTag("report-period-preview");f.addView(preview);Runnable update=()->{try{ReportPeriod p=new ReportPeriod(Integer.parseInt(day.getText().toString()));YearMonth selected=p.label(LocalDate.now());preview.setText("התקופה הנוכחית: "+p.start(selected).format(displayDate)+" – "+p.end(selected).format(displayDate));}catch(Exception e){preview.setText("יש לבחור יום בין 1 ל־31");}};day.addTextChangedListener(changed(update));update.run();hint(f,"ההגדרה משנה את הסיכומים, הגרפים, רשימות התנועות, התקציבים והייצוא. תאריכי העסקאות, הוראות הקבע וחיובי האשראי נשארים כפי שהוגדרו. ניתן לבטל את השינוי בכפתור ביטול פעולה אחרונה.");dialog("תקופת המעקב",f,()->{int start=Integer.parseInt(day.getText().toString());new ReportPeriod(start);JSONObject data=Tools.read(this).put("periodStart",start);edit("שינוי תקופת המעקב",()->Tools.write(this,data,true));resetReportSelection();render();toast("תקופת המעקב עודכנה");});}
    private void advancedSearchDialog(){LinearLayout f=form();hint(f,"אפשר לשלב טווח תאריכים, סכום, בן משפחה, סוג וסטטוס עם החיפוש, הקטגוריה ואמצעי התשלום. תאריכים ריקים משתמשים בתקופה הנבחרת.");TextView from=optionalDateField(f,"מתאריך",advancedFilter.from),to=optionalDateField(f,"עד תאריך",advancedFilter.to);EditText min=field(f,"סכום מינימום · לבחירה",advancedFilter.min<0?"":Finance.decimal(advancedFilter.min),true),max=field(f,"סכום מקסימום · לבחירה",advancedFilter.max<0?"":Finance.decimal(advancedFilter.max),true);AutoCompleteTextView family=historyField(f,"בן משפחה",advancedFilter.family,store.suggestions(true));Spinner kind=spinner(f,"סוג לסינון",Arrays.asList("הכל","הוצאות","הכנסות","זיכויים"));String[] kinds={"","expense","income","refund"};kind.setSelection(Arrays.asList(kinds).indexOf(advancedFilter.kind));Spinner status=spinner(f,"סטטוס לסינון",Arrays.asList("הכל","צפוי","בוצע"));status.setSelection(advancedFilter.status+1);dialog("סינון מתקדם",f,()->{MovementFilter next=new MovementFilter();next.from=from.getText().toString().trim();next.to=to.getText().toString().trim();next.min=min.getText().toString().trim().isEmpty()?-1:amount(min);next.max=max.getText().toString().trim().isEmpty()?-1:amount(max);next.family=family.getText().toString().trim();next.kind=kinds[kind.getSelectedItemPosition()];next.status=status.getSelectedItemPosition()-1;next.validate();advancedFilter=next;render();});}
    private JSONObject searchData(){try{return advancedFilter.json().put("query",query).put("category",categoryFilter).put("merchant",merchantFilter).put("account",accountFilter);}catch(Exception e){throw new IllegalArgumentException(e);}}
    private void saveSearchDialog(){LinearLayout f=form();EditText name=field(f,"שם החיפוש","",false);dialog("שמירת חיפוש",f,()->{JSONObject data=Tools.read(this);org.json.JSONArray all=data.optJSONArray("searches");if(all==null)all=new org.json.JSONArray();if(all.length()>=30)throw new IllegalArgumentException("אפשר לשמור עד 30 חיפושים");all.put(searchData().put("name",required(name,"שם")));data.put("searches",all);edit("שמירת חיפוש",()->Tools.write(this,data,true));render();});}
    private void savedSearchDialog(){final AlertDialog[] searchMenu={null};LinearLayout f=form();JSONObject data=Tools.read(this);org.json.JSONArray all=data.optJSONArray("searches");if(all==null||all.length()==0){hint(f,"אין חיפושים שמורים. בחר סינונים ולחץ על שמירת החיפוש הנוכחי.");}else for(int n=0;n<all.length();n++){final int index=n;JSONObject item=all.optJSONObject(n);if(item==null)continue;f.addView(button(item.optString("name"),false,()->{try{advancedFilter=MovementFilter.from(item);query=item.optString("query","");categoryFilter=item.optString("category","");merchantFilter=item.optString("merchant","");accountFilter=item.optLong("account",0);if(accountFilter>0)try{store.account(accountFilter);}catch(Exception e){accountFilter=0;}if(searchMenu[0]!=null)searchMenu[0].dismiss();render();}catch(Exception e){toast(e.getMessage());}}));f.addView(button("מחיקת החיפוש",false,()->{try{org.json.JSONArray updated=new org.json.JSONArray();for(int k=0;k<all.length();k++)if(k!=index)updated.put(all.get(k));JSONObject next=Tools.read(this).put("searches",updated);edit("מחיקת חיפוש",()->Tools.write(this,next,true));if(searchMenu[0]!=null)searchMenu[0].dismiss();render();}catch(Exception e){toast(e.getMessage());}}));}ScrollView scroll=new ScrollView(this);scroll.addView(f);searchMenu[0]=trackDialog(new AlertDialog.Builder(this).setTitle("חיפושים שמורים").setView(scroll).setPositiveButton("סגירה",null).create());searchMenu[0].show();}

    private void repairFixedStart(Store.Account a){
        LinearLayout f=form();hint(f,"השתמש כאן רק אם הכרטיס עבר מחיוב מלא לחיוב קבוע בגרסה קודמת, והמסגרת השתחררה מיד בטעות. עד לתאריך שתבחר יחול החיוב המלא, ורק במועדי חיוב שאחריו יחול הסכום הקבוע. סכומי העסקאות ויתרת הפתיחה לא יימחקו ולא ישונו.");
        TextView date=dateField(f,"מועד השינוי לחיוב קבוע",LocalDate.now().toString());date.setTag("fixed-change-date");EditText closing=field(f,"יום סגירת המחזור לפני המעבר",Integer.toString(a.closing),true);
        hint(f,"לדוגמה: אם הגדרת את הסכום ב־1 בנובמבר והחיוב ב־10 בחודש, בחר 1 בנובמבר. החיוב הקבוע הראשון יהיה ב־10 בנובמבר, והחיוב המלא יחול על המועדים הקודמים.");
        dialog("תיקון מועד המעבר לחיוב קבוע",f,()->{edit("תיקון כרטיס",()->store.correctLegacyFixedStart(a,LocalDate.parse(date.getText()),Integer.parseInt(closing.getText().toString())));render();});
    }
    private void creditBalanceDialog(Store.Account a){
        LinearLayout f=form();EditText limit=field(f,"מסגרת הכרטיס",Finance.decimal(a.limit),true);EditText opening=field(f,"ניצול קודם שאינו מתועד",Finance.decimal(a.opening),true);
        TextView start=dateField(f,a.type.equals("fixed")?"תאריך יתרת הפתיחה · ראשון בחודש":"תאריך יתרת הפתיחה",a.start);
        hint(f,"יש להזין רק חוב מעסקאות שלא הוזנו באפליקציה. תנועות מתועדות נספרות בנפרד. יתרת פתיחה אינה הוצאה חדשה.");
        LinearLayout prior=form();f.addView(prior);prior.setVisibility(a.type.equals("credit")?View.VISIBLE:View.GONE);EditText priorPayment=field(prior,"פירעון חודשי של הניצול הקודם",Finance.decimal(a.openingPayment),true);
        hint(f,a.type.equals("credit")?"הסכום יופחת מהניצול הקודם בכל מועד חיוב שלאחר תאריך היתרה, עד סילוקה. לפירעון מלא בחיוב הקרוב הזן את מלוא היתרה; 0 משאיר עדכון ידני. תשלומים ישנים עם סכומים משתנים יש לעדכן ידנית.":"שינוי יתרת הפתיחה מעדכן גם את תחזית החיוב הקבוע. היתרה היא מתחילת החודש; עסקאות מתועדות מאותו חודש מתווספות אליה.");
        dialog("מסגרת וניצול קודם · "+a.name,f,()->{long newLimit=amount(limit),newOpening=amount(opening);String date=start.getText().toString();if(a.type.equals("fixed")&&LocalDate.parse(date).getDayOfMonth()!=1)throw new IllegalArgumentException("בכרטיס בחיוב קבוע יש לבחור את היום הראשון בחודש");a.limit=newLimit;a.opening=newOpening;a.openingPayment=a.type.equals("credit")?amount(priorPayment):a.openingPayment;a.start=date;edit("עדכון חשבון / כרטיס",()->store.save(a));render();});
    }
    private void accountDialog(){accountDialog(null);}
    private void accountDialog(Store.Account existing){
        LinearLayout f=form();EditText name=field(f,"שם החשבון או הכרטיס",existing==null?"":existing.name,false);Spinner type=spinner(f,"סוג אמצעי התשלום",alphabetic(Arrays.asList(typeNames)));type.setSelection(alphabetic(Arrays.asList(typeNames)).indexOf(existing==null?typeNames[0]:accountType(existing)));
        if(existing!=null){boolean used=false;for(Store.Tx t:store.transactions())if(t.account==existing.id)used=true;for(Store.Rule r:store.rules())if(r.account==existing.id)used=true;type.setEnabled(!used);if(used)hint(f,"סוג אמצעי התשלום נשמר. בכרטיס אשראי ניתן לשנות את אופן החיוב בבורר למטה, גם כשקיימות תנועות.");hint(f,"שינוי אופן החיוב, הסכום או יום החיוב יחול ממועד חיוב שלאחר היום, ללא פירעון מיידי. שינוי יתרת הפתיחה או התאריך שלה מתקן את נתוני הבסיס ומחשב אותם מחדש.");}
        EditText foreignFee = field(f,"עמלת מט״ח באחוזים",existing==null?"0":existing.fxFee,true);
        hint(f,"תוספת לשער ההמרה בעסקאות מט״ח חדשות. לדוגמה, 3.2 עבור עמלה של 3.2%.");
        title(f,"פרטי הכרטיס");LinearLayout credit=column();f.addView(credit);title(credit,"איך הכרטיס מחויב?");Spinner billing=spinner(credit,"אופן חיוב האשראי",Arrays.asList("מלוא החיוב החודשי","סכום חודשי קבוע"));billing.setSelection(existing!=null&&existing.type.equals("fixed")?1:0);EditText closing=field(credit,"יום סגירת מחזור האשראי",existing==null?"31":""+existing.closing,true);EditText charge=field(credit,"יום החיוב בחודש",existing==null?"10":""+existing.charge,true);EditText limit=field(credit,"מסגרת הכרטיס · לבחירה",existing==null?"0":Finance.decimal(existing.limit),true);
        TextView cyclePreview=text("",14,INK);cyclePreview.setTag("credit-cycle-preview");credit.addView(cyclePreview);
        Runnable preview=()->{try{int close=Integer.parseInt(closing.getText().toString()),chargeDay=Integer.parseInt(charge.getText().toString());if(close<1||close>31||chargeDay<1||chargeDay>31)throw new IllegalArgumentException();LocalDate from=Finance.creditCycleStart(month,close),through=Finance.creditCycleEnd(month,close),due=Finance.due(through,"credit",close,chargeDay);cyclePreview.setText("מחזור "+monthNames[month.getMonthValue()-1]+": "+from.format(displayDate)+" עד "+through.format(displayDate)+"\nמועד ירידת הכסף: "+due.format(displayDate));}catch(Exception error){cyclePreview.setText("יש להזין יום סגירה ויום חיוב בין 1 ל־31");}};
        closing.addTextChangedListener(changed(preview));charge.addTextChangedListener(changed(preview));preview.run();
        hint(credit,"למחזור מ־10 בחודש ועד 9 בחודש הבא הזן 9 ביום סגירת המחזור. יום החיוב הוא היום שבו הכסף יורד בפועל. ההגדרה נפרדת לכל כרטיס, גם בחיוב חודשי קבוע.");
        LinearLayout initial=column();initial.setVisibility(View.GONE);TextView initialToggle=button("מצב התחלתי · חוב מלפני תחילת המעקב",false,()->initial.setVisibility(initial.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));credit.addView(initialToggle);credit.addView(initial);hint(initial,"החוב שהיה בכרטיס לפני שהתחלת להזין עסקאות. לדוגמה: אם היו תפוסים 4,000 ₪ מעסקאות שלא הוזנו, מזינים כאן 4,000. זו אינה הוצאה חדשה.");EditText opening=field(initial,"ניצול קודם שאינו מתועד",existing==null?"0":Finance.decimal(existing.opening),true);TextView start=dateField(initial,"תאריך יתרת הפתיחה",existing==null?LocalDate.now().withDayOfMonth(1).toString():existing.start);hint(initial,"רק חוב מעסקאות שלא הוזנו באפליקציה. לא ייספר כהוצאה חדשה. בחיוב קבוע יש לבחור את היום הראשון בחודש.");
        LinearLayout full=column();initial.addView(full);EditText openingPayment=field(full,"פירעון חודשי של הניצול הקודם",existing==null?"0":Finance.decimal(existing.openingPayment),true);hint(full,"לסילוק כל הניצול הקודם בחיוב הקרוב, הזן כאן את מלוא הסכום. אם הוא כולל תשלומים ישנים, הזן את הסכום החודשי שלהם. הפירעון מתחיל במועד החיוב הראשון שלאחר תאריך היתרה; 0 משאיר את היתרה לעדכון ידני. סכומים משתנים דורשים עדכון ידני.");
        hint(full,"ביום החיוב המסגרת תשתחרר בסכום העסקאות המיועדות לחיוב. בעסקת תשלומים ישתחרר רק התשלום של אותו חודש; יתרת התשלומים תמשיך לתפוס מסגרת.");
        LinearLayout fixed=column();credit.addView(fixed,Math.min(3,credit.getChildCount()));EditText payment=field(fixed,"סכום החיוב החודשי הקבוע",existing==null?"":Finance.decimal(existing.payment),true);hint(fixed,"הסכום שקבעת לתשלום בכל חודש. המסגרת מתעדכנת ביום החיוב בלבד; האומדן אינו כולל ריבית ועמלות שלא הוזנו.");
        Runnable visibility=()->{int p=Arrays.asList(typeNames).indexOf(type.getSelectedItem().toString());credit.setVisibility(p==2||p==3?View.VISIBLE:View.GONE);credit.getChildAt(2).setVisibility(p==2||p==3?View.VISIBLE:View.GONE);closing.setVisibility(p==2||p==3?View.VISIBLE:View.GONE);fixed.setVisibility(p==3?View.VISIBLE:View.GONE);full.setVisibility(p==2?View.VISIBLE:View.GONE);if(p==2||p==3){int mode=p==3?1:0;if(billing.getSelectedItemPosition()!=mode)billing.setSelection(mode);}};type.setOnItemSelectedListener(selection(visibility));billing.setOnItemSelectedListener(selection(()->{int p=Arrays.asList(typeNames).indexOf(type.getSelectedItem().toString());if(p==2||p==3){int target=alphabetic(Arrays.asList(typeNames)).indexOf(typeNames[billing.getSelectedItemPosition()==1?3:2]);if(type.getSelectedItemPosition()!=target)type.setSelection(target);visibility.run();}}));visibility.run();
        dialog(existing==null?"חשבון / כרטיס חדש":"עריכת חשבון / כרטיס",f,()->{
            Store.Account a=new Store.Account();a.id=existing==null?0:existing.id;a.name=required(name,"שם");a.fxFee=Finance.feePercent(foreignFee.getText().toString());for(Store.Account old:paymentAccounts())if(old.id!=a.id&&old.name.equalsIgnoreCase(a.name))throw new IllegalArgumentException("כבר קיים אמצעי תשלום בשם הזה");int p=Arrays.asList(typeNames).indexOf(type.getSelectedItem().toString());a.type=typeIds[p];a.cycleConfigured=p==2||p==3;a.closing=p==2||p==3?Integer.parseInt(closing.getText().toString()):31;a.charge=p==2||p==3?Integer.parseInt(charge.getText().toString()):1;
            if(a.closing<1||a.closing>31||a.charge<1||a.charge>31)throw new IllegalArgumentException("יום בחודש חייב להיות בין 1 ל־31");a.limit=p==2||p==3?amount(limit):0;a.payment=p==3?amount(payment):0;a.opening=p==2||p==3?amount(opening):0;a.openingPayment=p==2?amount(openingPayment):existing==null?0:existing.openingPayment;a.start=start.getText().toString();if(p==3&&LocalDate.parse(a.start).getDayOfMonth()!=1)throw new IllegalArgumentException("יתרת הפתיחה צריכה להיות ליום הראשון בחודש");if(p==3&&a.payment==0)throw new IllegalArgumentException("החיוב הקבוע חייב להיות גדול מאפס");edit("עדכון חשבון / כרטיס",()->store.save(a));render();
        });
    }
    private void ruleDialog(){ruleDialog(null);}
    private void ruleDialog(Store.Rule existing){
        List<Store.Account> ac=new ArrayList<>();List<String> names=new ArrayList<>();for(Store.Account a:paymentAccounts())if(!a.type.equals("cash")&&!a.type.equals("check")){ac.add(a);names.add(a.name);}if(ac.isEmpty()){toast("לפני הוראת קבע יש להוסיף חשבון בנק או כרטיס");tab=4;render();return;}
        LinearLayout f=form();AutoCompleteTextView name=historyField(f,"שם הוראת הקבע / בית העסק",existing==null?"":existing.name,store.suggestions(false));CurrencyEntry fx=new CurrencyEntry(f,ruleCurrency(existing));AutoCompleteTextView category=categoryField(f,existing==null?"חשבונות":existing.category);Spinner account=spinner(f,"חשבון בנק או כרטיס לחיוב",names);
        if(existing!=null)for(int n=0;n<ac.size();n++)if(ac.get(n).id==existing.account)account.setSelection(n);
        Runnable feePreview=()->fx.selectAccount(ac.get(account.getSelectedItemPosition()),null,true);
        account.setOnItemSelectedListener(selection(feePreview));feePreview.run();
        hint(f,"הסכום והשער נשמרים בהוראה. לחיובים חדשים מתווספת עמלת הכרטיס שהוגדרה בעת יצירת החיוב. אפשר לעדכן שער בהוראה או בחיוב בודד.");
        TextView start=dateField(f,"מועד החיוב הראשון",existing==null?LocalDate.now().toString():existing.start);EditText end=field(f,"תאריך סיום · YYYY-MM-DD · לבחירה",existing==null?"":existing.end,false);Spinner frequency=spinner(f,"תדירות החיוב",Arrays.asList("כל חודש","כל שלושה חודשים","כל שנה"));frequency.setSelection(existing==null?0:existing.interval==12?2:existing.interval==3?1:0);EditText reminder=field(f,"תזכורת לפני חידוש · ימים",existing==null?"7":""+existing.reminderDays,true);EditText renewal=field(f,"מועד חידוש מיוחד · YYYY-MM-DD · לבחירה",existing==null?"":existing.renewal,false);hint(f,"ללא מועד מיוחד, התזכורת לפי החיוב הבא. מועד מיוחד נשאר עד שתעדכן אותו. בחודש קצר החיוב עובר ליום האחרון בחודש.");
        if(existing!=null){hint(f,"השינויים יעדכנו את החיובים הצפויים שטרם אושרו, כולל חודשים שכבר נפתחו. חיובים שאושרו וחודשים שדולגו יישמרו כפי שנרשמו.");if(existing.active==0)hint(f,"הוראת הקבע מושהית. עריכת הפרטים לא תחדש אותה.");}
        dialog(existing==null?"הוראת קבע חדשה":"עריכת הוראת קבע",f,()->{
            Store.Rule r=new Store.Rule();
            r.id=existing==null?0:existing.id;
            r.name=required(name,"שם");
            fx.selectAccount(ac.get(account.getSelectedItemPosition()),null,true);
            Store.Tx converted=new Store.Tx();
            fx.apply(converted);
            r.amount=converted.amount;
            r.originalAmount=converted.originalAmount;
            r.currency=converted.currency;
            r.fxRate=converted.fxRate;
            r.fxFee=converted.fxFee;
            r.fxDate=converted.fxDate;
            r.fxSource=converted.fxSource;
            r.category=required(category,"קטגוריה");
            r.account=ac.get(account.getSelectedItemPosition()).id;
            r.start=start.getText().toString();
            r.end=end.getText().toString().trim();
            r.active=existing==null?1:existing.active;
            r.interval=new int[]{1,3,12}[frequency.getSelectedItemPosition()];
            r.reminderDays=Integer.parseInt(reminder.getText().toString());
            r.renewal=renewal.getText().toString().trim();
            edit("שמירת הוראת קבע",()->{
                if(existing==null){
                    store.save(r);
                    store.ensureReportPeriod(month);
                }else store.updateRule(r,month);
            });
            render();
        });
    }
    private Store.Tx ruleCurrency(Store.Rule rule) {
        if (rule==null) return null;
        Store.Tx entry=new Store.Tx();
        entry.amount=rule.amount;entry.originalAmount=rule.originalAmount;entry.currency=rule.currency;
        entry.fxRate=rule.fxRate;entry.fxFee=rule.fxFee;entry.fxDate=rule.fxDate;entry.fxSource=rule.fxSource;
        return entry;
    }

    private Analytics analytics(){return new Analytics(store,this);}
    private void insightAction(Analytics.Insight i){if(i.purchases){LinearLayout f=form();hint(f,i.detail);for(Store.Tx t:analytics().actual(month,analytics().cutoff(month,LocalDate.now())))if((i.category.isEmpty()||i.category.equals(t.category))&&(i.merchant.isEmpty()||i.merchant.equals(t.merchant)))f.addView(button((t.merchant.isEmpty()?t.category:t.merchant)+" · "+t.date+" · "+money(t.amount),false,()->transactionDialog(t)));ScrollView scroll=new ScrollView(this);scroll.addView(f);new AlertDialog.Builder(this).setTitle(i.title).setView(scroll).setPositiveButton("סגירה",null).show();return;}if(!i.category.isEmpty())showCategory(i.category);else if(!i.merchant.isEmpty())showMerchant(i.merchant);else if(i.tx>0){for(Store.Tx t:store.transactions())if(t.id==i.tx)transactionDialog(t);}else if(i.tx<0){for(Store.Rule r:store.rules())if(r.id==-i.tx)ruleDialog(r);}else{tab=3;render();}}
    private void insightsPage(){
        title(body,"תובנות מההוצאות שלך");hint(body,"הוצאות שבוצעו בלבד · השוואות לחודש חלקי נעשות לאותם ימים בחודש הקודם");
        List<Analytics.Insight> items=analytics().insights(month,LocalDate.now());if(items.isEmpty())hint(card(body),"עם עוד נתונים יופיעו כאן השוואות ותובנות. אין מספיק מידע לתקופה שנבחרה.");
        for(Analytics.Insight i:items){LinearLayout c=card(body);title(c,i.title);hint(c,i.detail);c.addView(button("פתיחת הנתונים",false,()->insightAction(i)));}
        LinearLayout reminders=card(body);title(reminders,"תזכורות ותקציבים");List<String> alerts=analytics().alerts(LocalDate.now());if(alerts.isEmpty())hint(reminders,"אין כרגע חידושים מתקרבים או תקציבים מעל 80%.");else for(String a:alerts)hint(reminders,a);
        LinearLayout quality=card(body);title(quality,"בדיקת הנתונים");List<Analytics.Issue> issues=analytics().quality(LocalDate.now());hint(quality,issues.size()+" פריטים לבדיקה · אין מחיקה או תיקון אוטומטי");for(int n=0;n<Math.min(50,issues.size());n++){Analytics.Issue issue=issues.get(n);title(quality,issue.title);hint(quality,issue.detail);quality.addView(button("פתיחת התנועה",false,()->transactionDialog(issue.tx)));quality.addView(button("בדקתי, להשאיר כך",false,()->{Tools.dismiss(this,issue.key);render();}));}
    }
    private static final class ForecastTotals {
        long charges;
        long income;
    }

    private ForecastTotals forecastTotals(YearMonth period) {
        store.ensureReportPeriod(period);
        ForecastTotals totals = new ForecastTotals();
        for (Store.Charge charge : store.periodCharges(period)) {
            if ("income".equals(charge.kind)) totals.income -= charge.amount;
            else totals.charges += charge.amount;
        }
        return totals;
    }

    private void forecastPage(){
        title(body,"מבט קדימה · שישה חודשים");hint(body,"חיובים ידועים והוראות קבע לפי מועדי ירידת הכסף. זו תחזית חיובים, לא יתרת בנק. פירעון כרטיס אינו הוצאה נוספת.");
        for(int n=0;n<6;n++){YearMonth m=month.plusMonths(n);ForecastTotals totals=forecastTotals(m);LinearLayout c=card(body);title(c,monthNames[m.getMonthValue()-1]+" "+m.getYear());hint(c,periodRange(m));c.addView(bold("חיובים מתועדים וצפויים: "+money(totals.charges),23,PRIMARY));if(totals.income>0)hint(c,"הכנסות מתועדות וצפויות בנפרד: "+money(totals.income));
            for(Store.Account a:store.accounts())if(a.type.equals("credit")||a.type.equals("fixed")){long usage=store.creditUsage(a,store.reporting().end(m));hint(c,a.name+" · ניצול בסוף החודש: "+money(usage)+(a.limit>0?" · פנוי: "+money(a.limit-usage):""));}
            hint(c,"מסגרות לפי עסקאות שבוצעו ונתוני פתיחה; אינן כוללות רכישות עתידיות שלא בוצעו.");c.addView(button("פירוט החיובים",false,()->{month=m;tab=1;chargesMode=true;render();}));
        }
        LinearLayout estimate=card(body);title(estimate,"אומדן נפרד להוצאות משתנות");estimate.addView(bold(money(analytics().variableAverage(month)),26,PRIMARY));hint(estimate,"ממוצע לשלושה חודשים קודמים: עסקאות שבוצעו, ללא הוראות קבע וללא עסקאות בתשלומים. אינו מתווסף אוטומטית לחיובים הידועים כדי למנוע ספירה כפולה.");
    }
    private void budgetsPage(){
        title(body,"התקציבים שלך");hint(body,"יעדים חודשיים גמישים · החישוב כולל הוצאות שבוצעו וצפויות, כמו בתמונת המצב");body.addView(button("הוספת תקציב",true,()->budgetDialog(null)));
        org.json.JSONObject budgets=Tools.budgets(this);List<String> keys=new ArrayList<>();budgets.keys().forEachRemaining(keys::add);Collections.sort(keys,java.text.Collator.getInstance(new Locale("he")));
        if(keys.isEmpty())hint(card(body),"אפשר להתחיל מתקציב כולל או מקטגוריה אחת בלבד.");for(String key:keys){long limit=budgets.optLong(key),spent=analytics().spend(month,key);LinearLayout c=card(body);title(c,key.isEmpty()?"תקציב כולל":key);c.addView(bold(money(spent)+" מתוך "+money(limit),22,spent>limit?RED:PRIMARY));ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgress(limit==0?0:(int)Math.min(100,spent*100d/limit));c.addView(progress,new LinearLayout.LayoutParams(-1,dp(10)));hint(c,(spent>limit?"חריגה: ":"נותרו: ")+money(Math.abs(limit-spent)));
            if(month.equals(store.reporting().label(LocalDate.now())))hint(c,"עוד "+java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(),store.reporting().end(month))+" ימים לסוף התקופה");c.addView(button("עריכת התקציב",false,()->budgetDialog(key)));if(!key.isEmpty())c.addView(button("פתיחת התנועות",false,()->showCategory(key)));c.addView(button("הסרת התקציב",false,()->{try{org.json.JSONObject d=Tools.read(this),b=Tools.budgets(this);b.remove(key);d.put("budgets",b);edit("עדכון העדפות",()->Tools.write(this,d,true));render();}catch(Exception e){toast(e.getMessage());}}));
        }
    }
    private void budgetDialog(String previous){
        LinearLayout f=form();List<String> options=new ArrayList<>(Arrays.asList("תקציב כולל","לפי קטגוריה"));Spinner type=spinner(f,"סוג תקציב",options);type.setSelection(previous!=null&&!previous.isEmpty()?1:0);AutoCompleteTextView category=categoryField(f,previous==null?"":previous);EditText limit=field(f,"תקציב חודשי",previous==null?"":Finance.decimal(Tools.budgets(this).optLong(previous)),true);
        dialog("תקציב חודשי",f,()->{String key=type.getSelectedItemPosition()==0?"":required(category,"קטגוריה");long value=amount(limit);if(value==0)throw new IllegalArgumentException("יש להזין תקציב גדול מאפס");org.json.JSONObject d=Tools.read(this),b=Tools.budgets(this);if(previous!=null)b.remove(previous);b.put(key,value);d.put("budgets",b);edit("עדכון העדפות",()->Tools.write(this,d,true));render();});
    }
    private Store.Tx reusable(Store.Tx t){Store.Tx copy=new Store.Tx();copy.amount=t.amount;copy.originalAmount=t.originalAmount;copy.currency=t.currency;copy.fxFee=t.fxFee;copy.fxRate=t.fxRate;copy.fxDate=t.fxDate;copy.fxSource=t.fxSource;copy.account=t.account;copy.category=t.category;copy.merchant=t.merchant;copy.tag=t.tag;copy.note=t.note;copy.kind=t.kind;copy.count=1;copy.status=1;copy.date=LocalDate.now().toString();return copy;}
    private void shortcutsPage(){
        title(body,"קיצורי הזנה");hint(body,"כל קיצור פותח טופס למילוי או שינוי. תנועה נשמרת רק בלחיצה על שמירה.");org.json.JSONArray templates=Tools.templates(this);
        for(int n=0;n<templates.length();n++){org.json.JSONObject item=templates.optJSONObject(n);if(item==null)continue;final int index=n;LinearLayout c=card(body);title(c,item.optString("name"));hint(c,item.optString("category")+" · "+item.optString("merchant"));c.addView(button("פתיחת תנועה מהקיצור",true,()->{Store.Tx t=new Store.Tx();t.amount=item.optLong("amount");t.originalAmount=t.amount;t.currency="ILS";t.fxRate="1";t.account=store.accounts().get(0).id;for(Store.Account a:store.accounts())if(a.name.equals(item.optString("account")))t.account=a.id;t.category=item.optString("category");t.merchant=item.optString("merchant");t.tag=item.optString("tag");t.note=item.optString("note");t.kind=item.optString("kind","expense");t.count=1;t.status=1;t.date=LocalDate.now().toString();transactionDialog(t);}));c.addView(button("הסרת הקיצור",false,()->{try{org.json.JSONArray all=Tools.templates(this);all.remove(index);org.json.JSONObject d=Tools.read(this);d.put("templates",all);edit("עדכון העדפות",()->Tools.write(this,d,true));render();}catch(Exception e){toast(e.getMessage());}}));}
        title(body,"שימוש חוזר בתנועה קודמת");List<Store.Tx> recent=store.transactions();for(int n=0;n<Math.min(12,recent.size());n++){Store.Tx t=recent.get(n);LinearLayout c=card(body);title(c,(t.merchant.isEmpty()?t.category:t.merchant)+" · "+money(t.amount));c.addView(button("שימוש חוזר בפרטים",false,()->transactionDialog(reusable(t))));c.addView(button("שמירה כקיצור אישי",false,()->templateDialog(t)));}
    }
    private void templateDialog(Store.Tx t){LinearLayout f=form();EditText name=field(f,"שם הקיצור",t.merchant.isEmpty()?t.category:t.merchant,false);dialog("קיצור אישי",f,()->{org.json.JSONObject d=Tools.read(this);org.json.JSONArray all=Tools.templates(this);all.put(new org.json.JSONObject().put("id",java.util.UUID.randomUUID().toString()).put("name",required(name,"שם")).put("category",t.category).put("merchant",t.merchant).put("tag",t.tag).put("note",t.note).put("account",store.account(t.account).name).put("amount",t.amount).put("kind",t.kind));d.put("templates",all);edit("עדכון העדפות",()->Tools.write(this,d,true));render();toast("הקיצור נשמר");});}
    private String sectionName(String key){switch(key){case "summary":return "סיכום החיובים";case "graph":return "הגרף הנבחר";case "charges":return "לוח החיובים";case "recent":return "תנועות אחרונות";case "export":return "ייצוא";default:return "תובנות ותזכורות";}}
    private void personalizeHome(){
        LinearLayout f=form(),list=column();f.addView(list);List<String> order=new ArrayList<>(Tools.cards(this));Set<String> enabled=new HashSet<>(order);for(String key:Arrays.asList("summary","insights","graph","charges","recent","export"))if(!order.contains(key))order.add(key);
        Runnable[] draw=new Runnable[1];draw[0]=()->{list.removeAllViews();for(int n=0;n<order.size();n++){String key=order.get(n);final int index=n;CheckBox cb=check(list,sectionName(key),enabled.contains(key));cb.setEnabled(!key.equals("summary"));cb.setOnCheckedChangeListener((b,on)->{if(on)enabled.add(key);else enabled.remove(key);});LinearLayout actions=row();actions.addView(button("↑",false,()->{if(index>0){Collections.swap(order,index,index-1);draw[0].run();}}));actions.addView(button("↓",false,()->{if(index<order.size()-1){Collections.swap(order,index,index+1);draw[0].run();}}));list.addView(actions);}};draw[0].run();
        dialog("התאמת תמונת המצב",f,()->{org.json.JSONArray cards=new org.json.JSONArray();for(String key:order)if(enabled.contains(key))cards.put(key);org.json.JSONObject d=Tools.read(this);d.put("homeCards",cards);edit("עדכון העדפות",()->Tools.write(this,d,true));render();});
    }

    private void settings(){tab=5;render();}
    private void settingsPage(boolean backups){
        if(backups){
            if(backupBusy()){LinearLayout progress=card(body);TextView status=bold("העברת קובץ גיבוי מתבצעת…",16,PRIMARY);status.setTag("backup-transfer-status");progress.addView(status);hint(progress,"אפשר להמשיך להשתמש באפליקציה. תוצג הודעה בסיום ההעברה.");}
            googleDriveCard();
            LinearLayout local=card(body);title(local,"קובץ גיבוי ידני");hint(local,"אפשר לשמור ולשחזר גם מהטלפון או מספק קבצים אחר. הגיבוי כולל תנועות, הוראות קבע, חשבונות והעדפות תצוגה. הגנת הכניסה נשארת אישית למכשיר.");space(local,10);
            TextView localSave=button("שמירת גיבוי מלא",false,()->exportBackup(true));localSave.setTag("drive-backup");local.addView(localSave);space(local,10);
            TextView localRestore=button("שחזור מקובץ גיבוי",false,()->chooseRestore(true));localRestore.setTag("drive-restore");local.addView(localRestore);
            long lastManual=getSharedPreferences("backup-history",MODE_PRIVATE).getLong("savedAt",0);if(lastManual>0){TextView last=text("קובץ גיבוי אחרון שנשמר: "+Instant.ofEpochMilli(lastManual).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),13,MUTED);last.setTag("backup-last-saved");local.addView(last);}
            hint(local,"קובץ הגיבוי מכיל נתונים אישיים ואינו מוצפן. יש לבחור תיקייה פרטית בחשבון שלך.");return;
        }
        LinearLayout planning=card(body);title(planning,"תזכורות ותכנון");planning.addView(button("התאמת תמונת המצב",false,this::personalizeHome));planning.addView(button("תקציבים וקיצורי הזנה",false,()->{tab=9;render();}));planning.addView(button("הפעלת תזכורות במכשיר",false,()->{getSharedPreferences("reminder-local",0).edit().putBoolean("enabled",true).apply();if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},702);ReminderJob.schedule(this);toast("תזכורות פעילות. נדרשת הרשאת התראות; התזמון נתון להגבלות Android.");}));planning.addView(button("ביטול תזכורות במכשיר",false,()->{getSharedPreferences("reminder-local",0).edit().putBoolean("enabled",false).apply();((android.app.job.JobScheduler)getSystemService(JOB_SCHEDULER_SERVICE)).cancel(7002);}));
        LinearLayout periodCard=card(body);title(periodCard,"תקופת המעקב");hint(periodCard,"יום תחילת התקופה: "+store.reporting().day+" · "+periodRange(store.reporting().label(LocalDate.now())));hint(periodCard,"תצוגה וסיכומים בלבד. ימי הסגירה והחיוב של כרטיסי האשראי אינם משתנים.");TextView changePeriod=button("שינוי תקופת המעקב",false,this::reportPeriodDialog);changePeriod.setTag("report-period-setting");periodCard.addView(changePeriod);
        LinearLayout appearance=card(body);title(appearance,"מראה האפליקציה");hint(appearance,"בחר מראה בהיר, כהה או התאמה להגדרות המכשיר.");
        String current=prefs.getString("appearance","SYSTEM");String[] keys={"SYSTEM","LIGHT","DARK"},labels={"לפי הגדרות המכשיר","בהיר","כהה"};RadioGroup modes=new RadioGroup(this);modes.setOrientation(RadioGroup.VERTICAL);
        for(int i=0;i<keys.length;i++){RadioButton option=new RadioButton(this);option.setId(View.generateViewId());option.setText(labels[i]);option.setTextColor(INK);option.setTag("appearance-"+keys[i]);option.setPadding(0,dp(7),0,dp(7));modes.addView(option);option.setChecked(keys[i].equals(current));final String choice=keys[i];option.setOnClickListener(v->{if(!choice.equals(prefs.getString("appearance","SYSTEM"))){prefs.edit().putString("appearance",choice).apply();recreate();}});}appearance.addView(modes);
        LinearLayout security=card(body);title(security,"אבטחה ופרטיות");hint(security,appLock.enabled()?"הגנת הכניסה פעילה · נעילה בכל חזרה מהרקע":"הגנת הכניסה כבויה");hint(security,"טביעת אצבע או קוד, תבנית וסיסמה של נעילת המכשיר. האימות מתבצע במכשיר דרך Android.");TextView protect=button(appLock.enabled()?"ביטול הגנת הכניסה":"הפעלת הגנת הכניסה",true,this::changeEntryLock);protect.setTag("entry-lock-setting");security.addView(protect);hint(security,"כשההגנה פעילה, צילומי מסך ותצוגה מקדימה במסך האפליקציות האחרונות נחסמים. ההגדרה אישית למכשיר ונשמרת בנפרד מגיבויי הנתונים.");
        LinearLayout tracking=card(body);title(tracking,"העדפות מעקב");CheckBox income=check(tracking,"הפעלת מעקב הכנסות",incomes());income.setOnCheckedChangeListener((b,on)->{prefs.edit().putBoolean("income",on).apply();});hint(tracking,"אפשר לעקוב אחר הוצאות בלבד. הנתונים הקיימים נשמרים גם כשמשנים את התצוגה.");
        LinearLayout about=card(body);title(about,"PocketFlow · גרסה 0.15.1");hint(about,"הנתונים נשמרים במכשיר. גיבוי ושחזור מקומי ו-Google Drive זמינים במסך הגיבויים.");about.addView(button("גיבוי ושחזור",false,()->{tab=6;render();}));
    }
    private boolean backupBusy(){return backupTransfer!=null||driveTask!=null;}
    private void googleDriveCard(){
        DriveState cloud=new DriveState(this);LinearLayout card=card(body);title(card,"Google Drive · גיבוי אוטומטי");
        if(!cloud.connected()){
            hint(card,"חבר את חשבון Google שלך לגיבוי ישיר בענן ולשחזור במכשיר אחר. נשמרים תנועות, הוראות קבע, חשבונות והעדפות תצוגה.");
            TextView connect=button("חיבור ל־Google וגיבוי ראשון",true,()->startDrive(DriveTask.CONNECT,""));connect.setTag("google-connect");card.addView(connect);
            hint(card,"נדרשת הגדרה חד־פעמית של PocketFlow ב־Google Cloud. לאחר אישור החיבור יופעל גיבוי יומי; אפשר לשנות או לבטל אותו כאן.");return;
        }
        TextView account=text(cloud.email(),14,INK);account.setTag("google-account");card.addView(account);
        if(cloud.prefs.getBoolean("needsAuth",false)){hint(card,"נדרש אישור מחדש של הגישה ל־Google Drive.");card.addView(button("חיבור מחדש",true,()->startDrive(DriveTask.CONNECT,"")));}
        TextView save=button("גיבוי עכשיו",true,()->startDrive(DriveTask.UPLOAD,""));save.setTag("google-backup");card.addView(save);space(card,8);
        TextView restore=button("בחירת גיבוי לשחזור",false,()->startDrive(DriveTask.LIST,""));restore.setTag("google-restore");card.addView(restore);
        CheckBox auto=check(card,"גיבוי אוטומטי",cloud.automatic());auto.setTag("google-auto");
        Spinner frequency=spinner(card,"תדירות הגיבוי",Arrays.asList("מדי יום","מדי שבוע"));frequency.setTag("google-frequency");frequency.setSelection(cloud.days()==7?1:0);frequency.setEnabled(cloud.automatic());
        auto.setOnCheckedChangeListener((b,on)->{cloud.automatic(on,cloud.days());frequency.setEnabled(on);});
        frequency.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){int days=pos==1?7:1;if(cloud.days()!=days)cloud.automatic(cloud.automatic(),days);}});
        hint(card,"הגיבוי פועל ברקע כשיש אינטרנט, גם כשהאפליקציה סגורה. Android עשוי לדחות את ההפעלה בהתאם לחיסכון בסוללה; אין שעת גיבוי קבועה.");
        long saved=cloud.prefs.getLong("lastSuccess",0);TextView status=text(saved==0?"עדיין לא הושלם גיבוי לחשבון זה":"גיבוי אחרון בענן: "+Instant.ofEpochMilli(saved).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),13,MUTED);status.setTag("google-last-success");card.addView(status);
        String failure=cloud.prefs.getString("error","");if(!failure.isEmpty())hint(card,failure);
        hint(card,"הגיבויים נשמרים באזור פרטי של PocketFlow בדרייב ואינם מופיעים ברשימת הקבצים הרגילה. הגישה מוגבלת לנתוני האפליקציה. מוצגים עד 50 הגיבויים האחרונים, ואין מחיקה אוטומטית.");
        TextView disconnect=button("ניתוק חשבון Google",false,()->{
            AlertDialog confirm=new AlertDialog.Builder(this).setTitle("ניתוק Google Drive").setMessage("הגיבוי האוטומטי יופסק והגישה לחשבון תבוטל. הגיבויים שכבר נשמרו לא יימחקו.").setNegativeButton("ביטול",null).setPositiveButton("ניתוק",(d,w)->startDrive(DriveTask.REVOKE,"")).create();confirm.show();styleDialog(confirm);
        });disconnect.setTag("google-disconnect");card.addView(disconnect);
    }
    private void startDrive(String action,String id){startDrive(action,id,null,new DriveState(this).generation());}
    private void startDrive(String action,String id,String token,long generation){
        if(backupBusy()){toast("העברת גיבוי מתבצעת כעת. יש להמתין לסיום.");return;}
        int availability=com.google.android.gms.common.GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        if(availability!=com.google.android.gms.common.ConnectionResult.SUCCESS){Dialog dialog=com.google.android.gms.common.GoogleApiAvailability.getInstance().getErrorDialog(this,availability,202);if(dialog!=null){trackDialog(dialog);dialog.show();}else toast("נדרשים שירותי Google Play פעילים במכשיר");return;}
        driveTask=new DriveTask(this,action,id,generation,false);driveTask.attach(this::completeDrive);driveTask.start(token);if(tab==6)render();
    }
    private void completeDrive(DriveTask result){
        driveTask=null;if(tab==6)render();
        if(result.resolution!=null){
            pendingDriveAction=result.action;pendingDriveFile=result.fileId;pendingDriveGeneration=result.generation;
            try{startIntentSenderForResult(result.resolution.getIntentSender(),201,null,0,0,0);}catch(IntentSender.SendIntentException e){pendingDriveAction="";toast("לא ניתן לפתוח את אישור Google. נסה להתחבר שוב.");}
            return;
        }
        if(result.error!=null){
            if(tab==6)render();
            toast(DriveTask.REVOKE.equals(result.action)?"החשבון נותק במכשיר. לא ניתן היה לבטל את הרשאת Google; אפשר להסירה בהגדרות חשבון Google.":"הפעולה מול Google Drive נכשלה. בדוק חיבור לאינטרנט והגדרת Google Cloud לפי המדריך.");return;
        }
        if(DriveTask.LIST.equals(result.action)){cloudFiles=result.files;cloudListPending=true;if(!appLock.enabled()||unlocked){cloudListPending=false;showCloudFiles();}return;}
        if(DriveTask.DOWNLOAD.equals(result.action)){pendingRestore=true;if(tab==6)render();else showRestorePreview();return;}
        if(tab==6)render();toast(DriveTask.REVOKE.equals(result.action)?"חשבון Google נותק":"הגיבוי נשמר ב־Google Drive");
    }
    private void driveAuthorizationResult(int result,Intent data){
        String action=pendingDriveAction,id=pendingDriveFile;long generation=pendingDriveGeneration;pendingDriveAction="";pendingDriveFile="";
        if(result!=RESULT_OK||data==null){if(tab==6)render();toast("אישור Google בוטל. הנתונים במכשיר לא השתנו.");return;}
        if(action.isEmpty()||generation!=new DriveState(this).generation()){toast("יש להתחבר מחדש ל־Google");return;}
        try{String token=DriveTask.token(com.google.android.gms.auth.api.identity.Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data));startDrive(action,id,token,generation);}
        catch(Exception e){toast("לא אושרה גישה לגיבוי ב־Google Drive");}
    }
    private void showCloudFiles(){
        if(appLock.enabled()&&!unlocked){cloudListPending=true;return;}
        if(cloudFiles==null)return;if(cloudFiles.isEmpty()){toast("לא נמצאו גיבויים של PocketFlow בחשבון זה");return;}
        List<DriveApi.Remote> choices=new ArrayList<>(cloudFiles);String[] labels=new String[choices.size()];
        for(int i=0;i<labels.length;i++){DriveApi.Remote file=choices.get(i);String date=file.date;try{date=Instant.parse(file.date).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));}catch(Exception ignored){}labels[i]=date+"\n"+file.name;}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("בחירת גיבוי מ־Google Drive").setItems(labels,(d,index)->startDrive(DriveTask.DOWNLOAD,choices.get(index).id)).setNegativeButton("ביטול",null).create();dialog.show();styleDialog(dialog);
    }
    private void exportBackup(boolean drive){
        if(backupBusy()){toast("העברת גיבוי מתבצעת כעת. יש להמתין לסיום.");return;}
        try{pendingExport=store.backup().toString(2);createFile("application/json",BackupFiles.filename(),drive?103:105);}
        catch(Exception e){toast("לא ניתן להכין גיבוי. הנתונים במכשיר נשמרו.");}
    }
    private void chooseRestore(boolean drive){
        if(backupBusy()){toast("העברת גיבוי מתבצעת כעת. יש להמתין לסיום.");return;}
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("application/json");intent.addCategory(Intent.CATEGORY_OPENABLE);
        if(drive)initialDriveLocation(intent);
        try{startActivityForResult(intent,drive?104:102);}catch(ActivityNotFoundException e){toast("אין במכשיר חלון קבצים זמין. יש להתקין או להפעיל ספק קבצים.");}
    }
    private void initialDriveLocation(Intent intent){
        String last=getSharedPreferences("backup-history",MODE_PRIVATE).getString("driveUri","");
        if(!last.isEmpty())intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,Uri.parse(last));
    }
    private void createFile(String mime,String name,int request){
        if(backupBusy()){toast("העברת גיבוי מתבצעת כעת. יש להמתין לסיום לפני ייצוא קובץ נוסף.");return;}
        try(FileOutputStream out=new FileOutputStream(new File(getCacheDir(),"export-draft"))){out.write(pendingExport.getBytes(StandardCharsets.UTF_8));}
        catch(IOException e){toast("לא ניתן להכין את הקובץ לשמירה");return;}
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(mime);i.putExtra(Intent.EXTRA_TITLE,name);if(request==103)initialDriveLocation(i);
        try{startActivityForResult(i,request);}catch(ActivityNotFoundException e){new File(getCacheDir(),"export-draft").delete();pendingExport="";toast("אין במכשיר חלון קבצים זמין. יש להתקין או להפעיל ספק קבצים.");}
    }
    private File restoreDraft(){return new File(getCacheDir(),"restore-draft.json");}
    private void clearRestore(){pendingRestore=false;restoreDraft().delete();}
    private void showRestorePreview(){
        if(!pendingRestore||(appLock.enabled()&&!unlocked)||(restoreDialog!=null&&restoreDialog.isShowing()))return;
        try{
            JSONObject backup;try(InputStream in=new FileInputStream(restoreDraft())){backup=BackupFiles.read(in);}
            String details=BackupFiles.description(backup);
            restoreDialog=new AlertDialog.Builder(this).setTitle("אישור שחזור גיבוי").setMessage(details+"\n\nהשחזור יחליף את כל הנתונים הנוכחיים במכשיר. מומלץ לשמור גיבוי לפני ההחלפה.")
                .setNegativeButton("ביטול",(d,w)->clearRestore()).setPositiveButton("שחזור והחלפת הנתונים",(d,w)->{
                    try{store.restore(backup);resetReportSelection();clearRestore();categoryFilter="";merchantFilter="";accountFilter=0;query="";advancedFilter=new MovementFilter();navigationHistory.clear();if(palette.dark!=AppTheme.dark(this))recreate();else render();toast("הגיבוי שוחזר בהצלחה");}
                    catch(Exception e){clearRestore();toast("השחזור נכשל. הנתונים הקודמים נשמרו.");}
                }).create();
            restoreDialog.setOnCancelListener(d->clearRestore());restoreDialog.show();styleDialog(restoreDialog);
        }catch(Exception e){clearRestore();toast("קובץ הגיבוי אינו תקין. הנתונים לא השתנו.");}
    }
    private String csv(String s){return "\""+s.replace("\"","\"\"")+"\"";}
    private String safeCsv(String s){return csv(s.matches("^[=+@\\-\\t\\r].*")?"'"+s:s);}
    private void exportCsv(){
        StringBuilder b=new StringBuilder("\uFEFFתאריך,סכום בשקלים,סוג,קטגוריה,בית עסק,אמצעי תשלום,בן משפחה,תיאור,תשלומים,סטטוס,מטבע,סכום מקורי,שער לשקל,תאריך שער,מקור שער,תשלום ראשון,עמלת מט״ח באחוזים,סכום עמלת מט״ח בשקלים\r\n");
        for(Store.Tx t:store.month(month))b.append(t.date).append(',').append(Finance.decimal(t.kind.equals("refund")?-t.amount:t.amount)).append(',').append(t.kind.equals("income")?"הכנסה":t.kind.equals("refund")?"זיכוי":"הוצאה").append(',').append(safeCsv(t.category)).append(',').append(safeCsv(t.merchant)).append(',').append(safeCsv(store.account(t.account).name)).append(',').append(safeCsv(t.tag)).append(',').append(safeCsv(t.note)).append(',').append(t.count).append(',').append(t.status==1?"בוצע":"צפוי").append(',').append(t.currency).append(',').append(Finance.decimal(t.originalAmount)).append(',').append(t.fxRate).append(',').append(safeCsv(t.fxDate)).append(',').append(safeCsv(t.fxSource)).append(',').append(safeCsv(t.firstDue)).append(',').append(t.fxFee).append(',').append(Finance.decimal(t.amount-Finance.convert(t.originalAmount,t.fxRate))).append("\r\n");
        pendingExport=b.toString();createFile("text/csv","PocketFlow-"+store.reporting().start(month)+"_"+store.reporting().end(month)+".csv",101);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(appLock.result(request,result))return;
        if(request==201){driveAuthorizationResult(result,data);return;}
        boolean saving=request==101||request==103||request==105,restoring=request==102||request==104;
        if(!saving&&!restoring)return;
        File draft=new File(getCacheDir(),"export-draft");
        if(result!=RESULT_OK||data==null||data.getData()==null){if(saving){draft.delete();pendingExport="";}return;}
        if(backupBusy()){toast("העברת גיבוי מתבצעת כעת");return;}
        backupTransfer=new BackupTransfer(request,data.getData());backupTransfer.attach(this::completeTransfer);backupTransfer.start(this);if(tab==6)render();toast(saving?"שומר קובץ גיבוי…":"קורא קובץ גיבוי…");
    }
    private void completeTransfer(BackupTransfer transfer){
        backupTransfer=null;boolean saving=transfer.request==101||transfer.request==103||transfer.request==105;
        File draft=new File(getCacheDir(),"export-draft");Uri uri=transfer.uri;
        if(transfer.error!=null){if(saving){draft.delete();pendingExport="";}else clearRestore();if(tab==6)render();toast(saving?"שמירת הקובץ נכשלה. בדוק הרשאות, מקום פנוי וחיבור לאינטרנט.":"לא ניתן לקרוא את הגיבוי. בדוק את הקובץ ואת החיבור ל־Drive. הנתונים לא השתנו.");return;}
        if(saving){
            draft.delete();pendingExport="";
            if(transfer.request!=101){
                SharedPreferences.Editor history=getSharedPreferences("backup-history",MODE_PRIVATE).edit().putLong("savedAt",System.currentTimeMillis()).putString("destination",BackupFiles.isDrive(uri)?"Google Drive":"מיקום שנבחר");
                if(BackupFiles.isDrive(uri))history.putString("driveUri",uri.toString());history.apply();
            }
            if(tab==6)render();toast(BackupFiles.isDrive(uri)?"קובץ הגיבוי נשמר דרך Drive. יש לוודא שההעלאה לענן הושלמה ב־Drive.":"הקובץ נשמר");
        }else{
            if(BackupFiles.isDrive(uri))getSharedPreferences("backup-history",MODE_PRIVATE).edit().putString("driveUri",uri.toString()).apply();
            pendingRestore=true;if(tab==6)render();else showRestorePreview();
        }
    }
}
