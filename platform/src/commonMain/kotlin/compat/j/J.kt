// Java-named helpers for code converted from Java by tools/j2k: the parts of
// java.lang / java.util that Kotlin does not have under the same name.
package compat.j

fun interface Runnable { fun run() }
fun interface Consumer<T> { fun accept(value: T) }
fun interface BiConsumer<T, U> { fun accept(first: T, second: U) }
fun interface IntConsumer { fun accept(value: Int) }
fun interface Supplier<T> { fun get(): T }
fun interface Function<T, R> { fun apply(value: T): R }
fun interface BiFunction<T, U, R> { fun apply(first: T, second: U): R }
fun interface Predicate<T> { fun test(value: T): Boolean }
fun interface Callable<V> { fun call(): V }

// ---- String ----
/** java.lang.String.trim: strips characters <= U+0020 only. */
fun String.javaTrim(): String = trim { it <= ' ' }
fun String.javaMatches(regex: String): Boolean = Regex(regex).matches(this)
fun String.javaReplaceAll(regex: String, replacement: String): String = Regex(regex).replace(this, javaReplacement(replacement))
fun String.javaReplaceFirst(regex: String, replacement: String): String = Regex(regex).replaceFirst(this, javaReplacement(replacement))
// Java and Kotlin share the $1 / \$ replacement syntax.
private fun javaReplacement(replacement: String): String = replacement
/** java.lang.String.split: regex separator; with limit 0 trailing empty strings are dropped. */
fun String.javaSplit(regex: String, limit: Int = 0): Array<String> {
    val pattern = Regex(regex)
    if (pattern.find(this) == null) return arrayOf(this)
    val parts = pattern.split(this, if (limit > 0) limit else 0)
    return (if (limit == 0) parts.dropLastWhile { it.isEmpty() } else parts).toTypedArray()
}
fun String.Companion.valueOf(value: Any?): String = value.toString()
fun String.Companion.valueOf(value: CharArray): String = value.concatToString()
fun String.Companion.join(separator: CharSequence, vararg parts: CharSequence?): String = parts.joinToString(separator)
fun String.Companion.join(separator: CharSequence, parts: Iterable<CharSequence?>): String = parts.joinToString(separator)
fun StringBuilder.setLength(length: Int) { if (length < this.length) deleteRange(length, this.length) else while (this.length < length) append('\u0000') }

// ---- boxed types ----
fun Int.Companion.parseInt(text: String): Int = text.toIntOrNull() ?: throw NumberFormatException("For input string: \"$text\"")
fun Int.Companion.valueOf(text: String): Int = parseInt(text)
fun Int.Companion.valueOf(value: Int): Int = value
fun Int.Companion.compare(a: Int, b: Int): Int = a.compareTo(b)
fun Int.Companion.toString(value: Int): String = value.toString()
fun Long.Companion.parseLong(text: String): Long = text.toLongOrNull() ?: throw NumberFormatException("For input string: \"$text\"")
fun Long.Companion.valueOf(text: String): Long = parseLong(text)
fun Long.Companion.valueOf(value: Long): Long = value
fun Long.Companion.compare(a: Long, b: Long): Int = a.compareTo(b)
fun Long.Companion.toString(value: Long): String = value.toString()
fun Long.Companion.signum(value: Long): Int = if (value > 0) 1 else if (value < 0) -1 else 0
fun Long.Companion.sum(a: Long, b: Long): Long = a + b
fun Long.Companion.max(a: Long, b: Long): Long = maxOf(a, b)
fun Long.Companion.min(a: Long, b: Long): Long = minOf(a, b)
fun Int.Companion.sum(a: Int, b: Int): Int = a + b
fun Int.Companion.max(a: Int, b: Int): Int = maxOf(a, b)
fun Int.Companion.min(a: Int, b: Int): Int = minOf(a, b)
fun Double.Companion.parseDouble(text: String): Double = text.trim().toDoubleOrNull() ?: throw NumberFormatException("For input string: \"$text\"")
fun Double.Companion.valueOf(text: String): Double = parseDouble(text)
fun Double.Companion.compare(a: Double, b: Double): Int = a.compareTo(b)
fun Double.Companion.isNaN(value: Double): Boolean = value.isNaN()
fun Double.Companion.isInfinite(value: Double): Boolean = value.isInfinite()
fun Boolean.Companion.parseBoolean(text: String?): Boolean = text.equals("true", ignoreCase = true)
fun Boolean.Companion.valueOf(value: Boolean): Boolean = value

