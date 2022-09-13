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

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import com.exactpro.th2.sense.api.Processor
import com.exactpro.th2.sense.api.ProcessorContext
import com.exactpro.th2.sense.api.ProcessorId
import mu.KotlinLogging

class ProcessorHolder<T : Processor>(
    processorsById: Map<ProcessorId, T>,
    private val onError: (String, Throwable) -> Unit,
    private val contextSupplier: (ProcessorId) -> ProcessorContext,
) {
    private val lock = ReentrantLock()
    private val processorHoldersById: Map<ProcessorId, Holder<T>> = processorsById.mapValues { (id, processor) ->
        Holder(processor) { contextSupplier(id) }
    }

    fun <V> mapEach(action: T.(ProcessorContext) -> V?): Map<ProcessorId, V> {
        return lock.withLock {
            processorHoldersById.asSequence()
                .mapNotNull { (id, holder) ->
                    LOGGER.trace { "Executing action on processor $id" }
                    holder.runCatching { processor.action(context) }
                        .onFailure {
                            LOGGER.error(it) { "Failed to execute action on processor $id" }
                            onError("Cannot process data by processor $id", it)
                        }
                        .getOrNull()?.let { id to it }
                }.toMap()
        }
    }

    private class Holder<T : Processor>(
        val processor: T,
        contextSupplier: () -> ProcessorContext,
    ) {
        val context: ProcessorContext by lazy(contextSupplier)
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { }
    }
}