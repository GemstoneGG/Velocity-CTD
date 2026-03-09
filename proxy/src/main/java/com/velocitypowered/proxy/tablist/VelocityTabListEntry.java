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

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.player.ChatSession;
import com.velocitypowered.api.proxy.player.TabList;
import com.velocitypowered.api.proxy.player.TabListEntry;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.protocol.packet.UpsertPlayerInfoPacket;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Generic tab list entry implementation.
 */
public class VelocityTabListEntry implements TabListEntry {

  /**
   * The tab list that this entry belongs to.
   */
  private final VelocityTabList tabList;

  /**
   * The player profile associated with this entry.
   */
  private final GameProfile profile;

  /**
   * The display name shown in the tab list (nullable).
   */
  private Component displayName;

  /**
   * The latency (ping) value for this entry.
   */
  private int latency;

  /**
   * The current game mode of the player (0 = survival, 1 = creative, etc.).
   */
  private int gameMode;

  /**
   * Whether the player should be listed in the tab list.
   */
  private boolean listed;

  /**
   * The sort order used for rendering in the tab list (1.21.2+).
   */
  private int listOrder;

  /**
   * Whether the player's hat layer is visible (1.21.4+).
   */
  private boolean showHat;

  /**
   * The associated secure chat session, or {@code null} if none.
   */
  private @Nullable ChatSession session;

  /**
   * Constructs a new {@code VelocityTabListEntry} instance with all tab display attributes.
   *
   * @param tabList the parent tab list this entry belongs to
   * @param profile the player profile to display
   * @param displayName the optional display name component
   * @param latency the player's latency (ping)
   * @param gameMode the player's game mode
   * @param session the chat session associated with this player (nullable)
   * @param listed whether this entry should be visible
   * @param listOrder the sort order in the tab list (1.21.2+)
   * @param showHat whether to show the player's hat layer (1.21.4+)
   */
  public VelocityTabListEntry(final VelocityTabList tabList, final GameProfile profile, final Component displayName,
                              final int latency, final int gameMode, final @Nullable ChatSession session,
                              final boolean listed, final int listOrder, final boolean showHat) {
    this.tabList = tabList;
    this.profile = profile;
    this.displayName = displayName;
    this.latency = latency;
    this.gameMode = gameMode;
    this.session = session;
    this.listed = listed;
    this.listOrder = listOrder;
    this.showHat = showHat;
  }

  /**
   * Returns the {@link ChatSession} associated with this tab list entry.
   *
   * @return the chat session, or {@code null} if none is present
   */
  @Override
  public @Nullable ChatSession getChatSession() {
    return this.session;
  }

  /**
   * Returns the {@link TabList} that this entry belongs to.
   *
   * @return the owning tab list
   */
  @Override
  public TabList getTabList() {
    return this.tabList;
  }

  /**
   * Returns the {@link GameProfile} associated with this entry.
   *
   * @return the profile identifying this player
   */
  @Override
  public GameProfile getProfile() {
    return this.profile;
  }

  /**
   * Returns the display name of this entry, if set.
   *
   * @return an {@link Optional} containing the display name, or empty if not set
   */
  @Override
  public Optional<Component> getDisplayNameComponent() {
    return Optional.ofNullable(displayName);
  }

  /**
   * Sets the display name for this tab entry and sends an update to the client.
   *
   * @param displayName the new display name to use, or {@code null} to clear
   * @return this tab list entry for method chaining
   */
  @Override
  public TabListEntry setDisplayName(final @Nullable Component displayName) {
    this.displayName = displayName;
    UpsertPlayerInfoPacket.Entry upsertEntry = this.tabList.createRawEntry(this);
    upsertEntry.setDisplayName(displayName == null ? null : new ComponentHolder(this.tabList.getPlayer().getProtocolVersion(), displayName));
    this.tabList.emitActionRaw(UpsertPlayerInfoPacket.Action.UPDATE_DISPLAY_NAME, upsertEntry);
    return this;
  }

  /**
   * Sets the display name for this tab list entry without sending an update packet to the client.
   *
   * <p>This method is intended for internal use when applying updates received from the network.</p>
   *
   * @param displayName the new display name, or {@code null} to clear it
   */
  void setDisplayNameWithoutUpdate(final @Nullable Component displayName) {
    this.displayName = displayName;
  }

  /**
   * Returns the latency (ping) value for this player.
   *
   * @return the latency in milliseconds
   */
  @Override
  public int getLatency() {
    return this.latency;
  }

  /**
   * Updates the latency and sends an update packet to the client.
   *
   * @param latency the new latency value
   * @return this tab list entry for method chaining
   */
  @Override
  public TabListEntry setLatency(final int latency) {
    this.latency = latency;
    UpsertPlayerInfoPacket.Entry upsertEntry = this.tabList.createRawEntry(this);
    upsertEntry.setLatency(latency);
    this.tabList.emitActionRaw(UpsertPlayerInfoPacket.Action.UPDATE_LATENCY, upsertEntry);
    return this;
  }

