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

    /*
    fun toSomething() {
        val packageChanges = mutableMapOf<PackageIdentifier, PackageChange>()
        tailrec fun PackageChange.register() {
            if(packageChanges.putIfAbsent(from, this) != null)
                parent?.register()
        }

        packages.forEach { (from, to) ->
            val change = packageChanges[from] ?: from.toChange { packageChanges[it] }
            change.register()
            if(from != to) {
                val new = packageChanges[to] ?: to.toChange { packageChanges[it] }
                new.register()
                if(from.parent != to) {
                    change.moveTo = new.parent
                }
                else {
                    change.name.to = new.name.to
                }
            }
        }
    }

    fun toSomething2() {
        val packageChanges = packages.entries.associate { (from, to) -> from to from.toChange().also { it.moveTo = to.toChange() } }
        val pendingClasses = classes.toMutableMap()
        val classChanges = classes.asSequence().filter { it.key.parent == null && it.value.parent == null }.map { (from, to) ->
            from to from.toChange(packageProvider = {
                PackageMove(
                        packageChanges[from.`package`] ?: from.`package`.toChange(),
                        packageChanges[to.`package`] ?: to.`package`.toChange()
                )
            }).also {
                it.name.to = to.className
            }
        }.toMap()

        pendingClasses.keys.removeAll(classChanges.keys)

        packageChanges.values.first().toString()

        println()
    }
    */

    fun toKnownStructure(): SimpleEnv {
        TODO()
        /*
        val structures = SimpleEnv()
        val parentMoves = mutableListOf<ClassMove>()
        fun ClassIdentifier.toStructure(): ClassStructure {
            structures[this]?.let { return it }
            ClassStructure(this, null, null, emptySet(), parentProvider = {
                ClassMove(it.toChange())
            }) { error("Unexpected call") }.let {
                structures[this] = it
                return it
            }
        }

        classes.forEach { from, to ->
            val structure = from.toStructure()
            structure.`class`.`package`.new = to.`package`.toChange()
            structure.`class`.name.to = to.className
        }

        fields.forEach { (fromClass, fromField), (_, toField) ->
            val structure = fromClass.toStructure()
            val fieldChange = fromField.toChange()
            fieldChange.name.to = toField.name
            structure.fields[fromField] = FieldStructure(fieldChange, structure.`class`, AccessLevel.UNKNOWN, null, null)
        }

        methods.forEach { (fromClass, fromMethod), (_, toMethod) ->
            val structure = fromClass.toStructure()
            val methodChange = fromMethod.toChange { it.toStructure().`class` }
            methodChange.name.to = toMethod.name
            structure.methods[fromMethod] = MethodStructure(methodChange, structure.`class`, AccessLevel.UNKNOWN, null)
        }

        return structures
        */
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

    fun bridge(
            scanner: ClassScanner,
            mappings: Mappings,
            revertMissingClasses: Boolean = false,
            revertMissingFields: Boolean = false,
            revertMissingMethods: Boolean = false
    ) : Mappings {

        val environment = RemapEnvironment()

        classes.forEach { (from, to) ->
            val change = scanner.provide(environment, from)?.`class` ?:
                            ClassStructure(
                                from.toChange({ scanner.provide(environment, it) }),
                                null, null, emptySet()
                            ).also { environment[it.`class`.from] = it }.`class`

            if(from != to) {
                if(from.`package` != to.`package`) {
                    change.`package`.new = to.`package`.toChange()
                }

                //if(from.parent != to.parent) {
                //    change.parent.new = to.parent?.toChange() //to.parent?.let { checkNotNull(scanner.provide(environment, it)).`class` }
                //}

                if(from.className != to.className) {
                    change.name.to = to.className
                }
            }
        }

        TODO()
    }

    fun bridge(mappings: Mappings, revertMissingClasses: Boolean = false, revertMissingFields: Boolean = false, reverMissingMethods: Boolean = false): Mappings {
        val structures = toKnownStructure()
        classes.forEach { from, to ->
            val next = mappings.classes[to] ?: if(revertMissingClasses) from else to
            structures[from]!!.`class`.let {
                it.name.to = next.className
                it.`package`.new = next.`package`.toChange()
            }
        }

        fields.forEach { (fromClass, fromField), to ->
            val next = mappings.fields[to]?.second ?: if(revertMissingFields) fromField else to.second
            structures[fromClass]!!.fields[fromField]!!.field.name.to = next.name
        }

        methods.forEach { (fromClass, fromMethod), to ->
            val next = mappings.methods[to]?.second ?: if(reverMissingMethods) fromMethod else to.second
            structures[fromClass]!!.methods[fromMethod]!!.method.name.to = next.name
        }
        return structures.toMappings()
    }

    fun SimpleEnv.toMappings(): Mappings {
        val result = Mappings()

        result.packages += values.asSequence()
                .map { it.`class`.`package`.let { it.from to it.to } }
                .filterNot { (from,to) -> from == to }
                .groupBy { it.first }
                .mapValuesTo(TreeMap<PackageIdentifier, PackageIdentifier>(compareBy { it.fullName })) { (_, value) ->
                    if (value.size == 1) value.first().second
                    else value.maxBy { (_, alt) -> classes.keys.count { it.`package` == alt } }!!.second
                }

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
        //removeSRGClientMappings()
        //removeUselessEntries()
    }

    operator fun times(mappings: Mappings) = bridge(mappings).apply {
        //removeSRGClientMappings()
        //removeUselessEntries()
    }

    operator fun plusAssign(mappings: Mappings) {
        val result = this + mappings

        classes += result.classes
        methods += result.methods
        fields += result.fields
    }

    operator fun plus(mappings: Mappings) : Mappings {
        TODO()
        /*
        val structure = toKnownStructure()
        structure.values.forEach { it.apply(mappings) }
        return structure.toMappings()
        */
    }
}
