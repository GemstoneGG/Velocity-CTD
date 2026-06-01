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

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Entry point for the thin bootstrap distribution. It reads the embedded library manifest, ensures
 * every dependency is present in the local libraries directory (downloading or unpacking as
 * needed), then hands control to the real proxy main class on a class loader assembled from those
 * libraries.
 */
public final class BootstrapMain {

  private static final String TRACE_PROPERTY = "velocityctd.bootstrap.trace";
  private static final String LIBRARIES_PROPERTY = "velocityctd.bootstrap.libraries";
  private static final String VERIFY_PROPERTY = "velocityctd.bootstrap.verify";
  private static final String CLEAN_PROPERTY = "velocityctd.bootstrap.clean";
  private static final String PARALLEL_PROPERTY = "velocityctd.bootstrap.parallel";

  private static final String MANIFEST_RESOURCE = "META-INF/velocityctd/libraries.list";

  private static final String DEFAULT_LIBRARIES_DIR = "libraries";

  private BootstrapMain() {
    throw new IllegalStateException("BootstrapMain should not be instantiated.");
  }

  /**
   * Resolves all libraries and launches the proxy.
   *
   * @param args the command line arguments forwarded to the proxy
   * @throws Exception if the proxy cannot be resolved or launched
   */
  public static void main(String... args) throws Exception {
    boolean trace = Boolean.parseBoolean(System.getProperty(TRACE_PROPERTY, "false"));
    BootstrapLogger.setTrace(trace);

    ClassLoader bootstrapLoader = BootstrapMain.class.getClassLoader();
    LibraryManifest manifest = loadManifest(bootstrapLoader);

    Path librariesDir = Path.of(System.getProperty(LIBRARIES_PROPERTY, DEFAULT_LIBRARIES_DIR)).toAbsolutePath();
    boolean verify = Boolean.parseBoolean(System.getProperty(VERIFY_PROPERTY, "true"));
    boolean clean = Boolean.parseBoolean(System.getProperty(CLEAN_PROPERTY, "false"));
    boolean parallel = Boolean.parseBoolean(System.getProperty(PARALLEL_PROPERTY, "true"));

    BootstrapLogger.trace("Found " + manifest.dependencies().size() + " libraries.");
    if (clean) {
      BootstrapLogger.trace("Cleaning unused libraries...");
      int removed = new LibraryCleaner(manifest).clean(librariesDir);
      if (removed > 0) {
        BootstrapLogger.info("Cleaned up " + removed + " unused " + (removed == 1 ? "library" : "libraries") + ".");
      } else {
        BootstrapLogger.trace("No unused libraries to remove.");
      }
    }

    if (verify) {
      BootstrapLogger.info("Resolving and verifying libraries...");
    } else {
      BootstrapLogger.info("Resolving libraries...");
    }

    long startTime = System.nanoTime();
    List<Path> jars;
    try {
      jars = new LibraryResolver(manifest, bootstrapLoader).resolve(librariesDir, verify, parallel);
    } catch (Exception e) {
      BootstrapLogger.error("Failed to resolve the libraries required to start the proxy: " + e.getMessage());

      // Advise to download fat jar on repeated failure. Should be removed if/when we don't ship the fat jar anymore.
      BootstrapLogger.error("If this keeps happening (e.g. behind a firewall or while a repository "
          + "is down), download the self-contained fat jar from https://github.com/GemstoneGG/Velocity-CTD/releases/latest "
          + "instead.");

      throw e;
    }
    double resolveTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime) / 1000d;

    BootstrapLogger.trace("Done (" + new DecimalFormat("#.##").format(resolveTime) + "s).");
    BootstrapLogger.info("Starting proxy.");
    launchProxy(manifest.mainClass(), jars, args);
  }

  private static LibraryManifest loadManifest(ClassLoader loader) throws Exception {
    try (InputStream input = loader.getResourceAsStream(MANIFEST_RESOURCE)) {
      if (input == null) {
        throw new IllegalStateException("Bootstrap jar is missing its library manifest (" + MANIFEST_RESOURCE + ")");
      }
      return LibraryManifest.parse(input);
    }
  }

  private static void launchProxy(String mainClassName, List<Path> jars, String[] args) throws Exception {
    DependencyClassLoader loader = DependencyClassLoader.create(jars);
    Thread.currentThread().setContextClassLoader(loader);
    Class<?> mainClass = Class.forName(mainClassName, true, loader);
    Method mainMethod = mainClass.getMethod("main", String[].class);
    try {
      mainMethod.invoke(null, (Object) args);
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof Exception checked) {
        throw checked;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw exception;
    }
  }
}
