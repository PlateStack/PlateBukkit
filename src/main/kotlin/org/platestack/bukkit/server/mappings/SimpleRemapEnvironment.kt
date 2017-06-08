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

import org.objectweb.asm.commons.Remapper

class SimpleRemapEnvironment(mappings: Mappings) : Remapper() {
    val env = mappings.toKnownStructure()

    override fun map(typeName: String): String {
        return env[ClassIdentifier(typeName)]?.`class`?.to?.fullName ?: typeName
    }

    override fun mapFieldName(owner: String, name: String, desc: String): String {
        return env[ClassIdentifier(owner)]?.fields?.get(FieldIdentifier(name))?.field?.name?.reverse ?: name
    }

    override fun mapMethodName(owner: String, name: String, desc: String): String {
        return env[ClassIdentifier(owner)]?.methods?.get(MethodIdentifier(name, desc))?.method?.name?.reverse ?: name
    }
}
