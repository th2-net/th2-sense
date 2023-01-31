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
