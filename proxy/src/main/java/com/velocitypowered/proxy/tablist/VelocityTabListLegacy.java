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

package com.velocitypowered.proxy.tablist;

import com.google.common.collect.ImmutableList;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.crypto.IdentifiedKey;
import com.velocitypowered.api.proxy.player.ChatSession;
import com.velocitypowered.api.proxy.player.TabListEntry;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.packet.LegacyPlayerListItemPacket;
import com.velocitypowered.proxy.protocol.packet.LegacyPlayerListItemPacket.Item;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Exposes the legacy 1.7 tab list to "plugins".
 */
public class VelocityTabListLegacy extends KeyedVelocityTabList {

  /**
   * A mapping from player names (as shown in tab list) to generated UUIDs,
   * used for identifying and updating legacy 1.7 tab list entries.
   *
   * <p>Legacy versions (pre-1.8) do not provide UUIDs in tab packets, so this map is
   * used to simulate identity tracking.</p>
   */
  private final Map<String, UUID> nameMapping = new ConcurrentHashMap<>();

  /**
   * Constructs a new legacy 1.7-compatible tab list implementation.
   *
   * @param player the connected player this tab list is for
   * @param proxyServer the proxy server instance
   */
  public VelocityTabListLegacy(final ConnectedPlayer player, final ProxyServer proxyServer) {
    super(player, proxyServer);
  }

  @Deprecated
  @Override
  public void setHeaderAndFooter(final Component header, final Component footer) {
  }

  @Override
  public void clearHeaderAndFooter() {
  }

  /**
   * Adds a tab list entry to the legacy tab list and tracks it by name.
   *
   * @param entry the tab list entry to add
   */
  @Override
  public void addEntry(final TabListEntry entry) {
    super.addEntry(entry);
    nameMapping.put(entry.getProfile().getName(), entry.getProfile().getId());
  }

  /**
   * Removes a tab list entry by UUID and deletes the associated name mapping.
   *
   * @param uuid the UUID of the entry to remove
   * @return the removed entry, if present
   */
  @Override
  public Optional<TabListEntry> removeEntry(final UUID uuid) {
    Optional<TabListEntry> entry = super.removeEntry(uuid);
    entry.map(TabListEntry::getProfile).map(GameProfile::getName).ifPresent(nameMapping::remove);
    return entry;
  }

  /**
   * Sends {@link LegacyPlayerListItemPacket} remove packets for all entries and clears the tab list.
   */
  @Override
  public void clearAll() {
    for (TabListEntry value : entries.values()) {
      connection.delayedWrite(new LegacyPlayerListItemPacket(
          LegacyPlayerListItemPacket.REMOVE_PLAYER,
          Collections.singletonList(LegacyPlayerListItemPacket.Item.from(value))));
    }

    clearAllSilent();
  }

  /**
   * Clears all entries and name mappings without sending any packets.
   */
  @Override
  public void clearAllSilent() {
    entries.clear();
    nameMapping.clear();
  }

  /**
   * Processes a legacy (1.7) tab list update packet, either adding or removing a single entry.
   *
   * @param packet the legacy packet to process
   */
  @Override
  public void processLegacy(final LegacyPlayerListItemPacket packet) {
    Item item = packet.getItems().getFirst(); // Only one item per packet in 1.7

    switch (packet.getAction()) {
      case LegacyPlayerListItemPacket.ADD_PLAYER -> {
        if (nameMapping.containsKey(item.getName())) { // ADD_PLAYER also used for updating ping
          KeyedVelocityTabListEntry entry = entries.get(nameMapping.get(item.getName()));
          if (entry != null) {
            entry.setLatencyInternal(item.getLatency());
          }
        } else {
          UUID uuid = UUID.randomUUID(); // Use a fake uuid to preserve the function of custom entries
          nameMapping.put(item.getName(), uuid);
          entries.put(uuid, (KeyedVelocityTabListEntry) TabListEntry.builder()
              .tabList(this)
              .profile(new GameProfile(uuid, item.getName(), ImmutableList.of()))
              .latency(item.getLatency())
              .build());
        }
      }
      case LegacyPlayerListItemPacket.REMOVE_PLAYER -> {
        UUID removedUuid = nameMapping.remove(item.getName());
        if (removedUuid != null) {
          entries.remove(removedUuid);
        }
      }
      default -> {
      }
      // For 1.7 there is only add and remove
    }
  }

  @Override
  final void updateEntry(final int action, final TabListEntry entry) {
    if (entries.containsKey(entry.getProfile().getId())) {
      switch (action) {
        // Add here because we removed beforehand
        case LegacyPlayerListItemPacket.UPDATE_LATENCY, LegacyPlayerListItemPacket.UPDATE_DISPLAY_NAME -> connection
              .write(new LegacyPlayerListItemPacket(LegacyPlayerListItemPacket.ADD_PLAYER,
                  // ADD_PLAYER also updates ping
                  Collections.singletonList(Item.from(entry))));
        default -> {
        }
        // Can't do anything else
      }
    }
  }

  /**
   * Builds a new {@link VelocityTabListEntryLegacy} for this tab list.
   *
   * @param profile the player's profile
   * @param displayName the display name
   * @param latency the latency
   * @param gameMode the game mode
   * @param key unused in legacy mode
   * @return the new tab list entry
   */
  @Override
  public TabListEntry buildEntry(final GameProfile profile,
                                 final @Nullable Component displayName,
                                 final int latency, final int gameMode, final @Nullable IdentifiedKey key) {
    return new VelocityTabListEntryLegacy(this, profile, displayName, latency, gameMode);
  }

  /**
   * Builds a new {@link VelocityTabListEntryLegacy}, ignoring extra chat metadata.
   *
   * @param profile the player's profile
   * @param displayName the display name
   * @param latency the latency
   * @param gameMode the game mode
   * @param chatSession unused
   * @param listed unused
   * @return the new tab list entry
   */
  @Override
  public TabListEntry buildEntry(final GameProfile profile, final @Nullable Component displayName, final int latency,
                                 final int gameMode, final @Nullable ChatSession chatSession, final boolean listed) {
    return new VelocityTabListEntryLegacy(this, profile, displayName, latency, gameMode);
  }

  /**
   * Builds a new {@link VelocityTabListEntryLegacy}, ignoring extra metadata like list order and hat.
   *
   * @param profile the player's profile
   * @param displayName the display name
   * @param latency the latency
   * @param gameMode the game mode
   * @param chatSession unused
   * @param listed unused
   * @param listOrder unused
   * @param showHat unused
   * @return the new tab list entry
   */
  @Override
  public TabListEntry buildEntry(final GameProfile profile, final @Nullable Component displayName, final int latency,
                                 final int gameMode, final @Nullable ChatSession chatSession, final boolean listed, final int listOrder,
                                 final boolean showHat) {
    return new VelocityTabListEntryLegacy(this, profile, displayName, latency, gameMode);
  }
}
