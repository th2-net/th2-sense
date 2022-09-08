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

package com.exactpro.th2.sense.app.source

import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import mu.KotlinLogging

abstract class AbstractSource<T> : Source<T> {
    private val started = AtomicBoolean()
    private val listeners: MutableList<SourceListener<T>> = CopyOnWriteArrayList()

    override fun start() {
        if (!started.compareAndSet(false, true)) {
            error("Source already started")
        }
        onStart()
    }

    override fun addListener(listener: SourceListener<T>) {
        listeners += listener
    }

    override fun close() {
        LOGGER.info { "Closing source ${this::class}" }
        onClose()
    }

    protected abstract fun onStart()

    protected open fun onClose(): Unit = Unit

    protected fun notify(data: T, time: Instant) {
        listeners.forEach { listener ->
            listener.runCatching { onData(data, time) }.onFailure {
                LOGGER.error(it) { "cannot handle data by ${listener::class}" }
            }
        }
    }

    protected fun refreshTime(time: Instant) {
        listeners.forEach { listener ->
            listener.runCatching { onTimeRefresh(time) }.onFailure {
                LOGGER.error(it) { "cannot handle time refresh by ${listener::class}" }
            }
        }
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { }
    }
}