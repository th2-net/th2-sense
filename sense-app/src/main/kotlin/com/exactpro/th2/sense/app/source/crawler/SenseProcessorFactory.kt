/*
 * Copyright 2023-2023 Exactpro (Exactpro Systems Limited)
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

import com.exactpro.th2.processor.api.IProcessor
import com.exactpro.th2.processor.api.IProcessorFactory
import com.exactpro.th2.processor.api.IProcessorSettings
import com.exactpro.th2.processor.api.ProcessorContext
import com.exactpro.th2.sense.app.cfg.ProcessorConfiguration
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.google.auto.service.AutoService

@AutoService(IProcessorFactory::class)
class SenseProcessorFactory : IProcessorFactory {
    override fun create(context: ProcessorContext): IProcessor {
        return SenseProcessor(context.settings as ProcessorConfiguration)
    }

    override fun registerModules(objectMapper: ObjectMapper) {
        with(objectMapper) {
            registerModule(
                SimpleModule().addAbstractTypeMapping(
                    IProcessorSettings::class.java,
                    ProcessorConfiguration::class.java
                )
            )
        }
    }
}
