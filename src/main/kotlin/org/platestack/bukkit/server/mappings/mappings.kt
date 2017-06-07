/*
 *  Copyright (C) 2017 José Roberto de Araújo Júnior
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.platestack.bukkit.server.mappings

typealias ClassToken = ClassIdentifier
typealias ClassMapping = HashMap<ClassToken, ClassToken>

typealias MethodToken = Pair<ClassIdentifier, MethodIdentifier>
typealias MethodMapping = HashMap<MethodToken, MethodToken>

typealias FieldToken = Pair<ClassIdentifier, FieldIdentifier>
typealias FieldMapping = HashMap<FieldToken, FieldToken>

private fun <T> Map<T,T>.inverse() = map { it.value to it.key }

class Mappings {
    val classes = ClassMapping()
    val methods = MethodMapping()
    val fields = FieldMapping()

    fun inverse() = Mappings().let {
        it.classes += classes.inverse()
        it.methods += methods.inverse()
        it.fields += fields.inverse()
    }

    operator fun times(mappings: Mappings) = Mappings().let {
        fun <T> Map<T,T>.bridge(map: Map<T,T>) = mapValues { map[it.key] ?: it.value }
        it.classes += classes.bridge(mappings.classes)
        it.methods += methods.bridge(mappings.methods)
        it.fields += fields.bridge(mappings.fields)
    }
}
