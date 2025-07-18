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
import com.velocitypowered.proxy.connection.registry.DimensionInfo;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.Pair;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a respawn packet sent by the server when the player changes dimensions or respawns.
 * The packet contains information about the new dimension, difficulty, gamemode, and more.
 */
public class RespawnPacket implements MinecraftPacket {

  /**
   * The dimension the player is respawning or teleporting to.
   */
  private int dimension;

  /**
   * The hashed seed used for terrain generation (introduced in Minecraft 1.15).
   */
  private long partialHashedSeed;

  /**
   * The difficulty of the server (used in versions ≤ 1.13.2).
   */
  private short difficulty;

  /**
   * The gamemode the player is respawning into.
   */
  private short gamemode;

  /**
   * The level type (e.g., "default", "flat") used in versions prior to 1.16.
   */
  private String levelType = "";

  /**
   * A byte flag indicating whether to retain player data (introduced in 1.16).
   */
  private byte dataToKeep;

  /**
   * Additional information about the dimension (used in 1.16–1.16.1).
   */
  private DimensionInfo dimensionInfo;

  /**
   * The previous gamemode of the player (introduced in 1.16).
   */
  private short previousGamemode;

  /**
   * NBT tag data about the current dimension (used in 1.16.2+).
   */
  private CompoundBinaryTag currentDimensionData;

  /**
   * Optional last death position as a pair of (dimension, location) (1.19+).
   */
  private @Nullable Pair<String, Long> lastDeathPosition;

  /**
   * Cooldown time before the player can re-enter a portal (1.20+).
   */
  private int portalCooldown;

  /**
   * The sea level in the current dimension (used for respawn logic) (1.21.2+).
   */
  private int seaLevel;

  /**
   * Constructs an empty {@code RespawnPacket}.
   *
   * <p>Fields must be populated manually before encoding.</p>
   */
  public RespawnPacket() {
  }

  /**
   * Constructs a new {@code RespawnPacket} with the specified parameters.
   *
   * @param dimension the dimension the player is respawning or teleporting to
   * @param partialHashedSeed the partial hashed seed
   * @param difficulty the difficulty of the server
   * @param gamemode the player's current gamemode
   * @param levelType the type of level (e.g., "default", "flat")
   * @param dataToKeep a byte flag indicating whether certain data should be kept
   * @param dimensionInfo additional information about the dimension (for 1.16-1.16.1)
   * @param previousGamemode the player's previous gamemode
   * @param currentDimensionData data about the current dimension (for 1.16.2+)
   * @param lastDeathPosition optional last death position (for 1.19+)
   * @param portalCooldown the cooldown for portal usage (for 1.20+)
   * @param seaLevel a determinable spawn point for a user (for 1.21.2+)
   */
  public RespawnPacket(final int dimension, final long partialHashedSeed, final short difficulty, final short gamemode,
                       final String levelType, final byte dataToKeep, final DimensionInfo dimensionInfo,
                       final short previousGamemode, final CompoundBinaryTag currentDimensionData,
                       @Nullable final Pair<String, Long> lastDeathPosition, final int portalCooldown,
                       final int seaLevel) {
    this.dimension = dimension;
    this.partialHashedSeed = partialHashedSeed;
    this.difficulty = difficulty;
    this.gamemode = gamemode;
    this.levelType = levelType;
    this.dataToKeep = dataToKeep;
    this.dimensionInfo = dimensionInfo;
    this.previousGamemode = previousGamemode;
    this.currentDimensionData = currentDimensionData;
    this.lastDeathPosition = lastDeathPosition;
    this.portalCooldown = portalCooldown;
    this.seaLevel = seaLevel;
  }

  /**
   * Creates a new {@code RespawnPacket} from a {@link JoinGamePacket}.
   *
   * @param joinGame the {@code JoinGamePacket} to use
   * @return a new {@code RespawnPacket} based on the provided {@code JoinGamePacket}
   */
  public static RespawnPacket fromJoinGame(final JoinGamePacket joinGame) {
    return new RespawnPacket(
        joinGame.getDimension(),
        joinGame.getPartialHashedSeed(),
        joinGame.getDifficulty(),
        joinGame.getGamemode(),
        joinGame.getLevelType(),
        (byte) 0,
        joinGame.getDimensionInfo(),
        joinGame.getPreviousGamemode(),
        joinGame.getCurrentDimensionData(),
        joinGame.getLastDeathPosition(),
        joinGame.getPortalCooldown(),
        joinGame.getSeaLevel());
  }

