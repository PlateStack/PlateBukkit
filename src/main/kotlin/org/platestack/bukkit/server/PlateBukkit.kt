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

package org.platestack.bukkit.server

import com.google.gson.JsonObject
import mu.KotlinLogging
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.platestack.api.message.Text
import org.platestack.api.minecraft.Minecraft
import org.platestack.api.minecraft.MinecraftServer
import org.platestack.api.plugin.PlateMetadata
import org.platestack.api.plugin.PlateNamespace
import org.platestack.api.plugin.version.MavenArtifact
import org.platestack.api.plugin.version.Version
import org.platestack.api.server.PlateServer
import org.platestack.api.server.PlateStack
import org.platestack.api.server.PlatformNamespace
import org.platestack.api.server.internal.InternalAccessor
import org.platestack.bukkit.message.BukkitTranslator
import org.platestack.bukkit.plugin.BukkitNamespace
import org.platestack.common.plugin.loader.CommonLoader
import org.platestack.libraryloader.ivy.LibraryResolver
import org.platestack.structure.immutable.immutableSetOf
import java.nio.file.Paths

class PlateBukkit(private val actualPlugin: JavaPlugin): PlateServer, org.bukkit.plugin.Plugin by actualPlugin {
    override val platformName: String get() = "bukkit"
    override val platform = PlatformNamespace("bukkit" to Version.parse(Bukkit.getBukkitVersion()))
    override lateinit var translator: BukkitTranslator

    override fun onEnable() {
        logger.info("PlateBukkit has been loaded successfully, setting up PlateStack...")

        val loader = CommonLoader(KotlinLogging.logger("PlateStack"))
        PlateStack = this
        PlateNamespace.loader = loader
        translator = BukkitTranslator()
        Minecraft.server = object : MinecraftServer {
            override val version = Version.parse(Bukkit.getVersion())
        }

        loader.setAPI(PlateMetadata(
                "platestack",
                "PlateStack Bukkit",
                Version(0,1,0,"SNAPSHOT"),
                "1.8",
                immutableSetOf(),
                LibraryResolver.readArtifacts(PlateServer::class.java.getResourceAsStream("libraries.list")).map {
                    MavenArtifact(it.group, it.artifact, it.version)
                }
        ))

        logger.info("Scanning plate plugins...")

        val plugins = loader.findPlugins(Paths.get("platestack"), true) {
            it.fileName.toString().let { it.endsWith(".jar", true) || it.endsWith(".plate", true) || it.endsWith(".car", true) }
        }.onEach {
            logger.info("Found: $it")
        }.map { it.toUri().toURL() }.toSet()

        logger.info { "Attempting to load all ${plugins.size} plate plugins..." }
        loader.load(plugins)
    }

    override fun getNamespace(id: String) = super.getNamespace(id) ?: when(id) {
        "bukkit" -> BukkitNamespace
        else -> null
    }

    override val internal = object : InternalAccessor {
        override fun toJson(text: Text) = translator.run { text.toJson(JsonObject()) }
    }
}
