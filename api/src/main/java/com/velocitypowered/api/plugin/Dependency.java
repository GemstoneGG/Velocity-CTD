/*
 * Copyright (C) 2018-2025 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.plugin;

import com.velocitypowered.api.plugin.ap.SerializedPluginDescription;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.intellij.lang.annotations.Pattern;

/**
 * Indicates that the {@link Plugin} depends on another plugin to enable.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface Dependency {

  /**
   * The plugin ID of the dependency.
   *
   * @return The dependency plugin ID
   * @see Plugin#id()
   */
  @Pattern(SerializedPluginDescription.ID_PATTERN_STRING)
  String id();

  /**
   * Whether the dependency is not required to enable this plugin. By default, this is
   * {@code false}, meaning that the dependency is required to enable this plugin.
   *
   * @return true if the dependency is not required for the plugin to work
   */
  boolean optional() default false;
}