// ---- collections ----
fun <K, V> Map<K, V>.javaGetOrDefault(key: K, default: V): V = if (containsKey(key)) getValue(key) else default
fun <K, V> MutableMap<K, V>.javaPutIfAbsent(key: K, value: V): V? = get(key) ?: run { put(key, value); null }
fun <K, V> MutableMap<K, V>.javaComputeIfAbsent(key: K, mapping: Function<K, V>): V = get(key) ?: mapping.apply(key).also { put(key, it) }
fun <K, V> MutableMap<K, V>.javaMerge(key: K, value: V, remapping: BiFunction<V, V, V>): V {
    val old = get(key)
    val next = if (old == null) value else remapping.apply(old, value)
    put(key, next)
    return next
}
fun <K, V> Map<K, V>.javaForEach(action: BiConsumer<K, V>) { for ((key, value) in this) action.accept(key, value) }
fun <T> Iterable<T>.javaForEach(action: Consumer<T>) { for (item in this) action.accept(item) }
fun <T> Iterator<T>.javaForEachRemaining(action: Consumer<T>) { while (hasNext()) action.accept(next()) }
fun <T> MutableList<T>.javaSort(comparator: Comparator<in T>?) { if (comparator != null) sortWith(comparator) else @Suppress("UNCHECKED_CAST") (this as MutableList<Comparable<Any>>).sort() }
fun <T> MutableCollection<T>.javaRemoveIf(filter: Predicate<T>): Boolean {
    var removed = false
    val each = iterator()
    while (each.hasNext()) if (filter.test(each.next())) { each.remove(); removed = true }
    return removed
}

object Arrays {
    fun <T> asList(vararg items: T): MutableList<T> = items.toMutableList()
    fun <T> sort(array: Array<T>, comparator: Comparator<in T>) = array.sortWith(comparator)
    fun sort(array: IntArray) = array.sort()
    fun sort(array: LongArray) = array.sort()
    fun fill(array: IntArray, value: Int) = array.fill(value)
    fun fill(array: LongArray, value: Long) = array.fill(value)
    fun fill(array: BooleanArray, value: Boolean) = array.fill(value)
    fun <T> fill(array: Array<T>, value: T) = array.fill(value)
    fun copyOf(array: IntArray, length: Int): IntArray = array.copyOf(length)
    fun copyOf(array: LongArray, length: Int): LongArray = array.copyOf(length)
    fun copyOf(array: ByteArray, length: Int): ByteArray = array.copyOf(length)
    fun copyOfRange(array: ByteArray, from: Int, to: Int): ByteArray = array.copyOfRange(from, to)
    fun equals(a: ByteArray?, b: ByteArray?): Boolean = if (a == null || b == null) a === b else a.contentEquals(b)
    fun equals(a: IntArray?, b: IntArray?): Boolean = if (a == null || b == null) a === b else a.contentEquals(b)
    fun toString(array: IntArray?): String = array?.contentToString() ?: "null"
    fun toString(array: Array<*>?): String = array?.contentToString() ?: "null"
    fun hashCode(array: IntArray?): Int = array?.contentHashCode() ?: 0
}

