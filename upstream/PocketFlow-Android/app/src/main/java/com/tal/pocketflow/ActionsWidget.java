package com.tal.pocketflow;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

/** Native directional slide animations between whole pages, driven by explicit arrow actions. */
public final class ActionsWidget extends AppWidgetProvider {
    static final String PAGE="com.tal.pocketflow.WIDGET_PAGE";
    static int page(Context context,int id){return Math.max(0,Math.min(2,context.getSharedPreferences("widget-pages",0).getInt("page-"+id,1)));}
    static int moved(int page,int direction){return Math.max(0,Math.min(2,page+direction));}
    static String action(int page){return page==0?WidgetActions.ACCOUNT:page==2?WidgetActions.RULE:WidgetActions.MOVEMENT;}
    static RemoteViews views(Context context,int id){return views(context,id,0);}
    static RemoteViews views(Context context,int id,int direction){
        int page=page(context,id);int label=page==0?R.string.widget_account_label:page==2?R.string.widget_rule_label:R.string.widget_add_label;
        RemoteViews views=new RemoteViews(context.getPackageName(),R.layout.widget_actions);views.setTextViewText(R.id.widget_page_indicator,(page+1)+" / 3");
        views.setViewVisibility(R.id.widget_flip_up,direction>0?View.GONE:View.VISIBLE);views.setViewVisibility(R.id.widget_flip_down,direction>0?View.VISIBLE:View.GONE);
        views.setInt(R.id.widget_flip_up,"setDisplayedChild",page);views.setInt(R.id.widget_flip_down,"setDisplayedChild",page);
        views.setContentDescription(R.id.widget_page_action,context.getString(label)+" +");views.setContentDescription(R.id.widget_page_indicator,context.getString(R.string.widget_page_number,page+1));
        views.setOnClickPendingIntent(R.id.widget_page_action,WidgetActions.open(context,action(page),id));
        views.setViewVisibility(R.id.widget_up,page==0?View.INVISIBLE:View.VISIBLE);views.setViewVisibility(R.id.widget_down,page==2?View.INVISIBLE:View.VISIBLE);
        views.setOnClickPendingIntent(R.id.widget_up,arrow(context,id,-1));views.setOnClickPendingIntent(R.id.widget_down,arrow(context,id,1));return views;
    }
    static PendingIntent arrow(Context context,int id,int direction){Intent intent=new Intent(context,ActionsWidget.class).setAction(PAGE).setData(Uri.parse("pocketflow://widget/"+id+"/arrow/"+direction)).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).putExtra("direction",direction);return PendingIntent.getBroadcast(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids){for(int id:ids)manager.updateAppWidget(id,views(context,id));}
    @Override public void onAppWidgetOptionsChanged(Context context,AppWidgetManager manager,int id,Bundle options){manager.updateAppWidget(id,views(context,id));}
    @Override public void onReceive(Context context,Intent intent){
        super.onReceive(context,intent);if(!PAGE.equals(intent.getAction()))return;int id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1),direction=intent.getIntExtra("direction",0);if(direction!=-1&&direction!=1)return;
        AppWidgetManager manager=AppWidgetManager.getInstance(context);boolean own=false;for(int bound:manager.getAppWidgetIds(new ComponentName(context,ActionsWidget.class)))if(bound==id)own=true;if(!own)return;
        int previous=page(context,id),next=moved(previous,direction);if(next==previous)return;context.getSharedPreferences("widget-pages",0).edit().putInt("page-"+id,next).apply();manager.updateAppWidget(id,views(context,id,direction));
    }
    @Override public void onDeleted(Context context,int[] ids){SharedPreferences.Editor edit=context.getSharedPreferences("widget-pages",0).edit();for(int id:ids)edit.remove("page-"+id);edit.apply();}
    @Override public void onRestored(Context context,int[] oldIds,int[] newIds){onUpdate(context,AppWidgetManager.getInstance(context),newIds);}
}
