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

package org.platestack.bukkit.scanner.transform

import org.platestack.bukkit.boot.BootReflectionTarget
import org.platestack.bukkit.scanner.rework.RemapEnvironment
import java.net.URL
import java.net.URLClassLoader
import org.platestack.bukkit.boot.CoreDependenciesClassLoader as BootCoreDepsClassLoader
import org.platestack.bukkit.boot.ScannerClassLoader as BootScannerClassLoader

interface RemapEnvironmentHost {
    val environment: RemapEnvironment
}

@BootReflectionTarget
class MainClassLoader(urls: Array<URL>, parent: BootCoreDepsClassLoader): URLClassLoader(urls, parent), RemapEnvironmentHost {
    val coreDeps get() = parent as BootCoreDepsClassLoader
    override val environment get() = (parent.parent.parent as BootScannerClassLoader).environment as RemapEnvironment
}

@BootReflectionTarget
class MainTransformerClassLoader(parent: MainClassLoader): RemapClassLoader(parent, parent.environment) {
    val main get() = parent as MainClassLoader
}
