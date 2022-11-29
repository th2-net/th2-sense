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

package com.exactpro.th2.sense.event.dsl.impl

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import com.exactpro.th2.sense.api.Event
import com.exactpro.th2.sense.api.ProcessorContext
import com.exactpro.th2.sense.event.dsl.EventTypeMather
import com.exactpro.th2.sense.event.dsl.AllOfEventMatcherBuilder
import com.exactpro.th2.sense.event.dsl.RelatedEventMatcherBuilder
import com.exactpro.th2.sense.event.dsl.SimpleMatcher
import mu.KotlinLogging

private val LOGGER = KotlinLogging.logger { }

internal class RelatedEventMatcher(
    private val name: String,
    private val subMatcher: EventTypeMather,
    private val relatedEvent: ProcessorContext.(Event) -> Event?,
) : EventTypeMather {
    override fun match(context: ProcessorContext, event: Event): Boolean {
        LOGGER.trace { "Matching '$name' event for '${event.eventId.id}'" }
        val relatedEvent = context.relatedEvent(event) ?: return false
        LOGGER.trace { "'$name' event '${relatedEvent.eventId.id}' is found" }
        return subMatcher.match(context, relatedEvent)
    }
}

internal class HasRelatedEventMatcher(
    private val name: String,
    private val mustExist: Boolean,
    private val relatedEvent: ProcessorContext.(Event) -> Event?,
) : EventTypeMather {
    override fun match(context: ProcessorContext, event: Event): Boolean {
        LOGGER.trace { "Checking for '$name' event for '${event.eventId.id}'" }
        val result = context.relatedEvent(event)
        LOGGER.trace { "'$name' event is ${result?.run { "found" } ?: "not found"}" }
        return (result == null) xor mustExist
    }
}

internal class AllEventTypeMatcher(
    private val delegates: Map<String, EventTypeMather>,
) : EventTypeMather {
    override fun match(context: ProcessorContext, event: Event): Boolean {
        LOGGER.trace { "Check event '${event.eventId.id}' matches all condition(s): ${delegates.size}" }
        return delegates.all { (name, matcher) ->
            matcher.matchAndLog(name, context, event)
        }.also {
            if (it) {
                LOGGER.trace { "All matchers '${delegates.keys}' accepted the event ${event.eventId.id}" }
            } else {
                LOGGER.trace { "Some matchers '${delegates.keys}' did not accept the event ${event.eventId.id}" }
            }
        }
    }
}

internal class AnyEventTypeMatcher(
    private val delegates: Map<String, EventTypeMather>,
) : EventTypeMather {
    override fun match(context: ProcessorContext, event: Event): Boolean {
        LOGGER.trace { "Check event '${event.eventId.id}' matches any condition(s): ${delegates.size}" }
        return delegates.any { (name, matcher) ->
            matcher.matchAndLog(name, context, event)
        }.also {
            if (it) {
                LOGGER.trace { "Some matcher from '${delegates.keys}' accepted the event ${event.eventId.id}" }
            } else {
                LOGGER.trace { "All matchers '${delegates.keys}' did not accept the event ${event.eventId.id}" }
            }
        }
    }
}

internal class NoneEventTypeMatcher(
    private val delegates: Map<String, EventTypeMather>,
) : EventTypeMather {
    override fun match(context: ProcessorContext, event: Event): Boolean {
        LOGGER.trace { "Check event '${event.eventId.id}' matches any condition(s): ${delegates.size}" }
        return delegates.none { (name, matcher) ->
            matcher.matchAndLog(name, context, event)
        }.also {
            if (it) {
                LOGGER.trace { "None matchers '${delegates.keys}' accepted the event ${event.eventId.id}" }
            } else {
                LOGGER.trace { "Some matchers '${delegates.keys}' accepted the event ${event.eventId.id}" }
            }
        }
    }
}

internal class SimpleMatcherDelegate<T>(
    private val extractor: Event.() -> T,
) : ReadOnlyProperty<AllOfEventMatcherBuilder, SimpleMatcher<T>> {
    override fun getValue(thisRef: AllOfEventMatcherBuilder, property: KProperty<*>): SimpleMatcher<T> {
        return SimpleMatcherImpl(extractor) {
            thisRef.register(property.name, it)
        }
    }
}

internal class RelatedEventMatcherBuilderImpl(
    private val builder: AllOfEventMatcherBuilder,
    private val name: String,
    private val relatedEvent: ProcessorContext.(Event) -> Event?,
) : RelatedEventMatcherBuilder {
    override fun register(matcher: EventTypeMather) {
        builder.register(name, RelatedEventMatcher(name, matcher, relatedEvent))
    }
}

private fun EventTypeMather.matchAndLog(
    name: String,
    context: ProcessorContext,
    relatedEvent: Event
): Boolean {
    LOGGER.trace { "Matching event '${relatedEvent.eventId.id}' by '$name' matcher" }
    val matchResult = match(context, relatedEvent)
    LOGGER.trace { "Matcher '$name' ${if (matchResult) "accepts" else "does not accept"} event '${relatedEvent.eventId.id}'" }
    return matchResult
}