package com.tal.pocketflow;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.*;
import java.util.*;

/** Local repository. Money is stored as cents; recurrence instances have stable unique keys. */
public final class Store extends SQLiteOpenHelper {
    public static class Account {
        long id,limit,payment,opening,openingPayment; boolean cycleConfigured; int closing,charge; String name,type,start,billingHistory="[]",fxFee="0";
        @Override public String toString(){return name;}
    }
    public static class Tx {
        long id,amount,account,rule,originalAmount,refundOf; String currency="ILS",fxFee="0",fxRate="1",fxDate="",fxSource="",firstDue=""; String date,category,merchant,note,tag,kind; int count,status;
    }
    public static class Rule {
        long id,amount,account,originalAmount; String currency="ILS",fxRate="1",fxFee="0",fxDate="",fxSource=""; String name,category,start,end; int active,interval=1,reminderDays=7; String renewal="";
    }
    public static class Charge {
        LocalDate date; long amount,account,tx; String name,kind="expense"; boolean projected;
        Charge(LocalDate d,long a,long ac,long t,String n,boolean p){date=d;amount=a;account=ac;tx=t;name=n;projected=p;}
    }
    private final Context context;
    public Store(Context c){super(c,"pocketflow.db",null,9);context=c.getApplicationContext();}
    @Override public void onConfigure(SQLiteDatabase db){db.setForeignKeyConstraintsEnabled(true);}
    @Override public void onCreate(SQLiteDatabase d){
        d.execSQL("CREATE TABLE accounts(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,type TEXT NOT NULL,closing INTEGER NOT NULL,charge INTEGER NOT NULL,credit_limit INTEGER NOT NULL,payment INTEGER NOT NULL,opening INTEGER NOT NULL,opening_payment INTEGER NOT NULL DEFAULT 0,billing_history TEXT NOT NULL DEFAULT '[]',cycle_configured INTEGER NOT NULL DEFAULT 0,start TEXT NOT NULL,fx_fee TEXT NOT NULL DEFAULT '0')");
        d.execSQL("CREATE TABLE rules(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,amount INTEGER NOT NULL,account INTEGER NOT NULL REFERENCES accounts(id),category TEXT NOT NULL,start TEXT NOT NULL,end TEXT NOT NULL,active INTEGER NOT NULL DEFAULT 1,interval_months INTEGER NOT NULL DEFAULT 1,reminder_days INTEGER NOT NULL DEFAULT 7,renewal TEXT NOT NULL DEFAULT '',currency TEXT NOT NULL DEFAULT 'ILS',original_amount INTEGER NOT NULL DEFAULT 0,fx_rate TEXT NOT NULL DEFAULT '1',fx_fee TEXT NOT NULL DEFAULT '0',fx_date TEXT NOT NULL DEFAULT '',fx_source TEXT NOT NULL DEFAULT '')");
        d.execSQL("CREATE TABLE transactions(id INTEGER PRIMARY KEY AUTOINCREMENT,date TEXT NOT NULL,amount INTEGER NOT NULL CHECK(amount>0),category TEXT NOT NULL,merchant TEXT NOT NULL,note TEXT NOT NULL,tag TEXT NOT NULL,account INTEGER NOT NULL REFERENCES accounts(id),kind TEXT NOT NULL,count INTEGER NOT NULL CHECK(count BETWEEN 1 AND 120),status INTEGER NOT NULL,rule INTEGER REFERENCES rules(id),currency TEXT NOT NULL DEFAULT 'ILS',original_amount INTEGER NOT NULL DEFAULT 0,fx_rate TEXT NOT NULL DEFAULT '1',fx_date TEXT NOT NULL DEFAULT '',fx_source TEXT NOT NULL DEFAULT '',first_due TEXT NOT NULL DEFAULT '',refund_of INTEGER NOT NULL DEFAULT 0,fx_fee TEXT NOT NULL DEFAULT '0',UNIQUE(rule,date))");
        d.execSQL("CREATE INDEX tx_date ON transactions(date)");
        d.execSQL("CREATE TABLE undo_action(id INTEGER PRIMARY KEY CHECK(id=1),payload TEXT NOT NULL)");
        d.execSQL("INSERT INTO accounts(name,type,closing,charge,credit_limit,payment,opening,start) VALUES('מזומן','cash',31,10,0,0,0,'"+LocalDate.now().withDayOfMonth(1)+"')");
    }
    @Override public void onOpen(SQLiteDatabase db){super.onOpen(db);if(!db.isReadOnly())ensureBankTransfer(db);}
    private void ensureBankTransfer(SQLiteDatabase db){
        db.execSQL("INSERT INTO accounts(name,type,closing,charge,credit_limit,payment,opening,start) SELECT 'העברה בנקאית','transfer',31,1,0,0,0,? WHERE NOT EXISTS(SELECT 1 FROM accounts WHERE type='transfer')",new Object[]{LocalDate.now().withDayOfMonth(1).toString()});
    }
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next){
        if (old < 9) {
            db.execSQL("ALTER TABLE rules ADD COLUMN currency TEXT NOT NULL DEFAULT 'ILS'");
            db.execSQL("ALTER TABLE rules ADD COLUMN original_amount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE rules ADD COLUMN fx_rate TEXT NOT NULL DEFAULT '1'");
            db.execSQL("ALTER TABLE rules ADD COLUMN fx_fee TEXT NOT NULL DEFAULT '0'");
            db.execSQL("ALTER TABLE rules ADD COLUMN fx_date TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE rules ADD COLUMN fx_source TEXT NOT NULL DEFAULT ''");
            db.execSQL("UPDATE rules SET original_amount=amount");
        }
        if (old < 8) {
            db.execSQL("ALTER TABLE accounts ADD COLUMN fx_fee TEXT NOT NULL DEFAULT '0'");
            db.execSQL("ALTER TABLE transactions ADD COLUMN fx_fee TEXT NOT NULL DEFAULT '0'");
        }
        if(old<2){
            db.execSQL("ALTER TABLE transactions ADD COLUMN currency TEXT NOT NULL DEFAULT 'ILS'");
            db.execSQL("ALTER TABLE transactions ADD COLUMN original_amount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE transactions ADD COLUMN fx_rate TEXT NOT NULL DEFAULT '1'");
            db.execSQL("ALTER TABLE transactions ADD COLUMN fx_date TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE transactions ADD COLUMN fx_source TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE transactions ADD COLUMN first_due TEXT NOT NULL DEFAULT ''");
            db.execSQL("UPDATE transactions SET original_amount=amount");
        }
        if(old<3)db.execSQL("ALTER TABLE accounts ADD COLUMN opening_payment INTEGER NOT NULL DEFAULT 0");
        if(old<4)db.execSQL("ALTER TABLE accounts ADD COLUMN billing_history TEXT NOT NULL DEFAULT '[]'");
        if(old<5)db.execSQL("ALTER TABLE accounts ADD COLUMN cycle_configured INTEGER NOT NULL DEFAULT 0");
        if(old<7){db.execSQL("ALTER TABLE transactions ADD COLUMN refund_of INTEGER NOT NULL DEFAULT 0");db.execSQL("CREATE TABLE undo_action(id INTEGER PRIMARY KEY CHECK(id=1),payload TEXT NOT NULL)");}
        if(old<6){db.execSQL("ALTER TABLE rules ADD COLUMN interval_months INTEGER NOT NULL DEFAULT 1");db.execSQL("ALTER TABLE rules ADD COLUMN reminder_days INTEGER NOT NULL DEFAULT 7");db.execSQL("ALTER TABLE rules ADD COLUMN renewal TEXT NOT NULL DEFAULT ''");}
    }
    public List<String> suggestions(boolean family){
        Set<String> values=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for(Tx t:transactions()){String value=(family?t.tag:t.merchant).trim();if(!value.isEmpty())values.add(value);}
        if(!family)for(Rule r:rules())if(!r.name.trim().isEmpty())values.add(r.name.trim());
        return new ArrayList<>(values);
    }
    public List<Account> accounts(){ refreshCache();if(accountCache==null)accountCache=readAccounts();List<Account> result=new ArrayList<>();for(Account a:accountCache)result.add(copy(a));return result;}
    private List<Account> readAccounts(){
        List<Account> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM accounts ORDER BY id",null)){
            while(c.moveToNext()){
                Account a=new Account();a.id=num(c,"id");a.name=str(c,"name");a.type=str(c,"type");a.closing=(int)num(c,"closing");a.charge=(int)num(c,"charge");a.limit=num(c,"credit_limit");a.payment=num(c,"payment");a.opening=num(c,"opening");a.openingPayment=num(c,"opening_payment");a.start=str(c,"start");a.billingHistory=str(c,"billing_history");a.fxFee=str(c,"fx_fee");a.cycleConfigured=num(c,"cycle_configured")==1;out.add(a);
            }
        }return out;
    }
    public Account account(long id){for(Account a:accounts())if(a.id==id)return a;throw new IllegalArgumentException("אמצעי התשלום לא קיים");}
    public void save(Account a){save(a,LocalDate.now());}
    void save(Account a,LocalDate changedOn){
        a.fxFee = Finance.feePercent(a.fxFee);
        if(a.id!=0){Account old=account(a.id);a.billingHistory=old.billingHistory;
            if(a.opening!=old.opening||!a.start.equals(old.start))a.billingHistory="[]";
            boolean policyChanged=!a.type.equals(old.type)||a.payment!=old.payment||a.openingPayment!=old.openingPayment||a.charge!=old.charge||a.closing!=old.closing||a.cycleConfigured!=old.cycleConfigured;
            if(policyChanged&&isCredit(a)&&isCredit(old)&&(old.opening>0||transactions().stream().anyMatch(t->t.account==a.id))){
                try{JSONArray history=new JSONArray(a.billingHistory);if(history.length()>0&&changedOn.isBefore(LocalDate.parse(history.getJSONObject(history.length()-1).getString("until"))))throw new IllegalArgumentException("תאריך המכשיר קודם לשינוי החיוב האחרון");history.put(policySnapshot(old,changedOn));a.billingHistory=history.toString();}catch(org.json.JSONException e){throw new IllegalArgumentException("היסטוריית חיוב לא תקינה",e);}
            }
        }
        ContentValues v=new ContentValues();v.put("name",a.name);v.put("type",a.type);v.put("closing",a.closing);v.put("charge",a.charge);v.put("credit_limit",a.limit);v.put("payment",a.payment);v.put("opening",a.opening);v.put("opening_payment",a.openingPayment);v.put("start",a.start);v.put("billing_history",a.billingHistory);v.put("cycle_configured",a.cycleConfigured?1:0);v.put("fx_fee",a.fxFee);
        remember("accounts",a.id);if(a.id==0){a.id=getWritableDatabase().insertOrThrow("accounts",null,v);rememberNew("accounts",a.id);}else getWritableDatabase().update("accounts",v,"id=?",new String[]{""+a.id});dirty();
    }
    /** Explicit repair for a full-to-fixed change saved before effective-date history existed. */
    public void correctLegacyFixedStart(Account a,LocalDate changedOn,int previousClosing){
        Account current=account(a.id);if(!current.type.equals("fixed")||!current.billingHistory.equals("[]"))throw new IllegalArgumentException("אפשר לתקן רק כרטיס בחיוב קבוע ללא היסטוריית שינוי");
        if(changedOn.isBefore(LocalDate.parse(current.start))||changedOn.isAfter(LocalDate.now())||previousClosing<1||previousClosing>31)throw new IllegalArgumentException("יש לבחור תאריך מעבר שבין תאריך היתרה להיום ויום סגירה בין 1 ל־31");
        try{Account previous=policy(current,policySnapshot(current,changedOn));previous.type="credit";previous.payment=0;previous.closing=previousClosing;String history=new JSONArray().put(policySnapshot(previous,changedOn)).toString();remember("accounts",a.id);ContentValues value=new ContentValues();value.put("billing_history",history);getWritableDatabase().update("accounts",value,"id=?",new String[]{""+a.id});a.billingHistory=history;dirty();}
        catch(org.json.JSONException e){throw new IllegalArgumentException(e);}
    }
    public List<Tx> transactions(){refreshCache();if(txCache==null)txCache=readTransactions();List<Tx> result=new ArrayList<>();for(Tx t:txCache)result.add(copy(t));return result;}
    private List<Tx> readTransactions(){
        List<Tx> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM transactions WHERE status<>2 ORDER BY date DESC,id DESC",null)){
            while(c.moveToNext()){
                Tx t=new Tx();t.id=num(c,"id");t.refundOf=num(c,"refund_of");t.date=str(c,"date");t.amount=num(c,"amount");t.account=num(c,"account");t.rule=num(c,"rule");t.category=str(c,"category");t.merchant=str(c,"merchant");t.note=str(c,"note");t.tag=str(c,"tag");t.kind=str(c,"kind");t.currency=str(c,"currency");t.originalAmount=num(c,"original_amount");t.fxRate=str(c,"fx_rate");t.fxFee=str(c,"fx_fee");t.fxDate=str(c,"fx_date");t.fxSource=str(c,"fx_source");t.firstDue=str(c,"first_due");t.count=(int)num(c,"count");t.status=(int)num(c,"status");out.add(t);
            }
        }return out;
    }
    public void save(Tx t){
        t.fxFee = Finance.feePercent(t.fxFee);
        validateRefund(t);
        if(t.amount<=0||t.count<1||t.count>120)throw new IllegalArgumentException("יש להזין סכום חיובי ומספר תשלומים בין 1 ל־120");
        LocalDate.parse(t.date);Account a=account(t.account);
        if(a.type.equals("fixed")&&LocalDate.parse(t.date).isBefore(LocalDate.parse(a.start)))throw new IllegalArgumentException("העסקה קודמת לתאריך יתרת הפתיחה של הכרטיס");
        if((!Finance.supportsInstallments(a.type)||!t.kind.equals("expense"))&&t.count>1)throw new IllegalArgumentException("תשלומים זמינים להוצאות באשראי ובהמחאות בלבד");
        if(!t.firstDue.isEmpty()&&LocalDate.parse(t.firstDue).isBefore(LocalDate.parse(t.date)))throw new IllegalArgumentException("התשלום הראשון אינו יכול להיות לפני תאריך העסקה");
        if(t.currency.equals("ILS")){t.originalAmount=t.amount;t.fxRate="1";t.fxFee="0";t.fxDate="";t.fxSource="";}
        if(!Arrays.asList("ILS","USD","EUR").contains(t.currency)||t.originalAmount<=0||Finance.convert(t.originalAmount,t.fxRate,t.fxFee)!=t.amount)throw new IllegalArgumentException("נתוני המרת המטבע אינם תקינים");
        ContentValues v=new ContentValues();v.put("refund_of",t.refundOf);v.put("date",t.date);v.put("amount",t.amount);v.put("currency",t.currency);v.put("original_amount",t.originalAmount);v.put("fx_rate",t.fxRate);v.put("fx_fee",t.fxFee);v.put("fx_date",t.fxDate);v.put("fx_source",t.fxSource);v.put("first_due",t.firstDue);v.put("category",t.category);v.put("merchant",t.merchant);v.put("note",t.note);v.put("tag",t.tag);v.put("account",t.account);v.put("kind",t.kind);v.put("count",t.count);v.put("status",t.status);if(t.rule>0)v.put("rule",t.rule);else v.putNull("rule");
        remember("transactions",t.id);if(t.id==0){t.id=getWritableDatabase().insertOrThrow("transactions",null,v);rememberNew("transactions",t.id);}else getWritableDatabase().update("transactions",v,"id=?",new String[]{""+t.id});dirty();
    }
    public void remove(Tx t){
        for(Tx r:transactions())if(r.refundOf==t.id)throw new IllegalArgumentException("יש למחוק את הזיכויים המקושרים לפני מחיקת הרכישה");remember("transactions",t.id);
        if(t.rule>0){ContentValues v=new ContentValues();v.put("status",2);getWritableDatabase().update("transactions",v,"id=?",new String[]{""+t.id});}
        else getWritableDatabase().delete("transactions","id=?",new String[]{""+t.id});dirty();
    }
    public List<Rule> rules(){refreshCache();if(ruleCache==null)ruleCache=readRules();List<Rule> result=new ArrayList<>();for(Rule r:ruleCache)result.add(copy(r));return result;}
    private List<Rule> readRules(){
        List<Rule> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM rules ORDER BY active DESC,name",null)){
            while(c.moveToNext()){Rule r=new Rule();r.id=num(c,"id");r.name=str(c,"name");r.amount=num(c,"amount");r.account=num(c,"account");r.category=str(c,"category");r.start=str(c,"start");r.end=str(c,"end");r.active=(int)num(c,"active");r.interval=(int)num(c,"interval_months");r.reminderDays=(int)num(c,"reminder_days");r.renewal=str(c,"renewal");r.currency=str(c,"currency");r.originalAmount=num(c,"original_amount");r.fxRate=str(c,"fx_rate");r.fxFee=str(c,"fx_fee");r.fxDate=str(c,"fx_date");r.fxSource=str(c,"fx_source");out.add(r);}
        }return out;
    }
    public void save(Rule r){
        if ("ILS".equals(r.currency)) {
            r.originalAmount=r.amount;r.fxRate="1";r.fxFee="0";r.fxDate="";r.fxSource="";
        }
        validateRuleCurrency(r);
        if(r.amount<=0)throw new IllegalArgumentException("יש להזין סכום חיובי");if(!Arrays.asList(1,3,12).contains(r.interval)||r.reminderDays<0||r.reminderDays>90)throw new IllegalArgumentException("תדירות או תזכורת לא תקינות");if(!r.renewal.isEmpty())LocalDate.parse(r.renewal);
        LocalDate s=LocalDate.parse(r.start);if(!r.end.trim().isEmpty()&&LocalDate.parse(r.end).isBefore(s))throw new IllegalArgumentException("תאריך הסיום קודם לתאריך ההתחלה");
        Account a=account(r.account);if(a.type.equals("cash")||a.type.equals("check"))throw new IllegalArgumentException("יש לבחור חשבון בנק או כרטיס");
        if(a.type.equals("fixed")&&s.isBefore(LocalDate.parse(a.start)))throw new IllegalArgumentException("הוראת הקבע קודמת ליתרת הפתיחה של הכרטיס");
        ContentValues v=new ContentValues();v.put("name",r.name);v.put("amount",r.amount);v.put("account",r.account);v.put("category",r.category);v.put("start",r.start);v.put("end",r.end);v.put("active",r.active);v.put("interval_months",r.interval);v.put("reminder_days",r.reminderDays);v.put("renewal",r.renewal);v.put("currency",r.currency);v.put("original_amount",r.originalAmount);v.put("fx_rate",r.fxRate);v.put("fx_fee",r.fxFee);v.put("fx_date",r.fxDate);v.put("fx_source",r.fxSource);
        remember("rules",r.id);if(r.id==0){r.id=getWritableDatabase().insertOrThrow("rules",null,v);rememberNew("rules",r.id);}else getWritableDatabase().update("rules",v,"id=?",new String[]{""+r.id});dirty();
    }
    private void validateRuleCurrency(Rule r) {
        Finance.feePercent(r.fxFee);
        if (!Arrays.asList("ILS","USD","EUR").contains(r.currency) || r.originalAmount<=0
                || Finance.convert(r.originalAmount,r.fxRate,r.fxFee)!=r.amount
                || ("ILS".equals(r.currency) && (!"0".equals(Finance.feePercent(r.fxFee))
                    || Finance.rate(r.fxRate).compareTo(java.math.BigDecimal.ONE)!=0))) {
            throw new IllegalArgumentException("נתוני המרת המטבע בהוראת הקבע אינם תקינים");
        }
    }

    long expectedRuleAmount(Rule r) {
        return "ILS".equals(r.currency) ? r.amount
                : Finance.convert(r.originalAmount,r.fxRate,account(r.account).fxFee);
    }

    /** Rebuild unconfirmed occurrences after an edit, retaining actuals and explicit skips. */
    public void updateRule(Rule rule,YearMonth visibleMonth){
        if(rule.id<=0)throw new IllegalArgumentException("הוראת הקבע אינה קיימת");
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
            YearMonth through=visibleMonth;
            try(Cursor c=d.rawQuery("SELECT MAX(date) FROM transactions WHERE rule=?",new String[]{""+rule.id})){if(c.moveToFirst()&&!c.isNull(0)){YearMonth last=YearMonth.from(LocalDate.parse(c.getString(0)));if(last.isAfter(through))through=last;}}
            save(rule);rememberWhere("transactions","rule=? AND status=0",new String[]{""+rule.id});d.delete("transactions","rule=? AND status=0",new String[]{""+rule.id});dirty();ensureMonth(through);d.setTransactionSuccessful();
        }finally{d.endTransaction();}
    }
    public void setRuleActive(Rule r,boolean active){
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
            r.active=active?1:0;save(r);
            if(!active){rememberWhere("transactions","rule=? AND status=0 AND date>=?",new String[]{""+r.id,LocalDate.now().toString()});d.delete("transactions","rule=? AND status=0 AND date>=?",new String[]{""+r.id,LocalDate.now().toString()});dirty();}
            d.setTransactionSuccessful();
        }finally{d.endTransaction();}
    }
    /** Creates only the requested month's instances; past actuals and cancelled instances are preserved. */
    public void ensureMonth(YearMonth month){
        refreshCache();if(Objects.equals(ensured.get(month),GEN.get()))return;SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
            for(Rule r:rules())if(r.active==1){
                Set<String> existingMonths=new HashSet<>();try(Cursor c=d.rawQuery("SELECT substr(date,1,7) FROM transactions WHERE rule=?",new String[]{""+r.id})){while(c.moveToNext())existingMonths.add(c.getString(0));}
                LocalDate start=LocalDate.parse(r.start);
                for(YearMonth instance=YearMonth.from(start);!instance.isAfter(month);instance=instance.plusMonths(r.interval)){
                LocalDate date=Finance.day(instance,start.getDayOfMonth());
                if(date.isBefore(start)||(!r.end.trim().isEmpty()&&date.isAfter(LocalDate.parse(r.end))))continue;
                if(existingMonths.contains(instance.toString()))continue;
                ContentValues v=new ContentValues();v.put("date",date.toString());v.put("amount",expectedRuleAmount(r));v.put("original_amount",r.originalAmount);v.put("currency",r.currency);v.put("fx_rate",r.fxRate);v.put("fx_fee","ILS".equals(r.currency)?"0":account(r.account).fxFee);v.put("fx_date",r.fxDate);v.put("fx_source",r.fxSource);v.put("category",r.category);v.put("merchant",r.name);v.put("note","");v.put("tag","");v.put("account",r.account);v.put("kind","expense");v.put("count",1);v.put("status",0);v.put("rule",r.id);
                long created=d.insertWithOnConflict("transactions",null,v,SQLiteDatabase.CONFLICT_IGNORE);if(created>0){rememberNew("transactions",created);dirty();}
                }
            }d.setTransactionSuccessful();
        }finally{d.endTransaction();}ensured.put(month,GEN.get());
    }
    public List<Tx> creditCycleTransactions(Account a,YearMonth month){
        LocalDate from=Finance.creditCycleStart(month,a.closing),through=Finance.creditCycleEnd(month,a.closing);List<Tx> out=new ArrayList<>();
        for(Tx t:transactions()){LocalDate date=LocalDate.parse(t.date);if(t.account==a.id&&!date.isBefore(from)&&!date.isAfter(through))out.add(t);}return out;
    }
    public LocalDate creditCyclePaymentDate(Account a,YearMonth month){return Finance.due(Finance.creditCycleEnd(month,a.closing),"credit",a.closing,a.charge);}
    ReportPeriod reporting(){return ReportPeriod.of(context);}
    void ensureReportPeriod(YearMonth label){ensureMonth(YearMonth.from(reporting().end(label)));}
    public List<Tx> month(YearMonth m){ReportPeriod p=reporting();String start=p.start(m).toString(),end=p.end(m).toString();List<Tx> out=new ArrayList<>();for(Tx t:transactions())if(t.date.compareTo(start)>=0&&t.date.compareTo(end)<=0)out.add(t);return out;}
    List<Charge> periodCharges(YearMonth label){ReportPeriod p=reporting();List<Charge> out=new ArrayList<>();for(YearMonth m=YearMonth.from(p.start(label));!m.isAfter(YearMonth.from(p.end(label)));m=m.plusMonths(1))for(Charge charge:charges(m))if(p.contains(label,charge.date))out.add(charge);out.sort(Comparator.comparing(c->c.date));return out;}

    public static final class Overview {
        long occasional,recurring,planned,refunds;
        final Map<String,Long> categories=new LinkedHashMap<>(),merchants=new LinkedHashMap<>(),payments=new LinkedHashMap<>(),families=new LinkedHashMap<>();
        long total(){return Math.addExact(occasional,recurring);}
    }
    /** Home overview includes each non-skipped expense once, whether actual or expected. */
    public Overview overview(YearMonth month){
        Overview result=new Overview();Set<Long> recurringPurchases=new HashSet<>();for(Tx source:transactions())if(source.rule>0)recurringPurchases.add(source.id);Map<Long,String> accountNames=new HashMap<>();for(Account account:accounts())accountNames.put(account.id,account.name);
        for(Tx t:month(month))if(isSpending(t)){
            long net=netExpense(t);if(t.kind.equals("refund"))result.refunds+=t.amount;
            if(t.rule>0||recurringPurchases.contains(t.refundOf))result.recurring=Math.addExact(result.recurring,net);else result.occasional=Math.addExact(result.occasional,net);
            if(t.status==0)result.planned=Math.addExact(result.planned,net);
            result.categories.put(t.category,Math.addExact(result.categories.getOrDefault(t.category,0L),net));
            String merchant=t.merchant.trim().isEmpty()?"ללא בית עסק":t.merchant.trim();result.merchants.put(merchant,Math.addExact(result.merchants.getOrDefault(merchant,0L),net));
            String payment=accountNames.get(t.account);result.payments.put(payment,Math.addExact(result.payments.getOrDefault(payment,0L),net));
            String family=t.tag.trim().isEmpty()?"ללא שיוך":t.tag.trim();result.families.put(family,Math.addExact(result.families.getOrDefault(family,0L),net));
        }
        List<Map.Entry<String,Long>> sorted=new ArrayList<>(result.categories.entrySet());sorted.sort((a,b)->Long.compare(b.getValue(),a.getValue()));result.categories.clear();for(Map.Entry<String,Long> entry:sorted)result.categories.put(entry.getKey(),entry.getValue());List<Map.Entry<String,Long>> merchantEntries=new ArrayList<>(result.merchants.entrySet());merchantEntries.sort((a,b)->Long.compare(b.getValue(),a.getValue()));result.merchants.clear();for(Map.Entry<String,Long> entry:merchantEntries)result.merchants.put(entry.getKey(),entry.getValue());List<Map.Entry<String,Long>> paymentEntries=new ArrayList<>(result.payments.entrySet());paymentEntries.sort((a,b)->Long.compare(b.getValue(),a.getValue()));result.payments.clear();for(Map.Entry<String,Long> entry:paymentEntries)result.payments.put(entry.getKey(),entry.getValue());List<Map.Entry<String,Long>> familyEntries=new ArrayList<>(result.families.entrySet());familyEntries.sort((a,b)->Long.compare(b.getValue(),a.getValue()));result.families.clear();for(Map.Entry<String,Long> entry:familyEntries)result.families.put(entry.getKey(),entry.getValue());return result;
    }
    public static final class SeriesData {
        final List<String> labels=new ArrayList<>();final Map<String,long[]> series=new LinkedHashMap<>();
    }
    public SeriesData cumulative(YearMonth selected,LocalDate today){
        ReportPeriod p=reporting();SeriesData data=new SeriesData();int days=p.elapsed(selected,today);long[] current=new long[days],previous=new long[days];YearMonth prior=selected.minusMonths(1);
        for(Tx t:transactions())if(t.status==1&&isSpending(t)){
            LocalDate date=LocalDate.parse(t.date);long value=netExpense(t);int index;
            if(p.contains(selected,date)){index=(int)java.time.temporal.ChronoUnit.DAYS.between(p.start(selected),date);if(index<days)current[index]=Math.addExact(current[index],value);}
            else if(p.contains(prior,date)){index=(int)java.time.temporal.ChronoUnit.DAYS.between(p.start(prior),date);if(index<days)previous[index]=Math.addExact(previous[index],value);}
        }
        for(int n=0;n<days;n++){data.labels.add(""+(n+1));if(n>0){current[n]=Math.addExact(current[n],current[n-1]);previous[n]=Math.addExact(previous[n],previous[n-1]);}}
        data.series.put("החודש הנבחר",current);data.series.put("החודש הקודם",previous);return data;
    }
    public SeriesData incomeVsExpenses(YearMonth selected){
        SeriesData data=new SeriesData();long[] income=new long[6],expenses=new long[6];for(int n=0;n<6;n++){YearMonth period=selected.minusMonths(5-n);data.labels.add(period.getMonthValue()+"/"+(period.getYear()%100));income[n]=total(period,"income");expenses[n]=total(period,"expense");}data.series.put("הכנסות",income);data.series.put("הוצאות",expenses);return data;
    }
    public long total(YearMonth m,String kind){long s=0;for(Tx t:month(m))if(t.status==1&&(t.kind.equals(kind)||(kind.equals("expense")&&t.kind.equals("refund"))))s+=kind.equals("expense")?netExpense(t):t.amount;return s;}
    public Map<String,Long> categories(YearMonth m){Map<String,Long> sums=new LinkedHashMap<>();for(Tx t:month(m))if(t.status==1&&isSpending(t))sums.put(t.category,sums.getOrDefault(t.category,0L)+netExpense(t));List<Map.Entry<String,Long>> sorted=new ArrayList<>(sums.entrySet());sorted.sort((a,b)->Long.compare(b.getValue(),a.getValue()));sums.clear();for(Map.Entry<String,Long> e:sorted)sums.put(e.getKey(),e.getValue());return sums;}
    public List<Charge> charges(YearMonth month){
        List<Charge> out=new ArrayList<>();List<Tx> txs=transactions();
        for(Account a:accounts()){
            if(!a.billingHistory.equals("[]")){
                for(LocalDate due:billingDates(a,month.atDay(1).minusDays(1),month.atEndOfMonth())){
                    long paid=creditState(a,due,txs,true).paid;
                    if(paid>0)out.add(new Charge(due,paid,a.id,0,a.name+" · פירעון אשראי משוער",true));
                }
                continue;
            }
            if(a.type.equals("fixed")&&a.cycleConfigured){
                LocalDate due=Finance.day(month,a.charge);long paid=cycleFixedState(a,due,txs,true).paid;
                if(paid>0)out.add(new Charge(due,paid,a.id,0,a.name+" · חיוב קבוע משוער",true));
            }else if(a.type.equals("fixed")){
                YearMonth start=YearMonth.from(LocalDate.parse(a.start));if(month.isBefore(start))continue;
                long balance=a.opening;
                for(YearMonth m=start;!m.isAfter(month);m=m.plusMonths(1)){
                    LocalDate due=Finance.day(m,a.charge);
                    for(Tx t:txs)if(t.account==a.id)balance+=fixedAccrual(t,m,true,due);
                    long payment=Finance.fixedPayment(balance,a.payment);balance-=payment;
                    if(m.equals(month)&&payment>0)out.add(new Charge(due,payment,a.id,0,a.name+" · חיוב קבוע משוער",true));
                    for(Tx t:txs)if(t.account==a.id)balance+=fixedAccrual(t,m,false,due);
                }
            }else{
                if(a.type.equals("credit")&&a.openingPayment>0){
                    LocalDate due=Finance.day(month,a.charge),start=LocalDate.parse(a.start);
                    long paid=Finance.openingRemaining(a.opening,a.openingPayment,start,a.charge,due.minusDays(1))-Finance.openingRemaining(a.opening,a.openingPayment,start,a.charge,due);
                    if(paid>0)out.add(new Charge(due,paid,a.id,0,a.name+" · פירעון ניצול קודם",true));
                }
                for(Tx t:txs)if(t.account==a.id){
                    LocalDate date=!t.firstDue.isEmpty()?LocalDate.parse(t.firstDue):t.kind.equals("income")?LocalDate.parse(t.date):Finance.due(LocalDate.parse(t.date),a.type,a.closing,a.charge);
                    int index=(int)java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.from(date),month);
                    if(index>=0&&index<t.count){LocalDate due=Finance.day(month,a.type.equals("credit")&&t.firstDue.isEmpty()&&!t.kind.equals("income")?a.charge:date.getDayOfMonth());long amount=Finance.installment(t.amount,t.count,index);if(!t.kind.equals("expense"))amount=-amount;Charge charge=new Charge(due,amount,a.id,t.id,t.merchant.trim().isEmpty()?t.category:t.merchant,t.status==0);charge.kind=t.kind;out.add(charge);}
                }
            }
        }
        out.sort(Comparator.comparing(c->c.date));return out;
    }
    /** Estimated occupied credit. Opening amounts are liabilities, never expenses. */
    public long creditUsage(Account a,LocalDate asOf){
        if(!isCredit(a))return 0;
        return a.billingHistory.equals("[]")?legacyCreditUsage(a,asOf,transactions(),false):creditState(a,asOf,transactions(),false).total();
    }
    private long legacyCreditUsage(Account a,LocalDate asOf,List<Tx> txs,boolean forecast){
        LocalDate start=LocalDate.parse(a.start);
        if(a.type.equals("fixed")){
            if(a.cycleConfigured)return cycleFixedState(a,asOf,txs,forecast).total();
            if(asOf.isBefore(start))return 0;
            java.util.TreeMap<LocalDate,Long> purchases=new java.util.TreeMap<>();
            for(Tx t:txs){LocalDate date=LocalDate.parse(t.date);if(t.account==a.id&&(forecast||t.status==1)&&isSpending(t)&&!date.isBefore(start)&&!date.isAfter(asOf))purchases.put(date,Math.addExact(purchases.getOrDefault(date,0L),netExpense(t)));}
            for(YearMonth m=YearMonth.from(start);!m.isAfter(YearMonth.from(asOf));m=m.plusMonths(1)){LocalDate due=Finance.day(m,a.charge);if(!due.isBefore(start)&&!due.isAfter(asOf))purchases.putIfAbsent(due,0L);}
            long balance=a.opening;for(Map.Entry<LocalDate,Long> event:purchases.entrySet()){balance=Math.addExact(balance,event.getValue());if(event.getKey().equals(Finance.day(YearMonth.from(event.getKey()),a.charge)))balance-=Finance.fixedPayment(balance,a.payment);}
            return balance;
        }
        long used=Finance.openingRemaining(a.opening,a.openingPayment,start,a.charge,asOf);
        for(Tx t:txs)if(t.account==a.id&&(forecast||t.status==1)&&isSpending(t)&&!LocalDate.parse(t.date).isAfter(asOf)){
            LocalDate first=t.firstDue.isEmpty()?Finance.due(LocalDate.parse(t.date),a.type,a.closing,a.charge):LocalDate.parse(t.firstDue);long remaining=t.amount;
            for(int n=0;n<t.count;n++){LocalDate due=Finance.day(YearMonth.from(first).plusMonths(n),t.firstDue.isEmpty()?a.charge:first.getDayOfMonth());if(!due.isAfter(asOf))remaining-=Finance.installment(t.amount,t.count,n);}
            used=Math.addExact(used,t.kind.equals("refund")?-remaining:remaining);
        }
        return used;
    }
    /** Liability stays occupied from purchase; only amounts whose cycle has closed can be repaid. */
    private long futureInstallments(Account a,LocalDate asOf,List<Tx> txs,boolean forecast){
        long reserved=0;for(Tx t:txs)if(t.account==a.id&&(forecast||t.status==1)&&isSpending(t)&&!LocalDate.parse(t.date).isAfter(asOf)){
            LocalDate first=t.firstDue.isEmpty()?Finance.due(LocalDate.parse(t.date),"credit",a.closing,a.charge):LocalDate.parse(t.firstDue);
            for(int n=0;n<t.count;n++)if(Finance.day(YearMonth.from(first).plusMonths(n),first.getDayOfMonth()).isAfter(asOf))reserved=Math.addExact(reserved,(t.kind.equals("refund")?-1:1)*Finance.installment(t.amount,t.count,n));
        }return reserved;
    }
    private CreditState cycleFixedState(Account a,LocalDate asOf,List<Tx> txs,boolean forecast){
        CreditState state=new CreditState();LocalDate start=LocalDate.parse(a.start);if(asOf.isBefore(start))return state;
        TreeMap<LocalDate,Long> events=new TreeMap<>();
        for(Tx t:txs){LocalDate date=LocalDate.parse(t.date);if(t.account==a.id&&(forecast||t.status==1)&&isSpending(t)&&!date.isBefore(start)&&!date.isAfter(asOf))events.put(date,Math.addExact(events.getOrDefault(date,0L),netExpense(t)));}
        for(YearMonth m=YearMonth.from(start);!m.isAfter(YearMonth.from(asOf));m=m.plusMonths(1)){LocalDate due=Finance.day(m,a.charge);if(!due.isBefore(start)&&!due.isAfter(asOf))events.putIfAbsent(due,0L);}
        state.recorded=a.opening;
        for(Map.Entry<LocalDate,Long> event:events.entrySet()){
            LocalDate date=event.getKey();state.recorded=Math.addExact(state.recorded,event.getValue());
            if(date.equals(Finance.day(YearMonth.from(date),a.charge))){long paid=Finance.fixedPayment(Math.max(0,state.recorded-futureInstallments(a,date,txs,forecast)),a.payment);state.recorded-=paid;if(date.equals(asOf))state.paid=paid;}
        }return state;
    }
    private boolean isCredit(Account a){return a.type.equals("credit")||a.type.equals("fixed");}
    private JSONObject policySnapshot(Account a,LocalDate until)throws org.json.JSONException{
        return new JSONObject().put("until",until.toString()).put("type",a.type).put("payment",a.payment).put("openingPayment",a.openingPayment).put("closing",a.closing).put("charge",a.charge).put("cycleConfigured",a.cycleConfigured);
    }
    private Account policy(Account a,JSONObject value)throws org.json.JSONException{
        Account p=new Account();p.id=a.id;p.start=a.start;p.opening=a.opening;p.name=a.name;p.type=value.getString("type");p.payment=value.getLong("payment");p.openingPayment=value.getLong("openingPayment");p.closing=value.getInt("closing");p.charge=value.getInt("charge");p.cycleConfigured=value.optBoolean("cycleConfigured",false);return p;
    }
    private JSONArray history(Account a){try{return new JSONArray(a.billingHistory);}catch(org.json.JSONException e){throw new IllegalArgumentException("היסטוריית חיוב לא תקינה",e);}}
    private void validateBillingHistory(Account a)throws org.json.JSONException{
        JSONArray h=history(a);if(!isCredit(a)&&h.length()>0)throw new IllegalArgumentException("היסטוריית אשראי בחשבון שאינו כרטיס");if(h.length()>10000)throw new IllegalArgumentException("היסטוריית חיוב ארוכה מדי");LocalDate previous=null;
        for(int i=0;i<h.length();i++){JSONObject row=h.getJSONObject(i);Account p=policy(a,row);LocalDate until=LocalDate.parse(row.getString("until"));if(!isCredit(p)||p.payment<0||p.openingPayment<0||p.closing<1||p.closing>31||p.charge<1||p.charge>31||(previous!=null&&until.isBefore(previous)))throw new IllegalArgumentException("היסטוריית חיוב לא תקינה");previous=until;}
    }
    private Account policyAt(Account a,LocalDate date){
        try{JSONArray h=history(a);for(int i=0;i<h.length();i++){JSONObject row=h.getJSONObject(i);if(!date.isAfter(LocalDate.parse(row.getString("until"))))return policy(a,row);}return a;}catch(org.json.JSONException e){throw new IllegalArgumentException(e);}
    }
    private SortedSet<LocalDate> billingDates(Account a,LocalDate after,LocalDate through){
        TreeSet<Integer> days=new TreeSet<>();days.add(a.charge);try{JSONArray h=history(a);for(int i=0;i<h.length();i++)days.add(h.getJSONObject(i).getInt("charge"));}catch(org.json.JSONException e){throw new IllegalArgumentException(e);}
        SortedSet<LocalDate> dates=new TreeSet<>();for(YearMonth m=YearMonth.from(after);!m.isAfter(YearMonth.from(through));m=m.plusMonths(1))for(int day:days){LocalDate date=Finance.day(m,day);if(date.isAfter(after)&&!date.isAfter(through)&&date.equals(Finance.day(m,policyAt(a,date).charge)))dates.add(date);}return dates;
    }
    private static final class CreditState {long opening,recorded,paid;long total(){return Math.addExact(opening,recorded);}}
    /** Replay future repayments under the policy effective on each date; never backdate a new policy. */
    private CreditState creditState(Account a,LocalDate asOf,List<Tx> txs,boolean forecast){
        try{
            JSONArray h=history(a);JSONObject first=h.getJSONObject(0);LocalDate cutoff=LocalDate.parse(first.getString("until"));Account initial=policy(a,first);CreditState state=new CreditState();
            LocalDate baseline=asOf.isBefore(cutoff)?asOf:cutoff;
            long total=legacyCreditUsage(initial,baseline,txs,forecast);
            LocalDate start=LocalDate.parse(a.start);
            state.opening=initial.type.equals("fixed")?Finance.openingRemaining(a.opening,initial.payment,start.minusDays(1),initial.charge,baseline):Finance.openingRemaining(a.opening,initial.openingPayment,start,initial.charge,baseline);
            if(baseline.isBefore(start))state.opening=0;
            state.opening=Math.max(0,Math.min(state.opening,total));state.recorded=total-state.opening;
            if(!asOf.isAfter(cutoff)){
                long before=legacyCreditUsage(initial,asOf.minusDays(1),txs,forecast),added=asOf.equals(start)?a.opening:0;
                for(Tx t:txs)if(t.account==a.id&&(forecast||t.status==1)&&isSpending(t)&&t.date.equals(asOf.toString())&&(!initial.type.equals("fixed")||!asOf.isBefore(start)))added=Math.addExact(added,netExpense(t));
                state.paid=Math.max(0,Math.addExact(before,added)-total);return state;
            }
            TreeMap<LocalDate,Long> events=new TreeMap<>();for(LocalDate due:billingDates(a,cutoff,asOf))events.put(due,0L);
            if(start.isAfter(cutoff)&&!start.isAfter(asOf))events.putIfAbsent(start,0L);
            for(Tx t:txs){LocalDate date=LocalDate.parse(t.date);if(t.account==a.id&&(forecast||t.status==1)&&isSpending(t)&&date.isAfter(cutoff)&&!date.isAfter(asOf))events.put(date,Math.addExact(events.getOrDefault(date,0L),netExpense(t)));}
            for(Map.Entry<LocalDate,Long> event:events.entrySet()){
                LocalDate date=event.getKey();if(date.equals(start)&&start.isAfter(cutoff))state.opening=a.opening;
                state.recorded=Math.addExact(state.recorded,event.getValue());Account active=policyAt(a,date);long payment=0;
                if(!date.isBefore(start)&&date.equals(Finance.day(YearMonth.from(date),active.charge))){
                    if(active.type.equals("fixed")){payment=Finance.fixedPayment(active.cycleConfigured?Math.max(0,state.total()-futureInstallments(active,date,txs,forecast)):state.total(),active.payment);long fromOpening=Math.min(state.opening,payment);state.opening-=fromOpening;state.recorded-=payment-fromOpening;}
                    else{
                        long reserve=0;for(Tx t:txs)if(t.account==a.id&&(forecast||t.status==1)&&isSpending(t)&&!LocalDate.parse(t.date).isAfter(date)){
                            LocalDate firstDue=t.firstDue.isEmpty()?Finance.due(LocalDate.parse(t.date),"credit",active.closing,active.charge):LocalDate.parse(t.firstDue);
                            for(int n=0;n<t.count;n++){LocalDate due=Finance.day(YearMonth.from(firstDue).plusMonths(n),t.firstDue.isEmpty()?active.charge:firstDue.getDayOfMonth());if(due.isAfter(date))reserve=Math.addExact(reserve,(t.kind.equals("refund")?-1:1)*Finance.installment(t.amount,t.count,n));}
                        }
                        long openingPaid=Math.min(state.opening,active.openingPayment),recordedPaid=Math.max(0,state.recorded-reserve);state.opening-=openingPaid;state.recorded-=recordedPaid;payment=Math.addExact(openingPaid,recordedPaid);
                    }
                }
                if(date.equals(asOf))state.paid=payment;
            }
            return state;
        }catch(org.json.JSONException e){throw new IllegalArgumentException("היסטוריית חיוב לא תקינה",e);}
    }
    /** Full lossless backup including skipped recurring occurrences. */
    public JSONObject backup() throws Exception{
        android.database.sqlite.SQLiteDatabase snapshot=getWritableDatabase();snapshot.beginTransactionNonExclusive();try{
        JSONObject root=new JSONObject();root.put("format","PocketFlow");root.put("version",9);root.put("createdAt",Instant.now().toString());
        android.content.SharedPreferences prefs=context.getSharedPreferences("prefs",Context.MODE_PRIVATE);
        root.put("settings",new JSONObject().put("income",prefs.getBoolean("income",false)).put("lastAccount",prefs.getLong("lastAccount",0)).put("appearance",prefs.getString("appearance","SYSTEM")).put("homeChart",prefs.getInt("homeChart",0)).put("homeChartSecond",prefs.getInt("homeChartSecond",0)).put("trendCategory",prefs.getString("trendCategory","")).put("chartAccount",prefs.getLong("chartAccount",0)).put(LauncherLayout.PREFERENCE,prefs.getString(LauncherLayout.PREFERENCE,"")).put("tools",Tools.read(context)));
        for(String table:new String[]{"accounts","rules","transactions"}){
            JSONArray rows=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM "+table+" ORDER BY id",null)){
                while(c.moveToNext()){JSONObject row=new JSONObject();for(int i=0;i<c.getColumnCount();i++)row.put(c.getColumnName(i),c.isNull(i)?JSONObject.NULL:c.getType(i)==Cursor.FIELD_TYPE_INTEGER?c.getLong(i):c.getString(i));rows.put(row);}
            }root.put(table,rows);
        }snapshot.setTransactionSuccessful();return root;
        }finally{snapshot.endTransaction();}
    }
    public void restore(JSONObject root) throws Exception{
        if(!root.getString("format").equals("PocketFlow")||(root.getInt("version")<1||root.getInt("version")>9))throw new IllegalArgumentException("קובץ הגיבוי אינו מתאים");
        JSONObject settings=root.optJSONObject("settings");
        boolean income=settings!=null&&settings.getBoolean("income");
        long lastAccount=settings==null?0:settings.getLong("lastAccount");
        String appearance=settings==null?"SYSTEM":settings.optString("appearance","SYSTEM");if(!Arrays.asList("SYSTEM","LIGHT","DARK").contains(appearance))throw new IllegalArgumentException("הגדרת מראה לא תקינה בגיבוי");
        JSONObject toolSettings=settings==null?new JSONObject():settings.optJSONObject("tools");if(toolSettings==null)toolSettings=new JSONObject();Tools.validate(toolSettings);
        int homeChart=settings==null?0:settings.optInt("homeChart",0);if(homeChart<0||homeChart>9)throw new IllegalArgumentException("בחירת גרף לא תקינה בגיבוי");
        int homeChartSecond=settings==null?0:settings.optInt("homeChartSecond",0);
        if(homeChartSecond<0||homeChartSecond>=ChartCatalog.LABELS.size())throw new IllegalArgumentException("בחירת הגרף השני אינה תקינה בגיבוי");
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
            d.delete("transactions",null,null);d.delete("rules",null,null);d.delete("accounts",null,null);dirty();
            for(String table:new String[]{"accounts","rules","transactions"}){
                JSONArray rows=root.getJSONArray(table);if(rows.length()>100000)throw new IllegalArgumentException("הגיבוי גדול מדי");
                Set<String> allowed=new HashSet<>();try(Cursor c=d.rawQuery("SELECT * FROM "+table+" LIMIT 0",null)){allowed.addAll(Arrays.asList(c.getColumnNames()));}
                for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(table.equals("rules")&&root.getInt("version")<9){row.put("currency","ILS");row.put("original_amount",row.getLong("amount"));row.put("fx_rate","1");row.put("fx_fee","0");row.put("fx_date","");row.put("fx_source","");}if(root.getInt("version")<8&&(table.equals("accounts")||table.equals("transactions")))row.put("fx_fee","0");if(table.equals("transactions")&&root.getInt("version")<7)row.put("refund_of",0);if(table.equals("rules")&&root.getInt("version")<6){row.put("interval_months",1);row.put("reminder_days",7);row.put("renewal","");}if(table.equals("accounts")&&root.getInt("version")<5)row.put("cycle_configured",0);if(table.equals("accounts")&&root.getInt("version")<4)row.put("billing_history","[]");if(table.equals("accounts")&&root.getInt("version")<3)row.put("opening_payment",0);if(table.equals("transactions")&&root.getInt("version")==1){row.put("currency","ILS");row.put("original_amount",row.getLong("amount"));row.put("fx_rate","1");row.put("fx_date","");row.put("fx_source","");row.put("first_due","");}if(table.equals("accounts")&&(row.getInt("cycle_configured")<0||row.getInt("cycle_configured")>1))throw new IllegalArgumentException("הגדרת מחזור לא תקינה בגיבוי");if(row.length()!=allowed.size())throw new IllegalArgumentException("מבנה גיבוי לא תקין");ContentValues v=new ContentValues();Iterator<String> keys=row.keys();while(keys.hasNext()){String key=keys.next();if(!allowed.contains(key))throw new IllegalArgumentException("שדה לא מוכר");Object value=row.get(key);if(value==JSONObject.NULL)v.putNull(key);else if(value instanceof Number)v.put(key,((Number)value).longValue());else v.put(key,value.toString());}d.insertOrThrow(table,null,v);dirty();}
            }
            Set<Long> accountIds=new HashSet<>();for(Account a:accounts()){
                Finance.feePercent(a.fxFee);accountIds.add(a.id);if(a.name.trim().isEmpty()||!Arrays.asList("cash","bank","debit","credit","fixed","check","transfer").contains(a.type)||a.closing<1||a.closing>31||a.charge<1||a.charge>31||a.opening<0||a.openingPayment<0||a.payment<0||a.limit<0)throw new IllegalArgumentException("כרטיס לא תקין בגיבוי");LocalDate.parse(a.start);validateBillingHistory(a);
            }
            if(accountIds.isEmpty())throw new IllegalArgumentException("אין חשבונות בגיבוי");
            for(Tx t:transactions()){Finance.feePercent(t.fxFee);if(t.currency.equals("ILS")&&!Finance.feePercent(t.fxFee).equals("0"))throw new IllegalArgumentException("עמלת מט״ח בשקלים אינה תקינה");LocalDate.parse(t.date);if(!t.firstDue.isEmpty())LocalDate.parse(t.firstDue);if(!Arrays.asList("ILS","USD","EUR").contains(t.currency)||t.originalAmount<=0||Finance.convert(t.originalAmount,t.fxRate,t.fxFee)!=t.amount)throw new IllegalArgumentException("המרה לא תקינה בגיבוי");if(t.amount<=0||!accountIds.contains(t.account)||!Arrays.asList("expense","income","refund").contains(t.kind)||t.status<0||t.status>1)throw new IllegalArgumentException("תנועה לא תקינה בגיבוי");}
            for(Rule r:rules()){validateRuleCurrency(r);if(!r.renewal.isEmpty())LocalDate.parse(r.renewal);LocalDate.parse(r.start);if(!r.end.trim().isEmpty())LocalDate.parse(r.end);if(r.amount<=0||!Arrays.asList(1,3,12).contains(r.interval)||r.reminderDays<0||r.reminderDays>90||!accountIds.contains(r.account))throw new IllegalArgumentException("הוראת קבע לא תקינה בגיבוי");}
            validateRefundSet(transactions());d.delete("undo_action",null,null);ensureBankTransfer(d);d.setTransactionSuccessful();
        }finally{d.endTransaction();dirty();}
        dirty();Tools.write(context,toolSettings,false);
        android.content.SharedPreferences.Editor preferences=context.getSharedPreferences("prefs",Context.MODE_PRIVATE).edit()
                .putBoolean("income",income).putLong("lastAccount",lastAccount).putString("appearance",appearance)
                .putInt("homeChart",homeChart).putInt("homeChartSecond",homeChartSecond).putString("trendCategory",settings==null?"":settings.optString("trendCategory",""))
                .putLong("chartAccount",settings==null?0:settings.optLong("chartAccount",0));
        if (settings != null && settings.has(LauncherLayout.PREFERENCE)) {
            LauncherLayout layout = new LauncherLayout(settings.optString(LauncherLayout.PREFERENCE));
            preferences.putString(LauncherLayout.PREFERENCE, layout.encode());
        }
        preferences.apply();
    }
    private long fixedAccrual(Tx t,YearMonth month,boolean before,LocalDate due){
        LocalDate first=LocalDate.parse(t.firstDue.isEmpty()?t.date:t.firstDue);
        int index=(int)java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.from(first),month);
        if(index<0||index>=t.count)return 0;
        LocalDate date=Finance.day(month,first.getDayOfMonth());if((!date.isAfter(due))!=before)return 0;
        long amount=Finance.installment(t.amount,t.count,index);return !t.kind.equals("expense")?-amount:amount;
    }

    private static final java.util.concurrent.atomic.AtomicLong GEN=new java.util.concurrent.atomic.AtomicLong();
    private long cachedGeneration=-1;private List<Tx> txCache;private List<Account> accountCache;private List<Rule> ruleCache;private final Map<YearMonth,Long> ensured=new HashMap<>();
    private void refreshCache(){long g=GEN.get();if(g!=cachedGeneration){txCache=null;accountCache=null;ruleCache=null;cachedGeneration=g;}}
    private void dirty(){GEN.incrementAndGet();refreshCache();}
    private static Tx copy(Tx x){Tx t=new Tx();t.id=x.id;t.amount=x.amount;t.account=x.account;t.rule=x.rule;t.refundOf=x.refundOf;t.originalAmount=x.originalAmount;t.currency=x.currency;t.fxFee=x.fxFee;t.fxRate=x.fxRate;t.fxDate=x.fxDate;t.fxSource=x.fxSource;t.firstDue=x.firstDue;t.date=x.date;t.category=x.category;t.merchant=x.merchant;t.note=x.note;t.tag=x.tag;t.kind=x.kind;t.count=x.count;t.status=x.status;return t;}
    private static Account copy(Account x){Account a=new Account();a.id=x.id;a.limit=x.limit;a.payment=x.payment;a.opening=x.opening;a.openingPayment=x.openingPayment;a.cycleConfigured=x.cycleConfigured;a.closing=x.closing;a.charge=x.charge;a.name=x.name;a.type=x.type;a.start=x.start;a.billingHistory=x.billingHistory;a.fxFee=x.fxFee;return a;}
    private static Rule copy(Rule x){Rule r=new Rule();r.id=x.id;r.amount=x.amount;r.account=x.account;r.name=x.name;r.category=x.category;r.start=x.start;r.end=x.end;r.active=x.active;r.interval=x.interval;r.reminderDays=x.reminderDays;r.renewal=x.renewal;r.currency=x.currency;r.originalAmount=x.originalAmount;r.fxRate=x.fxRate;r.fxFee=x.fxFee;r.fxDate=x.fxDate;r.fxSource=x.fxSource;return r;}
    static boolean isSpending(Tx t){return t.kind.equals("expense")||t.kind.equals("refund");}
    static long netExpense(Tx t){return t.kind.equals("refund")?-t.amount:t.kind.equals("expense")?t.amount:0;}
    Tx transaction(long id){for(Tx t:transactions())if(t.id==id)return t;throw new IllegalArgumentException("התנועה אינה קיימת");}
    long refunded(long id,long except){long total=0;for(Tx t:transactions())if(t.refundOf==id&&t.id!=except)total=Math.addExact(total,t.amount);return total;}
    private void validateRefund(Tx t){
        if(!Arrays.asList("expense","income","refund").contains(t.kind))throw new IllegalArgumentException("סוג תנועה לא תקין");
        if(t.kind.equals("refund")){Tx parent=transaction(t.refundOf);if(parent.id==t.id||!parent.kind.equals("expense")||parent.status!=1||parent.account!=t.account||!parent.category.equals(t.category)||t.rule!=0||t.count!=1||t.date.compareTo(parent.date)<0||Math.addExact(refunded(parent.id,t.id),t.amount)>parent.amount)throw new IllegalArgumentException("זיכוי חייב להיות מקושר לרכישה שבוצעה, באותו חשבון וקטגוריה, ועד ליתרה שלא זוכתה");}
        else {if(t.id==0&&t.refundOf==0)return;if(t.refundOf!=0)throw new IllegalArgumentException("קישור זיכוי לא תקין");for(Tx r:transactions())if(r.refundOf==t.id&&(t.kind.equals("income")||t.status!=1||t.account!=r.account||!t.category.equals(r.category)||t.date.compareTo(r.date)>0||refunded(t.id,0)>t.amount))throw new IllegalArgumentException("השינוי אינו תואם לזיכויים המקושרים לרכישה");}
    }
    private void validateRefundSet(List<Tx> txs){Map<Long,Tx> byId=new HashMap<>();Map<Long,Long> totals=new HashMap<>();for(Tx t:txs)byId.put(t.id,t);for(Tx t:txs){if(t.kind.equals("refund")){Tx p=byId.get(t.refundOf);if(p==null||p.id==t.id||!p.kind.equals("expense")||p.status!=1||p.account!=t.account||!p.category.equals(t.category)||t.date.compareTo(p.date)<0||t.rule!=0||t.count!=1||(!t.firstDue.isEmpty()&&t.firstDue.compareTo(t.date)<0))throw new IllegalArgumentException("קישור זיכוי לא תקין בגיבוי");long sum=Math.addExact(totals.getOrDefault(p.id,0L),t.amount);if(sum>p.amount)throw new IllegalArgumentException("סכום זיכויים גדול מהרכישה בגיבוי");totals.put(p.id,sum);}else if(t.refundOf!=0)throw new IllegalArgumentException("קישור זיכוי לא תקין בגיבוי");}}
    interface Change {void run() throws Exception;}
    private LinkedHashMap<String,JSONObject> undoRows;
    void edit(String label,Change change)throws Exception{
      if(undoRows!=null){change.run();return;}SQLiteDatabase d=getWritableDatabase();String toolsBefore=Tools.read(context).toString();d.beginTransaction();undoRows=new LinkedHashMap<>();boolean success=false;
      try{change.run();boolean prefsChanged=!toolsBefore.equals(Tools.read(context).toString());if(!undoRows.isEmpty()||prefsChanged){JSONObject payload=new JSONObject().put("label",label).put("rows",new JSONArray(undoRows.values()));if(prefsChanged)payload.put("toolsBefore",new JSONObject(toolsBefore));ContentValues v=new ContentValues();v.put("id",1);v.put("payload",payload.toString());d.insertWithOnConflict("undo_action",null,v,SQLiteDatabase.CONFLICT_REPLACE);}d.setTransactionSuccessful();success=true;}finally{undoRows=null;d.endTransaction();dirty();if(!success&&!toolsBefore.equals(Tools.read(context).toString()))Tools.write(context,new JSONObject(toolsBefore),false);}
    }
    private JSONObject row(Cursor c)throws Exception{JSONObject o=new JSONObject();for(int n=0;n<c.getColumnCount();n++)o.put(c.getColumnName(n),c.isNull(n)?JSONObject.NULL:c.getType(n)==Cursor.FIELD_TYPE_INTEGER?c.getLong(n):c.getString(n));return o;}
    private void remember(String table,long id){if(undoRows==null||id==0||undoRows.containsKey(table+":"+id))return;try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM "+table+" WHERE id=?",new String[]{""+id})){undoRows.put(table+":"+id,new JSONObject().put("table",table).put("id",id).put("before",c.moveToFirst()?row(c):JSONObject.NULL));}catch(Exception e){throw new IllegalArgumentException(e);}}
    private void rememberNew(String table,long id){if(undoRows==null)return;try{undoRows.putIfAbsent(table+":"+id,new JSONObject().put("table",table).put("id",id).put("before",JSONObject.NULL));}catch(Exception e){throw new IllegalArgumentException(e);}}
    private void rememberWhere(String table,String where,String[] args){if(undoRows==null)return;try(Cursor c=getReadableDatabase().query(table,new String[]{"id"},where,args,null,null,null)){while(c.moveToNext())remember(table,c.getLong(0));}}
    String undoLabel(){try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM undo_action WHERE id=1",null)){return c.moveToFirst()?new JSONObject(c.getString(0)).getString("label"):"";}catch(Exception e){return "";}}
    void undo()throws Exception{SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{JSONObject payload;try(Cursor c=d.rawQuery("SELECT payload FROM undo_action WHERE id=1",null)){if(!c.moveToFirst())return;payload=new JSONObject(c.getString(0));}JSONArray rows=payload.getJSONArray("rows");
      for(String table:new String[]{"transactions","rules","accounts"})for(int i=rows.length()-1;i>=0;i--){JSONObject item=rows.getJSONObject(i);if(table.equals(item.getString("table"))&&item.isNull("before")){if(table.equals("rules"))d.delete("transactions","rule=? AND status=0",new String[]{""+item.getLong("id")});d.delete(table,"id=?",new String[]{""+item.getLong("id")});}}
      for(String table:new String[]{"accounts","rules","transactions"})for(int i=0;i<rows.length();i++){JSONObject item=rows.getJSONObject(i);if(table.equals(item.getString("table"))&&!item.isNull("before")){JSONObject old=item.getJSONObject("before");ContentValues v=new ContentValues();Iterator<String> keys=old.keys();while(keys.hasNext()){String key=keys.next();Object val=old.get(key);if(val==JSONObject.NULL)v.putNull(key);else if(val instanceof Number)v.put(key,((Number)val).longValue());else v.put(key,val.toString());}if(d.update(table,v,"id=?",new String[]{""+item.getLong("id")})==0)d.insertOrThrow(table,null,v);}}
      if(payload.has("toolsBefore"))Tools.write(context,payload.getJSONObject("toolsBefore"),false);d.delete("undo_action",null,null);d.setTransactionSuccessful();}finally{d.endTransaction();dirty();}}

    private static String str(Cursor c,String name){return c.getString(c.getColumnIndexOrThrow(name));}
    private static long num(Cursor c,String name){return c.getLong(c.getColumnIndexOrThrow(name));}
}
