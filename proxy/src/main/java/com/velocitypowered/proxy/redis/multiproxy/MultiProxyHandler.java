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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
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

  private final boolean enabled;

  private int totalPlayerCount;

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

    if (!this.isRedisEnabled()) {
      return;
    }

    RedisManagerImpl redisManager = this.server.getRedisManager();

    Executors.newScheduledThreadPool(1).scheduleAtFixedRate(()
        -> totalPlayerCount = redisManager.getCache().size(), 100, 100, TimeUnit.MILLISECONDS);

    redisManager.listen(RedisShuttingDownAnnouncement.ID, RedisShuttingDownAnnouncement.class, it -> {
      handleShutdown(it.proxyId());

      if (this.server.getQueueManager().isMasterProxy()) {
        this.server.getQueueManager().schedulePingingBackend();
        this.server.getQueueManager().scheduleTickMessage();
        this.server.getQueueManager().rescheduleTimerTask();
      }
    });

    redisManager.listen(RedisStartupRequest.ID, RedisStartupRequest.class, it -> {
      if (it.proxyId().equalsIgnoreCase(getOwnProxyId())) {
        return;
      }

      this.server.getScheduler().buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
        if (!this.server.getQueueManager().isMasterProxy()) {
          this.server.getQueueManager().clearQueue();
        }
      }).delay(3, TimeUnit.SECONDS).schedule();
    });

    redisManager.listen(RedisGenericReplyRequest.ID, RedisGenericReplyRequest.class, it -> {
      if (!it.targetProxy().equalsIgnoreCase(this.getOwnProxyId())) {
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

      server.getPlayer(it.playerUuid()).ifPresent(player -> {
        if (it.message().startsWith("/")) {
          String[] split = it.message().split(" ");
          String command = split[0].substring(1);
          if (this.server.getCommandManager().hasCommand(command)) {
            this.server.getCommandManager().executeAsync(player, it.message().substring(1));
          } else {
            player.spoofChatInput(it.message());
          }
        } else {
          player.spoofChatInput(it.message());
        }
      }
      );
    });

    redisManager.send(new RedisStartupRequest(config.getProxyId()));
  }

  public Map<UUID, String> getTransferringServers() {
    return transferringServers;
  }

  private void handleShutdown(final String proxyId) {
    List<RemotePlayerInfo> info = this.server.getRedisManager().getCache();

    for (RemotePlayerInfo player : info) {
      if (player.getProxyId().equalsIgnoreCase(proxyId)) {
        this.server.getRedisManager().removePlayer(player);
      }
    }
  }

  private void handleLeave(final UUID player) {
    List<RemotePlayerInfo> info = this.server.getRedisManager().getCache();

    for (RemotePlayerInfo p : info) {
      if (p.getUuid().equals(player)) {
        this.server.getRedisManager().removePlayer(p);
      }
    }
  }

  private void handleJoin(final RemotePlayerInfo player) {
    this.server.getRedisManager().addOrUpdatePlayer(player);
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
  public boolean isRedisEnabled() {
    return enabled;
  }

  /**
   * Handles the event when a player leaves the proxy.
   *
   * @param player the {@link ConnectedPlayer} that left
   */
  public void onPlayerLeave(final ConnectedPlayer player) {
    if (shuttingDown) {
      return;
    }

    if (player.isDontRemoveFromRedis()) {
      return;
    }

    if (getPlayerInfo(player.getUniqueId()).isBeingTransferred()) {
      return;
    }

    this.server.getMultiProxyHandler().handleLeave(player.getUniqueId());
  }

  /**
   * Handles server switching, updating the stored server connection.
   *
   * @param player The UUID of the player.
   * @param serverName The name of the server the player is connecting to.
   */
  public void handleServerSwitch(final ConnectedPlayer player, final String serverName) {
    RemotePlayerInfo info = this.server.getRedisManager().getCache().stream().filter(i -> i.getUuid()
        .equals(player.getUniqueId())).findFirst().orElse(null);
    if (info == null) {
      info = createPlayerInfo(player);
    }

    info.setServerName(serverName);
    this.server.getRedisManager().addOrUpdatePlayer(info);
  }

  /**
   * Handles the event when a player joins the proxy.
   *
   * @param player the {@link ConnectedPlayer} that joined
   */
  public boolean onPlayerJoin(final ConnectedPlayer player) {
    if (shuttingDown) {
      return false;
    }

    List<RemotePlayerInfo> allPlayers = this.server.getRedisManager().getCache();
    for (RemotePlayerInfo info : allPlayers) {
      if (info.getUuid().equals(player.getUniqueId()) || info.getUsername().equalsIgnoreCase(player.getUsername())) {
        if (this.server.getConfiguration().isOnlineModeKickExistingPlayers()) {
          server.getRedisManager().send(new RedisKickPlayerRequest(player.getUniqueId(), server.getMultiProxyHandler().getOwnProxyId()));
        } else {
          player.setDontRemoveFromRedis(true);
          player.disconnect0(Component.translatable("velocity.error.already-connected-proxy.remote"), true);
          return false;
        }
      }
    }

    this.server.getMultiProxyHandler().handleJoin(createPlayerInfo(player));
    return true;
  }

  private RemotePlayerInfo createPlayerInfo(final ConnectedPlayer player) {
    Map<String, Integer> queuePriorities = new HashMap<>();

    for (RegisteredServer s : this.server.getAllServers()) {
      queuePriorities.put(s.getServerInfo().getName(), player.getQueuePriority(s.getServerInfo().getName()));
    }
    queuePriorities.put("all", player.getQueuePriority("all"));

    return new RemotePlayerInfo(
        this.config.getProxyId(), player.getUniqueId(), player.getUsername(),
        queuePriorities,
        server.getQueueManager().isQueueEnabled() && player.hasPermission("velocity.queue.full.bypass"),
        server.getQueueManager().isQueueEnabled() && player.hasPermission("velocity.queue.bypass")
    );
  }

  /**
   * Initiates the shutdown sequence for this proxy in the multi-proxy setup.
   * Sends a {@link RedisShuttingDownAnnouncement} message to notify other proxies that this proxy
   * instance is shutting down, ensuring they can update the status accordingly.
   */
  public void shutdown() {
    shuttingDown = true;

    if (this.server.getMultiProxyHandler().isRedisEnabled()) {
      this.server.getRedisManager().removeProxyId(this.config.getProxyId());
      this.server.getRedisManager().send(new RedisShuttingDownAnnouncement(this.config.getProxyId()));
    }
  }

  /**
   * Retrieves the total player counts across all connected proxies in the multi-proxy setup.
   *
   * @return the combined player count from this proxy and all other known proxies
   */
  public int getTotalPlayerCount() {
    return totalPlayerCount;
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
   * Returns the set of all proxy IDs known to this proxy.
   *
   * @return the list of all proxy IDs in lower case.
   */
  public List<String> getAllProxyIdsLowerCase() {
    List<String> ids = new ArrayList<>();
    for (String s : this.server.getRedisManager().getProxyIds()) {
      ids.add(s.toLowerCase());
    }

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
    return this.server.getRedisManager().getCache().stream()
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
    return this.server.getRedisManager().getCache();
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
      if (info.getUuid().equals(uuid)) {
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
      if (info.getName().equalsIgnoreCase(username)) {
        return info;
      }
    }
    return null;
  }

  /**
   * Checks if the player is still connected to any proxy on the network by UUID.
   *
   * @param uuid The UUID of the player.
   *
   * @return Whether the player is connected to any proxy or not.
   */
  public boolean isPlayerOnline(final UUID uuid) {
    return this.server.getRedisManager().getCache().stream().anyMatch(info -> info.getUuid().equals(uuid));
  }

  /**
   * Checks if the player is still connected to any proxy on the network by username.
   *
   * @param username The username of the player.
   *
   * @return Whether the player is connected to any proxy or not.
   */
  public boolean isPlayerOnline(final String username) {
    return this.server.getRedisManager().getCache().stream().noneMatch(info -> info.getUsername().equalsIgnoreCase(username));
  }

  /**
   * Alerts all servers on every proxy.
   *
   * @param component The component to broadcast on all servers.
   */
  public void alert(final Component component) {
    server.getRedisManager().send(new RedisServerAlertRequest(component));
  }
}
