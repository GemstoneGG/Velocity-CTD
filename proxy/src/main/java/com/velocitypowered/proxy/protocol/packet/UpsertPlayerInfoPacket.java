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

package com.velocitypowered.proxy.protocol.packet;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import com.velocitypowered.proxy.protocol.packet.chat.RemoteChatSession;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the packet for updating or inserting player information.
 */
public class UpsertPlayerInfoPacket implements MinecraftPacket {

  /**
   * The set of actions to apply to player info entries.
   */
  private final EnumSet<Action> actions;

  /**
   * The list of player info entries included in this packet.
   */
  private final List<Entry> entries;

  /**
   * Constructs an empty {@code UpsertPlayerInfoPacket} with no actions or entries.
   */
  public UpsertPlayerInfoPacket() {
    this.actions = EnumSet.noneOf(Action.class);
    this.entries = new ArrayList<>();
  }

  /**
   * Constructs a {@code UpsertPlayerInfoPacket} with a single action.
   *
   * @param action the action to apply
   */
  public UpsertPlayerInfoPacket(final Action action) {
    this.actions = EnumSet.of(action);
    this.entries = new ArrayList<>();
  }

  /**
   * Constructs a {@code UpsertPlayerInfoPacket} with the specified actions and entries.
   *
   * @param actions the set of actions to apply
   * @param entries the list of player info entries
   */
  public UpsertPlayerInfoPacket(final EnumSet<Action> actions, final List<Entry> entries) {
    this.actions = actions;
    this.entries = entries;
  }

  /**
   * Returns the list of player info entries.
   *
   * @return the entries
   */
  public List<Entry> getEntries() {
    return entries;
  }

  /**
   * Returns the set of actions to apply.
   *
   * @return the actions
   */
  public EnumSet<Action> getActions() {
    return actions;
  }

  /**
   * Checks whether this packet contains the specified action.
   *
   * @param action the action to check
   * @return {@code true} if the action is present, otherwise {@code false}
   */
  public boolean containsAction(final Action action) {
    return this.actions.contains(action);
  }

  /**
   * Adds an action to this packet.
   *
   * @param action the action to add
   */
  public void addAction(final Action action) {
    this.actions.add(action);
  }

  /**
   * Adds multiple actions to this packet.
   *
   * @param actions the actions to add
   */
  public void addAllActions(final Collection<? extends Action> actions) {
    this.actions.addAll(actions);
  }

  /**
   * Adds a player info entry to this packet.
   *
   * @param entry the entry to add
   */
  public void addEntry(final Entry entry) {
    this.entries.add(entry);
  }

  /**
   * Adds multiple player info entries to this packet.
   *
   * @param entries the entries to add
   */
  public void addAllEntries(final Collection<? extends Entry> entries) {
    this.entries.addAll(entries);
  }

