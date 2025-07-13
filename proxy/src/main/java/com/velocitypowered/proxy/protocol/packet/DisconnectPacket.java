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

import com.google.common.base.Preconditions;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import io.netty.buffer.ByteBuf;
import net.kyori.adventure.text.Component;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a packet sent by the server to disconnect the client. This packet contains
 * a reason for the disconnection, which is sent to the client and displayed to the player.
 * The packet can be sent in different states (e.g., login, play), which affects how the
 * reason is processed.
 */
public class DisconnectPacket implements MinecraftPacket {

  /**
   * The component holder containing the disconnection reason to be sent to the client.
   *
   * <p>This may be {@code null} until it is explicitly set or populated via decoding.</p>
   */
  private @Nullable ComponentHolder reason;

  /**
   * The {@link StateRegistry} representing the protocol state (e.g., LOGIN or PLAY)
   * during which this packet is processed.
   *
   * <p>This value determines how the reason component is serialized.</p>
   */
  private final StateRegistry state;

  /**
   * Constructs a new {@code DisconnectPacket} for the specified protocol state.
   *
   * <p>The reason for disconnection can be set later via {@link #setReason(ComponentHolder)}.</p>
   *
   * @param state the protocol state (e.g., {@link StateRegistry#LOGIN} or {@link StateRegistry#PLAY})
   */
  public DisconnectPacket(final StateRegistry state) {
    this.state = state;
  }

  private DisconnectPacket(final StateRegistry state, final ComponentHolder reason) {
    this.state = state;
    this.reason = Preconditions.checkNotNull(reason, "reason");
  }

  /**
   * Retrieves the reason for the disconnection, which will be sent to the client.
   *
   * @return the reason for the disconnection as a {@link ComponentHolder}
   * @throws IllegalStateException if no reason is specified
   */
  public ComponentHolder getReason() {
    if (reason == null) {
      throw new IllegalStateException("No reason specified");
    }

    return reason;
  }

  /**
   * Sets the disconnection reason that will be sent to the client.
   *
   * @param reason the {@link ComponentHolder} containing the disconnection message,
   *               or {@code null} to clear it
   */
  public void setReason(@Nullable final ComponentHolder reason) {
    this.reason = reason;
  }

  @Override
  public final String toString() {
    return "Disconnect{"
        + "reason='" + reason + '\''
        + '}';
  }

  @Override
  public final void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    reason = ComponentHolder.read(buf, state == StateRegistry.LOGIN
        ? ProtocolVersion.MINECRAFT_1_20_2 : version);
  }

  @Override
  public final void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    getReason().write(buf);
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  /**
   * Creates a new {@code DisconnectPacket} with the specified reason and version.
   *
   * @param component the component explaining the disconnection reason
   * @param version the protocol version in use
   * @param state the state in which the disconnection occurs
   * @return the created {@code DisconnectPacket}
   */
  public static DisconnectPacket create(final Component component, final ProtocolVersion version, final StateRegistry state) {
    Preconditions.checkNotNull(component, "component");
    return new DisconnectPacket(state, new ComponentHolder(state == StateRegistry.LOGIN
        ? ProtocolVersion.MINECRAFT_1_20_2 : version, component));
  }
}
