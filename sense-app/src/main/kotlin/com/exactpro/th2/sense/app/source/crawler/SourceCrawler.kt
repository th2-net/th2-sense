/*
 * Copyright 2022-2023 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.sense.app.source.crawler

import com.exactpro.th2.common.schema.factory.CommonFactory
import com.exactpro.th2.processor.Application
import mu.KotlinLogging
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SourceCrawler(
    commonFactory: CommonFactory,
): AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor()
    private val application = Application(commonFactory)

    fun start() {
        executor.submit{ application.run() }
    }

    override fun close() {
        application.close()
        executor.shutdown()
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            LOGGER.warn { "Force executor close" }
            val tasks = executor.shutdownNow()
            LOGGER.warn { "${tasks.size} task(s) left in executor" }
        }
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { }
    }
}
