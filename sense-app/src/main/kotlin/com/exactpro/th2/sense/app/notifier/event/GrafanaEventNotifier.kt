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

package com.exactpro.th2.sense.app.notifier.event

import java.util.concurrent.ConcurrentHashMap
import com.exactpro.th2.sense.api.EventNotifier
import com.exactpro.th2.sense.api.Notification
import com.exactpro.th2.sense.api.NotificationInfo
import com.exactpro.th2.sense.api.NotificationName
import io.prometheus.client.Gauge
import mu.KotlinLogging

class GrafanaEventNotifier : EventNotifier {
    private val metricByNotification: MutableMap<NotificationName, Gauge> = ConcurrentHashMap()

    override fun notificationRegistered(info: NotificationInfo) {
        metricByNotification.compute(info.name) { key, oldValue ->
            when (oldValue) {
                null -> {
                    LOGGER.info { "Registering new notification $key" }
                    Gauge.build(createMetricName(key), info.description ?: "")
                        .register()
                }
                else -> oldValue.apply { set(0.0) }
            }
        }
    }

    override fun notify(notification: Notification) {
        val metric = metricByNotification[notification.name] ?: error("unknown notification ${notification.name}")
        metric.set(1.0)
        LOGGER.info { "Notification ${notification.name} is received" }
    }

    private fun createMetricName(name: NotificationName): String {
        return "th2_sense_${name}_notification"
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { }
    }
}