  /**
   * Gets the dimension ID the player is respawning into.
   *
   * @return the dimension ID
   */
  public int getDimension() {
    return dimension;
  }

  /**
   * Sets the dimension ID the player is respawning into.
   *
   * @param dimension the dimension ID
   */
  public void setDimension(final int dimension) {
    this.dimension = dimension;
  }

  /**
   * Gets the hashed seed used for terrain generation.
   *
   * @return the partial hashed seed
   */
  public long getPartialHashedSeed() {
    return partialHashedSeed;
  }

  /**
   * Sets the hashed seed used for terrain generation.
   *
   * @param partialHashedSeed the hashed seed
   */
  public void setPartialHashedSeed(final long partialHashedSeed) {
    this.partialHashedSeed = partialHashedSeed;
  }

  /**
   * Gets the server difficulty.
   *
   * @return the difficulty
   */
  public short getDifficulty() {
    return difficulty;
  }

  /**
   * Sets the server difficulty.
   *
   * @param difficulty the difficulty
   */
  public void setDifficulty(final short difficulty) {
    this.difficulty = difficulty;
  }

  /**
   * Gets the player's current gamemode.
   *
   * @return the gamemode
   */
  public short getGamemode() {
    return gamemode;
  }

  /**
   * Sets the player's current gamemode.
   *
   * @param gamemode the gamemode
   */
  public void setGamemode(final short gamemode) {
    this.gamemode = gamemode;
  }

  /**
   * Gets the legacy level type string (used in versions < 1.16).
   *
   * @return the level type
   */
  public String getLevelType() {
    return levelType;
  }

  /**
   * Sets the legacy level type string.
   *
   * @param levelType the level type
   */
  public void setLevelType(final String levelType) {
    this.levelType = levelType;
  }

  /**
   * Gets the data retention flag for the respawn.
   *
   * @return the data-to-keep byte
   */
  public byte getDataToKeep() {
    return dataToKeep;
  }

  /**
   * Sets the data retention flag for the respawn.
   *
   * @param dataToKeep the data-to-keep byte
   */
  public void setDataToKeep(final byte dataToKeep) {
    this.dataToKeep = dataToKeep;
  }

  /**
   * Gets the player's previous gamemode.
   *
   * @return the previous gamemode
   */
  public short getPreviousGamemode() {
    return previousGamemode;
  }

  /**
   * Sets the player's previous gamemode.
   *
   * @param previousGamemode the previous gamemode
   */
  public void setPreviousGamemode(final short previousGamemode) {
    this.previousGamemode = previousGamemode;
  }

  /**
   * Gets the last known death position of the player (1.19+).
   *
   * @return the last death position or {@code null}
   */
  public @Nullable Pair<String, Long> getLastDeathPosition() {
    return lastDeathPosition;
  }

  /**
   * Sets the last known death position of the player (1.19+).
   *
   * @param lastDeathPosition the last death position
   */
  public void setLastDeathPosition(@Nullable final Pair<String, Long> lastDeathPosition) {
    this.lastDeathPosition = lastDeathPosition;
  }

  /**
   * Gets the cooldown time before portal reuse is allowed (1.20+).
   *
   * @return the portal cooldown
   */
  public int getPortalCooldown() {
    return portalCooldown;
  }

  /**
   * Sets the cooldown time before portal reuse is allowed (1.20+).
   *
   * @param portalCooldown the portal cooldown
   */
  public void setPortalCooldown(final int portalCooldown) {
    this.portalCooldown = portalCooldown;
  }

  /**
   * Gets the sea level in the dimension (1.21.2+).
   *
   * @return the sea level
   */
  public int getSeaLevel() {
    return seaLevel;
  }

  /**
   * Sets the sea level in the dimension (1.21.2+).
   *
   * @param seaLevel the sea level
   */
  public void setSeaLevel(final int seaLevel) {
    this.seaLevel = seaLevel;
  }

  @Override
  public final String toString() {
    return "Respawn{"
        + "dimension=" + dimension
        + ", partialHashedSeed=" + partialHashedSeed
        + ", difficulty=" + difficulty
        + ", gamemode=" + gamemode
        + ", levelType='" + levelType + '\''
        + ", dataToKeep=" + dataToKeep
        + ", dimensionRegistryName='" + dimensionInfo.toString() + '\''
        + ", dimensionInfo=" + dimensionInfo
        + ", previousGamemode=" + previousGamemode
        + ", dimensionData=" + currentDimensionData
        + ", portalCooldown=" + portalCooldown
        + ", seaLevel=" + seaLevel
        + '}';
  }

