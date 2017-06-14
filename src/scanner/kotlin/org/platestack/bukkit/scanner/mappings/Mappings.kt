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

package org.platestack.bukkit.scanner.mappings

import org.platestack.bukkit.scanner.*
import org.platestack.bukkit.scanner.rework.ClassScanner
import org.platestack.bukkit.scanner.rework.RemapEnvironment
import org.platestack.bukkit.scanner.structure.*
import java.io.Writer
import java.util.*

class Mappings {
    val packages = PackageMapping()
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
                .apply { putAll(packages) }
                .forEach { (from, to) ->
                    writer.write("PK: ${from.toSRG()} ${to.toSRG()}\n")
                }

        classes.asSequence().sortedBy { it.key.fullName }.forEach { (from, to) ->
            writer.write("CL: $from $to\n")
        }

        fields.asSequence().sortedWith(compareBy({ it.key.first.fullName }, { it.key.second.name })).forEach { (from, to) ->
            writer.write("FD: ${from.first}/${from.second.name} ${to.first}/${to.second}\n")
        }

        methods.asSequence().sortedWith(compareBy({ it.key.first.fullName }, { it.key.second.name }, { it.key.second.descriptor })).forEach { (from, to) ->
            writer.write("MD: ${from.first}/${from.second.name} ${from.second.descriptor} ${to.first}/${to.second.name} ${to.second.descriptor}\n")
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
        it.packages += packages.inverse()
        it.classes += classes.inverse()
        it.methods += methods.inverse()
        it.fields += fields.inverse()
    }

    fun toFullStructure(scanner: ClassScanner): RemapEnvironment {
        val environment = RemapEnvironment()

        packages.forEach { (from, to) -> environment[from] = PackageMove(from.toChange(), to.toChange()) }
        classes.keys.forEach { id -> scanner.provideFull(environment, id) }
        methods.keys.forEach { (owner, id) -> scanner.provide(environment, owner, id) }
        fields.keys.forEach { (owner, id) -> scanner.provide(environment, owner, id) }



        val full = Mappings()
        var debug: Any

        val remapped = mutableSetOf<ClassIdentifier>()

        fun ClassChange.remap() {
            if(!remapped.add(from))
                return

            classes[from]?.let { new ->
                name.to = new.className
                parent?.remap()
            }
        }

        val remappedMethods = mutableSetOf<MethodToken>()
        fun MethodStructure.remap() {
            val token = owner.from to method.from
            if(!remappedMethods.add(token))
                return

            methods[token]?.let { new ->
                method.name.to = new.second.name
                owner.remap()
            }
        }

        val remappedFields = mutableSetOf<FieldToken>()
        fun FieldStructure.remap() {
            val token = owner.from to field.from
            if(!remappedFields.add(token))
                return

            fields[token]?.let { new ->
                field.name.to = new.second.name
                owner.remap()
            }
        }

        debug = environment.classes.values.asSequence()
                .onEach { it.`class`.remap() }
                .map { it.`class`.from to it.`class`.to }
                .toMap()
        full.classes += debug

        debug = environment.classes.values.asSequence()
                .flatMap { c -> c.fields.values.asSequence().map { c to it } }
                .onEach { it.second.remap() }
                .map { (c, f) -> FieldToken(c.`class`.from, f.field.from) to FieldToken(c.`class`.to, f.field.to) }
                .toMap()
        full.fields += debug

        debug = environment.classes.values.asSequence()
                .flatMap { c -> c.methods.values.asSequence().map { c to it } }
                .onEach { it.second.remap() }
                .map { (c, m) -> MethodToken(c.`class`.from, m.method.from) to MethodToken(c.`class`.to, m.method.to) }
                .toMap()
        full.methods += debug
        return environment
    }
}
