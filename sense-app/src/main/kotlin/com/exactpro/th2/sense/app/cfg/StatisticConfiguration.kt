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

package com.exactpro.th2.sense.app.cfg

import java.time.Duration

data class StatisticConfiguration(
    val eventBuckets: Set<Duration> = DEFAULT_EVENTS_BUCKETS,
    val messagesBuckets: Set<Duration> = DEFAULT_MESSAGES_BUCKETS,
) {
    companion object {
        val DEFAULT_EVENTS_BUCKETS: Set<Duration> = sortedSetOf(Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofHours(1))
        val DEFAULT_MESSAGES_BUCKETS: Set<Duration> = sortedSetOf(Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofHours(1))
    }
}
