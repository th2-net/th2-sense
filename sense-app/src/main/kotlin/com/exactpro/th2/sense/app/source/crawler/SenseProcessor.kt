package com.exactpro.th2.sense.app.source.crawler

import com.exactpro.th2.common.grpc.Event as GrpcEvent
import com.exactpro.th2.common.grpc.EventID
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.message.getMessage
import com.exactpro.th2.common.util.toInstant
import com.exactpro.th2.processor.api.IProcessor
import com.exactpro.th2.sense.api.Event
import com.exactpro.th2.sense.app.cfg.ProcessorConfiguration
import com.exactpro.th2.sense.app.source.AbstractSource
import com.google.auto.service.AutoService
import com.google.protobuf.TextFormat.shortDebugString
import mu.KotlinLogging
import java.time.Instant

@AutoService(IProcessor::class)
class SenseProcessor(private val cfg: ProcessorConfiguration): IProcessor {
    private val messages = ProxySource<Message>()
    private val events = ProxySource<Event>()

    init {
        CrawlerSourceHolder.set(cfg.name, messages, events)
    }

    override fun handle(intervalEventId: EventID, event: GrpcEvent) {
        try {
            events.next(event.toModel(), event.endTimestamp.toInstant())
        } catch (ex: Exception) {
            LOGGER.error(ex) { "Cannot process event from crawler: ${shortDebugString(event.id)}" }
        }
    }

    override fun handle(intervalEventId: EventID, message: Message) {
        try {
            messages.next(message, message.metadata.id.timestamp.toInstant())
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