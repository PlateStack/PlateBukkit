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
import org.platestack.api.structure.ReflectionTarget
import org.platestack.bukkit.boot.PlateStackLoader
import org.platestack.bukkit.scanner.ClassLoaderResourceScanner
import org.platestack.bukkit.scanner.ClassRemapEnvironment
import org.platestack.bukkit.scanner.MappingsProvider
import org.platestack.bukkit.scanner.mappings.Mappings
import org.platestack.bukkit.scanner.mappings.provider.BukkitURLMappingsProvider
import org.platestack.bukkit.scanner.mappings.provider.Srg2NotchURLMappingsProvider
import org.platestack.bukkit.scanner.structure.ClassIdentifier
import org.platestack.bukkit.scanner.structure.ClassStructure
import org.platestack.common.plugin.loader.Transformer
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.logging.Level
import java.util.logging.Logger

@ReflectionTarget(PlateStackLoader::class)
object BukkitTransformer: Transformer {
    var mappingProvider = MappingsProvider { _, _, _ -> Mappings() }

    private lateinit var classLoader: ClassLoader
    private var logger by UniqueModification<Logger>()
    private val env by lazy {
        logger.info("Computing the mappings: SRG -> Notch -> CraftBukkit")
        val minecraftVersion = Bukkit.getVersion().split("(MC:", ")")[1].trim()
        val bukkitVersion = Bukkit.getBukkitVersion()
        val packageVersion = Bukkit.getServer().javaClass.`package`.name.substringAfterLast('.')
        logger.info("Minecraft: $minecraftVersion Bukkit: $bukkitVersion Package: $packageVersion")
        //TODO remove this line
        val repository = File("D:\\_InteliJ\\org.platestack\\Mappings").toURI().toURL()
        val bukkitMappings = BukkitURLMappingsProvider(repository, scanner, logger)
        val srgMappings = Srg2NotchURLMappingsProvider(repository, logger)
        mappingProvider = MappingsProvider { a, b, c ->
            (srgMappings(a, b, c) % bukkitMappings(a, b, c)).also { mappings ->
                Files.newBufferedWriter(Paths.get("srg-craftbukkit.srg")).use { mappings.exportSRG(it) }
            }
        }

        ClassRemapEnvironment(scanner).apply {
            apply(mappingProvider(minecraftVersion, bukkitVersion, packageVersion))
        }
    }

    private var sourceClassLoader: ClassLoader? = null

    private val scanner by lazy { object : ClassLoaderResourceScanner(classLoader) {
        private fun supplyClassNormally(identifier: ClassIdentifier): ClassStructure? {
            try {
                return super.supplyClass(identifier)
            }
            catch (e: ClassNotFoundException) {
                sourceClassLoader?.let {
                    try {
                        it.getResourceAsStream(identifier.fullName+".class")?.use { input ->
                            return supplyClass(identifier, input)
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

        override fun supplyClass(identifier: ClassIdentifier): ClassStructure? {
            try {
                try {
                    return supplyClassNormally(identifier)
                }
                catch(e: StackOverflowError) {
                    logger.log(Level.SEVERE, "Error while creating structure: $identifier", e)
                    throw Error(e)
                }
            }
            catch (e: ClassNotFoundException) {
                //logger.fine("Could not find the class $identifier! Using a dummy class!")
                //return ClassStructure(identifier.toChange(classSupplier = this::supplyClassChange), null, emptyList())
                return null
            }
        }
    } }

    @ReflectionTarget(PlateStackLoader::class)
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
