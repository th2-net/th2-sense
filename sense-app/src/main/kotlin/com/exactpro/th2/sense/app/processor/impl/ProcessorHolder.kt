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

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import com.exactpro.th2.sense.api.Processor
import com.exactpro.th2.sense.api.ProcessorContext
import com.exactpro.th2.sense.api.ProcessorId
import com.exactpro.th2.sense.app.processor.ProcessorKey
import mu.KotlinLogging

class ProcessorHolder<T : Processor>(
    processorsById: Map<ProcessorId, T>,
    private val onError: (String, Throwable) -> Unit,
    private val contextSupplier: (ProcessorId) -> ProcessorContext,
) {
    private val innerIdCounter = AtomicInteger(1)
    private val lock = ReentrantLock()
    private val processorHoldersById: MutableMap<ProcessorKey, Holder<T>> = processorsById.asSequence().associateTo(hashMapOf()) { (id, processor) ->
        val processorKey = id.toKey()
        LOGGER.info { "processor $id registered under the key $processorKey" }
        processorKey to Holder(processor) { contextSupplier(id) }
    }

    fun <V> mapEach(action: T.(ProcessorContext) -> V?): Map<ProcessorKey, V> {
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

    fun addProcessor(id: ProcessorId, processor: T): ProcessorKey {
        return lock.withLock {
            val key = id.toKey()
            processorHoldersById[key] = Holder(processor, isRegisteredByUser = true) { contextSupplier(id) }
            key
        }.also {
            LOGGER.info { "Processor ${processor::class.java.simpleName} registered with key $it" }
        }
    }

    fun removeProcessor(key: ProcessorKey) {
        lock.withLock {
            val holder = processorHoldersById[key]
            checkNotNull(holder) { "cannot find processor with key $key" }
            check(holder.isRegisteredByUser) { "processor with key $key is registered on startup and cannot be removed" }
            processorHoldersById.remove(key)
        }
    }

    private fun ProcessorId.toKey() = ProcessorKey(this, innerIdCounter.getAndIncrement())

    private class Holder<T : Processor>(
        val processor: T,
        val isRegisteredByUser: Boolean = false,
        contextSupplier: () -> ProcessorContext,
    ) {
        val context: ProcessorContext by lazy(contextSupplier)
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { }
    }
}