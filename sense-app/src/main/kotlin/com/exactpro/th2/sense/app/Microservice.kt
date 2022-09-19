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

package com.exactpro.th2.sense.app

import com.exactpro.th2.common.event.Event as CommonEvent
import com.exactpro.th2.common.event.EventUtils
import com.exactpro.th2.common.grpc.EventBatch
import com.exactpro.th2.common.schema.factory.AbstractCommonFactory
import com.exactpro.th2.common.schema.message.MessageRouter
import com.exactpro.th2.common.schema.message.storeEvent
import com.exactpro.th2.dataprovider.grpc.DataProviderService
import com.exactpro.th2.sense.api.Event
import com.exactpro.th2.sense.api.EventProcessor
import com.exactpro.th2.sense.api.EventProcessorFactory
import com.exactpro.th2.sense.api.EventProvider
import com.exactpro.th2.sense.api.MessageProvider
import com.exactpro.th2.sense.api.ProcessorId
import com.exactpro.th2.sense.api.ProcessorSettings
import com.exactpro.th2.sense.app.bootstrap.App
import com.exactpro.th2.sense.app.cfg.CachingConfiguration
import com.exactpro.th2.sense.app.cfg.CrawlerSourceConfiguration
import com.exactpro.th2.sense.app.cfg.HttpServerConfiguration
import com.exactpro.th2.sense.app.cfg.MqSourceConfiguration
import com.exactpro.th2.sense.app.cfg.SenseAppConfiguration
import com.exactpro.th2.sense.app.cfg.SourceConfiguration
import com.exactpro.th2.sense.app.cfg.StatisticConfiguration
import com.exactpro.th2.sense.app.notifier.event.GrafanaEventNotifier
import com.exactpro.th2.sense.app.notifier.grpc.SenseService
import com.exactpro.th2.sense.app.notifier.http.SenseHttpServer
import com.exactpro.th2.sense.app.processor.event.EventProcessorListener
import com.exactpro.th2.sense.app.processor.impl.EventProviderImpl
import com.exactpro.th2.sense.app.processor.impl.MessageProviderImpl
import com.exactpro.th2.sense.app.processor.impl.ProcessorContextImpl
import com.exactpro.th2.sense.app.processor.impl.ProcessorHolder
import com.exactpro.th2.sense.app.processor.impl.ServiceLoaderProcessorProvider
import com.exactpro.th2.sense.app.source.Source
import com.exactpro.th2.sense.app.source.crawler.SourceCrawler
import com.exactpro.th2.sense.app.source.event.MqEventSource
import com.exactpro.th2.sense.app.statistic.impl.AggregatedEventStatistic
import com.exactpro.th2.sense.app.statistic.impl.BucketEventStatistic
import com.exactpro.th2.sense.app.statistic.impl.GrafanaEventStatistic
import com.exactpro.th2.sense.app.statistic.impl.NotificationEventStatistic
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.NamedType
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.auto.service.AutoService
import io.grpc.BindableService
import mu.KotlinLogging

@AutoService(App::class)
@Suppress("unused") // loaded by service loader
class Microservice : App {
    override fun start(
        commonFactory: AbstractCommonFactory,
        closeResource: (name: String, action: () -> Unit) -> Unit,
    ) {
        val processorProvider = ServiceLoaderProcessorProvider.create<EventProcessor, EventProcessorFactory<*>>()
        val mapper = configureObjectMapper(processorProvider)
        val (
            sourceCfg: SourceConfiguration,
            processors: Map<ProcessorId, ProcessorSettings>,
            statistic: StatisticConfiguration,
            messagesCaching: CachingConfiguration,
            eventsCaching: CachingConfiguration,
            httpServerCfg: HttpServerConfiguration?,
        ) = checkNotNull(commonFactory.getCustomConfiguration(SenseAppConfiguration::class.java, mapper)) {
            "cannot read configuration"
        }
        val dataProvider = commonFactory.grpcRouter.getService(DataProviderService::class.java)
        val eventProvider: EventProvider = EventProviderImpl(dataProvider, eventsCaching)
        val messageProvider: MessageProvider = MessageProviderImpl(dataProvider, messagesCaching)

        val processorsById = processorProvider.loadProcessors(processors)
        val eventBatchRouter = commonFactory.eventBatchRouter
        val rootEventID = createRootEvent(eventBatchRouter, processorsById.keys)

        val onError: (String, Throwable) -> Unit = { name, cause ->
            eventBatchRouter.storeErrorEvent(name, cause, rootEventID)
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

        val servers = arrayListOf<BindableService>()

        servers += SenseService(notificationEventStat)

        val eventSource: Source<Event> = createSource(commonFactory, sourceCfg, servers::add)

        eventSource.addListener(eventListener)
//        messageSource.addListener()

        eventSource.start()
//        messageSource.start()

        val server = commonFactory.grpcRouter.startServer(*servers.toTypedArray())
        closeResource("grpc servers", server::shutdown)

        httpServerCfg?.apply {
            val httpServer = SenseHttpServer(this, notificationEventStat)
            closeResource("http server", httpServer::close)
        }

        closeResource("event source", eventSource::close)
//        closeResource("message source", messageSource::close)
    }

    private fun MessageRouter<EventBatch>.storeErrorEvent(
        name: String,
        cause: Throwable,
        rootEventID: String,
    ) {
        runCatching {
            storeEvent(
                CommonEvent.start().endTimestamp()
                    .name(name)
                    .type("SenseError")
                    .status(CommonEvent.Status.FAILED)
                    .exception(cause, true),
                rootEventID,
            )
        }.onFailure {
            LOGGER.error(it) { "Cannot store event: $name" }
        }
    }

    private fun createRootEvent(eventBatchRouter: MessageRouter<EventBatch>, processorIds: Set<ProcessorId>): String {
        return eventBatchRouter.storeEvent(
            CommonEvent.start().endTimestamp()
                .name("Sense root event")
                .type("Microservice")
                .status(CommonEvent.Status.PASSED)
                .bodyData(EventUtils.createMessageBean("Registered processors: ${processorIds.joinToString { it.name }}"))
        ).id
    }

    override fun close() {
    }

    private fun createSource(
        commonFactory: AbstractCommonFactory,
        sourceCfg: SourceConfiguration,
        addServer: (BindableService) -> Unit,
    ): Source<Event> = when (sourceCfg) {
        is CrawlerSourceConfiguration -> setupCrawler(sourceCfg, addServer)
        MqSourceConfiguration -> MqEventSource(commonFactory.eventBatchRouter)/* to MqMessageSource(commonFactory.messageRouterMessageGroupBatch)*/
    }

    private fun configureObjectMapper(processorProvider: ServiceLoaderProcessorProvider<EventProcessor, EventProcessorFactory<*>>): ObjectMapper {
        val processorSettings = processorProvider.processorSettings.asSequence()
            .map { (id, clazz) -> NamedType(clazz, id.name) }
            .toList().toTypedArray()
        if (processorSettings.isNotEmpty()) {
            MAPPER.registerSubtypes(*processorSettings)
        }
        return MAPPER
    }

    private fun setupCrawler(
        sourceCfg: CrawlerSourceConfiguration,
        addServer: (BindableService) -> Unit,
    ): Source<Event> {
        val sourceCrawler = SourceCrawler(sourceCfg)
        addServer(sourceCrawler)

        return sourceCrawler.events
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { }
        private val MAPPER: ObjectMapper = jacksonObjectMapper()
    }
}