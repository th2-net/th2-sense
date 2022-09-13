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
import com.exactpro.th2.sense.api.EventNotifier
import com.exactpro.th2.sense.api.EventType
import com.exactpro.th2.sense.api.Notification
import com.exactpro.th2.sense.api.NotificationInfo
import com.exactpro.th2.sense.app.notifier.event.NotificationRequest
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal class TestNotificationEventStatistic {
    private val notifier = mock<EventNotifier> { }

    private val onError = mock<(String, Throwable) -> Unit> { }
    private val notificationStat = NotificationEventStatistic(listOf(notifier), onError)

    @ParameterizedTest(name = "Types: {0}")
    @ValueSource(ints = [1, 2, 5, 10])
    fun `notifies if single event received`(typesCount: Int) {
        val eventTypes = (0 until typesCount).associate {
            EventType("test$it") to 1L
        }
        notificationStat.submitNotification(
            NotificationRequest("test", eventTypes)
        )
        val currentTime = Instant.now()
        eventTypes.forEach { (type, _) ->
            notificationStat.update(type, Event.getDefaultInstance(), currentTime)
        }

        val notification = argumentCaptor<Notification>().apply {
            inOrder(notifier) {
                verify(notifier).notificationRegistered(eq(NotificationInfo("test")))
                verify(notifier).notify(capture())
            }
        }.firstValue
        verifyZeroInteractions(onError)

        expectThat(notification).isEqualTo(
            Notification(
                "test",
                currentTime,
                eventTypes,
            )
        )
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 100])
    fun `notifies if several expectations submitted`(receivedEventsBefore: Int) {
        val eventType = EventType("test")
        notificationStat.submitNotification(
            NotificationRequest(
                "another",
                mapOf(
                    eventType to Long.MAX_VALUE
                )
            )
        )

        repeat(receivedEventsBefore) {
            notificationStat.update(eventType, Event.getDefaultInstance(), Instant.now())
        }

        notificationStat.submitNotification(
            NotificationRequest(
                "test",
                mapOf(
                    eventType to 1
                )
            )
        )
        val currentTime = Instant.now()
        notificationStat.update(eventType, Event.getDefaultInstance(), currentTime)

        val notification = argumentCaptor<Notification>().apply {
            inOrder(notifier) {
                verify(notifier).notificationRegistered(eq(NotificationInfo("another")))
                verify(notifier).notificationRegistered(eq(NotificationInfo("test")))
                verify(notifier).notify(capture())
            }
        }.firstValue
        verifyZeroInteractions(onError)

        expectThat(notification).isEqualTo(
            Notification(
                "test",
                currentTime,
                mapOf(
                    eventType to 1
                )
            )
        )
    }
}