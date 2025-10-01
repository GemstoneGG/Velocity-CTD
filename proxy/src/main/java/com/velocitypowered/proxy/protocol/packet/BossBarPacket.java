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
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import com.velocitypowered.proxy.util.collect.Enum2IntMap;
import io.netty.buffer.ByteBuf;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a packet used to manage boss bars.
 * This packet can add, remove, or update a boss bar.
 */
public class BossBarPacket implements MinecraftPacket {

  /**
   * Maps {@link BossBar.Color} to protocol integer values.
   */
  private static final Enum2IntMap<BossBar.Color> COLORS_TO_PROTOCOL =
      new Enum2IntMap.Builder<>(BossBar.Color.class)
          .put(BossBar.Color.PINK, 0)
          .put(BossBar.Color.BLUE, 1)
          .put(BossBar.Color.RED, 2)
          .put(BossBar.Color.GREEN, 3)
          .put(BossBar.Color.YELLOW, 4)
          .put(BossBar.Color.PURPLE, 5)
          .put(BossBar.Color.WHITE, 6)
          .build();

  /**
   * Maps {@link BossBar.Overlay} to protocol integer values.
   */
  private static final Enum2IntMap<BossBar.Overlay> OVERLAY_TO_PROTOCOL =
      new Enum2IntMap.Builder<>(BossBar.Overlay.class)
          .put(BossBar.Overlay.PROGRESS, 0)
          .put(BossBar.Overlay.NOTCHED_6, 1)
          .put(BossBar.Overlay.NOTCHED_10, 2)
          .put(BossBar.Overlay.NOTCHED_12, 3)
          .put(BossBar.Overlay.NOTCHED_20, 4)
          .build();

  /**
   * Maps {@link BossBar.Flag} to protocol bit flags.
   */
  private static final Enum2IntMap<BossBar.Flag> FLAG_BITS_TO_PROTOCOL =
      new Enum2IntMap.Builder<>(BossBar.Flag.class)
          .put(BossBar.Flag.DARKEN_SCREEN, 0x1)
          .put(BossBar.Flag.PLAY_BOSS_MUSIC, 0x2)
          .put(BossBar.Flag.CREATE_WORLD_FOG, 0x4)
          .build();

  /**
   * Action ID for adding a boss bar.
   */
  public static final int ADD = 0;

  /**
   * Action ID for removing a boss bar.
   */
  public static final int REMOVE = 1;

  /**
   * Action ID for updating the progress of a boss bar.
   */
  public static final int UPDATE_PERCENT = 2;

  /**
   * Action ID for updating the name of a boss bar.
   */
  public static final int UPDATE_NAME = 3;

  /**
   * Action ID for updating the style (color/overlay) of a boss bar.
   */
  public static final int UPDATE_STYLE = 4;

  /**
   * Action ID for updating the flags of a boss bar.
   */
  public static final int UPDATE_PROPERTIES = 5;

  /**
   * The UUID identifying the boss bar instance.
   */
  private @Nullable UUID uuid;

  /**
   * The current action being performed on the boss bar.
   */
  private int action;

  /**
   * The display name of the boss bar.
   */
  private @Nullable ComponentHolder name;

  /**
   * The current progress (0.0–1.0) of the boss bar.
   */
  private float percent;

  /**
   * The color ID of the boss bar.
   */
  private int color;

  /**
   * The overlay ID of the boss bar.
   */
  private int overlay;

  /**
   * The combined bit flags for this boss bar.
   */
  private short flags;

  /**
   * Creates a packet to add a new boss bar.
   *
   * @param id the UUID of the boss bar
   * @param bar the {@link BossBar} instance
   * @param name the {@link ComponentHolder} containing the boss bar's name
   * @return a {@link BossBarPacket} to add a boss bar
   */
  public static BossBarPacket createAddPacket(final UUID id, final BossBar bar,
                                              final ComponentHolder name) {
    final BossBarPacket packet = new BossBarPacket();
    packet.setUuid(id);
    packet.setAction(BossBarPacket.ADD);
    packet.setName(name);
    packet.setColor(COLORS_TO_PROTOCOL.get(bar.color()));
    packet.setOverlay(OVERLAY_TO_PROTOCOL.get(bar.overlay()));
    packet.setPercent(bar.progress());
    packet.setFlags(serializeFlags(bar.flags()));
    return packet;
  }

  /**
   * Creates a packet to remove an existing boss bar.
   *
   * @param id the UUID of the boss bar to remove
   * @param ignoredBar the {@link BossBar} instance
   * @return a {@link BossBarPacket} to remove a boss bar
   */
  public static BossBarPacket createRemovePacket(final UUID id, final BossBar ignoredBar) {
    final BossBarPacket packet = new BossBarPacket();
    packet.setUuid(id);
    packet.setAction(REMOVE);
    return packet;
  }

