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

package com.exactpro.th2.sense.event.dsl.util

import java.time.Instant
import com.exactpro.th2.common.grpc.EventID
import com.exactpro.th2.common.grpc.EventStatus
import com.exactpro.th2.sense.api.Event
import com.google.protobuf.Timestamp
import com.google.protobuf.UnsafeByteOperations

fun createEvent(
    id: EventID = createEventId("${System.nanoTime()}"),
    name: String = "Default Name",
    type: String = "Default Time",
    startTime: Instant = Instant.now(),
    endTime: Instant = startTime.plusSeconds(1),
    status: EventStatus = EventStatus.SUCCESS,
    parentEventID: EventID? = null,
    body: String = "[]",
): Event = Event.newBuilder()
    .setEventId(id)
    .setStatus(status)
    .setEventName(name)
    .setEventType(type)
    .setStartTimestamp(startTime.toTimestamp())
    .setEndTimestamp(endTime.toTimestamp())
    .apply {
        parentEventID?.also { parentEventId = it }
    }
    .setBody(UnsafeByteOperations.unsafeWrap(body.toByteArray(Charsets.UTF_8)))
    .build()

fun createEventId(id: String): EventID = EventID.newBuilder().setId(id).build()

private fun Instant.toTimestamp(): Timestamp = Timestamp.newBuilder()
    .setSeconds(epochSecond)
    .setNanos(nano)
    .build()