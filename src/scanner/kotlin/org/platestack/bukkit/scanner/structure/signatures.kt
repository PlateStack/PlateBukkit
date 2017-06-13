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

private fun checkNotEmpty(name: String) = check(name.isNotBlank()) { "A name can't be blank" }

class ClassName(from: String, to: String = from): Name(from, to, {
    require('.' !in it) { "A class name can't contains '.': $it" }
    require('/' !in it) { "A class name can't contains '/': $it" }
})

class PackageName(from: String, to: String = from): Name(from, to, {
    require('.' !in it) { "A class name can't contains '.': $it" }
    require('/' !in it) { "A class name can't contains '/': $it" }
})

/**
 * A general name which cannot be empty
 * @property from The original name
 * @property to The name after the transformation
 */
open class Name protected constructor(override final val from: String, to: String, val validator: (String)->Unit) : Change {
    constructor(from: String, to: String = from): this(from, to, ::checkNotEmpty)
    init {
        validator(from)
    }

    override final var to = to; set(value) {
        validator(value)
        field = value
    }

    fun component1() = from
    fun component2() = to
    fun copy(from: String = this.from, to: String = this.to) = Name(from, to)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as Name

        if (from != other.from) return false
        if (to != other.to) return false

        return true
    }

    override fun hashCode(): Int {
        var result = from.hashCode()
        result = 31 * result + to.hashCode()
        return result
    }

    override fun toString(): String {
        return "$from -> $to"
    }
}

private val arrayPattern = Regex("^\\[+")

/**
 * The JVM descriptor which indicates a field or parameter type
 * @property array If this descriptor begins with `[`
 * @property base The primitive field type: `BCDFIJSZ` or `L` for object types.
 * @property type The object type represented by this descriptor.
 * @property from The original descriptor
 * @property to The descriptor after the transformation
 */
data class ParameterDescriptor(val array: String, val base: Char, val type: ClassChange?) : Change {
    constructor(signature: String, classSupplier: (ClassIdentifier) -> ClassChange): this(StringBuilder(signature), classSupplier)
    private constructor(b: StringBuilder, classSupplier: (ClassIdentifier) -> ClassChange)
            : this(
            arrayPattern.find(b)?.value?.let { b.delete(0, it.length); it } ?: "",
            b.first(), b.first().let { if(it == 'L') classSupplier(ClassIdentifier(b.substring(1, b.length - 1))) else null }
    )

    init {
        check(base in "BCDFIJSZL") { "Unexpected primitive type: $base" }
        check((type == null && base != 'L') || (base == 'L' && type != null)) { "Object types must declare the base to 'L' and the referred type." }
    }

    override val from = array + base + (type?.from?.fullName?.let { it+';' } ?: "")
    override val to get() = array + base + (type?.to?.fullName?.let { it+';' } ?: "")
    override fun toString() = "$from -> $to"
}

private val signaturePattern = Regex("\\[*([BCDFIJSZ]|L[^;]+;)")

/**
 * The JVM method descriptor
 * @property returnType The type of the object returned by the represented function. `null` indicates `V` *(`void`)*
 * @property parameterTypes The list of parameters required by the represented method.
 * @property from The original descriptor
 * @property to The descriptor after the transformation
 */
data class MethodDescriptor(val returnType: ParameterDescriptor?, val parameterTypes: List<ParameterDescriptor>) : Change {
    constructor(signature: String, classSupplier: (ClassIdentifier)-> ClassChange)
            : this(
                    if("()V" == signature)  null
                    else signature.substringAfterLast(')').let {
                        if(it == "V") null
                        else ParameterDescriptor(it, classSupplier)
                    },

                    if("()V" == signature || signature.startsWith("()")) emptyList()
                    else signaturePattern.findAll(signature.substringBeforeLast(')').substring(1))
                            .map { ParameterDescriptor(it.value, classSupplier) }
                            .toList()

    )
    override val from =
            if(returnType == null && parameterTypes.isEmpty()) "()V"
            else '('+parameterTypes.asSequence().map { it.from }.joinToString("")+')'+(returnType?.from ?: 'V')

    override val to get() =
            if(returnType == null && parameterTypes.isEmpty()) "()V"
            else '('+parameterTypes.asSequence().map { it.to }.joinToString("")+')'+(returnType?.to ?: 'V')

    override fun toString() = "$from -> $to"
}
