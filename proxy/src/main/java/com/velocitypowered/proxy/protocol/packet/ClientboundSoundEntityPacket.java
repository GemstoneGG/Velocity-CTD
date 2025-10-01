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
import io.netty.buffer.ByteBuf;
import java.util.Random;
import net.kyori.adventure.sound.Sound;
import org.jetbrains.annotations.Nullable;

/**
 * A clientbound packet that instructs the client to play a sound tied to an entity.
 *
 * <p>This is sent by the server when a sound should be played at the location of a
 * specific entity, with optional fixed range and seed.</p>
 */
public class ClientboundSoundEntityPacket implements MinecraftPacket {

  /**
   * Fallback random instance for generating sound seeds when none are provided.
   */
  private static final Random SEEDS_RANDOM = new Random();

  /**
   * The sound to play, including name, source, volume, pitch, and optional seed.
   */
  private Sound sound;

  /**
   * A fixed attenuation range for the sound, or {@code null} if default range applies.
   */
  private @Nullable Float fixedRange;

  /**
   * The entity ID of the sound emitter.
   */
  private int emitterEntityId;

  /**
   * Constructs an empty sound entity packet.
   *
   * <p>This is primarily used for deserialization.</p>
   */
  public ClientboundSoundEntityPacket() {
  }

  /**
   * Constructs a new sound entity packet.
   *
   * @param sound the sound to play
   * @param fixedRange the fixed attenuation range, or {@code null} to use the default
   * @param emitterEntityId the entity ID of the sound emitter
   */
  public ClientboundSoundEntityPacket(final Sound sound, final @Nullable Float fixedRange, final int emitterEntityId) {
    this.sound = sound;
    this.fixedRange = fixedRange;
    this.emitterEntityId = emitterEntityId;
  }

  /**
   * Decoding is not implemented for this packet.
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion protocolVersion) {
    throw new UnsupportedOperationException("Decode is not implemented");
  }

  /**
   * Encodes this packet to the buffer.
   *
   * @param buf the buffer to write to
   * @param direction the protocol direction
   * @param protocolVersion the client protocol version
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion protocolVersion) {
    ProtocolUtils.writeVarInt(buf, 0); // Version-dependent, hardcoded sound ID

    ProtocolUtils.writeMinimalKey(buf, sound.name());

    buf.writeBoolean(fixedRange != null);
    if (fixedRange != null) {
      buf.writeFloat(fixedRange);
    }

    ProtocolUtils.writeSoundSource(buf, protocolVersion, sound.source());

    ProtocolUtils.writeVarInt(buf, emitterEntityId);

    buf.writeFloat(sound.volume());

    buf.writeFloat(sound.pitch());

    buf.writeLong(sound.seed().orElse(SEEDS_RANDOM.nextLong()));
  }

  /**
   * Passes this packet to the active {@link MinecraftSessionHandler}.
   *
   * @param handler the session handler
   * @return {@code true} if handled, {@code false} otherwise
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  /**
   * Gets the sound to play.
   *
   * @return the sound
   */
  public Sound getSound() {
    return sound;
  }

  /**
   * Sets the sound to play.
   *
   * @param sound the sound
   */
  public void setSound(final Sound sound) {
    this.sound = sound;
  }

  /**
   * Gets the fixed attenuation range for the sound.
   *
   * @return the fixed range, or {@code null} if default
   */
  public @Nullable Float getFixedRange() {
    return fixedRange;
  }

  /**
   * Sets the fixed attenuation range for the sound.
   *
   * @param fixedRange the fixed range, or {@code null} to use default
   */
  public void setFixedRange(final @Nullable Float fixedRange) {
    this.fixedRange = fixedRange;
  }

  /**
   * Gets the entity ID of the sound emitter.
   *
   * @return the emitter entity ID
   */
  public int getEmitterEntityId() {
    return emitterEntityId;
  }

  /**
   * Sets the entity ID of the sound emitter.
   *
   * @param emitterEntityId the emitter entity ID
   */
  public void setEmitterEntityId(final int emitterEntityId) {
    this.emitterEntityId = emitterEntityId;
  }
}
