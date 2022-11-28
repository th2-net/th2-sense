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
import com.exactpro.th2.common.grpc.EventStatus
import com.exactpro.th2.sense.api.Event
import com.exactpro.th2.sense.event.content.EventContent
import com.exactpro.th2.sense.event.content.EventContent.*
import com.exactpro.th2.sense.event.dsl.util.createEvent
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
internal class TestEventRuleBuilderPositive : AbstractRuleBuilderTest() {

    @Nested
    inner class Simple {
        @Test
        fun `accepts event by status`() {
            val rule = rule {
                eventType("test") whenever allOf {
                    status equal EventStatus.FAILED
                }
            }
            val event = createEvent(status = EventStatus.FAILED)

            rule.assertAcceptEvent(event) asType type("test")
        }
        @Test
        fun `accepts event by name`() {
            val rule = rule {
                eventType("test") whenever allOf {
                    name equal "Test"
                }
            }
            val event = createEvent(name = "Test")

            rule.assertAcceptEvent(event) asType type("test")
        }

        @Test
        fun `accepts event by type`() {
            val rule = rule {
                eventType("test") whenever allOf {
                    type equal "Test"
                }
            }
            val event = createEvent(type = "Test")

            rule.assertAcceptEvent(event) asType type("test")
        }

        @Test
        fun `accepts event by start time`() {
            val startTime = Instant.now()
            val rule = rule {
                eventType("test") whenever allOf {
                    startTimestamp equal startTime
                }
            }
            val event = createEvent(startTime = startTime)

            rule.assertAcceptEvent(event) asType type("test")
        }

        @Test
        fun `accepts event by end time`() {
            val endTime = Instant.now()
            val rule = rule {
                eventType("test") whenever allOf {
                    endTimestamp equal endTime
                }
            }
            val event = createEvent(startTime = endTime.minusSeconds(1), endTime = endTime)

            rule.assertAcceptEvent(event) asType type("test")
        }

        @Test
        fun `accepts event by parent event`() {
            val rule = rule {
                eventType("test") whenever allOf {
                    parentEvent allOf {
                        name equal "Parent"
                    }
                }
            }

            val parent = createEvent(name = "Parent")
            val event = createEvent(name = "Child", parentEventID = parent.eventId)

            event.setupParent(parent)

            rule.assertAcceptEvent(event) asType type("test")
        }

        @Test
        fun `accepts event by root event`() {
            val rule = rule {
                eventType("test") whenever allOf {
                    rootEvent allOf {
                        name equal "Root"
                    }
                }
            }

            val root = createEvent(name = "Root")
            val event = createEvent(name = "Child", parentEventID = root.eventId)

            event.setupRoot(root)

            rule.assertAcceptEvent(event) asType type("test")
        }
    }

    @Nested
    inner class AllOf {
        @Test
        fun `accepts all of simple matchers`() {
            val rule = rule {
                eventType("test") whenever allOf {
                    name allOf {
                        startsWith("T")
                        endsWith("t")
                    }
                }
            }

            val event = createEvent(name = "Test")
            rule.assertAcceptEvent(event) asType type("test")
        }

        @Test
        fun `accepts all of event matcher`() {
            val rule = rule {
                eventType("test") whenever allOf {
                    name equal "Test"
                    type equal "TestType"
                }
            }
            val event = createEvent(name = "Test", type = "TestType")

            rule.assertAcceptEvent(event) asType type("test")
        }

        @Test
        fun `accepts all of event matcher in any of`() {
            val rule = rule {
                eventType("test") whenever anyOf {
                    allOf {
                        name equal "Test"
                        type equal "TestType"
                    }
                }
            }
            val event = createEvent(name = "Test", type = "TestType")

            rule.assertAcceptEvent(event) asType type("test")
        }

        @Test
        fun `accepts all of in sub event matcher`() {
            val rule = rule {
                eventType("test") whenever allOf {
                    parentEvent allOf {
                        name equal "Parent"
                        type equal "ParentType"
                    }
                }
            }

            val parent = createEvent(name = "Parent", type = "ParentType")
            val event = createEvent(parentEventID = parent.eventId)

            event.setupParent(parent)

            rule.assertAcceptEvent(event) asType type("test")
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class AnyOf {
        @ParameterizedTest
        @MethodSource("simpleMatch")
        fun `accepts any of simple match`(event: Event) {
            val rule = rule {
                eventType("test") whenever allOf {
                    name anyOf {
                        equal("A")
                        equal("B")
                    }
                }
            }

            rule.assertAcceptEvent(event) asType type("test")
        }

        @ParameterizedTest
        @MethodSource("complexMatch")
        internal fun `accepts any of event match`(event: Event) {
            val rule = rule {
                eventType("test") whenever anyOf {
                    name equal "Test"
                    type equal "Test"
                }
            }

            rule.assertAcceptEvent(event) asType type("test")
        }

        @ParameterizedTest
        @MethodSource("complexMatch")
        internal fun `accepts any of sub event match`(parent: Event) {
            val rule = rule {
                eventType("test") whenever allOf {
                    parentEvent allOf {
                        anyOf {
                            name equal "Test"
                            type equal "Test"
                        }
                    }
                }
            }

            val event = createEvent(parentEventID = parent.eventId)
            event.setupParent(parent)

            rule.assertAcceptEvent(event) asType type("test")
        }

        fun simpleMatch(): List<Arguments> = listOf(
            arguments(createEvent(name = "A")),
            arguments(createEvent(name = "B"))
        )

        fun complexMatch(): List<Arguments> = listOf(
            arguments(createEvent(name = "Test")),
            arguments(createEvent(type = "Test")),
        )
    }

    @Nested
    inner class NoneOf {
        @Test
        fun `accepts none of simple matcher`() {
            val rule = rule {
                eventType("test") whenever allOf {
                    name noneOf {
                        equal("A")
                        equal("B")
                    }
                }
            }

            val event = createEvent(name = "C")

            rule.assertAcceptEvent(event) asType type("test")
        }

        @Test
        fun `accepts none of event matcher`() {
            val rule = rule {
                eventType("test") whenever allOf {
                    noneOf {
                        name equal "A"
                        type equal "B"
                    }
                }
            }

            val event = createEvent(name = "B", type = "C")

            rule.assertAcceptEvent(event) asType type("test")
        }

        @Test
        fun `accepts none of sub event matcher`() {
            val rule = rule {
                eventType("test") whenever allOf {
                    parentEvent allOf {
                        noneOf {
                            name equal "A"
                            type equal "B"
                        }
                    }
                }
            }

            val parent = createEvent(name = "B", type = "C")
            val event = createEvent(parentEventID = parent.eventId)
            event.setupParent(parent)

            rule.assertAcceptEvent(event) asType type("test")
        }
    }

    @Nested
    inner class EventContent {
        @Test
        fun `filters content`() {
            val event = createEvent(body = """
                [
                    {
                      "type": "table",
                      "rows" : [
                        {
                          "Column": "Value"
                        }
                      ]
                    },
                    {
                      "type": "message",
                      "data": "Test"
                    }
                ]
            """.trimIndent())
            val rule = rule {
                eventType("test") whenever allOf {
                    body allOf {
                        first().isTable()
                            .get(Table::rows)
                            .first()
                            .get { get("Column") }
                            .isNotNull() equal "Value"

                        last().isMessage()
                            .get(Message::data) equal "Test"
                    }
                }
            }

            rule.assertAcceptEvent(event) asType type("test")
        }
    }
}