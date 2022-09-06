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

import com.exactpro.th2.sense.event.dsl.AllOfEventMatcherBuilder
import com.exactpro.th2.sense.event.dsl.EventMatcherBuilder
import com.exactpro.th2.sense.event.dsl.EventTypeMather
import com.exactpro.th2.sense.event.dsl.SimpleMatchFunction
import com.exactpro.th2.sense.event.dsl.SimpleMatcher
import mu.KotlinLogging

private val LOGGER = KotlinLogging.logger { }

internal class EventMatcherBuilderImpl(
    private val matchers: MutableMap<String, EventTypeMather>,
) : EventMatcherBuilder {
    override fun register(name: String, matcher: EventTypeMather) {
        val prev = matchers.put(name, matcher)
        if (prev != null) {
            LOGGER.warn { "Matcher $name was overridden" }
        }
    }
}

internal fun matchAllOfInternal(block: AllOfEventMatcherBuilder.() -> Unit): EventTypeMather {
    return AllEventTypeMatcher(hashMapOf<String, EventTypeMather>().also {
        EventMatcherBuilderImpl(it).block()
        check(it.isNotEmpty()) { "no matchers specified" }
    })
}

internal fun matchAnyOfInternal(block: EventMatcherBuilder.() -> Unit): EventTypeMather {
    return AnyEventTypeMatcher(hashMapOf<String, EventTypeMather>().also {
        EventMatcherBuilderImpl(it).block()
        check(it.isNotEmpty()) { "no matchers specified" }
    })
}

internal fun matchNoneOfInternal(block: EventMatcherBuilder.() -> Unit): EventTypeMather {
    return NoneEventTypeMatcher(hashMapOf<String, EventTypeMather>().also {
        EventMatcherBuilderImpl(it).block()
        check(it.isNotEmpty()) { "no matchers specified" }
    })
}