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

package org.platestack.bukkit.server;

import org.bukkit.plugin.java.JavaPlugin;
import org.platestack.libraryloader.ivy.LibraryResolver;
import org.platestack.libraryloader.ivy.MavenArtifact;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class InitialResolver
{
    private InitialResolver()
    {}

    public static List<File> resolve(JavaPlugin plugin, List<InputStream> lists) throws IOException, ParseException
    {
        try
        {
            LibraryResolver.setUserDir(new File(plugin.getDataFolder(), "libs").getAbsoluteFile());
            final Set<MavenArtifact> dependencies = new HashSet<>();
            for (InputStream listPath : lists)
                dependencies.addAll(LibraryResolver.readArtifacts(listPath));

            return LibraryResolver.getInstance().resolve(
                    new MavenArtifact("org.platestack", "plate-bukkit", plugin.getDescription().getVersion()),
                    dependencies
            );
        }
        finally
        {
            lists.forEach(in-> {
                try
                {
                    in.close();
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                }
            });
        }
    }
}
