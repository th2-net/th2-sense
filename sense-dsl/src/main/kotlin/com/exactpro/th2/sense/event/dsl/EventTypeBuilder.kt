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

@RuleDsl
class EventTypeBuilder(
    private val context: ProcessorContext,
    val event: Event,
) {
    /**
     * Creates a new event type with corresponding name
     */
    fun eventType(name: String): EventType = EventType(name)

    /**
     * Returns parent event for this event or throws an exception
     */
    val Event.parentEvent: Event
        get() = context.eventProvider.findParentFor(this) ?: error("no parent event for ${eventId.id} event")

    /**
     * Returns root event for this event or throws an exception if this event is a root event
     */
    val Event.rootEvent: Event
        get() = context.eventProvider.findRootFor(this) ?: error("no root event for ${eventId.id} event")

    fun String.substringBetween(start: String, end: String, ignoreCase: Boolean = false): String {
        val startIndex = indexOf(start, ignoreCase = ignoreCase)
        if (startIndex < 0) return ""
        val from = startIndex + start.length
        val to = indexOf(end, startIndex = from, ignoreCase = ignoreCase)
        if (to < 0) return ""
        return substring(from, to)
    }
}