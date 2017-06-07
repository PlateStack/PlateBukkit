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

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

final public class PlateStackLoader extends JavaPlugin
{
    @Override
    public final void onEnable()
    {
        try
        {
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

            final URL ourUrl = getClass().getProtectionDomain().getCodeSource().getLocation();
            final URL ivyUrl = ivyJarLocal.toUri().toURL();

            ClassLoader classLoader = new URLClassLoader(new URL[]{
                    ivyUrl,
                    ourUrl,
            }, ClassLoader.getSystemClassLoader());

            final Class<?> initialResolverClass = classLoader.loadClass(getClass().getPackage().getName() + ".InitialResolver");
            if(classLoader != initialResolverClass.getClassLoader())
                throw new IllegalStateException("The InitialResolver class was not loaded by our custom classloader which contains the ivy library! "+initialResolverClass.getClassLoader());

            getLogger().info("Downloading libraries required by the PlateStack platform...");
            final Method resolveMethod = initialResolverClass.getDeclaredMethod("resolve", JavaPlugin.class, List.class);

            final List<?> jars = (List<?>) resolveMethod.invoke(null, this,
                    Arrays.asList(
                            Objects.requireNonNull(getClass().getResourceAsStream("/org/platestack/api/libraries.list"), "api/libraries.list not found"),
                            Objects.requireNonNull(getClass().getResourceAsStream("/org/platestack/common/libraries.list"), "common/libraries.list not found"),
                            Objects.requireNonNull(getClass().getResourceAsStream("/org/platestack/bukkit/libraries.list"), "bukkit/libraries.list not found")
                    )
            );

            classLoader = new URLClassLoader(
                    Stream.concat(
                            Stream.of(ourUrl),
                            jars.stream().map(it-> (File) it).map(file -> {
                                try
                                {
                                    return file.toURI().toURL();
                                } catch(MalformedURLException e)
                                {
                                    throw new RuntimeException(e);
                                }
                            })
                    )
                            //.peek(file-> getLogger().info("Using: "+file))
                            .toArray(URL[]::new),
                    ClassLoader.getSystemClassLoader()
            );

            final ClassLoader[] classLoaders = {classLoader, getClassLoader()};

            classLoader = new ClassLoader(ClassLoader.getSystemClassLoader()) {
                @Override
                protected Enumeration<URL> findResources(String name) throws IOException
                {
                    final List<Enumeration<URL>> total = new ArrayList<>(classLoaders.length);
                    for (ClassLoader loader : classLoaders)
                    {
                        Enumeration<URL> resources = loader.getResources(name);
                        if(resources.hasMoreElements())
                            total.add(resources);
                    }

                    if(total.isEmpty())
                        return new Enumeration<URL>()
                        {
                            @Override
                            public boolean hasMoreElements()
                            {
                                return false;
                            }

                            @Override
                            public URL nextElement()
                            {
                                throw new NoSuchElementException();
                            }
                        };

                    if(total.size() == 1)
                        return total.get(0);

                    return new Enumeration<URL>()
                    {
                        private Queue<Enumeration<URL>> queue = new ArrayDeque<>(total);

                        @Override
                        public boolean hasMoreElements()
                        {
                            return !queue.isEmpty();
                        }

                        @Override
                        public URL nextElement()
                        {
                            Enumeration<URL> iter = queue.peek();
                            URL next = iter.nextElement();
                            if(!iter.hasMoreElements())
                                queue.remove();
                            return next;
                        }
                    };
                }

                @Override
                protected URL findResource(String name)
                {
                    for (ClassLoader loader : classLoaders)
                    {
                        final URL resource = loader.getResource(name);
                        if(resource != null)
                            return resource;
                    }

                    return null;
                }


                @Override
                protected Class<?> findClass(String name) throws ClassNotFoundException
                {
                    ClassNotFoundException exception = null;
                    for (ClassLoader loader : classLoaders)
                    {
                        final URL resource = loader.getResource(name.replace('.', '/') + ".class");
                        if(resource != null)
                        {
                            final byte[] classData;
                            try(InputStream input = resource.openStream())
                            {
                                final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                                int nRead;
                                final byte[] data = new byte[16384];

                                while ((nRead = input.read(data, 0, data.length)) != -1) {
                                    buffer.write(data, 0, nRead);
                                }

                                buffer.flush();
                                classData = buffer.toByteArray();
                            }
                            catch(IOException e)
                            {
                                e.printStackTrace();
                                continue;
                            }

                            URL source;
                            try
                            {
                                final JarURLConnection connection =
                                        (JarURLConnection) resource.openConnection();
                                source = connection.getJarFileURL();
                            }
                            catch(Exception e)
                            {
                                source = resource;
                            }

                            ProtectionDomain protectionDomain = new ProtectionDomain(new CodeSource(source, (Certificate[]) null), null);
                            return defineClass(name, classData, 0, classData.length, protectionDomain);
                        }
                        try
                        {
                            return loader.loadClass(name);
                        }
                        catch(ClassNotFoundException e)
                        {
                            if(exception == null)
                                exception = new ClassNotFoundException(name);
                            exception.addSuppressed(e);
                        }
                    }

                    if(exception != null)
                        throw exception;
                    throw new ClassNotFoundException(name);
                }
            };

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
