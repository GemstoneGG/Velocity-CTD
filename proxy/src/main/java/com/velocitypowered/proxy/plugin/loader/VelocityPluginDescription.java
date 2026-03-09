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

package com.velocitypowered.proxy.plugin.loader;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.plugin.meta.PluginDependency;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Implements {@link PluginDescription}.
 */
public class VelocityPluginDescription implements PluginDescription {

  /**
   * The plugin's unique ID.
   */
  private final String id;

  /**
   * The human-readable name of the plugin.
   */
  private final @Nullable String name;

  /**
   * The plugin's version.
   */
  private final @Nullable String version;

  /**
   * A short description of the plugin.
   */
  private final @Nullable String description;

  /**
   * The plugin's website or documentation URL.
   */
  private final @Nullable String url;

  /**
   * The list of plugin authors.
   */
  private final List<String> authors;

  /**
   * The plugin's declared dependencies, indexed by their ID.
   */
  private final Map<String, PluginDependency> dependencies;

  /**
   * The path to the source file the plugin was loaded from.
   */
  private final Path source;

  /**
   * Creates a new plugin description.
   *
   * @param id           the ID
   * @param name         the name of the plugin
   * @param version      the plugin version
   * @param description  a description of the plugin
   * @param url          the website for the plugin
   * @param authors      the authors of this plugin
   * @param dependencies the dependencies for this plugin
   * @param source       the original source for the plugin
   */
  public VelocityPluginDescription(final String id, final @Nullable String name, final @Nullable String version,
                                   final @Nullable String description, final @Nullable String url,
                                   final @Nullable List<String> authors, final Collection<PluginDependency> dependencies, final Path source) {
    this.id = checkNotNull(id, "id");
    this.name = Strings.emptyToNull(name);
    this.version = Strings.emptyToNull(version);
    this.description = Strings.emptyToNull(description);
    this.url = Strings.emptyToNull(url);
    this.authors = authors == null ? ImmutableList.of() : ImmutableList.copyOf(authors);
    this.dependencies = Maps.uniqueIndex(dependencies, d -> d == null ? null : d.getId());
    this.source = source;
  }

  /**
   * Returns the unique identifier of the plugin.
   *
   * @return the plugin ID
   */
  @Override
  public String getId() {
    return id;
  }

  /**
   * Returns the display name of the plugin, if provided.
   *
   * @return an {@link Optional} containing the plugin name
   */
  @Override
  public Optional<String> getName() {
    return Optional.ofNullable(name);
  }

  /**
   * Returns the version of the plugin, if declared.
   *
   * @return an {@link Optional} containing the plugin version
   */
  @Override
  public Optional<String> getVersion() {
    return Optional.ofNullable(version);
  }

  /**
   * Returns the plugin's short description, if present.
   *
   * @return an {@link Optional} containing the description
   */
  @Override
  public Optional<String> getDescription() {
    return Optional.ofNullable(description);
  }

  /**
   * Returns the URL associated with the plugin, such as documentation or homepage.
   *
   * @return an {@link Optional} containing the plugin's URL
   */
  @Override
  public Optional<String> getUrl() {
    return Optional.ofNullable(url);
  }

  /**
   * Returns an immutable list of plugin authors.
   *
   * @return the list of author names
   */
  @Override
  public List<String> getAuthors() {
    return authors;
  }

  /**
   * Returns all declared dependencies of the plugin.
   *
   * @return a collection of {@link PluginDependency} objects
   */
  @Override
  public Collection<PluginDependency> getDependencies() {
    return dependencies.values();
  }

  /**
   * Retrieves a declared dependency by its plugin ID.
   *
   * @param id the plugin ID of the dependency
   * @return an {@link Optional} containing the dependency, if declared
   */
  @Override
  public Optional<PluginDependency> getDependency(final String id) {
    return Optional.ofNullable(dependencies.get(id));
  }

  /**
   * Returns the path to the JAR file the plugin was loaded from.
   *
   * @return an {@link Optional} containing the source file path
   */
  @Override
  public Optional<Path> getSource() {
    return Optional.ofNullable(source);
  }

  /**
   * Returns a string representation of the plugin description for debugging.
   *
   * @return a string containing all plugin metadata fields
   */
  @Override
  public String toString() {
    return "VelocityPluginDescription{"
        + "proxyId='" + id + '\''
        + ", name='" + name + '\''
        + ", version='" + version + '\''
        + ", description='" + description + '\''
        + ", url='" + url + '\''
        + ", authors=" + authors
        + ", dependencies=" + dependencies
        + ", source=" + source
        + '}';
  }
}
