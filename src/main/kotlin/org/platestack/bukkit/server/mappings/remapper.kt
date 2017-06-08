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
import org.platestack.api.server.UniqueModification
import java.io.InputStream
import java.lang.reflect.Modifier
import kotlin.concurrent.getOrSet

// Scanners

abstract class StreamScanner : Scanner {
    abstract val knownClasses: MutableMap<ClassIdentifier, ClassStructure>
    protected val loadingStructures = ThreadLocal<MutableSet<ClassIdentifier>>()

    fun supplyClass(identifier: ClassIdentifier, input: InputStream): ClassStructure {
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
                            superStructure?.let { FieldStructure(superStructure.field, it.owner, AccessLevel[access], SignatureType(desc) { supplyClassChange(it) }) }
                                    ?: FieldStructure(
                                            FieldChange(Name(field.name)),
                                            structure.`class`, AccessLevel[access],
                                            SignatureType(desc) { supplyClassChange(it) }
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
        finally {
            loading.remove(identifier)
        }
    }
}

open class ClassLoaderResourceScanner(val classLoader: ClassLoader): StreamScanner() {
    override val knownClasses = HashMap<ClassIdentifier, ClassStructure>()

    override fun supplyClass(identifier: ClassIdentifier): ClassStructure? {
        knownClasses[identifier]?.let { return it }

        classLoader.getResourceAsStream(identifier.fullName+".class")?.use { input ->
            return supplyClass(identifier, input)
        }

        // TODO: Why are we throwing ClassNotFoundEx if we can return null? It makes no sense.
        throw ClassNotFoundException(identifier.fullName)
    }
}

interface Scanner {
    fun supplyClassChange(identifier: ClassIdentifier): ClassChange {
        return supplyClass(identifier)?.`class` ?: ClassStructure(identifier.toChange(classSupplier = this::supplyClassChange), null, emptyList()).`class` //throw ClassNotFoundException(identifier.fullName)
    }

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

data class FieldStructure(val field: FieldChange, override val owner: ClassChange, override var access: AccessLevel, val signature: SignatureType) : ClassScoped {
    fun apply(mappings: Mappings) {
        field.apply(mappings.fields[owner.from to field.from]?.also {
            owner.name.reverse = it.first.className
            owner.`package`.name.reverse = it.first.`package`.fullName
        })
    }
}

class ClassStructure(`class`: ClassChange?, var `super`: ClassStructure?, val interfaces: List<ClassStructure>) {
    var `class` by UniqueModification<ClassChange>()

    init {
        if(`class` != null)
            this.`class` = `class`
    }

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

    fun findReverse(method: MethodIdentifier, viewer: ClassStructure): MethodStructure? =
            sequenceOf(
                    methods[method],
                    methods.values.find { it.method.to == method },
                    `super`?.findReverse(method, viewer),
                    interfaces.asSequence().mapNotNull { it.findReverse(method, viewer) }.firstOrNull()
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

    fun findReverse(field: FieldIdentifier, viewer: ClassStructure): FieldStructure? =
            sequenceOf(
                    fields[field],
                    fields.values.find { it.field.to == field },
                    `super`?.findReverse(field, viewer),
                    interfaces.asSequence().mapNotNull { it.findReverse(field, viewer) }.firstOrNull()
            )
            .filterNotNull()
            .filter { it.canBeAccessedBy(viewer) }
            .firstOrNull()

    fun find(field: FieldIdentifier): FieldStructure?
            = fields[field]
            ?: `super`?.find(field, this)
            ?: interfaces.asSequence().mapNotNull { it.find(field, this) }.firstOrNull()

    fun findReverse(field: FieldIdentifier): FieldStructure?
            = fields[field] ?: fields.values.find { it.field.to == field }
            ?: `super`?.findReverse(field, this)
            ?: interfaces.asSequence().mapNotNull { it.findReverse(field, this) }.firstOrNull()

    fun find(method: MethodIdentifier): MethodStructure?
            = methods[method]
            ?: `super`?.find(method, this)
            ?: interfaces.asSequence().mapNotNull { it.find(method, this) }.firstOrNull()

    fun findReverse(method: MethodIdentifier): MethodStructure?
            = methods[method] ?: methods.values.find { it.method.to == method }
            ?: `super`?.findReverse(method, this)
            ?: interfaces.asSequence().mapNotNull { it.findReverse(method, this) }.firstOrNull()
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
                    else ClassIdentifier(`package`, parts.subList(0, parts.size -1)),
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

// Named

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
