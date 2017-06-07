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

package org.platestack.bukkit.server.mappings

import org.objectweb.asm.*
import org.objectweb.asm.commons.Remapper
import java.io.InputStream
import java.lang.reflect.Modifier

// Scanners

abstract class StreamScanner : Scanner {
    val knownClasses = HashMap<ClassIdentifier, ClassStructure>()

    fun supplyClassChange(identifier: ClassIdentifier): ClassChange {
        return supplyClass(identifier)?.`class` ?: throw ClassNotFoundException(identifier.fullName)
    }

    open fun supplyClass(identifier: ClassIdentifier, input: InputStream): ClassStructure {
        val visitor = object : ClassVisitor(Opcodes.ASM5) {
            lateinit var structure: ClassStructure
            override fun visit(version: Int, access: Int, name: String?, signature: String?, superName: String?, interfaces: Array<out String>?) {
                structure = ClassStructure(
                        ClassChange(PackageChange(Name(identifier.`package`.fullName)), Name(identifier.className)),
                        superName?.let { supplyClass(ClassIdentifier(it)) },
                        interfaces?.map { supplyClass(ClassIdentifier(it)) ?: throw ClassNotFoundException(it) } ?: emptyList()
                )
                knownClasses[structure.`class`.from] = structure
            }

            override fun visitField(access: Int, name: String, desc: String, signature: String?, value: Any?): FieldVisitor? {
                val field = FieldIdentifier(name, desc)
                val superStructure = structure.find(field)
                structure.fields[field] =
                        superStructure?.let { FieldStructure(superStructure.field, it.owner, AccessLevel[access]) }
                                ?: FieldStructure(
                                FieldChange(
                                        Name(field.name),
                                        SignatureType(field.signature) { supplyClassChange(it) }
                                ),
                                structure.`class`, AccessLevel[access]
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
                                        MethodSignature(method.signature) { supplyClassChange(it) }
                                ),
                                structure.`class`, AccessLevel[access]
                        )

                return null
            }
        }

        ClassReader(input).accept(visitor, 0)
        return visitor.structure
    }
}

class ClassLoaderResourceScanner(val classLoader: ClassLoader): StreamScanner() {
    override fun supplyClass(identifier: ClassIdentifier): ClassStructure? {
        knownClasses[identifier]?.let { return it }

        classLoader.getResourceAsStream(identifier.fullName+".class")?.use { input ->
            return supplyClass(identifier, input)
        }

        throw ClassNotFoundException(identifier.fullName)
    }
}

interface Scanner {
    fun supplyClass(identifier: ClassIdentifier): ClassStructure? = null
    fun supplyField(classStructure: ClassStructure, identifier: FieldIdentifier): FieldStructure? = null
    fun supplyMethod(classStructure: ClassStructure, identifier: MethodIdentifier): MethodStructure? = null
}

// Environments

class ClassRemapEnvironment(
        val classBuilder: ((ClassIdentifier)-> ClassStructure?)?,
        val fieldBuilder: ((ClassStructure, FieldIdentifier) -> FieldStructure?)?,
        val methodBuilder: ((ClassStructure, MethodIdentifier) -> MethodStructure?)?,
        mappings: Mappings = Mappings()
) : Remapper() {
    constructor(scanner: Scanner): this(scanner::supplyClass, scanner::supplyField, scanner::supplyMethod)
    val classes = HashMap<ClassIdentifier, ClassStructure>()
    var mappings = mappings; private set

    fun apply(mappings: Mappings) {
        classes.values.forEach { it.apply(mappings) }
        this.mappings = mappings
    }

    private operator fun get(fromFullClassName: String) = get(ClassIdentifier(fromFullClassName))

    private fun register(structure: ClassStructure) {
        classes.computeIfAbsent(structure.`class`.from) { _ ->
            structure.`super`?.let { register(it) }
            structure.interfaces.forEach { register(it) }
            structure.apply(mappings)
            structure
        }
    }

    operator fun get(fromClass: ClassIdentifier) =
            classes[fromClass]
            ?:
            classBuilder?.invoke(fromClass)?.also { register(it) }


    override fun map(fromFullClassName: String): String {
        return get(fromFullClassName)?.`class`?.to?.toString() ?: fromFullClassName
    }

    override fun mapFieldName(owner: String, name: String, desc: String): String {
        val classStructure = get(owner) ?: return name
        val identifier = FieldIdentifier(name, desc)
        val fieldStructure = classStructure.find(identifier) ?: fieldBuilder?.invoke(classStructure, identifier)?.also {
            classStructure.fields[it.field.from] = it
            it.apply(mappings)
        }

        return fieldStructure?.field?.to?.name ?: name
    }

    override fun mapMethodName(owner: String, name: String, desc: String): String {
        val classStructure = get(owner) ?: return name
        val identifier = MethodIdentifier(name, desc)
        val methodStructure = classStructure.find(identifier) ?: methodBuilder?.invoke(classStructure, identifier)?.also {
            classStructure.methods[it.method.from] = it
            it.apply(mappings)
        }

        return methodStructure?.method?.to?.name ?: name
    }
}

// Structures

enum class AccessLevel {
    PRIVATE, INTERNAL, PROTECTED, PUBLIC, UNKNOWN;

    companion object {
        operator fun get(modifier: Int): AccessLevel {
            return if (Modifier.isPublic(modifier))
                PUBLIC
            else if (Modifier.isProtected(modifier))
                PROTECTED
            else if (Modifier.isPrivate(modifier))
                PRIVATE
            else
                INTERNAL
        }
    }
}

