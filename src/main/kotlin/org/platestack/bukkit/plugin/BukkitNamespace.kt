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

package org.platestack.bukkit.plugin

import org.bukkit.Bukkit
import org.platestack.api.plugin.PluginNamespace

object BukkitNamespace: PluginNamespace {
    private val objects = mutableMapOf<String, BukkitPlugin>()
    override val id: String get() = "bukkit"

    override fun get(pluginId: String): BukkitPlugin? {
        synchronized(objects) {
            val current = Bukkit.getPluginManager().getPlugin(pluginId)
            val wrapper = objects[pluginId]
            if (current == null) {
                if(wrapper != null)
                    objects.remove(pluginId, wrapper)

                return null
            }

            val cached = wrapper?.bukkit?.get()
            if(cached == current) {
                return wrapper
            }

            val new = BukkitPlugin(current)
            objects[pluginId] = new
            return new
        }
    }
}
