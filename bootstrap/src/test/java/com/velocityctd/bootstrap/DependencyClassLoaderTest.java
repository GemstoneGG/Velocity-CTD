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

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DependencyClassLoaderTest {

  @Test
  void exposesEachJarAsUrlInOrder(@TempDir Path tempDir) throws Exception {
    Path first = tempDir.resolve("a.jar");
    Path second = tempDir.resolve("b.jar");

    try (DependencyClassLoader loader = DependencyClassLoader.create(List.of(first, second))) {
      URL[] urls = loader.getURLs();
      assertEquals(2, urls.length);
      assertEquals(first.toUri().toURL(), urls[0]);
      assertEquals(second.toUri().toURL(), urls[1]);
    }
  }

  @Test
  void hasStableName(@TempDir Path tempDir) throws Exception {
    try (DependencyClassLoader loader =
        DependencyClassLoader.create(List.of(tempDir.resolve("a.jar")))) {
      assertEquals("velocity-bootstrap", loader.getName());
    }
  }

  @Test
  void doesNotInheritTheApplicationClasspath(@TempDir Path tempDir) throws Exception {
    try (DependencyClassLoader loader =
        DependencyClassLoader.create(List.of(tempDir.resolve("a.jar")))) {
      // The parent is the platform loader, so the bootstrap's own classes must not be visible.
      assertEquals(
          ClassLoader.getSystemClassLoader().getParent(), loader.getParent());
    }
  }
}
