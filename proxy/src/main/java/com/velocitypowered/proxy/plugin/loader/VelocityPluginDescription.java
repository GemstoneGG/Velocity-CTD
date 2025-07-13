/*
 * Copyright (C) 2018-2025 Velocity Contributors
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
  public VelocityPluginDescription(final String id, @Nullable final String name, @Nullable final String version,
                                   @Nullable final String description, @Nullable final String url,
                                   @Nullable final List<String> authors, final Collection<PluginDependency> dependencies, final Path source) {
    this.id = checkNotNull(id, "id");
    this.name = Strings.emptyToNull(name);
    this.version = Strings.emptyToNull(version);
    this.description = Strings.emptyToNull(description);
    this.url = Strings.emptyToNull(url);
    this.authors = authors == null ? ImmutableList.of() : ImmutableList.copyOf(authors);
    this.dependencies = Maps.uniqueIndex(dependencies, d -> d == null ? null : d.getId());
    this.source = source;
  }

  @Override
  public final String getId() {
    return id;
  }

  @Override
  public final Optional<String> getName() {
    return Optional.ofNullable(name);
  }

  @Override
  public final Optional<String> getVersion() {
    return Optional.ofNullable(version);
  }

  @Override
  public final Optional<String> getDescription() {
    return Optional.ofNullable(description);
  }

  @Override
  public final Optional<String> getUrl() {
    return Optional.ofNullable(url);
  }

  @Override
  public final List<String> getAuthors() {
    return authors;
  }

  @Override
  public final Collection<PluginDependency> getDependencies() {
    return dependencies.values();
  }

  @Override
  public final Optional<PluginDependency> getDependency(final String id) {
    return Optional.ofNullable(dependencies.get(id));
  }

  @Override
  public final Optional<Path> getSource() {
    return Optional.ofNullable(source);
  }

  @Override
  public final String toString() {
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
