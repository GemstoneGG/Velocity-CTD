/*
 * Copyright (C) 2018-2025 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.proxy.messages;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a legacy channel identifier (for Minecraft 1.12 and below). For modern 1.13 plugin
 * messages, please see {@link MinecraftChannelIdentifier}. This class is immutable and safe for
 * multithreaded use.
 */
public final class LegacyChannelIdentifier implements ChannelIdentifier {

  /**
   * The name of the legacy plugin message channel.
   */
  private final String name;

  /**
   * Creates a new legacy channel identifier.
   *
   * @param name the name for the channel
   */
  public LegacyChannelIdentifier(final String name) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "provided name is empty");
    this.name = name;
  }

  /**
   * Returns the name of this legacy plugin message channel.
   *
   * @return the channel name
   */
  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return name + " (legacy)";
  }

  @Override
  public boolean equals(@Nullable final Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    LegacyChannelIdentifier that = (LegacyChannelIdentifier) o;
    return Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }

  @Override
  public String getId() {
    return this.getName();
  }
}
