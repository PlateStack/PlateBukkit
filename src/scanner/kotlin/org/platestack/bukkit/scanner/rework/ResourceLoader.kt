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

package org.platestack.bukkit.scanner.rework

import java.io.InputStream
import java.net.URL
import java.util.*

class ClassResourceLoader(private val classLoader: ClassLoader) : ResourceLoader {
    override fun getResource(name: String): URL? = classLoader.getResource(name)
    override fun getResources(name: String): Enumeration<URL> = classLoader.getResources(name)
    override fun getResourceAsStream(name: String): InputStream? = classLoader.getResourceAsStream(name)
}

interface ResourceLoader {
    fun getResource(name: String): URL?
    fun getResourceAsStream(name: String): InputStream?
    fun getResources(name: String): Enumeration<URL>
}
