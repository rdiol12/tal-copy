package com.tal.pocketflow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

/** Pure date and money rules; all stored amounts use integer agorot. */
public final class Finance {
    private Finance() {}
    public static long money(String text) {
        String s = text.trim().replace("₪", "").replace(" ", "");
        if (s.contains(",") && !s.contains(".")) {
            if (s.matches("[0-9]+,[0-9]{1,2}")) s=s.replace(',', '.');
            else if (s.matches("[0-9]{1,3}(,[0-9]{3})+")) s=s.replace(",", "");
            else throw new IllegalArgumentException("פורמט סכום לא תקין");
        } else if (s.contains(",")) {
            if (!s.matches("[0-9]{1,3}(,[0-9]{3})+\\.[0-9]{1,2}")) throw new IllegalArgumentException("פורמט סכום לא תקין");
            s=s.replace(",", "");
        }
        BigDecimal d=new BigDecimal(s).setScale(2, RoundingMode.UNNECESSARY);
        if(d.signum()<0) throw new IllegalArgumentException("יש להזין סכום חיובי");
        return d.movePointRight(2).longValueExact();
    }
    /** A literal amount or a sum of amounts, optionally prefixed with '='. */
    public static long sumMoney(String text){
        String expression=text.trim();if(expression.startsWith("="))expression=expression.substring(1).trim();
        if(expression.length()>500)throw new IllegalArgumentException("החישוב ארוך מדי");
        String[] parts=expression.split("\\+",-1);if(parts.length>100)throw new IllegalArgumentException("אפשר לחבר עד 100 סכומים");
        long total=0;
        try{for(String part:parts){if(!part.trim().matches("[0-9₪.,\\s]+"))throw new IllegalArgumentException("יש להזין סכומים עם סימן + ביניהם");total=Math.addExact(total,money(part));}}
        catch(ArithmeticException error){throw new IllegalArgumentException("הסכום גדול מדי או כולל יותר משתי ספרות אחרי הנקודה");}
        return total;
    }
    public static String decimal(long cents) { return BigDecimal.valueOf(cents, 2).toPlainString(); }
    public static LocalDate day(YearMonth m,int day) { return m.atDay(Math.min(Math.max(day,1),m.lengthOfMonth())); }
    /** Cycle named after its start month: closing 9 means Oct 10 through Nov 9. */
    public static LocalDate creditCycleStart(YearMonth month,int closing){
        if(closing<1||closing>31)throw new IllegalArgumentException("יום סגירת מחזור חייב להיות בין 1 ל־31");
        return closing==31?month.atDay(1):day(month,closing).plusDays(1);
    }
    public static LocalDate creditCycleEnd(YearMonth month,int closing){return creditCycleStart(month.plusMonths(1),closing).minusDays(1);}
    public static LocalDate due(LocalDate purchase, String type, int closing, int charge) {
        if(type.equals("debit")||type.equals("bank")||type.equals("cash")||type.equals("check")||type.equals("transfer")) return purchase;
        YearMonth cycle=YearMonth.from(purchase);
        if(purchase.isAfter(day(cycle,closing))) cycle=cycle.plusMonths(1);
        LocalDate close=day(cycle,closing), due=day(cycle,charge);
        if(!due.isAfter(close)) due=day(cycle.plusMonths(1),charge);
        return due;
    }
    public static long installment(long total,int count,int index) {
        if(count<1||index<0||index>=count) throw new IllegalArgumentException();
        return total/count+(index<total%count?1:0);
    }
    /** Remaining unrecorded liability, repaid on charge days strictly after the baseline date. */
    public static long openingRemaining(long opening,long monthly,LocalDate start,int charge,LocalDate asOf){
        if(asOf.isBefore(start))return 0;
        if(monthly<=0||opening==0)return opening;
        YearMonth first=YearMonth.from(start);
        if(!day(first,charge).isAfter(start))first=first.plusMonths(1);
        YearMonth last=YearMonth.from(asOf);
        if(asOf.isBefore(day(last,charge)))last=last.minusMonths(1);
        long count=java.time.temporal.ChronoUnit.MONTHS.between(first,last)+1;
        if(count<=0)return opening;
        return count>opening/monthly?0:opening-monthly*count;
    }
    public static long fixedPayment(long debt,long agreed) { return Math.min(Math.max(0,debt),Math.max(0,agreed)); }
    public static boolean supportsInstallments(String type){return type.equals("credit")||type.equals("fixed")||type.equals("check");}
    public static BigDecimal rate(String value){
        BigDecimal rate=new BigDecimal(value.trim().replace(',','.'));
        if(rate.signum()<=0||rate.compareTo(new BigDecimal("1000000"))>0||rate.scale()>8)throw new IllegalArgumentException("יש להזין שער המרה חיובי, עד 8 ספרות אחרי הנקודה");
        return rate;
    }
    public static long convert(long originalCents,String rate){return BigDecimal.valueOf(originalCents).multiply(rate(rate)).setScale(0,RoundingMode.HALF_UP).longValueExact();}
    public static String feePercent(String value) {
        BigDecimal percent;
        try {
            percent = new BigDecimal(value.trim().replace(',', '.'));
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("יש להזין עמלת מט״ח בין 0 ל־100 אחוזים");
        }
        if (percent.signum() < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0
                || percent.scale() > 4) {
            throw new IllegalArgumentException("יש להזין עמלת מט״ח בין 0 ל־100, עד 4 ספרות אחרי הנקודה");
        }
        return percent.stripTrailingZeros().toPlainString();
    }

    public static long convert(long originalCents, String rate, String feePercent) {
        BigDecimal multiplier = BigDecimal.ONE.add(new BigDecimal(feePercent(feePercent)).movePointLeft(2));
        return BigDecimal.valueOf(originalCents).multiply(rate(rate)).multiply(multiplier)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

}
