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

import com.exactpro.th2.common.grpc.MessageID
import com.exactpro.th2.dataprovider.grpc.DataProviderService
import com.exactpro.th2.sense.api.Message
import com.exactpro.th2.sense.api.MessageProvider
import com.exactpro.th2.sense.app.cfg.CachingConfiguration
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import mu.KotlinLogging

class MessageProviderImpl(
    private val provider: DataProviderService,
    cachingConfiguration: CachingConfiguration,
) : MessageProvider {
    private val messagesCache: Cache<MessageID, List<Message>> = CacheBuilder.newBuilder()
//        .maximumSize(cachingConfiguration.maxSize)
        .maximumWeight(cachingConfiguration.maxWeightInBytes)
        .weigher { _: MessageID, value: List<Message> ->
            value.sumOf { it.serializedSize }
        }
        .build()

    override fun get(messageID: MessageID): List<Message> {
        return messagesCache.get(messageID) {
            provider.getMessage(messageID).messageItemList.map { it.message }
        }
    }

    override fun get(messageIDs: Set<MessageID>): List<Message> {
        return messageIDs.asSequence().flatMap { get(it).asSequence() }.toList()
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { }
    }
}