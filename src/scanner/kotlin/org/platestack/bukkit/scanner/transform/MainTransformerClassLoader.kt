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
import org.platestack.bukkit.scanner.rework.RemapEnvironment
import org.platestack.bukkit.scanner.rework.ResourceLoaderScanner
import org.platestack.bukkit.scanner.structure.ClassIdentifier
import org.platestack.bukkit.scanner.structure.FieldIdentifier
import org.platestack.bukkit.scanner.structure.MethodIdentifier
import org.platestack.common.transform.TransformingClassLoader
import java.io.InputStream

class MainTransformerClassLoader(parent: MainClassLoader): TransformingClassLoader(parent), RemapEnvironmentHost {
    override val environment = RemapEnvironment(parent.environment)
    private val scanner = ResourceLoaderScanner(parent)

    private val remapper = object : Remapper() {
        override fun map(typeName: String): String {
            return scanner.provide(environment, ClassIdentifier(typeName))?.`class`?.to?.fullName ?: typeName
        }

        override fun mapFieldName(owner: String, name: String, desc: String): String {
            return scanner.provide(environment, ClassIdentifier(owner), FieldIdentifier(name))?.field?.to?.name ?: name
        }

        override fun mapMethodName(owner: String, name: String, desc: String): String {
            return scanner.provide(environment, ClassIdentifier(owner), MethodIdentifier(name, desc))?.method?.to?.name ?: name
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

        return writer.toByteArray()
    }
}
