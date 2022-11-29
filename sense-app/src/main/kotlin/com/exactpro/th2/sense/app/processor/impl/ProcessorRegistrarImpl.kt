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

import com.exactpro.th2.sense.api.EmptyProcessorSettings
import com.exactpro.th2.sense.api.Processor
import com.exactpro.th2.sense.api.ProcessorId
import com.exactpro.th2.sense.api.ProcessorSettings
import com.exactpro.th2.sense.app.processor.ProcessorKey
import com.exactpro.th2.sense.app.processor.ProcessorProvider
import com.exactpro.th2.sense.app.processor.ProcessorRegistrar
import com.fasterxml.jackson.databind.ObjectMapper

class ProcessorRegistrarImpl<T : Processor>(
    private val provider: ProcessorProvider<T>,
    private val holder: ProcessorHolder<T>,
    private val mapper: ObjectMapper,
) : ProcessorRegistrar {
    override fun register(id: ProcessorId, settingsData: String?): ProcessorKey {
        val settings: ProcessorSettings = if (settingsData == null) {
            EmptyProcessorSettings()
        } else {
            requireNotNull(provider.processorSettings[id]) {
                "cannot find settings for processor with id $id"
            }.let {
                mapper.readValue(settingsData, it)
            }
        }
        val processor = checkNotNull(provider.loadProcessors(mapOf(id to settings))[id]) {
            "processor for id $id was not created"
        }
        return holder.addProcessor(id, processor)
    }

    override fun unregister(key: ProcessorKey) {
        holder.removeProcessor(key)
    }
}