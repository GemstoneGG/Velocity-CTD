/*
 * Copyright (C) 2018-2026 Velocity Contributors
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

package com.velocitypowered.proxy.plugin.loader.java;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.velocitypowered.api.plugin.InvalidPluginException;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.plugin.ap.SerializedPluginDescription;
import com.velocitypowered.api.plugin.meta.PluginDependency;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.plugin.PluginClassLoader;
import com.velocitypowered.proxy.plugin.loader.PluginLoader;
import com.velocitypowered.proxy.plugin.loader.VelocityPluginContainer;
import com.velocitypowered.proxy.plugin.loader.VelocityPluginDescription;
import java.io.BufferedInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * Implements loading a Java plugin.
 */
public class JavaPluginLoader implements PluginLoader {

  /**
   * The base directory used for plugin-specific storage.
   */
  private final Path baseDirectory;

  /**
   * Constructs a new Java plugin loader.
   *
   * @param ignoredServer  the proxy server instance (unused)
   * @param baseDirectory  the base directory for plugins
   */
  public JavaPluginLoader(final ProxyServer ignoredServer, final Path baseDirectory) {
    this.baseDirectory = baseDirectory;
  }

  /**
   * Attempts to load a plugin description from the given JAR path.
   *
   * <p>This method scans for a {@code velocity-plugin.json} entry in the JAR and validates
   * plugin ID and dependency syntax. If successful, it returns a {@link PluginDescription}.</p>
   *
   * @param source the path to the plugin JAR file
   * @return the parsed plugin description
   * @throws Exception if no valid plugin metadata is found or parsing fails
   */
  @Override
  public PluginDescription loadCandidate(final Path source) throws Exception {
    Optional<SerializedPluginDescription> serialized = getSerializedPluginInfo(source);

    if (serialized.isEmpty()) {
      throw new InvalidPluginException("Did not find a valid velocity-plugin.json.");
    }

    SerializedPluginDescription pd = serialized.get();
    if (!SerializedPluginDescription.ID_PATTERN.matcher(pd.getId()).matches()) {
      throw new InvalidPluginException("Plugin ID '" + pd.getId() + "' is invalid.");
    }

    for (SerializedPluginDescription.Dependency dependency : pd.getDependencies()) {
      if (!SerializedPluginDescription.ID_PATTERN.matcher(dependency.getId()).matches()) {
        throw new InvalidPluginException(
            "Dependency ID '" + dependency.getId() + "' for plugin '" + pd.getId() + "' is invalid."
        );
      }
    }

    return createCandidateDescription(pd, source);
  }

  /**
   * Loads and prepares a plugin instance based on a previously parsed candidate description.
   *
   * <p>This method creates a {@link PluginClassLoader}, loads the main class, and returns
   * a fully resolved {@link PluginDescription} containing a reference to the class.</p>
   *
   * @param candidate the candidate plugin metadata
   * @return the enriched plugin description with main class loaded
   * @throws Exception if the plugin could not be loaded or the main class is invalid
   */
  @Override
  public PluginDescription createPluginFromCandidate(final PluginDescription candidate) throws Exception {
    if (!(candidate instanceof JavaVelocityPluginDescriptionCandidate candidateInst)) {
      throw new IllegalArgumentException("Description provided isn't of the Java plugin loader");
    }

    URL pluginJarUrl = candidate.getSource().orElseThrow(
        () -> new InvalidPluginException("Description provided does not have a source path")).toUri().toURL();
    PluginClassLoader loader = new PluginClassLoader(new URL[]{pluginJarUrl});
    loader.addToClassloaders();

    Class<?> mainClass = loader.loadClass(candidateInst.getMainClass());
    return createDescription(candidateInst, mainClass);
  }

