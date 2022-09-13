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

package com.exactpro.th2.sense.app.notifier.grpc

import com.exactpro.th2.sense.grpc.NotificationRequest as GrpcNotificationRequest
import java.util.stream.Collectors
import com.exactpro.th2.common.message.toJson
import com.exactpro.th2.sense.api.EventType
import com.exactpro.th2.sense.app.notifier.event.NotificationRequest
import com.exactpro.th2.sense.app.notifier.event.NotificationService
import com.exactpro.th2.sense.grpc.ExpectedEvent
import com.exactpro.th2.sense.grpc.SenseGrpc.SenseImplBase
import com.google.protobuf.Empty
import io.grpc.Status
import io.grpc.stub.StreamObserver
import mu.KotlinLogging

class SenseService(
    private val notificationService: NotificationService,
) : SenseImplBase() {

    override fun notifyOnEvents(request: GrpcNotificationRequest, responseObserver: StreamObserver<Empty>) {
        LOGGER.info { "Received request ${request.toJson()}" }
        try {
            notificationService.submitNotification(request.toInternalRequest())
            responseObserver.onNext(Empty.getDefaultInstance())
            responseObserver.onCompleted()
        } catch (ex: Exception) {
            responseObserver.onError(
                ex.toStatus(request).asRuntimeException()
            )
        }
    }

    private fun Exception.toStatus(request: GrpcNotificationRequest): Status = when (this) {
        is IllegalArgumentException -> {
            LOGGER.error(this) { "Incorrect parameters in the request ${request.toJson()}" }
            Status.INVALID_ARGUMENT
        }
        is IllegalStateException -> {
            LOGGER.error(this) { "Incorrect state of the sense to submit the request ${request.toJson()}" }
            Status.INTERNAL
        }
        else -> {
            LOGGER.error(this) { "Cannot execute request ${request.toJson()}" }
            Status.UNKNOWN
        }
    }.withDescription(message)

    companion object {
        private val LOGGER = KotlinLogging.logger { }
    }
}

private fun GrpcNotificationRequest.toInternalRequest(): NotificationRequest {
    return NotificationRequest(
        name = name.apply { require(isNotBlank()) { "name cannot be blank" } },
        eventsByType = expectedEventsList.stream().collect(
            Collectors.toUnmodifiableMap(
                { it.toEventType() },
                { it.expectedCount },
            )
        )
    )
}
private fun ExpectedEvent.toEventType(): EventType = EventType(type.name)
private val ExpectedEvent.expectedCount: Long
    get() = count.apply { require(count > 0) { "negative count for ${type.name} event type" } }