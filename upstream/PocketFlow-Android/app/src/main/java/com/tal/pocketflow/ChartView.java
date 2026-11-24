package com.tal.pocketflow;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import java.util.*;

/** Accessible chart: labels and selectable equivalents are supplied by the host screen. */
public class ChartView extends View {
    public interface Pick { void select(String key); }
    public static final int[] COLORS={0xff255DD2,0xff087F82,0xffDB9B50,0xffB46BA2,0xff71B4B0,0xff9CA8C3};
    public static int[] colors(Context c){return AppTheme.dark(c)?new int[]{0xffADC7FF,0xff80D6CB,0xffE0BC82,0xffD9ACD0,0xffA6D8D5,0xffC0CCDD}:COLORS;}
    final Map<String,Long> values; final boolean donut; final Pick pick;
    String centerLabel="סה״כ הוצאות · ₪";
    final Paint p=new Paint(3);final RectF rect=new RectF();
    public ChartView(Context c){this(c,new LinkedHashMap<>(),true,null);}
    public ChartView(Context c,Map<String,Long> v,boolean donut,Pick pick){super(c);this.values=v;this.donut=donut;this.pick=pick;setLayerType(View.LAYER_TYPE_SOFTWARE,null);setContentDescription(donut?"התפלגות הוצאות. הפירוט והסינון זמינים בכפתורים שמתחת לגרף":"השוואת הוצאות חודשית. הפירוט מופיע מתחת לגרף");}
    float dp(float n){return n*getResources().getDisplayMetrics().density;}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);AppTheme.Palette palette=AppTheme.palette(getContext());float w=getWidth(),h=getHeight();long total=0,max=1,min=0,positive=0;for(long v:values.values()){total+=v;positive+=Math.max(0,v);max=Math.max(max,v);min=Math.min(min,v);}
        if(donut){
            float size=Math.min(w-dp(35),h-dp(30)),cx=w/2,cy=h/2;rect.set(cx-size/2,cy-size/2,cx+size/2,cy+size/2);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(25));p.setStrokeCap(Paint.Cap.BUTT);
            if(positive==0){p.setColor(palette.variant);c.drawOval(rect,p);}else{float start=-90;int i=0;for(long v:values.values()){float sweep=360f*Math.max(0,v)/positive;p.setColor(colors(getContext())[i++%COLORS.length]);c.drawArc(rect,start+1,Math.max(0,sweep-2),false,p);start+=sweep;}}
            p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setColor(palette.ink);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(dp(25));c.drawText(String.format(Locale.US,"%,.0f",total/100d),cx,cy,p);p.setColor(palette.muted);p.setTextSize(dp(13));c.drawText(centerLabel,cx,cy+dp(25),p);
        }else{
            int n=Math.max(1,values.size()),i=0;float usable=h-dp(55),slot=w/n;
            for(Map.Entry<String,Long> e:values.entrySet()){float baseline=(float)(dp(15)+usable*max/(max-(double)min)),height=(float)(usable*e.getValue()/(max-(double)min)),left=slot*i+slot*.22f,right=slot*(i+1)-slot*.22f;p.setColor(i==n-1?palette.primary:palette.container);rect.set(left,Math.min(baseline-height,baseline),right,Math.max(baseline-height,baseline));c.drawRoundRect(rect,dp(6),dp(6),p);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(dp(11));p.setColor(palette.muted);c.drawText(e.getKey(),slot*(i+.5f),h-dp(10),p);p.setTextSize(dp(10));c.drawText(String.format(Locale.US,"%,.0f",e.getValue()/100d),slot*(i+.5f),Math.max(dp(12),baseline-height-dp(5)),p);i++;}
        }
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()!=MotionEvent.ACTION_UP)return true;
        performClick();if(pick==null||values.isEmpty())return true;
        if(donut){double angle=Math.toDegrees(Math.atan2(e.getY()-getHeight()/2f,e.getX()-getWidth()/2f))+90;if(angle<0)angle+=360;long total=0;for(long v:values.values())total+=Math.max(0,v);double cursor=0;for(Map.Entry<String,Long> item:values.entrySet()){cursor+=360d*Math.max(0,item.getValue())/Math.max(1,total);if(item.getValue()>0&&angle<=cursor){pick.select(item.getKey());break;}}}
        else{int index=Math.min(values.size()-1,(int)(e.getX()/getWidth()*values.size()));if(index>=0)pick.select(new ArrayList<>(values.keySet()).get(index));}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
