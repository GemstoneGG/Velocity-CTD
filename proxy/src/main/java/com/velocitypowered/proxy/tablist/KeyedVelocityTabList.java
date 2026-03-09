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

package com.velocitypowered.proxy.tablist;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.crypto.IdentifiedKey;
import com.velocitypowered.api.proxy.player.ChatSession;
import com.velocitypowered.api.proxy.player.TabListEntry;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.packet.LegacyPlayerListItemPacket;
import com.velocitypowered.proxy.protocol.packet.chat.RemoteChatSession;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Exposes the tab list to "plugins".
 */
public class KeyedVelocityTabList implements InternalTabList {

  /**
   * The player this tab list belongs to.
   */
  protected final ConnectedPlayer player;

  /**
   * The Minecraft connection used to communicate tab list changes.
   */
  protected final MinecraftConnection connection;

  /**
   * The proxy server instance.
   */
  protected final ProxyServer proxyServer;

  /**
   * The map of tab list entries, keyed by UUID.
   */
  protected final Map<UUID, KeyedVelocityTabListEntry> entries = new ConcurrentHashMap<>();

  /**
   * Constructs a new {@code KeyedVelocityTabList} for the specified player.
   *
   * @param player the connected player this tab list is associated with
   * @param proxyServer the proxy server instance used for player lookup and metadata
   */
  public KeyedVelocityTabList(final ConnectedPlayer player, final ProxyServer proxyServer) {
    this.player = player;
    this.proxyServer = proxyServer;
    this.connection = player.getConnection();
  }

  /**
   * Returns the player that this tab list belongs to.
   *
   * @return the associated {@link Player}
   */
  @Override
  public Player getPlayer() {
    return player;
  }

  /**
   * Sets the header and footer text components displayed at the top and bottom of the player's tab list.
   *
   * @param header the header component (must not be {@code null})
   * @param footer the footer component (must not be {@code null})
   */
  @Deprecated
  @Override
  public void setHeaderAndFooter(final Component header, final Component footer) {
    Preconditions.checkNotNull(header, "header");
    Preconditions.checkNotNull(footer, "footer");
    this.player.sendPlayerListHeaderAndFooter(header, footer);
  }

  /**
   * Clears the header and footer components from the player's tab list display.
   */
  @Override
  public void clearHeaderAndFooter() {
    this.player.clearPlayerListHeaderAndFooter();
  }

  /**
   * Adds a new {@link TabListEntry} to this tab list and notifies the client.
   *
   * @param entry the entry to add
   *
   * @throws IllegalArgumentException if the entry is not from this tab list,
   *                                  is already present, or is not a {@link KeyedVelocityTabListEntry}
   * @throws NullPointerException if {@code entry} is {@code null}
   */
  @Override
  public void addEntry(final TabListEntry entry) {
    Preconditions.checkNotNull(entry, "entry");
    Preconditions.checkArgument(entry.getTabList().equals(this),
        "The provided entry was not created by this tab list");
    Preconditions.checkArgument(!entries.containsKey(entry.getProfile().getId()),
        "this TabList already contains an entry with the same uuid");
    Preconditions.checkArgument(entry instanceof KeyedVelocityTabListEntry,
        "Not a Velocity tab list entry");

    LegacyPlayerListItemPacket.Item packetItem = LegacyPlayerListItemPacket.Item.from(entry);
    connection.write(
        new LegacyPlayerListItemPacket(LegacyPlayerListItemPacket.ADD_PLAYER,
            Collections.singletonList(packetItem)));
    entries.put(entry.getProfile().getId(), (KeyedVelocityTabListEntry) entry);
  }

  /**
   * Removes the tab list entry with the specified UUID, if it exists.
   * Sends a removal packet to the client if the entry was present.
   *
   * @param uuid the UUID of the tab list entry to remove
   * @return the removed entry, or an empty {@link Optional} if not found
   *
   * @throws NullPointerException if {@code uuid} is {@code null}
   */
  @Override
  public Optional<TabListEntry> removeEntry(final UUID uuid) {
    Preconditions.checkNotNull(uuid, "uuid");

    TabListEntry entry = entries.remove(uuid);
    if (entry != null) {
      LegacyPlayerListItemPacket.Item packetItem = LegacyPlayerListItemPacket.Item.from(entry);
      connection.write(
          new LegacyPlayerListItemPacket(LegacyPlayerListItemPacket.REMOVE_PLAYER,
              Collections.singletonList(packetItem)));
    }

    return Optional.ofNullable(entry);
  }

