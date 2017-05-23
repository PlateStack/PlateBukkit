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

package org.platestack.bukkit.message

import com.google.gson.JsonObject
import org.platestack.api.message.Language
import org.platestack.api.message.Text
import org.platestack.api.message.Translator

class BukkitTranslator: Translator {
    override fun toJson(text: Text, language: Language): String {
        val root = JsonObject()
        text.style.apply {
            color?.let {
                root.addProperty("color", it.id)
            }

        }

        return root.toString()
    }
}