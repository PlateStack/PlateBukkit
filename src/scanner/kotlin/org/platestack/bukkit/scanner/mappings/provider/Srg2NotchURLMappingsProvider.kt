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

import org.platestack.bukkit.scanner.*
import org.platestack.bukkit.scanner.mappings.Mappings
import org.platestack.bukkit.scanner.structure.ClassIdentifier
import org.platestack.bukkit.scanner.structure.FieldIdentifier
import org.platestack.bukkit.scanner.structure.MethodIdentifier
import org.platestack.bukkit.scanner.structure.PackageIdentifier
import java.io.Reader
import java.net.URL
import java.util.logging.Logger
import kotlin.streams.asSequence
import kotlin.streams.toList

class Srg2NotchURLMappingsProvider(val base: URL, val logger: Logger) : MappingsProvider {
    override fun invoke(minecraftVersion: String, bukkitVersion: String, packageVersion: String): Mappings {
        val root = URL(base, "forge/")
        return load(URL(root, "$minecraftVersion/")).asSequence().map { it.inverse() }.reduce { acc, mappings ->
            acc + mappings
        }.also {
            logger.info("The final mappings have ${it.classes.size} classes, ${it.fields.size} fields and ${it.methods.size} methods")
        }
    }

    private fun load(dir: URL): List<Mappings> {
        return URL(dir, "list.txt").openStream().use { it.reader().buffered().lines().filterComments().toList() }.map { subdir ->
            URL(dir, "$subdir/notch-srg.srg").openStream().use {
                logger.info { "Reading SRG mappings: $subdir/notch-srg.srg" }
                parse(it.reader())
            }
        }
    }

    private fun parse(reader: Reader): Mappings {
        val groups = reader.buffered().lines().filterComments().asSequence()
                .map { it.split(' ', limit = 5) }
                .groupBy { it.first() }

        val mappings = Mappings()
        val packages = mutableMapOf<String, PackageToken>()

        fun String.packageToken(): PackageToken {
            val noDot = if(this == ".") "" else this
            return packages.computeIfAbsent(noDot) { _ -> PackageIdentifier(noDot) }
        }

        groups["PK:"]!!.associate {
            it[1].packageToken() to it[2].packageToken()
        }.let {
            logger.info { "Loaded ${it.size} fallback package mappings" }
            mappings.packages += it
        }

        val classes = mutableMapOf<String, ClassIdentifier>()
        fun String.classToken(): ClassToken = classes.computeIfAbsent(this) { _ -> ClassIdentifier(this) }
        groups["CL:"]!!.associate {
            it[1].classToken() to it[2].classToken()
        }.let {
            logger.info { "Loaded ${it.size} class name mappings" }
            mappings.classes += it
        }

        fun String.fieldToken(): FieldToken = substringBeforeLast('/').classToken() to FieldIdentifier(substringAfterLast('/'))
        groups["FD:"]!!.associate {
            it[1].fieldToken() to it[2].fieldToken()
        }.let {
            logger.info { "Loaded ${it.size} field name mappings" }
            mappings.fields += it
        }

        infix fun String.method(desc: String): MethodToken = substringBeforeLast('/').classToken() to MethodIdentifier(substringAfterLast('/'), desc)
        groups["MD:"]!!.associate {
            (it[1] method it[2]) to (it[3] method it[4])
        }.let {
            logger.info { "Loaded ${it.size} method name mappings" }
            mappings.methods += it
        }

        return mappings
    }
}