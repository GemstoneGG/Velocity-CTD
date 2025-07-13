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

import com.google.common.collect.ImmutableList;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.crypto.IdentifiedKey;
import com.velocitypowered.api.proxy.player.TabListEntry;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a legacy player list item packet, which is used to modify the player list in a Minecraft client.
 * The packet can add, remove, or update player entries (e.g., updating gamemode, latency, or display names).
 */
public class LegacyPlayerListItemPacket implements MinecraftPacket {

  /**
   * Action code for adding a player to the player list.
   */
  public static final int ADD_PLAYER = 0;

  /**
   * Action code for updating a player's game mode in the player list.
   */
  public static final int UPDATE_GAMEMODE = 1;

  /**
   * Action code for updating a player's latency in the player list.
   */
  public static final int UPDATE_LATENCY = 2;

  /**
   * Action code for updating a player's display name in the player list.
   */
  public static final int UPDATE_DISPLAY_NAME = 3;

  /**
   * Action code for removing a player from the player list.
   */
  public static final int REMOVE_PLAYER = 4;

  /**
   * The action type this packet represents. One of the {@code ADD_PLAYER},
   * {@code UPDATE_GAMEMODE}, {@code UPDATE_LATENCY}, {@code UPDATE_DISPLAY_NAME},
   * or {@code REMOVE_PLAYER} constants.
   */
  private int action;

  /**
   * The list of items representing player data changes in this packet.
   */
  private final List<Item> items = new ArrayList<>();

  /**
   * Constructs a new {@link LegacyPlayerListItemPacket} with the specified action and list of items.
   *
   * @param action the type of player list action to perform
   * @param items the list of {@link Item} instances to apply
   */
  public LegacyPlayerListItemPacket(final int action, final List<Item> items) {
    this.action = action;
    this.items.addAll(items);
  }

  /**
   * Constructs an empty {@link LegacyPlayerListItemPacket} to be populated during decoding.
   */
  public LegacyPlayerListItemPacket() {
  }

  /**
   * Returns the action type represented by this packet.
   *
   * @return the action code
   */
  public int getAction() {
    return action;
  }

  /**
   * Returns the list of items included in this packet.
   *
   * @return the list of {@link Item} instances
   */
  public List<Item> getItems() {
    return items;
  }

  @Override
  public final void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      action = ProtocolUtils.readVarInt(buf);
      int length = ProtocolUtils.readVarInt(buf);

