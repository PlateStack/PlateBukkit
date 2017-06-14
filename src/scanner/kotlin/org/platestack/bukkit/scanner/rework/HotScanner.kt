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

import org.objectweb.asm.Type
import org.objectweb.asm.Type.*
import org.platestack.bukkit.scanner.structure.*
import org.platestack.util.tryIgnoring
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier.*

open class HotScanner(val classLoader: ClassLoader) : ClassScanner {
    protected val ClassIdentifier.hotName get() = fullName.replace('/', '.')
    protected val Class<*>.coldName get() = name.replace('.','/')

    override fun fullScan(environment: RemapEnvironment, classId: ClassIdentifier): ClassStructure? {
        val structure = scan(environment, classId, true) ?: return null
        val `class` = classLoader.loadClass(classId.hotName)
        ClassScanner.fillStructure(
                this, environment, structure,
                `class`.declaredFields.map { FieldIdentifier(it.name) },
                `class`.declaredMethods.map { MethodIdentifier(it.name, getMethodDescriptor(it)) }
        )
        return structure
    }

    override fun scan(environment: RemapEnvironment, classId: ClassIdentifier, fullParents: Boolean): ClassStructure? {
        val `class` = try {
            classLoader.loadClass(classId.hotName)
        }
        catch (ignored: ClassNotFoundException) {
            return null
        }

        return ClassScanner.createStructure(
                this, environment, classId, fullParents,
                `class`.superclass?.let { ClassIdentifier(it.coldName) },
                `class`.interfaces.map { ClassIdentifier(it.coldName) }.toSet(),
                `class`.isInterface
        )
    }

    private fun findVisibleMethod(from: Class<*>, current: Class<*>, methodName: String, parameters: Array<Class<*>>): Method? {
        val declared = tryIgnoring(NoSuchMethodException::class) { current.getDeclaredMethod(methodName, *parameters) }
                ?: current.superclass?.let { return findVisibleMethod(from, it, methodName, parameters) }
                ?: return null

        return declared.modifiers.let { mod->
            declared.takeIf {
                when {
                    isStatic(mod) || isPrivate(mod) -> from == current
                    //isPublic(mod) -> true // Not needed here but would be needed if this method was public
                    isProtected(mod) -> true // Assumes that from is an instance of current
                    else -> from.`package` == current.`package`
                }
            }
        }
    }

    private fun Type.toClass(): Class<*> {
        return when (sort) {
            VOID -> Void.TYPE
            BOOLEAN -> java.lang.Boolean.TYPE
            CHAR -> Character.TYPE
            BYTE -> java.lang.Byte.TYPE
            SHORT -> java.lang.Short.TYPE
            INT -> java.lang.Integer.TYPE
            FLOAT -> java.lang.Float.TYPE
            LONG -> java.lang.Long.TYPE
            DOUBLE -> java.lang.Double.TYPE
            OBJECT -> classLoader.loadClass(className)
            ARRAY ->  java.lang.reflect.Array.newInstance(elementType.toClass(), *IntArray(dimensions)).javaClass
            else -> throw UnsupportedOperationException("Unknown type: $sort - $this")
        }
    }

    private fun getVisibleMethod(`class`: Class<*>, methodId: MethodIdentifier): Method? {
        val type = getType(methodId.descriptor)
        val name = methodId.name
        val parameters = type.argumentTypes.map { it.toClass() }.toTypedArray()
        return findVisibleMethod(`class`, `class`, name, parameters)
    }

    fun Method.findParentMethod(environment: RemapEnvironment, viewer: ClassIdentifier, from: Class<*>, methodId: MethodIdentifier): MethodStructure? {
        val parents = (sequenceOf(from.superclass) + from.interfaces.asSequence()).filterNotNull()
        val parentMethod = parents.map { provide(environment, ClassIdentifier(it.coldName), methodId) }.filterNotNull().firstOrNull()
        if(parentMethod != null && parentMethod.isStatic == false) {
            if(when(parentMethod.access) {
                AccessLevel.PRIVATE -> false
                AccessLevel.INTERNAL -> parentMethod.owner.`package`.from == viewer.`package`
                AccessLevel.PROTECTED, AccessLevel.PUBLIC, AccessLevel.UNKNOWN -> true
            }) {
                return parentMethod
            }
        }

        return null
    }

    override fun scan(environment: RemapEnvironment, classId: ClassIdentifier, methodId: MethodIdentifier): MethodStructure? {
        val `class` = try {
            classLoader.loadClass(classId.hotName)
        }
        catch (ignored: ClassNotFoundException) {
            return null
        }

        val method = getVisibleMethod(`class`, methodId) ?: return null
        val parentMethod = method.findParentMethod(environment, classId, `class`, methodId)
        return MethodStructure(
                parentMethod?.method ?: methodId.toChange { checkNotNull(provide(environment, it)).`class` },
                parentMethod?.owner ?: checkNotNull(provide(environment, classId), { ClassNotFoundException(classId.fullName) }).`class`,
                AccessLevel[method.modifiers],
                isStatic(method.modifiers)
        )
    }

    private fun findVisibleField(from: Class<*>, current: Class<*>, fieldName: String): Field? {
        val declared = tryIgnoring(NoSuchFieldException::class) { current.getDeclaredField(fieldName) }
                ?: current.superclass?.let { return findVisibleField(from, it, fieldName) }
                ?: return null

        return declared.modifiers.let { mod->
            declared.takeIf {
                when {
                    isStatic(mod) || isPrivate(mod) -> from == current
                    //isPublic(mod) -> true // Not needed here but would be needed if this method was public
                    isProtected(mod) -> true // Assumes that from is an instance of current
                    else -> from.`package` == current.`package`
                }
            }
        }
    }

    private fun getVisibleField(`class`: Class<*>, fieldName: String): Field? {
        return findVisibleField(`class`, `class`, fieldName)
    }

    override fun scan(environment: RemapEnvironment, classId: ClassIdentifier, fieldId: FieldIdentifier): FieldStructure? {
        val `class` = try {
            classLoader.loadClass(classId.hotName)
        }
        catch (ignored: ClassNotFoundException) {
            return null
        }

        val field = getVisibleField(`class`, fieldId.name) ?: return null
        val ownerId = ClassIdentifier(field.declaringClass.coldName)
        val mod = field.modifiers
        return FieldStructure(
                fieldId.toChange(),
                checkNotNull(provide(environment, ownerId), { ClassNotFoundException(ownerId.fullName) }).`class`,
                AccessLevel[mod],
                isStatic(mod),
                ParameterDescriptor(getDescriptor(field.type)) {
                    checkNotNull(provide(environment, it)).`class`
                }
        )
    }
}
