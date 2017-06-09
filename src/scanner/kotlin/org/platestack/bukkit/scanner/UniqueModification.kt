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

import kotlin.reflect.KProperty

//TODO This is a copy of the one provided by plate-api, find a way to use that instead
internal class UniqueModification<V: Any> {

    private var field: V? = null

    operator fun getValue(thisRef: Any?, property: KProperty<*>): V {
        return field ?: throw UninitializedPropertyAccessException("No value has been set to ${property.name} yet")
    }
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
        if(field != null)
            error("The value can be modified only one time.")

        this.field = value
    }
}