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
import com.google.common.collect.Maps;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.player.ChatSession;
import com.velocitypowered.api.proxy.player.TabListEntry;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.console.VelocityConsole;
import com.velocitypowered.proxy.protocol.packet.RemovePlayerInfoPacket;
import com.velocitypowered.proxy.protocol.packet.UpsertPlayerInfoPacket;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import com.velocitypowered.proxy.protocol.packet.chat.RemoteChatSession;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import net.kyori.adventure.text.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Base class for handling tab lists.
 */
public class VelocityTabList implements InternalTabList {

  /**
   * Logger used to report unusual tab list activity or inconsistencies.
   */
  private static final Logger logger = LogManager.getLogger(VelocityConsole.class);

  /**
   * The connected player that owns this tab list.
   */
  private final ConnectedPlayer player;

  /**
   * The connection used to send player info packets.
   */
  private final MinecraftConnection connection;

  /**
   * The current entries on this player's tab list, keyed by profile UUID.
   */
  private final ConcurrentMap<UUID, VelocityTabListEntry> entries;

  /**
   * Constructs the instance.
   *
   * @param player player associated with this tab list
   */
  public VelocityTabList(final ConnectedPlayer player) {
    this.player = player;
    this.connection = player.getConnection();
    this.entries = Maps.newConcurrentMap();
  }

  /**
   * Returns the {@link Player} instance that owns this tab list.
   *
   * @return the player associated with this tab list
   */
  @Override
  public Player getPlayer() {
    return player;
  }

  /**
   * Sets the header and footer components of the player's tab list.
   *
   * <p>This will cause the client to display the specified components at the top and bottom of
   * the player list overlay.</p>
   *
   * @param header the component to display at the top of the tab list (must not be {@code null})
   * @param footer the component to display at the bottom of the tab list (must not be {@code null})
   * @throws NullPointerException if {@code header} or {@code footer} is {@code null}
   */
  @Override
  public void setHeaderAndFooter(final Component header, final Component footer) {
    Preconditions.checkNotNull(header, "header");
    Preconditions.checkNotNull(footer, "footer");
    this.player.sendPlayerListHeaderAndFooter(header, footer);
  }

  /**
   * Removes the header and footer components from the tab list display.
   */
  @Override
  public void clearHeaderAndFooter() {
    this.player.clearPlayerListHeaderAndFooter();
  }

