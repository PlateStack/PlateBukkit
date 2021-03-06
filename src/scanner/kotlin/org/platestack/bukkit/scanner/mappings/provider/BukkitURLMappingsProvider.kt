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

package org.platestack.bukkit.scanner.mappings.provider

import org.platestack.bukkit.scanner.MappingsProvider
import org.platestack.bukkit.scanner.filterComments
import org.platestack.bukkit.scanner.mappings.Mappings
import org.platestack.bukkit.scanner.structure.*
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL
import java.util.logging.Logger
import kotlin.streams.asSequence
import kotlin.streams.toList

class BukkitURLMappingsProvider(val base: URL, val logger: Logger, val checkPackageVersion: Boolean = true) : MappingsProvider {
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
        mappings.packages[PackageIdentifier("")] = PackageIdentifier("net/minecraft/server/$packageVersion")

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

        val emptyPackage = PackageIdentifier(null, "")

        fun ClassIdentifier.toNoPackage(): ClassIdentifier {
            if(`package` == emptyPackage)
                return this
            else
                return ClassIdentifier(emptyPackage, parent?.toNoPackage(), className)
        }

        /**
         * NiceName -> net/minecraft/server/v5/NiceName
         */
        val fromNoPackage = Mappings().also {
            it.classes += mappings.classes.map { it.value.let { it.toNoPackage() to it } }
        }

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
                                    if (it.fullName.startsWith("net/minecraft/server/"))
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
            val classId = remapOrRegisterNoPackage(ClassIdentifier(className))

            (inverse.classes[classId]!! to FieldIdentifier(fromFieldName)) to (classId to FieldIdentifier(toFieldName))
        }.toMap().let {
            logger.info { "Loaded ${it.size} field name mappings" }
            mappings.fields += it
        }

        methodList.associate { (className, fromMethodName, noPackageSignature, toMethodName) ->
            val classId = remapOrRegisterNoPackage(ClassIdentifier(className)) //fromNoPackage.classes[ClassIdentifier(className)] ?: error("Unknown class while mapping $line")
            val methodSignature = MethodDescriptor(noPackageSignature) { from->
                remapOrRegisterNoPackage(from).toChange()
            }

            val newMethod = MethodIdentifier(toMethodName, methodSignature.to)

            val inverseSignature = MethodDescriptor(methodSignature.to) { from->
                inverse.classes[from]!!.toChange()
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