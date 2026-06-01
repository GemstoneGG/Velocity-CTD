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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocityctd.bootstrap.Dependency.Origin;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryCleanerTest {

  private static Dependency dependency(String group, String name, String version) {
    return new Dependency(Origin.MAVEN, group, name, version, "jar", null, "hash", null);
  }

  private static LibraryManifest manifestOf(Dependency... dependencies) {
    return new LibraryManifest("com.example.Proxy", List.of(), List.of(dependencies));
  }

  private static Path touch(Path librariesDir, Dependency dependency) throws IOException {
    Path file = librariesDir.resolve(dependency.relativePath());
    Files.createDirectories(file.getParent());
    Files.writeString(file, "content");
    return file;
  }

  @Test
  void returnsZeroWhenDirectoryMissing(@TempDir Path tempDir) {
    Path missing = tempDir.resolve("does-not-exist");
    assertEquals(0, new LibraryCleaner(manifestOf()).clean(missing));
  }

  @Test
  void keepsExpectedFiles(@TempDir Path tempDir) throws IOException {
    Dependency keep = dependency("gg.gemstone", "component", "1.0.1");
    Path kept = touch(tempDir, keep);

    int removed = new LibraryCleaner(manifestOf(keep)).clean(tempDir);

    assertEquals(0, removed);
    assertTrue(Files.exists(kept));
  }

  @Test
  void removesUnusedFilesAndPrunesEmptyDirectories(@TempDir Path tempDir) throws IOException {
    Dependency keep = dependency("gg.gemstone", "component", "1.0.1");
    Dependency stale = dependency("org.other", "unused", "2.0.0");
    Path kept = touch(tempDir, keep);
    Path staleFile = touch(tempDir, stale);

    int removed = new LibraryCleaner(manifestOf(keep)).clean(tempDir);

    assertEquals(1, removed);
    assertTrue(Files.exists(kept));
    assertFalse(Files.exists(staleFile));
    // The whole org/other/... directory tree should have been pruned away.
    assertFalse(Files.exists(tempDir.resolve("org")));
  }

  @Test
  void removesSupersededVersionOfSameArtifact(@TempDir Path tempDir) throws IOException {
    Dependency current = dependency("gg.gemstone", "component", "2.0.0");
    Dependency old = dependency("gg.gemstone", "component", "1.0.1");
    Path currentFile = touch(tempDir, current);
    Path oldFile = touch(tempDir, old);

    int removed = new LibraryCleaner(manifestOf(current)).clean(tempDir);

    assertEquals(1, removed);
    assertTrue(Files.exists(currentFile));
    assertFalse(Files.exists(oldFile));
    // The stale version directory is gone, but the artifact directory itself remains.
    assertFalse(Files.exists(tempDir.resolve("gg/gemstone/component/1.0.1")));
    assertTrue(Files.exists(tempDir.resolve("gg/gemstone/component/2.0.0")));
  }

  @Test
  void preservesNonJarFiles(@TempDir Path tempDir) throws IOException {
    Dependency stale = dependency("org.other", "unused", "2.0.0");
    Path staleJar = touch(tempDir, stale);
    Path checksum = staleJar.resolveSibling(staleJar.getFileName() + ".sha256");
    Path readme = tempDir.resolve("README.txt");
    Files.writeString(checksum, "deadbeef");
    Files.writeString(readme, "do not delete me");

    int removed = new LibraryCleaner(manifestOf()).clean(tempDir);

    assertEquals(1, removed);
    assertFalse(Files.exists(staleJar));
    // Non-jar files are left untouched, even when their directory tree is otherwise stale.
    assertTrue(Files.exists(checksum));
    assertTrue(Files.exists(readme));
  }

  @Test
  void preservesTopLevelLibrariesDirectoryEvenWhenEmptied(@TempDir Path tempDir) throws IOException {
    Dependency stale = dependency("org.other", "unused", "2.0.0");
    touch(tempDir, stale);

    int removed = new LibraryCleaner(manifestOf()).clean(tempDir);

    assertEquals(1, removed);
    assertTrue(Files.isDirectory(tempDir));
  }
}
