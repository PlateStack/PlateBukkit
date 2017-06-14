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

package org.platestack.bukkit.scanner.rework

import org.platestack.bukkit.boot.BootReflectionTarget
import org.platestack.bukkit.scanner.*
import org.platestack.bukkit.scanner.mappings.Mappings
import org.platestack.bukkit.scanner.structure.*
import java.io.File

class RemapEnvironment(val parent: RemapEnvironment? = null) {
    val packages: Map<PackageToken, PackageMove> = sortedMapOf()
    val classes: Map<ClassToken, ClassStructure> = sortedMapOf()

    @BootReflectionTarget
    fun export(dir: File) {
        dir.mkdirs()
        File(dir, "mappings.srg").writer().buffered().use { out->
            packages.values.forEach { out.write("PK: ${it.from} ${it.to}\n") }
            classes.values.forEach { out.write("CL: ${it.`class`.from} ${it.`class`.to}\n") }
            classes.values.forEach { c ->
                c.fields.values.forEach { out.write("FD: ${c.`class`.from}/${it.field.from} ${c.`class`.to}/${it.field.to}\n") }
            }
            classes.values.forEach { c ->
                c.methods.values.forEach { out.write("MD: ${c.`class`.from}/${it.method.from} ${c.`class`.to}/${it.method.to}\n") }
            }
        }

        File(dir, "structures.txt").writer().buffered().use { out->
            classes.values.forEach { c ->
                out.write("${c.`class`.from} -> ${c.`class`.to}\n")
                out.write("| itf=${c.isInterface}\n")
                out.write("| full=${c.isFull}\n")
                out.write("| super=${c.`super`?.`class`?.let { "${it.from} -> ${it.to}" }} ${if(c.`super`?.isFull == true) "(full)" else "(partial)"}\n")
                out.write("| interfaces=${c.interfaces.size}\n")
                c.interfaces.forEach {
                    out.write("| | ${it.`class`.from} -> ${it.`class`.to} ${if(it.`super`?.isFull == true) "(full)" else "(partial)"}\n")
                }
                out.write("| fields=${c.fields.size}\n")
                c.fields.values.forEach {
                    out.write("| | ${it.field.from} -> ${it.field.to}\n")
                    out.write("| | | static=${it.static}\n")
                    out.write("| | | access=${it.access}\n")
                    out.write("| | | type=${it.descriptor?.from} -> ${it.descriptor?.to}\n")
                    out.write("| | | owner=${it.owner}\n")
                }
                c.methods.values.forEach {
                    out.write("| | ${it.method.from} -> ${it.method.to}\n")
                    out.write("| | | static=${it.isStatic}\n")
                    out.write("| | | access=${it.access}\n")
                    out.write("| | | type=${it.method.descriptorType.from} -> ${it.method.descriptorType.to}\n")
                    out.write("| | | owner=${it.owner}\n")
                }
            }
        }
    }

    fun applyToForeign(mappings: Mappings) {
        val packagesBefore = packages.values.associate { it.from to it.to }
        val classesBefore = classes.values.associate { it.`class`.from to it.`class`.to }
        val methodsBefore = classes.values.asSequence().flatMap {
            val cf = it.`class`.from
            val ct = it.`class`.to
            it.methods.values.asSequence().map {
                MethodToken(cf, it.method.from) to MethodToken(ct, it.method.to)
            }
        }.toMap()

        val fieldsBefore = classes.values.asSequence().flatMap {
            val cf = it.`class`.from
            val ct = it.`class`.to
            it.fields.values.asSequence().map {
                FieldToken(cf, it.field.from) to FieldToken(ct, it.field.to)
            }
        }.toMap()

        val newPackages = sortedMapOf<PackageIdentifier, PackageChange>()

        packages.values.forEach { native ->
            val nativeTo = packagesBefore[native.from]!!
            mappings.packages[nativeTo]?.let { foreign ->
                if(nativeTo != foreign) {
                    native.new.name.to = foreign.name
                    if(native.from.parent != foreign.parent) {
                        native.new.moveTo = foreign.parent?.toChange()?.also { newPackages[it.from] = it }
                    }
                    //println("PK: ${native.from} ${native.to}")
                }
            }
        }

        classes.values.forEach { structure ->
            structure.`class`.let { native ->
                val nativeTo = classesBefore[native.from]!!
                mappings.classes[nativeTo]?.let { foreign ->
                    native.name.to = foreign.className
                    if(nativeTo.`package` != foreign.`package`) {
                        native.`package`.new = newPackages[foreign.`package`] ?: foreign.`package`.toChange().also {
                            newPackages[it.from] = it
                        }
                    }
                    native.name
                }
            }

            structure.fields.values.forEach { native ->
                mappings.fields[fieldsBefore[native.owner.from to native.field.from]!!]?.let { foreign ->
                    native.field.name.to = foreign.second.name
                }
            }

            structure.methods.values.forEach { native ->
                mappings.methods[methodsBefore[native.owner.from to native.method.from]!!]?.let { foreign ->
                    native.method.name.to = foreign.second.name
                }
            }
        }

        //classes.values.asSequence().map { it.`class` }.sortedBy { it.from }.forEach { println("CL: ${it.from} ${it.to}") }
        (packages as MutableMap) += newPackages.map { it.key to PackageMove(it.value) }
    }