  /**
   * Checks whether an entry with the given UUID exists in this tab list.
   *
   * @param uuid the UUID to check
   * @return {@code true} if an entry with the given UUID is present, {@code false} otherwise
   *
   * @throws NullPointerException if {@code uuid} is {@code null}
   */
  @Override
  public boolean containsEntry(final UUID uuid) {
    Preconditions.checkNotNull(uuid, "uuid");
    return entries.containsKey(uuid);
  }

  /**
   * Gets the tab list entry for the specified UUID, if present.
   *
   * @param uuid the UUID of the tab list entry to retrieve
   * @return an {@link Optional} containing the entry, or empty if not found
   */
  @Override
  public Optional<TabListEntry> getEntry(final UUID uuid) {
    return Optional.ofNullable(this.entries.get(uuid));
  }

  /**
   * Clears all entries from the tab list. Note that the entries are written with
   * {@link MinecraftConnection#delayedWrite(Object)}, so make sure to do an explicit
   * {@link MinecraftConnection#flush()}.
   */
  @Override
  public void clearAll() {
    Collection<KeyedVelocityTabListEntry> listEntries = entries.values();
    if (listEntries.isEmpty()) {
      return;
    }

    List<LegacyPlayerListItemPacket.Item> items = new ArrayList<>(listEntries.size());
    for (TabListEntry value : listEntries) {
      items.add(LegacyPlayerListItemPacket.Item.from(value));
    }

    clearAllSilent();
    connection.delayedWrite(new LegacyPlayerListItemPacket(
            LegacyPlayerListItemPacket.REMOVE_PLAYER, items));
  }

  /**
   * Clears all tab list entries from the internal map without sending any removal packets.
   *
   * <p>This is used internally as a silent clear before a batch tab list update.</p>
   */
  @Override
  public void clearAllSilent() {
    entries.clear();
  }

  /**
   * Returns an unmodifiable view of the current tab list entries.
   *
   * @return a collection of all current {@link TabListEntry} instances
   */
  @Override
  public Collection<TabListEntry> getEntries() {
    return Collections.unmodifiableCollection(this.entries.values());
  }

  /**
   * Builds a {@link TabListEntry} using the specified profile and display settings.
   *
   * @param profile the game profile for the entry
   * @param displayName the display name (nullable)
   * @param latency the latency to display
   * @param gameMode the player's game mode (0 = survival, etc.)
   * @param key the cryptographic key identifying the player (nullable)
   * @return the constructed {@link TabListEntry}
   */
  @Override
  public TabListEntry buildEntry(final GameProfile profile, final @Nullable Component displayName,
                                 final int latency, final int gameMode, final @Nullable IdentifiedKey key) {
    return new KeyedVelocityTabListEntry(this, profile, displayName, latency, gameMode, key);
  }

  /**
   * Builds a {@link TabListEntry} using the specified profile and display settings,
   * extracting the key from the given {@link ChatSession}.
   *
   * @param profile the game profile
   * @param displayName the display name (nullable)
   * @param latency the latency to display
   * @param gameMode the player's game mode
   * @param chatSession the chat session, may provide a key (nullable)
   * @param listed whether the player is listed in the tab list (currently unused)
   * @return the constructed {@link TabListEntry}
   */
  @Override
  public TabListEntry buildEntry(final GameProfile profile, final @Nullable Component displayName,
                                 final int latency, final int gameMode, final @Nullable ChatSession chatSession,
                                 final boolean listed) {
    return new KeyedVelocityTabListEntry(this, profile, displayName, latency, gameMode,
        chatSession == null ? null : chatSession.getIdentifiedKey());
  }

