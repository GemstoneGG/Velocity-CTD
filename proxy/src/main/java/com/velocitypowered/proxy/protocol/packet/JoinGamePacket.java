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

package com.velocitypowered.proxy.protocol.packet;

import com.google.common.collect.ImmutableSet;
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
 * Represents a packet sent to the client when they successfully join a game in Minecraft.
 * This packet contains all the necessary information to initialize the client state,
 * including the player's entity ID, game mode, dimension, world settings, and more.
 */
public class JoinGamePacket implements MinecraftPacket {

  /**
   * Reader used to parse large NBT registry blobs in the Join Game packet.
   */
  private static final BinaryTagIO.Reader JOINGAME_READER = BinaryTagIO.reader(4 * 1024 * 1024);

  /**
   * The entity ID of the joining player.
   */
  private int entityId;

  /**
   * The game mode the player is entering the world with.
   */
  private short gamemode;

  /**
   * The legacy dimension ID for versions before 1.16.
   */
  private int dimension;

  /**
   * The partial hashed seed of the world (used from 1.15+).
   */
  private long partialHashedSeed;

  /**
   * The difficulty of the world (used in legacy versions).
   */
  private short difficulty;

  /**
   * Whether the world is in hardcore mode.
   */
  private boolean isHardcore;

  /**
   * The maximum number of players allowed (ignored by modern clients).
   */
  private int maxPlayers;

  /**
   * The level type string used by legacy versions (e.g., "default", "flat").
   */
  private @Nullable String levelType;

  /**
   * The server-defined view distance (1.14+).
   */
  private int viewDistance;

  /**
   * Whether reduced debug info is enabled for the player (F3).
   */
  private boolean reducedDebugInfo;

  /**
   * Whether the client should show the respawn screen on death.
   */
  private boolean showRespawnScreen;

  /**
   * Whether crafting is limited by recipe unlocks (1.20.2+).
   */
  private boolean doLimitedCrafting;

  /**
   * A set of dimension names that are available (1.16+).
   */
  private ImmutableSet<String> levelNames;

  /**
   * The NBT registry blob that describes dimension and tag registries (1.16+).
   */
  private CompoundBinaryTag registry;

  /**
   * The parsed dimension info for the current world (1.16+).
   */
  private DimensionInfo dimensionInfo;

  /**
   * The NBT structure describing the current dimension (1.16.2+).
   */
  private CompoundBinaryTag currentDimensionData;

  /**
   * The previously selected game mode (e.g., for rejoining a spectator session).
   */
  private short previousGamemode;

  /**
   * The simulation distance defined by the server (1.18+).
   */
  private int simulationDistance;

  /**
   * The last recorded death location of the player (1.19+).
   */
  private @Nullable Pair<String, Long> lastDeathPosition;

  /**
   * The cooldown time before the player can use a portal again (1.20+).
   */
  private int portalCooldown;

  /**
   * The Y-level of sea level in the current dimension (1.21.2+).
   */
  private int seaLevel;

  /**
   * Whether the server enforces secure chat messages (1.20.5+).
   */
  private boolean enforcesSecureChat;

  /**
   * Gets the entity ID of the player.
   *
   * @return the entity ID
   */
  public int getEntityId() {
    return entityId;
  }

  /**
   * Sets the entity ID of the player.
   *
   * @param entityId the new entity ID
   */
  public void setEntityId(final int entityId) {
    this.entityId = entityId;
  }

  /**
   * Gets the current game mode of the player.
   *
   * @return the game mode
   */
  public short getGamemode() {
    return gamemode;
  }

  /**
   * Sets the current game mode of the player.
   *
   * @param gamemode the game mode to set
   */
  public void setGamemode(final short gamemode) {
    this.gamemode = gamemode;
  }

  /**
   * Gets the dimension ID (legacy).
   *
   * @return the dimension ID
   */
  public int getDimension() {
    return dimension;
  }

  /**
   * Sets the dimension ID (legacy).
   *
   * @param dimension the dimension ID
   */
  public void setDimension(final int dimension) {
    this.dimension = dimension;
  }

  /**
   * Gets the partial hashed world seed.
   *
   * @return the seed
   */
  public long getPartialHashedSeed() {
    return partialHashedSeed;
  }

  /**
   * Gets the difficulty setting.
   *
   * @return the difficulty
   */
  public short getDifficulty() {
    return difficulty;
  }