interface ClassScoped {
    val owner: ClassChange
    val access: AccessLevel
    fun canBeAccessedBy(`class`: ClassStructure) = true
}

data class MethodStructure(val method: MethodChange, override val owner: ClassChange, override var access: AccessLevel) : ClassScoped {
    fun apply(mappings: Mappings) {
        method.apply(mappings, mappings.methods[owner.from to method.from]?.also {
            owner.name.reverse = it.first.className
            owner.`package`.name.reverse = it.first.`package`.fullName
        })
    }
}

data class FieldStructure(val field: FieldChange, override val owner: ClassChange, override var access: AccessLevel) : ClassScoped {
    fun apply(mappings: Mappings) {
        field.apply(mappings, mappings.fields[owner.from to field.from]?.also {
            owner.name.reverse = it.first.className
            owner.`package`.name.reverse = it.first.`package`.fullName
        })
    }
}

class ClassStructure(val `class`: ClassChange, val `super`: ClassStructure?, val interfaces: List<ClassStructure>) {
    val fields = HashMap<FieldIdentifier, FieldStructure>()
    val methods = HashMap<MethodIdentifier, MethodStructure>()

    fun apply(mappings: Mappings) {
        `class`.apply(mappings)
        fields.values.forEach{ it.apply(mappings) }
        methods.values.forEach{ it.apply(mappings) }
    }

    fun find(method: MethodIdentifier, viewer: ClassStructure): MethodStructure? =
            sequenceOf(
                    methods[method],
                    `super`?.find(method, viewer),
                    interfaces.asSequence().mapNotNull { it.find(method, viewer) }.firstOrNull()
            )
            .filterNotNull()
            .filter { it.canBeAccessedBy(viewer) }
            .firstOrNull()

    fun find(field: FieldIdentifier, viewer: ClassStructure): FieldStructure? =
            sequenceOf(
                    fields[field],
                    `super`?.find(field, viewer),
                    interfaces.asSequence().mapNotNull { it.find(field, viewer) }.firstOrNull()
            )
            .filterNotNull()
            .filter { it.canBeAccessedBy(viewer) }
            .firstOrNull()

    fun find(field: FieldIdentifier): FieldStructure?
            = fields[field]
            ?: `super`?.find(field, this)
            ?: interfaces.asSequence().mapNotNull { it.find(field, this) }.firstOrNull()

    fun find(method: MethodIdentifier): MethodStructure?
            = methods[method]
            ?: `super`?.find(method, this)
            ?: interfaces.asSequence().mapNotNull { it.find(method, this) }.firstOrNull()
}

// Signatures

data class Name(val current: String, var reverse: String = current)
data class SignatureType(val array: Boolean, val descriptor: Char, val type: ClassChange?) {
    constructor(signature: String, classSupplier: (ClassIdentifier) -> ClassChange): this(StringBuilder(signature), classSupplier)
    private constructor(b: StringBuilder, classSupplier: (ClassIdentifier) -> ClassChange)
            : this(
                if(b.first() == '[') { b.deleteCharAt(0); true } else false,
                b.first(), b.first().let { if(it == 'L') classSupplier(ClassIdentifier(b.substring(1, b.length -1))) else null }
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

// Identifiers

data class FieldIdentifier(val name: String, val signature: String) {
    override fun toString() = "$name $signature"
}

data class MethodIdentifier(val name: String, val signature: String) {
    override fun toString() = "$name $signature"
}

data class PackageIdentifier(val parent: PackageIdentifier?, val name: String) {
    constructor(parentName: String, name: String): this(if(parentName.isEmpty()) null else PackageIdentifier(parentName+'/'), name)
    constructor(fullName: String): this(StringBuilder(fullName))
    private constructor(b: StringBuilder): this(
            b.let {
                check(b.last() == '/') {"$b does not ends with '/'"}
                b.deleteCharAt(b.length-1)
                val index = b.lastIndexOf("/")
                if(index == -1)
                    ""
                else {
                    val parent = b.substring(0, index)
                    b.delete(0, index+1)
                    parent
                }
            },
            b.append('/').toString()
    )

    init {
        check(name.isNotBlank()) { "Package name is blank" }
        check(name != "/") { "Package can't be named '/' : $this" }
        check(name.endsWith('/')) { "Package name does not ends with '/': $this " }
        check('/' !in name.substring(0, name.length-1)) { "Package name contains '/' in the middle of the name '$name' : $this" }
    }

    val fullName = (parent?.let { "$it" } ?: "") + name
    override fun toString() = fullName
}

data class ClassIdentifier(val `package`: PackageIdentifier, val className: String) {
    constructor(packageName: String, className: String): this(PackageIdentifier(packageName+'/'), className)
    constructor(fullName: String): this(fullName.substringBeforeLast('/', ""), fullName.substringAfterLast('/'))

    val fullName = "$`package`$className"
    override fun toString() = fullName
}

// Named

data class FieldChange(val name: Name, val type: SignatureType) {
    val from = FieldIdentifier(name.current, type.from)
    val to get() = FieldIdentifier(name.reverse, type.to)

    fun apply(mappings: Mappings, new: FieldToken?) {
        type.apply(mappings)
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
}

data class ClassChange(val `package`: PackageChange, val name: Name) {
    val from = ClassIdentifier(`package`.from, name.current)
    val to get() = ClassIdentifier(`package`.to, name.reverse)

    fun apply(mappings: Mappings) {
        mappings.classes[from]?.also { new ->
            name.reverse = new.className
            `package`.name.reverse = new.`package`.fullName
        }
    }
}
