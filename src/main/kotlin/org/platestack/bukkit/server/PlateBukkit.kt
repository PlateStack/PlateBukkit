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

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.platestack.api.message.Translator
import org.platestack.api.minecraft.Minecraft
import org.platestack.api.minecraft.MinecraftServer
import org.platestack.api.plugin.PlatePlugin
import org.platestack.api.plugin.version.Version
import org.platestack.api.server.PlateServer
import org.platestack.api.server.PlateStack
import org.platestack.api.server.PlatformNamespace
import org.platestack.api.server.internal.InternalAccessor
import org.platestack.bukkit.plugin.BukkitNamespace
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PlateBukkit: PlateServer, JavaPlugin() {
    override val platformName: String get() = "bukkit"
    override val platform = PlatformNamespace("bukkit" to Version.parse(Bukkit.getBukkitVersion()))
    override lateinit var translator: Translator

    override fun onEnable() {
        PlateStack = this
        Minecraft.server = object : MinecraftServer {
            override val version = Version.parse(Bukkit.getVersion())
        }
    }

    override fun getNamespace(id: String) = super.getNamespace(id) ?: when(id) {
        "bukkit" -> BukkitNamespace
        else -> null
    }

    override val internal = object : InternalAccessor {
        override fun createLogger(plugin: PlatePlugin): Logger {
            return LoggerFactory.getLogger(plugin.javaClass)
        }
    }
}
