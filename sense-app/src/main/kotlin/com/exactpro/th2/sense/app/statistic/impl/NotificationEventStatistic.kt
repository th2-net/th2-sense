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
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.annotation.concurrent.GuardedBy
import kotlin.concurrent.read
import kotlin.concurrent.write
import com.exactpro.th2.sense.api.Event
import com.exactpro.th2.sense.api.EventNotifier
import com.exactpro.th2.sense.api.EventType
import com.exactpro.th2.sense.api.Notification
import com.exactpro.th2.sense.api.NotificationInfo
import com.exactpro.th2.sense.app.notifier.event.NotificationName
import com.exactpro.th2.sense.app.notifier.event.NotificationRequest
import com.exactpro.th2.sense.app.notifier.event.NotificationService
import com.exactpro.th2.sense.app.statistic.EventStatistic
import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import mu.KotlinLogging

private typealias ExpectationName = String

class NotificationEventStatistic(
    private val notificationListeners: List<EventNotifier>,
    private val onError: (String, Throwable) -> Unit
) : EventStatistic, NotificationService {
    private val lock = ReentrantReadWriteLock()

    @GuardedBy("lock")
    private val counters: MutableMap<EventType, Long> = hashMapOf()

    @GuardedBy("lock")
    private val expectationByName: MutableMap<ExpectationName, ExpectationHolder> = hashMapOf()

    @GuardedBy("lock")
    private val expectationsByType: Multimap<EventType, ExpectationHolder> = HashMultimap.create()

    @GuardedBy("lock")
    private val expectationToTypes: Multimap<ExpectationName, EventType> = HashMultimap.create()

    override fun submitNotification(notificationRequest: NotificationRequest): NotificationName {
        require(notificationRequest.eventsByType.isNotEmpty()) { "at least one type must be expected" }
        val actualExpectedCounts: Map<EventType, Long> = lock.read {
            notificationRequest.eventsByType.mapValues { (type, count) ->
                require(count > 0) { "positive number must be expected for type $type but was $count" }
                counters.getOrDefault(type, 0) + count
            }
        }
        val holder = ExpectationHolder(
            notificationRequest.name,
            actualExpectedCounts,
            notificationRequest.eventsByType,
            notificationRequest.description,
        )
        lock.write {
            check(holder.name !in expectationToTypes.keys()) { "duplicated expectation '${holder.name}'" }
            expectationByName[holder.name] = holder
            expectationToTypes.putAll(holder.name, actualExpectedCounts.keys)
            actualExpectedCounts.keys.forEach {
                expectationsByType.put(it, holder)
            }
            registered(holder)
            return holder.name
        }
    }

    override fun awaitNotification(name: NotificationName, callback: () -> Unit) {
        lock.write {
            val holder = expectationByName[name] ?: run {
                LOGGER.info { "No expectation with name $name submitted" }
                callback()
                return
            }
            holder.addCallback(callback)
            LOGGER.info { "Callback added to expectation $name" }
        }
    }

    override fun removeNotification(name: NotificationName) {
        lock.write {
            val holder = expectationByName.remove(name) ?: error("unknown notification name $name")
            val eventTypes: Collection<EventType> = expectationToTypes.removeAll(name)
            eventTypes.forEach {
                expectationsByType.remove(it, holder)
            }
        }
    }

    override fun update(type: EventType, event: Event, currentTime: Instant) {
        val completed = lock.write {
            val expectations: MutableCollection<ExpectationHolder> = expectationsByType[type].ifEmpty { return }
            val count = checkNotNull(counters.merge(type, 1L, Long::plus)) { "count must not be null" }
            val completed = expectations.filter {
                it.updateCount(type, count)
            }
            expectations.removeAll(completed)
            completed.forEach(::cleanExpectation)

            completed
        }

        completed.forEach { notify(it, currentTime) }
    }

    override fun refresh(currentTime: Instant) = Unit

    private fun cleanExpectation(expectation: ExpectationHolder) {
        expectationByName.remove(expectation.name)
        expectationToTypes.removeAll(expectation.name).forEach { mappedType ->
            val expectations = expectationsByType.get(mappedType)
            expectations.remove(expectation)
            if (expectations.isEmpty()) {
                counters.remove(mappedType)
            }
        }
    }

    private fun registered(expectation: ExpectationHolder) {
        val notificationInfo = NotificationInfo(
            expectation.name,
            expectation.description,
        )
        LOGGER.info { "Registering new expectation: $expectation" }
        eachListener("register") { notificationRegistered(notificationInfo) }
    }

    private fun notify(expectation: ExpectationHolder, currentTime: Instant) {
        val notification = Notification(
            expectation.name,
            currentTime,
            expectation.relativeExpectedByType,
            expectation.description,
        )
        runCatching {
            LOGGER.info { "Executing notification ${expectation.name} callback" }
            expectation.callbacks.forEach {
                try {
                    it()
                } catch (ex: Exception) {
                    LOGGER.error(ex) { "cannot execute callback" }
                }
            }
        }.onFailure {
            LOGGER.error(it) { "Cannot execute notification callback for ${expectation.name}" }
        }
        LOGGER.info { "Received notification for $expectation" }
        eachListener("notification") { notify(notification) }
    }

    private inline fun eachListener(actionName: String, action: EventNotifier.() -> Unit) {
        notificationListeners.forEach { listener ->
            listener.runCatching {
                action()
            }.onFailure {
                LOGGER.error(it) { "cannot process $actionName by ${listener::class}" }
                onError("Cannot process $actionName by ${listener::class.simpleName}", it)
            }
        }
    }

    private class ExpectationHolder(
        val name: ExpectationName,
        private val expectedByType: Map<EventType, Long>,
        val relativeExpectedByType: Map<EventType, Long>,
        val description: String? = null,
    ) {
        private val achieved: MutableSet<EventType> = hashSetOf()
        private val _callbacks: MutableList<() -> Unit> = arrayListOf()
        val callbacks: List<() -> Unit>
            get() = _callbacks

        /**
         * @return `true` if all expectation are achieved. Otherwise, returns `false`
         */
        fun updateCount(type: EventType, count: Long): Boolean {
            if (isAchieved) return true
            val expectedCount = expectedByType[type] ?: return false
            if (expectedCount <= count) {
                achieved += type
            }
            return isAchieved
        }

        fun addCallback(callback: () -> Unit) {
            _callbacks += callback
        }

        override fun toString(): String {
            return "ExpectationHolder(name='$name', relativeExpectedByType=$relativeExpectedByType, description=$description)"
        }

        private val isAchieved: Boolean
            get() = achieved.size == expectedByType.size


    }

    companion object {
        private val LOGGER = KotlinLogging.logger { }
    }
}