    fun applyToNative(mappings: Mappings) {
        val newPackages = sortedMapOf<PackageIdentifier, PackageChange>()

        packages.values.forEach { native ->
            mappings.packages[native.from]?.let { foreign ->
                if(native.from != foreign) {
                    native.new.name.to = foreign.name
                    if(native.from.parent != foreign.parent) {
                        native.new.moveTo = foreign.parent?.toChange()?.also { newPackages[it.from] = it }
                    }
                    //println("PK: ${native.from} ${native.to}")
                }
            }
        }

        classes.values.forEach { structure ->
            structure.`class`.let { native ->
                mappings.classes[native.from]?.let { foreign ->
                    native.name.to = foreign.className
                    if(native.`package`.to != foreign.`package`) {
                        native.`package`.new = newPackages[foreign.`package`] ?: foreign.`package`.toChange().also {
                            newPackages[it.from] = it
                        }
                    }
                    native.name
                }
            }

            structure.fields.values.forEach { native ->
                mappings.fields[native.owner.from to native.field.from]?.let { foreign ->
                    native.field.name.to = foreign.second.name
                }
            }

            structure.methods.values.forEach { native ->
                mappings.methods[native.owner.from to native.method.from]?.let { foreign ->
                    native.method.name.to = foreign.second.name
                }
            }
        }

        //classes.values.asSequence().map { it.`class` }.sortedBy { it.from }.forEach { println("CL: ${it.from} ${it.to}") }
        (packages as MutableMap) += newPackages.map { it.key to PackageMove(it.value) }
    }

    fun inverse(): RemapEnvironment {
        val packageChanges = ReverseMap<PackageChange>()

        fun PackageChange.inverse(): PackageChange {
            packageChanges[this]?.let { return it }

            val inverse = PackageChange(
                    parent?.inverse(),
                    parent?.inverse(),
                    PackageName(name.to, name.from)
            )

            packageChanges[this] = inverse
            return inverse
        }

        val packageMoves = ReverseMap<PackageMove>()

        fun PackageMove.inverse(): PackageMove {
            packageMoves[this]?.let { return it }

            val inverse = PackageMove(new.inverse(), old.inverse())

            packageMoves[this] = inverse
            return inverse
        }

        packages.values.forEach { it.inverse() }

        val classChanges = ReverseMap<ClassChange>()

        fun ClassChange.inverse(): ClassChange {
            classChanges[this]?.let { return it }

            val inverse = ClassChange(
                    `package`.inverse(),
                    parent?.inverse(),
                    ClassName(name.to, name.from)
            )

            classChanges[this] = inverse
            return inverse
        }

        val classStructures = ReverseMap<ClassStructure>()

        val fieldChanges = ReverseMap<FieldChange>()

        fun FieldChange.inverse(): FieldChange {
            fieldChanges[this]?.let { return it }

            val inverse = FieldChange(Name(name.to, name.from))
            fieldChanges[this] = inverse
            return inverse
        }

        fun ParameterDescriptor.inverse(): ParameterDescriptor {
            return ParameterDescriptor(array, base, type?.inverse())
        }

        fun FieldStructure.inverse(): FieldStructure {
            return FieldStructure(
                    field.inverse(),
                    owner.inverse(),
                    access,
                    static,
                    descriptor?.inverse()
            )
        }

        val methodChanges = ReverseMap<MethodChange>()

        fun MethodDescriptor.inverse(): MethodDescriptor {
            return MethodDescriptor(
                    returnType?.inverse(),
                    parameterTypes.map { it.inverse() }
            )
        }

        fun MethodChange.inverse(): MethodChange {
            methodChanges[this]?.let { return it }

            val inverse = MethodChange(
                    Name(name.to, name.from),
                    descriptorType.inverse()
            )
            methodChanges[this] = inverse
            return inverse
        }

        fun MethodStructure.inverse(): MethodStructure {
            return MethodStructure(
                    method.inverse(),
                    owner.inverse(),
                    access,
                    isStatic
            )
        }

        fun ClassStructure.inverse(): ClassStructure {
            classStructures[this]?.let { return it }

            val inverse = ClassStructure(
                    `class`.inverse(),
                    `super`?.inverse(),
                    isInterface,
                    interfaces.mapTo(mutableSetOf()) { it.inverse() }
            )
            classStructures[this] = inverse

            inverse.fields += fields.values.asSequence().associate { it.inverse().let { it.field.from to it } }
            inverse.methods += methods.values.asSequence().associate { it.inverse().let { it.method.from to it } }
            inverse.isFull = isFull

            return inverse
        }

        classes.values.forEach { it.inverse() }

        val inverse = RemapEnvironment()
        (inverse.packages as MutableMap) += packageMoves.values.associate { it.from to it }
        (inverse.classes as MutableMap) += classStructures.values.associate { it.`class`.from to it }
        return inverse
    }

    operator fun get(`package`: PackageToken): PackageMove? = packages[`package`] ?: parent?.get(`package`)

    operator fun get(`class`: ClassToken): ClassStructure? = classes[`class`] ?: parent?.get(`class`)

    operator fun set(`class`: ClassToken, structure: ClassStructure) {
        (classes as MutableMap)[`class`] = structure
    }

    operator fun set(`package`: PackageToken, structure: PackageMove) {
        (packages as MutableMap)[`package`] = structure
    }
}
