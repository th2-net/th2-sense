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

package com.exactpro.th2.sense.internal

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.KClass

internal fun <T : Any> KClass<*>.genericParameter(index: Int, stopClass: KClass<*>): Class<out T> {
    var superClass: Type
    var currentClass: Class<*> = this.java
    do {
        superClass = currentClass.genericSuperclass
        currentClass = when (superClass) {
            is ParameterizedType -> superClass.rawType as Class<*>
            else -> superClass as Class<*>
        }
    } while (currentClass != stopClass.java)
    (superClass as? ParameterizedType) ?: error("super class is not a generic")
    require(superClass.rawType == stopClass.java) { "unexpected super class $superClass" }
    @Suppress("UNCHECKED_CAST")
    return superClass.actualTypeArguments[index] as Class<out T>
}