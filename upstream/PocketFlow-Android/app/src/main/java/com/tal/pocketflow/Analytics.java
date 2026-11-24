package com.tal.pocketflow;
import android.content.Context;
import org.json.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
/** Read-only analysis. Actual spend, scheduled cash flow and estimates are kept distinct. */
final class Analytics {
 final Store store;final Context context;
 Analytics(Store s,Context c){store=s;context=c;}
 static String money(long n){return "\u200e₪ "+String.format(Locale.US,"%,.2f",n/100d);}
 static final class Insight{String title,detail,category="",merchant="";boolean purchases;long account,tx;Insight(String t,String d){title=t;detail=d;}}
 LocalDate cutoff(YearMonth m,LocalDate today){return store.reporting().cutoff(m,today);}
 List<Store.Tx> actual(YearMonth m,LocalDate through){String start=store.reporting().start(m).toString();List<Store.Tx> out=new ArrayList<>();for(Store.Tx t:store.transactions())if(t.status==1&&Store.isSpending(t)&&t.date.compareTo(start)>=0&&t.date.compareTo(through.toString())<=0)out.add(t);return out;}
 List<Insight> insights(YearMonth m,LocalDate today){
  List<Insight> out=new ArrayList<>();LocalDate end=cutoff(m,today);if(end.isBefore(store.reporting().start(m)))return out;
  YearMonth prev=m.minusMonths(1);LocalDate prevEnd=store.reporting().comparisonEnd(m,today);List<Store.Tx> now=actual(m,end),before=actual(prev,prevEnd);
  Map<String,Long> sums=new TreeMap<>(),old=new HashMap<>(),merchants=new HashMap<>();long total=0;
  for(Store.Tx t:now){sums.merge(t.category,Store.netExpense(t),Long::sum);total+=Store.netExpense(t);if(!t.merchant.isEmpty())merchants.merge(t.merchant,Store.netExpense(t),Long::sum);}
  for(Store.Tx t:before)old.merge(t.category,Store.netExpense(t),Long::sum);
  long previous=0;for(Store.Tx t:before)previous+=Store.netExpense(t);
  if(total>0||previous>0){Insight comparison=new Insight("החודש לעומת החודש הקודם",money(total)+" לעומת "+money(previous)+" · "+store.reporting().start(m)+" עד "+end+" מול "+store.reporting().start(prev)+" עד "+prevEnd+" · הוצאות שבוצעו בלבד");comparison.purchases=true;out.add(comparison);}
  for(Map.Entry<String,Long> e:sums.entrySet()){long prior=old.getOrDefault(e.getKey(),0L);if(prior>0&&Math.abs(e.getValue()-prior)*100d/prior>=20){long delta=Math.round((e.getValue()-prior)*100d/prior);Insight i=new Insight(e.getKey()+": "+(delta>0?"עלייה של ":"ירידה של ")+Math.abs(delta)+"%",money(e.getValue())+" לעומת "+money(prior)+" · השוואה לאותם ימים בחודש הקודם · הוצאות שבוצעו");i.category=e.getKey();i.purchases=true;out.add(i);}}
  if(total>0&&!merchants.isEmpty()){List<Map.Entry<String,Long>> sorted=new ArrayList<>(merchants.entrySet());sorted.sort((a,b)->Long.compare(b.getValue(),a.getValue()));Map.Entry<String,Long> top=sorted.get(0);Insight i=new Insight("בית העסק המוביל: "+top.getKey(),money(top.getValue())+" · "+Math.round(top.getValue()*100d/total)+"% מההוצאות שבוצעו בתקופה");i.merchant=top.getKey();i.purchases=true;out.add(i);}
  long annual=0;for(Store.Rule r:store.rules())if(r.active==1&&!LocalDate.parse(r.start).isAfter(store.reporting().end(m))&&(r.end.isEmpty()||!LocalDate.parse(r.end).isBefore(store.reporting().start(m))))annual+=store.expectedRuleAmount(r)*12/r.interval;
  if(annual>0)out.add(new Insight("העלות השנתית של ההוצאות החוזרות",money(annual)+" בקצב הנוכחי · סכומים שהוגדרו, ללא חיזוי שינויי מחיר"));
  for(Store.Tx t:store.transactions())if(t.status==1&&t.kind.equals("expense")&&t.count>1){Store.Account a=store.account(t.account);LocalDate first=t.firstDue.isEmpty()?Finance.due(LocalDate.parse(t.date),a.type,a.closing,a.charge):LocalDate.parse(t.firstDue);if(store.reporting().contains(m,Finance.day(YearMonth.from(first).plusMonths(t.count-1),first.getDayOfMonth()))){Insight i=new Insight("התשלום האחרון החודש: "+(t.merchant.isEmpty()?t.category:t.merchant),money(Finance.installment(t.amount,t.count,t.count-1))+" יסתיימו בלוח התשלומים · לא יתווספו בחודש הבא");i.tx=t.id;out.add(i);}}
  return out;
 }
 static LocalDate next(Store.Rule r,LocalDate today){LocalDate start=LocalDate.parse(r.start);long diff=Math.max(0,ChronoUnit.MONTHS.between(YearMonth.from(start),YearMonth.from(today)));long step=diff/r.interval*r.interval;LocalDate n=Finance.day(YearMonth.from(start).plusMonths(step),start.getDayOfMonth());if(n.isBefore(today))n=Finance.day(YearMonth.from(start).plusMonths(step+r.interval),start.getDayOfMonth());return !r.end.isEmpty()&&n.isAfter(LocalDate.parse(r.end))?null:n;}
 List<Insight> renewals(LocalDate today){List<Insight> out=new ArrayList<>();for(Store.Rule r:store.rules())if(r.active==1){LocalDate n=r.renewal.isEmpty()?next(r,today):LocalDate.parse(r.renewal);if(n==null)continue;long days=ChronoUnit.DAYS.between(today,n);if(days<=r.reminderDays){Insight i=new Insight((days<0?"מועד חידוש עבר: ":"חידוש מתקרב: ")+r.name,n+" · "+money(store.expectedRuleAmount(r))+" · "+(days<0?"יש לעדכן את מועד החידוש":"בעוד "+days+" ימים"));i.tx=-r.id;out.add(i);}}return out;}
 static final class Issue{String key,title,detail;Store.Tx tx;Issue(String k,String t,String d,Store.Tx x){key=k;title=t;detail=d;tx=x;}}
 List<Issue> quality(LocalDate today){List<Issue> out=new ArrayList<>();Map<String,Store.Tx> seen=new HashMap<>();for(Store.Tx t:store.transactions()){
  String fingerprint=t.id+":"+t.date+":"+t.amount+":"+t.account+":"+t.merchant+":"+t.status;
  if(t.status==0&&t.date.compareTo(today.toString())<0)addIssue(out,new Issue("pending:"+fingerprint,"חיוב צפוי שמועדו עבר",(t.merchant.isEmpty()?t.category:t.merchant)+" · "+t.date+" · "+money(t.amount),t));
  if(t.category.trim().isEmpty())addIssue(out,new Issue("category:"+fingerprint,"תנועה ללא קטגוריה",t.date+" · "+money(t.amount),t));
  if(t.status==1&&t.rule==0){String key=t.kind+"|"+t.date+"|"+t.amount+"|"+t.account+"|"+t.merchant.trim().toLowerCase(Locale.ROOT)+"|"+(t.merchant.isEmpty()?t.category:"");Store.Tx old=seen.putIfAbsent(key,t);if(old!=null)addIssue(out,new Issue("duplicate:"+fingerprint+":"+old.id,"כפילות אפשרית",(t.merchant.isEmpty()?t.category:t.merchant)+" · "+money(t.amount)+" · "+t.date+" · שתי תנועות דומות, לא נמחק דבר",t));}
 }return out;}
 private void addIssue(List<Issue> list,Issue i){if(!Tools.dismissed(context,i.key))list.add(i);}
 long spend(YearMonth m,String category){long sum=0;for(Store.Tx t:store.month(m))if(Store.isSpending(t)&&(category.isEmpty()||t.category.equals(category)))sum+=Store.netExpense(t);return sum;}
 long variableAverage(YearMonth from){ReportPeriod period=store.reporting();long sum=0;for(Store.Tx t:store.transactions())if(t.status==1&&Store.isSpending(t)&&t.rule==0&&t.count==1&&(!t.kind.equals("refund")||(store.transaction(t.refundOf).rule==0&&store.transaction(t.refundOf).count==1))){YearMonth m=period.label(LocalDate.parse(t.date));if(!m.isBefore(from.minusMonths(3))&&m.isBefore(from))sum+=Store.netExpense(t);}return sum/3;}
 List<String> alerts(LocalDate today){List<String> out=new ArrayList<>();for(Insight i:renewals(today))out.add(i.title+" · "+i.detail);JSONObject b=Tools.budgets(context);Iterator<String> keys=b.keys();while(keys.hasNext()){String key=keys.next();long limit=b.optLong(key),spent=spend(store.reporting().label(today),key);if(limit>0&&spent>=limit*0.8)out.add((key.isEmpty()?"התקציב הכולל":key)+": "+Math.round(spent*100d/limit)+"% נוצלו");}return out;}
}
