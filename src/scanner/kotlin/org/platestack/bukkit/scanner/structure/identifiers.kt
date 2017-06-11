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

@file:Suppress("EqualsOrHashCode")

package org.platestack.bukkit.scanner.structure

import java.util.*

interface Identifier

/**
 * A field name
 */
data class FieldIdentifier(val name: String): Identifier {
    override fun toString() = name

    fun toChange() = FieldChange(Name(name))
}

private val validMethod = Regex("^\\((\\)V|(\\[?([BCDFIJSZ]|L[^;]+;))*\\)(V|\\[?([BCDFIJSZ]|L[^;]+;)))$")
//private val validMethod = Regex("^\\((\\[?([BCDFIJSZ]|L[^;]+;))*\\)(V|\\[?([BCDFIJSZ]|L[^;]+;))$")

/**
 * A method name and its descriptor
 */
data class MethodIdentifier(val name: String, val descriptor: String): Identifier {
    override fun toString() = "$name $descriptor"

    init {
        check(validMethod.matches(descriptor)) { "Invalid method descriptor for $name: $descriptor" }
    }

    fun toChange(classSupplier: (ClassIdentifier) -> ClassChange = { it.toChange() }) =
            MethodChange(Name(name), MethodDescriptor(descriptor, classSupplier))
}

/**
 * A disassembled package name
 * @property parent The parent package which this package resides
 * @property name This package name, without any separator character
 * @property fullName Name including the parent's name and '/' separators.
 *
 * The returned string is similar to class names, it does **not** ends with '/'
 *
 * @property prefix The same as [fullName] but with a trailing '/'
 */
data class PackageIdentifier constructor(val parent: PackageIdentifier?, val name: String): Identifier {
    constructor(parentName: String, name: String): this(if(parentName.isBlank()) null else PackageIdentifier(parentName), name)
    constructor(fullName: String): this(fullName.substringBeforeLast('/', ""), fullName.substringAfterLast('/'))

    init {
        check(name.isNotBlank() || parent == null) { "Package name is blank but has a parent!" }
        check('/' !in name) { "Package can't contains '/'" }
        check('.' !in name) { "Package can't contains '.'" }
    }

    val fullName = if(parent != null) "$parent$name" else name
    val prefix = if(name.isBlank()) "" else "$fullName/"

    fun toChange(parentSupplier: (PackageIdentifier) -> PackageChange? = {null}): PackageChange {
        val parentChange = parent?.let { parentSupplier(it) ?: it.toChange(parentSupplier) }
        return PackageChange(parentChange, parentChange, PackageName(name))
    }

    // Caches the hash code as this class is immutable and kinda complex
    private val hashCode by lazy { Objects.hash(parent, name) }
    override fun hashCode() = hashCode
    override fun toString() = prefix
}

/**
 * A disassembled class name. Composite class names are disassembled by the '$' character.
 *
 * Full name             | Package     | Parent              | Class name     | Notes
 * --------------------- | ----------- | ------------------- | -------------- | -------------
 * com/example/CoolClass | com.example | null                | CoolClass      | Normal class
 * NoPackageClass        | (empty)     | null                | NoPackageClass | Package is `PackageIdentifier(null, "")`
 * a/Nested$Class        | a           | a/Nested            | $Class         | The parent retains the package, the nested class name retains the '$' symbol
 * a/Class$with$lambda$3 | a           | a/Class$with$lambda | $3             | The parent class may be fake and doesn't exists, but it is still a parent class
 * a/Class$with$lambda   | a           | a/Class$with        | $lambda        | The parent class also retains it's parent classes
 * a/Class$with          | a           | a/Class             | $with          | Up to we reach a normal class
 * scala/Nothing$        | scala       | scala/Nothing       | $              | Trailing '$' does not creates empty class name, it is processed like all other nested classes.
 * scary/$Scala$$$name   | scary       | scary/$Scala$$      | $name          | The name retains its '$'
 * scary/$Scala$$        | scary       | scary/$Scala$       | $              | Every '$' is processed individually
 * scary/$Scala$         | scary       | scary/$Scala        | $              | Class names can't be empty!
 * scary/$Scala          | scary       | null                | $Scala         | So it has no normal parent class in the end
 *
 * @property package The package which this class resides
 * @property parent The class which nests this class or is referred by this class name before the actual name
 * @property name The actual name of this class. Must contains the separation character.
 * @property fullSimpleName The combination of the parent's name with this name, excluding the package name
 * @property fullName The full name including the package and the parent name.
 *
 * If the parent's package differs then the package used by this identifier is used.
 */
data class ClassIdentifier(val `package`: PackageIdentifier, val parent: ClassIdentifier?, val className: String): Identifier {

    init {
        check(className.isNotBlank()) { "The class name can't be empty" }
        check('.' !in className) { "Class name can't contains '.'" }
        check('/' !in className) { "Class name can't contains '/'" }
    }

    val fullSimpleName:String = (parent?.fullSimpleName ?: "") + className
    val fullName: String = `package`.prefix + fullSimpleName

    fun toChange(packageProvider: (PackageIdentifier) -> PackageMove = { PackageMove(it.toChange()) },
                 parentProvider: (ClassIdentifier) -> ClassMove? = { null }
    ): ClassChange {
        return ClassChange(
                packageProvider(`package`),
                parent?.let { parentProvider(it) ?: ClassMove(it.toChange(packageProvider, parentProvider)) }
                        ?: ClassMove(null, null),
                ClassName(className)
        )
    }

    companion object Builder {
        @JvmName("create") @JvmStatic
        operator fun invoke(`package`: PackageIdentifier, compositeName: String): ClassIdentifier {
            require(compositeName.isNotBlank()) { "The composite name can't be empty" }

            var parent: ClassIdentifier? = null
            val parts = compositeName.split('$').mapIndexedTo(ArrayDeque()) { index, part ->
                if(index == 0) part
                else '$'+part
            }

            if(parts.peek().isEmpty())
                parts.remove()

            parts.forEach {
                parent = ClassIdentifier(`package`, parent, it)
            }

            return checkNotNull(parent)
        }

        @JvmName("create") @JvmStatic
        operator fun invoke(fullName: String): ClassIdentifier {
            require(fullName.isNotBlank()) { "The full name can't be empty" }
            require(fullName.last() != '/') { "The full name can't ends with '/'" }
            val index = fullName.lastIndexOf('/')
            val pack: String
            val name: String
            if(index == -1) {
                pack = ""
                name = fullName
            }
            else {
                pack = fullName.substring(0, index)
                name = fullName.substring(index+1)
            }

            return ClassIdentifier(PackageIdentifier(pack), name)
        }
    }

    // Caches the hash code as this class is immutable and kinda complex
    private val hashCode by lazy { Objects.hash(`package`, parent, className) }
    override fun hashCode() = hashCode
    override fun toString() = fullName
}
