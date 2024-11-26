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
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.builtin.VelocityCommand;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.redis.RedisManagerImpl;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements handling for setups with multiple proxies.
 */
public class MultiProxyHandler {
  private static final Logger logger = LoggerFactory.getLogger(MultiProxyHandler.class);

  private final VelocityServer server;
  private final VelocityConfiguration.Redis config;
  private boolean shuttingDown = false;
  private final Map<UUID, String> transferringServers = new HashMap<>();

  // All the players currently connected on ALL proxies, including own proxy.
  private final List<RemotePlayerInfo> allPlayers = new CopyOnWriteArrayList<>();

  private final boolean enabled;

  /**
   * Stores information about a remote player connected to a different proxy.
   * Used to track the player's UUID, name, and the server they are connected to
   * within the multi-proxy network.
   */
  public static final class RemotePlayerInfo {
    private final String proxyId;
    private final UUID uuid;
    private final String name;
    private final Map<String, Integer> queuePriority;
    private String serverName = null;
    private String queuedServer = null;
    private boolean beingTransferred = false;
    private final boolean fullQueueBypass;

    /**
     * Constructs a new {@code RemotePlayerInfo} with the specified UUID and name.
     *
     * @param uuid the player's unique identifier
     * @param name the player's name
     */
    public RemotePlayerInfo(final String proxyId, final UUID uuid, final String name, Map<String, Integer> queuePriority,
                            final boolean fullQueueBypass) {
      this.proxyId = proxyId;
      this.uuid = uuid;
      this.name = name;
      this.queuePriority = queuePriority;
      this.fullQueueBypass = fullQueueBypass;
    }

    public UUID getUuid() {
      return uuid;
    }

    public String getName() {
      return name;
    }

    public Map<String, Integer> getQueuePriority() {
      return queuePriority;
    }

    public boolean isFullQueueBypass() {
      return fullQueueBypass;
    }

    /**
     * Returns the server the player is currently connected to, or null.
     *
     * @return The server the player is currently connected to, or null.
     */
    public String getServerName() {
      if (serverName == null) {
        return "";
      }
      return serverName;
    }

    public boolean isBeingTransferred() {
      return beingTransferred;
    }

    public String getUsername() {
      return this.name;
    }

    public void setQueuedServer(final String server) {
      this.queuedServer = server;
    }

    public String getQueuedServer() {
      return this.queuedServer;
    }

    public String getProxyId() {
      return this.proxyId;
    }

    public void setServerName(final String serverName) {
      this.serverName = serverName;
    }

    public void setBeingTransferred(final boolean beingTransferred) {
      this.beingTransferred = beingTransferred;
    }
  }

