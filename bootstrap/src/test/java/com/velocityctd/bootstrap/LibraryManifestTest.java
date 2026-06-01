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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.velocityctd.bootstrap.Dependency.Origin;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class LibraryManifestTest {

  private static InputStream manifest(String... lines) {
    String joined = String.join("\n", lines);
    return new ByteArrayInputStream(joined.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void parsesMainClassRepositoriesAndDependencies() {
    LibraryManifest result = LibraryManifest.parse(manifest(
        "main-class\tcom.example.Proxy",
        "repo\thttps://repo1.example/maven2",
        "repo\thttps://repo2.example/maven2",
        "dep\tMAVEN\tgg.gemstone\tcomponent\t1.0.1\tjar\t-\tabc123"));

    assertEquals("com.example.Proxy", result.mainClass());
    assertEquals(
        List.of("https://repo1.example/maven2", "https://repo2.example/maven2"),
        result.repositories());
    assertEquals(1, result.dependencies().size());

    Dependency dependency = result.dependencies().getFirst();
    assertEquals(Origin.MAVEN, dependency.origin());
    assertEquals("gg.gemstone", dependency.group());
    assertEquals("component", dependency.name());
    assertEquals("1.0.1", dependency.version());
    assertEquals("jar", dependency.extension());
    assertNull(dependency.classifier());
    assertEquals("abc123", dependency.sha256());
    assertNull(dependency.embeddedResource());
  }

  @Test
  void ignoresBlankLinesAndComments() {
    LibraryManifest result = LibraryManifest.parse(manifest(
        "# a comment",
        "",
        "   ",
        "main-class\tcom.example.Proxy",
        "  # indented comment"));

    assertEquals("com.example.Proxy", result.mainClass());
    assertEquals(List.of(), result.repositories());
    assertEquals(List.of(), result.dependencies());
  }

  @Test
  void parsesEmbeddedDependencyWithResourcePath() {
    LibraryManifest result = LibraryManifest.parse(manifest(
        "main-class\tcom.example.Proxy",
        "dep\tEMBEDDED\tcom.velocityctd\tvelocity-proxy\t3.0.0\tjar\t-\tdeadbeef\tjar-in-jar/velocity-proxy-3.0.0.jar"));

    Dependency dependency = result.dependencies().getFirst();
    assertEquals(Origin.EMBEDDED, dependency.origin());
    assertEquals("jar-in-jar/velocity-proxy-3.0.0.jar", dependency.embeddedResource());
  }

  @Test
  void treatsDashClassifierAsNull() {
    LibraryManifest dash = LibraryManifest.parse(manifest(
        "main-class\tcom.example.Proxy",
        "dep\tMAVEN\tg\tn\t1\tjar\t-\thash"));
    assertNull(dash.dependencies().getFirst().classifier());

    LibraryManifest named = LibraryManifest.parse(manifest(
        "main-class\tcom.example.Proxy",
        "dep\tMAVEN\tg\tn\t1\tjar\tnatives\thash"));
    assertEquals("natives", named.dependencies().getFirst().classifier());
  }

  @Test
  void throwsOnUnknownEntry() {
    IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
        LibraryManifest.parse(manifest(
            "main-class\tcom.example.Proxy",
            "bogus\tvalue")));
    assertEquals("Unknown manifest entry: bogus\tvalue", exception.getMessage());
  }

  @Test
  void throwsWhenMainClassMissing() {
    assertThrows(IllegalStateException.class, () ->
        LibraryManifest.parse(manifest(
            "repo\thttps://repo.example/maven2")));
  }

  @Test
  void repositoriesAndDependenciesAreImmutable() {
    LibraryManifest result = LibraryManifest.parse(manifest(
        "main-class\tcom.example.Proxy",
        "repo\thttps://repo.example/maven2",
        "dep\tMAVEN\tg\tn\t1\tjar\t-\thash"));

    assertThrows(UnsupportedOperationException.class, () ->
        result.repositories().add("https://evil.example"));
    assertThrows(UnsupportedOperationException.class, () ->
        result.dependencies().clear());
  }
}
