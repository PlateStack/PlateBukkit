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

package org.platestack.bukkit.scanner.rework

import org.platestack.bukkit.scanner.structure.*

/**
 * Object which can provide structural information about classes
 */
interface ClassScanner {
    /**
     * Provide information about a class hierarchy or return null when not possible.
     *
     * @param fullParents If unregisterd
     * @return A new [ClassStructure] object containing a class structure which contains only the super class and interfaces
     *
     * The returned object will not be automatically registered to the environment.
     *
     * All super classes and interfaces will be automatically registered to the environment when absent.
     */
    fun scan(environment: RemapEnvironment, classId: ClassIdentifier, fullParents: Boolean = false): ClassStructure?

    /**
     * Provides full information about a class
     */
    fun fullScan(environment: RemapEnvironment, classId: ClassIdentifier): ClassStructure?

    fun scan(environment: RemapEnvironment, classId: ClassIdentifier, fieldId: FieldIdentifier): FieldStructure?

    fun scan(environment: RemapEnvironment, classId: ClassIdentifier, methodId: MethodIdentifier): MethodStructure?

    fun provide(environment: RemapEnvironment, packageId: PackageIdentifier): PackageMove {
        return environment[packageId] ?: PackageMove(packageId.toChange { provide(environment, it).old })
    }

    fun provide(environment: RemapEnvironment, classId: ClassIdentifier, fullParents: Boolean = false): ClassStructure? {
        val structure = environment[classId] ?: scan(environment, classId, fullParents)?.also {
            environment[classId] = it
        } ?: return null

        if(!fullParents || structure.isFull)
            return structure

        structure.`super`?.takeUnless { it.isFull }?.let { provideFull(environment, it.`class`.from) }
        structure.interfaces.forEach {
            if(!it.isFull) {
                provideFull(environment, it.`class`.from)
            }
        }

        return structure
    }

    fun provideFull(environment: RemapEnvironment, classId: ClassIdentifier) : ClassStructure? {
        val structure = provide(environment, classId, true) ?: return null
        structure.let { if(it.isFull) return it }
        val full = fullScan(environment, classId) ?: return null
        full.fields.forEach {
            structure.fields.putIfAbsent(it.key, it.value)
        }

        full.methods.forEach {
            structure.methods.putIfAbsent(it.key, it.value)
        }

        structure.isInterface = full.isInterface
        structure.isFull = true
        return structure
    }

    fun provide(environment: RemapEnvironment, classId: ClassIdentifier, fieldId: FieldIdentifier): FieldStructure? {
        return environment[classId]?.fields?.get(fieldId) ?: scan(environment, classId, fieldId)?.also {
            checkNotNull(provide(environment, classId)).fields[fieldId] = it
        }
    }

    fun provide(environment: RemapEnvironment, classId: ClassIdentifier, methodId: MethodIdentifier): MethodStructure? {
        return environment[classId]?.methods?.get(methodId) ?: scan(environment, classId, methodId)?.also {
            checkNotNull(provide(environment, classId)).methods[methodId] = it
        }
    }

    companion object {
        fun fillStructure(
                scanner: ClassScanner,
                environment: RemapEnvironment,
                structure: ClassStructure,
                fields: List<FieldIdentifier>,
                methods: List<MethodIdentifier>
        ) {
            val classId = structure.`class`.from
            structure.isFull = true

            fields.forEach { fieldId ->
                val fieldStructure = checkNotNull(scanner.provide(environment, classId, fieldId))
                structure.fields[fieldId] = fieldStructure
            }

            methods.forEach { methodId ->
                val methodStructure = checkNotNull(scanner.provide(environment, classId, methodId)) {
                    "Couldn't find the method $classId#$methodId"
                }
                structure.methods[methodId] = methodStructure
            }

            structure.`super`?.let { sup ->
                if(!sup.isFull) {
                    scanner.provideFull(environment, sup.`class`.from)
                }
            }

            structure.interfaces.forEach {
                if(!it.isFull) {
                    scanner.provideFull(environment, it.`class`.from)
                }
            }
        }

        fun createStructure(scanner: ClassScanner,
                environment: RemapEnvironment, classId: ClassIdentifier, fullParents: Boolean,
                `super`: ClassIdentifier?, interfaces: Set<ClassIdentifier>, isInterface: Boolean
        ): ClassStructure {
            val structure = ClassStructure(classId,
                    `super`,
                    isInterface,
                    interfaces,
                    packageProvider = { scanner.provide(environment, it) },
                    parentProvider = {
                        val parent = scanner.provide(environment, it, fullParents)

                        if(parent != null)
                            ClassMove(parent.`class`)
                        else {
                            val fake = ClassStructure(it,
                                    null,
                                    null,
                                    emptySet(),
                                    structureProvider = { error("Unexpected call!") }
                            )
                            environment[it] = fake
                            ClassMove(fake.`class`)
                        }
                    },
                    structureProvider = { parentId ->

                        fun createFakeIntermediary(id: ClassIdentifier): ClassStructure {
                            return if(id.parent != null) {
                                System.err.println("Creating fake structure for intermediary class: $id")
                                val fake = ClassStructure(id.toChange(
                                        packageProvider = { scanner.provide(environment, it) },
                                        parentProvider = { (scanner.provide(environment, it, fullParents) ?: createFakeIntermediary(it)).`class` }
                                ), null, null, emptySet()).also {
                                    environment[it.`class`.from] = it
                                }
                                fake
                            }
                            else {
                                error("Referred class not found: $id ; Referred by: $classId")
                            }
                        }

                        scanner.provide(environment, parentId, fullParents) ?: createFakeIntermediary(parentId)  }
            )

            return structure
        }
    }
}
