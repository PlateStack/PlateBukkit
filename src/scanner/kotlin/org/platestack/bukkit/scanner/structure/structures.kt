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

import org.platestack.bukkit.scanner.UniqueModification
import org.platestack.bukkit.scanner.mappings.Mappings
import java.lang.reflect.Modifier

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

data class MethodStructure(val method: MethodChange, override val owner: ClassChange, override var access: AccessLevel) : ClassScoped {
    fun apply(mappings: Mappings) {
        method.apply(mappings, mappings.methods[owner.from to method.from]?.also {
            owner.name.reverse = it.first.className
            owner.`package`.name.reverse = it.first.`package`.fullName
        })
    }
}

data class FieldStructure(val field: FieldChange, override val owner: ClassChange, override var access: AccessLevel, val signature: SignatureType) : ClassScoped {
    fun apply(mappings: Mappings) {
        field.apply(mappings.fields[owner.from to field.from]?.also {
            owner.name.reverse = it.first.className
            owner.`package`.name.reverse = it.first.`package`.fullName
        })
    }
}

class ClassStructure(`class`: ClassChange?, var `super`: ClassStructure?, val interfaces: List<ClassStructure>) {
    var `class` by UniqueModification<ClassChange>()

    init {
        if(`class` != null)
            this.`class` = `class`
    }

    val fields = HashMap<FieldIdentifier, FieldStructure>()
    val methods = HashMap<MethodIdentifier, MethodStructure>()

    fun apply(mappings: Mappings) {
        `class`.apply(mappings)
        fields.values.forEach{ it.apply(mappings) }
        methods.values.forEach{ it.apply(mappings) }
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
}
