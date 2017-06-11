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

import org.objectweb.asm.*
import org.platestack.bukkit.scanner.structure.*
import org.platestack.bukkit.scanner.structure.AccessLevel.*
import org.platestack.util.tryIgnoring
import java.lang.reflect.Modifier

interface ColdScanner : ClassScanner {
    fun fullScan(environment: RemapEnvironment, classId: ClassIdentifier, reader: ClassReader): ClassStructure {
        val fieldsVisitor = FieldStructureVisitor(this, environment, classId, null, null)
        val methodsVisitor = MethodStructureVisitor(this, environment, classId, null, fieldsVisitor)
        val structure = scan(environment, classId, true, reader, methodsVisitor)

        structure.isFull = true

        fieldsVisitor.fields.forEach {
            structure.fields[it.field.from] = it
        }

        buildMethodStructures(this, environment, classId, methodsVisitor.superclass, requireNotNull(methodsVisitor.interfaceIds), methodsVisitor.methods).forEach {
            structure.methods[it.method.from] = it
        }

        ClassScanner.fillStructure(
                this, environment, structure,
                emptyList(), emptyList()
        )

        return structure
    }

    data class MethodStructureData(val methodId: MethodIdentifier, val access: Int)

    class MethodStructureVisitor(
            val scanner: ClassScanner,
            val environment: RemapEnvironment,
            classId: ClassIdentifier,
            val methodId: MethodIdentifier?,
            delegate: ClassVisitor?,
            val constructors: Boolean = false
    ) : ClassHierarchyVisitor(classId, delegate, false) {
        var methods: MutableList<ColdScanner.MethodStructureData> = if(methodId == null) mutableListOf() else ArrayList(1)

        /*
        fun findMethodOwner(methodId: MethodIdentifier): MethodChange {
            val parents = (sequenceOf(superclass) + checkNotNull(interfaceIds).asSequence()).filterNotNull()
            val parentMethod = parents.map { scanner.provide(environment, it, methodId) }.filterNotNull().firstOrNull()
            if(parentMethod != null && parentMethod.isStatic == false) {
                if(when(parentMethod.access) {
                    PRIVATE -> false
                    INTERNAL -> parentMethod.owner.`package`.from == classId.`package`
                    PROTECTED, PUBLIC, UNKNOWN -> true
                }) {
                    return parentMethod.method
                }
            }

            return methodId.toChange { checkNotNull(scanner.provide(environment, it)).`class` }
        }
        */

        override fun visitMethod(access: Int, name: String, desc: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
            if((constructors || (name != "<init>" && name != "<clinit>")) && (methodId == null || (name == methodId.name && desc == methodId.descriptor))) {
                val methodId = methodId ?: MethodIdentifier(name, desc)

                /*
                val method = MethodStructure(
                        findMethodOwner(methodId),
                        checkNotNull(scanner.provide(environment, checkNotNull(classId))).`class`,
                        AccessLevel[access],
                        Modifier.isStatic(access)
                )
                */
                val method = ColdScanner.MethodStructureData(methodId, access)
                methods.add(method)
                if(this.methodId != null && cv == null)
                    throw Abort
            }

            return cv?.safeCall { visitMethod(access, name, desc, signature, exceptions) }
        }
    }

    class FieldStructureVisitor(
            val scanner: ClassScanner,
            val environment: RemapEnvironment,
            classId: ClassIdentifier,
            val fieldId: FieldIdentifier?,
            delegate: ClassVisitor?
    ) : ClassHierarchyVisitor(classId, delegate, false) {
        var fields: MutableList<FieldStructure> = if(fieldId == null) mutableListOf() else ArrayList(1)

        override fun visitField(access: Int, name: String, desc: String, signature: String?, value: Any?): FieldVisitor? {
            if(fieldId == null || name == fieldId.name) {
                val field = FieldStructure(
                        (fieldId ?: FieldIdentifier(name)).toChange(),
                        checkNotNull(scanner.provide(environment, checkNotNull(classId))).`class`,
                        AccessLevel[access],
                        Modifier.isStatic(access),
                        ParameterDescriptor(desc) {
                            checkNotNull(scanner.provide(environment, it)).`class`
                        }
                )
                fields.add(field)
                if(fieldId != null && cv == null)
                    throw Abort
            }

            return cv?.safeCall { visitField(access, name, desc, signature, value) }
        }
    }

    fun scan(environment: RemapEnvironment, classId: ClassIdentifier, fieldId: FieldIdentifier, classReader: ClassReader, delegate: ClassVisitor? = null): FieldStructure? {
        /*
        var field: FieldStructure? = null
        var superclass: ClassIdentifier? = null

        val visitor = object : ClassVisitor(Opcodes.ASM5, delegate) {
            override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
                if(name != classId.fullName)
                    throw DifferentClassException(expected = classId.fullName, found = name)

                superName?.let { superclass = ClassIdentifier(it) }
                delegate?.safeCall { visit(version, access, name, signature, superName, interfaces) }
            }

            override fun visitField(access: Int, name: String, desc: String, signature: String?, value: Any?): FieldVisitor? {
                if(name == fieldId.name) {
                    field = FieldStructure(
                            fieldId.toChange(),
                            checkNotNull(provide(environment, classId)).`class`,
                            AccessLevel[access],
                            Modifier.isStatic(access),
                            ParameterDescriptor(desc) {
                                checkNotNull(provide(environment, it)).`class`
                            }
                    )
                    if(delegate == null)
                        throw Abort
                }

                return delegate?.safeCall { visitField(access, name, desc, signature, value) }
            }
        }
        */
        val visitor = FieldStructureVisitor(this, environment, classId, fieldId, null)

        try {
            classReader.accept(visitor, 0)
        }
        catch (_: Abort) {
            return visitor.fields.first()
        }

        if(visitor.fields.isNotEmpty())
            return visitor.fields.first()

        val superId = visitor.superclass ?: return null

        val superField = provide(environment, superId, fieldId) ?: return null
        if(superField.static == true)
            return null

        return superField.takeIf {
            when (superField.access) {
                PRIVATE -> false
                INTERNAL -> superField.owner.`package`.from == classId.`package`
                PROTECTED, PUBLIC, UNKNOWN -> true
            }
        }
    }

