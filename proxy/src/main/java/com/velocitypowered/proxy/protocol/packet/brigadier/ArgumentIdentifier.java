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

package com.velocitypowered.proxy.protocol.packet.brigadier;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.velocitypowered.api.network.ProtocolVersion;
import java.util.HashMap;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents an identifier for a Brigadier command argument, mapping the argument to
 * different protocol versions.
 *
 * <p>The {@code ArgumentIdentifier} is responsible for holding an identifier string for
 * an argument and a map that associates protocol versions with their respective IDs.
 * It ensures that the protocol version is compatible with the Minecraft 1.19 protocol or later.</p>
 */
public final class ArgumentIdentifier {

  /**
   * The string-based Brigadier identifier for this argument type.
   */
  private final String identifier;

  /**
   * A mapping of protocol versions to their respective integer IDs for this identifier.
   */
  private final Map<ProtocolVersion, Integer> versionById;

  private ArgumentIdentifier(final String identifier, final VersionSet... versions) {
    this.identifier = Preconditions.checkNotNull(identifier);

    Preconditions.checkNotNull(versions);

    Map<ProtocolVersion, Integer> temp = new HashMap<>();

    ProtocolVersion previous = null;
    for (VersionSet version : versions) {
      VersionSet current = Preconditions.checkNotNull(version);

      Preconditions.checkArgument(
          current.getVersion().noLessThan(ProtocolVersion.MINECRAFT_1_19),
          "Version too old for ID index");
      Preconditions.checkArgument(previous == null || previous.greaterThan(current.getVersion()),
          "Invalid protocol version order");

      for (ProtocolVersion v : ProtocolVersion.values()) {
        if (v.noLessThan(current.getVersion())) {
          temp.putIfAbsent(v, current.getId());
        }
      }

      previous = current.getVersion();
    }

    this.versionById = ImmutableMap.copyOf(temp);
  }

  @Override
  public String toString() {
    return "ArgumentIdentifier{"
        + "identifier='" + identifier + '\''
        + '}';
  }

  /**
   * Returns the string identifier for this argument type.
   *
   * @return the identifier string
   */
  public String getIdentifier() {
    return identifier;
  }

  /**
   * Gets the corresponding integer ID for the given protocol version.
   *
   * @param version the protocol version
   * @return the associated ID, or {@code null} if unavailable
   */
  public @Nullable Integer getIdByProtocolVersion(final ProtocolVersion version) {
    return versionById.get(Preconditions.checkNotNull(version));
  }

  /**
   * Creates a {@link VersionSet} mapping a protocol version to a specific ID.
   *
   * @param version the starting protocol version
   * @param id the ID to apply for this version and newer
   * @return a new {@link VersionSet}
   */
  public static VersionSet mapSet(final ProtocolVersion version, final int id) {
    return new VersionSet(version, id);
  }

  /**
   * Creates an {@link ArgumentIdentifier} instance from an identifier string and
   * version mappings.
   *
   * @param identifier the Brigadier identifier string
   * @param versions the version mappings (must be ordered from newest to oldest)
   * @return a new {@link ArgumentIdentifier}
   */
  public static ArgumentIdentifier id(final String identifier, final VersionSet... versions) {
    return new ArgumentIdentifier(identifier, versions);
  }

  /**
   * This class is purely for convenience.
   */
  public static final class VersionSet {

    /**
     * The protocol version from which this ID mapping becomes valid.
     *
     * <p>All protocol versions greater than or equal to this version will use
     * the associated {@code id} value for the argument identifier.</p>
     */
    private final ProtocolVersion version;

    /**
     * The integer ID that represents the argument identifier starting at the
     * specified {@link #version}.
     */
    private final int id;

    private VersionSet(final ProtocolVersion version, final int id) {
      this.version = Preconditions.checkNotNull(version);
      this.id = id;
    }

    /**
     * Returns the ID associated with this version.
     *
     * @return the integer ID
     */
    public int getId() {
      return id;
    }

    /**
     * Returns the protocol version at which this ID mapping starts.
     *
     * @return the protocol version
     */
    public ProtocolVersion getVersion() {
      return version;
    }
  }
}
