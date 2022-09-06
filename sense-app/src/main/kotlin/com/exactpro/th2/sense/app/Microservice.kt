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

import com.exactpro.th2.common.schema.factory.AbstractCommonFactory
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
import com.exactpro.th2.sense.app.cfg.MqSourceConfiguration
import com.exactpro.th2.sense.app.cfg.SenseAppConfiguration
import com.exactpro.th2.sense.app.cfg.SourceConfiguration
import com.exactpro.th2.sense.app.cfg.StatisticConfiguration
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
import com.google.auto.service.AutoService
import mu.KotlinLogging

@AutoService(App::class)
@Suppress("unused") // loaded by service loader
class Microservice : App {
    override fun start(
        commonFactory: AbstractCommonFactory,
        closeResource: (name: String, action: () -> Unit) -> Unit,
    ) {
        val (
            sourceCfg: SourceConfiguration,
            processors: Map<ProcessorId, ProcessorSettings>,
            statistic: StatisticConfiguration,
            messagesCaching: CachingConfiguration,
            eventsCaching: CachingConfiguration,
        ) = checkNotNull(commonFactory.getCustomConfiguration(SenseAppConfiguration::class.java)) {
            "cannot read configuration"
        }
        val dataProvider = commonFactory.grpcRouter.getService(DataProviderService::class.java)
        val eventProvider: EventProvider = EventProviderImpl(dataProvider, eventsCaching)
        val messageProvider: MessageProvider = MessageProviderImpl(dataProvider, messagesCaching)

        val processorProvider = ServiceLoaderProcessorProvider.create<EventProcessor, EventProcessorFactory<*>>(processors)

        val holder = ProcessorHolder(processorProvider.processors) {
            LOGGER.info { "Creating context for event processor $it" }
            ProcessorContextImpl(eventProvider, messageProvider)
        }

        val globalEventsStatistic = BucketEventStatistic(statistic.eventBuckets)

        val aggregatedEventStatistic = AggregatedEventStatistic(globalEventsStatistic, listOf(GrafanaEventStatistic))
        val eventListener = EventProcessorListener(holder) { event, results, time ->
            aggregatedEventStatistic.refresh(time)
            results.forEach { result ->
                aggregatedEventStatistic.update(result.type, event, time)
            }
        }

        val eventSource: Source<Event> = createSource(commonFactory, sourceCfg, closeResource)

        eventSource.addListener(eventListener)
//        messageSource.addListener()

        eventSource.start()
//        messageSource.start()

        closeResource("event source", eventSource::close)
//        closeResource("message source", messageSource::close)
    }

    override fun close() {
    }

    private fun createSource(
        commonFactory: AbstractCommonFactory,
        sourceCfg: SourceConfiguration,
        closeResource: (name: String, action: () -> Unit) -> Unit,
    ): Source<Event> = when (sourceCfg) {
        is CrawlerSourceConfiguration -> setupCrawler(commonFactory, sourceCfg, closeResource)
        MqSourceConfiguration -> MqEventSource(commonFactory.eventBatchRouter)/* to MqMessageSource(commonFactory.messageRouterMessageGroupBatch)*/
    }

    private fun setupCrawler(
        commonFactory: AbstractCommonFactory,
        sourceCfg: CrawlerSourceConfiguration,
        closeResource: (name: String, action: () -> Unit) -> Unit,
    ) : Source<Event> {
        val sourceCrawler = SourceCrawler(sourceCfg)
        val server = commonFactory.grpcRouter.startServer(sourceCrawler)
        closeResource("crawler processor", server::shutdown)

        return sourceCrawler.events
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { }
    }
}