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

import org.platestack.bukkit.scanner.structure.*
import java.io.InputStream
import java.lang.ref.WeakReference

class HybridScanner(classLoader: ClassLoader, private val coldStream: (ClassIdentifier) -> InputStream?) : HotScanner(classLoader), InputStreamScanner {
    constructor(classLoader: ClassLoader): this(classLoader, ClassResourceLoader(classLoader).let {
        {id: ClassIdentifier -> it.getResourceAsStream(id.fullName.replace('.','/')+".class") }
    })

    private val findLoadedClassMethod = ClassLoader::class.java.getDeclaredMethod("findLoadedClass", String::class.java).also { it.isAccessible = true }
    private val knownLoadedClasses = hashMapOf<String, WeakReference<Class<*>>>()

    fun ClassLoader.findLoadedClass(name: String): Class<*>? {
        return findLoadedClassMethod(this, name) as Class<*>? ?: parent?.findLoadedClass(name)
    }

    fun isClassLoaded(name: String): Boolean {
        knownLoadedClasses[name]?.let { ref->
            if(ref.get() == null) {
                knownLoadedClasses.remove(name, ref)
            }
            else {
                return true
            }
        }

        return classLoader.findLoadedClass(name)?.also { knownLoadedClasses[name] = WeakReference(it) } != null
    }

    override fun getColdStream(classId: ClassIdentifier) = coldStream(classId)

    override fun scan(environment: RemapEnvironment, classId: ClassIdentifier, fullParents: Boolean): ClassStructure? {
        if(isClassLoaded(classId.hotName))
            return super<HotScanner>.scan(environment, classId, fullParents)
        else
            return super<InputStreamScanner>.scan(environment, classId, fullParents)
    }

    override fun fullScan(environment: RemapEnvironment, classId: ClassIdentifier): ClassStructure? {
        if(isClassLoaded(classId.hotName))
            return super<HotScanner>.fullScan(environment, classId)
        else
            return super<InputStreamScanner>.fullScan(environment, classId)
    }

    override fun scan(environment: RemapEnvironment, classId: ClassIdentifier, methodId: MethodIdentifier): MethodStructure? {
        if(isClassLoaded(classId.hotName))
            return super<HotScanner>.scan(environment, classId, methodId)
        else
            return super<InputStreamScanner>.scan(environment, classId, methodId)
    }

    override fun scan(environment: RemapEnvironment, classId: ClassIdentifier, fieldId: FieldIdentifier): FieldStructure? {
        if(isClassLoaded(classId.hotName))
            return super<HotScanner>.scan(environment, classId, fieldId)
        else
            return super<InputStreamScanner>.scan(environment, classId, fieldId)
    }
}
