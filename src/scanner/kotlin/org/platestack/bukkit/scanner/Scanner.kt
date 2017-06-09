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

package org.platestack.bukkit.scanner

import org.platestack.bukkit.scanner.structure.*

interface Scanner {
    fun supplyClassChange(identifier: ClassIdentifier): ClassChange {
        return supplyClass(identifier)?.`class` ?: ClassStructure(identifier.toChange(classSupplier = this::supplyClassChange), null, emptyList()).`class` //throw ClassNotFoundException(identifier.fullName)
    }

    fun supplyClass(identifier: ClassIdentifier): ClassStructure? = null
    fun supplyField(classStructure: ClassStructure, identifier: FieldIdentifier): FieldStructure? = null
    fun supplyMethod(classStructure: ClassStructure, identifier: MethodIdentifier): MethodStructure? = null
}