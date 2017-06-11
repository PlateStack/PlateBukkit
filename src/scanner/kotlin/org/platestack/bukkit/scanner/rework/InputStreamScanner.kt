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

import org.objectweb.asm.ClassReader
import org.platestack.bukkit.scanner.structure.*
import java.io.InputStream

interface InputStreamScanner : ColdScanner {
    fun getColdStream(classId: ClassIdentifier): InputStream?

    override fun scan(environment: RemapEnvironment, classId: ClassIdentifier, fullParents: Boolean): ClassStructure? {
        return getColdStream(classId)?.use {
            scan(environment, classId, fullParents, ClassReader(it))
        }
    }

    override fun fullScan(environment: RemapEnvironment, classId: ClassIdentifier): ClassStructure? {
        return getColdStream(classId)?.use {
            fullScan(environment, classId, ClassReader(it))
        }
    }

    override fun scan(environment: RemapEnvironment, classId: ClassIdentifier, fieldId: FieldIdentifier): FieldStructure? {
        return getColdStream(classId)?.use {
            scan(environment, classId, fieldId, ClassReader(it))
        }
    }

    override fun scan(environment: RemapEnvironment, classId: ClassIdentifier, methodId: MethodIdentifier): MethodStructure? {
        return getColdStream(classId)?.use {
            scan(environment, classId, methodId, ClassReader(it))
        }
    }
}
