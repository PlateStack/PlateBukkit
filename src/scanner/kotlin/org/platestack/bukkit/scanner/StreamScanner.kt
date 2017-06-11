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

import org.platestack.bukkit.scanner.structure.ClassIdentifier
import org.platestack.bukkit.scanner.structure.ClassStructure
import java.io.InputStream

abstract class StreamScanner : Scanner {
    abstract val knownClasses: MutableMap<ClassIdentifier, ClassStructure>
    protected val loadingStructures = ThreadLocal<MutableSet<ClassIdentifier>>()

    fun supplyClass(identifier: ClassIdentifier, input: InputStream): ClassStructure {
        TODO()
        /*
        val loading = loadingStructures.getOrSet { mutableSetOf() }
        if (!loading.add(identifier))
            error("Cyclic loading from: $loading to $identifier")
        try {
            val visitor = object : ClassVisitor(Opcodes.ASM5) {
                lateinit var structure: ClassStructure
                override fun visit(version: Int, access: Int, name: String?, signature: String?, superName: String?, interfaces: Array<out String>?) {
                    fun softSupplyClassChange(it: ClassIdentifier): ClassChange {
                        return supplyClass(it)?.`class` ?: ClassStructure(identifier.toChange(classSupplier = { supplyClassChange(it) }), null, emptyList()).`class` //ClassChange(it.`package`.toChange(), it.parent?.let { softSupplyClassChange(it) }, Name(it.className))
                    }

                    val interfaceList = mutableListOf<ClassStructure>()

                    structure = ClassStructure(null, null, interfaceList)
                    knownClasses[identifier] = structure
                    structure.`class` = ClassChange(identifier.`package`.toChange(), null, Name(identifier.className))
                    loading.remove(identifier)

                    identifier.parent?.let { structure.`class`.parent = softSupplyClassChange(it) }

                    superName?.let { structure.`super` = supplyClass(ClassIdentifier(it)) }

                    interfaces?.map { supplyClass(ClassIdentifier(it)) ?: throw ClassNotFoundException(it) }?.let {
                        interfaceList += it
                    }
                }

                override fun visitField(access: Int, name: String, desc: String, signature: String?, value: Any?): FieldVisitor? {
                    val field = FieldIdentifier(name)
                    val superStructure = structure.find(field)
                    structure.fields[field] =
                            superStructure?.let { FieldStructure(superStructure.field, it.owner, AccessLevel[access], ParameterDescriptor(desc) { supplyClassChange(it) }) }
                                    ?: FieldStructure(
                                    FieldChange(Name(field.name)),
                                    structure.`class`, AccessLevel[access],
                                    ParameterDescriptor(desc) { supplyClassChange(it) }
                            )

                    return null
                }

                override fun visitMethod(access: Int, methodName: String, desc: String, signature: String?, exceptions: Array<String>?): MethodVisitor? {
                    val method = MethodIdentifier(methodName, desc)
                    val superStructure = structure.find(method)
                    structure.methods[method] =
                            superStructure?.let { MethodStructure(superStructure.method, it.owner, AccessLevel[access]) }
                                    ?: MethodStructure(
                                    MethodChange(
                                            Name(method.name),
                                            MethodDescriptor(method.descriptor) { supplyClassChange(it) }
                                    ),
                                    structure.`class`, AccessLevel[access]
                            )

                    return null
                }
            }

            ClassReader(input).accept(visitor, 0)
            return visitor.structure
        }
        finally {
            loading.remove(identifier)
        }
        */
    }
}