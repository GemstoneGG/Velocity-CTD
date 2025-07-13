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
                              final int latency, final int gameMode, @Nullable final ChatSession session,
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

  @Override
  public final @Nullable ChatSession getChatSession() {
    return this.session;
  }

  @Override
  public final TabList getTabList() {
    return this.tabList;
  }

  @Override
  public final GameProfile getProfile() {
    return this.profile;
  }

  @Override
  public final Optional<Component> getDisplayNameComponent() {
    return Optional.ofNullable(displayName);
  }

  @Override
  public final TabListEntry setDisplayName(@Nullable final Component displayName) {
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
  void setDisplayNameWithoutUpdate(@Nullable final Component displayName) {
    this.displayName = displayName;
  }

  @Override
  public final int getLatency() {
    return this.latency;
  }

  @Override
  public final TabListEntry setLatency(final int latency) {
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

  @Override
  public final int getGameMode() {
    return this.gameMode;
  }

  @Override
  public final TabListEntry setGameMode(final int gameMode) {
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
  protected void setChatSession(@Nullable final ChatSession session) {
    this.session = session;
  }

  @Override
  public final boolean isListed() {
    return listed;
  }

  @Override
  public final VelocityTabListEntry setListed(final boolean listed) {
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

  @Override
  public final int getListOrder() {
    return listOrder;
  }

  @Override
  public final VelocityTabListEntry setListOrder(final int listOrder) {
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

  @Override
  public final boolean isShowHat() {
    return showHat;
  }

  @Override
  public final VelocityTabListEntry setShowHat(final boolean showHat) {
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