  /**
   * Creates a packet to update the progress (percentage) of the boss bar.
   *
   * @param id the UUID of the boss bar
   * @param bar the {@link BossBar} instance
   * @return a {@link BossBarPacket} to update the boss bar's progress
   */
  public static BossBarPacket createUpdateProgressPacket(final UUID id, final BossBar bar) {
    final BossBarPacket packet = new BossBarPacket();
    packet.setUuid(id);
    packet.setAction(UPDATE_PERCENT);
    packet.setPercent(bar.progress());
    return packet;
  }

  /**
   * Creates a packet to update the name of the boss bar.
   *
   * @param id the UUID of the boss bar
   * @param ignoredBar the {@link BossBar} instance
   * @param name the {@link ComponentHolder} containing the boss bar's new name
   * @return a {@link BossBarPacket} to update the boss bar's name
   */
  public static BossBarPacket createUpdateNamePacket(final UUID id, final BossBar ignoredBar,
                                                     final ComponentHolder name) {
    final BossBarPacket packet = new BossBarPacket();
    packet.setUuid(id);
    packet.setAction(UPDATE_NAME);
    packet.setName(name);
    return packet;
  }

  /**
   * Creates a packet to update the style (color and overlay) of the boss bar.
   *
   * @param id the UUID of the boss bar
   * @param bar the {@link BossBar} instance
   * @return a {@link BossBarPacket} to update the boss bar's style
   */
  public static BossBarPacket createUpdateStylePacket(final UUID id, final BossBar bar) {
    final BossBarPacket packet = new BossBarPacket();
    packet.setUuid(id);
    packet.setAction(UPDATE_STYLE);
    packet.setColor(COLORS_TO_PROTOCOL.get(bar.color()));
    packet.setOverlay(OVERLAY_TO_PROTOCOL.get(bar.overlay()));
    return packet;
  }

  /**
   * Creates a packet to update the properties of the boss bar.
   *
   * @param id the UUID of the boss bar
   * @param bar the {@link BossBar} instance
   * @return a {@link BossBarPacket} to update the boss bar's properties
   */
  public static BossBarPacket createUpdatePropertiesPacket(final UUID id, final BossBar bar) {
    final BossBarPacket packet = new BossBarPacket();
    packet.setUuid(id);
    packet.setAction(UPDATE_PROPERTIES);
    packet.setFlags(serializeFlags(bar.flags()));
    return packet;
  }

  /**
   * Retrieves the UUID of the boss bar.
   *
   * @return the UUID of the boss bar
   * @throws IllegalStateException if the UUID has not been set
   */
  public UUID getUuid() {
    if (uuid == null) {
      throw new IllegalStateException("No boss bar UUID specified");
    }

    return uuid;
  }

  /**
   * Sets the UUID for this boss bar packet.
   *
   * @param uuid the UUID to assign, or {@code null} if unset
   */
  public void setUuid(final @Nullable UUID uuid) {
    this.uuid = uuid;
  }

  /**
   * Returns the current action identifier of this boss bar packet.
   *
   * @return the action ID (e.g., {@link #ADD}, {@link #REMOVE})
   */
  public int getAction() {
    return action;
  }

  /**
   * Sets the action type for this boss bar packet.
   *
   * @param action the action ID (e.g., {@link #ADD}, {@link #REMOVE})
   */
  public void setAction(final int action) {
    this.action = action;
  }

  /**
   * Returns the name component of the boss bar.
   *
   * @return the boss bar's {@link ComponentHolder}, or {@code null} if not set
   */
  public @Nullable ComponentHolder getName() {
    return name;
  }

  /**
   * Sets the display name for the boss bar.
   *
   * @param name the {@link ComponentHolder} name, or {@code null} to unset
   */
  public void setName(final @Nullable ComponentHolder name) {
    this.name = name;
  }

  /**
   * Returns the progress value of the boss bar.
   *
   * @return the progress from 0.0 (empty) to 1.0 (full)
   */
  public float getPercent() {
    return percent;
  }

  /**
   * Sets the progress value for the boss bar.
   *
   * @param percent the progress value from 0.0 to 1.0
   */
  public void setPercent(final float percent) {
    this.percent = percent;
  }

  /**
   * Returns the color ID of the boss bar.
   *
   * @return the protocol ID of the color
   */
  public int getColor() {
    return color;
  }

  /**
   * Sets the color ID of the boss bar.
   *
   * @param color the protocol color ID to set
   */
  public void setColor(final int color) {
    this.color = color;
  }

