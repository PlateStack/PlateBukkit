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

class Mappings {
    val classes = ClassMapping()
    val methods = MethodMapping()
    val fields = FieldMapping()

    fun <T> Map<T,T>.inverse() = map { it.value to it.key }

    fun inverse() = Mappings().also {
        it.classes += classes.inverse()
        it.methods += methods.inverse()
        it.fields += fields.inverse()
    }

    operator fun rem(mappings: Mappings) = times(mappings).also {
        //val restored = it.classes.asSequence().filterNot { it.value in mappings.classes }.associate { it.key to (classes[it.key] ?: it.value) }
        //it.classes += restored
    }

    operator fun times(mappings: Mappings) = Mappings().also {
        fun <T> Map<T,T>.bridge(map: Map<T,T>) = mapValues { map[it.value] ?: it.value }
        it.classes += classes.bridge(mappings.classes)
        it.methods += methods.bridge(mappings.methods)
        it.fields += fields.bridge(mappings.fields)
    }

    operator fun plusAssign(mappings: Mappings) {
        classes += mappings.classes
        methods += mappings.methods
        fields += mappings.fields
    }

    operator fun plus(mappings: Mappings) = Mappings().also {
        it += this
        it += mappings
    }
}
