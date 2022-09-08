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

package com.exactpro.th2.sense.app.source.crawler

import com.exactpro.th2.crawler.dataprocessor.grpc.Status as CrawlerStatus
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import com.exactpro.th2.common.message.toJson
import com.exactpro.th2.common.util.toInstant
import com.exactpro.th2.crawler.dataprocessor.grpc.CrawlerId
import com.exactpro.th2.crawler.dataprocessor.grpc.CrawlerInfo
import com.exactpro.th2.crawler.dataprocessor.grpc.DataProcessorGrpc.DataProcessorImplBase
import com.exactpro.th2.crawler.dataprocessor.grpc.DataProcessorInfo
import com.exactpro.th2.crawler.dataprocessor.grpc.EventDataRequest
import com.exactpro.th2.crawler.dataprocessor.grpc.EventResponse
import com.exactpro.th2.crawler.dataprocessor.grpc.IntervalInfo
import com.exactpro.th2.crawler.dataprocessor.grpc.MessageDataRequest
import com.exactpro.th2.crawler.dataprocessor.grpc.MessageResponse
import com.exactpro.th2.sense.api.Event
import com.exactpro.th2.sense.api.Message
import com.exactpro.th2.sense.app.cfg.CrawlerSourceConfiguration
import com.exactpro.th2.sense.app.source.AbstractSource
import com.exactpro.th2.sense.app.source.Source
import com.google.protobuf.Empty
import io.grpc.Status
import io.grpc.stub.StreamObserver
import mu.KotlinLogging

class SourceCrawler(
    private val cfg: CrawlerSourceConfiguration,
) : DataProcessorImplBase() {
    private val _messages = ProxySource<Message>()
    private val _events = ProxySource<Event>()
    val messages: Source<Message>
        get() = _messages
    val events: Source<Event>
        get() = _events

    private val knownCrawlers: MutableSet<CrawlerId> = ConcurrentHashMap.newKeySet()
    private lateinit var currentTime: Instant

    override fun crawlerConnect(request: CrawlerInfo, responseObserver: StreamObserver<DataProcessorInfo>) {
        LOGGER.info { "Accepting handshake from ${request.id.toJson()}" }
        responseObserver.onNext(DataProcessorInfo.newBuilder().setName(cfg.name).setVersion(cfg.version).build())
        responseObserver.onCompleted()
        knownCrawlers += request.id
        LOGGER.info { "Handshake accepted" }
    }

    override fun intervalStart(request: IntervalInfo, responseObserver: StreamObserver<Empty>) {
        LOGGER.info { "Start of interval from ${request.startTime.toInstant()} to ${request.endTime.toInstant()}" }
        currentTime = request.endTime.toInstant()
        _events.time(currentTime)
        _messages.time(currentTime)
        responseObserver.onNext(Empty.getDefaultInstance())
        responseObserver.onCompleted()
    }

    override fun sendEvent(request: EventDataRequest, responseObserver: StreamObserver<EventResponse>) {
        LOGGER.info { "Received ${request.eventDataCount} event(s) from ${request.id.toJson()}" }
        if (request.id !in knownCrawlers) {
            LOGGER.warn { "${request.id.toJson()} crawler in unknown" }
            responseObserver.onNext(EventResponse.newBuilder().setStatus(CrawlerStatus.newBuilder().setHandshakeRequired(true)).build())
            responseObserver.onCompleted()
            return
        }
        try {
            request.eventDataList.forEach { _events.next(it, currentTime) }
            responseObserver.onNext(EventResponse.newBuilder().setStatus(CrawlerStatus.getDefaultInstance()).build())
            responseObserver.onCompleted()
        } catch (ex: Exception) {
            LOGGER.error(ex) { "Cannot process request from crawler ${request.id.toJson()}" }
            responseObserver.onError(Status.INTERNAL.withDescription(ex.message).asRuntimeException())
        }
    }

    override fun sendMessage(request: MessageDataRequest, responseObserver: StreamObserver<MessageResponse>) {
        LOGGER.info { "Received ${request.messageDataCount} message(s) from ${request.id.toJson()}" }
        if (request.id !in knownCrawlers) {
            LOGGER.warn { "${request.id.toJson()} crawler in unknown" }
            responseObserver.onNext(MessageResponse.newBuilder().setStatus(CrawlerStatus.newBuilder().setHandshakeRequired(true)).build())
            responseObserver.onCompleted()
            return
        }
        try {
            request.messageDataList.forEach { group -> group.messageItemList.forEach { _messages.next(it.message, currentTime) } }
            responseObserver.onNext(MessageResponse.newBuilder().setStatus(CrawlerStatus.getDefaultInstance()).build())
            responseObserver.onCompleted()
        } catch (ex: Exception) {
            LOGGER.error(ex) { "Cannot process request from crawler ${request.id.toJson()}" }
            responseObserver.onError(Status.INTERNAL.withDescription(ex.message).asRuntimeException())
        }
    }

    private class ProxySource<T> : AbstractSource<T>() {
        override fun onStart() = Unit
        fun next(data: T, time: Instant) {
            notify(data, time)
        }

        fun time(time: Instant) {
            refreshTime(time)
        }
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { }
    }
}
