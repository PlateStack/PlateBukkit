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
    public static List<File> resolveKotlin(final JavaPlugin plugin, final List<InputStream> lists) throws IOException, ParseException
    {
        final Set<MavenArtifact> artifacts = readArtifacts(lists);
        artifacts.removeIf(it-> !it.getGroup().equals("org.jetbrains.kotlin"));
        return resolveArtifacts(plugin, "plate-kotlin", artifacts);
    }

    public static Set<MavenArtifact> readArtifacts(final List<InputStream> lists) throws IOException
    {
        final Set<MavenArtifact> dependencies;
        try
        {
            dependencies = new HashSet<>();
            for (InputStream listPath : lists)
                dependencies.addAll(LibraryResolver.readArtifacts(listPath));
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

        return dependencies;
    }

    public static List<File> resolve(final JavaPlugin plugin, final String requester, final List<InputStream> lists) throws IOException, ParseException
    {
        return resolveArtifacts(plugin, requester, readArtifacts(lists));
    }

    public static List<File> resolveArtifacts(final JavaPlugin plugin, String requester, final Set<MavenArtifact> dependencies) throws IOException, ParseException
    {
        LibraryResolver.setUserDir(new File(plugin.getDataFolder(), "libs").getAbsoluteFile());

        return LibraryResolver.getInstance().resolve(
                new MavenArtifact("org.platestack", requester, plugin.getDescription().getVersion()),
                dependencies
        );
    }

    private InitialResolver() {}
}
