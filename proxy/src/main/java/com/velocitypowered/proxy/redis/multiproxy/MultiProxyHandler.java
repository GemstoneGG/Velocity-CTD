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
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.builtin.VelocityCommand;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.redis.RedisManagerImpl;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements handling for setups with multiple proxies.
 */
public class MultiProxyHandler {
  private static final Logger logger = LoggerFactory.getLogger(MultiProxyHandler.class);

  private final VelocityServer server;
  private @MonotonicNonNull Instant lastPingSent = null;
  private final Map<String, OtherProxy> seenProxies = new ConcurrentHashMap<>();
  private final VelocityConfiguration.Redis config;
  private boolean shuttingDown = false;

  /**
   * Represents another proxy instance in a multi-proxy setup.
   * Tracks the last time a ping was received from the proxy, its status, and the list of
   * players connected to this proxy.
   */
  public static final class OtherProxy {
    public Instant lastSeenPing;
    public ProxyStatus status;
    public List<RemotePlayerInfo> players = new ArrayList<>();

    public OtherProxy() {
      this.lastSeenPing = Instant.now();
      this.status = ProxyStatus.HEALTHY;
    }
  }

  /**
   * Stores information about a remote player connected to a different proxy.
   * Used to track the player's UUID, name, and the server they are connected to
   * within the multi-proxy network.
   */
  public static final class RemotePlayerInfo {
    public final String proxyId;
    public final UUID uuid;
    public final String name;
    public String serverName = null;

    /**
     * Constructs a new {@code RemotePlayerInfo} with the specified UUID and name.
     *
     * @param uuid the player's unique identifier
     * @param name the player's name
     */
    public RemotePlayerInfo(final String proxyId, final UUID uuid, final String name) {
      this.proxyId = proxyId;
      this.uuid = uuid;
      this.name = name;
    }
  }

  /**
   * Enumeration representing the status of a proxy in a multi-proxy setup.
   * A proxy can be {@link #HEALTHY}, {@link #TIMED_OUT}, or {@link #SHUTDOWN}.
   */
  public enum ProxyStatus {
    HEALTHY,
    TIMED_OUT,
    SHUTDOWN,
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

    if (!this.isEnabled()) {
      return;
    }

    RedisManagerImpl redisManager = this.server.getRedisManager();

    redisManager.listen(RedisProxyIdAnnouncement.ID, RedisProxyIdAnnouncement.class, it -> {
      if (it.wantsReply()) {
        // if the proxy who sent this wants a reply, broadcast our own back.
        redisManager.send(new RedisProxyIdAnnouncement(this.config.getProxyId(), false, generateLocalPlayers()));
        this.lastPingSent = Instant.now();
      }

      OtherProxy proxy = this.handleAndGetProxyFromPacket(it.proxyId());

      if (proxy == null) {
        return;
      }

      if (it.players() != null) {
        proxy.players = new ArrayList<>(it.players());
      }
    });

    redisManager.listen(RedisPlayerJoinUpdate.ID, RedisPlayerJoinUpdate.class, it -> {
      OtherProxy proxy = this.handleAndGetProxyFromPacket(it.player().proxyId);

      if (proxy == null) {
        return;
      }

      this.handleRemoteJoin(proxy, it.player());
    });

    redisManager.listen(RedisPlayerLeaveUpdate.ID, RedisPlayerLeaveUpdate.class, it -> {
      OtherProxy proxy = this.handleAndGetProxyFromPacket(it.proxyId());

      if (proxy == null) {
        return;
      }

      proxy.players.removeIf(player -> player.uuid.equals(it.uuid()));
    });

    redisManager.listen(RedisPlayerServerChange.ID, RedisPlayerServerChange.class, it -> {
      OtherProxy proxy = this.handleAndGetProxyFromPacket(it.proxyId());

      if (proxy == null) {
        return;
      }

      for (RemotePlayerInfo player : proxy.players) {
        if (it.uuid().equals(player.uuid)) {
          player.serverName = it.server();
        }
      }
    });