  /**
   * Sets the difficulty setting.
   *
   * @param difficulty the difficulty to set
   */
  public void setDifficulty(final short difficulty) {
    this.difficulty = difficulty;
  }

  /**
   * Gets the max number of players.
   *
   * @return the max players
   */
  public int getMaxPlayers() {
    return maxPlayers;
  }

  /**
   * Sets the max number of players.
   *
   * @param maxPlayers the value to set
   */
  public void setMaxPlayers(final int maxPlayers) {
    this.maxPlayers = maxPlayers;
  }

  /**
   * Gets the legacy level type string.
   *
   * @return the level type
   */
  public @Nullable String getLevelType() {
    return levelType;
  }

  /**
   * Sets the legacy level type string.
   *
   * @param levelType the level type to set
   */
  public void setLevelType(final @Nullable String levelType) {
    this.levelType = levelType;
  }

  /**
   * Gets the view distance.
   *
   * @return the view distance
   */
  public int getViewDistance() {
    return viewDistance;
  }

  /**
   * Sets the view distance.
   *
   * @param viewDistance the view distance to set
   */
  public void setViewDistance(final int viewDistance) {
    this.viewDistance = viewDistance;
  }

  /**
   * Checks whether reduced debug info is enabled.
   *
   * @return {@code true} if reduced debug is enabled
   */
  public boolean isReducedDebugInfo() {
    return reducedDebugInfo;
  }

  /**
   * Sets whether reduced debug info is enabled.
   *
   * @param reducedDebugInfo the flag to set
   */
  public void setReducedDebugInfo(final boolean reducedDebugInfo) {
    this.reducedDebugInfo = reducedDebugInfo;
  }

  /**
   * Gets the dimension info.
   *
   * @return the dimension info
   */
  public DimensionInfo getDimensionInfo() {
    return dimensionInfo;
  }

  /**
   * Sets the dimension info.
   *
   * @param dimensionInfo the dimension info to set
   */
  public void setDimensionInfo(final DimensionInfo dimensionInfo) {
    this.dimensionInfo = dimensionInfo;
  }

  /**
   * Gets the previous game mode.
   *
   * @return the previous game mode
   */
  public short getPreviousGamemode() {
    return previousGamemode;
  }

  /**
   * Sets the previous game mode.
   *
   * @param previousGamemode the mode to set
   */
  public void setPreviousGamemode(final short previousGamemode) {
    this.previousGamemode = previousGamemode;
  }

  /**
   * Checks if hardcore mode is enabled.
   *
   * @return {@code true} if hardcore is active
   */
  public boolean getIsHardcore() {
    return isHardcore;
  }

  /**
   * Sets whether hardcore mode is enabled.
   *
   * @param isHardcore whether the world is hardcore
   */
  public void setIsHardcore(final boolean isHardcore) {
    this.isHardcore = isHardcore;
  }

  /**
   * Checks if limited crafting is enabled.
   *
   * @return {@code true} if crafting is limited
   */
  public boolean getDoLimitedCrafting() {
    return doLimitedCrafting;
  }

  /**
   * Sets whether limited crafting is enabled.
   *
   * @param doLimitedCrafting the crafting flag
   */
  public void setDoLimitedCrafting(final boolean doLimitedCrafting) {
    this.doLimitedCrafting = doLimitedCrafting;
  }

  /**
   * Gets the current dimension's full NBT data.
   *
   * @return the current dimension tag
   */
  public CompoundBinaryTag getCurrentDimensionData() {
    return currentDimensionData;
  }

  /**
   * Gets the simulation distance.
   *
   * @return the simulation distance
   */
  public int getSimulationDistance() {
    return simulationDistance;
  }

  /**
   * Sets the simulation distance.
   *
   * @param simulationDistance the value to set
   */
  public void setSimulationDistance(final int simulationDistance) {
    this.simulationDistance = simulationDistance;
  }

  /**
   * Gets the last death position of the player.
   *
   * @return the death position, or {@code null} if absent
   */
  public @Nullable Pair<String, Long> getLastDeathPosition() {
    return lastDeathPosition;
  }

  /**
   * Sets the last death position of the player.
   *
   * @param lastDeathPosition the position to set
   */
  public void setLastDeathPosition(final @Nullable Pair<String, Long> lastDeathPosition) {
    this.lastDeathPosition = lastDeathPosition;
  }

  /**
   * Gets the portal cooldown (in ticks).
   *
   * @return the portal cooldown
   */
  public int getPortalCooldown() {
    return portalCooldown;
  }

