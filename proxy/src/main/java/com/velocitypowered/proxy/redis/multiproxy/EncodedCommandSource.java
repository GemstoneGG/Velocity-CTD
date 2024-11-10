/*
 * Copyright (C) 2024 Velocity Contributors
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

package com.velocitypowered.proxy.redis.multiproxy;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.VelocityServer;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encodes a command source over Redis, for sending replies to user commands.
 */
public class EncodedCommandSource {
  private static final Logger logger = LoggerFactory.getLogger(EncodedCommandSource.class);
  private final String target;
  private final String targetProxy;

  private EncodedCommandSource(String target, String targetProxy) {
    this.target = target;
    this.targetProxy = targetProxy;
  }

  /**
   * Creates an {@link EncodedCommandSource} from a Velocity {@link CommandSource} and a proxy ID.
   *
   * @param source the command source to use
   * @param proxyId the ID of this proxy
   * @return a {@link EncodedCommandSource} that replies to the given source
   */
  public static EncodedCommandSource from(CommandSource source, String proxyId) {
    String target;

    if (source instanceof ConsoleCommandSource) {
      target = "#console";
    } else if (source instanceof Player player) {
      target = player.getUniqueId().toString();
    } else {
      throw new IllegalArgumentException("unsupported command source passed to EncodedCommandSource: " + source);
    }

    return new EncodedCommandSource(target, proxyId);
  }

  /**
   * Creates an {@link EncodedCommandSource} that sends a message globally (all players and console) on the target #
   * proxy.
   *
   * @param proxyId the ID of the target proxy
   * @return an {@link EncodedCommandSource} that sends a message globally on the target proxy.
   */
  public static EncodedCommandSource global(String proxyId) {
    return new EncodedCommandSource("#all", proxyId);
  }

  /**
   * Returns the encoded command source corresponding to the remote server console.
   *
   * @return the encoded command source corresponding to the remote server console.
   */
  public static EncodedCommandSource console(String targetProxy) {
    return new EncodedCommandSource("#console", targetProxy);
  }

  /**
   * Sends the given message to the recipients of this {@link EncodedCommandSource}, if any.
   *
   * @param server the proxy server
   * @param component the component to send
   */
  public void sendMessage(VelocityServer server, Component component) {
    if (!Objects.equals(server.getMultiProxyHandler().getOwnProxyId(), this.targetProxy)) {
      return;
    }

    if (!this.target.startsWith("#")) {
      UUID uuid;

      try {
        uuid = UUID.fromString(this.target);
      } catch (IllegalArgumentException e) {
        logger.error("invalid UUID in encoded command source", e);
        return;
      }

      server.getPlayer(uuid).ifPresent(it -> it.sendMessage(component));
      return;
    }

    switch (this.target) {
      case "#all" -> server.sendMessage(component);
      case "#console" -> server.getConsoleCommandSource().sendMessage(component);
      default -> logger.warn("invalid target in encoded command source: {}", this.target);
    }
  }

  public String proxy() {
    return this.targetProxy;
  }
}
