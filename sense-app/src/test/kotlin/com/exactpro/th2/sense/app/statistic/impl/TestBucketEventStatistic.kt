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
import com.exactpro.th2.common.message.toTimestamp
import com.exactpro.th2.sense.api.Event
import com.exactpro.th2.sense.api.EventType
import com.exactpro.th2.sense.app.cfg.StatisticConfiguration
import com.exactpro.th2.sense.app.statistic.EventBucketStat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import strikt.api.expectThat
import strikt.assertions.hasEntry
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
internal class TestBucketEventStatistic {
    private val stat = BucketEventStatistic(StatisticConfiguration.DEFAULT_EVENTS_BUCKETS)

    @ParameterizedTest
    @MethodSource("buckets")
    fun `adds events to correct bucket`(bucket: Duration) {
        val type = EventType("test")
        val time = Instant.now()
        val event = Event.newBuilder()
            .setStartTimestamp((time - (bucket.minusMillis(1))).toTimestamp())
            .build()
        stat.update(type, event, time)

        expectThat(stat.stats)
            .isEqualTo(
                StatisticConfiguration.DEFAULT_EVENTS_BUCKETS.associateWith {
                    if (it == bucket) {
                        listOf(EventBucketStat(type, 1))
                    } else {
                        emptyList()
                    }
                }
            )
    }

    @ParameterizedTest
    @MethodSource("buckets")
    fun `transfers data to next`(bucket: Duration) {
        val type = EventType("test")
        val time = Instant.now()
        val eventTime = time - (bucket.minusMillis(1))
        val event = Event.newBuilder()
            .setStartTimestamp(eventTime.toTimestamp())
            .build()
        stat.update(type, event, time)

        stat.refresh(time + bucket)

        expectThat(stat.stats)
            .isEqualTo(
                buildMap {
                    var currentBucketIndex = -2
                    StatisticConfiguration.DEFAULT_EVENTS_BUCKETS.forEachIndexed { index, currentBucket ->
                        if (currentBucket == bucket) {
                            currentBucketIndex = index
                        }
                        when (index) {
                            currentBucketIndex + 1 -> put(currentBucket, listOf(EventBucketStat(type, 1)))
                            else -> put(currentBucket, emptyList())
                        }
                    }
                }
            )
    }

    @Test
    fun `correctly merges data with next bucket`() {
        val type = EventType("test")
        val time = Instant.now()
        val bucket = StatisticConfiguration.DEFAULT_EVENTS_BUCKETS.first()
        val secondBucket = StatisticConfiguration.DEFAULT_EVENTS_BUCKETS.first { it > bucket }

        val firstEventTime = time - (bucket.minusMillis(1))
        val first = Event.newBuilder()
            .setStartTimestamp(firstEventTime.toTimestamp())
            .build()
        val second = Event.newBuilder()
            .setStartTimestamp((firstEventTime - secondBucket.minus(bucket).minusMillis(2)).toTimestamp())
            .build()
        stat.update(type, first, time)
        stat.update(type, second, time)

        stat.refresh(time.plusMillis(2))

        expectThat(stat.stats)
            .isNotEmpty()
            .hasSize(StatisticConfiguration.DEFAULT_EVENTS_BUCKETS.size)
            .hasEntry(bucket, emptyList())
            .hasEntry(secondBucket, listOf(EventBucketStat(type, 2)))
    }

    @Test
    fun `drops from the last bucket`() {
        val type = EventType("test")
        val time = Instant.now()
        val bucket = StatisticConfiguration.DEFAULT_EVENTS_BUCKETS.last()

        val firstEventTime = time - (bucket.minusMillis(1))
        val event = Event.newBuilder()
            .setStartTimestamp(firstEventTime.toTimestamp())
            .build()
        stat.update(type, event, time)

        stat.refresh(time.plusMillis(2))

        expectThat(stat.stats)
            .isNotEmpty()
            .hasSize(StatisticConfiguration.DEFAULT_EVENTS_BUCKETS.size)
            .hasEntry(bucket, emptyList())
    }

    companion object {
        @JvmStatic
        fun buckets(): List<Arguments> = StatisticConfiguration.DEFAULT_EVENTS_BUCKETS.map { Arguments.arguments(it) }
    }
}