  @Override
  public final void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    String dimensionKey = "";
    String levelName = null;
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_16)) {
      if (version.noLessThan(ProtocolVersion.MINECRAFT_1_16_2)
          && version.lessThan(ProtocolVersion.MINECRAFT_1_19)) {
        this.currentDimensionData = ProtocolUtils.readCompoundTag(buf, version, BinaryTagIO.reader());
        dimensionKey = ProtocolUtils.readString(buf);
      } else {
        if (version.noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
          dimension = ProtocolUtils.readVarInt(buf);
        } else {
          dimensionKey = ProtocolUtils.readString(buf);
        }

        levelName = ProtocolUtils.readString(buf);
      }
    } else {
      this.dimension = buf.readInt();
    }

    if (version.noGreaterThan(ProtocolVersion.MINECRAFT_1_13_2)) {
      this.difficulty = buf.readUnsignedByte();
    }

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_15)) {
      this.partialHashedSeed = buf.readLong();
    }

    this.gamemode = buf.readByte();
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_16)) {
      this.previousGamemode = buf.readByte();
      boolean isDebug = buf.readBoolean();
      boolean isFlat = buf.readBoolean();
      this.dimensionInfo = new DimensionInfo(dimensionKey, levelName, isFlat, isDebug, version);
      if (version.lessThan(ProtocolVersion.MINECRAFT_1_19_3)) {
        this.dataToKeep = (byte) (buf.readBoolean() ? 1 : 0);
      } else if (version.lessThan(ProtocolVersion.MINECRAFT_1_20_2)) {
        this.dataToKeep = buf.readByte();
      }
    } else {
      this.levelType = ProtocolUtils.readString(buf, 16);
    }

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_19) && buf.readBoolean()) {
      this.lastDeathPosition = Pair.of(ProtocolUtils.readString(buf), buf.readLong());
    }

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_20)) {
      this.portalCooldown = ProtocolUtils.readVarInt(buf);
    }

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_21_2)) {
      this.seaLevel = ProtocolUtils.readVarInt(buf);
    }

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_20_2)) {
      this.dataToKeep = buf.readByte();
    }
  }

  @Override
  public final void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_16)) {
      if (version.noLessThan(ProtocolVersion.MINECRAFT_1_16_2)
          && version.lessThan(ProtocolVersion.MINECRAFT_1_19)) {
        ProtocolUtils.writeBinaryTag(buf, version, currentDimensionData);
        ProtocolUtils.writeString(buf, dimensionInfo.getRegistryIdentifier());
      } else {
        if (version.noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
          ProtocolUtils.writeVarInt(buf, dimension);
        } else {
          ProtocolUtils.writeString(buf, dimensionInfo.getRegistryIdentifier());
        }
        ProtocolUtils.writeString(buf, dimensionInfo.getLevelName());
      }
    } else {
      buf.writeInt(dimension);
    }

    if (version.noGreaterThan(ProtocolVersion.MINECRAFT_1_13_2)) {
      buf.writeByte(difficulty);
    }

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_15)) {
      buf.writeLong(partialHashedSeed);
    }

    buf.writeByte(gamemode);
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_16)) {
      buf.writeByte(previousGamemode);
      buf.writeBoolean(dimensionInfo.isDebugType());
      buf.writeBoolean(dimensionInfo.isFlat());
      if (version.lessThan(ProtocolVersion.MINECRAFT_1_19_3)) {
        buf.writeBoolean(dataToKeep != 0);
      } else if (version.lessThan(ProtocolVersion.MINECRAFT_1_20_2)) {
        buf.writeByte(dataToKeep);
      }
    } else {
      ProtocolUtils.writeString(buf, levelType);
    }

    // optional death location
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_19)) {
      if (lastDeathPosition != null) {
        buf.writeBoolean(true);
        ProtocolUtils.writeString(buf, lastDeathPosition.key());
        buf.writeLong(lastDeathPosition.value());
      } else {
        buf.writeBoolean(false);
      }
    }

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_20)) {
      ProtocolUtils.writeVarInt(buf, portalCooldown);
    }

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_21_2)) {
      ProtocolUtils.writeVarInt(buf, seaLevel);
    }

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_20_2)) {
      buf.writeByte(dataToKeep);
    }
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
