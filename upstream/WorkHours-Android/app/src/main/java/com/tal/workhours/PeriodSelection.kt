package com.tal.workhours

import java.time.YearMonth

/** Month in the picker is the END month shown in the header, never a shifted start month. */
fun periodEndingIn(ending: YearMonth, startDay: Int): PayPeriod {
    require(ending.year in 1900..2100 && startDay in 1..31)
    return periodFromMonth(if (startDay == 1) ending else ending.minusMonths(1), startDay)
}
