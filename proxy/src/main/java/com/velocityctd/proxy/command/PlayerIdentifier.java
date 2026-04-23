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

package com.velocityctd.proxy.command;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocityctd.proxy.cluster.VelocityClusterPlayer;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.Collection;
import java.util.Collections;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Utility for resolving player identifier strings used across commands.
 *
 * <p>Supported identifier formats:</p>
 * <ul>
 *   <li>{@code all} &mdash; all players in the cluster</li>
 *   <li>{@code current} &mdash; all players on the sender's current server</li>
 *   <li>{@code +<server>} &mdash; all players on the given server</li>
 *   <li>{@code -<proxy>} &mdash; all players on the given proxy (only in multi-proxy mode)</li>
 *   <li>{@code <player>} &mdash; a specific player by name</li>
 * </ul>
 */
public final class PlayerIdentifier {

  /**
   * The type of identifier that was parsed.
   */
  public enum Type {
    /** The {@code all} keyword. */
    ALL,
    /** The {@code current} keyword (sender's current server). */
    CURRENT_SERVER,
    /** A {@code +<server>} prefix. */
    SERVER,
    /** A {@code -<proxy>} prefix. */
    PROXY,
    /** One or more player names (possibly comma-separated). */
    PLAYER,
    /** The source is not a player (failed {@code current} from console). */
    PLAYER_EXECUTOR_REQUIRED
  }

  /**
   * The result of resolving a player identifier.
   *
   * @param type the type of identifier that was parsed
   * @param success whether the identifier was resolved successfully
   * @param players the resolved players (empty on failure)
   * @param name contextual display name: the server name for {@link Type#SERVER}
   *             and {@link Type#CURRENT_SERVER}, the proxy ID for {@link Type#PROXY},
   *             or the input that failed resolution on failure
   */
  public record Result(
      Type type,
      boolean success,
      Collection<VelocityClusterPlayer> players,
      @Nullable String name
  ) {

    @Override
    public String name() {
      if (name == null) {
        throw new NullPointerException("name");
      }
      return name;
    }

    private static Result success(Type type, Collection<VelocityClusterPlayer> players, @Nullable String name) {
      return new Result(type, true, players, name);
    }

    private static Result failure(Type type, @Nullable String name) {
      return new Result(type, false, Collections.emptyList(), name);
    }
  }

  private PlayerIdentifier() {
    throw new AssertionError();
  }

  /**
   * Creates a suggestion provider that suggests all identifier formats.
   *
   * @param server the proxy server instance
   * @param argName the name of the string argument to complete
   * @return a suggestion provider for player identifiers
   */
  public static SuggestionProvider<CommandSource> suggest(VelocityServer server, String argName) {
    return (ctx, builder) -> {
      String argument = ctx.getArguments().containsKey(argName)
          ? ctx.getArgument(argName, String.class)
          : "";

      if ("all".regionMatches(true, 0, argument, 0, argument.length())) {
        builder.suggest("all");
      }

      if ("current".regionMatches(true, 0, argument, 0, argument.length())
          && ctx.getSource() instanceof ConnectedPlayer) {
        builder.suggest("current");
      }

      if (argument.isEmpty() || argument.startsWith("+")) {
        for (VelocityRegisteredServer registeredServer : server.getAllServers()) {
          String serverName = registeredServer.getServerInfo().getName();
          if (serverName.regionMatches(true, 0, argument, 1, argument.length() - 1)) {
            builder.suggest("+" + serverName);
          }
        }
      }

      if ((argument.isEmpty() || argument.startsWith("-")) && server.getClusterProxyService().isMultiProxy()) {
        for (String id : server.getClusterProxyService().getAllProxyIds()) {
          if (id.regionMatches(true, 0, argument, 1, argument.length() - 1)) {
            builder.suggest("-" + id);
          }
        }
      }

      for (String playerName : server.getClusterPlayerService().getPlayerNames()) {
        if (playerName.regionMatches(true, 0, argument, 0, argument.length())) {
          builder.suggest(playerName);
        }
      }

      return builder.buildFuture();
    };
  }

  /**
   * Resolves a player identifier into a result containing the matched players
   * and metadata about how they were resolved.
   *
   * <p>This method does <b>not</b> send any messages to the source. The caller
   * is responsible for checking {@link Result#success()} and sending appropriate
   * error or success messages based on {@link Result#type()} and {@link Result#name()}.</p>
   *
   * @param server the proxy server instance
   * @param identifier the identifier string to resolve
   * @param source the command source (used for {@code current} resolution)
   * @return the resolution result
   */
  public static Result resolve(VelocityServer server, String identifier, CommandSource source) {
    if (identifier.equalsIgnoreCase("all")) {
      return Result.success(Type.ALL, server.getClusterPlayerService().getAllPlayers(), null);
    }

    if (identifier.equalsIgnoreCase("current")) {
      if (!(source instanceof ConnectedPlayer sender)) {
        return Result.failure(Type.PLAYER_EXECUTOR_REQUIRED, null);
      }

      VelocityServerConnection currentServer = sender.getCurrentServer().orElse(null);
      if (currentServer == null) {
        return Result.failure(Type.CURRENT_SERVER, "current");
      }

      String serverName = currentServer.getServerInfo().getName();
      return Result.success(Type.CURRENT_SERVER, server.getClusterPlayerService().getPlayersOnServer(serverName), serverName);
    }

    if (identifier.startsWith("+") && identifier.length() > 1) {
      String serverInput = identifier.substring(1);
      VelocityRegisteredServer found = findServer(server, serverInput);
      if (found == null) {
        return Result.failure(Type.SERVER, serverInput);
      }

      String resolvedName = found.getServerInfo().getName();
      return Result.success(Type.SERVER, server.getClusterPlayerService().getPlayersOnServer(resolvedName), resolvedName);
    }

    if (identifier.startsWith("-") && identifier.length() > 1
        && server.getClusterProxyService().isMultiProxy()) {
      String proxyInput = identifier.substring(1);
      String realId = server.getClusterProxyService().getAllProxyIds().stream()
          .filter(id -> id.equalsIgnoreCase(proxyInput))
          .findFirst().orElse(null);

      if (realId == null) {
        return Result.failure(Type.PROXY, proxyInput);
      }

      return Result.success(Type.PROXY, server.getClusterPlayerService().getPlayersOnProxy(realId), realId);
    }

    VelocityClusterPlayer singlePlayer = server.getClusterPlayerService().getPlayer(identifier).orElse(null);
    if (singlePlayer == null) {
      return Result.failure(Type.PLAYER, identifier);
    }

    return Result.success(Type.PLAYER, Collections.singleton(singlePlayer), null);
  }

  /**
   * Finds a server by name using fuzzy matching (exact &gt; contains).
   * Returns {@code null} if no match or if the match is ambiguous.
   */
  private static @Nullable VelocityRegisteredServer findServer(VelocityServer server, String input) {
    String lower = input.toLowerCase();
    VelocityRegisteredServer exact = null;
    VelocityRegisteredServer contains = null;
    boolean ambiguous = false;

    for (VelocityRegisteredServer rs : server.getAllServers()) {
      String name = rs.getServerInfo().getName().toLowerCase();
      if (name.equals(lower)) {
        exact = rs;
        break;
      }
      if (name.contains(lower)) {
        if (contains != null) {
          ambiguous = true;
        }
        contains = rs;
      }
    }

    if (exact != null) {
      return exact;
    }

    return ambiguous ? null : contains;
  }
}
