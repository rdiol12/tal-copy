package com.tal.pocketflow;
import android.content.Context;import java.time.*;import java.time.temporal.ChronoUnit;import java.util.*;
/** Reporting windows only. Does not change card policies, posting dates or recurrence schedules. */
final class ReportPeriod {
 final int day;
 ReportPeriod(int day){if(day<1||day>31)throw new IllegalArgumentException("יום התחלה חייב להיות בין 1 ל־31");this.day=day;}
 static ReportPeriod of(Context c){return new ReportPeriod(Tools.read(c).optInt("periodStart",1));}
 LocalDate start(YearMonth label){return Finance.day(label,day);}
 LocalDate end(YearMonth label){return start(label.plusMonths(1)).minusDays(1);}
 YearMonth label(LocalDate date){YearMonth m=YearMonth.from(date);return date.isBefore(start(m))?m.minusMonths(1):m;}
 boolean contains(YearMonth label,LocalDate date){return !date.isBefore(start(label))&&!date.isAfter(end(label));}
 int length(YearMonth label){return (int)ChronoUnit.DAYS.between(start(label),end(label))+1;}
 LocalDate cutoff(YearMonth label,LocalDate today){return today.isBefore(start(label))?start(label).minusDays(1):today.isAfter(end(label))?end(label):today;}
 int elapsed(YearMonth label,LocalDate today){return (int)ChronoUnit.DAYS.between(start(label),cutoff(label,today))+1;}
 LocalDate comparisonEnd(YearMonth selected,LocalDate today){YearMonth prev=selected.minusMonths(1);int days=elapsed(selected,today);return days==0?start(prev).minusDays(1):start(prev).plusDays(Math.min(days,length(prev))-1);}
 List<LocalDate> ruleDates(Store.Rule r,YearMonth label){List<LocalDate> dates=new ArrayList<>();LocalDate first=LocalDate.parse(r.start);YearMonth origin=YearMonth.from(first);for(YearMonth m=YearMonth.from(start(label));!m.isAfter(YearMonth.from(end(label)));m=m.plusMonths(1)){long delta=ChronoUnit.MONTHS.between(origin,m);LocalDate due=Finance.day(m,first.getDayOfMonth());if(delta>=0&&delta%r.interval==0&&!due.isBefore(first)&&(r.end.isEmpty()||!due.isAfter(LocalDate.parse(r.end)))&&contains(label,due))dates.add(due);}return dates;}
}