  /**
   * Sets the portal cooldown (in ticks).
   *
   * @param portalCooldown the cooldown to set
   */
  public void setPortalCooldown(final int portalCooldown) {
    this.portalCooldown = portalCooldown;
  }

  /**
   * Gets the current world's sea level.
   *
   * @return the sea level
   */
  public int getSeaLevel() {
    return seaLevel;
  }

  /**
   * Sets the current world's sea level.
   *
   * @param seaLevel the value to set
   */
  public void setSeaLevel(final int seaLevel) {
    this.seaLevel = seaLevel;
  }

  /**
   * Checks whether secure chat is enforced.
   *
   * @return {@code true} if secure chat is enforced
   */
  public boolean getEnforcesSecureChat() {
    return this.enforcesSecureChat;
  }

  /**
   * Sets whether secure chat is enforced.
   *
   * @param enforcesSecureChat the flag to set
   */
  public void setEnforcesSecureChat(final boolean enforcesSecureChat) {
    this.enforcesSecureChat = enforcesSecureChat;
  }

  /**
   * Gets the registry data sent in the Join Game packet.
   *
   * @return the registry NBT data
   */
  public CompoundBinaryTag getRegistry() {
    return registry;
  }

  /**
   * Returns a string representation of this join game packet for debugging purposes.
   *
   * <p>This includes the entity ID, dimension, view distance, and other game-specific
   * metadata relevant at the start of a client session.</p>
   *
   * @return a string representation of the packet
   */
  @Override
  public String toString() {
    return "JoinGame{"
        + "entityId=" + entityId
        + ", gamemode=" + gamemode
        + ", dimension=" + dimension
        + ", partialHashedSeed=" + partialHashedSeed
        + ", difficulty=" + difficulty
        + ", isHardcore=" + isHardcore
        + ", maxPlayers=" + maxPlayers
        + ", levelType='" + levelType + '\''
        + ", viewDistance=" + viewDistance
        + ", reducedDebugInfo=" + reducedDebugInfo
        + ", showRespawnScreen=" + showRespawnScreen
        + ", doLimitedCrafting=" + doLimitedCrafting
        + ", levelNames=" + levelNames
        + ", registry='" + registry + '\''
        + ", dimensionInfo='" + dimensionInfo + '\''
        + ", currentDimensionData='" + currentDimensionData + '\''
        + ", previousGamemode=" + previousGamemode
        + ", simulationDistance=" + simulationDistance
        + ", lastDeathPosition='" + lastDeathPosition + '\''
        + ", portalCooldown=" + portalCooldown
        + ", seaLevel=" + seaLevel
        + '}';
  }