object Collections {
    fun <T> emptyList(): MutableList<T> = ArrayList()
    fun <K, V> emptyMap(): MutableMap<K, V> = LinkedHashMap()
    fun <T> emptySet(): MutableSet<T> = LinkedHashSet()
    fun <T> singletonList(item: T): MutableList<T> = arrayListOf(item)
    fun <T> unmodifiableList(list: MutableList<T>): MutableList<T> = list
    fun <T : Comparable<T>> sort(list: MutableList<T>) = list.sort()
    fun <T> sort(list: MutableList<T>, comparator: Comparator<in T>) = list.sortWith(comparator)
    fun <T> reverse(list: MutableList<T>) = list.reverse()
    fun <T> swap(list: MutableList<T>, i: Int, j: Int) { val t = list[i]; list[i] = list[j]; list[j] = t }
    fun <T> addAll(target: MutableCollection<T>, vararg items: T): Boolean = target.addAll(items)
    fun <T : Comparable<T>> max(items: Collection<T>): T = items.max()
    fun <T : Comparable<T>> min(items: Collection<T>): T = items.min()
}

object Objects {
    fun equals(a: Any?, b: Any?): Boolean = a == b
    fun hash(vararg values: Any?): Int = values.contentHashCode()
    fun hashCode(value: Any?): Int = value?.hashCode() ?: 0
    fun toString(value: Any?): String = value.toString()
    fun <T> requireNonNull(value: T?): T = value ?: throw NullPointerException()
    fun <T> requireNonNull(value: T?, message: String): T = value ?: throw NullPointerException(message)
    fun isNull(value: Any?): Boolean = value == null
    fun nonNull(value: Any?): Boolean = value != null
}

class StringJoiner(private val separator: CharSequence, private val prefix: CharSequence = "", private val suffix: CharSequence = "") {
    private val parts = ArrayList<String>()
    fun add(part: CharSequence?): StringJoiner { parts.add(part.toString()); return this }
    fun length(): Int = toString().length
    override fun toString(): String = parts.joinToString(separator, prefix, suffix)
}

// ---- sorted collections (Kotlin/Native has no TreeMap/TreeSet) ----

interface SortedSet<E> : MutableSet<E> {
    fun first(): E
    fun last(): E
}

/** Sorted set kept as an ordered list; fine for the small sets the app uses. */
class TreeSet<E>(private val comparator: Comparator<in E>? = null) : AbstractMutableSet<E>(), SortedSet<E> {
    private val items = ArrayList<E>()

    constructor(source: Collection<E>) : this(null) { addAll(source) }

    @Suppress("UNCHECKED_CAST")
    private fun compare(a: E, b: E): Int = comparator?.compare(a, b) ?: (a as Comparable<E>).compareTo(b)
    private fun find(element: E): Int {
        var low = 0; var high = items.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val c = compare(items[mid], element)
            if (c < 0) low = mid + 1 else if (c > 0) high = mid - 1 else return mid
        }
        return -(low + 1)
    }
    override val size: Int get() = items.size
    override fun add(element: E): Boolean { val i = find(element); if (i >= 0) return false; items.add(-(i + 1), element); return true }
    override fun contains(element: E): Boolean = find(element) >= 0
    override fun remove(element: E): Boolean { val i = find(element); if (i < 0) return false; items.removeAt(i); return true }
    override fun clear() = items.clear()
    override fun iterator(): MutableIterator<E> = items.iterator()
    override fun first(): E = if (items.isEmpty()) throw NoSuchElementException() else items[0]
    override fun last(): E = if (items.isEmpty()) throw NoSuchElementException() else items[items.size - 1]
    fun pollFirst(): E? = if (items.isEmpty()) null else items.removeAt(0)
    fun floor(element: E): E? { val i = find(element); return if (i >= 0) items[i] else (-(i + 1) - 1).let { if (it >= 0) items[it] else null } }
    fun ceiling(element: E): E? { val i = find(element); return if (i >= 0) items[i] else (-(i + 1)).let { if (it < items.size) items[it] else null } }
    fun lower(element: E): E? { val i = find(element); val at = (if (i >= 0) i else -(i + 1)) - 1; return if (at >= 0) items[at] else null }
    fun higher(element: E): E? { val i = find(element); val at = if (i >= 0) i + 1 else -(i + 1); return if (at < items.size) items[at] else null }
}

/** Sorted map kept as ordered parallel lists. */
class TreeMap<K, V>(private val comparator: Comparator<in K>? = null) : AbstractMutableMap<K, V>() {
    private val keyList = ArrayList<K>()
    private val valueList = ArrayList<V>()

