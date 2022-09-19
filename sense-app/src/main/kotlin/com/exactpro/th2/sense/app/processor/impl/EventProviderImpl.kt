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

package com.exactpro.th2.sense.app.processor.impl

import com.exactpro.th2.common.grpc.EventID
import com.exactpro.th2.common.message.toJson
import com.exactpro.th2.dataprovider.grpc.DataProviderService
import com.exactpro.th2.sense.api.Event
import com.exactpro.th2.sense.api.EventProvider
import com.exactpro.th2.sense.app.cfg.CachingConfiguration
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import mu.KotlinLogging

class EventProviderImpl(
    private val provider: DataProviderService,
    cachingConfiguration: CachingConfiguration,
) : EventProvider {
    private val eventsCache: Cache<EventID, Event> = cacheBuilder(cachingConfiguration)
        .weigher { _: EventID, value: Event -> value.body.size() }
        .removalListener<EventID, Event> {
            LOGGER.trace { "Event ${it.key.toJson()} was evicted from cache because ${it.cause}" }
        }
        .build()

    override fun findEvent(eventID: EventID): Event? {
        return cacheOrLoad(eventID) {
            try {
                provider.getEvent(eventID)
            } catch (ex: Exception) {
                LOGGER.error(ex) { "Cannot load event by id" }
                Event.getDefaultInstance()
            }
        }
    }

    override fun findParentFor(event: Event): Event? {
        return if (event.hasParentEventId()) {
            findEvent(event.parentEventId)
        } else {
            null
        }
    }

    override fun findRootFor(event: Event): Event? {
        if (!event.hasParentEventId()) {
            return null
        }
        var parent: Event? = null
        do {
            parent = findParentFor(parent ?: event)
        } while (parent != null && parent.hasParentEventId())
        return parent
    }

    private inline fun cacheOrLoad(
        eventID: EventID,
        crossinline load: () -> Event,
    ): Event? {
        return eventsCache.get(eventID) {
            LOGGER.trace { "Loading value for event ${eventID.id}" }
            load()
        }.takeIf { it != Event.getDefaultInstance() }
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { }
    }
}