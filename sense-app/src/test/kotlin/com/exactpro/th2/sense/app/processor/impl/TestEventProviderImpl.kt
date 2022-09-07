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

import com.exactpro.th2.common.event.EventUtils
import com.exactpro.th2.common.grpc.EventID
import com.exactpro.th2.dataprovider.grpc.DataProviderService
import com.exactpro.th2.dataprovider.grpc.EventResponse
import com.exactpro.th2.sense.api.Event
import com.exactpro.th2.sense.app.cfg.CachingConfiguration
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.isSameInstanceAs
import strikt.assertions.isSuccess

internal class TestEventProviderImpl {
    private val dataProviderService = mock<DataProviderService> {  }
    private val provider = EventProviderImpl(dataProviderService, CachingConfiguration())

    @Test
    fun `returns event`() {
        val event = createEvent()
        returnEvents(event)

        expectThat(provider.findEvent(event.eventId)).isSameInstanceAs(event)
    }

    @Test
    fun `returns parent event`() {
        val parent = createEvent()
        val event = createEvent(parentId = parent.eventId)
        returnEvents(parent, event)

        expectThat(provider.findParentFor(event)).isSameInstanceAs(parent)
    }

    @Test
    fun `returns root event`() {
        val root = createEvent()
        val parent = createEvent(parentId = root.eventId)
        val event = createEvent(parentId = parent.eventId)
        returnEvents(root, parent, event)

        expectThat(provider.findRootFor(event)).isSameInstanceAs(root)
    }

    @Test
    fun `hits the cache if event was requested before`() {
        val event = createEvent()
        returnEvents(event)

        expect {
            that(provider.findEvent(event.eventId)).isSameInstanceAs(event)
            that(provider.findEvent(event.eventId)).isSameInstanceAs(event)
            catching { verify(dataProviderService).getEvent(eq(event.eventId)) }.isSuccess()
        }
    }

    private fun returnEvents(vararg events: Event) {
        events.forEach {
            whenever(dataProviderService.getEvent(eq(it.eventId))) doReturn it
        }
    }

    private fun createEvent(parentId: EventID? = null): EventResponse = Event.newBuilder()
        .setEventId(EventUtils.toEventID(System.nanoTime().toString()))
        .apply {
            parentId?.also {
                parentEventId = it
            }
        }
        .build()
}