    constructor(source: Map<out K, V>) : this(null) { putAll(source) }

    @Suppress("UNCHECKED_CAST")
    private fun compare(a: K, b: K): Int = comparator?.compare(a, b) ?: (a as Comparable<K>).compareTo(b)
    private fun find(key: K): Int {
        var low = 0; var high = keyList.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val c = compare(keyList[mid], key)
            if (c < 0) low = mid + 1 else if (c > 0) high = mid - 1 else return mid
        }
        return -(low + 1)
    }
    override val size: Int get() = keyList.size
    override fun put(key: K, value: V): V? {
        val i = find(key)
        if (i >= 0) { val old = valueList[i]; valueList[i] = value; return old }
        keyList.add(-(i + 1), key); valueList.add(-(i + 1), value); return null
    }
    override fun get(key: K): V? { val i = find(key); return if (i >= 0) valueList[i] else null }
    override fun containsKey(key: K): Boolean = find(key) >= 0
    override fun remove(key: K): V? { val i = find(key); if (i < 0) return null; keyList.removeAt(i); return valueList.removeAt(i) }
    override fun clear() { keyList.clear(); valueList.clear() }
    fun firstKey(): K = if (keyList.isEmpty()) throw NoSuchElementException() else keyList[0]
    fun lastKey(): K = if (keyList.isEmpty()) throw NoSuchElementException() else keyList[keyList.size - 1]
    fun floorKey(key: K): K? { val i = find(key); return if (i >= 0) keyList[i] else (-(i + 1) - 1).let { if (it >= 0) keyList[it] else null } }
    fun ceilingKey(key: K): K? { val i = find(key); return if (i >= 0) keyList[i] else (-(i + 1)).let { if (it < keyList.size) keyList[it] else null } }

    private inner class Entry(private val index: Int) : MutableMap.MutableEntry<K, V> {
        override val key: K get() = keyList[index]
        override val value: V get() = valueList[index]
        override fun setValue(newValue: V): V { val old = valueList[index]; valueList[index] = newValue; return old }
    }
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = object : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
            override val size: Int get() = keyList.size
            override fun add(element: MutableMap.MutableEntry<K, V>): Boolean { put(element.key, element.value); return true }
            override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> = object : MutableIterator<MutableMap.MutableEntry<K, V>> {
                private var next = 0
                override fun hasNext(): Boolean = next < keyList.size
                override fun next(): MutableMap.MutableEntry<K, V> = Entry(next++)
                override fun remove() { next--; keyList.removeAt(next); valueList.removeAt(next) }
            }
        }
}

// ---- java.util.concurrent.atomic ----
class AtomicLong(initial: Long = 0L) {
    private var value = initial
    fun get(): Long = value
    fun set(newValue: Long) { value = newValue }
    fun incrementAndGet(): Long = ++value
    fun getAndIncrement(): Long = value++
    fun addAndGet(delta: Long): Long { value += delta; return value }
    override fun toString(): String = value.toString()
}
class AtomicInteger(initial: Int = 0) {
    private var value = initial
    fun get(): Int = value
    fun set(newValue: Int) { value = newValue }
    fun incrementAndGet(): Int = ++value
    fun getAndIncrement(): Int = value++
    fun addAndGet(delta: Int): Int { value += delta; return value }
    override fun toString(): String = value.toString()
}
class AtomicBoolean(initial: Boolean = false) {
    private var value = initial
    fun get(): Boolean = value
    fun set(newValue: Boolean) { value = newValue }
    fun getAndSet(newValue: Boolean): Boolean { val old = value; value = newValue; return old }
    fun compareAndSet(expected: Boolean, newValue: Boolean): Boolean = if (value == expected) { value = newValue; true } else false
}
class AtomicReference<T>(initial: T? = null) {
    private var value: T? = initial
    fun get(): T? = value
    fun set(newValue: T?) { value = newValue }
}

// ---- java.util.Comparator statics and java.util.stream ----
fun interface ToLongFunction<T> { fun applyAsLong(value: T): Long }
fun interface ToIntFunction<T> { fun applyAsInt(value: T): Int }
fun interface ToDoubleFunction<T> { fun applyAsDouble(value: T): Double }

