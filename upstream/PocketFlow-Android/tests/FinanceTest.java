import com.tal.pocketflow.Finance;
import java.time.*;
public class FinanceTest {
    private static int passed=0;
    private static void equal(Object actual,Object expected){if(!actual.equals(expected))throw new AssertionError("Expected "+expected+", got "+actual);passed++;}
    private static void reject(String s){try{Finance.money(s);throw new AssertionError("Accepted "+s);}catch(IllegalArgumentException|ArithmeticException ok){passed++;}}
    private static void rejectSum(String s){try{Finance.sumMoney(s);throw new AssertionError("Accepted sum "+s);}catch(IllegalArgumentException ok){passed++;}}
    public static void main(String[] args){
        equal(Finance.sumMoney("=67+22+33+55"),17700L);equal(Finance.sumMoney("67 + 22 + 33 + 55"),17700L);
        equal(Finance.sumMoney("=10.25+2,50"),1275L);equal(Finance.sumMoney("177"),17700L);
        rejectSum("=10+");rejectSum("=10++2");rejectSum("=10-2");rejectSum("=10*2");rejectSum("==10+2");rejectSum("=");rejectSum("=10.001+2");rejectSum("92233720368547758.07+0.01");
        equal(Finance.money("102.25"),10225L);
        equal(Finance.money("102,25"),10225L);
        equal(Finance.money("1,234.50"),123450L);
        equal(Finance.money("1,234"),123400L);
        reject("-1");reject("1.123");reject("1,2,3");reject("1,23.45");reject("");
        equal(Finance.due(LocalDate.of(2026,10,3),"credit",31,10),LocalDate.of(2026,11,10));
        equal(Finance.due(LocalDate.of(2026,10,12),"credit",15,25),LocalDate.of(2026,10,25));
        equal(Finance.due(LocalDate.of(2026,10,16),"credit",15,25),LocalDate.of(2026,11,25));
        equal(Finance.due(LocalDate.of(2026,10,15),"credit",15,15),LocalDate.of(2026,11,15));
        equal(Finance.due(LocalDate.of(2026,12,30),"credit",31,10),LocalDate.of(2027,1,10));
        equal(Finance.due(LocalDate.of(2026,2,28),"debit",31,10),LocalDate.of(2026,2,28));
        equal(Finance.day(YearMonth.of(2028,2),31),LocalDate.of(2028,2,29));
        equal(Finance.day(YearMonth.of(2027,2),31),LocalDate.of(2027,2,28));
        equal(Finance.day(YearMonth.of(2027,3),31),LocalDate.of(2027,3,31));
        long total=0;for(int i=0;i<3;i++)total+=Finance.installment(10000,3,i);equal(total,10000L);
        equal(Finance.installment(10000,3,0),3334L);equal(Finance.installment(10000,3,2),3333L);
        equal(Finance.fixedPayment(8000,10000),8000L);equal(Finance.fixedPayment(12000,10000),10000L);equal(Finance.fixedPayment(-500,10000),0L);
        equal(Finance.convert(1000,"3.114"),3114L);equal(Finance.convert(100,"3.125"),313L);equal(Finance.convert(100,"3,5"),350L);
        equal(Finance.supportsInstallments("fixed"),true);equal(Finance.supportsInstallments("check"),true);equal(Finance.supportsInstallments("debit"),false);
        System.out.println("PASS: "+passed+" date and money checks");
    }
}