  /**
   * Adds or updates a {@link TabListEntry} in the player's tab list.
   *
   * <p>If the entry already exists, differences will be compared and the
   * appropriate update actions will be sent to the client.</p>
   *
   * @param entry1 the tab list entry to add or merge
   */
  @Override
  public void addEntry(final TabListEntry entry1) {
    VelocityTabListEntry entry;
    if (entry1 instanceof VelocityTabListEntry) {
      entry = (VelocityTabListEntry) entry1;
    } else {
      entry = new VelocityTabListEntry(this, entry1.getProfile(),
          entry1.getDisplayNameComponent().orElse(null),
          entry1.getLatency(), entry1.getGameMode(), entry1.getChatSession(), entry1.isListed(), entry1.getListOrder(), entry1.isShowHat());
    }

    EnumSet<UpsertPlayerInfoPacket.Action> actions = EnumSet
            .noneOf(UpsertPlayerInfoPacket.Action.class);
    UpsertPlayerInfoPacket.Entry playerInfoEntry = new UpsertPlayerInfoPacket
            .Entry(entry.getProfile().getId());

    Preconditions.checkNotNull(entry.getProfile(), "Profile cannot be null");
    Preconditions.checkNotNull(entry.getProfile().getId(), "Profile ID cannot be null");

    this.entries.compute(entry.getProfile().getId(), (uuid, previousEntry) -> {
      if (previousEntry != null) {
        // We should merge entries here
        if (previousEntry.equals(entry)) {
          return previousEntry; // Nothing else to do, this entry is perfect
        }

        if (!Objects.equals(previousEntry.getDisplayNameComponent().orElse(null),
                entry.getDisplayNameComponent().orElse(null))) {
          actions.add(UpsertPlayerInfoPacket.Action.UPDATE_DISPLAY_NAME);
          playerInfoEntry.setDisplayName(entry.getDisplayNameComponent().isEmpty()
              ? null : new ComponentHolder(player.getProtocolVersion(), entry.getDisplayNameComponent().get())
          );
        }

        if (!Objects.equals(previousEntry.getLatency(), entry.getLatency())) {
          actions.add(UpsertPlayerInfoPacket.Action.UPDATE_LATENCY);
          playerInfoEntry.setLatency(entry.getLatency());
        }

        if (!Objects.equals(previousEntry.getGameMode(), entry.getGameMode())) {
          actions.add(UpsertPlayerInfoPacket.Action.UPDATE_GAME_MODE);
          playerInfoEntry.setGameMode(entry.getGameMode());
        }

        if (!Objects.equals(previousEntry.isListed(), entry.isListed())) {
          actions.add(UpsertPlayerInfoPacket.Action.UPDATE_LISTED);
          playerInfoEntry.setListed(entry.isListed());
        }

        if (!Objects.equals(previousEntry.getListOrder(), entry.getListOrder())
            && player.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_21_2)) {
          actions.add(UpsertPlayerInfoPacket.Action.UPDATE_LIST_ORDER);
          playerInfoEntry.setListOrder(entry.getListOrder());
        }

        if (!Objects.equals(previousEntry.isShowHat(), entry.isShowHat())
                && player.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_21_4)) {
          actions.add(UpsertPlayerInfoPacket.Action.UPDATE_HAT);
          playerInfoEntry.setShowHat(entry.isShowHat());
        }

        if (!Objects.equals(previousEntry.getChatSession(), entry.getChatSession())) {
          ChatSession from = entry.getChatSession();
          if (from != null) {
            actions.add(UpsertPlayerInfoPacket.Action.INITIALIZE_CHAT);
            playerInfoEntry.setChatSession(
                    new RemoteChatSession(from.getSessionId(), from.getIdentifiedKey()));
          }
        }
      } else {
        actions.addAll(EnumSet.of(UpsertPlayerInfoPacket.Action.ADD_PLAYER,
                UpsertPlayerInfoPacket.Action.UPDATE_LATENCY,
                UpsertPlayerInfoPacket.Action.UPDATE_LISTED));
        playerInfoEntry.setProfile(entry.getProfile());
        if (entry.getDisplayNameComponent().isPresent()) {
          actions.add(UpsertPlayerInfoPacket.Action.UPDATE_DISPLAY_NAME);
          playerInfoEntry.setDisplayName(entry.getDisplayNameComponent().isEmpty()
              ? null : new ComponentHolder(player.getProtocolVersion(), entry.getDisplayNameComponent().get())
          );
        }

        if (entry.getChatSession() != null) {
          actions.add(UpsertPlayerInfoPacket.Action.INITIALIZE_CHAT);
          ChatSession from = entry.getChatSession();
          playerInfoEntry.setChatSession(
                  new RemoteChatSession(from.getSessionId(), from.getIdentifiedKey()));
        }

        if (entry.getGameMode() != -1 && entry.getGameMode() != 256) {
          actions.add(UpsertPlayerInfoPacket.Action.UPDATE_GAME_MODE);
          playerInfoEntry.setGameMode(entry.getGameMode());
        }

        playerInfoEntry.setLatency(entry.getLatency());
        playerInfoEntry.setListed(entry.isListed());
        if (entry.getListOrder() != 0
            && player.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_21_2)) {
          actions.add(UpsertPlayerInfoPacket.Action.UPDATE_LIST_ORDER);
          playerInfoEntry.setListOrder(entry.getListOrder());
        }

        if (!entry.isShowHat() && player.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_21_4)) {
          actions.add(UpsertPlayerInfoPacket.Action.UPDATE_HAT);
          playerInfoEntry.setShowHat(entry.isShowHat());
        }
      }
      return entry;
    });

    if (!actions.isEmpty()) {
      this.connection.write(new UpsertPlayerInfoPacket(actions, List.of(playerInfoEntry)));
    }
  }

  /**
   * Removes a tab list entry by UUID and notifies the client.
   *
   * @param uuid the UUID of the entry to remove
   * @return an optional containing the removed entry, or empty if not found
   */
  @Override
  public Optional<TabListEntry> removeEntry(final UUID uuid) {
    this.connection.write(new RemovePlayerInfoPacket(List.of(uuid)));
    return Optional.ofNullable(this.entries.remove(uuid));
  }

  /**
   * Checks if a tab list entry exists for the specified UUID.
   *
   * @param uuid the UUID of the entry to check
   * @return {@code true} if the entry exists, otherwise {@code false}
   */
  @Override
  public boolean containsEntry(final UUID uuid) {
    return this.entries.containsKey(uuid);
  }

  /**
   * Retrieves the tab list entry associated with the specified UUID.
   *
   * @param uuid the UUID of the entry
   * @return an optional containing the entry, or empty if not found
   */
  @Override
  public Optional<TabListEntry> getEntry(final UUID uuid) {
    return Optional.ofNullable(this.entries.get(uuid));
  }

  /**
   * Returns all current tab list entries.
   *
   * @return a collection of all {@link TabListEntry} instances
   */
  @Override
  public Collection<TabListEntry> getEntries() {
    return List.copyOf(this.entries.values());
  }

  /**
   * Clears all tab list entries and sends a {@link RemovePlayerInfoPacket} to the client.
   */
  @Override
  public void clearAll() {
    this.connection.delayedWrite(new RemovePlayerInfoPacket(
            new ArrayList<>(this.entries.keySet())));
    clearAllSilent();
  }

  /**
   * Clears all tab list entries silently without sending any packets to the client.
   */
  @Override
  public void clearAllSilent() {
    this.entries.clear();
  }

  /**
   * Creates a new {@link TabListEntry} for this tab list.
   *
   * @param profile the game profile
   * @param displayName the display name (nullable)
   * @param latency the ping value
   * @param gameMode the game mode
   * @param chatSession the chat session (nullable)
   * @param listed whether the player is listed
   * @param listOrder the list order index
   * @param showHat whether to show the player's hat layer
   * @return the new tab list entry
   */
  @Override
  public TabListEntry buildEntry(final GameProfile profile, final @Nullable Component displayName, final int latency,
                                 final int gameMode, final @Nullable ChatSession chatSession, final boolean listed, final int listOrder,
                                 final boolean showHat) {
    return new VelocityTabListEntry(this, profile, displayName, latency, gameMode, chatSession, listed, listOrder, showHat);
  }

  /**
   * Processes an incoming {@link UpsertPlayerInfoPacket}, updating or adding
   * tab list entries based on its contents.
   *
   * @param infoPacket the packet containing tab list updates
   */
  @Override
  public void processUpdate(final UpsertPlayerInfoPacket infoPacket) {
    for (UpsertPlayerInfoPacket.Entry entry : infoPacket.getEntries()) {
      processUpsert(infoPacket.getActions(), entry);
    }
  }

  /**
   * Creates a bare {@link UpsertPlayerInfoPacket.Entry} with only the UUID set.
   *
   * @param entry the tab list entry
   * @return a packet entry with the UUID
   */
  protected UpsertPlayerInfoPacket.Entry createRawEntry(final VelocityTabListEntry entry) {
    Preconditions.checkNotNull(entry, "entry");
    Preconditions.checkNotNull(entry.getProfile(), "Profile cannot be null");
    Preconditions.checkNotNull(entry.getProfile().getId(), "Profile ID cannot be null");
    return new UpsertPlayerInfoPacket.Entry(entry.getProfile().getId());
  }

  /**
   * Sends a packet to the client with a single update action for the specified entry.
   *
   * @param action the update action
   * @param entry the entry to apply the action to
   */
  protected void emitActionRaw(final UpsertPlayerInfoPacket.Action action,
                               final UpsertPlayerInfoPacket.Entry entry) {
    this.connection.write(new UpsertPlayerInfoPacket(EnumSet.of(action), List.of(entry)));
  }

  private void processUpsert(final EnumSet<UpsertPlayerInfoPacket.Action> actions,
                             final UpsertPlayerInfoPacket.Entry entry) {
    Preconditions.checkNotNull(entry.getProfileId(), "Profile ID cannot be null");
    UUID profileId = entry.getProfileId();
    VelocityTabListEntry currentEntry = this.entries.get(profileId);
    if (actions.contains(UpsertPlayerInfoPacket.Action.ADD_PLAYER)) {
      if (currentEntry == null) {
        this.entries.put(profileId,
            currentEntry = new VelocityTabListEntry(
                this,
                entry.getProfile(),
                null,
                0,
                -1,
                null,
                false,
                0,
                true
            )
        );
      } else {
        logger.debug("Received an add player packet for an existing entry; this does nothing.");
      }
    } else if (currentEntry == null) {
      logger.debug(
          "Received a partial player before an ADD_PLAYER action; profile could not be built. {}",
          entry);
      return;
    }

    if (actions.contains(UpsertPlayerInfoPacket.Action.UPDATE_GAME_MODE)) {
      currentEntry.setGameModeWithoutUpdate(entry.getGameMode());
    }

    if (actions.contains(UpsertPlayerInfoPacket.Action.UPDATE_LATENCY)) {
      currentEntry.setLatencyWithoutUpdate(entry.getLatency());
    }

    if (actions.contains(UpsertPlayerInfoPacket.Action.UPDATE_DISPLAY_NAME)) {
      currentEntry.setDisplayNameWithoutUpdate(entry.getDisplayName() != null
          ? entry.getDisplayName().getComponent() : null);
    }

    if (actions.contains(UpsertPlayerInfoPacket.Action.INITIALIZE_CHAT)) {
      currentEntry.setChatSession(entry.getChatSession());
    }

    if (actions.contains(UpsertPlayerInfoPacket.Action.UPDATE_LISTED)) {
      currentEntry.setListedWithoutUpdate(entry.isListed());
    }

    if (actions.contains(UpsertPlayerInfoPacket.Action.UPDATE_LIST_ORDER)) {
      currentEntry.setListOrderWithoutUpdate(entry.getListOrder());
    }
  }

  /**
   * Processes a {@link RemovePlayerInfoPacket} by removing the associated tab list entries.
   *
   * <p>This method is typically called when the server receives a packet instructing it to
   * remove one or more players from the tab list. It removes each corresponding entry from
   * the internal map by UUID.</p>
   *
   * @param infoPacket the packet containing the list of player UUIDs to remove
   */
  @Override
  public void processRemove(final RemovePlayerInfoPacket infoPacket) {
    for (UUID uuid : infoPacket.getProfilesToRemove()) {
      this.entries.remove(uuid);
    }
  }
}
