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

import com.velocitypowered.api.proxy.crypto.IdentifiedKey;
import com.velocitypowered.api.proxy.player.ChatSession;
import com.velocitypowered.api.proxy.player.TabList;
import com.velocitypowered.api.proxy.player.TabListEntry;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.protocol.packet.LegacyPlayerListItemPacket;
import com.velocitypowered.proxy.protocol.packet.chat.RemoteChatSession;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Handles modern tab list entries.
 */
public class KeyedVelocityTabListEntry implements TabListEntry {

  /**
   * The parent tab list this entry belongs to.
   */
  private final KeyedVelocityTabList tabList;

  /**
   * The game profile (UUID and name) associated with this entry.
   */
  private final GameProfile profile;

  /**
   * The display name shown in the tab list (nullable).
   */
  private Component displayName;

  /**
   * The latency (ping) value shown in the tab list.
   */
  private int latency;

  /**
   * The player's current game mode (e.g. 0 = survival).
   */
  private int gameMode;

  /**
   * The player's public identity key used for signed chat, or {@code null} if absent.
   */
  private @Nullable IdentifiedKey playerKey;

  KeyedVelocityTabListEntry(final KeyedVelocityTabList tabList, final GameProfile profile,
                            final @Nullable Component displayName, final int latency,
                            final int gameMode, @Nullable final IdentifiedKey playerKey) {
    this.tabList = tabList;
    this.profile = profile;
    this.displayName = displayName;
    this.latency = latency;
    this.gameMode = gameMode;
    this.playerKey = playerKey;
  }

  @Override
  public final TabList getTabList() {
    return tabList;
  }

  @Override
  public final GameProfile getProfile() {
    return profile;
  }

  @Override
  public final Optional<Component> getDisplayNameComponent() {
    return Optional.ofNullable(displayName);
  }

  /**
   * Sets the display name component for this tab list entry and triggers an update to the client.
   *
   * @param displayName the new display name to set, or {@code null} to clear
   * @return this entry instance for chaining
   */
  @Override
  public TabListEntry setDisplayName(final @Nullable Component displayName) {
    this.displayName = displayName;
    tabList.updateEntry(LegacyPlayerListItemPacket.UPDATE_DISPLAY_NAME, this);
    return this;
  }

  /**
   * Sets the display name without sending an update packet.
   *
   * <p>This is used when applying updates received from a remote source.</p>
   *
   * @param displayName the new display name to set
   */
  void setDisplayNameInternal(final @Nullable Component displayName) {
    this.displayName = displayName;
  }

  @Override
  public final int getLatency() {
    return latency;
  }

  @Override
  public final TabListEntry setLatency(final int latency) {
    this.latency = latency;
    tabList.updateEntry(LegacyPlayerListItemPacket.UPDATE_LATENCY, this);
    return this;
  }

  /**
   * Sets the latency (ping) value without notifying the client.
   *
   * <p>Used internally for applying packet updates.</p>
   *
   * @param latency the new latency value
   */
  void setLatencyInternal(final int latency) {
    this.latency = latency;
  }

  @Override
  public final int getGameMode() {
    return gameMode;
  }

  @Override
  public final TabListEntry setGameMode(final int gameMode) {
    this.gameMode = gameMode;
    tabList.updateEntry(LegacyPlayerListItemPacket.UPDATE_GAMEMODE, this);
    return this;
  }

  /**
   * Sets the game mode without sending an update packet.
   *
   * <p>Used internally for deserialization or legacy packet updates.</p>
   *
   * @param gameMode the new game mode value
   */
  void setGameModeInternal(final int gameMode) {
    this.gameMode = gameMode;
  }

  @Override
  public final ChatSession getChatSession() {
    return new RemoteChatSession(null, this.playerKey);
  }

  @Override
  public final IdentifiedKey getIdentifiedKey() {
    return playerKey;
  }

  /**
   * Sets the player's identity key without triggering a network update.
   *
   * <p>This is typically used during tab list entry reconstruction or legacy packet handling.</p>
   *
   * @param playerKey the new {@link IdentifiedKey} to assign
   */
  void setPlayerKeyInternal(final IdentifiedKey playerKey) {
    this.playerKey = playerKey;
  }
}
