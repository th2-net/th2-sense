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

import com.exactpro.th2.sense.event.dsl.impl.AggregatedSimpleMatcher
import com.exactpro.th2.sense.event.dsl.impl.matchAndLog


infix fun <T : Any> SimpleMatcher<T>.allOf(block: SimpleMatcher<T>.() -> Unit) {
    val matchers = AggregatedSimpleMatcher<T>().also(block).aggregated
    register("allOf") { value ->
        matchers.all { (name, match) -> match.matchAndLog(name, value) }
    }
}

infix fun <T : Any> SimpleMatcher<T>.anyOf(block: SimpleMatcher<T>.() -> Unit) {
    val matchers = AggregatedSimpleMatcher<T>().also(block).aggregated
    register("anyOf") { value ->
        matchers.any { (name, match) -> match.matchAndLog(name, value) }
    }
}

infix fun <T : Any> SimpleMatcher<T>.noneOf(block: SimpleMatcher<T>.() -> Unit) {
    val matchers = AggregatedSimpleMatcher<T>().also(block).aggregated
    register("noneOf") { value ->
        matchers.none { (name, match) -> match.matchAndLog(name, value) }
    }
}

infix fun <T : Any> SimpleMatcher<T>.equal(value: T): Unit = register("equal") { it == value }
infix fun <T : Any> SimpleMatcher<T>.notEqual(value: T): Unit = register("notEqual") { it != value }
infix fun <T : Any> SimpleMatcher<T>.`in`(values: Collection<T>): Unit = register("in") { it in values }
infix fun <T : Any> SimpleMatcher<T>.notIn(values: Collection<T>): Unit = register("notIn") { it !in values }


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