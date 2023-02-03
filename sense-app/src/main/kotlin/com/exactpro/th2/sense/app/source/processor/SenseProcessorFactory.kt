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
package com.exactpro.th2.sense.app.source.processor

import com.exactpro.th2.dataprovider.lw.grpc.DataProviderService
import com.exactpro.th2.processor.api.IProcessor
import com.exactpro.th2.processor.api.IProcessorFactory
import com.exactpro.th2.processor.api.IProcessorSettings
import com.exactpro.th2.processor.api.ProcessorContext
import com.exactpro.th2.sense.api.*
import com.exactpro.th2.sense.app.cfg.*
import com.exactpro.th2.sense.app.notifier.event.GrafanaEventNotifier
import com.exactpro.th2.sense.app.processor.event.EventProcessorListener
import com.exactpro.th2.sense.app.processor.impl.*
import com.exactpro.th2.sense.app.statistic.impl.AggregatedEventStatistic
import com.exactpro.th2.sense.app.statistic.impl.BucketEventStatistic
import com.exactpro.th2.sense.app.statistic.impl.GrafanaEventStatistic
import com.exactpro.th2.sense.app.statistic.impl.NotificationEventStatistic
import com.exactpro.th2.sense.app.notifier.grpc.SenseService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.NamedType
import com.fasterxml.jackson.databind.module.SimpleModule
import com.google.auto.service.AutoService
import mu.KotlinLogging
import java.time.Instant
import java.util.Deque
import java.util.concurrent.ConcurrentLinkedDeque
import com.exactpro.th2.common.event.Event as CommonEvent

@AutoService(IProcessorFactory::class)
class SenseProcessorFactory : IProcessorFactory {
    private val processorProvider = ServiceLoaderProcessorProvider.create<EventProcessor, EventProcessorFactory<*>>()
    private val resources: Deque<() -> Unit> = ConcurrentLinkedDeque()

    override fun create(context: ProcessorContext): IProcessor {

        val commonFactory = context.commonFactory
        val (
            processors: Map<ProcessorId, ProcessorSettings>,
            statistic: StatisticConfiguration,
            messagesCaching: CachingConfiguration,
            eventsCaching: CachingConfiguration,
            httpServerCfg: HttpServerConfiguration?,
            grpcConfiguration: GrpcSenseConfiguration
        ) = checkNotNull(context.settings as SenseAppConfiguration) {
            "cannot read configuration"
        }
        val dataProvider = commonFactory.grpcRouter.getService(DataProviderService::class.java)
        val eventProvider: EventProvider = EventProviderImpl(dataProvider, eventsCaching)
        val messageProvider: MessageProvider = MessageProviderImpl(dataProvider, messagesCaching)

        val processorsById = processorProvider.loadProcessors(processors)

        val onError: (String, Throwable) -> Unit = { name, cause ->
            context.eventBatcher.onEvent(
                CommonEvent.start().endTimestamp()
                    .name(name)
                    .type("SenseError")
                    .status(CommonEvent.Status.FAILED)
                    .exception(cause, true)
                    .toProto(context.processorEventId)
            )
        }

        val holder = ProcessorHolder(processorsById, onError) {
            LOGGER.info { "Creating context for event processor $it" }
            ProcessorContextImpl(eventProvider, messageProvider)
        }

        val globalEventsStatistic = BucketEventStatistic(statistic.eventBuckets)
        val eventNotifier = GrafanaEventNotifier()

        val notificationEventStat = NotificationEventStatistic(listOf(eventNotifier), onError)
        val aggregatedEventStatistic = AggregatedEventStatistic(listOf(globalEventsStatistic, GrafanaEventStatistic, notificationEventStat), onError)
        val eventListener = EventProcessorListener(holder,
            { aggregatedEventStatistic.refresh(it) },
            { event, results, time ->
                aggregatedEventStatistic.refresh(time)
                results.forEach { result ->
                    aggregatedEventStatistic.update(result.type, event, time)
                }
            }
        )

        val registrar = ProcessorRegistrarImpl(processorProvider, holder)

        val senseService = SenseService(grpcConfiguration, notificationEventStat, registrar)

        return SenseProcessor(
            eventListener,
            httpServerCfg,
            notificationEventStat,
        )
        { closeResource ->
            val server = commonFactory.grpcRouter.startServer(senseService)
            closeResource("grpc sense service", senseService::close)
            server.start()
            closeResource("grpc servers", server::shutdown)
        }
    }

    override fun createProcessorEvent(): CommonEvent = CommonEvent.start().endTimestamp()
        .name("Sense data processor event ${Instant.now()}")
        .description("Will contain all events with errors and information about processed events/messages")
        .type("Microservice")

    override fun registerModules(configureMapper: ObjectMapper) {
        with(configureMapper) {
            registerModule(
                SimpleModule().addAbstractTypeMapping(
                    IProcessorSettings::class.java,
                    SenseAppConfiguration::class.java
                )
            )
        }

        val processorSettings = processorProvider.processorSettings.asSequence()
            .map { (id, clazz) -> NamedType(clazz, id.name) }
            .toList().toTypedArray()
        if (processorSettings.isNotEmpty()) {
            configureMapper.registerSubtypes(*processorSettings)
        }
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { }
    }
}
