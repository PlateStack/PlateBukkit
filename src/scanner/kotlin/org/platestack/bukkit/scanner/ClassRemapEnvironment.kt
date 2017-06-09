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

package org.platestack.bukkit.scanner

import org.objectweb.asm.commons.Remapper
import org.platestack.bukkit.scanner.mappings.Mappings
import org.platestack.bukkit.scanner.structure.*

class ClassRemapEnvironment(
        val classBuilder: ((ClassIdentifier)-> ClassStructure?)?,
        val fieldBuilder: ((ClassStructure, FieldIdentifier) -> FieldStructure?)?,
        val methodBuilder: ((ClassStructure, MethodIdentifier) -> MethodStructure?)?,
        mappings: Mappings = Mappings()
) : Remapper() {
    constructor(scanner: Scanner): this(scanner::supplyClass, scanner::supplyField, scanner::supplyMethod)
    val classes = HashMap<ClassIdentifier, ClassStructure>()
    var mappings = mappings.inverse(); private set
    var reverse = mappings

    fun apply(mappings: Mappings) {
        classes.values.forEach { it.apply(mappings) }
        this.mappings = mappings.inverse()
        reverse = mappings
    }

    private operator fun get(fromFullClassName: String) = get(ClassIdentifier(fromFullClassName))

    private fun register(structure: ClassStructure) {
        structure.apply(mappings)
        classes.computeIfAbsent(structure.`class`.to) { _ ->
            structure.`super`?.let { register(it) }
            structure.interfaces.forEach { register(it) }
            structure
        }
    }

    operator fun get(foreign: ClassIdentifier) : ClassStructure? {
        classes[foreign]?.let { return it }

        val native = reverse.classes[foreign] ?: foreign
        return classBuilder?.invoke(native)?.also { register(it) }
    }


    override fun map(fromFullClassName: String): String {
        val foreign = ClassIdentifier(fromFullClassName)
        classes[foreign]?.let { return it.`class`.from.fullName }

        val native = reverse.classes[foreign] ?: foreign
        classBuilder?.invoke(native)?.also { register(it) }
        return native.fullName

        //TODO("Not the information correctly")
        //val id = ClassIdentifier(fromFullClassName)
        //get(id)?.`class`?.to?.toString()?.let { return it }
        //val to = mappings.classes[id] ?: return fromFullClassName
        //val destiny = classBuilder?.invoke(to) ?: return fromFullClassName

    }

    override fun mapFieldName(owner: String, name: String, desc: String): String {
        val classStructure = get(owner) ?: return name
        val identifier = FieldIdentifier(name)
        val fieldStructure = classStructure.findReverse(identifier) ?: fieldBuilder?.invoke(classStructure, identifier)?.also {
            classStructure.fields[it.field.from] = it
            it.apply(mappings)
        }

        return fieldStructure?.field?.from?.name ?: name
    }

    override fun mapMethodName(owner: String, name: String, desc: String): String {
        val classStructure = get(owner) ?: return name
        val identifier = MethodIdentifier(name, desc)
        val methodStructure = classStructure.findReverse(identifier) ?: methodBuilder?.invoke(classStructure, identifier)?.also {
            classStructure.methods[it.method.from] = it
            it.apply(mappings)
        }

        return methodStructure?.method?.from?.name ?: name
    }
}
