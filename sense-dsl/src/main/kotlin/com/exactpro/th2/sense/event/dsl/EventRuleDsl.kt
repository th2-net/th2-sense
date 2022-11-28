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

import com.exactpro.th2.sense.api.Event
import com.exactpro.th2.sense.api.EventType
import com.exactpro.th2.sense.api.ProcessorContext
import com.exactpro.th2.sense.event.dsl.impl.matchAllOfInternal
import com.exactpro.th2.sense.event.dsl.impl.matchAnyOfInternal
import com.exactpro.th2.sense.event.dsl.impl.matchNoneOfInternal

@DslMarker
annotation class RuleDsl

interface EventTypeMather {
    fun match(context: ProcessorContext, event: Event): Boolean
}

@RuleDsl
fun interface EventTypeSupplier {
    fun ProcessorContext.get(event: Event): EventType
}

@RuleDsl
class SimpleMatchContext(
    val context: ProcessorContext,
)

typealias SimpleMatchFunction<T> = SimpleMatchContext.(value: T) -> Boolean

@RuleDsl
interface SimpleMatcher<T> {
    fun register(name: String, match: SimpleMatchFunction<T>)
}

@RuleDsl
interface RelatedEventMatcherBuilder {
    fun register(matcher: EventTypeMather)
}

@RuleDsl
interface EventMatcherBuilder : AllOfEventMatcherBuilder

@RuleDsl
interface AllOfEventMatcherBuilder {
    fun register(name: String, matcher: EventTypeMather)
}

/**
 * It is a builder class to create your rules of mapping events to particular type.
 *
 * NOTES:
 *
 * 1. `allOf` methods accepts the value only if **all** conditions are passed
 * 2. `anyOf` methods accepts the value if **any** condition is passed
 * 3. `noneOf` methods accepts the value if **none** of the conditions is passed
 *
 * The base structure of using this builder class:
 * ```
 * override fun EventRuleBuilder.setup() {
 *   eventType("<user type>") whenever <allOf|anyOf|noneOf> {
 *     <event field> <operation> <value>
 *     or
 *     <event field> <allOf|anyOf|noneOf> {
 *       <operation>(<value>)
 *       [<operation>(<value>)]
 *     }
 *   }
 * }
 * ```
 *
 * ### Examples:
 *
 * Event has name that starts with "Test"
 *
 * ```kotlin
 * override fun EventRuleBuilder.setup() {
 *   eventType("your type") whenever allOf {
 *     name startsWith "Test"
 *   }
 * }
 * ```
 *
 * Event has name that starts with "Test" and contains "Execution"
 * ```kotlin
 * override fun EventRuleBuilder.setup() {
 *   eventType("your type") whenever allOf {
 *     name allOf {
 *       startsWith("Test")
 *       contains("Execution")
 *     }
 *   }
 * }
 * ```
 *
 * Event has a parent with type "ParentType"
 * ```kotlin
 * override fun EventRuleBuilder.setup() {
 *   eventType("your type") whenever allOf {
 *     parentEvent allOf {
 *       type equal "ParentType"
 *     }
 *   }
 * }
 * ```
 */
@RuleDsl
class EventRuleBuilder {
    private val matcherByType: MutableList<Pair<EventTypeSupplier, EventTypeMather>> = arrayListOf()

    fun allOf(block: AllOfEventMatcherBuilder.() -> Unit): EventTypeMather = matchAllOfInternal(block)

    fun anyOf(block: EventMatcherBuilder.() -> Unit): EventTypeMather = matchAnyOfInternal(block)

    fun noneOf(block: EventMatcherBuilder.() -> Unit): EventTypeMather = matchNoneOfInternal(block)

    infix fun EventTypeSupplier.whenever(matcher: EventTypeMather) {
        this@EventRuleBuilder.matcherByType += this to matcher
    }

    /**
     * Creates a constant event type
     */
    fun eventType(name: String): EventTypeSupplier = EventTypeSupplier { EventType(name) }

    /**
     * Allows to create an event type based on the matched event data
     */
    fun dynamicEventType(supplier: EventTypeBuilder.() -> EventType): EventTypeSupplier = EventTypeSupplier {
        EventTypeBuilder(this, it).supplier()
    }

    internal fun build(): EventRule {
        check(matcherByType.isNotEmpty()) { "no matchers were specified" }
        return EventRule(matcherByType)
    }
}