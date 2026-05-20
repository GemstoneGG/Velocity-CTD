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
import org.apache.logging.log4j.Logger;

public interface ChatHandler<T extends MinecraftPacket> {

  Logger logger();

  Class<T> packetClass();

  void handlePlayerChatInternal(T packet);

  default boolean handlePlayerChat(MinecraftPacket packet) {
    if (packetClass().isInstance(packet)) {
      handlePlayerChatInternal(packetClass().cast(packet));
      return true;
    }

    return false;
  }

  default void invalidCancel(ConnectedPlayer player) {
    logger().fatal("A plugin tried to cancel a signed chat message."
        + " This is no longer possible in 1.19.1 and newer. "
        + "Disconnecting player {}", player.getUsername());
    player.disconnect(Component.text("A proxy plugin caused an illegal protocol state. "
        + "Contact your network administrator."));
  }

  default void invalidChange(ConnectedPlayer player) {
    logger().fatal("A plugin tried to change a signed chat message. "
        + "This is no longer possible in 1.19.1 and newer. "
        + "Disconnecting player {}", player.getUsername());
    player.disconnect(Component.text("A proxy plugin caused an illegal protocol state. "
        + "Contact your network administrator."));
  }
}