  /**
   * Creates a Guice {@link Module} for the given plugin, which binds core services
   * and plugin-specific components.
   *
   * <p>This module is later passed to Guice for constructing the plugin instance.</p>
   *
   * @param container the plugin container
   * @return a Guice module for plugin injection
   * @throws IllegalArgumentException if the container has no path or unsupported type
   */
  @Override
  public Module createModule(final PluginContainer container) {
    PluginDescription description = container.getDescription();
    if (!(description instanceof JavaVelocityPluginDescription javaDescription)) {
      throw new IllegalArgumentException("Description provided isn't of the Java plugin loader");
    }

    Optional<Path> source = javaDescription.getSource();
    if (source.isEmpty()) {
      throw new IllegalArgumentException("No path in plugin description");
    }

    return new VelocityPluginModule(javaDescription, container, baseDirectory);
  }

  /**
   * Constructs the plugin instance using the provided Guice modules and registers it.
   *
   * @param container the plugin container to populate
   * @param modules the Guice modules to use for injection
   * @throws IllegalStateException if no plugin instance is returned
   * @throws IllegalArgumentException if the container is of an unsupported type
   */
  @Override
  public void createPlugin(final PluginContainer container, final Module... modules) {
    if (!(container instanceof VelocityPluginContainer pluginContainer)) {
      throw new IllegalArgumentException("Container provided isn't of the Java plugin loader");
    }

    PluginDescription description = pluginContainer.getDescription();
    if (!(description instanceof JavaVelocityPluginDescription javaPluginDescription)) {
      throw new IllegalArgumentException("Description provided isn't of the Java plugin loader");
    }

    Injector injector = Guice.createInjector(modules);
    Object instance = injector.getInstance(javaPluginDescription.getMainClass());

    if (instance == null) {
      throw new IllegalStateException("Got nothing from injector for plugin " + description.getId());
    }

    pluginContainer.setInstance(instance);
  }

  private Optional<SerializedPluginDescription> getSerializedPluginInfo(final Path source)
      throws Exception {
    boolean foundBungeeBukkitPluginFile = false;
    try (JarInputStream in = new JarInputStream(new BufferedInputStream(Files.newInputStream(source)))) {
      JarEntry entry;
      while ((entry = in.getNextJarEntry()) != null) {
        switch (entry.getName()) {
          case "velocity-plugin.json" -> {
            try (Reader pluginInfoReader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
              return Optional.of(VelocityServer.GENERAL_GSON.fromJson(pluginInfoReader,
                  SerializedPluginDescription.class));
            }
          }
          case "paper-plugin.yml", "plugin.yml", "bungee.yml" -> foundBungeeBukkitPluginFile = true;
          default -> {
          }
        }
      }

      if (foundBungeeBukkitPluginFile) {
        throw new InvalidPluginException("The plugin file " + source.getFileName() + " appears to "
            + "be a Paper, Bukkit or BungeeCord plugin. Velocity does not support plugins from these "
            + "platforms.");
      }

      return Optional.empty();
    }
  }

  private VelocityPluginDescription createCandidateDescription(final SerializedPluginDescription description, final Path source) {
    Set<PluginDependency> dependencies = new HashSet<>();

    for (SerializedPluginDescription.Dependency dependency : description.getDependencies()) {
      dependencies.add(toDependencyMeta(dependency));
    }

    return new JavaVelocityPluginDescriptionCandidate(
        description.getId(),
        description.getName(),
        description.getVersion(),
        description.getDescription(),
        description.getUrl(),
        description.getAuthors(),
        dependencies,
        source,
        description.getMain()
    );
  }

  private VelocityPluginDescription createDescription(final JavaVelocityPluginDescriptionCandidate description, final Class<?> mainClass) {
    return new JavaVelocityPluginDescription(
        description.getId(),
        description.getName().orElse(null),
        description.getVersion().orElse(null),
        description.getDescription().orElse(null),
        description.getUrl().orElse(null),
        description.getAuthors(),
        description.getDependencies(),
        description.getSource().orElse(null),
        mainClass
    );
  }

  private static PluginDependency toDependencyMeta(final SerializedPluginDescription.Dependency dependency) {
    return new PluginDependency(
        dependency.getId(),
        null, // TODO Implement version matching in dependency annotation
        dependency.isOptional()
    );
  }
}
