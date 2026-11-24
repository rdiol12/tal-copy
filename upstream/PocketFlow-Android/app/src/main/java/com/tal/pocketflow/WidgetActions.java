package com.tal.pocketflow;

import android.app.PendingIntent;
import android.content.*;
import android.net.Uri;

final class WidgetActions {
    static final String EXTRA="pocketflow.widget.action",MOVEMENT="movement",RULE="rule",ACCOUNT="account";
    static boolean valid(String action){return MOVEMENT.equals(action)||RULE.equals(action)||ACCOUNT.equals(action);}
    static Intent intent(Context context,String action,int widgetId){return new Intent(context,MainActivity.class)
        .setAction("com.tal.pocketflow.WIDGET_OPEN").setData(Uri.parse("pocketflow://widget/"+widgetId+"/"+action))
        .putExtra(EXTRA,action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);}
    static PendingIntent open(Context context,String action,int widgetId){return PendingIntent.getActivity(context,0,intent(context,action,widgetId),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
}
