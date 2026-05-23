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

package com.velocitypowered.proxy.protocol.packet.chat;

import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import net.kyori.adventure.text.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Helpers for handling protocol violations around signed chat and signable commands.
 */
public final class SignedChatViolations {

  private static final Logger LOGGER = LogManager.getLogger(SignedChatViolations.class);

  private static final Component DISCONNECT_REASON = Component.text(
      "A proxy plugin caused an illegal protocol state. "
          + "Contact your network administrator.");

  private SignedChatViolations() {
    throw new AssertionError();
  }

  public static void invalidCancel(ConnectedPlayer player) {
    LOGGER.fatal("A plugin tried to cancel a signed chat message."
        + " This is no longer possible in 1.19.1 and newer. "
        + "Disconnecting player {}", player.getUsername());
    player.disconnect(DISCONNECT_REASON);
  }

  public static void invalidChange(ConnectedPlayer player) {
    LOGGER.fatal("A plugin tried to change a signed chat message. "
        + "This is no longer possible in 1.19.1 and newer. "
        + "Disconnecting player {}", player.getUsername());
    player.disconnect(DISCONNECT_REASON);
  }

  /**
   * Handles a plugin attempting to deny or change a command that carries signable
   * components.
   *
   * @param what   verb describing the violation, e.g. {@code "deny"} or {@code "change"}
   * @param player the offending player; will be disconnected after the log entry
   * @param packet the command packet that triggered the violation, included in the log
   */
  public static void alterSignableComponentError(String what, ConnectedPlayer player,
                                                 MinecraftPacket packet) {
    LOGGER.fatal("A plugin tried to {} a command with signable component(s). "
        + "This is not supported. Disconnecting player {}. Command packet: {}",
        what, player.getUsername(), packet);
    player.disconnect(DISCONNECT_REASON);
  }
}
