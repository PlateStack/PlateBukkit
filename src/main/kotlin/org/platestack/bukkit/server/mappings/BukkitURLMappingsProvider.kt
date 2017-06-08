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

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL
import java.util.logging.Logger
import kotlin.streams.asSequence
import kotlin.streams.toList

class BukkitURLMappingsProvider(val base: URL, val scanner: Scanner, val logger: Logger, val checkPackageVersion: Boolean = true) : MappingsProvider {
    fun InputStream.lines(charset: String = "UTF-8") = BufferedReader(InputStreamReader(this, charset)).lines()!!
    fun InputStream.readLine(charset: String = "UTF-8") = BufferedReader(InputStreamReader(this, charset)).readLine()!!

    override fun invoke(minecraftVersion: String, bukkitVersion: String, packageVersion: String): Mappings {
        logger.info { "Checking remote bukkit package version. Expecting: $packageVersion" }
        val dir = URL(base, "craftbukkit/$minecraftVersion/")
        val remotePackageVersion = URL(dir, "version.txt").openStream().use { 'v'+it.readLine() }

        if(remotePackageVersion != packageVersion) {
            if(checkPackageVersion)
                throw UnsupportedOperationException("Trying to get mappings for Minecraft: $minecraftVersion Bukkit: $bukkitVersion Package: $packageVersion but found mappings for package: $remotePackageVersion")
            else
                logger.warning("Using mappings for $remotePackageVersion instead of $packageVersion! Minecraft: $minecraftVersion Bukkit: $bukkitVersion")
        }

        logger.info { "Checking remote package name." }
        URL(dir, "package.srg").openStream().use { it.lines().filterComments().toList() }.let { packages ->
            if(packages.size != 1 || packages.first() != "./ net/minecraft/server/") {
                throw UnsupportedOperationException("Expected only 1 package to be ./ -> net/minecraft/server/ but got: \n${packages.joinToString("\n")}")
            }
        }

        /**
         * aaaa -> net/minecraft/server/v5/NiceName
         */
        val mappings = Mappings()

        logger.info { "Loading bukkit class name definitions from remote" }
        URL(dir, "bukkit-$minecraftVersion-cl.csrg").openStream().use {
            it.lines().filterComments()
                    .map { it.split(' ', limit = 2) }
                    .map { ClassIdentifier(it[0]) to ClassIdentifier("net/minecraft/server/$packageVersion/${it[1]}") }
                    .asSequence().toMap()
        }.let {
            logger.info { "Loaded ${it.size} class name mappings" }
            mappings.classes += it
        }

        val (fieldList, methodList) = URL(dir, "bukkit-$minecraftVersion-members.csrg").openStream().use {
            it.lines().filterComments()
                    .map { it.split(' ', limit = 4) }
                    .asSequence().partition { it.size == 3 }
        }

        /**
         * NiceName -> net/minecraft/server/v5/NiceName
         */
        val emptyPackage = PackageIdentifier(null, "")
        val fromNoPackage = Mappings().also {
            it.classes += mappings.classes.map { it.value.let { ClassIdentifier(emptyPackage, it.parent, it.className) } to it.value }
        }

        fun SignatureType.isolated() =
                if(type == null) this
                else copy(type = type.deepCopy())

        /**
         * net/minecraft/server/v5/NiceName -> aaaa
         */
        val inverse = mappings.inverse()

        val nms = PackageIdentifier("net/minecraft/server/$packageVersion")
        fun remapOrRegisterNoPackage(classId: ClassIdentifier): ClassIdentifier {
            return fromNoPackage.classes[classId] ?: classId.let {
                val from = it
                val to =
                        ClassIdentifier(
                                classId.`package`.takeIf { it.prefix.isNotBlank() && it.fullName != "net/minecraft/server" }?.let {
                                    if(it.fullName.startsWith("net/minecraft/server/"))
                                        PackageIdentifier(it.fullName.replace("net/minecraft/server/", nms.fullName))
                                    else
                                        it
                                } ?: nms
                                ,
                                classId.parent?.let { remapOrRegisterNoPackage(it) },
                                classId.className
                        )

                if(from != to)
                    logger.warning("Found omitted class: $it , remmaping to: $to")

                mappings.classes[from] = to
                inverse.classes[to] = from
                fromNoPackage.classes[from] = to
                to
            }
        }

        fieldList.asSequence().filterNot { (c, from, to) ->
            ('(' in from || '(' in to).also {
                if(it) logger.severe("Found a field with '(' char in it!: $c $from $to")
            }
        }.asSequence().mapNotNull { (className, fromFieldName, toFieldName) ->
            val line = "$className $fromFieldName $toFieldName"
            val classId = remapOrRegisterNoPackage(ClassIdentifier(className))

            // Technically, we don't need to verify the class anymore since the field signature have been dropped from the identifier
            val classStructure = scanner.supplyClass(classId) ?: error("The structure scanned couldn't find $classId to map $line")
            val targetField = classStructure.fields.keys.find { it.name == toFieldName }
                    ?: "Field $toFieldName not found while mapping $line".let { logger.severe(it); return@mapNotNull null }
            val from = FieldIdentifier(fromFieldName)

            (inverse.classes[classId]!! to from) to (classId to targetField)
        }.toMap().let {
            logger.info { "Loaded ${it.size} field name mappings" }
            mappings.fields += it
        }

        methodList.associate { (className, fromMethodName, noPackageSignature, toMethodName) ->
            val line = "$className $fromMethodName $noPackageSignature $toMethodName"
            val classId = remapOrRegisterNoPackage(ClassIdentifier(className)) //fromNoPackage.classes[ClassIdentifier(className)] ?: error("Unknown class while mapping $line")
            val methodSignature = MethodSignature(noPackageSignature) {
                scanner.supplyClass(
                        remapOrRegisterNoPackage(it)
                )?.`class` ?: error("No class found to remap the signature of $className$noPackageSignature . Mapping: $line")
            }

            val newMethod = MethodIdentifier(toMethodName, methodSignature.to)

            val inverseSignature = methodSignature.run {
                MethodSignature(returnType.isolated(), parameterTypes.map { it.isolated() }).apply {
                    apply(inverse)
                }
            }

            val oldMethod = MethodIdentifier(fromMethodName, inverseSignature.to)


            (inverse.classes[classId]!! to oldMethod) to (classId to newMethod)
        }.let {
            logger.info { "Loaded ${it.size} method name mappings" }
            mappings.methods += it
        }

        return mappings
    }
}