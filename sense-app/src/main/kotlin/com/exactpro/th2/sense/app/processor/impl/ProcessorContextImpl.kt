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

import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.cast
import com.exactpro.th2.sense.api.EventProvider
import com.exactpro.th2.sense.api.MessageProvider
import com.exactpro.th2.sense.api.ProcessorContext
import mu.KotlinLogging

class ProcessorContextImpl(
    override val eventProvider: EventProvider,
    override val messageProvider: MessageProvider,
) : ProcessorContext {
    private val contextParameters: MutableMap<ProcessorContext.Key<*>, Any> = ConcurrentHashMap()
    override fun <T : Any> get(key: ProcessorContext.Key<T>): T? {
        LOGGER.trace { "Getting key $key" }
        return contextParameters[key]?.let(key.type::cast)
    }

    override fun <T : Any> set(key: ProcessorContext.Key<T>, value: T) {
        LOGGER.trace { "Set key $key with value $value" }
        contextParameters[key] = value
    }

    override fun <T : Any> remove(key: ProcessorContext.Key<T>): T? {
        LOGGER.trace { "Remove key $key" }
        return contextParameters.remove(key)?.let(key.type::cast)
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { }
    }
}