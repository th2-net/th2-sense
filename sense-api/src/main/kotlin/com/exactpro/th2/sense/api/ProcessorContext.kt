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

package com.exactpro.th2.sense.api

import kotlin.reflect.KClass

interface ProcessorContext {
    val eventProvider: EventProvider
    val messageProvider: MessageProvider

    operator fun <T : Any> get(key: Key<T>): T?

    operator fun <T : Any> set(key: Key<T>, value: T)

    fun <T : Any> remove(key: Key<T>): T?

    class Key<T : Any> private constructor(
        private val name: String,
        val type: KClass<T>,
    ) {
        override fun toString(): String {
            return "$name($type)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Key<*>

            if (name != other.name) return false
            if (type != other.type) return false

            return true
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + type.hashCode()
            return result
        }

        companion object {
            @JvmStatic
            fun <T : Any> create(name: String, type: KClass<T>): Key<T> = Key(name, type)
        }
    }

    companion object {
        inline fun <reified T : Any> key(name: String): Key<T> = key(name, T::class)

        @JvmStatic
        fun <T : Any> key(name: String, type: KClass<T>) = Key.create(name, type)
    }
}