  /**
   * Sets the latency value silently.
   *
   * @param latency the new latency value to assign without notifying the client
   */
  void setLatencyWithoutUpdate(final int latency) {
    this.latency = latency;
  }

  /**
   * Returns the game mode of the player (e.g., 0 = survival).
   *
   * @return the game mode value
   */
  @Override
  public int getGameMode() {
    return this.gameMode;
  }

  /**
   * Sets the player's game mode and updates the client.
   *
   * @param gameMode the new game mode value
   * @return this tab list entry for method chaining
   */
  @Override
  public TabListEntry setGameMode(final int gameMode) {
    this.gameMode = gameMode;
    UpsertPlayerInfoPacket.Entry upsertEntry = this.tabList.createRawEntry(this);
    upsertEntry.setGameMode(gameMode);
    this.tabList.emitActionRaw(UpsertPlayerInfoPacket.Action.UPDATE_GAME_MODE, upsertEntry);
    return this;
  }

  /**
   * Sets the game mode silently.
   *
   * @param gameMode the new game mode value to assign without notifying the client
   */
  void setGameModeWithoutUpdate(final int gameMode) {
    this.gameMode = gameMode;
  }

  /**
   * Sets the chat session internally.
   *
   * @param session the new chat session
   */
  protected void setChatSession(final @Nullable ChatSession session) {
    this.session = session;
  }

  /**
   * Returns whether this player is currently listed in the tab list.
   *
   * @return {@code true} if listed, {@code false} otherwise
   */
  @Override
  public boolean isListed() {
    return listed;
  }

  /**
   * Updates the listed status and sends an update to the client.
   *
   * @param listed whether the player should be listed
   * @return this tab list entry for method chaining
   */
  @Override
  public VelocityTabListEntry setListed(final boolean listed) {
    this.listed = listed;
    UpsertPlayerInfoPacket.Entry upsertEntry = this.tabList.createRawEntry(this);
    upsertEntry.setListed(listed);
    this.tabList.emitActionRaw(UpsertPlayerInfoPacket.Action.UPDATE_LISTED, upsertEntry);
    return this;
  }

  /**
   * Sets the listed flag without triggering a network update.
   *
   * @param listed whether this entry should be listed
   */
  void setListedWithoutUpdate(final boolean listed) {
    this.listed = listed;
  }

  /**
   * Returns the list order used to sort this entry in the tab list.
   *
   * @return the numeric sort order
   */
  @Override
  public int getListOrder() {
    return listOrder;
  }

  /**
   * Sets the list order and sends an update to the client if supported by the protocol version.
   *
   * @param listOrder the new list order value
   * @return this tab list entry for method chaining
   */
  @Override
  public VelocityTabListEntry setListOrder(final int listOrder) {
    this.listOrder = listOrder;
    if (tabList.getPlayer().getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_21_2)) {
      UpsertPlayerInfoPacket.Entry upsertEntry = this.tabList.createRawEntry(this);
      upsertEntry.setListOrder(listOrder);
      tabList.emitActionRaw(UpsertPlayerInfoPacket.Action.UPDATE_LIST_ORDER, upsertEntry);
    }

    return this;
  }

  /**
   * Sets the list order silently.
   *
   * @param listOrder the tab list order to assign without notifying the client
   */
  void setListOrderWithoutUpdate(final int listOrder) {
    this.listOrder = listOrder;
  }

  /**
   * Returns whether the player's hat layer is shown.
   *
   * @return {@code true} if hat is visible, {@code false} otherwise
   */
  @Override
  public boolean isShowHat() {
    return showHat;
  }

  /**
   * Sets whether the hat layer should be shown and sends an update if supported.
   *
   * @param showHat {@code true} to show hat layer, {@code false} to hide
   * @return this tab list entry for method chaining
   */
  @Override
  public VelocityTabListEntry setShowHat(final boolean showHat) {
    this.showHat = showHat;
    if (tabList.getPlayer().getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_21_4)) {
      UpsertPlayerInfoPacket.Entry upsertEntry = this.tabList.createRawEntry(this);
      upsertEntry.setShowHat(showHat);
      tabList.emitActionRaw(UpsertPlayerInfoPacket.Action.UPDATE_HAT, upsertEntry);
    }

    return this;
  }

  /**
   * Sets the hat visibility flag silently.
   *
   * @param showHat whether to show the player's hat layer without sending a client update
   */
  void setShowHatWithoutUpdate(final boolean showHat) {
    this.showHat = showHat;
  }
}
