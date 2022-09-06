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
import com.exactpro.th2.sense.api.Event
import com.exactpro.th2.sense.event.dsl.util.createEvent
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
internal class TestEventRuleBuilderNegative : AbstractRuleBuilderTest() {

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class Simple {
        @ParameterizedTest
        @MethodSource("simpleMatch")
        fun `skip if does not match simple`(setup: AllOfEventMatcherBuilder.() -> Unit, event: Event) {
            val rule = rule {
                eventType("test") whenever allOf { setup() }
            }
            rule assertSkipsEvent event
        }

        @ParameterizedTest
        @MethodSource("simpleMatch")
        fun `skip if does not match simple in parent`(setup: AllOfEventMatcherBuilder.() -> Unit, parent: Event) {
            val rule = rule {
                eventType("test") whenever allOf {
                    parentEvent allOf { setup() }
                }
            }
            val event = createEvent(parentEventID = parent.eventId)
            event.setupParent(parent)
            rule assertSkipsEvent event
        }

        @ParameterizedTest
        @MethodSource("simpleMatch")
        fun `skip if does not match simple in root`(setup: AllOfEventMatcherBuilder.() -> Unit, root: Event) {
            val rule = rule {
                eventType("test") whenever allOf {
                    rootEvent allOf { setup() }
                }
            }
            val event = createEvent(parentEventID = root.eventId)
            event.setupRoot(root)
            rule assertSkipsEvent event
        }

        fun simpleMatch(): List<Arguments> = listOf(
            arguments(setupFun {
                name equal "Test"
            }, createEvent(name = "NotTest")),
            arguments(setupFun {
                type equal "Test"
            }, createEvent(type = "NotTest")),
            arguments(setupFun {
                startTimestamp equal Instant.MIN
            }, createEvent()),
            arguments(setupFun {
                endTimestamp equal Instant.MIN
            }, createEvent())
        )

        private fun setupFun(setup: AllOfEventMatcherBuilder.() -> Unit): AllOfEventMatcherBuilder.() -> Unit = setup
    }

    @Nested
    inner class AllOf {
        @Test
        fun `skip if not all match in simple matcher`() {
            val rule = rule {
                eventType("test") whenever allOf {
                    name allOf {
                        startsWith("T")
                        endsWith("t")
                    }
                }
            }

            Assertions.assertAll(
                Executable {
                    val event = createEvent(name = "Test1")
                    rule assertSkipsEvent event
                },
                Executable {
                    val event = createEvent(name = "NotTest")
                    rule assertSkipsEvent event
                }
            )
        }
        @Test
        fun `skip if not all match in event matcher`() {
            val rule = rule {
                eventType("test") whenever allOf {
                    name equal "Test"
                    type equal "Test"
                }
            }

            Assertions.assertAll(
                Executable {
                    val event = createEvent(name = "Test", type = "NotTest")
                    rule assertSkipsEvent event
                },
                Executable {
                    val event = createEvent(name = "NotTest", type = "Test")
                    rule assertSkipsEvent event
                }
            )
        }
    }

    @Nested
    inner class AnyOf {
        @Test
        fun `skip if none match in simple matcher`() {
            val rule = rule {
                eventType("test") whenever allOf {
                    name anyOf {
                        equal("A")
                        equal("B")
                    }
                }
            }

            val event = createEvent(name = "C")
            rule assertSkipsEvent event
        }

        @Test
        fun `skip if none match in event matcher`() {
            val rule = rule {
                eventType("test") whenever anyOf {
                    name equal "A"
                    type equal "B"
                }
            }

            val event = createEvent(name = "B", type = "C")
            rule assertSkipsEvent event
        }
    }

    @Nested
    inner class NoneOf {
        @Test
        fun `skips if any matches`() {
            val rule = rule {
                eventType("test") whenever noneOf {
                    name equal "A"
                    type equal "B"
                }
            }

            Assertions.assertAll(
                Executable {
                    val event = createEvent(name = "A")
                    rule assertSkipsEvent event
                },
                Executable {
                    val event = createEvent(type = "B")
                    rule assertSkipsEvent event
                }
            )
        }
    }
}