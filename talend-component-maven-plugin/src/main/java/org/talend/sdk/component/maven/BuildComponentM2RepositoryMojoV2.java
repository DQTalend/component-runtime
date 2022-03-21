/**
 * Copyright (C) 2006-2022 Talend Inc. - www.talend.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.talend.sdk.component.maven;

import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.newBufferedWriter;
import static java.nio.file.Files.newInputStream;
import static java.nio.file.Files.setLastModifiedTime;
import static java.nio.file.Files.walkFileTree;
import static java.nio.file.attribute.FileTime.from;
import static java.time.Instant.ofEpochMilli;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.apache.commons.io.output.NullOutputStream.NULL_OUTPUT_STREAM;
import static org.apache.maven.plugins.annotations.LifecyclePhase.PACKAGE;
import static org.apache.maven.plugins.annotations.ResolutionScope.TEST;
import static org.talend.sdk.component.maven.api.Audience.Type.TALEND_INTERNAL;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.talend.sdk.component.maven.api.Audience;

@Audience(TALEND_INTERNAL)
@Mojo(name = "build-repository", defaultPhase = PACKAGE, threadSafe = true, requiresDependencyResolution = TEST)
public class BuildComponentM2RepositoryMojoV2 extends CarConsumer {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    @Parameter(defaultValue = "true", property = "talend.connectors.write")
    private Boolean writeVersion;

    @Parameter(defaultValue = "${project.version}", property = "talend.connectors.version")
    private String version;

    @Parameter(defaultValue = "CONNECTORS_VERSION", property = "talend.connectors.file")
    private String connectorsVersionFile;

    @Parameter(property = "talend-m2.registryBase")
    private File componentRegistryBase;

    private Path componentRegistryBasePath; // Can't get a Path directly from a @Parameter...

    @Parameter(property = "talend-m2.root",
            defaultValue = "${maven.multiModuleProjectDirectory}/target/talend-component-kit/maven")
    protected File m2Root;

    protected Path m2RootPath; // Can't get a Path directly from a @Parameter...

    @Parameter(property = "talend-m2.clean", defaultValue = "true")
    private boolean cleanBeforeGeneration;

    @Parameter(defaultValue = "true", property = "talend.repository.createDigestRegistry")
    private boolean createDigestRegistry;

    @Parameter(defaultValue = "SHA-512", property = "talend.repository.digestAlgorithm")
    private String digestAlgorithm;

    @Override
    public void doExecute() throws MojoExecutionException {
        final Set<Artifact> componentCars = project.getDependencies()
                .stream()
                .filter(dependency -> "car".equals(dependency.getType())) // FIXME: car should be extracted
                .filter(dependency -> "compile".equals(dependency.getScope()))
                .map(dependency -> new DefaultArtifact(
                        dependency.getGroupId(),
                        dependency.getArtifactId(),
                        dependency.getClassifier(),
                        "car",
                        dependency.getVersion()))
                .map(artifact -> resolve(artifact, classifier, "car")) // No resolve, no file
                .collect(toSet());

        m2RootPath = Paths.get(m2Root.getAbsolutePath());
        componentRegistryBasePath = componentRegistryBase == null
                ? null
                : Paths.get(componentRegistryBase.getAbsolutePath());

        try {
            if (cleanBeforeGeneration && exists(m2RootPath)) {
                deleteDirectory(m2RootPath.toFile()); // java.nio.Files.delete fails if dir is not empty
            }
            createDirectories(m2RootPath);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        componentCars.forEach(this::copyComponentDependencies);

        if (componentCars.isEmpty()) {
            throw new IllegalStateException(
                    "No components found, check the component cars are included in your dependencies with scope compile");
        } else {
            final String coordinates = componentCars
                    .stream()
                    .map(this::computeCoordinates)
                    .collect(joining(","));

            getLog().info("Included components " + coordinates);
        }

        writeRegistry(getNewComponentRegistry(componentCars));
        if (createDigestRegistry) {
            writeDigest(getDigests());
        }

        if (writeVersion) {
            writeConnectorsVersion();
            getLog().info(connectorsVersionFile + " set to " + version);
        }

        getLog().info("Created component repository at " + m2Root);
    }

    private String computeCoordinates(final Artifact artifact) {
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
    }

    private Properties getNewComponentRegistry(final Set<Artifact> componentCars) {
        final Properties components = new Properties();
        if (componentRegistryBasePath != null && exists(componentRegistryBasePath)) {
            try (final InputStream source = newInputStream(componentRegistryBasePath)) {
                components.load(source);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }
        componentCars.forEach(
                componentCar -> components.put(componentCar.getArtifactId(), computeCoordinates(componentCar)));

        return components;
    }

    private void writeProperties(final Properties content, final Path location) {
        try (final Writer output = newBufferedWriter(location)) {
            content.store(output, "Generated by Talend Component Kit " + getClass().getSimpleName());
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Properties getDigests() {
        final Properties index = new Properties();
        try {
            walkFileTree(m2RootPath, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    if (!file.getFileName().toString().startsWith(".")) {
                        index.setProperty(m2RootPath.relativize(file).toString(), hash(file));
                    }
                    return super.visitFile(file, attrs);
                }
            });
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        return index;
    }

    private void writeDigest(final Properties digestRegistry) {
        writeProperties(digestRegistry, getDigestRegistry());
    }

    private void writeRegistry(final Properties components) {
        writeProperties(components, getRegistry());
    }

    private void writeConnectorsVersion() {
        try (final Writer output = newBufferedWriter(getConnectorsVersionFile())) {
            output.write(version);
            output.flush();
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String hash(final Path file) {
        try (final DigestOutputStream out =
                new DigestOutputStream(NULL_OUTPUT_STREAM, MessageDigest.getInstance(digestAlgorithm))) {
            copy(file, out);
            out.flush();
            return hex(out.getMessageDigest().digest());
        } catch (final NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(final byte[] data) {
        final StringBuilder out = new StringBuilder(data.length * 2);
        for (final byte b : data) {
            out.append(HEX_CHARS[b >> 4 & 15]).append(HEX_CHARS[b & 15]);
        }
        return out.toString();
    }

    private void copyDependency(final ZipEntry zipEntry, final ZipInputStream zipStream) {
        final String relativeDependencyPath = zipEntry
                .getName()
                .substring("MAVEN-INF/repository/".length());

        final Path m2DependencyPath = m2RootPath.resolve(relativeDependencyPath);

        try {
            createDirectories(m2DependencyPath.getParent());
            copy(zipStream, m2DependencyPath);

            final long lastModified = zipEntry.getTime();
            if (lastModified > 0) {
                setLastModifiedTime(m2DependencyPath, from(ofEpochMilli(lastModified)));
            }
            if (getLog().isDebugEnabled()) {
                getLog().debug("Adding " + m2DependencyPath);
            }
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    protected void copyComponentDependencies(final Artifact car) {
        try (final FileInputStream fileStream = new FileInputStream(car.getFile());
                final BufferedInputStream bufferedStream = new BufferedInputStream(fileStream);
                final ZipInputStream zipStream = new ZipInputStream(bufferedStream)) {

            ZipEntry zipEntry;
            while ((zipEntry = zipStream.getNextEntry()) != null) {
                if (zipEntry.isDirectory()) {
                    continue;
                }

                if (!zipEntry.getName().startsWith("MAVEN-INF/repository/")) {
                    continue;
                }

                copyDependency(zipEntry, zipStream);
            }
        } catch (final IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private Path getConnectorsVersionFile() {
        return m2RootPath.resolve(connectorsVersionFile);
    }

    private Path getRegistry() {
        return m2RootPath.resolve("component-registry.properties");
    }

    private Path getDigestRegistry() {
        return m2RootPath.resolve("component-registry-digest.properties");
    }
}
