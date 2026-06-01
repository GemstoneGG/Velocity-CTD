/*
 * Copyright (C) 2026 Velocity-CTD Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocityctd.bootstrap;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Removes stale artifacts from the libraries directory: jars left over from older installs (no
 * longer required, or superseded by a newer version) and the now-empty directories they leave
 * behind. Only {@code *.jar} files are ever deleted; any other file is left untouched so that
 * unrelated content placed under the directory cannot be removed. Anything present in the current
 * {@link LibraryManifest} is preserved.
 */
public final class LibraryCleaner {

  private static final String JAR_SUFFIX = ".jar";

  private final LibraryManifest manifest;

  /**
   * Creates a cleaner for the given manifest.
   *
   * @param manifest the manifest describing the libraries that should be kept
   */
  public LibraryCleaner(LibraryManifest manifest) {
    this.manifest = manifest;
  }

  /**
   * Removes every {@code *.jar} under the libraries directory that is not required by the manifest,
   * then prunes any directories left empty as a result.
   *
   * @param librariesDir the directory that mirrors a Maven repository layout
   *
   * @return how many files were removed ({@code >= 0})
   */
  public int clean(Path librariesDir) {
    if (!Files.isDirectory(librariesDir)) {
      return 0;
    }

    Set<Path> expected = new HashSet<>();
    for (Dependency dependency : manifest.dependencies()) {
      expected.add(librariesDir.resolve(dependency.relativePath()).normalize());
    }

    int removed = removeUnusedFiles(librariesDir, expected);
    pruneEmptyDirectories(librariesDir);

    return removed;
  }

  private int removeUnusedFiles(Path librariesDir, Set<Path> expected) {
    List<Path> files;
    try (Stream<Path> walk = Files.walk(librariesDir)) {
      files = walk.filter(Files::isRegularFile).toList();
    } catch (IOException exception) {
      throw new UncheckedIOException("Failed to scan libraries directory " + librariesDir, exception);
    }

    int removed = 0;
    for (Path file : files) {
      if (!isJar(file) || expected.contains(file.normalize())) {
        continue;
      }
      try {
        Files.delete(file);
        removed++;
        BootstrapLogger.trace("Removed unused library " + librariesDir.relativize(file));
      } catch (IOException exception) {
        BootstrapLogger.warn("Failed to remove " + file + ": " + exception.getMessage());
      }
    }
    return removed;
  }

  private void pruneEmptyDirectories(Path librariesDir) {
    List<Path> directories;
    // Sort deepest-first so a parent is only considered after its (possibly emptied) children.
    try (Stream<Path> walk = Files.walk(librariesDir)) {
      directories = walk.filter(Files::isDirectory).sorted(Comparator.reverseOrder()).toList();
    } catch (IOException exception) {
      throw new UncheckedIOException("Failed to scan libraries directory " + librariesDir, exception);
    }

    for (Path directory : directories) {
      if (directory.equals(librariesDir) || !isEmpty(directory)) {
        continue;
      }
      try {
        Files.delete(directory);
        BootstrapLogger.trace("Removed empty directory " + librariesDir.relativize(directory));
      } catch (IOException exception) {
        BootstrapLogger.warn("Failed to remove directory " + directory + ": " + exception.getMessage());
      }
    }
  }

  private static boolean isJar(Path file) {
    String name = file.getFileName().toString();
    return name.regionMatches(true, name.length() - JAR_SUFFIX.length(), JAR_SUFFIX, 0, JAR_SUFFIX.length());
  }

  private static boolean isEmpty(Path directory) {
    try (Stream<Path> entries = Files.list(directory)) {
      return entries.findAny().isEmpty();
    } catch (IOException exception) {
      return false;
    }
  }
}
