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

@file:JvmName("Main")

package com.exactpro.th2.sense.app.bootstrap

import java.util.Deque
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import com.exactpro.th2.common.metrics.LIVENESS_MONITOR
import com.exactpro.th2.common.metrics.READINESS_MONITOR
import com.exactpro.th2.common.schema.factory.CommonFactory
import com.exactpro.th2.sense.app.Microservice
import mu.KotlinLogging

private val LOGGER = KotlinLogging.logger { }

fun main(args: Array<String>) {
    LOGGER.info { "Starting the th2-sense service" }
    val resources: Deque<() -> Unit> = ConcurrentLinkedDeque()
    val lock = ReentrantLock()
    val condition: Condition = lock.newCondition()
    configureShutdownHook(resources, lock, condition)

    try {
        val resourceHandler: (name: String, resource: () -> Unit) -> Unit = { name, resource ->
            resources += {
                LOGGER.info { "Closing resource $name" }
                runCatching(resource).onFailure {
                    LOGGER.error(it) { "cannot close resource $name" }
                }
            }
        }

        val factory = CommonFactory.createFromArguments(*args)
        resourceHandler("common factory", factory::close)

        val app = loadApp()
        resourceHandler("app", app::close)

        app.start(factory, resourceHandler)

        awaitShutdown(lock, condition)
    } catch (ex: Exception) {
        LOGGER.error(ex) { "Cannot start the box" }
        exitProcess(1)
    }
}

private fun loadApp(): App = ServiceLoader.load(App::class.java).findFirst().orElseGet { error("Cannot load app") }

@Throws(InterruptedException::class)
private fun awaitShutdown(lock: ReentrantLock, condition: Condition) {
    try {
        lock.lock()
        LOGGER.info { "Wait shutdown" }
        condition.await()
        LOGGER.info { "App shutdown" }
    } finally {
        lock.unlock()
    }
}

private fun configureShutdownHook(resources: Deque<() -> Unit>, lock: ReentrantLock, condition: Condition) {
    Runtime.getRuntime().addShutdownHook(thread(
        start = false,
        name = "Shutdown hook"
    ) {
        LOGGER.info { "Shutdown start" }
        READINESS_MONITOR.disable()
        try {
            lock.lock()
            condition.signalAll()
        } finally {
            lock.unlock()
        }
        resources.descendingIterator().forEachRemaining { resource ->
            try {
                resource()
            } catch (e: Exception) {
                LOGGER.error(e) { "Cannot close resource ${resource::class}" }
            }
        }
        LIVENESS_MONITOR.disable()
        LOGGER.info { "Shutdown end" }
    })
}