      for (int i = 0; i < length; i++) {
        Item item = new Item(ProtocolUtils.readUuid(buf));
        items.add(item);
        switch (action) {
          case ADD_PLAYER -> {
            item.setName(ProtocolUtils.readString(buf));
            item.setProperties(ProtocolUtils.readProperties(buf));
            item.setGameMode(ProtocolUtils.readVarInt(buf));
            item.setLatency(ProtocolUtils.readVarInt(buf));
            item.setDisplayName(readOptionalComponent(buf, version));

            if (version.noLessThan(ProtocolVersion.MINECRAFT_1_19)) {
              if (buf.readBoolean()) {
                item.setPlayerKey(ProtocolUtils.readPlayerKey(version, buf));
              }
            }
          }
          case UPDATE_GAMEMODE -> item.setGameMode(ProtocolUtils.readVarInt(buf));
          case UPDATE_LATENCY -> item.setLatency(ProtocolUtils.readVarInt(buf));
          case UPDATE_DISPLAY_NAME -> item.setDisplayName(readOptionalComponent(buf, version));
          case REMOVE_PLAYER -> {
            // Do nothing, all that is needed is the UUID
          }
          default -> throw new UnsupportedOperationException("Unknown action " + action);
        }
      }
    } else {
      Item item = new Item();
      item.setName(ProtocolUtils.readString(buf));
      action = buf.readBoolean() ? ADD_PLAYER : REMOVE_PLAYER;
      item.setLatency(buf.readShort());
      items.add(item);
    }
  }

  private static @Nullable Component readOptionalComponent(final ByteBuf buf, final ProtocolVersion version) {
    if (buf.readBoolean()) {
      return ProtocolUtils.getJsonChatSerializer(version)
          .deserialize(ProtocolUtils.readString(buf));
    }

    return null;
  }

  @Override
  public final void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      ProtocolUtils.writeVarInt(buf, action);
      ProtocolUtils.writeVarInt(buf, items.size());
      for (Item item : items) {
        UUID uuid = item.getUuid();
        assert uuid != null : "UUID-less entry serialization attempt - 1.7 component!";

        ProtocolUtils.writeUuid(buf, uuid);
        switch (action) {
          case ADD_PLAYER -> {
            ProtocolUtils.writeString(buf, item.getName());
            ProtocolUtils.writeProperties(buf, item.getProperties());
            ProtocolUtils.writeVarInt(buf, item.getGameMode());
            ProtocolUtils.writeVarInt(buf, item.getLatency());
            writeDisplayName(buf, item.getDisplayName(), version);
            if (version.noLessThan(ProtocolVersion.MINECRAFT_1_19)) {
              if (item.getPlayerKey() != null) {
                buf.writeBoolean(true);
                ProtocolUtils.writePlayerKey(buf, item.getPlayerKey());
              } else {
                buf.writeBoolean(false);
              }
            }
          }
          case UPDATE_GAMEMODE -> ProtocolUtils.writeVarInt(buf, item.getGameMode());
          case UPDATE_LATENCY -> ProtocolUtils.writeVarInt(buf, item.getLatency());
          case UPDATE_DISPLAY_NAME -> writeDisplayName(buf, item.getDisplayName(), version);
          case REMOVE_PLAYER -> {
            // Do nothing, all that is needed is the UUID
          }
          default -> throw new UnsupportedOperationException("Unknown action " + action);
        }
      }
    } else {
      Item item = items.get(0);
      Component displayNameComponent = item.getDisplayName();
      if (displayNameComponent != null) {
        String displayName = LegacyComponentSerializer.legacySection()
            .serialize(displayNameComponent);
        ProtocolUtils.writeString(buf,
            displayName.length() > 16 ? displayName.substring(0, 16) : displayName);
      } else {
        ProtocolUtils.writeString(buf, item.getName());
      }

      buf.writeBoolean(action != REMOVE_PLAYER);
      buf.writeShort(item.getLatency());
    }
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  private void writeDisplayName(final ByteBuf buf, @Nullable final Component displayName,
                                final ProtocolVersion version) {
    buf.writeBoolean(displayName != null);
    if (displayName != null) {
      ProtocolUtils.writeString(buf, ProtocolUtils.getJsonChatSerializer(version)
          .serialize(displayName));
    }
  }

  /**
   * Represents an individual item in the player list, containing the player's details such as UUID, name,
   * game mode, latency, and optionally a display name and player key.
   */
  public static class Item {

    /**
     * The UUID of the player associated with this list entry.
     */
    private final UUID uuid;

    /**
     * The name of the player.
     */
    private String name = "";

    /**
     * The properties of the player, typically containing skin data.
     */
    private List<GameProfile.Property> properties = ImmutableList.of();

    /**
     * The player's game mode (e.g., survival, creative).
     */
    private int gameMode;

    /**
     * The player's latency (ping) in milliseconds.
     */
    private int latency;

    /**
     * The optional display name to show in the player list instead of the username.
     */
    private @Nullable Component displayName;

    /**
     * The optional cryptographic player key, available in 1.19+.
     */
    private @Nullable IdentifiedKey playerKey;

    /**
     * Constructs an empty {@link Item} with a {@code null} UUID.
     * Intended for legacy packet compatibility where UUIDs may not be used.
     */
    public Item() {
      uuid = null;
    }

    /**
     * Constructs a new {@link Item} with the specified player UUID.
     *
     * @param uuid the UUID of the player
     */
    public Item(final UUID uuid) {
      this.uuid = uuid;
    }

    /**
     * Creates an {@link Item} instance from a {@link TabListEntry}.
     * This method extracts relevant data from the {@link TabListEntry} such as
     * the player's profile ID, name, properties, latency, game mode, player key,
     * and display name, and uses them to populate a new {@code Item}.
     *
     * @param entry the {@link TabListEntry} from which to extract data
     * @return an {@link Item} populated with data from the {@link TabListEntry}
     */
    public static Item from(final TabListEntry entry) {
      return new Item(entry.getProfile().getId())
          .setName(entry.getProfile().getName())
          .setProperties(entry.getProfile().getProperties())
          .setLatency(entry.getLatency())
          .setGameMode(entry.getGameMode())
          .setPlayerKey(entry.getIdentifiedKey())
          .setDisplayName(entry.getDisplayNameComponent().orElse(null));
    }

    /**
     * Returns the UUID of the player represented by this item.
     *
     * @return the UUID of the player, or {@code null} if not set
     */
    public @Nullable UUID getUuid() {
      return uuid;
    }

    /**
     * Returns the name of the player.
     *
     * @return the player's name
     */
    public String getName() {
      return name;
    }

    /**
     * Sets the name of the player.
     *
     * @param name the player's name
     * @return this item instance
     */
    public Item setName(final String name) {
      this.name = name;
      return this;
    }

    /**
     * Returns the list of properties associated with the player's game profile.
     *
     * @return the profile properties
     */
    public List<GameProfile.Property> getProperties() {
      return properties;
    }

    /**
     * Sets the profile properties of the player.
     *
     * @param properties the properties to set
     * @return this item instance
     */
    public Item setProperties(final List<GameProfile.Property> properties) {
      this.properties = properties;
      return this;
    }

    /**
     * Returns the player's game mode.
     *
     * @return the game mode value
     */
    public int getGameMode() {
      return gameMode;
    }

    /**
     * Sets the game mode for the player.
     *
     * @param gameMode the game mode value
     * @return this item instance
     */
    public Item setGameMode(final int gameMode) {
      this.gameMode = gameMode;
      return this;
    }

    /**
     * Returns the player's latency (ping).
     *
     * @return the latency value
     */
    public int getLatency() {
      return latency;
    }

    /**
     * Sets the latency (ping) for the player.
     *
     * @param latency the latency value
     * @return this item instance
     */
    public Item setLatency(final int latency) {
      this.latency = latency;
      return this;
    }

    /**
     * Returns the display name component for the player, or {@code null} if none.
     *
     * @return the display name component
     */
    public @Nullable Component getDisplayName() {
      return displayName;
    }

    /**
     * Sets the display name for the player.
     *
     * @param displayName the display name component
     * @return this item instance
     */
    public Item setDisplayName(@Nullable final Component displayName) {
      this.displayName = displayName;
      return this;
    }

    /**
     * Sets the player’s identified key for secure profile handling.
     *
     * @param playerKey the identified key
     * @return this item instance
     */
    public Item setPlayerKey(final IdentifiedKey playerKey) {
      this.playerKey = playerKey;
      return this;
    }

    /**
     * Returns the player's identified key used for profile authentication.
     *
     * @return the identified key, or {@code null} if not present
     */
    public @Nullable IdentifiedKey getPlayerKey() {
      return playerKey;
    }
  }
}
