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

import com.velocityctd.bootstrap.Dependency.Origin;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Resolves every {@link Dependency} in a {@link LibraryManifest} into a local jar file, downloading
 * Maven artifacts or extracting embedded jar-in-jar resources as needed, and verifying SHA-256
 * checksums.
 */
public final class LibraryResolver {

  private static final int MAX_PARALLEL_DOWNLOADS = 8;

  private static final int MAX_DOWNLOAD_ATTEMPTS = 3;

  private static final Duration INITIAL_BACKOFF = Duration.ofMillis(500);
  private static final Duration MAX_BACKOFF = Duration.ofSeconds(5);

  private final LibraryManifest manifest;
  private final ClassLoader resourceLoader;
  private final HttpClient httpClient;

  /**
   * Creates a resolver for the given manifest.
   *
   * @param manifest the manifest describing the required dependencies and repositories
   * @param resourceLoader the class loader used to read embedded jar-in-jar resources
   */
  public LibraryResolver(LibraryManifest manifest, ClassLoader resourceLoader) {
    this.manifest = manifest;
    this.resourceLoader = resourceLoader;
    this.httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(30))
        .build();
  }

  /**
   * Ensures every dependency is present and valid in the libraries directory.
   *
   * @param librariesDir the directory that mirrors a Maven repository layout
   * @param verify whether to re-verify the checksum of already-cached files
   * @param parallel whether to resolve dependencies concurrently
   * @return the resolved jar paths, in manifest order
   */
  public List<Path> resolve(Path librariesDir, boolean verify, boolean parallel) {
    List<Dependency> dependencies = manifest.dependencies();
    if (!parallel || dependencies.size() <= 1) {
      List<Path> resolved = new ArrayList<>(dependencies.size());
      for (Dependency dependency : dependencies) {
        resolved.add(ensure(dependency, librariesDir, verify));
      }
      return resolved;
    }

    return resolveParallel(dependencies, librariesDir, verify);
  }

  private List<Path> resolveParallel(List<Dependency> dependencies, Path librariesDir, boolean verify) {
    int threads = Math.min(dependencies.size(), MAX_PARALLEL_DOWNLOADS);
    BootstrapLogger.trace("Resolving " + dependencies.size() + " libraries using " + threads + " threads.");

    ExecutorService executor = Executors.newFixedThreadPool(threads, runnable -> {
      Thread thread = new Thread(runnable, "velocityctd-bootstrap-resolver");
      thread.setDaemon(true);
      return thread;
    });

    try {
      List<Future<Path>> futures = new ArrayList<>(dependencies.size());
      for (Dependency dependency : dependencies) {
        futures.add(executor.submit(() -> ensure(dependency, librariesDir, verify)));
      }

      List<Path> resolved = new ArrayList<>(dependencies.size());
      for (Future<Path> future : futures) {
        resolved.add(awaitResult(future));
      }
      return resolved;
    } finally {
      executor.shutdownNow();
    }
  }

  private static Path awaitResult(Future<Path> future) {
    try {
      return future.get();
    } catch (ExecutionException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof RuntimeException runtime) {
        throw runtime;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new IllegalStateException("Failed to resolve library", cause);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while resolving libraries", exception);
    }
  }

  private Path ensure(Dependency dependency, Path librariesDir, boolean verify) {
    // Always verify embedded jars (re-extract on hash mismatch).
    verify |= dependency.origin() == Origin.EMBEDDED;

    Path target = librariesDir.resolve(dependency.relativePath());
    if (Files.isRegularFile(target) && (!verify || checksumMatches(target, dependency.sha256()))) {
      return target;
    }

    try {
      Files.createDirectories(target.getParent());
    } catch (IOException exception) {
      throw new IllegalStateException("Could not create directory for " + target, exception);
    }

    if (dependency.origin() == Origin.EMBEDDED) {
      extractEmbedded(dependency, target);
    } else {
      download(dependency, target);
    }
    return target;
  }

  private void extractEmbedded(Dependency dependency, Path target) {
    String resource = dependency.embeddedResource();
    BootstrapLogger.trace("Unpacking " + describe(dependency));
    try (InputStream input = resourceLoader.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalStateException("Embedded resource missing from bootstrap jar: " + resource);
      }

      Path temp = Files.createTempFile(target.getParent(), ".unpack", ".tmp");
      try {
        Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
        verifyOrThrow(temp, dependency);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
      } finally {
        Files.deleteIfExists(temp);
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to unpack embedded library " + resource, exception);
    }
  }

  private void download(Dependency dependency, Path target) {
    List<String> attempted = new ArrayList<>();
    for (String repository : manifest.repositories()) {
      String base = repository.endsWith("/") ? repository : repository + "/";
      String url = base + dependency.relativePath();
      attempted.add(url);
      if (downloadFrom(url, dependency, target)) {
        return;
      }
    }

    throw new IllegalStateException("Could not download " + describe(dependency) + " from any repository. Tried: " + attempted);
  }

  /**
   * Attempts to download and verify an artifact from a single repository, retrying transient
   * failures (connection errors, read timeouts, 408/429/5xx responses) with exponential backoff.
   *
   * @return {@code true} if the artifact was downloaded and moved into place; {@code false} if it
   *         is absent or invalid here (a 4xx, a checksum mismatch, or exhausted transient retries) and
   *         the caller should fall through to the next repository
   */
  private boolean downloadFrom(String url, Dependency dependency, Path target) {
    for (int attempt = 1; ; attempt++) {
      try {
        Path temp = Files.createTempFile(target.getParent(), ".download", ".tmp");
        try {
          var response = httpClient.send(
              HttpRequest.newBuilder(URI.create(url))
                  .timeout(Duration.ofSeconds(300))
                  .GET()
                  .build(),
              BodyHandlers.ofFile(temp));
          int status = response.statusCode();
          if (status != 200) {
            if (isTransientStatus(status) && attempt < MAX_DOWNLOAD_ATTEMPTS) {
              BootstrapLogger.warn("HTTP " + status + " from " + url + " (attempt " + attempt + "/"
                  + MAX_DOWNLOAD_ATTEMPTS + "). Retrying.");
              backoff(attempt);
              continue;
            }
            return false;
          }

          if (!checksumMatches(temp, dependency.sha256())) {
            BootstrapLogger.warn("Checksum mismatch for " + describe(dependency) + " from " + url
                + " (expected " + dependency.sha256() + "). Trying next repository.");
            return false;
          }

          Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
          BootstrapLogger.trace("Downloaded " + describe(dependency));

          return true;
        } finally {
          Files.deleteIfExists(temp);
        }
      } catch (IOException exception) {
        if (attempt < MAX_DOWNLOAD_ATTEMPTS) {
          BootstrapLogger.warn("Failed downloading from " + url + ": " + exception.getMessage()
              + " (attempt " + attempt + "/" + MAX_DOWNLOAD_ATTEMPTS + "). Retrying.");
          backoff(attempt);
          continue;
        }
        BootstrapLogger.warn("Failed downloading from " + url + ": " + exception.getMessage()
            + " (gave up after " + MAX_DOWNLOAD_ATTEMPTS + " attempts). Trying next repository.");
        return false;
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while downloading " + url, exception);
      }
    }
  }

  private static boolean isTransientStatus(int status) {
    return status == 408 || status == 429 || status >= 500;
  }

  private static void backoff(int attempt) {
    long cap = Math.min(
        INITIAL_BACKOFF.toMillis() << (attempt - 1),
        MAX_BACKOFF.toMillis());
    long half = cap / 2;
    long millis = half + ThreadLocalRandom.current().nextLong(half + 1);
    try {
      Thread.sleep(millis);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while backing off before retry", exception);
    }
  }

  private void verifyOrThrow(Path file, Dependency dependency) {
    if (!checksumMatches(file, dependency.sha256())) {
      throw new IllegalStateException("Checksum mismatch for " + describe(dependency) + " (expected " + dependency.sha256() + ")");
    }
  }

  private static boolean checksumMatches(Path file, String expectedSha256) {
    return sha256Hex(file).equalsIgnoreCase(expectedSha256);
  }

  private static String sha256Hex(Path file) {
    try (InputStream input = Files.newInputStream(file)) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[8192];
      int read;
      while ((read = input.read(buffer)) != -1) {
        digest.update(buffer, 0, read);
      }

      return HexFormat.of().formatHex(digest.digest());
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to hash " + file, exception);
    }
  }

  private static String describe(Dependency dependency) {
    String classifierSuffix = dependency.classifier() == null ? "" : ":" + dependency.classifier();
    return dependency.group() + ':' + dependency.name() + ':' + dependency.version() + classifierSuffix;
  }
}
