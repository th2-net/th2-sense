/*
 * Copyright 2022 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.sense.event.dsl

import java.util.Locale
import kotlin.jvm.internal.CallableReference
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty
import com.exactpro.th2.sense.event.content.EventContent
import com.exactpro.th2.sense.event.dsl.impl.AggregatedSimpleMatcher
import com.exactpro.th2.sense.event.dsl.impl.FilterSimpleMatcher
import com.exactpro.th2.sense.event.dsl.impl.InstanceSimpleMatcher
import com.exactpro.th2.sense.event.dsl.impl.matchAndLog


infix fun <T : Any> SimpleMatcher<T>.allOf(block: SimpleMatcher<T>.() -> Unit) {
    val matchers = AggregatedSimpleMatcher<T>().also(block).aggregated
    register("allOf") { value ->
        matchers.all { (name, match) -> match.matchAndLog(name, value, this) }
    }
}

infix fun <T : Any> SimpleMatcher<T>.anyOf(block: SimpleMatcher<T>.() -> Unit) {
    val matchers = AggregatedSimpleMatcher<T>().also(block).aggregated
    register("anyOf") { value ->
        matchers.any { (name, match) -> match.matchAndLog(name, value, this) }
    }
}

infix fun <T : Any> SimpleMatcher<T>.noneOf(block: SimpleMatcher<T>.() -> Unit) {
    val matchers = AggregatedSimpleMatcher<T>().also(block).aggregated
    register("noneOf") { value ->
        matchers.none { (name, match) -> match.matchAndLog(name, value, this) }
    }
}

infix fun <IN : Any, OUT> SimpleMatcher<IN>.get(function: IN.() -> OUT): SimpleMatcher<OUT> {
    val callName = when (function) {
        is KProperty<*> ->
            "value of property ${function.name}"

        is KFunction<*> ->
            "return value of ${function.name}"

        is CallableReference -> "value of ${function.propertyName}"
        else -> "get value"
    }
    val parent = this
    return object : SimpleMatcher<OUT> {
        override fun register(name: String, match: SimpleMatchFunction<OUT>) {
            parent.register(callName) {
                match.matchAndLog(name, it.function(), this)
            }
        }
    }
}

operator fun <K : Any, V : Any> SimpleMatcher<Map<K, V>>.get(key: K): SimpleMatcher<V?> {
    val parent = this
    return object : SimpleMatcher<V?> {
        override fun register(name: String, match: SimpleMatchFunction<V?>) {
            parent.register("get key '$key'") {
                match.matchAndLog(name, it[key], this)
            }
        }
    }
}

fun <T : Any> SimpleMatcher<T?>.isNotNull(): SimpleMatcher<T> {
    val parent = this
    return object : SimpleMatcher<T> {
        override fun register(name: String, match: SimpleMatchFunction<T>) {
            parent.register("isNotNull") {
                it?.let { match.matchAndLog(name, it, this) } ?: false
            }
        }
    }
}

infix fun <T> SimpleMatcher<T>.equal(value: T): Unit = register("equal") { it == value }
infix fun <T> SimpleMatcher<T>.notEqual(value: T): Unit = register("notEqual") { it != value }
infix fun <T : Any> SimpleMatcher<T>.`in`(values: Collection<T>): Unit = register("in") { it in values }
infix fun <T : Any> SimpleMatcher<T>.notIn(values: Collection<T>): Unit = register("notIn") { it !in values }
infix fun <T : Any> SimpleMatcher<T>.matches(function: SimpleMatchFunction<T>): Unit = register("matches", function)

//region Strings
infix fun SimpleMatcher<String>.matchRegex(regex: Regex): Unit = register("matchRegex") { regex.matches(it) }
infix fun SimpleMatcher<String>.contains(part: String): Unit = register("contains") { it.contains(part) }
infix fun SimpleMatcher<String>.startsWith(prefix: String): Unit = register("startsWith") { it.startsWith(prefix) }
infix fun SimpleMatcher<String>.endsWith(suffix: String): Unit = register("endsWith") { it.endsWith(suffix) }
infix fun SimpleMatcher<String>.equalIgnoreCase(value: String): Unit = register("equalIgnoreCase") { it.equals(value, ignoreCase = true) }
//endregion

//region Comparable
infix fun <T : Comparable<T>> SimpleMatcher<T>.lessThan(value: T): Unit = register("lessThan") { it < value }
infix fun <T : Comparable<T>> SimpleMatcher<T>.greaterThan(value: T): Unit = register("greaterThan") { it > value }
infix fun <T : Comparable<T>> SimpleMatcher<T>.lessOrEqualTo(value: T): Unit = register("lessOrEqualTo") { it <= value }
infix fun <T : Comparable<T>> SimpleMatcher<T>.greaterOrEqualTo(value: T): Unit = register("greaterOrEqualTo") { it >= value }
//endregion

//region Collections
fun <T : Any> SimpleMatcher<List<T>>.atIndex(index: Int): SimpleMatcher<T> {
    val parent = this
    return object : SimpleMatcher<T> {
        override fun register(name: String, match: SimpleMatchFunction<T>) {
            parent.register("at index $index") {
                if (index < it.size) {
                    match.matchAndLog(name, it[index], this)
                } else {
                    false
                }
            }
        }

    }
}

private val DEFAULT_FILTER: Any?.() -> Boolean = { true }
fun <T : Any> SimpleMatcher<List<T>>.first(filter: T.() -> Boolean = DEFAULT_FILTER): SimpleMatcher<T> {
    val parent = this
    val callName = if (filter === DEFAULT_FILTER) "first" else "first by filter"
    return object : SimpleMatcher<T> {
        override fun register(name: String, match: SimpleMatchFunction<T>) {
            parent.register(callName) { list ->
                list.firstOrNull(filter)?.let {
                    match.matchAndLog(name, it, this)
                } ?: false
            }
        }
    }
}

fun <T : Any> SimpleMatcher<List<T>>.last(filter: T.() -> Boolean = DEFAULT_FILTER): SimpleMatcher<T> {
    val parent = this
    val callName = if (filter === DEFAULT_FILTER) "last" else "last by filter"
    return object : SimpleMatcher<T> {
        override fun register(name: String, match: SimpleMatchFunction<T>) {
            parent.register(callName) { list ->
                list.lastOrNull(filter)?.let {
                    match.matchAndLog(name, it, this)
                } ?: false
            }
        }
    }
}
//endregion

//region EventContent
fun SimpleMatcher<List<EventContent>>.tables(): SimpleMatcher<List<EventContent.Table>> {
    return FilterSimpleMatcher("tables", this) { it.filterIsInstance<EventContent.Table>() }
}
fun SimpleMatcher<List<EventContent>>.verifications(): SimpleMatcher<List<EventContent.Verification>> {
    return FilterSimpleMatcher("verifications", this) { it.filterIsInstance<EventContent.Verification>() }
}

fun SimpleMatcher<List<EventContent>>.treeTables(): SimpleMatcher<List<EventContent.TreeTable>> {
    return FilterSimpleMatcher("treeTables", this) { it.filterIsInstance<EventContent.TreeTable>() }
}

fun SimpleMatcher<List<EventContent>>.messages(): SimpleMatcher<List<EventContent.Message>> {
    return FilterSimpleMatcher("messages", this) { it.filterIsInstance<EventContent.Message>() }
}

fun SimpleMatcher<List<EventContent>>.references(): SimpleMatcher<List<EventContent.Reference>> {
    return FilterSimpleMatcher("references", this) { it.filterIsInstance<EventContent.Reference>() }
}

fun SimpleMatcher<out EventContent>.isTable(): SimpleMatcher<EventContent.Table> = instanceSimpleMatcher()

fun SimpleMatcher<out EventContent>.isTreeTable(): SimpleMatcher<EventContent.TreeTable> = instanceSimpleMatcher()

fun SimpleMatcher<out EventContent>.isVerification(): SimpleMatcher<EventContent.Verification> = instanceSimpleMatcher()

fun SimpleMatcher<out EventContent>.isMessage(): SimpleMatcher<EventContent.Message> = instanceSimpleMatcher()

fun SimpleMatcher<out EventContent>.isReference(): SimpleMatcher<EventContent.Reference> = instanceSimpleMatcher()

fun SimpleMatcher<EventContent.Table.Row>.getColumn(column: String): SimpleMatcher<String?> {
    val parent = this
    return object : SimpleMatcher<String?> {
        override fun register(name: String, match: SimpleMatchFunction<String?>) {
            parent.register("get column '$column'") {
                match.matchAndLog(name, it[column], this)
            }
        }
    }
}
//endregion

private inline fun <reified T> SimpleMatcher<out Any>.instanceSimpleMatcher() = InstanceSimpleMatcher(this, T::class.java)

private val CallableReference.propertyName: String
    get() = "^get(.+)$".toRegex().find(name).let { match ->
        return when (match) {
            null -> name
            else -> match.groupValues[1].replaceFirstChar { it.lowercase(Locale.getDefault()) }
        }
    }