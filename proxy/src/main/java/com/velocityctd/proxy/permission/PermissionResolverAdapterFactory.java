/*
 * Copyright (C) 2018-2026 Velocity-CTD Contributors
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

package com.velocityctd.proxy.permission;

import com.velocityctd.api.permission.PermissionResolver;
import com.velocityctd.api.permission.PermissionResolverFunctionAdapter;
import com.velocityctd.api.permission.PermissionResolverProvider;
import com.velocitypowered.api.permission.PermissionFunction;
import com.velocitypowered.api.permission.PermissionSubject;
import com.velocitypowered.proxy.plugin.PluginClassLoader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.ServiceLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Factory for producing an {@link PermissionResolver} for a given {@link PermissionSubject}.
 *
 * <p>This factory supports an optional, runtime-discovered integration that is embedded inside the
 * proxy jar (as a nested jar resource). When present, the embedded integration jar is extracted to
 * a temporary file and loaded via a dedicated {@link PluginClassLoader}. The implementation is then
 * discovered using {@link ServiceLoader} by locating an {@link PermissionResolverProvider}.
 *
 * <p>If no provider can be loaded (e.g., the embedded jar is missing, cannot be extracted, or the
 * provider reports it is unavailable), this factory falls back to {@link PermissionResolverFunctionAdapter}.
 *
 * <p>The provider lookup and jar extraction are performed at most once per JVM. The result is cached
 * (including the "no provider available" outcome) to avoid repeated I/O and class loading overhead.
 */
public final class PermissionResolverAdapterFactory {

  private static final Logger LOGGER = LogManager.getLogger(PermissionResolverAdapterFactory.class);

  private static final String[] INTEGRATION_RESOURCES = new String[] {
      "META-INF/velocityctd/integrations/velocity-luckperms-integration.jar"
  };

  private static volatile boolean hasLoadedProvider = false;
  private static volatile @Nullable PermissionResolverProvider loadedProvider = null;

  private PermissionResolverAdapterFactory() {
  }

  /**
   * Creates an {@link PermissionResolver} for the supplied {@link PermissionSubject}.
   *
   * <p>If an {@link PermissionResolverProvider} is available via the embedded integration,
   * this method delegates to {@link PermissionResolverProvider#createResolver(PermissionSubject, PermissionFunction)}.
   * Otherwise, it returns a {@link PermissionResolverFunctionAdapter} wrapping {@code delegate}.
   *
   * @param permissionSubject the subject the resolver will evaluate permissions for
   * @param delegate the base permission function to delegate to when the permission resolver is not available.
   *                 this must not be a {@link PermissionResolver}, the caller should confirm this beforehand.
   * @return a permission resolver when an integration is available; otherwise a simple adapter that adapts a permission function
   */
  public static PermissionResolver createPermissionResolverAdapter(
      PermissionSubject permissionSubject,
      PermissionFunction delegate
  ) {
    if (delegate instanceof PermissionResolver) {
      throw new IllegalArgumentException("delegate should not be a PermissionResolver.");
    }

    return getLoadedProvider()
        .map(provider -> provider.createResolver(permissionSubject, delegate))
        .orElseGet(() -> new PermissionResolverFunctionAdapter(delegate));
  }

  private static Optional<PermissionResolverProvider> getLoadedProvider() {
    if (hasLoadedProvider) {
      return Optional.ofNullable(loadedProvider);
    }

    synchronized (PermissionResolverAdapterFactory.class) {
      // Check again in lock
      if (hasLoadedProvider) {
        return Optional.ofNullable(loadedProvider);
      }

      loadedProvider = loadProviderOnce().orElse(null);
      hasLoadedProvider = true;

      return Optional.ofNullable(loadedProvider);
    }
  }

  private static Optional<PermissionResolverProvider> loadProviderOnce() {
    for (String integrationResource : INTEGRATION_RESOURCES) {
      Optional<PermissionResolverProvider> provider = tryLoadProvider(integrationResource);
      if (provider.isPresent()) {
        return provider;
      }
    }

    return Optional.empty();
  }

  private static Optional<PermissionResolverProvider> tryLoadProvider(String resourceName) {
    Path jarPath = extractEmbeddedJarToTempFile(resourceName);
    if (jarPath == null) {
      return Optional.empty();
    }

    URL jarUrl;
    try {
      jarUrl = jarPath.toUri().toURL();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    PluginClassLoader integrationLoader = new PluginClassLoader(new URL[] {jarUrl});
    PermissionResolverProvider provider = ServiceLoader.load(PermissionResolverProvider.class, integrationLoader)
        .stream()
        .map(ServiceLoader.Provider::get)
        .filter(PermissionResolverProvider::isAvailable)
        .findFirst()
        .orElse(null);

    if (provider == null) {
      // The jar didn't provide a valid provider, or the provider isn't available. Close the class loader and delete the file.
      try {
        integrationLoader.close();
      } catch (IOException e) {
        LOGGER.error("Could not close class loader for {}. Resource will be kept "
            + "open until proxy shutdown.", jarPath.getFileName(), e);
        return Optional.empty();
      }

      try {
        Files.delete(jarPath);
      } catch (IOException e) {
        LOGGER.error("Could not delete temporary file {}.", jarPath.getFileName(), e);
      }

      return Optional.empty();
    }

    return Optional.of(provider);
  }

  private static Path extractEmbeddedJarToTempFile(String resourcePath) {
    ClassLoader cl = PermissionResolverAdapterFactory.class.getClassLoader();
    if (cl == null) {
      cl = ClassLoader.getSystemClassLoader();
    }

    try (InputStream in = cl.getResourceAsStream(resourcePath)) {
      if (in == null) {
        return null;
      }

      Path tmp = Files.createTempFile("velocity-integration-", ".jar");
      tmp.toFile().deleteOnExit();

      Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
      return tmp;
    } catch (IOException e) {
      return null;
    }
  }
}
