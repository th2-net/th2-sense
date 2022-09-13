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

import java.time.Duration
import java.time.Instant
import java.util.SortedMap
import java.util.TreeMap
import javax.annotation.concurrent.NotThreadSafe
import com.exactpro.th2.common.util.toInstant
import com.exactpro.th2.sense.api.Event
import com.exactpro.th2.sense.api.EventType
import com.exactpro.th2.sense.app.statistic.EventStatistic
import mu.KotlinLogging

@NotThreadSafe
class BucketEventStatistic(
    timeBuckets: Collection<Duration>,
) : EventStatistic {
    private val buckets: MutableMap<Duration, EventBucket>
    private val lastBucket: Duration

    init {
        val sortedBuckets = timeBuckets.sorted()
        sortedBuckets.first().run {
            check(!isNegative && !isZero) { "Buckets must be positive values but first value was $this" }
        }
        buckets = sortedBuckets.associateWithTo(linkedMapOf()) { EventBucket() }
        lastBucket = sortedBuckets.last()
    }

    override fun update(type: EventType, event: Event, currentTime: Instant) {
        val startTimestamp = event.startTimestamp.toInstant()
        LOGGER.trace { "Finding bucket for event ${event.eventId.id} (start=$startTimestamp, current=$currentTime)" }
        val delta = Duration.between(startTimestamp, currentTime)
        val bucketKey = buckets.keys.find { it > delta } ?: run {
            LOGGER.debug { "Event ${event.eventId.id} is outside of any bucket. Last bucket max time: ${currentTime.plus(lastBucket)}, Event time: $startTimestamp" }
            return
        }
        val bucket = buckets.getValue(bucketKey)
        bucket.update(type, startTimestamp)
        LOGGER.trace { "Bucket $bucketKey is updated. Current stats: ${bucket.stats}" }
    }

    val stats: Map<Duration, List<EventBucketStat>>
        get() = buckets.mapValues { it.value.stats }

    override fun refresh(currentTime: Instant) {
        var fromPrev: Map<EventType, SortedMap<Instant, Int>>? = null
        buckets.forEach { (duration, bucket) ->
            fromPrev?.also { bucket.merge(it) }
            fromPrev = bucket.clean(currentTime - duration)
        }
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { }
    }
}

private class EventBucket {
    private val startTimes: MutableMap<EventType, SortedMap<Instant, Int>> = hashMapOf()

    fun update(type: EventType, startTimestamp: Instant) {
        startTimes.computeIfAbsent(type) { TreeMap() }.merge(startTimestamp, 1, Int::plus)
    }

    fun merge(others: Map<EventType, SortedMap<Instant, Int>>) {
        others.forEach { (type, counts) ->
            startTimes.merge(type, counts) { old, new ->
                new.forEach { (time, count) -> old.merge(time, count, Int::plus) }
                old
            }
        }
    }

    val stats: List<EventBucketStat>
        get() = startTimes.mapNotNull { (type, times) ->
            when {
                times.isEmpty() -> null
                else -> EventBucketStat(type, times.values.sum())
            }
        }

    fun clean(end: Instant): Map<EventType, SortedMap<Instant, Int>> {
        val transferToAnother = startTimes.asSequence().mapNotNull { (type, times) ->
            when {
                times.isEmpty() -> null
                times.firstKey() <= end -> times.headMap(end)
                else -> null
            }?.takeIf { it.isNotEmpty() }?.let { type to it.toSortedMap() }
        }.toMap()
        startTimes.replaceAll { _, map ->
            map.run {
                when {
                    isEmpty() -> this
                    lastKey() > end -> tailMap(end).toSortedMap()
                    else -> also { it.clear() }
                }
            }
        }
        return transferToAnother
    }

    override fun toString(): String {
        return stats.toString()
    }
}

data class EventBucketStat(val type: EventType, val count: Int)