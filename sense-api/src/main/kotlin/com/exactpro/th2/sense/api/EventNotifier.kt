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

package com.exactpro.th2.sense.api

import java.time.Instant

typealias NotificationName = String
interface EventNotifier {
    /**
     * The method will be called whenever a new notification is registered
     */
    fun notificationRegistered(info: NotificationInfo)
    /**
     * The method will be invoked when corresponding number of events is collected
     */
    fun notify(notification: Notification)
}

data class NotificationInfo(
    val name: NotificationName,
    val description: String? = null,
)

data class Notification(
    val name: NotificationName,
    val sourceTimestamp: Instant,
    val achievedCounts: Map<EventType, Long>,
    val description: String? = null,
)