  /**
   * Returns the overlay ID of the boss bar.
   *
   * @return the protocol ID of the overlay
   */
  public int getOverlay() {
    return overlay;
  }

  /**
   * Sets the overlay ID of the boss bar.
   *
   * @param overlay the protocol overlay ID to assign
   */
  public void setOverlay(final int overlay) {
    this.overlay = overlay;
  }

  /**
   * Returns the flag bitmask for this boss bar.
   *
   * @return the bitmask containing {@link BossBar.Flag} values
   */
  public short getFlags() {
    return flags;
  }

  /**
   * Sets the flag bitmask for this boss bar.
   *
   * @param flags the short bitmask containing {@link BossBar.Flag} values
   */
  public void setFlags(final short flags) {
    this.flags = flags;
  }

  /**
   * Returns a string representation of this boss bar packet.
   *
   * <p>This includes the UUID, action, name, progress, style, and flags.</p>
   *
   * @return a string describing this boss bar packet
   */
  @Override
  public String toString() {
    return "BossBar{"
        + "uuid=" + uuid
        + ", action=" + action
        + ", name='" + name + '\''
        + ", percent=" + percent
        + ", color=" + color
        + ", overlay=" + overlay
        + ", flags=" + flags
        + '}';
  }

  /**
   * Decodes this boss bar packet from the provided {@link ByteBuf}.
   *
   * <p>This method reads the boss bar UUID and action type, then conditionally decodes
   * the corresponding fields based on the action (e.g., name, progress, style, flags).</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet
   * @param version the Minecraft protocol version
   * @throws UnsupportedOperationException if the action ID is unknown
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    this.uuid = ProtocolUtils.readUuid(buf);
    this.action = ProtocolUtils.readVarInt(buf);
    switch (action) {
      case ADD -> {
        this.name = ComponentHolder.read(buf, version);
        this.percent = buf.readFloat();
        this.color = ProtocolUtils.readVarInt(buf);
        this.overlay = ProtocolUtils.readVarInt(buf);
        this.flags = buf.readUnsignedByte();
      }
      case REMOVE -> {
      }
      case UPDATE_PERCENT -> this.percent = buf.readFloat();
      case UPDATE_NAME -> this.name = ComponentHolder.read(buf, version);
      case UPDATE_STYLE -> {
        this.color = ProtocolUtils.readVarInt(buf);
        this.overlay = ProtocolUtils.readVarInt(buf);
      }
      case UPDATE_PROPERTIES -> this.flags = buf.readUnsignedByte();
      default -> throw new UnsupportedOperationException("Unknown action " + action);
    }
  }

  /**
   * Encodes this boss bar packet into the given {@link ByteBuf}.
   *
   * <p>This writes the UUID and action ID, and conditionally writes fields depending on
   * the action type (e.g., name, progress, style, flags).</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet
   * @param version the Minecraft protocol version
   * @throws IllegalStateException if required fields (e.g., UUID, name) are missing
   * @throws UnsupportedOperationException if the action ID is unknown
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    if (uuid == null) {
      throw new IllegalStateException("No boss bar UUID specified");
    }
    ProtocolUtils.writeUuid(buf, uuid);
    ProtocolUtils.writeVarInt(buf, action);
    switch (action) {
      case ADD -> {
        if (name == null) {
          throw new IllegalStateException("No name specified!");
        }

        name.write(buf);
        buf.writeFloat(percent);
        ProtocolUtils.writeVarInt(buf, color);
        ProtocolUtils.writeVarInt(buf, overlay);
        buf.writeByte(flags);
      }
      case REMOVE -> {
      }
      case UPDATE_PERCENT -> buf.writeFloat(percent);
      case UPDATE_NAME -> {
        if (name == null) {
          throw new IllegalStateException("No name specified!");
        }

        name.write(buf);
      }
      case UPDATE_STYLE -> {
        ProtocolUtils.writeVarInt(buf, color);
        ProtocolUtils.writeVarInt(buf, overlay);
      }

      case UPDATE_PROPERTIES -> buf.writeByte(flags);
      default -> throw new UnsupportedOperationException("Unknown action " + action);
    }
  }

  private static byte serializeFlags(final Set<BossBar.Flag> flags) {
    byte val = 0x0;
    for (BossBar.Flag flag : flags) {
      val |= (byte) FLAG_BITS_TO_PROTOCOL.get(flag);
    }

    return val;
  }

  /**
   * Handles this boss bar packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates handling to {@code handler.handle(this)} to update the
   * boss bar state on the client.</p>
   *
   * @param handler the session handler responsible for processing this packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
