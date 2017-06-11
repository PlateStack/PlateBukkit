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

interface Change {
    val from: Any
    val to: Any
}

/**
 * A field name
 */
data class FieldChange(val name: Name): Change {
    override val from = FieldIdentifier(name.from)
    override val to get() = FieldIdentifier(name.to)
    override fun toString() = "$from -> $to"
}

/**
 * A method name and its descriptor
 */
data class MethodChange(val name: Name, val descriptorType: MethodDescriptor) : Change {
    override val from = MethodIdentifier(name.from, descriptorType.from)
    override val to get() = MethodIdentifier(name.to, descriptorType.to)
    override fun toString() = "$from -> $to"
}

/**
 * A package name
 * @property package The parent where this class resides
 * @property name The actual name of this package. Must contains not contains any separation character!
 */
data class PackageChange(val parent: PackageChange?, var moveTo: PackageChange?, val name: PackageName) : Change {
    override val from: PackageIdentifier = PackageIdentifier(parent?.from, name.from)
    override val to: PackageIdentifier get() = PackageIdentifier(moveTo?.to, name.to)
    override fun toString() = "$from -> $to"
}

/**
 * A migration from a location to an other
 */
interface Move {
    /**
     * The old location
     */
    val old: Change?

    /**
     * The new location
     */
    val new: Change?

    val from: Identifier?
    val to: Identifier?
}

/**
 * A migration from one package to another
 */
data class PackageMove(override val old: PackageChange, override var new: PackageChange = old) : Move {
    override val from get() = old.from
    override val to get() = new.to
    override fun toString() = "$from -> $to"
}

/**
 * A migration from a class to an other
 */
data class ClassMove(override val old: ClassChange?, override var new: ClassChange? = old) : Move {
    override val from get() = old?.from
    override val to get() = new?.to
    override fun toString() = "$from -> $to"
}

/**
 * A full class name
 * @property package The package where this class resides
 * @property parent The class which nests this class or is referred by this class name before the actual name
 * @property name The actual name of this class. Must contains the separation character.
 */
data class ClassChange(val `package`: PackageMove, var parent: ClassMove, val name: ClassName) : Change {
    override val from: ClassIdentifier = ClassIdentifier(`package`.from, parent.from, name.from)
    override val to: ClassIdentifier get() = ClassIdentifier(`package`.to, parent.to, name.to)
    override fun toString() = "$from -> $to"
}
