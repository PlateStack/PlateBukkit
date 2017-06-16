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

package org.platestack.bukkit.scanner.transform

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import org.platestack.bukkit.scanner.mappings.provider.Srg2NotchURLMappingsProvider
import org.platestack.bukkit.scanner.rework.RemapEnvironment
import org.platestack.bukkit.scanner.rework.ResourceLoaderScanner
import org.platestack.bukkit.scanner.structure.ClassIdentifier
import org.platestack.bukkit.scanner.structure.FieldIdentifier
import org.platestack.bukkit.scanner.structure.MethodIdentifier
import org.platestack.common.transform.TransformingClassLoader
import java.io.File
import java.io.InputStream

open class RemapClassLoader(parent: ClassLoader, parentEnvironment: RemapEnvironment): TransformingClassLoader(parent), RemapEnvironmentHost {
    override val environment = RemapEnvironment(parentEnvironment)
    private val scanner = ResourceLoaderScanner(parent)

    private val remapper = object : Remapper() {
        override fun map(typeName: String): String {
            val result = scanner.provide(environment, ClassIdentifier(typeName))?.`class`?.to?.fullName ?: typeName
            return result
        }

        override fun mapFieldName(owner: String, name: String, desc: String): String {
            val cid = ClassIdentifier(owner)
            val fid = FieldIdentifier(name)
            val result = scanner.provide(environment, cid, fid)?.field?.to?.name ?: scanner.provide(environment, cid)?.find(fid)?.field?.name?.to ?: name
            if(Srg2NotchURLMappingsProvider.fieldNamePattern.matches(result))
                NoSuchFieldError("The field $name was remapped to a SRG name \"$result\". " +
                        "This indicates that the original field is not available on this server " +
                        "or has been incorrectly analyzed by the remapper. Field: $owner#$name $desc"
                ).printStackTrace()

            return result
        }

        override fun mapMethodName(owner: String, name: String, desc: String): String {
            val cid = ClassIdentifier(owner)
            val mid = MethodIdentifier(name, desc)
            val result = scanner.provide(environment, cid, mid)?.method?.to?.name ?: scanner.provide(environment, cid)?.find(mid)?.method?.name?.to ?: name
            if(Srg2NotchURLMappingsProvider.methodNamePattern.matches(result))
                NoSuchFieldError("The method $name was remapped to a SRG name \"$result\". " +
                        "This indicates that the original method is not available on this server " +
                        "or has been incorrectly analyzed by the remapper. Method: $owner#$name $desc"
                ).printStackTrace()

            return result
        }
    }

    override fun transform(source: ClassLoader, name: String, input: InputStream): ByteArray {
        val reader = ClassReader(input)
        val writer = ClassWriter(0)

        synchronized(this) {
            ClassRemapper(writer, remapper).let {
                reader.accept(it, 0)
            }
        }

        return writer.toByteArray().also {
            File("classes/$name.class").also { it.parentFile.mkdirs() }.outputStream().buffered().use { out -> out.write(it) }
        }
    }
}
