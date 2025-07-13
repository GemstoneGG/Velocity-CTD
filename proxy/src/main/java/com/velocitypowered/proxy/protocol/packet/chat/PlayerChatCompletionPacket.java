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

package com.velocitypowered.proxy.protocol.packet.chat;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;

/**
 * Represents a packet sent between the server and client to handle chat completion suggestions.
 * This packet allows the server to send chat message completions or suggestions to the client,
 * helping users complete commands or chat messages.
 */
public class PlayerChatCompletionPacket implements MinecraftPacket {

  /**
   * The array of string completions suggested by the server.
   */
  private String[] completions;

  /**
   * The action that determines how the completions will be applied on the client.
   */
  private Action action;

  /**
   * Constructs an empty {@code PlayerChatCompletionPacket} for decoding purposes.
   */
  public PlayerChatCompletionPacket() {
  }

  /**
   * Constructs a {@code PlayerChatCompletionPacket} with the specified completions and action.
   *
   * @param completions the string completions to send
   * @param action the completion action to apply
   */
  public PlayerChatCompletionPacket(final String[] completions, final Action action) {
    this.completions = completions;
    this.action = action;
  }

  /**
   * Returns the current array of suggested completions.
   *
   * @return the completions array
   */
  public String[] getCompletions() {
    return completions;
  }

  /**
   * Returns the action associated with this packet.
   *
   * @return the {@link Action} to be applied
   */
  public Action getAction() {
    return action;
  }

  /**
   * Sets the completions array for this packet.
   *
   * @param completions the new completions to apply
   */
  public void setCompletions(final String[] completions) {
    this.completions = completions;
  }

  /**
   * Sets the action to be applied for this completion update.
   *
   * @param action the new {@link Action}
   */
  public void setAction(final Action action) {
    this.action = action;
  }

  @Override
  public final void decode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                           final ProtocolVersion protocolVersion) {
    action = Action.values()[ProtocolUtils.readVarInt(buf)];
    completions = ProtocolUtils.readStringArray(buf);
  }

  @Override
  public final void encode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                           final ProtocolVersion protocolVersion) {
    ProtocolUtils.writeVarInt(buf, action.ordinal());
    ProtocolUtils.writeStringArray(buf, completions);
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  /**
   * Represents the different actions that can be taken with chat completions.
   */
  public enum Action {

    /**
     * Add the specified completions to the client's current suggestion list.
     */
    ADD,

    /**
     * Remove the specified completions from the client's current suggestion list.
     */
    REMOVE,

    /**
     * Replace the client's entire suggestion list with the specified completions.
     */
    SET
  }
}
