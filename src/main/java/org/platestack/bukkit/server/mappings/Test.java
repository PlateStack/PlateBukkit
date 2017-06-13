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

package org.platestack.bukkit.server.mappings;

import org.platestack.bukkit.scanner.mappings.Mappings;
import org.platestack.bukkit.scanner.mappings.provider.BukkitURLMappingsProvider;
import org.platestack.bukkit.scanner.mappings.provider.Srg2NotchURLMappingsProvider;
import org.platestack.bukkit.scanner.rework.HybridScanner;
import org.platestack.bukkit.scanner.rework.RemapEnvironment;
import org.platestack.bukkit.scanner.structure.ClassIdentifier;
import org.platestack.bukkit.scanner.structure.ClassStructure;
import org.platestack.bukkit.scanner.structure.FieldIdentifier;
import org.platestack.bukkit.scanner.structure.FieldStructure;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;

class Test
{
    static void showClass(Class<?> c) {
        System.out.println("getName(): " + c.getName());
        System.out.println("getCanonicalName(): " + c.getCanonicalName());
        System.out.println("getSimpleName(): " + c.getSimpleName());
        System.out.println("toString(): " + c.toString());
        System.out.println();
    }

    private static void x(Runnable r) {
        showClass(r.getClass());
        showClass(java.lang.reflect.Array.newInstance(r.getClass(), 1).getClass()); // Obtains an array class of a lambda base type.
    }

    public static void main(String[] args) throws MalformedURLException
    {
        URL base = new File("D:\\_InteliJ\\org.platestack\\Mappings").toURI().toURL();
        Logger logger = Logger.getLogger("main");
        Srg2NotchURLMappingsProvider srgProvider = new Srg2NotchURLMappingsProvider(base, logger);
        Mappings srg2notchMapping = srgProvider.invoke("1.11.2", "1.11.2-R0.1-SNAPSHOT", "v1_11_R1");
        System.out.println(srg2notchMapping);

        BukkitURLMappingsProvider bukkitProvider = new BukkitURLMappingsProvider(base, logger, true);
        Mappings notch2craftMapping = bukkitProvider.invoke("1.11.2", "1.11.2-R0.1-SNAPSHOT", "v1_11_R1");
        System.out.println(notch2craftMapping);

        RemapEnvironment craft2notch = notch2craftMapping.inverse().toFullStructure(new HybridScanner(Thread.currentThread().getContextClassLoader()));
        RemapEnvironment notch2srg = craft2notch.inverse();
        notch2srg.applyToNative(srg2notchMapping.inverse());
        System.out.println(notch2srg);

        RemapEnvironment srg2craft = notch2srg.inverse();
        srg2craft.applyToForeign(notch2craftMapping);
        System.out.println(srg2craft);

        /*
        RemapEnvironment srg2notch = notch2srg.inverse();
        srg2notch.applyToNative(notch2craftMapping);


        Mappings bridge = notch2craftMapping.inverse().bridge(new HybridScanner(Test.class.getClassLoader()), srg2notchMapping.inverse(), false, false, false);
        System.out.println(bridge);

        Mappings srg2bukkit = srg2notchMapping.bridge(notch2craftMapping, true, false, false);
        System.out.println(srg2bukkit);
        */
    }

    public static void main1(String[] args)
    {
        //x(() -> {});
        RemapEnvironment env = new RemapEnvironment();
        HybridScanner scanner = new HybridScanner(Test.class.getClassLoader());

        ClassIdentifier id = ClassIdentifier.create(Test.class.getName().replace('.', '/'));
        ClassStructure scan = scanner.provide(env, id, false);
        System.out.println(scan);

        id = ClassIdentifier.create("org.bukkit.plugin.java.JavaPluginLoader".replace('.','/'));
        scan = scanner.provide(env, id, true);
        System.out.println(scan);

        FieldStructure field = scanner.provide(env, id, new FieldIdentifier("loaders"));
        System.out.println(field);

        id = ClassIdentifier.create("org.bukkit.plugin.java.PluginClassLoader".replace('.','/'));
        field = scanner.provide(env, id, new FieldIdentifier("debug"));
        System.out.println(field);

        id = ClassIdentifier.create("java.security.SecureClassLoader".replace('.','/'));
        field = scanner.provide(env, id, new FieldIdentifier("debug"));
        System.out.println(field);

        id = ClassIdentifier.create("org.bukkit.plugin.java.PluginClassLoader".replace('.','/'));
        scan = scanner.fullScan(env, id);
        System.out.println(scan);

        id = ClassIdentifier.create("org.platestack.bukkit.server.mappings.StreamScanner$supplyClass$visitor$1$visitField$$inlined$let$lambda$1".replace('.','/'));
        scan = scanner.fullScan(env, id);
        System.out.println(scan);

        id = ClassIdentifier.create("org.platestack.bukkit.server.mappings.Distant".replace('.','/'));
        scan = scanner.fullScan(env, id);
        System.out.println(scan);
    }
}