  @Override
  public final void decode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                           final ProtocolVersion protocolVersion) {
    Action[] actions = Action.class.getEnumConstants();
    byte[] bytes = new byte[-Math.floorDiv(-actions.length, 8)];
    buf.readBytes(bytes);
    BitSet actionSet = BitSet.valueOf(bytes);

    for (int idx = 0; idx < actions.length; idx++) {
      if (actionSet.get(idx)) {
        addAction(actions[idx]);
      }
    }

    int length = ProtocolUtils.readVarInt(buf);
    for (int idx = 0; idx < length; idx++) {
      Entry entry = new Entry(ProtocolUtils.readUuid(buf));
      for (Action action : this.actions) {
        action.read.read(protocolVersion, buf, entry);
      }

      addEntry(entry);
    }
  }

  @Override
  public final void encode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                           final ProtocolVersion protocolVersion) {
    Action[] actions = Action.class.getEnumConstants();
    BitSet set = new BitSet(actions.length);
    for (int idx = 0; idx < actions.length; idx++) {
      set.set(idx, this.actions.contains(actions[idx]));
    }

    byte[] bytes = set.toByteArray();
    buf.writeBytes(Arrays.copyOf(bytes, -Math.floorDiv(-actions.length, 8)));

    ProtocolUtils.writeVarInt(buf, this.entries.size());
    for (Entry entry : this.entries) {
      ProtocolUtils.writeUuid(buf, entry.profileId);

      for (Action action : this.actions) {
        action.write.write(protocolVersion, buf, entry);
      }
    }
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  /**
   * Reads a fixed bit set from the buffer.
   *
   * @param buf the buffer to read from
   * @param param0 the size of the bit set
   * @return the bit set read from the buffer
   */
  public BitSet readFixedBitSet(final ByteBuf buf, final int param0) {
    byte[] var0 = new byte[-Math.floorDiv(-param0, 8)];
    buf.readBytes(var0);
    return BitSet.valueOf(var0);
  }

  /**
   * Represents the possible actions in the player info packet.
   */
  public enum Action {

    /**
     * Adds a player to the tab list. This action includes the full {@link GameProfile},
     * including the player's UUID, name, and properties.
     */
    ADD_PLAYER((ignored, buf, info) -> {
      info.profile = new GameProfile(
          info.profileId,
          ProtocolUtils.readString(buf, 16),
          ProtocolUtils.readProperties(buf)
      );
    }, (ignored, buf, info) -> {
      ProtocolUtils.writeString(buf, info.profile.getName());
      ProtocolUtils.writeProperties(buf, info.profile.getProperties());
    }),

    /**
     * Initializes the remote chat session for the player.
     * This is part of the secure chat system introduced in 1.19.
     */
    INITIALIZE_CHAT((version, buf, info) -> {
      if (buf.readBoolean()) {
        info.chatSession = new RemoteChatSession(version, buf);
      } else {
        info.chatSession = null;
      }
    }, (ignored, buf, info) -> {
      buf.writeBoolean(info.chatSession != null);
      if (info.chatSession != null) {
        info.chatSession.write(buf);
      }
    }),

    /**
     * Updates the player's current game mode (e.g., survival, creative).
     */
    UPDATE_GAME_MODE((ignored, buf, info) ->
            info.gameMode = ProtocolUtils.readVarInt(buf), (ignored, buf, info) ->
            ProtocolUtils.writeVarInt(buf, info.gameMode)),

    /**
     * Updates whether the player should be listed in the tab list.
     */
    UPDATE_LISTED((ignored, buf, info) ->
            info.listed = buf.readBoolean(), (ignored, buf, info) ->
            buf.writeBoolean(info.listed)),

    /**
     * Updates the player's network latency (ping) in milliseconds.
     */
    UPDATE_LATENCY((ignored, buf, info) ->
            info.latency = ProtocolUtils.readVarInt(buf), (ignored, buf, info) ->
            ProtocolUtils.writeVarInt(buf, info.latency)),

    /**
     * Updates the display name shown for the player in the tab list.
     * This overrides the default name with a component.
     */
    UPDATE_DISPLAY_NAME((version, buf, info) -> {
      if (buf.readBoolean()) {
        info.displayName = ComponentHolder.read(buf, version);
      } else {
        info.displayName = null;
      }
    }, (version, buf, info) -> {
      buf.writeBoolean(info.displayName != null);
      if (info.displayName != null) {
        info.displayName.write(buf);
      }
    }),

    /**
     * Updates the player's order in the tab list. Entries with lower numbers appear first.
     */
    UPDATE_LIST_ORDER((version, buf, info) ->
            info.listOrder = ProtocolUtils.readVarInt(buf), (version, buf, info) ->
            ProtocolUtils.writeVarInt(buf, info.listOrder)),

    /**
     * Updates whether the player's hat layer should be rendered in the tab list.
     */
    UPDATE_HAT((version, buf, info) ->
            info.showHat = buf.readBoolean(), (version, buf, info) ->
            buf.writeBoolean(info.showHat));

    /**
     * Function to read this action's data from the buffer.
     */
    private final Read read;

    /**
     * Function to write this action's data to the buffer.
     */
    private final Write write;

    Action(final Read read, final Write write) {
      this.read = read;
      this.write = write;
    }

    private interface Read {
      void read(ProtocolVersion version, ByteBuf buf, Entry info);
    }

    private interface Write {
      void write(ProtocolVersion version, ByteBuf buf, Entry info);
    }
  }

  /**
   * Represents an entry in the player info packet.
   */
  public static class Entry {

    /**
     * The UUID of the player.
     */
    private final UUID profileId;

    /**
     * The full game profile of the player.
     */
    private GameProfile profile;

    /**
     * Whether the player should be listed in the tab list.
     */
    private boolean listed;

    /**
     * The player's latency in milliseconds.
     */
    private int latency;

    /**
     * The player's game mode (survival, creative, etc.).
     */
    private int gameMode;

    /**
     * The display name override to show in the tab list.
     */
    @Nullable private ComponentHolder displayName;

    /**
     * Whether the player's hat layer should be shown.
     */
    private boolean showHat;

    /**
     * The custom ordering index for tab list entries.
     */
    private int listOrder;

    /**
     * The remote chat session for this player (used in secure chat).
     */
    @Nullable private RemoteChatSession chatSession;

    /**
     * Constructs a new {@code Entry} with the given profile UUID.
     *
     * @param uuid the UUID of the player's profile
     */
    public Entry(final UUID uuid) {
      this.profileId = uuid;
    }

    /**
     * Returns the UUID of the player.
     *
     * @return the profile UUID
     */
    public UUID getProfileId() {
      return profileId;
    }

    /**
     * Returns the {@link GameProfile} of the player.
     *
     * @return the game profile
     */
    public GameProfile getProfile() {
      return profile;
    }

    /**
     * Returns whether the player should be listed in the tab list.
     *
     * @return {@code true} if listed; {@code false} otherwise
     */
    public boolean isListed() {
      return listed;
    }

    /**
     * Returns the player's latency (ping).
     *
     * @return the latency in milliseconds
     */
    public int getLatency() {
      return latency;
    }

    /**
     * Returns the player's current game mode.
     *
     * @return the game mode ID
     */
    public int getGameMode() {
      return gameMode;
    }

    /**
     * Returns the display name shown in the tab list.
     *
     * @return the display name, or {@code null} if not set
     */
    @Nullable
    public ComponentHolder getDisplayName() {
      return displayName;
    }

    /**
     * Returns whether the player's hat layer should be shown.
     *
     * @return {@code true} if the hat layer is shown; {@code false} otherwise
     */
    public boolean isShowHat() {
      return showHat;
    }

    /**
     * Returns the player's order in the tab list.
     *
     * @return the list order value
     */
    public int getListOrder() {
      return listOrder;
    }

    /**
     * Returns the player's remote chat session information.
     *
     * @return the chat session, or {@code null} if not present
     */
    @Nullable
    public RemoteChatSession getChatSession() {
      return chatSession;
    }

    /**
     * Sets the player's {@link GameProfile}.
     *
     * @param profile the profile to set
     */
    public void setProfile(final GameProfile profile) {
      this.profile = profile;
    }

    /**
     * Sets whether the player should be listed in the tab list.
     *
     * @param listed {@code true} to list the player, {@code false} otherwise
     */
    public void setListed(final boolean listed) {
      this.listed = listed;
    }

    /**
     * Sets the player's latency.
     *
     * @param latency the latency in milliseconds
     */
    public void setLatency(final int latency) {
      this.latency = latency;
    }

    /**
     * Sets the player's game mode.
     *
     * @param gameMode the game mode ID
     */
    public void setGameMode(final int gameMode) {
      this.gameMode = gameMode;
    }

    /**
     * Sets the player's display name in the tab list.
     *
     * @param displayName the display name component, or {@code null} to clear
     */
    public void setDisplayName(@Nullable final ComponentHolder displayName) {
      this.displayName = displayName;
    }

    /**
     * Sets whether the player's hat layer should be rendered.
     *
     * @param showHat {@code true} to show the hat; {@code false} otherwise
     */
    public void setShowHat(final boolean showHat) {
      this.showHat = showHat;
    }

    /**
     * Sets the player's order in the tab list.
     *
     * @param listOrder the list order value
     */
    public void setListOrder(final int listOrder) {
      this.listOrder = listOrder;
    }

    /**
     * Sets the player's remote chat session.
     *
     * @param chatSession the chat session to set, or {@code null} to clear
     */
    public void setChatSession(@Nullable final RemoteChatSession chatSession) {
      this.chatSession = chatSession;
    }

    @Override
    public final String toString() {
      return "Entry{"
          + "profileId=" + profileId
          + ", profile=" + profile
          + ", listed=" + listed
          + ", latency=" + latency
          + ", gameMode=" + gameMode
          + ", displayName=" + displayName
          + ", listOrder=" + listOrder
          + ", chatSession=" + chatSession
          + '}';
    }
  }
}
