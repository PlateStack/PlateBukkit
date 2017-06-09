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

data class FieldIdentifier(val name: String) {
    override fun toString() = name

    fun toChange() = FieldChange(Name(name))
}

data class MethodIdentifier(val name: String, val signature: String) {
    override fun toString() = "$name $signature"

    fun toChange(classSupplier: (ClassIdentifier) -> ClassChange): MethodChange = MethodChange(Name(name), MethodSignature(signature, classSupplier))
}

data class PackageIdentifier constructor(val parent: PackageIdentifier?, val name: String) {
    constructor(parentName: String, name: String): this(if(parentName.isBlank()) null else PackageIdentifier(parentName), name)
    constructor(fullName: String): this(fullName.substringBeforeLast('/', ""), fullName.substringAfterLast('/'))

    init {
        check(name.isNotBlank() || parent == null) { "Package name is blank but has a parent! : $this" }
        check('/' !in name) { "Package can't contains '/' : $this" }
        check('.' !in name) { "Package can't contains '.' : $this" }
    }

    val fullName = if(parent != null) "$parent$name" else name
    val prefix = if(name.isBlank()) "" else "$fullName/"
    override fun toString() = prefix

    fun toChange(): PackageChange = PackageChange(Name(fullName))
}

data class ClassIdentifier(val `package`: PackageIdentifier, val parent: ClassIdentifier?, val className: String) {
    private constructor(`package`: PackageIdentifier, parts: List<String>):
            this(`package`,
                    if(parts.size == 1) null
                    else ClassIdentifier(`package`, parts.subList(0, parts.size - 1)),
                    parts.last()
            )

    constructor(packageName: String, className: String): this(PackageIdentifier(packageName), className.split('$'))
    constructor(fullName: String): this(fullName.substringBeforeLast('/', ""), fullName.substringAfterLast('/'))

    val fullName: String = (if(parent == null) `package`.prefix else parent.fullName + '$') + className
    override fun toString() = fullName

    fun toChange(
            packageSupplier: (PackageIdentifier) -> PackageChange = {it.toChange()},
            classSupplier: (ClassIdentifier) -> ClassChange?
    ): ClassChange = ClassChange(packageSupplier(`package`), parent?.let(classSupplier), Name(className))
}
