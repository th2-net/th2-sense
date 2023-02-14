/*
 * Copyright 2022-2023 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.sense.api

import com.exactpro.th2.dataprovider.lw.grpc.EventResponse
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

typealias Event = EventResponse

interface EventProcessor : Processor {
    fun ProcessorContext.process(event: Event): EventResult
}

sealed class EventResult {
    class Accept(
        val type: EventType
    ) : EventResult()

    object Skip : EventResult()
}

data class EventType(
    @get:JsonValue
    val type: String
) {
    companion object {
        @JsonCreator
        @JvmStatic
        fun create(type: String): EventType = EventType(type)
    }
}