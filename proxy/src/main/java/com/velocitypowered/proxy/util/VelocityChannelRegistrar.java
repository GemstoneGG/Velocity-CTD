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

package com.velocitypowered.proxy.util;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.ChannelRegistrar;
import com.velocitypowered.api.proxy.messages.LegacyChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.proxy.protocol.util.PluginMessageUtil;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Registry for channels recognized by the proxy.
 */
public class VelocityChannelRegistrar implements ChannelRegistrar {

  /**
   * Map of all registered channel identifiers, keyed by their raw string ID.
   *
   * <p>This includes both legacy and modern identifiers, as well as aliases produced
   * by {@link PluginMessageUtil#transformLegacyToModernChannel(String)}.</p>
   */
  private final Map<String, ChannelIdentifier> identifierMap = new ConcurrentHashMap<>();

  /**
   * Registers the provided {@link ChannelIdentifier} instances into the proxy channel registry.
   *
   * <p>Legacy identifiers will be registered under both their original ID and the transformed
   * modern ID (e.g., {@code BungeeCord} → {@code legacy:bungeecord}).</p>
   *
   * @param identifiers the channel identifiers to register
   * @throws IllegalArgumentException if any identifier is not a {@link LegacyChannelIdentifier} or {@link MinecraftChannelIdentifier}
   */
  @Override
  public void register(final ChannelIdentifier... identifiers) {
    for (ChannelIdentifier identifier : identifiers) {
      Preconditions.checkArgument(identifier instanceof LegacyChannelIdentifier
          || identifier instanceof MinecraftChannelIdentifier, "identifier is unknown");
    }

    for (ChannelIdentifier identifier : identifiers) {
      if (identifier instanceof MinecraftChannelIdentifier) {
        identifierMap.put(identifier.getId(), identifier);
      } else {
        String rewritten = PluginMessageUtil.transformLegacyToModernChannel(identifier.getId());
        identifierMap.put(identifier.getId(), identifier);
        identifierMap.put(rewritten, identifier);
      }
    }
  }

  /**
   * Unregisters the provided {@link ChannelIdentifier} instances from the proxy channel registry.
   *
   * <p>Legacy identifiers are removed from both their raw and transformed forms.</p>
   *
   * @param identifiers the channel identifiers to unregister
   * @throws IllegalArgumentException if any identifier is not a {@link LegacyChannelIdentifier} or {@link MinecraftChannelIdentifier}
   */
  @Override
  public void unregister(final ChannelIdentifier... identifiers) {
    for (ChannelIdentifier identifier : identifiers) {
      Preconditions.checkArgument(identifier instanceof LegacyChannelIdentifier
          || identifier instanceof MinecraftChannelIdentifier, "identifier is unknown");
    }

    for (ChannelIdentifier identifier : identifiers) {
      if (identifier instanceof MinecraftChannelIdentifier) {
        identifierMap.remove(identifier.getId());
      } else {
        String rewritten = PluginMessageUtil.transformLegacyToModernChannel(identifier.getId());
        identifierMap.remove(identifier.getId());
        identifierMap.remove(rewritten);
      }
    }
  }

  /**
   * Returns all legacy channel IDs.
   *
   * @return all legacy channel IDs
   */
  public Collection<ChannelIdentifier> getLegacyChannelIds() {
    Collection<ChannelIdentifier> ids = new HashSet<>();
    for (ChannelIdentifier value : identifierMap.values()) {
      ids.add(new LegacyChannelIdentifier(value.getId()));
    }

    return ids;
  }

  /**
   * Returns all channel IDs (as strings) for use with Minecraft 1.13 and above.
   *
   * @return the channel IDs for Minecraft 1.13 and above
   */
  public Collection<ChannelIdentifier> getModernChannelIds() {
    Collection<ChannelIdentifier> ids = new HashSet<>();
    for (ChannelIdentifier value : identifierMap.values()) {
      if (value instanceof MinecraftChannelIdentifier) {
        ids.add(value);
      } else {
        ids.add(MinecraftChannelIdentifier.from(PluginMessageUtil.transformLegacyToModernChannel(value.getId())));
      }
    }

    return ids;
  }

  /**
   * Retrieves a {@link ChannelIdentifier} by its string ID, if it exists in the registry.
   *
   * @param id the raw identifier string
   * @return the matching {@link ChannelIdentifier}, or {@code null} if none found
   */
  public @Nullable ChannelIdentifier getFromId(final String id) {
    return identifierMap.get(id);
  }

  /**
   * Returns all the channel names to register depending on the Minecraft protocol version.
   *
   * @param protocolVersion the protocol version in use
   * @return the list of channels to register
   */
  public Collection<ChannelIdentifier> getChannelsForProtocol(final ProtocolVersion protocolVersion) {
    if (protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_13)) {
      return getModernChannelIds();
    }

    return getLegacyChannelIds();
  }
}
