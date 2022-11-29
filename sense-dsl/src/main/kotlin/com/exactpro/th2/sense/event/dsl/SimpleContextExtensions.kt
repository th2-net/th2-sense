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

import com.exactpro.th2.sense.api.ProcessorContext

inline fun <reified T : Any> SimpleMatchContext.key(name: String): ProcessorContext.Key<T> = ProcessorContext.key(name)

operator fun <T : Any> SimpleMatchContext.get(key: ProcessorContext.Key<T>): T? = context[key]

operator fun <T : Any> SimpleMatchContext.set(key: ProcessorContext.Key<T>, value: T): T { context[key] = value; return value }

fun <T : Any> SimpleMatchContext.remove(key: ProcessorContext.Key<T>): T? = context.remove(key)