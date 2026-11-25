package com.tal.pocketflow

import compat.j.javaEntries
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Store.overview copies a map's entries, clears the map and reads the copies. On iOS, Kotlin's
 * HashMap entries stop working once the map changes, which crashed the app on launch; the
 * converted code therefore copies entries with javaEntries().
 */
class JavaEntriesTest {
    @Test fun entriesStayReadableAfterTheMapIsCleared() {
        val map = HashMap<String, Long>(); map["food"] = 30L; map["fuel"] = 50L
        val sorted = ArrayList(map.javaEntries()).sortedByDescending { it.value }
        map.clear()
        for (entry in sorted) map[entry.key] = entry.value
        assertEquals(listOf("fuel", "food"), sorted.map { it.key })
        assertEquals(mapOf("fuel" to 50L, "food" to 30L), map)
    }

    @Test fun setValueWritesThrough() {
        val map = HashMap<String, Long>(); map["a"] = 1L
        map.javaEntries().first().setValue(5L)
        assertEquals(5L, map["a"])
    }
}
