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

package com.exactpro.th2.sense.event.content

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
    defaultImpl = EventContent.Unknown::class,
    visible = false,
)
sealed class EventContent {
    @JsonTypeName("message")
    data class Message(
        val data: String,
    ) : EventContent()

    @JsonTypeName("reference")
    data class Reference(
        val eventId: String,
    ) : EventContent()

    @JsonTypeName("table")
    data class Table(
        val rows: List<Table.Row>,
    ) : EventContent() {
        class Row {
            private val columns: MutableMap<String, String> = hashMapOf()

            operator fun get(name: String): String? = columns[name]

            @JsonAnySetter
            operator fun set(name: String, value: String) {
                columns[name] = value
            }

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Row

                if (columns != other.columns) return false

                return true
            }

            override fun hashCode(): Int = columns.hashCode()
            override fun toString(): String {
                return "Row(columns=$columns)"
            }

        }
    }

    @JsonTypeName("treeTable")
    data class TreeTable(
        val name: String? = null,
        val rows: Map<String, TreeTable.Row>,
    ) : EventContent() {

        @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "type",
            visible = false,
        )
        sealed class Row

        @JsonTypeName("row")
        data class SimpleRow(
            val columns: Map<String, String>,
        ) : Row()

        @JsonTypeName("collection")
        data class NestedRow(
            val rows: Map<String, TreeTable.Row>,
        ) : Row()
    }

    @JsonTypeName("verification")
    class Verification(
        val fields: Map<String, Verification.Field>,
    ) : EventContent() {
        @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "type",
            visible = false,
        )
        sealed class Field

        @JsonTypeName("field")
        data class SimpleField(
            val operation: String,
            val status: String,
            val key: Boolean,
            val actual: String,
            val expected: String,
        ) : Field()

        @JsonTypeName("collection")
        data class NestedField(
            val operation: String,
            val key: Boolean,
            val actual: String,
            val expected: String,
            val status: String? = null,
            val fields: Map<String, Verification.Field> = emptyMap(),
        ) : Field()
    }

    class Unknown : EventContent() {
        @field:JsonIgnore
        private val _props: MutableMap<String, Any?> = hashMapOf()

        @get:JsonIgnore
        val props: Map<String, Any?>
            get() = _props

        @JsonAnySetter
        operator fun set(key: String, value: Any?) {
            _props[key] = value
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Unknown

            if (_props != other._props) return false

            return true
        }

        override fun hashCode(): Int = _props.hashCode()
        override fun toString(): String {
            return "Unknown(props=$props)"
        }


    }
}