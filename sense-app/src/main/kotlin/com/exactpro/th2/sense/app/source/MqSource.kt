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

package com.exactpro.th2.sense.app.source

import java.time.Instant
import com.exactpro.th2.common.schema.message.MessageRouter
import com.exactpro.th2.common.schema.message.SubscriberMonitor

abstract class MqSource<T, R>(
    private val router: MessageRouter<T>,
    private val attributes: Array<String>,
) : AbstractSource<R>() {
    private lateinit var subscription: SubscriberMonitor

    override fun onStart() {
        subscription = router.subscribeAll({ _, data ->
            val time = Instant.now()
            transform(data).forEach {
                notify(it, time)
            }
        }, *attributes)
    }

    protected abstract fun transform(data: T): Collection<R>

    override fun onClose() {
        subscription.unsubscribe()
    }
}