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

package org.platestack.bukkit.scanner.structure

import java.lang.reflect.Modifier
import kotlin.concurrent.getOrSet

enum class AccessLevel {
    PRIVATE, INTERNAL, PROTECTED, PUBLIC, UNKNOWN;

    companion object {
        operator fun get(modifier: Int): AccessLevel {
            return if (Modifier.isPublic(modifier))
                PUBLIC
            else if (Modifier.isProtected(modifier))
                PROTECTED
            else if (Modifier.isPrivate(modifier))
                PRIVATE
            else
                INTERNAL
        }
    }
}

interface ClassScoped {
    val owner: ClassChange
    val access: AccessLevel
    fun canBeAccessedBy(`class`: ClassStructure) = true
}

data class MethodStructure(val method: MethodChange, override val owner: ClassChange, override var access: AccessLevel, var isStatic: Boolean?) : ClassScoped {
    override fun toString() = "${owner.from}#${method.from} -> ${owner.to}#${method.to}"
}

data class FieldStructure(val field: FieldChange, override val owner: ClassChange, override var access: AccessLevel, var static: Boolean?, val descriptor: ParameterDescriptor) : ClassScoped {
    override fun toString() = "${owner.from}#${field.from} -> ${owner.to}#${field.to}"
}

class ClassStructure private constructor(var isInterface: Boolean?, val interfaces: Set<ClassStructure>) {
    lateinit var `class`: ClassChange; private set
    var `super`: ClassStructure? = null; private set

    val fields: MutableMap<FieldIdentifier, FieldStructure> = mutableMapOf() //HashMap()
    val methods: MutableMap<MethodIdentifier, MethodStructure> = HashMap()
    var isFull = false

    constructor(`class`: ClassChange, `super`: ClassStructure?, isInterface: Boolean?, interfaces: Set<ClassStructure>): this(isInterface, interfaces) {
        this.`class` = `class`
        this.`super` = `super`
    }

    companion object {
        private val lock = ThreadLocal<MutableMap<ClassIdentifier, ClassStructure>>()
        operator fun invoke(
                id: ClassIdentifier, `super`: ClassIdentifier?, isInterface: Boolean?, interfaces: Set<ClassIdentifier>,
                packageProvider: ((PackageIdentifier) -> PackageMove)? = null,
                parentProvider: ((ClassIdentifier) -> ClassMove?)? = null,
                structureProvider: (ClassIdentifier) -> ClassStructure
        ): ClassStructure {
            synchronized(lock) {
                val loading = lock.getOrSet(::mutableMapOf)
                loading[id]?.let {
                    System.err.println("Cyclic structure creation from: $loading to $id")
                    return it
                }

                val loadingInterfaces = mutableSetOf<ClassStructure>()
                val structure = ClassStructure(isInterface, loadingInterfaces)
                check(loading.put(id, structure) == null) {
                    "Expected $id to be outside of the loading map"
                }

                try {
                    fun ClassIdentifier.create() =
                            if(packageProvider != null) {
                                if(parentProvider != null) {
                                    toChange(packageProvider, parentProvider)
                                }
                                else {
                                    toChange(packageProvider)
                                }
                            }
                            else {
                                if(parentProvider != null) {
                                    toChange(parentProvider = parentProvider)
                                }
                                else {
                                    toChange()
                                }
                            }

                    structure.`class` = id.create()
                    structure.`super` = `super`?.let(structureProvider)
                    loadingInterfaces.addAll(interfaces.map { structureProvider(it) })
                    return structure
                }
                finally {
                    checkNotNull(loading.remove(id)) {
                        "Expected $id to be on the loading map"
                    }
                }
            }
        }
    }

    fun find(method: MethodIdentifier, viewer: ClassStructure): MethodStructure? =
            sequenceOf(
                    methods[method],
                    `super`?.find(method, viewer),
                    interfaces.asSequence().mapNotNull { it.find(method, viewer) }.firstOrNull()
            )
                    .filterNotNull()
                    .filter { it.canBeAccessedBy(viewer) }
                    .firstOrNull()

    fun findReverse(method: MethodIdentifier, viewer: ClassStructure): MethodStructure? =
            sequenceOf(
                    methods[method],
                    methods.values.find { it.method.to == method },
                    `super`?.findReverse(method, viewer),
                    interfaces.asSequence().mapNotNull { it.findReverse(method, viewer) }.firstOrNull()
            )
                    .filterNotNull()
                    .filter { it.canBeAccessedBy(viewer) }
                    .firstOrNull()

    fun find(field: FieldIdentifier, viewer: ClassStructure): FieldStructure? =
            sequenceOf(
                    fields[field],
                    `super`?.find(field, viewer),
                    interfaces.asSequence().mapNotNull { it.find(field, viewer) }.firstOrNull()
            )
                    .filterNotNull()
                    .filter { it.canBeAccessedBy(viewer) }
                    .firstOrNull()

    fun findReverse(field: FieldIdentifier, viewer: ClassStructure): FieldStructure? =
            sequenceOf(
                    fields[field],
                    fields.values.find { it.field.to == field },
                    `super`?.findReverse(field, viewer),
                    interfaces.asSequence().mapNotNull { it.findReverse(field, viewer) }.firstOrNull()
            )
                    .filterNotNull()
                    .filter { it.canBeAccessedBy(viewer) }
                    .firstOrNull()

    fun find(field: FieldIdentifier): FieldStructure?
            = fields[field]
            ?: `super`?.find(field, this)
            ?: interfaces.asSequence().mapNotNull { it.find(field, this) }.firstOrNull()

    fun findReverse(field: FieldIdentifier): FieldStructure?
            = fields[field] ?: fields.values.find { it.field.to == field }
            ?: `super`?.findReverse(field, this)
            ?: interfaces.asSequence().mapNotNull { it.findReverse(field, this) }.firstOrNull()

    fun find(method: MethodIdentifier): MethodStructure?
            = methods[method]
            ?: `super`?.find(method, this)
            ?: interfaces.asSequence().mapNotNull { it.find(method, this) }.firstOrNull()

    fun findReverse(method: MethodIdentifier): MethodStructure?
            = methods[method] ?: methods.values.find { it.method.to == method }
            ?: `super`?.findReverse(method, this)
            ?: interfaces.asSequence().mapNotNull { it.findReverse(method, this) }.firstOrNull()

    override fun toString() = when(isInterface) {
        true -> "interface ";
        false -> "class "
        null-> "unknown "
    } + `class`.toString() + if(isFull) " (full)" else " (partial)"
}
