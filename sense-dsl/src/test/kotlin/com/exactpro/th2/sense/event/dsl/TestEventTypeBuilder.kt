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

import com.exactpro.th2.sense.api.EventProvider
import com.exactpro.th2.sense.api.ProcessorContext
import com.exactpro.th2.sense.event.dsl.util.createEvent
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.same
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isSameInstanceAs

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class TestEventTypeBuilder {
    private val eventProviderMock = mock<EventProvider> { }
    private val contextMock = mock<ProcessorContext> {
        on { eventProvider } doReturn eventProviderMock
    }

    @ParameterizedTest(name = "Substring between {1} and {2} in {0} is {3}")
    @MethodSource("substringTest")
    fun `substring between`(string: String, start: String, end: String, result: String) {
        val actualResult = EventTypeBuilder(contextMock, createEvent()).run {
            string.substringBetween(start, end)
        }
        expectThat(actualResult).isEqualTo(result)
    }

    fun substringTest(): List<Arguments> = listOf(
        arguments("abcdefg", "a", "g", "bcdef"),
        arguments("abcdefg", "abc", "efg", "d"),
        arguments("abcdefg", "c", "e", "d"),
        arguments("abcabc", "b", "ab", "c"),
        arguments("abcabc", "e", "ab", ""),
        arguments("abcabc", "a", "be", ""),
        arguments("abcabc", "", "", ""),
    )

    @Test
    fun `returns parent event`() {
        val parent = createEvent()
        val event = createEvent(parentEventID = parent.eventId)
        whenever(eventProviderMock.findParentFor(same(event))) doReturn parent
        val actualParent = EventTypeBuilder(contextMock, event).run { event.parentEvent }
        expectThat(actualParent).isSameInstanceAs(parent)
    }

    @Test
    fun `returns root event`() {
        val root = createEvent()
        val event = createEvent(parentEventID = root.eventId)
        whenever(eventProviderMock.findRootFor(same(event))) doReturn root
        val actualRoot = EventTypeBuilder(contextMock, event).run { event.rootEvent }
        expectThat(actualRoot).isSameInstanceAs(root)
    }
}