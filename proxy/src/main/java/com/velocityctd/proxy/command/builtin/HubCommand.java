/*
 * Copyright (C) 2026 Velocity-CTD Contributors
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

package com.velocityctd.proxy.command.builtin;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static java.util.Objects.requireNonNull;

import com.mojang.brigadier.context.CommandContext;
import com.velocityctd.proxy.command.CommandUtils;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.builtin.BuiltinCommandDefinition;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.connection.util.ConnectionMessages;
import com.velocitypowered.proxy.connection.util.ConnectionRequestResults;
import com.velocitypowered.proxy.connection.util.FallbackServers;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.translation.GlobalTranslator;
import org.jetbrains.annotations.Nullable;

/**
 * Implements Velocity-CTD's {@code /hub} command.
 */
public class HubCommand implements BuiltinCommandDefinition {

  private final VelocityServer server;

  public HubCommand(VelocityServer server) {
    this.server = server;
  }

  @Override
  public String label() {
    return "hub";
  }

  @Override
  public BrigadierCommand build() {
    return new BrigadierCommand(BrigadierCommand
            .literalArgumentBuilder(label())
            .requires(src -> src instanceof ConnectedPlayer && src.getPermissionValue("velocity.command.hub") == Tristate.TRUE)
            .executes(this::hub)
            .build()
    );
  }

  private int hub(CommandContext<CommandSource> context) {
    if (!(context.getSource() instanceof ConnectedPlayer player)) {
      return 0;
    }

    VelocityServerConnection con = player.getCurrentServer().orElse(null);
    requireNonNull(con);

    VelocityRegisteredServer currentServer = con.getServer();
    requireNonNull(currentServer);

    List<VelocityRegisteredServer> candidates = resolveHubServers(player).calculateRetryDeque(server).stream()
        .map(server::getServer)
        .flatMap(Optional::stream)
        .distinct()
        .toList();
    if (candidates.isEmpty()) {
      player.sendMessage(Component.translatable("velocity.command.no-fallbacks"));
      return 0;
    }

    // The chain is only walked up to the server the player is already on: every hub they would
    // rather be on has turned them away, and the one they are on is itself a perfectly good hub.
    Deque<VelocityRegisteredServer> serversToTry = new ArrayDeque<>();
    for (VelocityRegisteredServer candidate : candidates) {
      if (hasSameName(candidate, currentServer)) {
        break;
      }

      serversToTry.add(candidate);
    }

    if (serversToTry.isEmpty()) {
      // The player is already on the most preferred hub.
      sendAlreadyConnected(player, currentServer);
      return 0;
    }

    // Whether the player is on a hub themselves, which decides what they are told once the chain in
    // front of them is exhausted.
    boolean currentServerIsHub = serversToTry.size() < candidates.size();
    connectToFirstAvailable(player, currentServer, serversToTry, currentServerIsHub);

    return SINGLE_SUCCESS;
  }

  /**
   * Attempts each remaining hub in order until one accepts the player. Failed hops are logged but
   * never reported to the player, so that a single {@code /hub} cannot produce a pile of connection
   * errors; only the outcome of the chain as a whole is.
   */
  private void connectToFirstAvailable(ConnectedPlayer player, VelocityRegisteredServer currentServer,
                                       Deque<VelocityRegisteredServer> serversToTry, boolean currentServerIsHub) {
    VelocityRegisteredServer nextServer = serversToTry.poll();
    if (nextServer == null) {
      if (currentServerIsHub) {
        sendAlreadyConnected(player, currentServer);
      } else {
        player.sendMessage(Component.translatable("velocity.error.no-available-servers"));
      }

      return;
    }

    if (fallbackConnectingTranslationExists(player)) {
      player.sendMessage(Component.translatable("velocity.command.hub.fallback-connecting")
              .arguments(Component.text(nextServer.getServerInfo().getName())));
    }

    if (CommandUtils.shouldQueue(server, player, nextServer)) {
      // The queue owns getting the player onto this server from here on out, retries included.
      server.getQueueManager().queue(player, nextServer);
      return;
    }

    player.createConnectionRequest(nextServer).connect().whenComplete((result, throwable) -> {
      if (shouldTryNextHub(player, nextServer, result, throwable)) {
        connectToFirstAvailable(player, currentServer, serversToTry, currentServerIsHub);
      }
    });
  }

  /**
   * Handles the outcome of a single hub attempt and decides whether the walk continues. It stops
   * when the player is on their way somewhere, has already been told why they are not, or can no
   * longer be moved safely.
   */
  private boolean shouldTryNextHub(ConnectedPlayer player, VelocityRegisteredServer hub,
                                   @Nullable ConnectionRequestBuilder.Result result, @Nullable Throwable throwable) {
    if (throwable == null) {
      switch (result.getStatus()) {
        case SUCCESS, CONNECTION_CANCELLED -> {
          // Either the player made it, or whatever cancelled the request (a plugin, a version
          // mismatch, a queue restriction) has already explained itself and must be honored.
          return false;
        }
        case ALREADY_CONNECTED -> {
          player.sendMessage(ConnectionMessages.ALREADY_CONNECTED);
          return false;
        }
        case CONNECTION_IN_PROGRESS -> {
          player.sendMessage(ConnectionMessages.IN_PROGRESS);
          return false;
        }
        default -> {
          // SERVER_DISCONNECTED: the backend turned us away, so drop down to the next hub.
        }
      }
    }

    if (result instanceof ConnectionRequestResults.Impl impl && !impl.isSafe()) {
      // The proxy has already disconnected the player over this, and reported it. The channel close
      // that follows is asynchronous, so isActive() cannot be relied on to notice.
      return false;
    }

    Component reason = result == null ? null : result.getReasonComponent().orElse(null);
    CommandUtils.reportConnectionFailure(server, player, hub, reason, throwable);

    return player.isActive();
  }

  private static void sendAlreadyConnected(ConnectedPlayer player, VelocityRegisteredServer currentServer) {
    player.sendMessage(Component.translatable("velocity.command.hub.fallback-already-connected")
            .arguments(Component.text(currentServer.getServerInfo().getName())));
  }

  private static boolean hasSameName(VelocityRegisteredServer first, VelocityRegisteredServer second) {
    return first.getServerInfo().getName().equalsIgnoreCase(second.getServerInfo().getName());
  }

  /**
   * Resolves the servers {@code /hub} should consider for the given player: {@code hub-servers}
   * when it is configured, and otherwise their regular fallback chain.
   */
  private FallbackServers resolveHubServers(ConnectedPlayer player) {
    List<String> hubServers = server.getConfiguration().getHubServers();
    if (hubServers.isEmpty()) {
      return FallbackServers.resolveFallbackServers(server.getConfiguration(), player);
    }

    return new FallbackServers(
        hubServers,
        server.getConfiguration().getDynamicFallbackFilter(),
        null
    );
  }

  private static boolean fallbackConnectingTranslationExists(ConnectedPlayer player) {
    Locale locale = player.getEffectiveLocale();

    if (locale == null) {
      locale = Locale.ENGLISH;
    }

    Component format = GlobalTranslator.translator().translate(Component.translatable("velocity.command.hub.fallback-connecting"), locale);
    return format != null && !format.equals(Component.empty());
  }
}