    fun scan(environment: RemapEnvironment, classId: ClassIdentifier, methodId: MethodIdentifier, classReader: ClassReader): MethodStructure? {
        val visitor = MethodStructureVisitor(this, environment, classId, methodId, null)

        fun getMethod() =
                buildMethodStructures(this, environment, classId, visitor.superclass, checkNotNull(visitor.interfaceIds), visitor.methods)
                .first()

        try {
            classReader.accept(visitor, 0)
        }
        catch (_: Abort) {
            return getMethod()
        }

        if(visitor.methods.isNotEmpty())
            return getMethod()

        val superId = visitor.superclass ?: return null

        val superMethod = provide(environment, superId, methodId) ?: return null
        if(superMethod.isStatic == true)
            return null

        return superMethod.takeIf {
            when (superMethod.access) {
                PRIVATE -> false
                INTERNAL -> superMethod.owner.`package`.from == classId.`package`
                PROTECTED, PUBLIC, UNKNOWN -> true
            }
        }
    }

    open class ClassHierarchyVisitor(val classId: ClassIdentifier, delegate: ClassVisitor? = null, val abort: Boolean = true) : ClassVisitor(Opcodes.ASM5, delegate) {
        var superclass: ClassIdentifier? = null
        var interfaceIds: Set<ClassIdentifier>? = null
        var isInterface = false

        override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
            if(name != classId.fullName)
                throw DifferentClassException(expected = classId.fullName, found = name)

            isInterface = Modifier.isInterface(access)
            superclass = if(isInterface && superName == "java/lang/Object") null else superName?.let { ClassIdentifier(superName) }
            interfaceIds = interfaces?.asSequence()?.map { ClassIdentifier(it) }?.toSet() ?: emptySet()
            if(abort && cv == null)
                throw Abort

            cv?.safeCall { visit(version, access, name, signature, superName, interfaces) }
        }
    }

    fun scan(environment: RemapEnvironment, classId: ClassIdentifier, fullParents: Boolean = false, reader: ClassReader, delegate: ClassVisitor? = null) : ClassStructure {
        val visitor = ClassHierarchyVisitor(classId, delegate)

        tryIgnoring(Abort::class) {
            reader.accept(visitor, 0)
        }

        return ClassScanner.createStructure(
                this, environment, classId, fullParents,
                visitor.superclass, checkNotNull(visitor.interfaceIds), visitor.isInterface
        )
    }

    class DifferentClassException(expected: String, found: String): ClassNotFoundException("Expected: $expected, Found: $found")

    object Abort: Throwable(null, null, false, false)

    companion object {
        inline fun <T> ClassVisitor.safeCall(function: ClassVisitor.()->T): T {
            try {
                return function()
            }
            catch (_: Abort) {
                throw InterruptedException("Caught Abort from delegated visitor")
            }
        }

        fun findMethodOwner(scanner: ClassScanner, environment: RemapEnvironment, classId: ClassIdentifier, superclass: ClassIdentifier?, interfaceIds: Set<ClassIdentifier>, methodId: MethodIdentifier): MethodStructure? {
            val parents = (sequenceOf(superclass) + checkNotNull(interfaceIds).asSequence()).filterNotNull()
            val parentMethod = parents.map { scanner.provide(environment, it, methodId) }.filterNotNull().firstOrNull()
            if(parentMethod != null && parentMethod.isStatic == false) {
                if(when(parentMethod.access) {
                    PRIVATE -> false
                    INTERNAL -> parentMethod.owner.`package`.from == classId.`package`
                    PROTECTED, PUBLIC, UNKNOWN -> true
                }) {
                    return parentMethod
                }
            }

            return null
        }

        fun buildMethodStructures(
                scanner: ClassScanner,
                environment: RemapEnvironment,
                classId: ClassIdentifier,
                superclass: ClassIdentifier?,
                interfaceIds: Set<ClassIdentifier>,
                methods: List<MethodStructureData>
        ) = methods.asSequence().map {
            val parentMethod = findMethodOwner(scanner, environment, classId, superclass, interfaceIds, it.methodId)
            MethodStructure(
                    parentMethod?.method ?: it.methodId.toChange { checkNotNull(scanner.provide(environment, it)).`class` },
                    parentMethod?.owner ?: checkNotNull(scanner.provide(environment, checkNotNull(classId))).`class`,
                    AccessLevel[it.access],
                    Modifier.isStatic(it.access)
            )
        }
    }
}
