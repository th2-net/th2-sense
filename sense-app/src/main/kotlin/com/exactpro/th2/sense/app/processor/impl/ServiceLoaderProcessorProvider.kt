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

import java.util.ServiceLoader
import java.util.ServiceLoader.Provider
import java.util.function.Function
import java.util.stream.Collectors
import java.util.stream.Stream
import com.exactpro.th2.sense.api.Processor
import com.exactpro.th2.sense.api.ProcessorFactory
import com.exactpro.th2.sense.api.ProcessorId
import com.exactpro.th2.sense.api.ProcessorSettings
import com.exactpro.th2.sense.app.processor.ProcessorProvider
import mu.KotlinLogging

class ServiceLoaderProcessorProvider<P : Processor, T : ProcessorFactory<P, *>>(
    private val processorFactoryClass: Class<out T>,
    private val classLoader: ClassLoader = ServiceLoaderProcessorProvider::class.java.classLoader,
) : ProcessorProvider<P> {
    override val processorSettings: Map<ProcessorId, Class<out ProcessorSettings>>
        get() = classLoader.factories(processorFactoryClass)
            .collect(Collectors.toUnmodifiableMap(
                { it.id },
                { it.settings },
            ))

    override fun loadProcessors(settings: Map<ProcessorId, ProcessorSettings>): Map<ProcessorId, P> {
        return classLoader.load(processorFactoryClass, settings)
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { }

        @JvmStatic
        inline fun <P : Processor, reified T : ProcessorFactory<P, *>> create(
            classLoader: ClassLoader = ServiceLoaderProcessorProvider::class.java.classLoader,
        ): ServiceLoaderProcessorProvider<P, T> =
            ServiceLoaderProcessorProvider(T::class.java, classLoader)

        @JvmStatic
        private fun <P : Processor, T : ProcessorFactory<P, *>> ClassLoader.load(
            clazz: Class<out T>,
            settingsById: Map<ProcessorId, ProcessorSettings>,
        ): Map<ProcessorId, P> = factories(clazz)
            .filter {
                val enabled = it.id in settingsById.keys
                if (!enabled) {
                    LOGGER.info { "Skip processor ${it.id} because it is not enabled" }
                }
                enabled
            }
            .map {
                @Suppress("UNCHECKED_CAST")
                val factor: ProcessorFactory<P, ProcessorSettings> = it as ProcessorFactory<P, ProcessorSettings>
                factor.run { create(checkNotNull(settingsById[id]) { "no configuration for processor $id" }) } }
            .collect(Collectors.toUnmodifiableMap(
                Processor::id,
                Function.identity(),
            )).also {
                LOGGER.info { "Loaded ${it.size} processor(s): ${it.keys}" }
            }

        @JvmStatic
        private fun <P : Processor, T : ProcessorFactory<P, *>> ClassLoader.factories(clazz: Class<out T>): Stream<T> =
            ServiceLoader.load(clazz, this)
                .stream()
                .map(Provider<out T>::get)
    }
}