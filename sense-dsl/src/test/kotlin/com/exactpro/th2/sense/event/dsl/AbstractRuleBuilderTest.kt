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
import com.exactpro.th2.sense.api.EventProvider
import com.exactpro.th2.sense.api.EventResult
import com.exactpro.th2.sense.api.EventType
import com.exactpro.th2.sense.api.ProcessorContext
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.same
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.Assertions
import strikt.api.DescribeableBuilder
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isSameInstanceAs

abstract class AbstractRuleBuilderTest {
    protected val eventProvider = mock<EventProvider> { }
    protected val context: ProcessorContext = mock {
        on { eventProvider } doReturn eventProvider
    }

    protected fun EventRule.assertAcceptEvent(event: Event): DescribeableBuilder<EventType> {
        val result = handle(context, event)
        return expectThat(result)
            .isA<EventResult.Accept>()
            .get { type }
    }

    protected infix fun EventRule.assertSkipsEvent(event: Event) {
        val result = handle(context, event)
        expectThat(result).isSameInstanceAs(EventResult.Skip)
    }

    protected fun Event.setupParent(parent: Event) {
        whenever(eventProvider.findParentFor(same(this))) doReturn parent
    }

    protected fun Event.setupRoot(root: Event) {
        whenever(eventProvider.findRootFor(same(this))) doReturn root
    }

    protected infix fun DescribeableBuilder<EventType>.asType(expectedType: EventType) {
        this isEqualTo expectedType
    }

    protected fun rule(block: EventRuleBuilder.() -> Unit): EventRule = EventRuleBuilder().also(block).build()

    protected fun type(name: String): EventType = EventType(name)
}