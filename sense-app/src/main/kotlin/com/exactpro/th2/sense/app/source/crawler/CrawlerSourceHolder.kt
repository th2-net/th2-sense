package com.exactpro.th2.sense.app.source.crawler

import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.sense.api.Event
import com.exactpro.th2.sense.app.source.Source

object CrawlerSourceHolder {
    private val sources = mutableMapOf<String, CrawlerSources>()
    fun set(name: String, messages: Source<Message>, events: Source<Event>) {
        sources[name] = CrawlerSources(messages, events)
    }

    operator fun get(name: String): CrawlerSources? {
        return sources[name]
    }
}

data class CrawlerSources(val messages: Source<Message>, val events: Source<Event>)