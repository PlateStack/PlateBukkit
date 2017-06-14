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

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final public class PlateStackLoader extends JavaPlugin
{
    @Override
    public final void onEnable()
    {
        try
        {
            // Setup the variables and download ivy
            final String ivyVersion = "2.4.0";
            final String ivyJar = "ivy-"+ivyVersion+".jar";
            final String ivyMd5 = ivyJar+".md5";
            final String ivySha1 = ivyJar+".sha1";
            final String ivyGroup = "org/apache/ivy";
            final String ivyWebDir = ivyGroup+"/ivy/"+ivyVersion+'/';

            final Path dataFolder = getDataFolder().toPath();
            final Path libsFolder = dataFolder.resolve("libs");
            final Path ivyDir = Files.createDirectories(libsFolder.resolve(ivyGroup.replace('/', '.')));

            final Path ivyJarLocal = ivyDir.resolve(ivyJar);
            final Path ivyMd5Local = ivyDir.resolve(ivyMd5);
            final Path ivySha1Local = ivyDir.resolve(ivySha1);
            if(!check(ivyJarLocal, ivyMd5Local, ivySha1Local))
            {
                final URL[] repositories = new URL[]{
                        new URL("http://central.maven.org/maven2/"),
                        new URL("https://jcenter.bintray.com/")
                };

                boolean success = false;

                retry:
                for (int i = 0; i < 4; i++)
                {
                    if(i > 0)
                        getLogger().warning("Retrying download... Attempt "+(i+1)+"/4");

                    for (URL repository : repositories)
                    {
                        try
                        {
                            download(new URL(repository, ivyWebDir + ivyMd5), ivyMd5Local);
                            download(new URL(repository, ivyWebDir + ivySha1), ivySha1Local);
                            download(new URL(repository, ivyWebDir + ivyJar), ivyJarLocal);
                            if(check(ivyJarLocal, ivyMd5Local, ivySha1Local))
                            {
                                success = true;
                                break retry;
                            }
                        }
                        catch(IOException ignored)
                        {
                        }
                    }
                }

                if(!success)
                    throw new IllegalStateException("Failed to download the ivy library, impossible to continue.");
            }

            // Setup the initial class loader
            final URL[] modules = Stream.of("initial", "library-loader", "main", "api-util", "scanner", "api", "common-util")
                    .map(it-> "/META-INF/modules/"+it+"/")
                    .map(it-> Objects.requireNonNull(getClass().getResource(it), "Missing the internal file: "+it))
                    .toArray(URL[]::new);

            final URL moduleInitial = modules[0];
            final URL moduleLibraryLoader = modules[1];
            final URL moduleMain = modules[2];
            final URL moduleUtil = modules[3];
            final URL moduleScanner = modules[4];
            final URL moduleApi = modules[5];
            final URL moduleCommonUtil = modules[6];

            final URL ivyUrl = ivyJarLocal.toUri().toURL();
            ClassLoader classLoader = new URLClassLoader(
                    new URL[]{ moduleInitial, moduleLibraryLoader, ivyUrl },
                    getClassLoader()
            );

            final Class<?> initialResolverClass = classLoader.loadClass(getClass().getPackage().getName() + ".InitialResolver");
            if(classLoader != initialResolverClass.getClassLoader())
                throw new IllegalStateException("The InitialResolver class was not loaded by our custom classloader which contains the ivy library! "+initialResolverClass.getClassLoader());

            // Setup kotlin class loader
            getLogger().info("Downloading kotlin...");
            final Method resolveKotlinMethod = initialResolverClass.getDeclaredMethod("resolveKotlin", JavaPlugin.class, List.class);
            final List<String> libraryListFiles =
                    Stream.of("common", "bukkit")
                            .map(it-> "/META-INF/modules/main/org/platestack/"+it+"/libraries.list").collect(Collectors.toList());
            libraryListFiles.add("/META-INF/modules/api/org/platestack/api/libraries.list");

            final List<?> kotlinJars = (List<?>) resolveKotlinMethod.invoke(null, this,
                    libraryListFiles.stream()
                            .map(it->
                                    Objects.requireNonNull(getClass().getResourceAsStream(
                                            it
                                    ), "Missing file: "+it )
                            ).collect(Collectors.toList())
            );

            classLoader =
                    new KotlinClassLoader(
                            Stream.concat(
                                    kotlinJars.stream()
                                            .map(it-> (File) it)
                                            .map(file ->
                                            {
                                                try
                                                {
                                                    return file.toURI().toURL();
                                                } catch(MalformedURLException e)
                                                {
                                                    throw new RuntimeException(e);
                                                }
                                            }),
                                    //Stream.of(moduleUtil, moduleScanner, moduleCommonUtil)
                                    Stream.empty()
                            )
                            .toArray(URL[]::new),
                            getClassLoader()
                    );
            final ClassLoader kotlinClassLoader = classLoader;

            // Setup scanning class loader
            /*
            final Class<?> scanningClassLoaderClass = classLoader.loadClass("org.platestack.bukkit.scanner.transform.ScannerClassLoader");
            if(classLoader != scanningClassLoaderClass.getClassLoader())
                throw new IllegalStateException("The ScannerClassLoader class was not loaded by the right class loader! "+scanningClassLoaderClass.getClassLoader());

            classLoader = (ClassLoader) scanningClassLoaderClass.getConstructor(ClassLoader.class).newInstance(classLoader);
            final ClassLoader scanningClassLoader = classLoader;
            */
            final ScannerClassLoader scanningClassLoader = new ScannerClassLoader(classLoader);
            classLoader = scanningClassLoader;

            // Setup root class loader
            final Method resolveMethod = initialResolverClass.getDeclaredMethod("resolve", JavaPlugin.class, String.class, List.class);
            getLogger().info("Downloading libraries required by the PlateStack API...");

            final List<?> apiJars = (List<?>) resolveMethod.invoke(null, this, "plate-api",
                    Collections.singletonList(
                            Objects.requireNonNull(getClass().getResourceAsStream(
                                    "/META-INF/modules/api/org/platestack/api/libraries.list"
                            ), "Missing file: /META-INF/modules/api/org/platestack/api/libraries.list" )
                    )
            );
            apiJars.removeAll(kotlinJars);

            /*
            final Class<?> rootClassLoaderClass = classLoader.loadClass("org.platestack.bukkit.scanner.transform.RootClassLoader");
            if(kotlinClassLoader != rootClassLoaderClass.getClassLoader())
                throw new IllegalStateException("The RootClassLoader class was not loaded by the right class loader! "+rootClassLoaderClass.getClassLoader());

            classLoader = (ClassLoader) rootClassLoaderClass.getConstructor(URL[].class, URL[].class, scanningClassLoaderClass)
                    .newInstance(
                            new URL[]{moduleApi},
                            apiJars.stream()
                                    .map(it-> (File) it)
                                    .map(it ->
                                    {
                                        try
                                        {
                                            return it.toURI().toURL();
                                        } catch(MalformedURLException e)
                                        {
                                            throw new RuntimeException(e);
                                        }
                                    })
                                    .toArray(URL[]::new),
                            scanningClassLoader
                    );
            */
            final RootClassLoader rootClassLoader = new RootClassLoader(
                    new URL[]{moduleApi},
                    apiJars.stream()
                            .map(it-> (File) it)
                            .map(it ->
                            {
                                try
                                {
                                    return it.toURI().toURL();
                                } catch(MalformedURLException e)
                                {
                                    throw new RuntimeException(e);
                                }
                            })
                            .toArray(URL[]::new),
                    scanningClassLoader
            );
            classLoader = rootClassLoader;

            // Setup core dependencies class loader
            getLogger().info("Downloading libraries required by the PlateStack Bukkit and Common...");
            final List<?> coreJars = (List<?>) resolveMethod.invoke(null, this, "plate-bukkit",
                    Stream.of("common", "bukkit").map(it->
                            Objects.requireNonNull(getClass().getResourceAsStream(
                                    "/META-INF/modules/main/org/platestack/"+it+"/libraries.list"
                            ), "Missing file: /META-INF/modules/main/org/platestack/"+it+"/libraries.list" )
                    )
                    .collect(Collectors.toList())
            );
            apiJars.removeAll(kotlinJars);
            apiJars.removeAll(apiJars);

            /*
            final Class<?> coreDepsClass = classLoader.loadClass("org.platestack.bukkit.scanner.transform.CoreDependenciesClassLoader");
            if(classLoader != coreDepsClass.getClassLoader())
                throw new IllegalStateException("The CoreDependenciesClassLoader class was not loaded by the right class loader! "+coreDepsClass.getClassLoader());

            classLoader = (ClassLoader) coreDepsClass
                    .getConstructor(URL[].class, rootClassLoaderClass)
                    .newInstance(coreJars.stream().map(it-> (File) it).map(it ->
                    {
                        try
                        {
                            return it.toURI().toURL();
                        } catch(MalformedURLException e)
                        {
                            throw new RuntimeException(e);
                        }
                    }).toArray(URL[]::new), rootClassLoader);
            */
            final CoreDependenciesClassLoader coreDepsClassLoader = new CoreDependenciesClassLoader(
                    Stream.concat(
                            coreJars.stream().map(it-> (File) it).map(it ->
                            {
                                try
                                {
                                    return it.toURI().toURL();
                                } catch(MalformedURLException e)
                                {
                                    throw new RuntimeException(e);
                                }
                            }),
                            Stream.of(moduleUtil, moduleScanner, moduleCommonUtil)
                    ).toArray(URL[]::new)
                    ,
                    rootClassLoader
            );
            classLoader = coreDepsClassLoader;

            // Setup SRG -> CraftBukkit remap environment
            final Class<?> bootClass = classLoader.loadClass("org.platestack.bukkit.scanner.Boot");
            if(classLoader != bootClass.getClassLoader())
                throw new IllegalStateException("The Boot class was not loaded by the right class loader! "+bootClass.getClassLoader());

            final Method method = bootClass
                    .getDeclaredMethod("boot", JavaPlugin.class, RootClassLoader.class);
            method.setAccessible(true);
            method.invoke(null, this, rootClassLoader);

            // Setup main class loader
            final Class<?> mainClassLoaderClass = classLoader.loadClass("org.platestack.bukkit.scanner.transform.MainClassLoader");
            if(classLoader != mainClassLoaderClass.getClassLoader())
                throw new IllegalStateException("The MainClassLoader class was not loaded by the right class loader! "+mainClassLoaderClass.getClassLoader());

            classLoader = (ClassLoader) mainClassLoaderClass
                    .getConstructor(URL[].class, CoreDependenciesClassLoader.class)
                    .newInstance(new URL[]{moduleMain}, coreDepsClassLoader);

            // Setup main transformer class loader
            classLoader = (ClassLoader) rootClassLoader.loadClass("org.platestack.bukkit.scanner.transform.MainTransformerClassLoader")
                    .getConstructor(mainClassLoaderClass)
                    .newInstance(classLoader);

            // Initialize PlateBukkit
            final Class<?> plateBukkitClass = classLoader.loadClass("org.platestack.bukkit.server.PlateBukkit");
            if(classLoader != plateBukkitClass.getClassLoader())
                throw new IllegalStateException("The PlateBukkit class was not loaded by our custom classloader which contains all the required libraries! "+initialResolverClass.getClassLoader());

            final Class<?> transformerClass = classLoader.loadClass("org.platestack.bukkit.server.mappings.BukkitTransformer");
            if(classLoader != plateBukkitClass.getClassLoader())
                throw new IllegalStateException("The BukkitTransformer object was not loaded by our custom classloader which contains all the required libraries! "+initialResolverClass.getClassLoader());

            final Class<?> kotlin = classLoader.loadClass("kotlin.jvm.JvmClassMappingKt");
            final Object transformerKClass = kotlin.getDeclaredMethod("getKotlinClass", Class.class).invoke(null, transformerClass);

            final Class<?> transformerInterface = classLoader.loadClass("org.platestack.common.plugin.loader.Transformer");

            final Object transformer = transformerInterface.cast(
                transformerKClass.getClass().getMethod("getObjectInstance").invoke(transformerKClass)
            );

            transformerClass.getDeclaredMethod("initialize", ClassLoader.class, Logger.class)
                    .invoke(transformer, classLoader, getLogger());

            final Constructor<?> constructor = plateBukkitClass.getDeclaredConstructor(JavaPlugin.class, transformerInterface);
            final Object plateBukkit = constructor.newInstance(this, transformer);

            final Method onEnableMethod = plateBukkitClass.getMethod("onEnable");
            onEnableMethod.invoke(plateBukkit);
        }
        catch(Throwable e)
        {
            try
            {
                getLogger().log(Level.SEVERE, "Failed to load PlateStack. Shutting down!", e);
            }
            catch(Throwable e2)
            {
                e.addSuppressed(e2);
                e.printStackTrace();
            }
            finally
            {
                Bukkit.shutdown();
            }
        }
    }

    private void download(final URL url, final Path toPath)
            throws IOException
    {
        getLogger().info(()-> "Attempting to download "+url+" to "+toPath);
        try
        {
            Files.createDirectories(toPath.getParent());
            try (
                    InputStream inputStream = url.openStream();
                    ReadableByteChannel rbc = Channels.newChannel(inputStream);
                    FileChannel out = FileChannel.open(toPath,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE
                    )
            )
            {
                long total = out.transferFrom(rbc, 0, Long.MAX_VALUE);
                getLogger().info(()-> "Downloaded "+total+" bytes to "+toPath);
            }
        }
        catch(IOException e)
        {
            getLogger().warning(()-> "Failed to download "+url+" - "+e.getClass().getSimpleName()+": "+String.valueOf(e.getLocalizedMessage()));
            throw e;
        }
    }

    private boolean check(final Path jar, final Path md5, final Path sha1) throws NoSuchAlgorithmException, IOException
    {
        if(!Files.isRegularFile(jar) || !Files.isRegularFile(md5) || !Files.isRegularFile(sha1))
            return false;

        final Optional<BigInteger> referenceMd5 = readHash(md5);
        final Optional<BigInteger> referenceSha1 = readHash(sha1);

        if(!referenceMd5.isPresent() || !referenceSha1.isPresent())
            return false;

        getLogger().fine(()-> "Found valid md5 and sha1 files for "+jar+", checking if it matches...");

        final MessageDigest digestMd5 = MessageDigest.getInstance("MD5");
        final MessageDigest digestSha1 = MessageDigest.getInstance("SHA-1");

        try (InputStream in = Files.newInputStream(jar))
        {
            final byte[] bytes = new byte[40*1024];
            int read;
            while ((read = in.read(bytes)) != -1)
            {
                digestMd5.update(bytes, 0, read);
                digestSha1.update(bytes, 0, read);
            }

            final BigInteger resultMd5 = new BigInteger(1, digestMd5.digest());
            final BigInteger resultSha1 = new BigInteger(1, digestSha1.digest());

            if(resultMd5.equals(referenceMd5.get()) && resultSha1.equals(referenceSha1.get()))
            {
                getLogger().fine(()-> jar+" is valid");
                return true;
            }
            else
            {
                getLogger().warning(()-> jar+" is CORRUPTED");
                return false;
            }
        }
    }

    private Optional<BigInteger> readHash(final Path path) throws IOException
    {
        return Files.lines(path).findFirst()
                .map(String::trim).filter(line-> !line.isEmpty())
                .map(line-> line.split(" ")[0])
                .map(hash -> new BigInteger(hash, 16));
    }
}
