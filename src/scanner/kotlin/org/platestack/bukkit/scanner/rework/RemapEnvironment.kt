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

import org.platestack.bukkit.scanner.ClassToken
import org.platestack.bukkit.scanner.PackageToken
import org.platestack.bukkit.scanner.structure.ClassIdentifier
import org.platestack.bukkit.scanner.structure.ClassStructure
import org.platestack.bukkit.scanner.structure.PackageMove

class RemapEnvironment {
    val packages: Map<PackageToken, PackageMove> = hashMapOf()
    val classes: Map<ClassIdentifier, ClassStructure> = hashMapOf()

    operator fun get(`package`: PackageToken) = packages[`package`]

    operator fun get(`class`: ClassToken) = classes[`class`]

    operator fun set(`class`: ClassToken, structure: ClassStructure) {
        (classes as MutableMap)[`class`] = structure
    }

    operator fun set(`package`: PackageToken, structure: PackageMove) {
        (packages as MutableMap)[`package`] = structure
    }
}
