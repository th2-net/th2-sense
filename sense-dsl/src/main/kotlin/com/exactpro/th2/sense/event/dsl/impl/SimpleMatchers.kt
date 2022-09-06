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

import com.exactpro.th2.sense.api.Event
import com.exactpro.th2.sense.api.ProcessorContext
import com.exactpro.th2.sense.event.dsl.EventTypeMather
import com.exactpro.th2.sense.event.dsl.SimpleMatchContext
import com.exactpro.th2.sense.event.dsl.SimpleMatchFunction
import com.exactpro.th2.sense.event.dsl.SimpleMatcher
import mu.KotlinLogging

private val LOGGER = KotlinLogging.logger { }

internal class AggregatedSimpleMatcher<T : Any> : SimpleMatcher<T> {
    private val _aggregated: MutableList<Pair<String, SimpleMatchFunction<T>>> = arrayListOf()
    val aggregated: List<Pair<String, SimpleMatchFunction<T>>>
        get() = _aggregated
    override fun register(name: String, match: SimpleMatchFunction<T>) { _aggregated += name to match }
}


internal class SimpleMatcherImpl<T : Any>(
    private val extractor: Event.() -> T,
    private val addMatcher: (EventTypeMather) -> Unit,
) : SimpleMatcher<T> {
    override fun register(name: String, match: SimpleMatchFunction<T>) {
        addMatcher(object : EventTypeMather {
            override fun match(context: ProcessorContext, event: Event): Boolean {
                return match.matchAndLog(name, event.extractor())
            }
        })
    }
}

internal fun <T : Any> SimpleMatchFunction<T>.matchAndLog(name: String, value: T): Boolean {
    LOGGER.trace { "Checking value '$value' by simple matcher '$name'" }
    return invoke(SimpleMatchContext, value).also {
        LOGGER.trace { "Simple matcher '$name' ${if (it) "accepts" else "does not accept"} value $value" }
    }
}