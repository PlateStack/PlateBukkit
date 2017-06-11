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
import java.util.stream.Stream

typealias PackageToken = PackageIdentifier
typealias PackageMapping = LinkedHashMap<PackageToken, PackageToken>

typealias ClassToken = ClassIdentifier
typealias ClassMapping = LinkedHashMap<ClassToken, ClassToken>

typealias MethodToken = Pair<ClassIdentifier, MethodIdentifier>
typealias MethodMapping = LinkedHashMap<MethodToken, MethodToken>

typealias FieldToken = Pair<ClassIdentifier, FieldIdentifier>
typealias FieldMapping = LinkedHashMap<FieldToken, FieldToken>

fun Stream<String>.filterComments() = map(String::trim).filter(String::isNotBlank).filter { !it.startsWith('#') }!!

internal typealias SimpleEnv = LinkedHashMap<ClassIdentifier, ClassStructure>
