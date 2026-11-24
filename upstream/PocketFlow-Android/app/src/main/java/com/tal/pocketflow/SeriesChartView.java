package com.tal.pocketflow;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;

/** Multiple lines or grouped bars; exact amounts and accessible labels live below the plot. */
public final class SeriesChartView extends View {
    final Store.SeriesData data;
    final boolean lines;
    private final ChartView.Pick pick;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    SeriesChartView(Context context,Store.SeriesData data,boolean lines,ChartView.Pick pick){super(context);this.data=data;this.lines=lines;this.pick=pick;setContentDescription(lines?"השוואת הוצאות מצטברות. הסכומים והמקרא מופיעים מתחת לגרף":"הכנסות והוצאות לפי חודש. הסכומים המדויקים מופיעים מתחת לגרף");}
    private float dp(float value){return value*getResources().getDisplayMetrics().density;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);int count=data.labels.size();if(count==0)return;AppTheme.Palette palette=AppTheme.palette(getContext());
        float left=dp(52),right=getWidth()-dp(8),top=dp(20),bottom=getHeight()-dp(38),width=Math.max(1,right-left);long max=1,min=0;
        for(long[] series:data.series.values())for(long amount:series){max=Math.max(max,amount);min=Math.min(min,amount);}
        paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(dp(10));paint.setStrokeWidth(dp(1));paint.setStyle(Paint.Style.FILL);
        for(int i=0;i<=4;i++){float y=bottom-(bottom-top)*i/4f;paint.setColor(palette.outline);canvas.drawLine(left,y,right,y,paint);paint.setColor(palette.muted);paint.setTextAlign(Paint.Align.RIGHT);canvas.drawText(String.format(Locale.US,"%,.0f",(min+(max-(double)min)*i/4d)/100d),left-dp(5),y+dp(3),paint);}
        int seriesIndex=0;float slot=width/count;
        for(long[] series:data.series.values()){
            paint.setColor(ChartView.colors(getContext())[seriesIndex%ChartView.COLORS.length]);
            if(lines){Path path=new Path();for(int i=0;i<count;i++){float x=count==1?left+width/2:left+width*i/(count-1),y=bottom-(float)((series[i]-(double)min)/(max-(double)min)*(bottom-top));if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2.5f));canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);float lastX=count==1?left+width/2:right,lastY=bottom-(float)((series[count-1]-(double)min)/(max-(double)min)*(bottom-top));canvas.drawCircle(lastX,lastY,dp(3.5f),paint);}
            else{int size=data.series.size();float bar=Math.min(dp(17),slot*.7f/Math.max(1,size)),offset=(seriesIndex-(size-1)/2f)*bar;for(int i=0;i<count;i++){float center=left+slot*(i+.5f)+offset,baseline=bottom-(float)((-min)/(max-(double)min)*(bottom-top)),height=(float)(series[i]/(max-(double)min)*(bottom-top));canvas.drawRoundRect(new RectF(center-bar*.44f,Math.min(baseline-height,baseline),center+bar*.44f,Math.max(baseline-height,baseline)),dp(3),dp(3),paint);}}
            seriesIndex++;
        }
        paint.setColor(palette.muted);paint.setTextSize(dp(10));paint.setTextAlign(Paint.Align.CENTER);
        for(int i=0;i<count;i++)if(!lines||i==0||i==count-1||(i+1)%5==0){float x=lines?(count==1?left+width/2:left+width*i/(count-1)):left+slot*(i+.5f);canvas.drawText(data.labels.get(i),x,bottom+dp(22),paint);}
    }
    @Override public boolean onTouchEvent(MotionEvent event){if(event.getAction()==MotionEvent.ACTION_UP){performClick();if(pick!=null&&!data.labels.isEmpty()){float left=dp(52),width=Math.max(1,getWidth()-left-dp(8));int index=(int)((event.getX()-left)/width*data.labels.size());if(event.getX()>=left&&index>=0&&index<data.labels.size())pick.select(data.labels.get(index));}}return true;}
    @Override public boolean performClick(){super.performClick();return true;}
}