  /**
   * Decodes this packet from the provided {@link ByteBuf}.
   *
   * <p>This method reads the fields of the join game packet based on the specified protocol
   * version and initializes the internal state of the player session.</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param version the Minecraft protocol version
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_20_2)) {
      // haha funny, they made 1.20.2 more complicated
      this.decode1202Up(buf, version);
    } else if (version.noLessThan(ProtocolVersion.MINECRAFT_1_16)) {
      // Minecraft 1.16 and above have significantly more complicated logic for reading this packet,
      // so separate it out.
      this.decode116Up(buf, version);
    } else {
      this.decodeLegacy(buf, version);
    }
  }

  private void decodeLegacy(final ByteBuf buf, final ProtocolVersion version) {
    this.entityId = buf.readInt();
    this.gamemode = buf.readByte();
    this.isHardcore = (this.gamemode & 0x08) != 0;
    this.gamemode &= ~0x08;

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_9_1)) {
      this.dimension = buf.readInt();
    } else {
      this.dimension = buf.readByte();
    }

    if (version.noGreaterThan(ProtocolVersion.MINECRAFT_1_13_2)) {
      this.difficulty = buf.readUnsignedByte();
    }

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_15)) {
      this.partialHashedSeed = buf.readLong();
    }

    this.maxPlayers = buf.readUnsignedByte();
    this.levelType = ProtocolUtils.readString(buf, 16);

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_14)) {
      this.viewDistance = ProtocolUtils.readVarInt(buf);
    }

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      this.reducedDebugInfo = buf.readBoolean();
    }

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_15)) {
      this.showRespawnScreen = buf.readBoolean();
    }
  }

  private void decode116Up(final ByteBuf buf, final ProtocolVersion version) {
    this.entityId = buf.readInt();
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_16_2)) {
      this.isHardcore = buf.readBoolean();
      this.gamemode = buf.readByte();
    } else {
      this.gamemode = buf.readByte();
      this.isHardcore = (this.gamemode & 0x08) != 0;
      this.gamemode &= ~0x08;
    }

    this.previousGamemode = buf.readByte();

    this.levelNames = ImmutableSet.copyOf(ProtocolUtils.readStringArray(buf));
    this.registry = ProtocolUtils.readCompoundTag(buf, version, JOINGAME_READER);

    String dimensionIdentifier;
    String levelName = null;
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_16_2) && version.lessThan(ProtocolVersion.MINECRAFT_1_19)) {
      this.currentDimensionData = ProtocolUtils.readCompoundTag(buf, version, JOINGAME_READER);
      dimensionIdentifier = ProtocolUtils.readString(buf);
    } else {
      dimensionIdentifier = ProtocolUtils.readString(buf);
      levelName = ProtocolUtils.readString(buf);
    }

    this.partialHashedSeed = buf.readLong();
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_16_2)) {
      this.maxPlayers = ProtocolUtils.readVarInt(buf);
    } else {
      this.maxPlayers = buf.readUnsignedByte();
    }

    this.viewDistance = ProtocolUtils.readVarInt(buf);
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_18)) {
      this.simulationDistance = ProtocolUtils.readVarInt(buf);
    }

    this.reducedDebugInfo = buf.readBoolean();
    this.showRespawnScreen = buf.readBoolean();

    boolean isDebug = buf.readBoolean();
    boolean isFlat = buf.readBoolean();
    this.dimensionInfo = new DimensionInfo(dimensionIdentifier, levelName, isFlat, isDebug, version);

    // optional death location
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_19) && buf.readBoolean()) {
      this.lastDeathPosition = Pair.of(ProtocolUtils.readString(buf), buf.readLong());
    }

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_20)) {
      this.portalCooldown = ProtocolUtils.readVarInt(buf);
    }
  }

  @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
  private void decode1202Up(final ByteBuf buf, final ProtocolVersion version) {
    this.entityId = buf.readInt();
    this.isHardcore = buf.readBoolean();

    this.levelNames = ImmutableSet.copyOf(ProtocolUtils.readStringArray(buf));

    this.maxPlayers = ProtocolUtils.readVarInt(buf);

    this.viewDistance = ProtocolUtils.readVarInt(buf);
    this.simulationDistance = ProtocolUtils.readVarInt(buf);

    this.reducedDebugInfo = buf.readBoolean();
    this.showRespawnScreen = buf.readBoolean();
    this.doLimitedCrafting = buf.readBoolean();

    String dimensionKey = "";
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
      dimension = ProtocolUtils.readVarInt(buf);
    } else {
      dimensionKey = ProtocolUtils.readString(buf);
    }

    String levelName = ProtocolUtils.readString(buf);
    this.partialHashedSeed = buf.readLong();

    this.gamemode = buf.readByte();
    this.previousGamemode = buf.readByte();

    boolean isDebug = buf.readBoolean();
    boolean isFlat = buf.readBoolean();
    this.dimensionInfo = new DimensionInfo(dimensionKey, levelName, isFlat, isDebug, version);

    // optional death location
    if (buf.readBoolean()) {
      this.lastDeathPosition = Pair.of(ProtocolUtils.readString(buf), buf.readLong());
    }

    this.portalCooldown = ProtocolUtils.readVarInt(buf);

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_21_2)) {
      this.seaLevel = ProtocolUtils.readVarInt(buf);
    }

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
      this.enforcesSecureChat = buf.readBoolean();
    }
  }

  /**
   * Encodes this packet into the provided {@link ByteBuf}.
   *
   * <p>This method writes the player join information (entity ID, game mode, dimension,
   * view distance, etc.) according to the given protocol version.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet (clientbound or serverbound)
   * @param version the Minecraft protocol version
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_20_2)) {
      // haha funny, they made 1.20.2 more complicated
      this.encode1202Up(buf, version);
    } else if (version.noLessThan(ProtocolVersion.MINECRAFT_1_16)) {
      // Minecraft 1.16 and above have significantly more complicated logic for reading this packet,
      // so separate it out.
      this.encode116Up(buf, version);
    } else {
      this.encodeLegacy(buf, version);
    }
  }

  private void encodeLegacy(final ByteBuf buf, final ProtocolVersion version) {
    buf.writeInt(entityId);
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_16_2)) {
      buf.writeBoolean(isHardcore);
      buf.writeByte(gamemode);
    } else {
      buf.writeByte(isHardcore ? gamemode | 0x8 : gamemode);
    }

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_9_1)) {
      buf.writeInt(dimension);
    } else {
      buf.writeByte(dimension);
    }

    if (version.noGreaterThan(ProtocolVersion.MINECRAFT_1_13_2)) {
      buf.writeByte(difficulty);
    }
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_15)) {
      buf.writeLong(partialHashedSeed);
    }

    buf.writeByte(maxPlayers);
    if (levelType == null) {
      throw new IllegalStateException("No level type specified.");
    }

    ProtocolUtils.writeString(buf, levelType);
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_14)) {
      ProtocolUtils.writeVarInt(buf, viewDistance);
    }

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      buf.writeBoolean(reducedDebugInfo);
    }

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_15)) {
      buf.writeBoolean(showRespawnScreen);
    }
  }

  private void encode116Up(final ByteBuf buf, final ProtocolVersion version) {
    buf.writeInt(entityId);
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_16_2)) {
      buf.writeBoolean(isHardcore);
      buf.writeByte(gamemode);
    } else {
      buf.writeByte(isHardcore ? gamemode | 0x8 : gamemode);
    }

    buf.writeByte(previousGamemode);

    ProtocolUtils.writeStringArray(buf, levelNames.toArray(String[]::new));
    ProtocolUtils.writeBinaryTag(buf, version, this.registry);
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_16_2) && version.lessThan(ProtocolVersion.MINECRAFT_1_19)) {
      ProtocolUtils.writeBinaryTag(buf, version, currentDimensionData);
      ProtocolUtils.writeString(buf, dimensionInfo.getRegistryIdentifier());
    } else {
      ProtocolUtils.writeString(buf, dimensionInfo.getRegistryIdentifier());
      ProtocolUtils.writeString(buf, dimensionInfo.getLevelName());
    }

    buf.writeLong(partialHashedSeed);
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_16_2)) {
      ProtocolUtils.writeVarInt(buf, maxPlayers);
    } else {
      buf.writeByte(maxPlayers);
    }

    ProtocolUtils.writeVarInt(buf, viewDistance);
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_18)) {
      ProtocolUtils.writeVarInt(buf, simulationDistance);
    }

    buf.writeBoolean(reducedDebugInfo);
    buf.writeBoolean(showRespawnScreen);

    buf.writeBoolean(dimensionInfo.isDebugType());
    buf.writeBoolean(dimensionInfo.isFlat());

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
  }

  private void encode1202Up(final ByteBuf buf, final ProtocolVersion version) {
    buf.writeInt(entityId);
    buf.writeBoolean(isHardcore);

    ProtocolUtils.writeStringArray(buf, levelNames.toArray(String[]::new));

    ProtocolUtils.writeVarInt(buf, maxPlayers);

    ProtocolUtils.writeVarInt(buf, viewDistance);
    ProtocolUtils.writeVarInt(buf, simulationDistance);

    buf.writeBoolean(reducedDebugInfo);
    buf.writeBoolean(showRespawnScreen);
    buf.writeBoolean(doLimitedCrafting);

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
      ProtocolUtils.writeVarInt(buf, dimension);
    } else {
      ProtocolUtils.writeString(buf, dimensionInfo.getRegistryIdentifier());
    }

    ProtocolUtils.writeString(buf, dimensionInfo.getLevelName());
    buf.writeLong(partialHashedSeed);

    buf.writeByte(gamemode);
    buf.writeByte(previousGamemode);

    buf.writeBoolean(dimensionInfo.isDebugType());
    buf.writeBoolean(dimensionInfo.isFlat());

    // optional death location
    if (lastDeathPosition != null) {
      buf.writeBoolean(true);
      ProtocolUtils.writeString(buf, lastDeathPosition.key());
      buf.writeLong(lastDeathPosition.value());
    } else {
      buf.writeBoolean(false);
    }

    ProtocolUtils.writeVarInt(buf, portalCooldown);

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_21_2)) {
      ProtocolUtils.writeVarInt(buf, seaLevel);
    }

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
      buf.writeBoolean(this.enforcesSecureChat);
    }
  }

  /**
   * Handles this join game packet using the provided {@link MinecraftSessionHandler}.
   *
   * <p>This delegates processing to {@code handler.handle(this)} and transitions
   * the connection into the play phase.</p>
   *
   * @param handler the session handler to process the packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
