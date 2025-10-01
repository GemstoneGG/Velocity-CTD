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

package com.velocitypowered.proxy.connection.registry;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.network.ProtocolVersion;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents basic information for a Minecraft dimension.
 */
public final class DimensionInfo {

  /**
   * The namespaced identifier for this dimension in the dimension registry,
   * such as {@code minecraft:overworld} or {@code minecraft:the_nether}.
   *
   * <p>This value is used in the Join Game and Respawn packets to inform the client
   * which dimension it is currently in.</p>
   */
  private final String registryIdentifier;

  /**
   * The user-visible level name for this dimension, displayed in the F3 debug screen
   * and game logs.
   *
   * <p>This value may be {@code null} for older protocol versions or in cases where
   * no descriptive name is needed.</p>
   */
  private final String levelName;

  /**
   * Indicates whether the dimension uses a flat world generator.
   *
   * <p>Flat worlds typically disable fog rendering below the surface and use simpler
   * lighting models.</p>
   */
  private final boolean isFlat;

  /**
   * Indicates whether the dimension is a debug-type world.
   *
   * <p>Debug worlds show all block states in a grid and disable most standard gameplay.
   * This is often used for block or model inspection.</p>
   */
  private final boolean isDebugType;

  /**
   * Initializes a new {@link DimensionInfo} instance.
   *
   * @param registryIdentifier the identifier for the dimension from the registry
   * @param levelName          the level name as displayed in the F3 menu and logs
   * @param isFlat             if true will set world lighting below surface-level to not display
   *                           fog
   * @param isDebugType        if true, constrains the world to the very limited debug-type world
   * @param protocolVersion    the protocol version used to determine compatibility constraints (e.g., disallow empty registry keys pre-1.20.5)
   */
  public DimensionInfo(final String registryIdentifier, final @Nullable String levelName,
                       final boolean isFlat, final boolean isDebugType, final ProtocolVersion protocolVersion) {
    this.registryIdentifier = Preconditions.checkNotNull(registryIdentifier, "registryIdentifier cannot be null");
    if (protocolVersion.lessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
      Preconditions.checkArgument(!registryIdentifier.isEmpty(), "registryIdentifier cannot be empty");
    }

    this.levelName = levelName;
    this.isFlat = isFlat;
    this.isDebugType = isDebugType;
  }

  /**
   * Returns whether this dimension uses the debug world type.
   *
   * <p>Debug worlds are typically limited in scope and used for block visualization or testing.</p>
   *
   * @return {@code true} if this is a debug-type dimension, {@code false} otherwise
   */
  public boolean isDebugType() {
    return isDebugType;
  }

  /**
   * Returns whether this dimension is a flat world.
   *
   * <p>This affects fog rendering and world lighting behavior below surface level.</p>
   *
   * @return {@code true} if this dimension is flat, {@code false} otherwise
   */
  public boolean isFlat() {
    return isFlat;
  }

  /**
   * Gets the level name of this dimension.
   *
   * <p>This value appears in the debug screen (F3) and game logs.</p>
   *
   * @return the level name, or {@code null} if not specified
   */
  public @Nullable String getLevelName() {
    return levelName;
  }

  /**
   * Gets the registry identifier of this dimension.
   *
   * <p>This identifier is used by Minecraft to locate the dimension in the dimension registry.</p>
   *
   * @return the registry identifier string
   */
  public String getRegistryIdentifier() {
    return registryIdentifier;
  }
}