    redisManager.listen(RedisShuttingDownAnnouncement.ID, RedisShuttingDownAnnouncement.class, it -> {
      OtherProxy proxy = this.handleAndGetProxyFromPacket(it.proxyId());

      if (proxy == null) {
        return;
      }

      proxy.status = ProxyStatus.SHUTDOWN;
    });

    redisManager.listen(RedisGenericReplyRequest.ID, RedisGenericReplyRequest.class, it -> {
      if (!it.targetProxy().equals(this.getOwnProxyId())) {
        return;
      }

      switch (it.type()) {
        case Reload -> {
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

        case Uptime -> sendMessage(it.source(), VelocityCommand.getUptimeComponent(server));
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
        player.spoofChatInput(it.message());

        if (it.message().startsWith("/")) {
          it.replySource().sendMessage(server, Component.translatable(
              "velocity.command.sudo.command-executed",
              NamedTextColor.GREEN,
              Component.text(player.getUsername()),
              Component.text(it.message())
          ));
        } else {
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

    Thread tickThread = new Thread(this::tick);
    tickThread.setName("Velocity Multi-Proxy Tick Thread");
    tickThread.setDaemon(true);
    tickThread.start();

    // solicit the ID of all other proxies
    redisManager.send(new RedisProxyIdAnnouncement(this.config.getProxyId(), true, List.of()));
    this.lastPingSent = Instant.now();
  }

  private List<RemotePlayerInfo> generateLocalPlayers() {
    Collection<Player> players = this.server.getAllPlayers();
    List<RemotePlayerInfo> playerInfos = new ArrayList<>(players.size());

    for (Player player : players) {
      RemotePlayerInfo playerInfo = new RemotePlayerInfo(this.getOwnProxyId(), player.getUniqueId(), player.getUsername());
      player.getCurrentServer()
          .ifPresent(serverConnection -> playerInfo.serverName = serverConnection.getServerInfo().getName());

      playerInfos.add(playerInfo);
    }

    return playerInfos;
  }

  private void handleRemoteJoin(OtherProxy proxy, RemotePlayerInfo player) {
    // This handles the edge case if a player joins two proxies at once, once the player info broadcast is received
    // we disconnect them from the local proxy.
    for (Player localPlayer : this.server.getAllPlayers()) {
      if (localPlayer.getUniqueId().equals(player.uuid)) {
        localPlayer.disconnect(Component.translatable("velocity.error.already-connected-proxy.remote"));
      }
    }

    proxy.players.add(player);
  }

  private OtherProxy handleAndGetProxyFromPacket(final String proxyId) {
    if (proxyId.equals(this.config.getProxyId())) {
      return null;
    }

    OtherProxy proxy = this.seenProxies.computeIfAbsent(proxyId, key -> new OtherProxy());
    proxy.lastSeenPing = Instant.now();
    proxy.status = ProxyStatus.HEALTHY;
    return proxy;
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
    VelocityConfiguration.Redis config = this.server.getConfiguration().getRedis();
    return config.isEnabled() && config.getProxyId() != null;
  }

  private void tick() {
    Instant now = Instant.now();
    RedisManagerImpl redisManager = this.server.getRedisManager();

    if (this.lastPingSent != null && this.lastPingSent.until(now, ChronoUnit.MILLIS) > this.config.getPingIntervalMs()) {
      redisManager.send(new RedisProxyIdAnnouncement(this.config.getProxyId(), false, null));
    }

    for (OtherProxy proxy : this.seenProxies.values()) {
      if (proxy.lastSeenPing.until(now, ChronoUnit.MILLIS) > this.config.getOtherProxyTimeoutMs()) {
        proxy.status = ProxyStatus.TIMED_OUT;
      }
    }
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

    this.server.getRedisManager().send(new RedisPlayerJoinUpdate(new RemotePlayerInfo(
        this.config.getProxyId(), player.getUniqueId(), player.getUsername())));
    return false;
  }

  /**
   * Initiates the shutdown sequence for this proxy in the multi-proxy setup.
   * Sends a {@link RedisShuttingDownAnnouncement} message to notify other proxies that this proxy
   * instance is shutting down, ensuring they can update the status accordingly.
   */
  public void shutdown() {
    shuttingDown = true;
    this.server.getRedisManager().send(new RedisShuttingDownAnnouncement(this.config.getProxyId()));
  }

  /**
   * Retrieves the total player counts across all connected proxies in the multi-proxy setup.
   *
   * @return the combined player count from this proxy and all other known proxies
   */
  public int getTotalPlayerCount() {
    int playerCount = this.server.getPlayerCount();

    for (OtherProxy proxy : this.seenProxies.values()) {
      playerCount += proxy.players.size();
    }

    return playerCount;
  }

  /**
   * Returns the set of all proxy IDs known to this proxy.
   *
   * @return the set of all proxy IDs
   */
  public Set<String> getAllProxyIds() {
    Set<String> foreignProxies = new HashSet<>(this.seenProxies.keySet());

    if (this.config.getProxyId() != null) {
      foreignProxies.add(this.config.getProxyId());
    }

    return foreignProxies;
  }

  /**
   * Retrieves a list of players connected to a specified proxy in the multi-proxy setup.
   *
   * @param proxyId the ID of the proxy to retrieve players from
   * @return a {@link List} of {@link RemotePlayerInfo} representing the players on the specified proxy,
   *         or {@code null} if the proxy ID is unknown
   */
  public List<RemotePlayerInfo> getPlayers(final String proxyId) {
    if (proxyId.equals(this.getOwnProxyId())) {
      return generateLocalPlayers();
    }

    OtherProxy proxy = this.seenProxies.get(proxyId);

    if (proxy == null) {
      return null;
    }

    return new ArrayList<>(proxy.players);
  }

  /**
   * Requests a remote proxy reloads its config (as if executing {@code /velocity reload}).
   *
   * @param proxyId the ID of the remote proxy
   * @param source the source to send replies back to
   */
  public void requestReload(String proxyId, @Nullable CommandSource source) {
    this.server.getRedisManager().send(new RedisGenericReplyRequest(
        RedisGenericReplyRequest.Type.Reload, proxyId, EncodedCommandSource.from(source, this.getOwnProxyId())));
  }

  /**
   * Requests a remote proxy sends an uptime report (as if executing {@code /velocity uptime}).
   *
   * @param proxyId the ID of the remote proxy
   * @param source the source to send replies back to
   */
  public void requestUptime(String proxyId, CommandSource source) {
    this.server.getRedisManager().send(new RedisGenericReplyRequest(
        RedisGenericReplyRequest.Type.Uptime, proxyId, EncodedCommandSource.from(source, this.getOwnProxyId())));
  }

  /**
   * Requests a remote proxy reloads its config (as if executing {@code /velocity reload}).
   *
   * @param target the target of the message
   * @param component the message to send
   */
  public void sendMessage(EncodedCommandSource target, Component component) {
    this.server.getRedisManager().send(new RedisSendMessage(target, component));
  }

  /**
   * Returns the list of all known remote players.
   *
   * @return the list of all known remote players
   */
  public List<RemotePlayerInfo> getAllPlayers() {
    List<RemotePlayerInfo> players = new ArrayList<>();

    for (String proxyId : getAllProxyIds()) {
      players.addAll(getPlayers(proxyId));
    }

    return players;
  }

  /**
   * Runs a command on a remote player.
   *
   * @param player the target player
   * @param source where to send feedback to
   */
  public void sudo(RemotePlayerInfo player, CommandSource source, String message) {
    this.server.getRedisManager().send(new RedisSudo(player.proxyId, player.uuid, EncodedCommandSource.from(source, this.getOwnProxyId()), message));
  }
}
