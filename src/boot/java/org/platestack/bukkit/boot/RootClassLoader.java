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

package org.platestack.bukkit.boot;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.stream.Stream;

public class RootClassLoader extends URLClassLoader implements EnvironmentHost
{
    public RootClassLoader(URL[] modules, URL[] urls, ScannerClassLoader parent)
    {
        super(Stream.concat(Arrays.stream(modules), Arrays.stream(urls)).toArray(URL[]::new), parent);
    }

    @Override
    public Object getEnvironment()
    {
        return ((ScannerClassLoader) getParent()).getEnvironment();
    }
}
