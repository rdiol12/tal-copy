package com.tal.pocketflow;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.*;

/** Only a public GET is sent. No transactions, amounts or user identifiers leave the app. */
public final class ExchangeRates {
    public static final String URL="https://www.boi.org.il/PublicApi/GetExchangeRates";
    public static final class Rate {
        final String currency,value,date;
        Rate(String currency,String value,String date){this.currency=currency;this.value=value;this.date=date;}
    }
    private final SharedPreferences prefs;
    public ExchangeRates(Context c){prefs=c.getSharedPreferences("exchange-rates",Context.MODE_PRIVATE);}
    public Rate cached(String currency){String rate=prefs.getString(currency+".rate","");return rate.isEmpty()?null:new Rate(currency,rate,prefs.getString(currency+".date",""));}
    public static Map<String,Rate> parse(String json) throws Exception {
        Map<String,Rate> result=new HashMap<>();JSONArray rates=new JSONObject(json).getJSONArray("exchangeRates");
        for(int i=0;i<rates.length();i++){JSONObject row=rates.getJSONObject(i);String code=row.getString("key");if(!code.equals("USD")&&!code.equals("EUR"))continue;
            BigDecimal unit=new BigDecimal(row.getString("unit"));if(unit.signum()<=0)throw new IOException("Invalid rate unit");
            String value=Finance.rate(new BigDecimal(row.getString("currentExchangeRate")).divide(unit,8,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()).toPlainString();
            String date=row.getString("lastUpdate").substring(0,10);java.time.LocalDate.parse(date);result.put(code,new Rate(code,value,date));
        }
        if(result.size()!=2)throw new IOException("Missing exchange rates");return result;
    }
    public Map<String,Rate> fetch() throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new java.net.URL(URL).openConnection();connection.setConnectTimeout(10000);connection.setReadTimeout(10000);connection.setRequestProperty("Accept","application/json");connection.setRequestProperty("User-Agent","PocketFlow/0.2");
        try {
            if(connection.getResponseCode()!=200)throw new IOException("שירות השערים אינו זמין כעת");StringBuilder json=new StringBuilder();
            try(Reader in=new InputStreamReader(connection.getInputStream(),StandardCharsets.UTF_8)){char[] b=new char[4096];int n;while((n=in.read(b))!=-1){json.append(b,0,n);if(json.length()>1000000)throw new IOException("Unexpected response");}}
            Map<String,Rate> rates=parse(json.toString());SharedPreferences.Editor edit=prefs.edit();for(Rate r:rates.values())edit.putString(r.currency+".rate",r.value).putString(r.currency+".date",r.date);edit.apply();return rates;
        }finally{connection.disconnect();}
    }
}
