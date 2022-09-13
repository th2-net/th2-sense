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

package com.exactpro.th2.sense.app.statistic.impl

import java.time.Instant
import com.exactpro.th2.sense.api.Event
import com.exactpro.th2.sense.api.EventType
import com.exactpro.th2.sense.app.statistic.EventStatistic
import io.prometheus.client.Counter
import io.prometheus.client.Gauge

object GrafanaEventStatistic : EventStatistic {
    override fun update(type: EventType, event: Event, currentTime: Instant) {
        EVENTS_COUNTER.labels(event.status.name, type.type).inc()
    }

    override fun refresh(currentTime: Instant) {
        CURRENT_TIME.set(currentTime.toEpochMilli().toDouble())
    }

    private const val TYPE_LABEL = "type"
    private const val STATUS_LABEL = "status"
    private val EVENTS_COUNTER = Counter.build("th2_sense_event_counter", "counts number of events by usertype and event status")
        .labelNames(STATUS_LABEL, TYPE_LABEL)
        .register()
    private val CURRENT_TIME = Gauge.build("th2_sense_current_time_mls", "the current time for processing data")
        .register()
}