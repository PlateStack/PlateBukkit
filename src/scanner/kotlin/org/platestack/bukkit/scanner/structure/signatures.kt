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

import org.platestack.bukkit.scanner.mappings.Mappings

data class Name(val current: String, var reverse: String = current)
data class SignatureType(val array: Boolean, val descriptor: Char, val type: ClassChange?) {
    constructor(signature: String, classSupplier: (ClassIdentifier) -> ClassChange): this(StringBuilder(signature), classSupplier)
    private constructor(b: StringBuilder, classSupplier: (ClassIdentifier) -> ClassChange)
            : this(
            if(b.first() == '[') { b.deleteCharAt(0); true } else false,
            b.first(), b.first().let { if(it == 'L') classSupplier(ClassIdentifier(b.substring(1, b.length - 1))) else null }
    )
    val from = if(array) "[" else {""} + descriptor + (type?.from?.fullName?.let { it+';' } ?: "")
    val to get() = if(array) "[" else {""} + descriptor + (type?.to?.fullName?.let { it+';' } ?: "")

    fun apply(mappings: Mappings) {
        type?.let { type.apply(mappings) }
    }
}

private val signaturePattern = Regex("\\[?([BCDFIJSZV]|L[^;]+;)")
data class MethodSignature(val returnType: SignatureType, val parameterTypes: List<SignatureType>) {
    constructor(signature: String, classSupplier: (ClassIdentifier)-> ClassChange)
            : this(
            SignatureType(signature.substringAfterLast(')'), classSupplier),
            signaturePattern.findAll(signature.substringBeforeLast(')').substring(1)).map { SignatureType(it.value, classSupplier) }.toList()
    )
    val from = '('+parameterTypes.asSequence().map { it.from }.joinToString("")+')'+returnType.from
    val to get() = '('+parameterTypes.asSequence().map { it.to }.joinToString("")+')'+returnType.to

    fun apply(mappings: Mappings) {
        returnType.apply(mappings)
        parameterTypes.forEach { it.apply(mappings) }
    }
}
