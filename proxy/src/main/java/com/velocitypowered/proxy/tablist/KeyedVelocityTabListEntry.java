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
                            final int gameMode, final @Nullable IdentifiedKey playerKey) {
    this.tabList = tabList;
    this.profile = profile;
    this.displayName = displayName;
    this.latency = latency;
    this.gameMode = gameMode;
    this.playerKey = playerKey;
  }

  /**
   * Returns the {@link TabList} that this entry belongs to.
   *
   * @return the parent tab list
   */
  @Override
  public TabList getTabList() {
    return tabList;
  }

  /**
   * Returns the {@link GameProfile} associated with this entry.
   *
   * <p>This includes the UUID and username of the player shown in the tab list.</p>
   *
   * @return the game profile of the entry
   */
  @Override
  public GameProfile getProfile() {
    return profile;
  }

  /**
   * Retrieves the display name component shown in the tab list for this entry.
   *
   * <p>This component is optional and may be {@code null} if no display name override
   * was set. When absent, the client will typically display the player's username instead.</p>
   *
   * @return an {@link Optional} containing the display name, or empty if not set
   */
  @Override
  public Optional<Component> getDisplayNameComponent() {
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

  /**
   * Returns the latency (ping) value associated with this entry.
   *
   * @return the latency in milliseconds
   */
  @Override
  public int getLatency() {
    return latency;
  }

  /**
   * Sets the latency (ping) value for this tab list entry and notifies the client of the update.
   *
   * <p>This value is typically shown in the client as the connection bar indicator
   * beside the player's name in the tab list.</p>
   *
   * @param latency the new latency value in milliseconds
   * @return this {@link TabListEntry} instance for chaining
   */
  @Override
  public TabListEntry setLatency(final int latency) {
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

  /**
   * Returns the game mode for this tab list entry.
   *
   * <p>This is typically represented as an integer (e.g., 0 for survival, 1 for creative).</p>
   *
   * @return the integer game mode value
   */
  @Override
  public int getGameMode() {
    return gameMode;
  }

  /**
   * Sets the game mode for this tab list entry and triggers an update to the client.
   *
   * <p>Game mode values typically follow Minecraft's internal representation:
   * <ul>
   *   <li>0 - Survival</li>
   *   <li>1 - Creative</li>
   *   <li>2 - Adventure</li>
   *   <li>3 - Spectator</li>
   * </ul>
   * This information is shown in the client tab list entry, though usage may vary by client version.</p>
   *
   * @param gameMode the new game mode value
   * @return this {@link TabListEntry} instance for chaining
   */
  @Override
  public TabListEntry setGameMode(final int gameMode) {
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

  /**
   * Returns the {@link ChatSession} representing this entry's chat capabilities.
   *
   * <p>For tab list entries, this typically wraps the player's {@link IdentifiedKey}
   * for signed message support in modern clients.</p>
   *
   * @return the remote chat session for the entry
   */
  @Override
  public ChatSession getChatSession() {
    return new RemoteChatSession(null, this.playerKey);
  }

  /**
   * Returns the {@link IdentifiedKey} used by this player for chat signing.
   *
   * @return the player's identity key, or {@code null} if not present
   */
  @Override
  public IdentifiedKey getIdentifiedKey() {
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
