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

package com.exactpro.th2.sense.event.rule

import com.exactpro.th2.sense.api.EventProcessor
import com.exactpro.th2.sense.api.EventProcessorFactory
import com.exactpro.th2.sense.api.ProcessorId
import com.exactpro.th2.sense.api.ProcessorSettings
import com.exactpro.th2.sense.event.dsl.EventRuleBuilder
import com.exactpro.th2.sense.internal.genericParameter

abstract class ConfigurableEventRuleFactory<S : ProcessorSettings>(
    name: String,
) : EventProcessorFactory<S> {

    override val settings: Class<out S> = this::class.genericParameter(0, ConfigurableEventRuleFactory::class)
    init {
        require(name.isNotBlank()) { "rule name cannot be blank" }
    }

    override val id: ProcessorId = ProcessorId(name)

    override fun create(settings: S): EventProcessor = EventRule(id) { setup(settings) }

    protected abstract fun EventRuleBuilder.setup(settings: S)
}