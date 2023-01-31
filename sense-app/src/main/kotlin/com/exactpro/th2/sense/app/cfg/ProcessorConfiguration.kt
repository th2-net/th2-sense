package com.exactpro.th2.sense.app.cfg

import com.exactpro.th2.processor.api.IProcessorSettings
import com.google.auto.service.AutoService

@AutoService(IProcessorSettings::class)
data class ProcessorConfiguration(val name: String, val version: String): IProcessorSettings