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
@file:JvmName("Boot")
package org.platestack.bukkit.scanner

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.platestack.bukkit.boot.BootReflectionTarget
import org.platestack.bukkit.boot.RootClassLoader
import org.platestack.bukkit.boot.ScannerClassLoader
import org.platestack.bukkit.scanner.mappings.provider.BukkitURLMappingsProvider
import org.platestack.bukkit.scanner.mappings.provider.Srg2NotchURLMappingsProvider
import org.platestack.bukkit.scanner.rework.HybridScanner
import org.platestack.bukkit.scanner.rework.RemapEnvironment
import org.platestack.bukkit.scanner.structure.ClassIdentifier
import org.platestack.bukkit.scanner.structure.PackageIdentifier
import org.platestack.bukkit.scanner.structure.PackageMove
import java.io.File
import java.net.URL

@BootReflectionTarget
private fun boot(plugin: JavaPlugin, root: RootClassLoader) {
    val environment = RemapEnvironment()
    (root.parent as ScannerClassLoader).environment = environment

    //TODO Change the default repository
    val repository = URL(plugin.config.getString("remap.repository", File("D:\\_InteliJ\\org.platestack\\Mappings").toURI().toURL().toString()))

    val minecraftVersion = Bukkit.getVersion().split("(MC:", ")")[1].trim()
    val bukkitVersion = Bukkit.getBukkitVersion()
    val packageVersion = Bukkit.getServer().javaClass.`package`.name.substringAfterLast('.')

    val srgProvider = Srg2NotchURLMappingsProvider(repository, plugin.logger)
    val srg2notchMappings = srgProvider(minecraftVersion, bukkitVersion, packageVersion)

    val bukkitProvider = BukkitURLMappingsProvider(repository, plugin.logger, true)
    val notch2craftMappings = bukkitProvider(minecraftVersion, bukkitVersion, packageVersion)

    val craft2notch = notch2craftMappings.inverse().toFullStructure(HybridScanner(root))
    val normalNMS = PackageIdentifier("net/minecraft/server").toChange()
    sequenceOf("MinecraftServer", "ServerStatisticManager")
            .map { ClassIdentifier("net/minecraft/server/$packageVersion/$it") }
            .map { checkNotNull(craft2notch[it]) { "Unable to fix the $it mapping." } }
            .forEach {
                it.`class`.`package`.new = normalNMS
            }

    craft2notch[normalNMS.from] = PackageMove(normalNMS)

    requireNotNull(craft2notch[ClassIdentifier("net/minecraft/server/$packageVersion/MinecraftServer")])
    craft2notch.export(File(plugin.dataFolder, "mappings/craft2notch"))

    val notch2srg = craft2notch.inverse().also { it.applyToNative(srg2notchMappings.inverse()) }
    notch2srg.export(File(plugin.dataFolder, "mappings/notch2srg"))

    val srg2craft = notch2srg.inverse().also { it.applyToForeign(notch2craftMappings) }
    srg2craft.export(File(plugin.dataFolder, "mappings/srg2craf"))

    environment.apply {
        (packages as MutableMap) += srg2craft.packages
        (classes as MutableMap) += srg2craft.classes
    }
}
