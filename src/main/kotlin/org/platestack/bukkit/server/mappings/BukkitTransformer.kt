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

import org.bukkit.Bukkit
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.platestack.api.server.UniqueModification
import org.platestack.common.plugin.loader.Transformer
import java.io.InputStream
import java.util.logging.Logger

object BukkitTransformer: Transformer, Scanner {
    private lateinit var classLoader: ClassLoader
    private var logger by UniqueModification<Logger>()
    private val bukkitScanner by lazy { ClassLoaderResourceScanner(classLoader) }
    private val env by lazy {
        logger.info("Computing the mappings: SRG -> Notch -> CraftBukkit")
        val minecraftVersion = Bukkit.getVersion().split("(MC:", ")")[1].trim()
        val bukkitVersion = Bukkit.getBukkitVersion()
        val packageVersion = Bukkit.getServer().javaClass.`package`.name.substringAfterLast('.')
        logger.info("Minecraft: $minecraftVersion Bukkit: $bukkitVersion Package: $packageVersion")

        ClassRemapEnvironment(this)
    }

    private var sourceClassLoader: ClassLoader? = null

    override fun supplyField(classStructure: ClassStructure, identifier: FieldIdentifier) = bukkitScanner.supplyField(classStructure, identifier)
    override fun supplyMethod(classStructure: ClassStructure, identifier: MethodIdentifier) = bukkitScanner.supplyMethod(classStructure, identifier)

    override fun supplyClass(identifier: ClassIdentifier): ClassStructure? {
        try {
            return bukkitScanner.supplyClass(identifier)
        }
        catch (e: ClassNotFoundException) {
            sourceClassLoader?.let {
                try {
                    it.getResourceAsStream(identifier.fullName+".class")?.use { input ->
                        return bukkitScanner.supplyClass(identifier, input)
                    }

                    throw ClassNotFoundException(identifier.fullName)
                }
                catch (e2: ClassNotFoundException) {
                    e2.addSuppressed(e)
                    throw e2
                }
            } ?: throw e
        }
    }

    fun initialize(classLoader: ClassLoader, logger: Logger) {
        this.classLoader = classLoader
        this.logger = logger
    }

    override fun invoke(source: ClassLoader, name: String, input: InputStream): ByteArray {
        val reader = ClassReader(input)
        val writter = ClassWriter(0)

        synchronized(this) {
            sourceClassLoader = source
            val adapter = ClassRemapper(writter, env)
            reader.accept(adapter, 0)
            sourceClassLoader = null
        }

        return writter.toByteArray()
    }
}
