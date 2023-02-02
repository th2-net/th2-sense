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

import com.exactpro.th2.common.grpc.Event as GrpcEvent
import com.exactpro.th2.common.grpc.EventID
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.util.toInstant
import com.exactpro.th2.processor.api.IProcessor
import com.exactpro.th2.sense.api.Event
import com.exactpro.th2.sense.app.source.AbstractSource
import com.exactpro.th2.sense.app.source.Source
import com.google.auto.service.AutoService
import com.google.protobuf.TextFormat.shortDebugString
import mu.KotlinLogging
import java.time.Instant

@AutoService(IProcessor::class)
class SenseProcessor: IProcessor {
    private val _messages = ProxySource<Message>()
    private val _events = ProxySource<Event>()

    val messages: Source<Message>
        get() = _messages
    val events: Source<Event>
        get() = _events

    override fun handle(intervalEventId: EventID, event: GrpcEvent) {
        try {
            _events.next(event.toModel(), event.endTimestamp.toInstant())
        } catch (ex: Exception) {
            LOGGER.error(ex) { "Cannot process event from crawler: ${shortDebugString(event.id)}" }
        }
    }

    override fun handle(intervalEventId: EventID, message: Message) {
        try {
            _messages.next(message, message.metadata.id.timestamp.toInstant())
        } catch (ex: Exception) {
            LOGGER.error(ex) { "Cannot process event from crawler: ${shortDebugString(message.metadata.id)}" }
        }
    }

    private class ProxySource<T> : AbstractSource<T>() {
        override fun onStart() = Unit
        fun next(data: T, time: Instant) {
            notify(data, time)
        }
    }

    override fun close() {}

    companion object {
        private val LOGGER = KotlinLogging.logger { }
    }
}

private fun GrpcEvent.toModel(): Event {
    return Event.newBuilder()
        .setEventName(name)
        .setEventType(type)
        .setEndTimestamp(endTimestamp)
        .setBody(body)
        .addAllAttachedMessageId(attachedMessageIdsList)
        .also {
            if (hasParentId()) {
                it.parentEventId = parentId
            }
        }
        .build()
}