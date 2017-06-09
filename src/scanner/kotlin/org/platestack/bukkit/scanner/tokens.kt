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

import org.platestack.bukkit.scanner.structure.ClassIdentifier
import org.platestack.bukkit.scanner.structure.ClassStructure
import org.platestack.bukkit.scanner.structure.FieldIdentifier
import org.platestack.bukkit.scanner.structure.MethodIdentifier
import java.util.stream.Stream

typealias ClassToken = ClassIdentifier
typealias ClassMapping = HashMap<ClassToken, ClassToken>

typealias MethodToken = Pair<ClassIdentifier, MethodIdentifier>
typealias MethodMapping = HashMap<MethodToken, MethodToken>

typealias FieldToken = Pair<ClassIdentifier, FieldIdentifier>
typealias FieldMapping = HashMap<FieldToken, FieldToken>

fun Stream<String>.filterComments() = map(String::trim).filter(String::isNotBlank).filter { !it.startsWith('#') }!!

internal typealias SimpleEnv = HashMap<ClassIdentifier, ClassStructure>
