/*
 * Copyright (C) 2018-2023 Velocity Contributors
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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.velocitypowered.api.plugin.InvalidPluginException;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.plugin.ap.SerializedPluginDescription;
import com.velocitypowered.api.plugin.meta.PluginDependency;
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
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Implements loading a Java plugin.
 */
public class JavaPluginLoader implements PluginLoader {

  private final Path baseDirectory;

  public JavaPluginLoader(VelocityServer ignoredServer, Path baseDirectory) {
    this.baseDirectory = baseDirectory;
  }

  @Override
  public PluginDescription loadCandidate(Path source) throws Exception {
    Optional<SerializedPluginDescription> serialized = getSerializedPluginInfo(source);

    if (serialized.isEmpty()) {
      throw new InvalidPluginException("Did not find a valid velocity-plugin.json.");
    }

    SerializedPluginDescription pd = serialized.get();
    for (SerializedPluginDescription.Dependency dependency : pd.dependencies()) {
      if (!SerializedPluginDescription.ID_PATTERN.matcher(dependency.id()).matches()) {
        throw new InvalidPluginException(
            "Dependency ID '" + dependency.id() + "' for plugin '" + pd.id() + "' is invalid."
        );
      }
    }

    for (String providedId : pd.provides()) {
      if (!SerializedPluginDescription.ID_PATTERN.matcher(providedId).matches()) {
        throw new InvalidPluginException(
            "Provided ID '" + providedId + "' for plugin '" + pd.id() + "' is invalid."
        );
      }
    }

    return createCandidateDescription(pd, source);
  }

  @Override
  public PluginDescription createPluginFromCandidate(PluginDescription candidate) throws Exception {
    if (!(candidate instanceof JavaVelocityPluginDescriptionCandidate candidateInst)) {
      throw new IllegalArgumentException("Description provided isn't of the Java plugin loader");
    }

    URL pluginJarUrl = candidate.getSource().orElseThrow(
        () -> new InvalidPluginException("Description provided does not have a source path")
    ).toUri().toURL();
    PluginClassLoader loader = new PluginClassLoader(new URL[]{pluginJarUrl});
    loader.addToClassloaders();

    Class<?> mainClass = loader.loadClass(candidateInst.getMainClass());
    return createDescription(candidateInst, mainClass);
  }

  @Override
  public Module createModule(PluginContainer container) {
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

  @Override
  public void createPlugin(PluginContainer container, Module... modules) {
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
      throw new IllegalStateException(
          "Got nothing from injector for plugin " + description.getId());
    }

    pluginContainer.setInstance(instance);
  }

  /**
   * Reads a plugin's description, refusing one whose ID is missing or invalid, or that names no
   * main class.
   *
   * <p>The description is a record, so Gson builds it through its constructor, which requires
   * those same things; a file breaking one would otherwise fail inside Gson and reach the plugin's
   * author as a constructor failure wrapped in Gson's stack trace. The file is checked as JSON
   * first, so the refusal names what is wrong.
   *
   * @param reader the {@code velocity-plugin.json} contents
   * @return the description, or {@code null} when the file is empty
   * @throws InvalidPluginException if the ID is missing or invalid, or the main class is missing
   */
  private static @Nullable SerializedPluginDescription readDescription(Reader reader)
      throws InvalidPluginException {
    JsonObject json = VelocityServer.GENERAL_GSON.fromJson(reader, JsonObject.class);

    if (json == null) {
      return null;
    }

    JsonElement id = requirePresent(json, "id", "No plugin ID provided.");
    requirePresent(json, "main", "No plugin main class provided.");

    if (!id.isJsonPrimitive()
        || !SerializedPluginDescription.ID_PATTERN.matcher(id.getAsString()).matches()) {
      throw new InvalidPluginException("Plugin ID '" + readable(id) + "' is invalid.");
    }

    return VelocityServer.GENERAL_GSON.fromJson(json, SerializedPluginDescription.class);
  }

  private static JsonElement requirePresent(JsonObject json, String key, String message)
      throws InvalidPluginException {
    JsonElement value = json.get(key);

    if (value == null || value.isJsonNull()) {
      throw new InvalidPluginException(message);
    }

    return value;
  }

  private static String readable(JsonElement value) {
    return value.isJsonPrimitive() ? value.getAsString() : value.toString();
  }

  private Optional<SerializedPluginDescription> getSerializedPluginInfo(Path source)
      throws Exception {
    boolean foundBungeeBukkitPluginFile = false;
    try (JarInputStream in = new JarInputStream(
        new BufferedInputStream(Files.newInputStream(source)))) {
      JarEntry entry;
      while ((entry = in.getNextJarEntry()) != null) {
        switch (entry.getName()) {
          case "velocity-plugin.json" -> {
            try (Reader pluginInfoReader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
              return Optional.ofNullable(readDescription(pluginInfoReader));
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

  private VelocityPluginDescription createCandidateDescription(
      SerializedPluginDescription description,
      Path source) {
    Set<PluginDependency> dependencies = new HashSet<>();

    for (SerializedPluginDescription.Dependency dependency : description.dependencies()) {
      dependencies.add(toDependencyMeta(dependency));
    }

    return new JavaVelocityPluginDescriptionCandidate(
        description.id(),
        description.name(),
        description.version(),
        description.description(),
        description.url(),
        description.authors(),
        dependencies,
        description.provides(),
        source,
        description.main()
    );
  }

  private VelocityPluginDescription createDescription(
      JavaVelocityPluginDescriptionCandidate description,
      Class<?> mainClass) {
    return new JavaVelocityPluginDescription(
        description.getId(),
        description.getName().orElse(null),
        description.getVersion().orElse(null),
        description.getDescription().orElse(null),
        description.getUrl().orElse(null),
        description.getAuthors(),
        description.getDependencies(),
        description.getProvidedIds(),
        description.getSource().orElse(null),
        mainClass
    );
  }

  private static PluginDependency toDependencyMeta(
      SerializedPluginDescription.Dependency dependency) {
    return new PluginDependency(
        dependency.id(),
        null, // TODO Implement version matching in dependency annotation
        dependency.optional()
    );
  }
}
