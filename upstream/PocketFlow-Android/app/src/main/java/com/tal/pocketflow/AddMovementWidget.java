package com.tal.pocketflow;

import android.appwidget.*;
import android.content.Context;
import android.widget.RemoteViews;

public final class AddMovementWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids){for(int id:ids){RemoteViews views=new RemoteViews(context.getPackageName(),R.layout.widget_add);views.setOnClickPendingIntent(R.id.widget_add_action,WidgetActions.open(context,WidgetActions.MOVEMENT,id));manager.updateAppWidget(id,views);}}
}
