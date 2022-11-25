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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.stream.Collectors
import com.exactpro.th2.common.message.toJavaDuration
import com.exactpro.th2.common.message.toJson
import com.exactpro.th2.sense.api.EventType
import com.exactpro.th2.sense.app.cfg.GrpcSenseConfiguration
import com.exactpro.th2.sense.app.notifier.event.NotificationRequest
import com.exactpro.th2.sense.app.notifier.event.NotificationService
import com.exactpro.th2.sense.grpc.AwaitNotificationRequest
import com.exactpro.th2.sense.grpc.ExpectedEvent
import com.exactpro.th2.sense.grpc.SenseGrpc.SenseImplBase
import com.google.protobuf.Empty
import io.grpc.Status
import io.grpc.stub.StreamObserver
import mu.KotlinLogging

class SenseService(
    private val configuration: GrpcSenseConfiguration,
    private val notificationService: NotificationService,
) : SenseImplBase(), AutoCloseable {
    private val executor = Executors.newSingleThreadScheduledExecutor()
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

    override fun awaitNotificationOnEvents(request: AwaitNotificationRequest, responseObserver: StreamObserver<Empty>) {
        LOGGER.info { "Received request ${request.toJson()}" }
        try {
            val awaitTimeout = request.run {
                if (hasTimeout()) timeout.toJavaDuration() else configuration.defaultAwaitTimeout
            }
            require(awaitTimeout.run { !isZero && !isNegative }) { "invalid await timeout" }
            val completion = CompletableFuture<Any?>()
            val notificationRequest = request.request.toInternalRequest { completion.complete(null) }
            val future = completion.whenComplete { _, error ->
                if (error != null) {
                    runCatching { notificationService.removeNotification(notificationRequest.name) }
                        .onFailure { LOGGER.error(it) { "cannot remove notification ${notificationRequest.name}" } }
                    responseObserver.onError(error.toStatus(request.request).asRuntimeException())
                } else {
                    responseObserver.onNext(Empty.getDefaultInstance())
                    responseObserver.onCompleted()
                }
            }
            notificationService.submitNotification(notificationRequest)
            if (!future.isDone) {
                executor.schedule(
                    { future.completeExceptionally(TimeoutException("notification was not invoked withing $awaitTimeout")) },
                    awaitTimeout.toMillis(),
                    TimeUnit.MILLISECONDS,
                )
            }
            LOGGER.info { "Request ${request.toJson()} processed" }
        } catch (ex: Exception) {
            responseObserver.onError(
                ex.toStatus(request.request).asRuntimeException()
            )
        }
    }

    override fun close() {
        LOGGER.info { "Closing executor" }
        executor.shutdown()
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            LOGGER.warn { "Force executor close" }
            val tasks = executor.shutdownNow()
            LOGGER.warn { "${tasks.size} task(s) left in executor" }
        }
    }

    private fun Throwable.toStatus(request: GrpcNotificationRequest): Status = when (this) {
        is IllegalArgumentException -> {
            LOGGER.error(this) { "Incorrect parameters in the request ${request.toJson()}" }
            Status.INVALID_ARGUMENT
        }
        is IllegalStateException -> {
            LOGGER.error(this) { "Incorrect state of the sense to submit the request ${request.toJson()}" }
            Status.INTERNAL
        }
        is TimeoutException -> {
            LOGGER.error(this) { "Notification $request was not invoked within the specified timeout" }
            Status.ABORTED
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

private fun GrpcNotificationRequest.toInternalRequest(onCompleted: () -> Unit = {}): NotificationRequest {
    return NotificationRequest(
        name = name.apply { require(isNotBlank()) { "name cannot be blank" } },
        eventsByType = expectedEventsList.stream().collect(
            Collectors.toUnmodifiableMap(
                { it.toEventType() },
                { it.expectedCount },
            )
        ),
        callback = onCompleted,
    )
}
private fun ExpectedEvent.toEventType(): EventType = EventType(type.name)
private val ExpectedEvent.expectedCount: Long
    get() = count.apply { require(count > 0) { "negative count for ${type.name} event type" } }