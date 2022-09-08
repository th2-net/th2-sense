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

package com.exactpro.th2.sense.app.processor.event

import java.time.Instant
import com.exactpro.th2.sense.api.Event
import com.exactpro.th2.sense.api.EventProcessor
import com.exactpro.th2.sense.api.EventResult
import com.exactpro.th2.sense.api.ProcessorId
import com.exactpro.th2.sense.app.processor.impl.ProcessorHolder
import com.exactpro.th2.sense.app.source.SourceListener
import mu.KotlinLogging

class EventProcessorListener(
    private val processorHolder: ProcessorHolder<EventProcessor>,
    private val onTimeRefresh: (Instant) -> Unit,
    private val onMatchedEvent: (Event, Collection<EventResult.Accept>, Instant) -> Unit,
) : SourceListener<Event> {
    override fun onData(data: Event, sourceTime: Instant) {
        LOGGER.trace { "Processing event ${data.eventId.id}" }
        val matchResult: Map<ProcessorId, EventResult.Accept> = processorHolder.mapEach { context ->
            context.process(data).let { it as? EventResult.Accept }
        }
        if (matchResult.isEmpty()) return
        LOGGER.info { "Event ${data.eventId.id} is matched by ${matchResult.size} processor(s): ${matchResult.keys}" }
        onMatchedEvent(data, matchResult.values, sourceTime)
    }

    override fun onTimeRefresh(sourceTime: Instant) {
        LOGGER.info { "Refreshing time: $sourceTime" }
        onTimeRefresh.invoke(sourceTime)
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { }
    }
}