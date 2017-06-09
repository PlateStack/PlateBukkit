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

import org.platestack.bukkit.scanner.FieldToken
import org.platestack.bukkit.scanner.MethodToken
import org.platestack.bukkit.scanner.mappings.Mappings

data class FieldChange(val name: Name) {
    val from = FieldIdentifier(name.current)
    val to get() = FieldIdentifier(name.reverse)

    fun apply(new: FieldToken?) {
        new?.let { name.reverse = new.second.name }
    }
}

data class MethodChange(val name: Name, val signatureType: MethodSignature) {
    val from = MethodIdentifier(name.current, signatureType.from)
    val to get() = MethodIdentifier(name.reverse, signatureType.to)

    fun apply(mappings: Mappings, new: MethodToken?) {
        signatureType.apply(mappings)
        new?.let { name.reverse = new.second.name }
    }
}

data class PackageChange(val name: Name) {
    val from = PackageIdentifier(name.current)
    val to get() = PackageIdentifier(name.reverse)

    init {
        check(!name.current.endsWith('/')) { "Package name can't end with '/'" }
        checkReverse()
    }

    fun checkReverse() {
        check(!name.reverse.endsWith('/')) { "Package name can't end with '/'" }
    }
}

data class ClassChange(val `package`: PackageChange, var parent: ClassChange?, val name: Name) {
    val from: ClassIdentifier = ClassIdentifier(`package`.from, parent?.from, name.current)
    val to: ClassIdentifier get() = ClassIdentifier(`package`.to, parent?.to, name.reverse)

    fun apply(mappings: Mappings) {
        mappings.classes[from]?.also { new ->
            name.reverse = new.className
            `package`.name.reverse = new.`package`.fullName
            `package`.checkReverse()
        }
    }

    fun deepCopy(`package`: PackageChange = PackageChange(Name(this.`package`.name.current, this.`package`.name.reverse))) : ClassChange =
            ClassChange(
                    `package`,
                    parent?.deepCopy(`package`),
                    Name(name.current, name.reverse)
            )
}
