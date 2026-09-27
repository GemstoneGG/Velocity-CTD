/*
 * Copyright (C) 2018-2023 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.plugin.ap;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.velocitypowered.api.plugin.Plugin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Serialized version of {@link com.velocitypowered.api.plugin.PluginDescription}.
 *
 * <p>A value the plugin leaves out is held as {@code null}, which Gson leaves out of the
 * serialized file.
 *
 * @param id the plugin ID
 * @param name the display name, or {@code null} for none
 * @param version the version, or {@code null} for none
 * @param description the description, or {@code null} for none
 * @param url the website, or {@code null} for none
 * @param authors the authors
 * @param dependencies the plugins this one depends on
 * @param provides the other IDs this plugin claims
 * @param main the fully qualified name of the main class
 */
public record SerializedPluginDescription(String id, @Nullable String name,
    @Nullable String version, @Nullable String description, @Nullable String url,
    @Nullable List<String> authors, @Nullable List<Dependency> dependencies,
    @Nullable List<String> provides, String main) {

  public static final String ID_PATTERN_STRING = "[a-z][a-z0-9-_]{0,63}";
  public static final Pattern ID_PATTERN = Pattern.compile(ID_PATTERN_STRING);

  /**
   * Checks the ID and the main class, and settles what is absent: empty text becomes
   * {@code null}, and a missing or empty list becomes an empty one.
   */
  public SerializedPluginDescription {
    Preconditions.checkNotNull(id, "id");
    Preconditions.checkArgument(ID_PATTERN.matcher(id).matches(), "id is not valid");
    Preconditions.checkNotNull(main, "main");
    name = Strings.emptyToNull(name);
    version = Strings.emptyToNull(version);
    description = Strings.emptyToNull(description);
    url = Strings.emptyToNull(url);
    authors = authors == null || authors.isEmpty() ? ImmutableList.of() : authors;
    dependencies =
        dependencies == null || dependencies.isEmpty() ? ImmutableList.of() : dependencies;
    provides = provides == null || provides.isEmpty() ? ImmutableList.of() : provides;
  }

  static SerializedPluginDescription from(Plugin plugin, String qualifiedName) {
    List<Dependency> dependencies = new ArrayList<>();
    for (com.velocitypowered.api.plugin.Dependency dependency : plugin.dependencies()) {
      dependencies.add(new Dependency(dependency.id(), dependency.optional()));
    }
    return new SerializedPluginDescription(plugin.id(), plugin.name(), plugin.version(),
        plugin.description(), plugin.url(),
        Arrays.stream(plugin.authors()).filter(author -> !author.isEmpty())
            .collect(Collectors.toList()), dependencies,
        Arrays.stream(plugin.provides()).filter(provided -> !provided.isEmpty())
            .collect(Collectors.toList()), qualifiedName);
  }

  @Override
  public List<String> authors() {
    return authors == null ? ImmutableList.of() : authors;
  }

  @Override
  public List<Dependency> dependencies() {
    return dependencies == null ? ImmutableList.of() : dependencies;
  }

  @Override
  public List<String> provides() {
    return provides == null ? ImmutableList.of() : provides;
  }

  /**
   * Returns the plugin ID.
   *
   * @return the plugin ID
   * @deprecated this is a record now; use {@link #id()}
   */
  @Deprecated(forRemoval = true)
  public String getId() {
    return this.id();
  }

  /**
   * Returns the display name.
   *
   * @return the display name, or {@code null} for none
   * @deprecated this is a record now; use {@link #name()}
   */
  @Deprecated(forRemoval = true)
  public @Nullable String getName() {
    return this.name();
  }

  /**
   * Returns the version.
   *
   * @return the version, or {@code null} for none
   * @deprecated this is a record now; use {@link #version()}
   */
  @Deprecated(forRemoval = true)
  public @Nullable String getVersion() {
    return this.version();
  }

  /**
   * Returns the description.
   *
   * @return the description, or {@code null} for none
   * @deprecated this is a record now; use {@link #description()}
   */
  @Deprecated(forRemoval = true)
  public @Nullable String getDescription() {
    return this.description();
  }

  /**
   * Returns the website.
   *
   * @return the website, or {@code null} for none
   * @deprecated this is a record now; use {@link #url()}
   */
  @Deprecated(forRemoval = true)
  public @Nullable String getUrl() {
    return this.url();
  }

  /**
   * Returns the authors.
   *
   * @return the authors
   * @deprecated this is a record now; use {@link #authors()}
   */
  @Deprecated(forRemoval = true)
  public List<String> getAuthors() {
    return this.authors();
  }

  /**
   * Returns the plugins this one depends on.
   *
   * @return the dependencies
   * @deprecated this is a record now; use {@link #dependencies()}
   */
  @Deprecated(forRemoval = true)
  public List<Dependency> getDependencies() {
    return this.dependencies();
  }

  /**
   * Returns the other IDs this plugin claims.
   *
   * @return the provided IDs
   * @deprecated this is a record now; use {@link #provides()}
   */
  @Deprecated(forRemoval = true)
  public List<String> getProvides() {
    return this.provides();
  }

  /**
   * Returns the fully qualified name of the main class.
   *
   * @return the main class
   * @deprecated this is a record now; use {@link #main()}
   */
  @Deprecated(forRemoval = true)
  public String getMain() {
    return this.main();
  }

  /**
   * Represents a dependency.
   *
   * @param id the ID of the plugin depended on
   * @param optional whether this plugin loads without it
   */
  public record Dependency(String id, boolean optional) {

    /**
     * Returns the ID of the plugin depended on.
     *
     * @return the dependency's ID
     * @deprecated this is a record now; use {@link #id()}
     */
    @Deprecated(forRemoval = true)
    public String getId() {
      return this.id();
    }

    /**
     * Returns whether this plugin loads without the dependency.
     *
     * @return {@code true} when the dependency is optional
     * @deprecated this is a record now; use {@link #optional()}
     */
    @Deprecated(forRemoval = true)
    public boolean isOptional() {
      return this.optional();
    }
  }
}
