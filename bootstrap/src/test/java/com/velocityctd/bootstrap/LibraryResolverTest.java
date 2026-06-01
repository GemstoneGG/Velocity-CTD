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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import com.velocityctd.bootstrap.Dependency.Origin;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryResolverTest {

  private final List<HttpServer> servers = new ArrayList<>();

  @AfterEach
  void stopServers() {
    for (HttpServer server : servers) {
      server.stop(0);
    }
  }

  private static String sha256(byte[] data) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    return HexFormat.of().formatHex(digest.digest(data));
  }

  private static Dependency mavenDependency(String sha256) {
    return new Dependency(Origin.MAVEN, "gg.gemstone", "component", "1.0.1", "jar", null, sha256, null);
  }

  private static Dependency embeddedDependency(String sha256, String resource) {
    return new Dependency(
        Origin.EMBEDDED, "com.velocityctd", "proxy", "1.0.1", "jar", null, sha256, resource);
  }

  private static LibraryManifest manifest(List<String> repositories, Dependency... dependencies) {
    return new LibraryManifest("com.example.Proxy", repositories, List.of(dependencies));
  }

  private static ClassLoader resourceLoader(Map<String, byte[]> resources) {
    return new ClassLoader(null) {
      @Override
      public InputStream getResourceAsStream(String name) {
        byte[] data = resources.get(name);
        return data == null ? null : new ByteArrayInputStream(data);
      }
    };
  }

  private String startServer(int status, byte[] body) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/", exchange -> {
      if (body == null) {
        exchange.sendResponseHeaders(status, -1);
      } else {
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
      }
      exchange.close();
    });
    server.start();
    servers.add(server);
    return "http://localhost:" + server.getAddress().getPort();
  }

  @Test
  void extractsEmbeddedDependency(@TempDir Path tempDir) throws Exception {
    byte[] jar = "embedded-jar-bytes".getBytes(StandardCharsets.UTF_8);
    String resource = "jar-in-jar/proxy-1.0.1.jar";
    Dependency dependency = embeddedDependency(sha256(jar), resource);
    LibraryResolver resolver = new LibraryResolver(
        manifest(List.of(), dependency), resourceLoader(Map.of(resource, jar)));

    List<Path> resolved = resolver.resolve(tempDir, false, false);

    assertEquals(1, resolved.size());
    Path jarFile = tempDir.resolve("com/velocityctd/proxy/1.0.1/proxy-1.0.1.jar");
    assertEquals(jarFile, resolved.getFirst());
    assertArrayEquals(jar, Files.readAllBytes(jarFile));
  }

  @Test
  void throwsWhenEmbeddedChecksumMismatches(@TempDir Path tempDir) {
    byte[] jar = "embedded-jar-bytes".getBytes(StandardCharsets.UTF_8);
    String resource = "jar-in-jar/proxy-1.0.1.jar";
    Dependency dependency = embeddedDependency("deadbeef", resource);
    LibraryResolver resolver = new LibraryResolver(
        manifest(List.of(), dependency), resourceLoader(Map.of(resource, jar)));

    IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
        resolver.resolve(tempDir, false, false));
    assertTrue(exception.getMessage().contains("Checksum mismatch"));
  }

  @Test
  void throwsWhenEmbeddedResourceMissing(@TempDir Path tempDir) throws Exception {
    byte[] jar = "embedded-jar-bytes".getBytes(StandardCharsets.UTF_8);
    Dependency dependency = embeddedDependency(sha256(jar), "jar-in-jar/missing.jar");
    LibraryResolver resolver = new LibraryResolver(
        manifest(List.of(), dependency), resourceLoader(Map.of()));

    IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
        resolver.resolve(tempDir, false, false));
    assertTrue(exception.getMessage().contains("Embedded resource missing"));
  }

  @Test
  void returnsCachedFileWithoutVerification(@TempDir Path tempDir) throws Exception {
    Dependency dependency = mavenDependency("does-not-matter");
    Path cached = tempDir.resolve(dependency.relativePath());
    Files.createDirectories(cached.getParent());
    byte[] original = "cached".getBytes(StandardCharsets.UTF_8);
    Files.write(cached, original);

    // No repositories are configured, so any attempt to download would fail.
    LibraryResolver resolver = new LibraryResolver(
        manifest(List.of(), dependency), resourceLoader(Map.of()));

    List<Path> resolved = resolver.resolve(tempDir, false, false);

    assertEquals(cached, resolved.getFirst());
    assertArrayEquals(original, Files.readAllBytes(cached));
  }

  @Test
  void returnsCachedFileWhenChecksumMatches(@TempDir Path tempDir) throws Exception {
    byte[] content = "cached".getBytes(StandardCharsets.UTF_8);
    Dependency dependency = mavenDependency(sha256(content));
    Path cached = tempDir.resolve(dependency.relativePath());
    Files.createDirectories(cached.getParent());
    Files.write(cached, content);

    LibraryResolver resolver = new LibraryResolver(
        manifest(List.of(), dependency), resourceLoader(Map.of()));

    List<Path> resolved = resolver.resolve(tempDir, true, false);

    assertEquals(cached, resolved.getFirst());
  }

  @Test
  void downloadsAndVerifiesFromRepository(@TempDir Path tempDir) throws Exception {
    byte[] jar = "downloaded-jar".getBytes(StandardCharsets.UTF_8);
    Dependency dependency = mavenDependency(sha256(jar));
    String base = startServer(200, jar);
    LibraryResolver resolver = new LibraryResolver(
        manifest(List.of(base), dependency), resourceLoader(Map.of()));

    List<Path> resolved = resolver.resolve(tempDir, true, false);

    assertArrayEquals(jar, Files.readAllBytes(resolved.getFirst()));
  }

  @Test
  void fallsThroughToNextRepositoryWhenArtifactNotFound(@TempDir Path tempDir) throws Exception {
    byte[] jar = "downloaded-jar".getBytes(StandardCharsets.UTF_8);
    Dependency dependency = mavenDependency(sha256(jar));
    String missing = startServer(404, null);
    String present = startServer(200, jar);
    LibraryResolver resolver = new LibraryResolver(
        manifest(List.of(missing, present), dependency), resourceLoader(Map.of()));

    List<Path> resolved = resolver.resolve(tempDir, true, false);

    assertArrayEquals(jar, Files.readAllBytes(resolved.getFirst()));
  }

  @Test
  void fallsThroughToNextRepositoryWhenChecksumMismatches(@TempDir Path tempDir) throws Exception {
    byte[] correct = "the-real-jar".getBytes(StandardCharsets.UTF_8);
    byte[] tampered = "a-different-jar".getBytes(StandardCharsets.UTF_8);
    Dependency dependency = mavenDependency(sha256(correct));
    String wrong = startServer(200, tampered);
    String right = startServer(200, correct);
    LibraryResolver resolver = new LibraryResolver(
        manifest(List.of(wrong, right), dependency), resourceLoader(Map.of()));

    List<Path> resolved = resolver.resolve(tempDir, true, false);

    assertArrayEquals(correct, Files.readAllBytes(resolved.getFirst()));
  }

  @Test
  void throwsWhenArtifactMissingFromEveryRepository(@TempDir Path tempDir) throws Exception {
    Dependency dependency = mavenDependency("any-hash");
    String missing = startServer(404, null);
    LibraryResolver resolver = new LibraryResolver(
        manifest(List.of(missing), dependency), resourceLoader(Map.of()));

    IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
        resolver.resolve(tempDir, true, false));
    assertTrue(exception.getMessage().contains("Could not download"));
    assertTrue(exception.getMessage().contains("Tried:"));
  }

  @Test
  void resolvesMultipleEmbeddedDependenciesInParallel(@TempDir Path tempDir) throws Exception {
    byte[] firstJar = "first".getBytes(StandardCharsets.UTF_8);
    byte[] secondJar = "second".getBytes(StandardCharsets.UTF_8);
    Dependency first = new Dependency(
        Origin.EMBEDDED, "com.velocityctd", "first", "1.0.0", "jar", null, sha256(firstJar),
        "jar-in-jar/first.jar");
    Dependency second = new Dependency(
        Origin.EMBEDDED, "com.velocityctd", "second", "1.0.0", "jar", null, sha256(secondJar),
        "jar-in-jar/second.jar");
    LibraryResolver resolver = new LibraryResolver(
        manifest(List.of(), first, second),
        resourceLoader(Map.of(
            "jar-in-jar/first.jar", firstJar,
            "jar-in-jar/second.jar", secondJar)));

    List<Path> resolved = resolver.resolve(tempDir, false, true);

    assertEquals(2, resolved.size());
    assertArrayEquals(firstJar, Files.readAllBytes(resolved.get(0)));
    assertArrayEquals(secondJar, Files.readAllBytes(resolved.get(1)));
  }
}
