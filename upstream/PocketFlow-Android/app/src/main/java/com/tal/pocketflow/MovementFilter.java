package com.tal.pocketflow;
import org.json.*;import java.time.*;import java.util.*;
/** Combined, portable search criteria. Amounts refer to the original positive transaction amount. */
final class MovementFilter {
 String from="",to="",family="",kind="";long min=-1,max=-1;int status=-1;
 JSONObject json(){try{return new JSONObject().put("from",from).put("to",to).put("family",family).put("kind",kind).put("min",min).put("max",max).put("status",status);}catch(Exception e){throw new IllegalArgumentException(e);}}
 static MovementFilter from(JSONObject o){MovementFilter f=new MovementFilter();if(o==null)return f;f.from=o.optString("from","");f.to=o.optString("to","");f.family=o.optString("family","");f.kind=o.optString("kind","");f.min=o.optLong("min",-1);f.max=o.optLong("max",-1);f.status=o.optInt("status",-1);f.validate();return f;}
 void validate(){if(!from.isEmpty())LocalDate.parse(from);if(!to.isEmpty())LocalDate.parse(to);if(!from.isEmpty()&&!to.isEmpty()&&from.compareTo(to)>0)throw new IllegalArgumentException("תאריך ההתחלה מאוחר מתאריך הסיום");if(min< -1||max< -1||(min>=0&&max>=0&&min>max)||status< -1||status>1||!Arrays.asList("","expense","income","refund").contains(kind))throw new IllegalArgumentException("טווח הסינון אינו תקין");}
 boolean active(){return !from.isEmpty()||!to.isEmpty()||!family.isEmpty()||!kind.isEmpty()||min>=0||max>=0||status>=0;}
 boolean matches(Store.Tx t,YearMonth m,String category,long account,String merchant,String query){return matches(t,m,new ReportPeriod(1),category,account,merchant,query);}
 boolean matches(Store.Tx t,YearMonth m,ReportPeriod period,String category,long account,String merchant,String query){
  String start=from.isEmpty()?(to.isEmpty()?period.start(m).toString():"0000-01-01"):from,end=to.isEmpty()?(from.isEmpty()?period.end(m).toString():"9999-12-31"):to;
  if(t.date.compareTo(start)<0||t.date.compareTo(end)>0||(!category.isEmpty()&&!category.equals(t.category))||(account>0&&account!=t.account)||(!merchant.isEmpty()&&!merchant.equals(t.merchant.trim().isEmpty()?"ללא בית עסק":t.merchant.trim()))||(!family.isEmpty()&&!family.equalsIgnoreCase(t.tag.trim()))||(!kind.isEmpty()&&!kind.equals(t.kind))||(status>=0&&status!=t.status)||(min>=0&&t.amount<min)||(max>=0&&t.amount>max))return false;
  String hay=(t.merchant+" "+t.note+" "+t.tag+" "+t.category).toLowerCase(Locale.ROOT);for(String word:query.trim().toLowerCase(Locale.ROOT).split("\\s+"))if(!hay.contains(word))return false;return true;
 }
}