  /**
   * Initializes the {@code MultiProxyHandler} to manage multi-proxy functionality
   * within the Velocity server.
   * Configures Redis-based communication for handling
   * player actions and proxy status across a network of proxies.
   *
   * <p>If Redis support is disabled in the configuration, the handler exits early.
   * Otherwise, it sets up the required Redis listeners and initiates communication
   * between proxy instances.</p>
   *
   * @param server the {@link VelocityServer} instance to access server configuration and features
   */
  public MultiProxyHandler(final VelocityServer server) {
    this.server = server;
    this.config = this.server.getConfiguration().getRedis();

    this.enabled = config.isEnabled() && config.getProxyId() != null;

    if (!this.isEnabled()) {
      return;
    }

    RedisManagerImpl redisManager = this.server.getRedisManager();

    redisManager.addProxyId(this.server.getConfiguration().getRedis().getProxyId());

    redisManager.listen(RedisPlayerJoinUpdate.ID, RedisPlayerJoinUpdate.class, it -> {
      this.handleJoin(it.player());
    });

    redisManager.listen(RedisPlayerLeaveUpdate.ID, RedisPlayerLeaveUpdate.class, it -> {
      this.handleLeave(it.uuid());
    });

    redisManager.listen(RedisPlayerServerChange.ID, RedisPlayerServerChange.class, it -> {
      for (RemotePlayerInfo player : allPlayers) {
        if (it.uuid().equals(player.uuid)) {
          player.serverName = it.server();
        }
      }
    });

    redisManager.listen(RedisShuttingDownAnnouncement.ID, RedisShuttingDownAnnouncement.class, it -> {
      handleShutdown(it.proxyId());

      if (this.server.getQueueManager().isMasterProxy()) {
        this.server.getQueueManager().schedulePingingBackend();
        this.server.getQueueManager().scheduleTickMessage();
      }
    });

    redisManager.listen(RedisStartupRequest.ID, RedisStartupRequest.class, it -> {
      this.server.getRedisManager().send(new RedisStartupFillPlayersRequest(
          this.server.getMultiProxyHandler().getAllPlayers(),
          it.proxyId()
      ));

      this.server.getScheduler().buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
        if (!this.server.getQueueManager().isMasterProxy()) {
          this.server.getQueueManager().clearQueue();
        }
      }).delay(3, TimeUnit.SECONDS).schedule();
    });

    redisManager.listen(RedisStartupFillPlayersRequest.ID, RedisStartupFillPlayersRequest.class, it -> {
      for (RemotePlayerInfo info : it.players()) {
        this.server.getMultiProxyHandler().getAllPlayers().removeIf(i -> i.getUuid().equals(info.getUuid()));
      }

      this.server.getMultiProxyHandler().getAllPlayers().addAll(it.players());
    });

    redisManager.listen(RedisGenericReplyRequest.ID, RedisGenericReplyRequest.class, it -> {
      if (!it.targetProxy().equals(this.getOwnProxyId())) {
        return;
      }

      switch (it.type()) {
        case RELOAD -> {
          logger.info("Reloading Velocity configuration on remote request from {}...", it.source().proxy());

          try {
            if (this.server.reloadConfiguration()) {
              sendMessage(it.source(), Component.translatable("velocity.command.reload-success"));
              logger.info("Reloaded Velocity configuration on remote request from {}.", it.source().proxy());
            } else {
              sendMessage(it.source(), Component.translatable("velocity.command.reload-failure"));
              logger.error("Failed to reload Velocity configuration on remote request from {}!", it.source().proxy());
            }
          } catch (Exception e) {
            sendMessage(it.source(), Component.translatable("velocity.command.reload-failure"));
            logger.error("Failed to reload Velocity configuration on remote request from {}!", it.source().proxy(), e);
          }
        }

        case UPTIME -> sendMessage(it.source(), VelocityCommand.getUptimeComponent(server));
        default -> throw new IllegalStateException("invalid `RedisGenericReplyRequest` variant");
      }
    });

    redisManager.listen(RedisSendMessage.ID, RedisSendMessage.class, it -> {
      Component component = it.component();

      if (component != null) {
        it.target().sendMessage(server, component);
      }
    });

    redisManager.listen(RedisSudo.ID, RedisSudo.class, it -> {
      if (!it.targetProxy().equals(getOwnProxyId())) {
        return;
      }

      server.getPlayer(it.playerUuid()).ifPresentOrElse(player -> {

        if (it.message().startsWith("/")) {
          String[] split = it.message().split(" ");
          String command = split[0].substring(1);
          if (this.server.getCommandManager().hasCommand(command)) {
            this.server.getCommandManager().executeAsync(player, it.message().substring(1));
          } else {
            player.spoofChatInput(it.message());
          }

          it.replySource().sendMessage(server, Component.translatable(
              "velocity.command.sudo.command-executed",
              NamedTextColor.GREEN,
              Component.text(player.getUsername()),
              Component.text(it.message())
          ));
        } else {
          player.spoofChatInput(it.message());
          it.replySource().sendMessage(server, Component.translatable(
              "velocity.command.sudo.message-sent",
              NamedTextColor.GREEN,
              Component.text(player.getUsername()),
              Component.text(it.message())
          ));
        }
      },
          () -> it.replySource().sendMessage(server, Component.translatable(
              "velocity.command.remote-player-not-found",
              NamedTextColor.RED
          ))
      );
    });

    redisManager.send(new RedisStartupRequest(config.getProxyId()));
  }

  public Map<UUID, String> getTransferringServers() {
    return transferringServers;
  }

  private void handleShutdown(final String proxyId) {
    allPlayers.removeIf(info -> info.proxyId.equalsIgnoreCase(proxyId));
  }


  private void handleLeave(final UUID player) {
    allPlayers.removeIf(info -> info.uuid.equals(player));
  }

  private void handleJoin(final RemotePlayerInfo player) {
    // This handles the edge case if a player joins two proxies at once, once the player info broadcast is received,
    // we disconnect them from the local proxy.
    for (RemotePlayerInfo info : this.allPlayers) {
      if (info.uuid.equals(player.uuid)) {
        this.server.getPlayer(info.uuid).ifPresent(p -> {
          p.disconnect(Component.translatable("velocity.error.already-connected-proxy.remote"));
        });
      }
    }

    allPlayers.add(player);
  }

  /**
   * Gets the ID of the local proxy.
   *
   * @return the ID of the local proxy, if one is defined.
   */
  public @Nullable String getOwnProxyId() {
    return config.getProxyId();
  }

  /**
   * Checks if the multi-proxy setup is enabled based on the Redis configuration.
   * A multi-proxy setup is enabled if Redis is configured and a unique proxy ID is set.
   *
   * @return {@code true} if the multi-proxy setup is enabled; {@code false} otherwise
   */
  public boolean isEnabled() {
    return enabled;
  }


  /**
   * Handles the event when a player leaves the proxy.
   * Sends a {@link RedisPlayerLeaveUpdate} to notify other proxies in the multi-proxy setup.
   *
   * @param player the {@link ConnectedPlayer} that left
   */
  public void onPlayerLeave(final ConnectedPlayer player) {
    if (shuttingDown) {
      return;
    }

    this.server.getRedisManager().send(new RedisPlayerLeaveUpdate(this.config.getProxyId(), player.getUniqueId()));
  }

  /**
   * Handles server switching, updating the stored server connection.
   *
   * @param player The UUID of the player.
   * @param serverName The name of the server the player is connecting to.
   */
  public void handleServerSwitch(final UUID player, final String serverName) {
    this.server.getRedisManager().send(new RedisPlayerServerChange(getOwnProxyId(),
            player, serverName));
  }

  /**
   * Handles the event when a player joins the proxy.
   * Sends a {@link RedisPlayerJoinUpdate} to notify other proxies in the multi-proxy setup.
   *
   * @param player the {@link ConnectedPlayer} that joined
   */
  public boolean onPlayerJoin(final ConnectedPlayer player) {
    if (shuttingDown) {
      return false;
    }

    // check for dupe connections on foreign proxies and disconnect.
    for (String proxyId : this.getAllProxyIds()) {
      if (proxyId.equals(this.getOwnProxyId())) {
        continue;
      }

      for (RemotePlayerInfo playerInfo : this.getPlayers(proxyId)) {
        if (playerInfo.uuid.equals(player.getUniqueId())) {
          return true;
        }
      }
    }

    Map<String, Integer> queuePriorities = new HashMap<>();

    for (RegisteredServer s : this.server.getAllServers()) {
      queuePriorities.put(s.getServerInfo().getName(), player.getQueuePriority(s.getServerInfo().getName()));
    }
    queuePriorities.put("all", player.getQueuePriority("all"));

    this.server.getRedisManager().send(new RedisPlayerJoinUpdate(new RemotePlayerInfo(
        this.config.getProxyId(), player.getUniqueId(), player.getUsername(),
            queuePriorities,
        player.hasPermission("velocity.queue.full.bypass"))));
    return false;
  }

  /**
   * Initiates the shutdown sequence for this proxy in the multi-proxy setup.
   * Sends a {@link RedisShuttingDownAnnouncement} message to notify other proxies that this proxy
   * instance is shutting down, ensuring they can update the status accordingly.
   */
  public void shutdown() {
    shuttingDown = true;
    this.server.getRedisManager().removeProxyId(this.server.getConfiguration().getRedis().getProxyId());

    this.server.getRedisManager().send(new RedisShuttingDownAnnouncement(this.config.getProxyId()));
  }

  /**
   * Retrieves the total player counts across all connected proxies in the multi-proxy setup.
   *
   * @return the combined player count from this proxy and all other known proxies
   */
  public int getTotalPlayerCount() {
    return allPlayers.size();
  }

  /**
   * Returns the set of all proxy IDs known to this proxy.
   *
   * @return the list of all proxy IDs
   */
  public List<String> getAllProxyIds() {
    List<String> ids = this.server.getRedisManager().getProxyIds();
    Collections.sort(ids);
    return ids;
  }

  /**
   * Retrieves a list of players connected to a specified proxy in the multi-proxy setup.
   *
   * @param proxyId the ID of the proxy to retrieve players from
   * @return a {@link List} of {@link RemotePlayerInfo} representing the players on the specified proxy,
   *         or {@code null} if the proxy ID is unknown
   */
  public List<RemotePlayerInfo> getPlayers(final String proxyId) {
    return allPlayers.stream()
        .filter(info -> info.getProxyId().equalsIgnoreCase(proxyId))
        .toList();
  }

  /**
   * Requests a remote proxy reloads its config (as if executing {@code /velocity reload}).
   *
   * @param proxyId the ID of the remote proxy
   * @param source the source to send replies back to
   */
  public void requestReload(final String proxyId, @Nullable final CommandSource source) {
    this.server.getRedisManager().send(new RedisGenericReplyRequest(
        RedisGenericReplyRequest.Type.RELOAD, proxyId, EncodedCommandSource.from(source, this.getOwnProxyId())));
  }

  /**
   * Requests a remote proxy sends an uptime report (as if executing {@code /velocity uptime}).
   *
   * @param proxyId the ID of the remote proxy
   * @param source the source to send replies back to
   */
  public void requestUptime(final String proxyId, final CommandSource source) {
    this.server.getRedisManager().send(new RedisGenericReplyRequest(
        RedisGenericReplyRequest.Type.UPTIME, proxyId, EncodedCommandSource.from(source, this.getOwnProxyId())));
  }

  /**
   * Requests a remote proxy reloads its config (as if executing {@code /velocity reload}).
   *
   * @param target the target of the message
   * @param component the message to send
   */
  public void sendMessage(final EncodedCommandSource target, final Component component) {
    this.server.getRedisManager().send(new RedisSendMessage(target, component));
  }

  /**
   * Returns the list of all known remote players.
   *
   * @return the list of all known remote players
   */
  public List<RemotePlayerInfo> getAllPlayers() {
    return allPlayers;
  }

  /**
   * Get the connected players information if it exists.
   * Can be quite resource extensive, so use with caution.
   *
   * @param uuid The UUID of the player to fetch.
   * @return the {@link RemotePlayerInfo} of the player, or null.
   */
  public RemotePlayerInfo getPlayerInfo(final UUID uuid) {
    for (RemotePlayerInfo info : getAllPlayers()) {
      if (info.uuid.equals(uuid)) {
        return info;
      }
    }
    return null;
  }

  /**
   * Get the connected players information if it exists.
   * Can be quite resource extensive, so use with caution.
   *
   * @param username The username of the player to fetch.
   * @return the {@link RemotePlayerInfo} of the player, or null.
   */
  public RemotePlayerInfo getPlayerInfo(final String username) {
    for (RemotePlayerInfo info : getAllPlayers()) {
      if (info.name.equalsIgnoreCase(username)) {
        return info;
      }
    }
    return null;
  }

  /**
   * Checks if the player is still connected to any proxy on the network.
   *
   * @param uuid The UUID of the player.
   *
   * @return Whether the player is connected to any proxy or not.
   */
  public boolean isPlayerOnline(final UUID uuid) {
    return allPlayers.stream().anyMatch(info -> info.uuid.equals(uuid));
  }

  /**
   * Checks if the player is still connected to any proxy on the network.
   *
   * @param username The username of the player.
   *
   * @return Whether the player is connected to any proxy or not.
   */
  public boolean isPlayerOnline(final String username) {
    return allPlayers.stream().anyMatch(info -> info.getUsername().equalsIgnoreCase(username));

  }

  /**
   * Alerts all servers on every proxy.
   *
   * @param component The component to broadcast on all servers.
   */
  public void alert(final Component component) {
    server.getRedisManager().send(new RedisServerAlertRequest(component));
  }

  /**
   * Runs a command on a remote player.
   *
   * @param player the target player
   * @param source where to send feedback to
   */
  public void sudo(final RemotePlayerInfo player, final CommandSource source, final String message) {
    this.server.getRedisManager().send(new RedisSudo(player.proxyId, player.uuid, EncodedCommandSource.from(source, this.getOwnProxyId()), message));
  }
}