object Comparators {
    fun <T, U : Comparable<U>> comparing(key: Function<T, U>): Comparator<T> = Comparator { a, b -> key.apply(a).compareTo(key.apply(b)) }
    fun <T> comparingLong(key: ToLongFunction<T>): Comparator<T> = Comparator { a, b -> key.applyAsLong(a).compareTo(key.applyAsLong(b)) }
    fun <T> comparingInt(key: ToIntFunction<T>): Comparator<T> = Comparator { a, b -> key.applyAsInt(a).compareTo(key.applyAsInt(b)) }
    fun <T> comparingDouble(key: ToDoubleFunction<T>): Comparator<T> = Comparator { a, b -> key.applyAsDouble(a).compareTo(key.applyAsDouble(b)) }
    fun <T : Comparable<T>> naturalOrder(): Comparator<T> = Comparator { a, b -> a.compareTo(b) }
    fun <T : Comparable<T>> reverseOrder(): Comparator<T> = Comparator { a, b -> b.compareTo(a) }
}
fun <T> Comparator<T>.javaThenComparing(other: Comparator<in T>): Comparator<T> = Comparator { a, b -> compare(a, b).let { if (it != 0) it else other.compare(a, b) } }
fun <T, U : Comparable<U>> Comparator<T>.javaThenComparing(key: Function<T, U>): Comparator<T> = javaThenComparing(Comparators.comparing(key))

fun <T> Collection<T>.javaStream(): Stream<T> = Stream(toList())

/** Eager stand-in for java.util.stream.Stream. */
class Stream<T>(private val items: List<T>) {
    fun filter(predicate: Predicate<T>): Stream<T> = Stream(items.filter { predicate.test(it) })
    fun <R> map(mapper: Function<T, R>): Stream<R> = Stream(items.map { mapper.apply(it) })
    fun mapToLong(mapper: ToLongFunction<T>): LongStream = LongStream(items.map { mapper.applyAsLong(it) })
    fun mapToInt(mapper: ToIntFunction<T>): IntStream = IntStream(items.map { mapper.applyAsInt(it) })
    fun anyMatch(predicate: Predicate<T>): Boolean = items.any { predicate.test(it) }
    fun allMatch(predicate: Predicate<T>): Boolean = items.all { predicate.test(it) }
    fun noneMatch(predicate: Predicate<T>): Boolean = items.none { predicate.test(it) }
    fun sorted(comparator: Comparator<in T>): Stream<T> = Stream(items.sortedWith(comparator))
    fun distinct(): Stream<T> = Stream(items.distinct())
    fun limit(count: Long): Stream<T> = Stream(items.take(count.toInt()))
    fun count(): Long = items.size.toLong()
    fun forEach(action: Consumer<T>) = items.forEach { action.accept(it) }
    fun toList(): MutableList<T> = items.toMutableList()
}
class LongStream(private val items: List<Long>) {
    fun sum(): Long = items.sum()
    fun max(): Long? = items.maxOrNull()
    fun min(): Long? = items.minOrNull()
}
class IntStream(private val items: List<Int>) {
    fun sum(): Int = items.sum()
    fun max(): Int? = items.maxOrNull()
    fun min(): Int? = items.minOrNull()
}

/** java.lang.Thread for fire-and-forget background work. */
open class Thread(private val task: Runnable?, val name: String) {
    constructor(task: Runnable?) : this(task, "background")
    constructor() : this(null, "background")
    var isDaemon: Boolean = false
    var priority: Int = 5
    open fun run() { task?.run() }
    fun start() { compat.util.platformRunInBackground(name) { run() } }
    fun interrupt() {}
    val isInterrupted: Boolean get() = false
    companion object {
        fun sleep(millis: Long) {}
        fun currentThread(): Thread = Thread()
    }
}

/**
 * Marks a getX()/setX(v) function that is a real function, not a property accessor,
 * so the Java-to-Kotlin converter keeps the call instead of writing a property access.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class Fun
