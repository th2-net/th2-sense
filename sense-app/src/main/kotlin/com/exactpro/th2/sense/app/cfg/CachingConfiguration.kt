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

import kotlin.math.roundToLong

/**
 * 10% of max memory or 10Mb
 */
private val DEFAULT_MAX_WEIGHT: Long =
    (Runtime.getRuntime().maxMemory() * 0.1).roundToLong().coerceAtLeast(1024 * 1024 * 10)

data class CachingConfiguration(
    val maxSize: Long? = null,
    val maxWeightInBytes: Long = DEFAULT_MAX_WEIGHT,
)
