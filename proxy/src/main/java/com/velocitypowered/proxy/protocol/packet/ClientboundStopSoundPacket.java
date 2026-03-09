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

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import javax.annotation.Nullable;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;

/**
 * A clientbound packet instructing the client to stop one or more sounds.
 *
 * <p>This packet supports specifying a {@link Sound.Source}, a {@link Key} sound identifier,
 * or both together. If neither is specified, the client will stop all currently playing sounds.</p>
 */
public class ClientboundStopSoundPacket implements MinecraftPacket {

  /**
   * The sound source to stop (e.g. {@link Sound.Source#MUSIC}), or {@code null} if unspecified.
   */
  private @Nullable Sound.Source source;

  /**
   * The specific sound identifier to stop, or {@code null} if unspecified.
   */
  private @Nullable Key soundName;

  /**
   * Constructs an empty stop sound packet.
   *
   * <p>Used primarily for deserialization.</p>
   */
  public ClientboundStopSoundPacket() {
  }

  /**
   * Constructs a stop sound packet from a {@link SoundStop}.
   *
   * @param soundStop the stop instruction containing optional source and key
   */
  public ClientboundStopSoundPacket(final SoundStop soundStop) {
    this(soundStop.source(), soundStop.sound());
  }

  /**
   * Constructs a stop sound packet for the given source and sound name.
   *
   * @param source the sound source, or {@code null} if not specified
   * @param soundName the sound key, or {@code null} if not specified
   */
  public ClientboundStopSoundPacket(final @Nullable Sound.Source source, final @Nullable Key soundName) {
    this.source = source;
    this.soundName = soundName;
  }

  /**
   * Decodes this packet from the buffer.
   *
   * @param buf the input buffer
   * @param direction the protocol direction
   * @param protocolVersion the client protocol version
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion protocolVersion) {
    int flagsBitmask = buf.readByte();

    if ((flagsBitmask & 1) != 0) {
      source = ProtocolUtils.readSoundSource(buf, protocolVersion);
    } else {
      source = null;
    }

    if ((flagsBitmask & 2) != 0) {
      soundName = ProtocolUtils.readKey(buf);
    } else {
      soundName = null;
    }
  }

  /**
   * Encodes this packet to the buffer.
   *
   * @param buf the output buffer
   * @param direction the protocol direction
   * @param protocolVersion the client protocol version
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion protocolVersion) {
    int flagsBitmask = 0;
    if (source != null && soundName == null) {
      flagsBitmask |= 1;
    } else if (soundName != null && source == null) {
      flagsBitmask |= 2;
    } else if (source != null) {
      flagsBitmask |= 3;
    }

    buf.writeByte(flagsBitmask);

    if (source != null) {
      ProtocolUtils.writeSoundSource(buf, protocolVersion, source);
    }

    if (soundName != null) {
      ProtocolUtils.writeMinimalKey(buf, soundName);
    }
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
   * Gets the sound source to stop.
   *
   * @return the sound source, or {@code null} if not specified
   */
  @Nullable
  public Sound.Source getSource() {
    return source;
  }

  /**
   * Sets the sound source to stop.
   *
   * @param source the sound source, or {@code null} to unset
   */
  public void setSource(final @Nullable Sound.Source source) {
    this.source = source;
  }

  /**
   * Gets the specific sound key to stop.
   *
   * @return the sound key, or {@code null} if not specified
   */
  @Nullable
  public Key getSoundName() {
    return soundName;
  }

  /**
   * Sets the specific sound key to stop.
   *
   * @param soundName the sound key, or {@code null} to unset
   */
  public void setSoundName(final @Nullable Key soundName) {
    this.soundName = soundName;
  }
}
