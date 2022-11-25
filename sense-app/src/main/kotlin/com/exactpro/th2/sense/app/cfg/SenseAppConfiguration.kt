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

import java.util.function.Function
import java.util.stream.Collectors
import com.exactpro.th2.sense.api.ProcessorId
import com.exactpro.th2.sense.api.ProcessorSettings
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.util.StdConverter

data class SenseAppConfiguration(
    val source: SourceConfiguration,
    @JsonDeserialize(converter = ListToMapConverter::class)
    val processors: Map<ProcessorId, ProcessorSettings>,
    val statistic: StatisticConfiguration = StatisticConfiguration(),
    val messagesCaching: CachingConfiguration = CachingConfiguration(),
    val eventsCaching: CachingConfiguration = CachingConfiguration(),
    val httpConfiguration: HttpServerConfiguration? = null,
    val grpcConfiguration: GrpcSenseConfiguration = GrpcSenseConfiguration()
)

private class ListToMapConverter : StdConverter<List<ProcessorSettings>, Map<ProcessorId, ProcessorSettings>>() {
    override fun convert(value: List<ProcessorSettings>): Map<ProcessorId, ProcessorSettings> {
        return value.stream().collect(
            Collectors.toUnmodifiableMap(
                ProcessorSettings::id,
                Function.identity(),
            ) { old, _ -> error("Duplicated settings id: ${old.id}") }
        )
    }
}