  /**
   * Builds a {@link TabListEntry} with optional rendering metadata.
   *
   * <p>The list order and hat display parameters are currently ignored in Velocity,
   * and this method delegates to {@link #buildEntry(GameProfile, Component, int, int, ChatSession, boolean)}.</p>
   *
   * @param profile the game profile
   * @param displayName the display name (nullable)
   * @param latency the latency to display
   * @param gameMode the player's game mode
   * @param chatSession the chat session (nullable)
   * @param listed whether the entry should be listed
   * @param listOrder unused
   * @param showHat unused
   * @return the constructed {@link TabListEntry}
   */
  @Override
  public TabListEntry buildEntry(final GameProfile profile, final @Nullable Component displayName,
                                 final int latency, final int gameMode, final @Nullable ChatSession chatSession,
                                 final boolean listed, final int listOrder, final boolean showHat) {
    return buildEntry(profile, displayName, latency, gameMode, chatSession, listed);
  }

  /**
   * Processes a legacy 1.7-style {@link LegacyPlayerListItemPacket}, modifying the internal tab list.
   *
   * <p>Adds, removes, or updates tab list entries based on the packet action and content.</p>
   *
   * @param packet the legacy player list item packet to process
   */
  @Override
  public void processLegacy(final LegacyPlayerListItemPacket packet) {
    // Packets are already forwarded on, so no need to do that here
    for (LegacyPlayerListItemPacket.Item item : packet.getItems()) {
      UUID uuid = item.getUuid();
      assert uuid != null : "1.7 tab list entry given to modern tab list handler!";

      if (packet.getAction() != LegacyPlayerListItemPacket.ADD_PLAYER && !entries.containsKey(uuid)) {
        // Sometimes UPDATE_GAMEMODE is sent before ADD_PLAYER so don't want to warn here
        continue;
      }

      switch (packet.getAction()) {
        case LegacyPlayerListItemPacket.ADD_PLAYER -> {
          // ensure that name and properties are available
          String name = item.getName();
          List<GameProfile.Property> properties = item.getProperties();
          if (name == null || properties == null) {
            throw new IllegalStateException("Got null game profile for ADD_PLAYER");
          }

          entries.putIfAbsent(item.getUuid(), (KeyedVelocityTabListEntry) TabListEntry.builder()
                  .tabList(this)
                  .profile(new GameProfile(uuid, name, properties))
                  .displayName(item.getDisplayName())
                  .latency(item.getLatency())
                  .chatSession(new RemoteChatSession(null, item.getPlayerKey()))
                  .gameMode(item.getGameMode())
                  .build());
        }
        case LegacyPlayerListItemPacket.REMOVE_PLAYER -> entries.remove(uuid);
        case LegacyPlayerListItemPacket.UPDATE_DISPLAY_NAME -> {
          KeyedVelocityTabListEntry entry = entries.get(uuid);
          if (entry != null) {
            entry.setDisplayNameInternal(item.getDisplayName());
          }
        }
        case LegacyPlayerListItemPacket.UPDATE_LATENCY -> {
          KeyedVelocityTabListEntry entry = entries.get(uuid);
          if (entry != null) {
            entry.setLatencyInternal(item.getLatency());
          }
        }
        case LegacyPlayerListItemPacket.UPDATE_GAMEMODE -> {
          KeyedVelocityTabListEntry entry = entries.get(uuid);
          if (entry != null) {
            entry.setGameModeInternal(item.getGameMode());
          }
        }
        default -> {
        }
        // Nothing we can do here
      }
    }
  }

  /**
   * Updates a tab list entry with the specified action.
   *
   * @param action the {@link LegacyPlayerListItemPacket} action type
   * @param entry the tab list entry to update
   */
  void updateEntry(final int action, final TabListEntry entry) {
    if (entries.containsKey(entry.getProfile().getId())) {
      LegacyPlayerListItemPacket.Item packetItem = LegacyPlayerListItemPacket.Item.from(entry);

      IdentifiedKey selectedKey = packetItem.getPlayerKey();
      Optional<Player> existing = proxyServer.getPlayer(entry.getProfile().getId());
      if (existing.isPresent()) {
        selectedKey = existing.get().getIdentifiedKey();
      }

      if (selectedKey != null
          && selectedKey.getKeyRevision().getApplicableTo()
          .contains(connection.getProtocolVersion())
          && Objects.equals(selectedKey.getSignatureHolder(), entry.getProfile().getId())) {
        packetItem.setPlayerKey(selectedKey);
      } else {
        packetItem.setPlayerKey(null);
      }

      connection.write(new LegacyPlayerListItemPacket(action, List.of(packetItem)));
    }
  }
}
