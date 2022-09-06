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

package com.exactpro.th2.sense.event.dsl

import java.time.Instant
import kotlin.properties.ReadOnlyProperty
import com.exactpro.th2.common.grpc.EventStatus
import com.exactpro.th2.sense.api.Event
import com.exactpro.th2.sense.api.ProcessorContext
import com.exactpro.th2.sense.event.dsl.impl.AllEventTypeMatcher
import com.exactpro.th2.sense.event.dsl.impl.AnyEventTypeMatcher
import com.exactpro.th2.sense.event.dsl.impl.EventMatcherBuilderImpl
import com.exactpro.th2.sense.event.dsl.impl.HasRelatedEventMatcher
import com.exactpro.th2.sense.event.dsl.impl.NoneEventTypeMatcher
import com.exactpro.th2.sense.event.dsl.impl.RelatedEventMatcherBuilderImpl
import com.exactpro.th2.sense.event.dsl.impl.SimpleMatcherDelegate
import com.exactpro.th2.sense.event.dsl.impl.matchAllOfInternal
import com.exactpro.th2.sense.event.dsl.impl.matchAnyOfInternal
import com.exactpro.th2.sense.event.dsl.impl.matchNoneOfInternal
import com.google.protobuf.Timestamp

val AllOfEventMatcherBuilder.name: SimpleMatcher<String> by matcher { eventName }
val AllOfEventMatcherBuilder.type: SimpleMatcher<String> by matcher { eventType }
val AllOfEventMatcherBuilder.startTimestamp: SimpleMatcher<Instant> by matcher { startTimestamp.toInstant() }
val AllOfEventMatcherBuilder.endTimestamp: SimpleMatcher<Instant> by matcher { endTimestamp.toInstant() }
val AllOfEventMatcherBuilder.status: SimpleMatcher<EventStatus> by matcher { status }

infix fun RelatedEventMatcherBuilder.allOf(block: AllOfEventMatcherBuilder.() -> Unit) {
    register(matchAllOfInternal(block))
}
infix fun RelatedEventMatcherBuilder.anyOf(block: EventMatcherBuilder.() -> Unit) {
    register(matchAnyOfInternal(block))
}
infix fun RelatedEventMatcherBuilder.noneOf(block: EventMatcherBuilder.() -> Unit) {
    register(matchNoneOfInternal(block))
}

val AllOfEventMatcherBuilder.parentEvent: RelatedEventMatcherBuilder
    get() = RelatedEventMatcherBuilderImpl(
        this,
        "parent"
    ) { eventProvider.findParentFor(it) }

val AllOfEventMatcherBuilder.rootEvent: RelatedEventMatcherBuilder
    get() = RelatedEventMatcherBuilderImpl(
        this,
        "root"
    ) { eventProvider.findRootFor(it) }

fun AllOfEventMatcherBuilder.hasParentEvent() {
    val name = "hasParentEvent"
    register(name, HasRelatedEventMatcher(name, mustExist = true) { eventProvider.findParentFor(it) })
}

fun AllOfEventMatcherBuilder.doesNotHaveParentEvent() {
    val name = "doesNotHaveParentEvent"
    register(name, HasRelatedEventMatcher(name, mustExist = false) { eventProvider.findParentFor(it) })
}

fun AllOfEventMatcherBuilder.isRoot() {
    val name = "isRoot"
    register(name, HasRelatedEventMatcher(name, mustExist = false) { eventProvider.findRootFor(it) })
}

fun EventMatcherBuilder.allOf(block: AllOfEventMatcherBuilder.() -> Unit) {
    val name = "allOf"
    register(name, matchAllOfInternal(block))
}

fun AllOfEventMatcherBuilder.noneOf(block: EventMatcherBuilder.() -> Unit) {
    val name = "noneOf"
    register(name, matchNoneOfInternal(block))
}

fun AllOfEventMatcherBuilder.anyOf(block: EventMatcherBuilder.() -> Unit) {
    val name = "anyOf"
    register(name, matchAnyOfInternal(block))
}

private fun <T : Any> matcher(extractor: Event.() -> T): ReadOnlyProperty<AllOfEventMatcherBuilder, SimpleMatcher<T>> =
    SimpleMatcherDelegate(extractor)


private fun Timestamp.toInstant(): Instant = Instant.ofEpochSecond(seconds, nanos.toLong())