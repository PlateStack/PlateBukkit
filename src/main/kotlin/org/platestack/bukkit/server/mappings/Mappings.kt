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

import java.io.Writer
import java.util.*

class Mappings {
    val classes = ClassMapping()
    val methods = MethodMapping()
    val fields = FieldMapping()

    fun exportSRG(writer: Writer) {
        fun PackageIdentifier.toSRG() = if(fullName.isBlank()) "." else fullName
        classes.asSequence()
                .map { (from,to) -> from.`package` to to.`package` }
                .filterNot { (from,to) -> from == to }
                .groupBy { it.first }
                .mapValuesTo(TreeMap<PackageIdentifier, PackageIdentifier>(compareBy { it.fullName })) { (_, value) ->
                    if (value.size == 1) value.first().second
                    else value.maxBy { (_, alt) -> classes.keys.count { it.`package` == alt } }!!.second
                }
                .forEach { (from, to) ->
                    writer.write("PK: ${from.toSRG()} ${to.toSRG()}\n")
                }

        classes.asSequence().sortedBy { it.key.fullName }.forEach { (from, to) ->
            writer.write("CL: $from $to\n")
        }

        fields.asSequence().sortedWith(compareBy({ it.key.first.fullName }, { it.key.second.name })).forEach { (from, to) ->
            writer.write("FD: ${from.first}/${from.second.name} ${to.first}/${to.second}\n")
        }

        methods.asSequence().sortedWith(compareBy({ it.key.first.fullName }, { it.key.second.name }, { it.key.second.signature })).forEach { (from, to) ->
            writer.write("MD: ${from.first}/${from.second.name} ${from.second.signature} ${to.first}/${to.second.name} ${to.second.signature}\n")
        }
    }

    private fun <T> Map<T,T>.inverse() = map { it.value to it.key }

    fun removeUselessEntries() {
        fields.entries.removeIf { (from, to) -> from.second.name == to.second.name }
        methods.entries.removeIf { (from, to) -> from.second.name == to.second.name }
        classes.entries.removeIf { (from, to) ->
            from == to &&

                    fields.none {
                        it.key.first == from
                    } &&

                    methods.none {
                        it.key.first == from
                    }
        }
    }

    fun removeSRGClientMappings() {
        fields.entries.removeIf { (from, _) -> from.first.`package`.fullName.startsWith("net/minecraft/client") }
        methods.entries.removeIf { (from, _) -> from.first.`package`.fullName.startsWith("net/minecraft/client") }
        classes.entries.removeIf { (fromClass, _) -> fromClass.`package`.fullName.startsWith("net/minecraft/client") }
    }

    fun inverse() = Mappings().also {
        it.classes += classes.inverse()
        it.methods += methods.inverse()
        it.fields += fields.inverse()
    }

    fun toKnownStructure(): SimpleEnv {
        val structures = SimpleEnv()

        fun ClassIdentifier.toStructure(): ClassStructure {
            structures[this]?.let { return it }
            ClassStructure(toChange { it.toStructure().`class` }, null, emptyList()).let {
                structures[this] = it
                return it
            }
        }

        classes.forEach { from, to ->
            val structure = from.toStructure()
            structure.`class`.`package`.name.reverse = to.`package`.toChange().name.current
            structure.`class`.`package`.checkReverse()
            structure.`class`.name.reverse = to.className
        }

        fields.forEach { (fromClass, fromField), (_, toField) ->
            val structure = fromClass.toStructure()
            val fieldChange = fromField.toChange()
            fieldChange.name.reverse = toField.name
            structure.fields[fromField] = FieldStructure(fieldChange, structure.`class`, AccessLevel.UNKNOWN, SignatureType(false, 'V', null))
        }

        methods.forEach { (fromClass, fromMethod), (_, toMethod) ->
            val structure = fromClass.toStructure()
            val methodChange = fromMethod.toChange { it.toStructure().`class` }
            methodChange.name.reverse = toMethod.name
            structure.methods[fromMethod] = MethodStructure(methodChange, structure.`class`, AccessLevel.UNKNOWN)
        }

        return structures
    }

    fun bridge(mappings: Mappings, revertMissingClasses: Boolean = false, revertMissingFields: Boolean = false, reverMissingMethods: Boolean = false): Mappings {
        val structures = toKnownStructure()

        classes.forEach { from, to ->
            val next = mappings.classes[to] ?: if(revertMissingClasses) from else to
            structures[from]!!.`class`.let {
                it.name.reverse = next.className
                it.`package`.name.reverse = next.`package`.toChange().to.fullName
                it.`package`.checkReverse()
            }
        }

        fields.forEach { (fromClass, fromField), to ->
            val next = mappings.fields[to]?.second ?: if(revertMissingFields) fromField else to.second
            structures[fromClass]!!.fields[fromField]!!.field.name.reverse = next.name
        }

        methods.forEach { (fromClass, fromMethod), to ->
            val next = mappings.methods[to]?.second ?: if(reverMissingMethods) fromMethod else to.second
            structures[fromClass]!!.methods[fromMethod]!!.method.name.reverse = next.name
        }

        return structures.toMappings()
    }

    fun SimpleEnv.toMappings(): Mappings {
        val result = Mappings()
        result.classes += values.associate { structure ->
            val from = structure.`class`.from
            val to = structure.`class`.to

            result.fields += structure.fields.values.associate { field ->
                (from to field.field.from) to (to to field.field.to)
            }

            result.methods += structure.methods.values.associate { method ->
                (from to method.method.from) to (to to method.method.to)
            }

            from to to
        }
        return result
    }

    operator fun rem(mappings: Mappings) = bridge(mappings, true, false, false).apply {
        removeSRGClientMappings()
        removeUselessEntries()
    }

    operator fun times(mappings: Mappings) = bridge(mappings).apply {
        removeSRGClientMappings()
        removeUselessEntries()
    }

    operator fun plusAssign(mappings: Mappings) {
        val result = this + mappings

        classes += result.classes
        methods += result.methods
        fields += result.fields
    }

    operator fun plus(mappings: Mappings) : Mappings {
        val structure = toKnownStructure()
        structure.values.forEach { it.apply(mappings) }
        return structure.toMappings()
    }
}
