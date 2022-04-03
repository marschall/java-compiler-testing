/*
 * Copyright (C) 2022 Ashley Scopes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.ascopes.jct.paths;

import com.github.ascopes.jct.intern.AsyncResourceCloser;
import com.github.ascopes.jct.intern.StringUtils;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Feature;
import com.google.common.jimfs.Jimfs;
import com.google.common.jimfs.PathType;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Cleaner;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A wrapper around a temporary in-memory file system that exposes the root path of said file
 * system.
 *
 * <p>This provides a utility for constructing complex and dynamic directory tree structures
 * quickly and simply using fluent chained methods.
 *
 * @author Ashley Scopes
 * @since 0.0.1
 */
public class InMemoryPath implements Closeable {

  private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryPath.class);
  private static final Cleaner CLEANER = Cleaner.create();

  private final Path path;
  private final String identifier;

  /**
   * Initialize this forwarding path.
   *
   * @param path the path to delegate to.
   */
  private InMemoryPath(String identifier, Path path) {
    this.path = path;
    this.identifier = Objects.requireNonNull(identifier);
  }

  /**
   * Get the identifying name of the temporary file system.
   *
   * @return the identifier string.
   */
  public String getIdentifier() {
    return identifier;
  }

  /**
   * Get the root path of the in-memory directory.
   *
   * @return the root path.
   */
  public Path getPath() {
    return path;
  }

  /**
   * Close the underlying file system.
   *
   * @throws IOException if an IO error occurs.
   */
  @Override
  public void close() throws IOException {
    path.getFileSystem().close();
  }

  /**
   * Create a file with the given content.
   *
   * @param filePath the path to the file.
   * @param content  the content.
   * @return this object for further call chaining.
   * @throws IOException if an IO error occurs
   */
  public InMemoryPath createFile(Path filePath, byte[] content) throws IOException {
    try (var inputStream = new ByteArrayInputStream(content)) {
      return copyFrom(inputStream, filePath);
    }
  }

  /**
   * Create a file with the given lines of text.
   *
   * @param filePath the path to the file.
   * @param lines    the lines of text to store.
   * @return this object for further call chaining.
   * @throws IOException if an IO error occurs
   */
  public InMemoryPath createFile(Path filePath, String... lines) throws IOException {
    return createFile(filePath, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Create a file with the given content.
   *
   * @param fileName the path to the file.
   * @param content  the content.
   * @return this object for further call chaining.
   * @throws IOException if an IO error occurs
   */
  public InMemoryPath createFile(String fileName, byte[] content) throws IOException {
    return createFile(Path.of(fileName), content);
  }

  /**
   * Create a file with the given lines of text.
   *
   * @param fileName the path to the file.
   * @param lines    the lines of text to store.
   * @return this object for further call chaining.
   * @throws IOException if an IO error occurs
   */
  public InMemoryPath createFile(String fileName, String... lines) throws IOException {
    return createFile(Path.of(fileName), lines);
  }

  /**
   * Copy a resource from the classpath into this in-memory path at the target location.
   *
   * @param resource   the name of the classpath resource.
   * @param targetPath the path to store the resource within this location.
   * @return this object for further call chaining.
   * @throws IOException if an IO error occurs.
   */
  public InMemoryPath copyFromClassPath(String resource, Path targetPath) throws IOException {
    return copyFromClassPath(getClass().getClassLoader(), resource, targetPath);
  }

  /**
   * Copy a resource from the classpath into this in-memory path at the target location.
   *
   * @param resource   the name of the classpath resource.
   * @param targetPath the path to store the resource within this location.
   * @return this object for further call chaining.
   * @throws IOException if an IO error occurs.
   */
  public InMemoryPath copyFromClassPath(String resource, String targetPath) throws IOException {
    return copyFromClassPath(getClass().getClassLoader(), resource, targetPath);
  }

  /**
   * Copy a resource from the classpath into this in-memory path at the target location.
   *
   * @param loader     the classloader to load from.
   * @param resource   the name of the classpath resource.
   * @param targetPath the path to store the resource within this location.
   * @return this object for further call chaining.
   * @throws IOException if an IO error occurs.
   */
  public InMemoryPath copyFromClassPath(
      ClassLoader loader,
      String resource,
      Path targetPath
  ) throws IOException {
    var input = loader.getResourceAsStream(resource);
    if (input == null) {
      throw new FileNotFoundException("classpath:" + resource);
    }
    try (input) {
      return copyFrom(input, targetPath);
    }
  }

  /**
   * Copy a resource from the classpath into this in-memory path at the target location.
   *
   * @param loader     the classloader to load from.
   * @param resource   the name of the classpath resource.
   * @param targetPath the path to store the resource within this location.
   * @return this object for further call chaining.
   * @throws IOException if an IO error occurs.
   */
  public InMemoryPath copyFromClassPath(
      ClassLoader loader,
      String resource,
      String targetPath
  ) throws IOException {
    return copyFromClassPath(loader, resource, Path.of(targetPath));
  }

  /**
   * Copy a tree of resources from the classpath into this in-memory path at the target location.
   *
   * @param packageName the name of the classpath package to copy files from.
   * @param targetPath  the path to store the resources within this location, relative to the
   *                    provided package name.
   * @return this object for further call chaining.
   * @throws IOException if an IO error occurs.
   */
  public InMemoryPath copyTreeFromClassPath(
      String packageName,
      Path targetPath
  ) throws IOException {
    return copyTreeFromClassPath(getClass().getClassLoader(), packageName, targetPath);
  }

  /**
   * Copy a tree of resources from the classpath into this in-memory path at the target location.
   *
   * @param packageName the name of the classpath package to copy files from.
   * @param targetPath  the path to store the resources within this location, relative to the
   *                    provided package name.
   * @return this object for further call chaining.
   * @throws IOException if an IO error occurs.
   */
  public InMemoryPath copyTreeFromClassPath(
      String packageName,
      String targetPath
  ) throws IOException {
    return copyTreeFromClassPath(getClass().getClassLoader(), packageName, targetPath);
  }

  /**
   * Copy a tree of resources from the classpath into this in-memory path at the target location.
   *
   * @param loader      the class loader to use.
   * @param packageName the name of the classpath package to copy files from.
   * @param targetPath  the path to store the resources within this location, relative to the
   *                    provided package name.
   * @return this object for further call chaining.
   * @throws IOException if an IO error occurs.
   */
  public InMemoryPath copyTreeFromClassPath(
      ClassLoader loader,
      String packageName,
      Path targetPath
  ) throws IOException {
    targetPath = makeRelativeToHere(targetPath);
    var directory = Files.createDirectories(targetPath);

    var config = new ConfigurationBuilder()
        .addClassLoaders(loader)
        .forPackages(packageName);

    var resources = new Reflections(config).getAll(Scanners.Resources);

    for (var resource : resources) {
      try (var inputStream = loader.getResourceAsStream(resource)) {
        if (inputStream == null) {
          // This shouldn't ever happen I don't think, but better safe than sorry with providing
          // a semi-meaningful error message if it can happen.
          throw new IOException("Failed to find resource " + resource + " somehow!");
        }

        // +1 to discard the period.
        var resourcePath = directory.resolve(resource.substring(packageName.length() + 1));
        Files.createDirectories(resourcePath.getParent());
        copyFrom(inputStream, resourcePath);
      }
    }

    return this;
  }

  /**
   * Copy a tree of resources from the classpath into this in-memory path at the target location.
   *
   * @param loader      the classloader to use.
   * @param packageName the name of the classpath package to copy files from.
   * @param targetPath  the path to store the resources within this location, relative to the
   *                    provided package name.
   * @return this object for further call chaining.
   * @throws IOException if an IO error occurs.
   */
  public InMemoryPath copyTreeFromClassPath(
      ClassLoader loader,
      String packageName,
      String targetPath
  ) throws IOException {
    return copyTreeFromClassPath(loader, packageName, Path.of(targetPath));
  }

  /**
   * Copy an existing path into this location.
   *
   * @param existingFile the existing file.
   * @param targetPath   the path in this location to copy the file to.
   * @return this object for further call chaining.
   * @throws IOException if an IO error occurs.
   */
  public InMemoryPath copyFrom(Path existingFile, Path targetPath) throws IOException {
    try (var input = Files.newInputStream(existingFile)) {
      return copyFrom(input, targetPath);
    }
  }

  /**
   * Copy an existing path into this location.
   *
   * @param existingFile the existing file.
   * @param targetPath   the path in this location to copy the file to.
   * @return this object for further call chaining.
   * @throws IOException if an IO error occurs.
   */
  public InMemoryPath copyFrom(Path existingFile, String targetPath) throws IOException {
    return copyFrom(existingFile, Path.of(targetPath));
  }

  /**
   * Copy an existing resource at a given URL into this location.
   *
   * @param url        the URL of the resource to copy.
   * @param targetPath the path in this location to copy the file to.
   * @return this object for further call chaining.
   * @throws IOException if an IO error occurs.
   */
  public InMemoryPath copyFrom(URL url, Path targetPath) throws IOException {
    try (var input = url.openStream()) {
      return copyFrom(input, targetPath);
    }
  }

  /**
   * Copy an existing resource at a given URL into this location.
   *
   * @param url        the URL of the resource to copy.
   * @param targetPath the path in this location to copy the file to.
   * @return this object for further call chaining.
   * @throws IOException if an IO error occurs.
   */
  public InMemoryPath copyFrom(URL url, String targetPath) throws IOException {
    try (var input = url.openStream()) {
      return copyFrom(input, targetPath);
    }
  }

  /**
   * Copy an existing resource from an input stream into this location.
   *
   * @param input      the input stream to use.
   * @param targetPath the path in this location to copy the file to.
   * @return this object for further call chaining.
   * @throws IOException if an IO error occurs.
   */
  public InMemoryPath copyFrom(InputStream input, String targetPath) throws IOException {
    return copyFrom(input, Path.of(targetPath));
  }

  /**
   * Copy an existing resource from an input stream into this location.
   *
   * @param input      the input stream to use.
   * @param targetPath the path in this location to copy the file to.
   * @return this object for further call chaining.
   * @throws IOException if an IO error occurs.
   */
  public InMemoryPath copyFrom(InputStream input, Path targetPath) throws IOException {
    input = maybeBuffer(input, targetPath.toUri().getScheme());
    var path = makeRelativeToHere(targetPath);
    var options = new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};
    Files.createDirectories(path.getParent());

    try (var output = Files.newOutputStream(path, options)) {
      input.transferTo(output);
    }
    return this;
  }

  /**
   * Copy the entire tree to a temporary directory on the default file system so that it can be
   * inspected manually in a file explorer.
   *
   * <p>You must then delete the created directory manually.
   *
   * @return the path to the temporary directory that was created.
   * @throws IOException if something goes wrong copying the tree out of memory.
   */
  public Path copyToTempDir() throws IOException {
    var tempPath = Files.copy(
        path,
        Files.createTempDirectory(identifier),
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.COPY_ATTRIBUTES
    );

    LOGGER.info(
        "Copied {} into temporary directory on file system at {}",
        path.toUri(),
        tempPath
    );

    return tempPath;
  }

  @Override
  public String toString() {
    return "InMemoryPath{path=" + StringUtils.quoted(path.toUri()) + "}";
  }

  private Path makeRelativeToHere(Path path) {
    // ToString is needed as JIMFS will fail on trying to make a relative path from a different
    // provider.
    if (path.isAbsolute()) {
      throw new IllegalArgumentException(
          "Cannot use absolute paths for the target path (" + path + ")");
    }

    return path.getFileSystem() == this.path.getFileSystem()
        ? path.normalize()
        : this.path.resolve(path.toString()).normalize();
  }

  /**
   * Create a new in-memory path.
   *
   * <p>The underlying in-memory file system will be closed and destroyed when the returned
   * object is garbage collected, or when {@link #close()} is called on it manually.
   *
   * @return the in-memory path.
   * @see #createPath(String)
   * @see #createPath(boolean)
   * @see #createPath(String, boolean)
   */
  public static InMemoryPath createPath() {
    return createPath(UUID.randomUUID().toString(), true);
  }

  /**
   * Create a new in-memory path.
   *
   * <p>The underlying in-memory file system will be closed and destroyed when the returned
   * object is garbage collected, or when {@link #close()} is called on it manually.
   *
   * @param name a symbolic name to give the path. This must be a valid POSIX directory name.
   * @return the in-memory path.
   * @see #createPath()
   * @see #createPath(boolean)
   * @see #createPath(String, boolean)
   */
  public static InMemoryPath createPath(String name) {
    return createPath(name, true);
  }

  /**
   * Create a new in-memory path.
   *
   * @param closeOnGarbageCollection if {@code true}, then the {@link #close()} operation will be
   *                                 called on the underlying {@link java.nio.file.FileSystem} as
   *                                 soon as the returned object from this method is garbage
   *                                 collected. If {@code false}, then you must close the underlying
   *                                 file system manually using the {@link #close()} method on the
   *                                 returned object. Failing to do so will lead to resources being
   *                                 leaked.
   * @return the in-memory path.
   * @see #createPath()
   * @see #createPath(String)
   * @see #createPath(String, boolean)
   */
  public static InMemoryPath createPath(boolean closeOnGarbageCollection) {
    return createPath(UUID.randomUUID().toString(), closeOnGarbageCollection);
  }

  /**
   * Create a new in-memory path.
   *
   * @param name                     a symbolic name to give the path. This must be a valid POSIX
   *                                 directory name.
   * @param closeOnGarbageCollection if {@code true}, then the {@link #close()} operation will be
   *                                 called on the underlying {@link java.nio.file.FileSystem} as
   *                                 soon as the returned object from this method is garbage
   *                                 collected. If {@code false}, then you must close the underlying
   *                                 file system manually using the {@link #close()} method on the
   *                                 returned object. Failing to do so will lead to resources being
   *                                 leaked.
   * @return the in-memory path.
   * @see #createPath(boolean)
   * @see #createPath(String)
   * @see #createPath(String, boolean)
   */
  public static InMemoryPath createPath(String name, boolean closeOnGarbageCollection) {

    var config = Configuration
        .builder(PathType.unix())
        .setSupportedFeatures(Feature.LINKS, Feature.SYMBOLIC_LINKS, Feature.FILE_CHANNEL)
        .setAttributeViews("basic", "posix")
        .setRoots("/")
        .setWorkingDirectory("/")
        .setPathEqualityUsesCanonicalForm(true)
        .build();

    var fileSystem = Jimfs.newFileSystem(config);
    var rootPath = fileSystem.getRootDirectories().iterator().next().resolve(name);

    var inMemoryPath = new InMemoryPath(name, rootPath);
    var uri = inMemoryPath.path.toUri().toString();

    if (closeOnGarbageCollection) {
      CLEANER.register(inMemoryPath, new AsyncResourceCloser(uri, fileSystem));
    }

    LOGGER.debug("Created new in-memory directory {}", uri);

    return inMemoryPath;
  }

  private static InputStream maybeBuffer(InputStream input, String scheme) {
    if (input instanceof BufferedInputStream) {
      return input;
    }

    scheme = scheme == null
        ? "unknown"
        : scheme.toLowerCase(Locale.ROOT);

    switch (scheme) {
      case "classpath":
      case "jimfs":
      case "jrt":
      case "ram":
        return input;
      default:
        LOGGER.debug("Decided to wrap input {} in a buffer", input);
        return new BufferedInputStream(input);
    }
  }
}
