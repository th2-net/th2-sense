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

package com.exactpro.th2.sense.app.notifier.http

import com.exactpro.th2.sense.api.EventType
import com.exactpro.th2.sense.api.NotificationName
import com.exactpro.th2.sense.app.cfg.HttpServerConfiguration
import com.exactpro.th2.sense.app.notifier.event.NotificationRequest
import com.exactpro.th2.sense.app.notifier.event.NotificationService
import io.javalin.Javalin
import io.javalin.http.BadRequestResponse
import io.javalin.http.ConflictResponse
import io.javalin.http.Context
import io.javalin.http.InternalServerErrorResponse
import io.javalin.plugin.json.JavalinJackson
import mu.KotlinLogging

class SenseHttpServer(
    private val cfg: HttpServerConfiguration,
    private val notificationService: NotificationService,
) : AutoCloseable {
    private val server = Javalin.create {
        it.jsonMapper(JavalinJackson())
    }.apply {
        post("/event/notification", this@SenseHttpServer::submitNotification)
        if (cfg.host == null) {
            start(cfg.port)
        } else {
            start(cfg.host, cfg.port)
        }
    }

    private fun submitNotification(ctx: Context) {
        val body = ctx.bodyAsClass<HttpNotificationRequest>()
        LOGGER.info { "Received request for submitting notification $body" }
        try {
            notificationService.submitNotification(body.toInternalRequest())
            ctx.json(Response("${body.name} submitted"))
            LOGGER.info { "Request submitted for notification ${body.name}" }
        } catch (ex: IllegalArgumentException) {
            throw BadRequestResponse(checkNotNull(ex.message))
        } catch (ex: IllegalStateException) {
            throw ConflictResponse(checkNotNull(ex.message))
        } catch (ex: Exception) {
            throw InternalServerErrorResponse(checkNotNull(ex.message))
        }
    }

    private data class HttpNotificationRequest(
        val name: NotificationName,
        val expectedEvents: Map<EventType, Long>,
        val description: String? = null,
    )

    private data class Response(
        val message: String
    )

    override fun close() {
        server.close()
    }

    private fun HttpNotificationRequest.toInternalRequest(): NotificationRequest = NotificationRequest(name, expectedEvents, description)

    companion object {
        private val LOGGER = KotlinLogging.logger { }
    }
}
