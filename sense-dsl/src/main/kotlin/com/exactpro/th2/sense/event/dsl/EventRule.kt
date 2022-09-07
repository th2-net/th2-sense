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
import com.exactpro.th2.sense.api.EventResult
import com.exactpro.th2.sense.api.EventType
import com.exactpro.th2.sense.api.ProcessorContext
import mu.KotlinLogging

class EventRule(
    private val matchers: List<Pair<EventTypeSupplier, EventTypeMather>>
) {
    fun handle(context: ProcessorContext, event: Event): EventResult {
        return matchers.firstNotNullOfOrNull { (typeSupplier, matcher) ->
                LOGGER.trace { "Checking event '${event.eventId.id}' by mather" }
                matcher.match(context, event).let { match ->
                    if (match) {
                        typeSupplier.run { context.get(event) }
                            .also { type ->
                                LOGGER.trace { "Event '${event.eventId.id}' is accepted as type $type" }
                            }
                    } else {
                        LOGGER.trace { "Event '${event.eventId.id}' is not accepted" }
                        null
                    }
                }
            }?.let(EventResult::Accept)
            ?: EventResult.Skip
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { }
        @JvmStatic
        fun builder(): EventRuleBuilder = EventRuleBuilder()
    }
}