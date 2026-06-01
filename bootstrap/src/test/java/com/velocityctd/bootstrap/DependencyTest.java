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

import com.velocityctd.bootstrap.Dependency.Origin;
import org.junit.jupiter.api.Test;

class DependencyTest {

  private static Dependency dependency(String classifier) {
    return new Dependency(
        Origin.MAVEN, "gg.gemstone", "component", "1.0.1", "jar", classifier, "abc123", null);
  }

  @Test
  void fileNameOmitsClassifierWhenAbsent() {
    assertEquals("component-1.0.1.jar", dependency(null).fileName());
  }

  @Test
  void fileNameIncludesClassifierWhenPresent() {
    assertEquals("component-1.0.1-natives.jar", dependency("natives").fileName());
  }

  @Test
  void relativePathTranslatesGroupToDirectories() {
    assertEquals(
        "gg/gemstone/component/1.0.1/component-1.0.1.jar", dependency(null).relativePath());
  }

  @Test
  void relativePathIncludesClassifier() {
    assertEquals(
        "gg/gemstone/component/1.0.1/component-1.0.1-natives.jar",
        dependency("natives").relativePath());
  }

  @Test
  void fileNameHonoursExtension() {
    Dependency pom = new Dependency(
        Origin.MAVEN, "gg.gemstone", "component", "1.0.1", "pom", null, "abc123", null);
    assertEquals("component-1.0.1.pom", pom.fileName());
  }
}
