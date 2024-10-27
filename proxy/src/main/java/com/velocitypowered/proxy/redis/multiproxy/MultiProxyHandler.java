package com.velocitypowered.proxy.redis.multiproxy;

import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.redis.RedisManagerImpl;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

  public static final class OtherProxy {
    public Instant lastSeenPing;
    public ProxyStatus status;
    public List<RemotePlayerInfo> players = new ArrayList<>();

    public OtherProxy() {
      this.lastSeenPing = Instant.now();
      this.status = ProxyStatus.Healthy;
    }
  }

  public static final class RemotePlayerInfo {
    public final UUID uuid;
    public final String name;
    public String serverName = null;

    public RemotePlayerInfo(UUID uuid, String name) {
      this.uuid = uuid;
      this.name = name;
    }

    public RemotePlayerInfo(PlayerJoinUpdate update) {
      this(update.uuid(), update.name());
    }
  }

  public enum ProxyStatus {
    Healthy,
    TimedOut,
    Shutdown,
  }

  public MultiProxyHandler(VelocityServer server) {
    this.server = server;
    this.config = this.server.getConfiguration().getRedis();

    if (!this.isEnabled()) {
      return;
    }

    RedisManagerImpl redisManager = this.server.getRedisManager();

    redisManager.listen(ProxyIDAnnouncement.ID, ProxyIDAnnouncement.class, it -> {
      if (it.wantsReply()) {
        // if the proxy who sent this wants a reply, broadcast our own back.
        redisManager.send(new ProxyIDAnnouncement(this.config.getProxyId(), false));
        this.lastPingSent = Instant.now();
      }

      this.handleAndGetProxyFromPacket(it.proxyId());
    });

    redisManager.listen(PlayerJoinUpdate.ID, PlayerJoinUpdate.class, it -> {
      OtherProxy proxy = this.handleAndGetProxyFromPacket(it.proxyId());

      if (proxy == null) {
        return;
      }

      proxy.players.add(new RemotePlayerInfo(it));
    });

    redisManager.listen(PlayerLeaveUpdate.ID, PlayerLeaveUpdate.class, it -> {
      OtherProxy proxy = this.handleAndGetProxyFromPacket(it.proxyId());

      if (proxy == null) {
        return;
      }

      proxy.players.removeIf(player -> player.uuid.equals(it.uuid()));
    });

    redisManager.listen(PlayerServerChange.ID, PlayerServerChange.class, it -> {
      OtherProxy proxy = this.handleAndGetProxyFromPacket(it.proxyId());

      if (proxy == null) {
        return;
      }

      for (RemotePlayerInfo player: proxy.players) {
        if (it.uuid().equals(player.uuid)) {
          player.serverName = it.server();
        }
      }
    });

    redisManager.listen(ShuttingDown.ID, ShuttingDown.class, it -> {
      OtherProxy proxy = this.handleAndGetProxyFromPacket(it.proxyId());

      if (proxy == null) {
        return;
      }

      proxy.status = ProxyStatus.Shutdown;
    });

    Thread tickThread = new Thread(this::tick);
    tickThread.setName("Velocity Multi-Proxy Tick Thread");
    tickThread.setDaemon(true);
    tickThread.start();

    // solicit the ID of all other proxies
    redisManager.send(new ProxyIDAnnouncement(this.config.getProxyId(), true));
    this.lastPingSent = Instant.now();
  }

  private OtherProxy handleAndGetProxyFromPacket(String proxyId) {
    if (proxyId.equals(this.config.getProxyId())) {
      return null;
    }

    OtherProxy proxy = this.seenProxies.computeIfAbsent(proxyId, $ -> new OtherProxy());
    proxy.lastSeenPing = Instant.now();
    proxy.status = ProxyStatus.Healthy;
    return proxy;
  }

  public boolean isEnabled() {
    VelocityConfiguration.Redis config = this.server.getConfiguration().getRedis();
    return config.isEnabled() && config.getProxyId() != null;
  }

  private void tick() {
    Instant now = Instant.now();
    RedisManagerImpl redisManager = this.server.getRedisManager();

    if (this.lastPingSent != null && this.lastPingSent.until(now, ChronoUnit.MILLIS) > this.config.getPingIntervalMs()) {
      redisManager.send(new ProxyIDAnnouncement(this.config.getProxyId(), false));
    }

    for (OtherProxy proxy : this.seenProxies.values()) {
      if (proxy.lastSeenPing.until(now, ChronoUnit.MILLIS) > this.config.getOtherProxyTimeoutMs()) {
        proxy.status = ProxyStatus.TimedOut;
      }
    }
  }

  public void onPlayerLeave(ConnectedPlayer player) {
    if (shuttingDown) {
      return;
    }

    this.server.getRedisManager().send(new PlayerLeaveUpdate(this.config.getProxyId(), player.getUniqueId()));
  }

  public void onPlayerJoin(ConnectedPlayer player) {
    if (shuttingDown) {
      return;
    }

    this.server.getRedisManager().send(new PlayerJoinUpdate(this.config.getProxyId(), player.getUniqueId(), player.getUsername()));
  }

  public void shutdown() {
    shuttingDown = true;
    this.server.getRedisManager().send(new ShuttingDown(this.config.getProxyId()));
  }

  public int getTotalPlayerCount() {
    int playerCount = this.server.getPlayerCount();

    for (OtherProxy proxy: this.seenProxies.values()) {
      playerCount += proxy.players.size();
    }

    return playerCount;
  }

  public Set<String> getAllProxyIds() {
    return this.seenProxies.keySet();
  }

  public List<RemotePlayerInfo> getPlayers(String proxyId) {
    OtherProxy proxy = this.seenProxies.get(proxyId);

    if (proxy == null) {
      return null;
    }

    return proxy.players;
  }
}
