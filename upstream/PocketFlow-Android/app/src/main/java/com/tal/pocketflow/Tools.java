package com.tal.pocketflow;
import android.content.Context;
import org.json.*;
import java.util.*;
/** Portable user preferences; security and notification delivery state stay device-local. */
final class Tools {
 static JSONObject read(Context c){try{return new JSONObject(c.getSharedPreferences("prefs",0).getString("tools","{}"));}catch(Exception e){return new JSONObject();}}
 static void write(Context c,JSONObject data,boolean notify){validate(data);c.getSharedPreferences("prefs",0).edit().putString("tools",data.toString()).apply();}
 static void validate(JSONObject d){
  if(d.has("periodStart")){Object day=d.opt("periodStart");if(!(day instanceof Number)||((Number)day).doubleValue()!=((Number)day).intValue()||((Number)day).intValue()<1||((Number)day).intValue()>31)throw new IllegalArgumentException("יום תחילת תקופת המעקב אינו תקין");}
  if(d.toString().length()>500000)throw new IllegalArgumentException("העדפות גדולות מדי");
  JSONObject budgets=d.optJSONObject("budgets");if(budgets!=null){Iterator<String> keys=budgets.keys();while(keys.hasNext()){String k=keys.next();if(k.length()>500||budgets.optLong(k,-1)<0)throw new IllegalArgumentException("תקציב לא תקין");}}
  JSONArray searches=d.optJSONArray("searches");if(searches!=null){if(searches.length()>30)throw new IllegalArgumentException("יותר מדי חיפושים");for(int i=0;i<searches.length();i++){JSONObject q=searches.optJSONObject(i);if(q==null||q.optString("name").trim().isEmpty())throw new IllegalArgumentException("חיפוש שמור לא תקין");MovementFilter.from(q);}}
  JSONArray templates=d.optJSONArray("templates");if(templates!=null){if(templates.length()>100)throw new IllegalArgumentException("יותר מדי קיצורים");for(int i=0;i<templates.length();i++){JSONObject t=templates.optJSONObject(i);if(t==null||t.optString("name").isEmpty()||t.optString("name").length()>500||t.optString("category").isEmpty()||t.optLong("amount",-1)<0)throw new IllegalArgumentException("קיצור לא תקין");}}
  JSONArray cards=d.optJSONArray("homeCards");if(cards!=null){Set<String> seen=new HashSet<>();for(int i=0;i<cards.length();i++)if(!Arrays.asList("summary","graph","charges","recent","export","insights").contains(cards.optString(i))||!seen.add(cards.optString(i)))throw new IllegalArgumentException("סדר כרטיסיות לא תקין");if(!seen.contains("summary"))throw new IllegalArgumentException("יש להשאיר את הסיכום");}
 }
 static JSONObject budgets(Context c){JSONObject b=read(c).optJSONObject("budgets");return b==null?new JSONObject():b;}
 static JSONArray templates(Context c){JSONArray a=read(c).optJSONArray("templates");return a==null?new JSONArray():a;}
 static List<String> cards(Context c){JSONArray a=read(c).optJSONArray("homeCards");List<String> out=new ArrayList<>();if(a==null)return new ArrayList<>(Arrays.asList("summary","insights","graph","charges","recent","export"));for(int i=0;i<a.length();i++)out.add(a.optString(i));return out;}
 static void dismiss(Context c,String key){try{JSONObject d=read(c),items=d.optJSONObject("dismissed");if(items==null)items=new JSONObject();items.put(key,true);d.put("dismissed",items);write(c,d,true);}catch(JSONException e){throw new IllegalArgumentException(e);}}
 static boolean dismissed(Context c,String key){JSONObject d=read(c).optJSONObject("dismissed");return d!=null&&d.optBoolean(key);}
}
