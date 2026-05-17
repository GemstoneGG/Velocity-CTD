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

package com.velocitypowered.proxy;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.velocityctd.proxy.cluster.VelocityClusterPlayerService;
import com.velocityctd.proxy.cluster.VelocityClusterProxyService;
import com.velocityctd.proxy.cluster.local.LocalClusterPlayerService;
import com.velocityctd.proxy.cluster.local.LocalClusterProxyService;
import com.velocityctd.proxy.cluster.redis.RedisClusterPlayerService;
import com.velocityctd.proxy.cluster.redis.RedisClusterProxyService;
import com.velocityctd.proxy.command.builtin.AlertCommand;
import com.velocityctd.proxy.command.builtin.AlertRawCommand;
import com.velocityctd.proxy.command.builtin.FindCommand;
import com.velocityctd.proxy.command.builtin.GipCommand;
import com.velocityctd.proxy.command.builtin.GkickCommand;
import com.velocityctd.proxy.command.builtin.HubCommand;
import com.velocityctd.proxy.command.builtin.LeaveQueueCommand;
import com.velocityctd.proxy.command.builtin.PingCommand;
import com.velocityctd.proxy.command.builtin.PlistCommand;
import com.velocityctd.proxy.command.builtin.ProxyAliasCommand;
import com.velocityctd.proxy.command.builtin.QueueAdminCommand;
import com.velocityctd.proxy.command.builtin.SlashServerCommand;
import com.velocityctd.proxy.command.builtin.TransferCommand;
import com.velocityctd.proxy.queue.RedisVelocityQueueManager;
import com.velocityctd.proxy.queue.VelocityQueueManager;
import com.velocityctd.proxy.redis.VelocityRedis;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyPreShutdownEvent;
import com.velocitypowered.api.event.proxy.ProxyReloadEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.network.ProtocolState;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.plugin.PluginManager;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.config.BackendServerConfig;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.util.Favicon;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.api.util.ProxyVersion;
import com.velocitypowered.api.util.ServerLink;
import com.velocitypowered.proxy.adventure.ClickCallbackManager;
import com.velocitypowered.proxy.command.VelocityCommandManager;
import com.velocitypowered.proxy.command.builtin.BuiltinCommandDefinition;
import com.velocitypowered.proxy.command.builtin.CallbackCommand;
import com.velocitypowered.proxy.command.builtin.GlistCommand;
import com.velocitypowered.proxy.command.builtin.SendCommand;
import com.velocitypowered.proxy.command.builtin.ServerCommand;
import com.velocitypowered.proxy.command.builtin.ShutdownCommand;
import com.velocitypowered.proxy.command.builtin.VelocityCommand;
import com.velocitypowered.proxy.config.DynamicProxyFilterMode;
import com.velocitypowered.proxy.config.ProxyAddress;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.connection.client.PlayerRegistry;
import com.velocitypowered.proxy.connection.player.resourcepack.VelocityResourcePackInfo;
import com.velocitypowered.proxy.connection.util.ServerListPingHandler;
import com.velocitypowered.proxy.console.VelocityConsole;
import com.velocitypowered.proxy.crypto.EncryptionUtils;
import com.velocitypowered.proxy.event.VelocityEventManager;
import com.velocitypowered.proxy.network.ConnectionManager;
import com.velocitypowered.proxy.plugin.VelocityPluginManager;
import com.velocitypowered.proxy.plugin.loader.VelocityPluginContainer;
import com.velocitypowered.proxy.plugin.loader.VelocityPluginDescription;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.util.FaviconSerializer;
import com.velocitypowered.proxy.protocol.util.GameProfileSerializer;
import com.velocitypowered.proxy.scheduler.VelocityScheduler;
import com.velocitypowered.proxy.server.ServerMap;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import com.velocitypowered.proxy.util.AddressUtil;
import com.velocitypowered.proxy.util.VelocityChannelRegistrar;
import com.velocitypowered.proxy.util.ratelimit.Ratelimiter;
import com.velocitypowered.proxy.util.ratelimit.Ratelimiters;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bstats.MetricsBase;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;

/**
 * Implementation of {@link ProxyServer}.
 */
@SuppressWarnings({"unchecked"})
public class VelocityServer implements ProxyServer, ForwardingAudience {

  public static final String VELOCITY_URL = "https://github.com/GemstoneGG/Velocity-CTD";
  public static final String DISCORD_URL = "https://discord.gg/beer";

  private static final Logger LOGGER = LogManager.getLogger(VelocityServer.class);

  private static final int PRE_SHUTDOWN_TIMEOUT = Integer.getInteger("velocity.pre-shutdown-timeout", 10);

  public static final Gson GENERAL_GSON = new GsonBuilder()
      .registerTypeHierarchyAdapter(Favicon.class, FaviconSerializer.INSTANCE)
      .registerTypeHierarchyAdapter(GameProfile.class, GameProfileSerializer.INSTANCE)
      .create();

  private static final Gson PRE_1_16_PING_SERIALIZER = new GsonBuilder()
      .registerTypeHierarchyAdapter(
          Component.class,
          ProtocolUtils.getJsonChatSerializer(ProtocolVersion.MINECRAFT_1_15_2)
              .serializer().getAdapter(Component.class)
      )
      .registerTypeHierarchyAdapter(Favicon.class, FaviconSerializer.INSTANCE)
      .create();

  private static final Gson PRE_1_20_3_PING_SERIALIZER = new GsonBuilder()
      .registerTypeHierarchyAdapter(
          Component.class,
          ProtocolUtils.getJsonChatSerializer(ProtocolVersion.MINECRAFT_1_20_2)
              .serializer().getAdapter(Component.class)
      )
      .registerTypeHierarchyAdapter(Favicon.class, FaviconSerializer.INSTANCE)
      .create();

  private static final Gson MODERN_PING_SERIALIZER = new GsonBuilder()
      .registerTypeHierarchyAdapter(
          Component.class,
          ProtocolUtils.getJsonChatSerializer(ProtocolVersion.MINECRAFT_1_20_3)
              .serializer().getAdapter(Component.class)
      )
      .registerTypeHierarchyAdapter(Favicon.class, FaviconSerializer.INSTANCE)
      .create();

  private final ConnectionManager cm;

  private final ProxyOptions options;

  private @MonotonicNonNull VelocityConfiguration configuration;

  private @MonotonicNonNull KeyPair serverKeyPair;

  private final ServerMap servers;

  private final VelocityCommandManager commandManager;

  private final AtomicBoolean shutdownInProgress = new AtomicBoolean(false);

  private boolean shutdown = false;

  private final VelocityPluginManager pluginManager;

  private final PlayerRegistry playerRegistry = new PlayerRegistry(this);

  /**
   * Holds a set of all registered BuiltinCommand instances. Used for unregistering these commands later.
   */
  private final Set<BuiltinCommandDefinition> registeredBuiltinCommands = new HashSet<>();

  private final VelocityConsole console;

  private @MonotonicNonNull Ratelimiter<InetAddress> ipAttemptLimiter;

  private @MonotonicNonNull Ratelimiter<UUID> commandRateLimiter;

  private @MonotonicNonNull Ratelimiter<UUID> tabCompleteRateLimiter;

  private final VelocityEventManager eventManager;

  private final VelocityScheduler scheduler;

  private final VelocityChannelRegistrar channelRegistrar = new VelocityChannelRegistrar();

  private final ServerListPingHandler serverListPingHandler;

  /**
   * The system timestamp (in milliseconds) when the proxy started.
   */
  private final long startTime;

  /**
   * The {@link TranslationRegistryManager} instance that manages (registers and unregisters)
   * Velocity's Adventure translations.
   */
  private final TranslationRegistryManager translationRegistryManager = new TranslationRegistryManager();

  /**
   * Coordinates server queues and handles queue assignment logic.
   */
  private @Nullable VelocityQueueManager queueManager;

  /**
   * Provides access to the Redis integration used for multi-proxy features such
   * as queues, dynamic proxy discovery, and global player tracking.
   */
  private @MonotonicNonNull VelocityRedis redis;

  /**
   * The cluster player service for tracking and querying players across the cluster.
   */
  private @MonotonicNonNull VelocityClusterPlayerService clusterPlayerService;

  /**
   * The cluster proxy service for proxy discovery.
   */
  private @MonotonicNonNull VelocityClusterProxyService clusterProxyService;

  VelocityServer(ProxyOptions options) {
    pluginManager = new VelocityPluginManager(this);
    eventManager = new VelocityEventManager(pluginManager);
    commandManager = new VelocityCommandManager(eventManager, pluginManager);
    scheduler = new VelocityScheduler(pluginManager);
    console = new VelocityConsole(this);
    cm = new ConnectionManager(this);
    servers = new ServerMap(this);
    startTime = System.currentTimeMillis();
    serverListPingHandler = new ServerListPingHandler(this);
    this.options = options;
  }

  public KeyPair getServerKeyPair() {
    return serverKeyPair;
  }

  /**
   * Returns the queue manager currently in use.
   * Will throw when the queue is not enabled. Please check this beforehand with {@link #isQueueEnabled()}.
   *
   * @return the {@link VelocityQueueManager}, or throws {@link IllegalStateException} if not initialized
   */
  @Override
  public VelocityQueueManager getQueueManager() {
    if (queueManager == null) {
      throw new IllegalStateException("Queue is not enabled.");
    }

    return queueManager;
  }

  /**
   * Returns the {@link VelocityRedis} instance for interacting with Redis features.
   *
   * @return the {@link VelocityRedis} instance
   */
  public VelocityRedis getRedis() {
    return redis;
  }

  @Override
  public final VelocityConfiguration getConfiguration() {
    return this.configuration;
  }

  /**
   * Gets the system timestamp (in milliseconds) when the proxy started.
   *
   * @return the proxy startup time
   */
  public long getStartTime() {
    return startTime;
  }

  @Override
  public ProxyVersion getVersion() {
    Package pkg = VelocityServer.class.getPackage();
    String implName = Optional.ofNullable(pkg)
        .map(Package::getImplementationTitle)
        .orElse("Velocity-CTD");
    String implVersion = Optional.ofNullable(pkg)
        .map(Package::getImplementationVersion)
        .orElse("<unknown>");
    String implVendor = Optional.ofNullable(pkg)
        .map(Package::getImplementationVendor)
        .orElse("Velocity(-CTD) Contributors");

    return new ProxyVersion(implName, implVendor, implVersion);
  }

  private VelocityPluginContainer createVirtualPlugin() {
    ProxyVersion version = getVersion();
    PluginDescription description = new VelocityPluginDescription(
        "velocityctd", version.getName(), version.getVersion(), "The Velocity-CTD proxy",
            (version.getName().equals("Velocity") || version.getName().equals("Velocity-CTD")) ? VELOCITY_URL : null,
            ImmutableList.of(version.getVendor()), Collections.emptyList(), null);
    VelocityPluginContainer container = new VelocityPluginContainer(description);
    container.setInstance(VelocityVirtualPlugin.INSTANCE);
    return container;
  }

  @Override
  public VelocityCommandManager getCommandManager() {
    return commandManager;
  }

  void awaitProxyShutdown() {
    cm.getBossGroup().terminationFuture().syncUninterruptibly();
  }

  /**
   * Starts the Velocity proxy, initializing all required systems including networking,
   * plugin management, configuration loading, and event dispatching.
   *
   * <p>This method should be called exactly once during proxy bootstrap. It prepares the proxy
   * to begin accepting player connections and enables plugin interactions.</p>
   *
   * <p>This method ensures that all critical fields (such as {@code serverKeyPair}, {@code scheduler},
   * {@code cm}, and {@code configuration}) are initialized before completing.</p>
   *
   * @throws RuntimeException if startup configuration is invalid or plugin loading fails
   */
  @EnsuresNonNull({"serverKeyPair", "servers", "pluginManager", "eventManager", "scheduler",
      "console", "cm", "configuration"})
  void start() {
    LOGGER.info("Booting up {} {}...", getVersion().getName(), getVersion().getVersion());
    console.setupStreams();
    pluginManager.registerPlugin(this.createVirtualPlugin());

    // Yes, you're reading that correctly. We're generating a 1024-bit RSA keypair. Sounds
    // dangerous, right? We're well within the realm of factoring such a key...
    //
    // You can blame Mojang. For the record, we also don't consider the Minecraft protocol
    // encryption scheme to be secure, and it has reached the point where any serious cryptographic
    // protocol needs a refresh. There are multiple obvious weaknesses, and this is far from the
    // most serious.
    //
    // If you are using Minecraft in a security-sensitive application, *I don't know what to say.*
    serverKeyPair = EncryptionUtils.createRsaKeyPair(1024);

    cm.logChannelInformation();

    this.doStartupConfigLoad();

    if (getConfiguration().getRedis().getProxyId() == null && getConfiguration().getRedis().isEnabled()) {
      throw new IllegalArgumentException("'proxy-id' cannot be null when redis is enabled!");
    }

    if ((getConfiguration().getQueue().getMasterProxyIds() == null
        || getConfiguration().getQueue().getMasterProxyIds().isEmpty()) && getConfiguration().getQueue().isEnabled()) {
      throw new IllegalArgumentException("'master-proxy-ids' cannot be empty when queues is enabled!");
    }

    for (ServerInfo cliServer : options.getServers()) {
      servers.register(cliServer);
    }

    if (!options.isIgnoreConfigServers()) {
      for (Map.Entry<String, BackendServerConfig> entry : configuration.getBackendServers().entrySet()) {
        servers.register(new ServerInfo(entry.getKey(), AddressUtil.parseAddress(entry.getValue().address()), entry.getValue().forwardingMode()));
      }
    }

    if (configuration.getRedis().isEnabled()) {
      redis = new VelocityRedis(this);

      clusterPlayerService = new RedisClusterPlayerService(this, redis);
      clusterProxyService = new RedisClusterProxyService(redis);

      if (configuration.getQueue().isEnabled()) {
        queueManager = new RedisVelocityQueueManager(this);
      }
    } else {
      clusterPlayerService = new LocalClusterPlayerService(this);
      clusterProxyService = new LocalClusterProxyService(this);

      if (configuration.getQueue().isEnabled()) {
        queueManager = new VelocityQueueManager(this);
      }
    }

    registerCommands();

    // Re-send the available commands to all online players once a click-callback has been registered.
    // Vanilla Velocity does not register any click-callbacks, only plugins may do so via the Adventure API.
    // If no plugins are making use of this feature, we can omit the /velocity:callback (ClickCallbackManager#COMMAND_LABEL)
    // from the available commands, as it only adds clutter to command completion suggestions.
    // ConnectedPlayer#sendAvailableCommands will include this callback command in the command set if a click-listener
    // has been registered at least once.
    ClickCallbackManager.INSTANCE.setOnFirstRegistration(() -> {
      for (ConnectedPlayer player : getAllPlayers()) {
        if (player.getConnection().getState() == StateRegistry.PLAY) {
          player.sendAvailableCommands();
        }
      }
    });

    LOGGER.info("Loading localizations...");
    translationRegistryManager.registerTranslations();

    ipAttemptLimiter = Ratelimiters.createWithMilliseconds(configuration.getLoginRatelimit());
    commandRateLimiter = Ratelimiters.createWithMilliseconds(configuration.getCommandRatelimit());
    tabCompleteRateLimiter = Ratelimiters.createWithMilliseconds(configuration.getTabCompleteRatelimit());
    loadPlugins();

    // Go ahead and fire the proxy initialization event. We block since plugins should have a chance
    // to fully initialize before we accept any connections to the server.
    eventManager.fire(new ProxyInitializeEvent()).join();

    // init console permissions after plugins are loaded
    console.setupPermissions();

    Integer port = this.options.getPort();
    if (port != null) {
      LOGGER.debug("Overriding bind port to {} from command line option", port);
      this.cm.bind(new InetSocketAddress(configuration.getBind().getHostString(), port));
    } else {
      this.cm.bind(configuration.getBind());
    }

    Boolean haproxy = this.options.isHaproxy();
    if (haproxy != null) {
      LOGGER.debug("Overriding HAProxy protocol to {} from command line option", haproxy);
      configuration.setProxyProtocol(haproxy);
    }

    if (configuration.isQueryEnabled()) {
      this.cm.queryBind(configuration.getBind().getHostString(), configuration.getQueryPort());
    }

    String defaultPackage = new String(new byte[] {'o', 'r', 'g', '.', 'b', 's', 't', 'a', 't', 's' });
    if (!MetricsBase.class.getPackage().getName().startsWith(defaultPackage)) {
      Metrics.VelocityMetrics.startMetrics(this, configuration.getMetrics());
    } else {
      LOGGER.warn("debug environment, metrics is disabled!");
    }
  }

  @SuppressFBWarnings("DM_EXIT")
  private void doStartupConfigLoad() {
    try {
      Path configPath = Path.of("velocity.toml");
      configuration = VelocityConfiguration.read(configPath);

      if (!configuration.validate()) {
        LOGGER.error("Your configuration is invalid. Velocity will not start up until the errors "
            + "are resolved.");
        LogManager.shutdown();
        System.exit(1);
      }

      commandManager.setAnnounceProxyCommands(configuration.isAnnounceProxyCommands());
    } catch (Exception e) {
      LOGGER.error("Unable to read/load/save your velocity.toml. The server will shut down.", e);
      LogManager.shutdown();
      System.exit(1);
    }
  }

  private void loadPlugins() {
    LOGGER.info("Loading plugins...");

    try {
      Path pluginPath = Path.of("plugins");
      ArrayList<Path> additionalPlugins = new ArrayList<>();

      if (!pluginPath.toFile().exists()) {
        Files.createDirectory(pluginPath);
      } else {
        if (!pluginPath.toFile().isDirectory()) {
          LOGGER.warn("Plugin location {} is not a directory, continuing without loading plugins",
              pluginPath);
          return;
        }
      }

      for (String additionalPluginPath : options.getAdditionalPlugins()) {
        Path path = Path.of(additionalPluginPath);
        if (!Files.exists(path)) {
          LOGGER.warn("Unable to find plugin file by path {}", additionalPluginPath);
          continue;
        }

        if (!path.toFile().isFile()) {
          LOGGER.warn("Plugin {} is not a file", additionalPluginPath);
          continue;
        }

        additionalPlugins.add(path);
      }

      pluginManager.loadPlugins(pluginPath, additionalPlugins);
    } catch (Exception e) {
      LOGGER.error("Couldn't load plugins", e);
    }

    // Register the plugin main classes so that we can fire the proxy initialize event
    for (PluginContainer plugin : pluginManager.getPlugins()) {
      Optional<?> instance = plugin.getInstance();
      if (instance.isPresent()) {
        try {
          eventManager.registerInternally(plugin, instance.get());
        } catch (Exception e) {
          LOGGER.error("Unable to register plugin listener for {}",
              plugin.getDescription().getName().orElse(plugin.getDescription().getId()), e);
        }
      }
    }

    LOGGER.info("Loaded {} plugins", pluginManager.getPlugins().size());
  }

  public Bootstrap createBootstrap(@Nullable EventLoopGroup group) {
    return this.cm.createWorker(group);
  }

  public ChannelInitializer<Channel> getBackendChannelInitializer() {
    return this.cm.backendChannelInitializer.get();
  }

  public ServerListPingHandler getServerListPingHandler() {
    return serverListPingHandler;
  }

  public boolean isShutdown() {
    return shutdown;
  }

  /**
   * Reloads the proxy's configuration.
   *
   * @return {@code true} if successful, {@code false} if we can't read the configuration
   * @throws IOException if we can't read {@code velocity.toml}
   */
  public boolean reloadConfiguration() throws IOException {
    Path configPath = Path.of("velocity.toml");
    VelocityConfiguration newConfiguration = VelocityConfiguration.read(configPath);

    if (!newConfiguration.validate()) {
      return false;
    }

    unregisterCommands();

    this.configuration = newConfiguration;

    reloadServerList();

    registerCommands();

    translationRegistryManager.unregisterTranslations();

    translationRegistryManager.registerTranslations();

    // Re-register servers. If a server is being replaced, make sure to note what players need to
    // move back to a fallback server.
    Collection<ConnectedPlayer> evacuate = new ArrayList<>();
    for (Map.Entry<String, BackendServerConfig> entry : newConfiguration.getBackendServers().entrySet()) {
      ServerInfo newInfo = new ServerInfo(entry.getKey(), AddressUtil.parseAddress(entry.getValue().address()), entry.getValue().forwardingMode());
      Optional<VelocityRegisteredServer> rs = servers.getServer(entry.getKey());
      if (rs.isEmpty()) {
        servers.register(newInfo);
      } else if (!rs.get().getServerInfo().equals(newInfo)) {
        evacuate.addAll(rs.get().getPlayersConnected());

        servers.unregister(rs.get().getServerInfo());
        servers.register(newInfo);
      }
    }

    // If we had any players to evacuate, let's move them now. Wait until they are all moved off.
    if (!evacuate.isEmpty()) {
      CountDownLatch latch = new CountDownLatch(evacuate.size());
      for (ConnectedPlayer player : evacuate) {
        Optional<VelocityRegisteredServer> next = player.currentServerRetrySession().getNextServerToTry();
        if (next.isPresent()) {
          player.createConnectionRequest(next.get()).connectWithIndication()
              .whenComplete((success, ex) -> {
                if (ex != null || success == null || !success) {
                  player.disconnect(Component.text("Your server has been changed, but we could "
                      + "not move you to any fallback servers."));
                }
                latch.countDown();
              });
        } else {
          latch.countDown();
          player.disconnect(Component.text("Your server has been changed, but we could "
              + "not move you to any fallback servers."));
        }
      }
      try {
        latch.await();
      } catch (InterruptedException e) {
        LOGGER.error("Interrupted whilst moving players", e);
        Thread.currentThread().interrupt();
      }
    }

    // If we have a new bind address, bind to it
    if (!configuration.getBind().equals(newConfiguration.getBind())) {
      this.cm.bind(newConfiguration.getBind());
      this.cm.close(configuration.getBind());
    }

    boolean queryPortChanged = newConfiguration.getQueryPort() != configuration.getQueryPort();
    boolean queryAlreadyEnabled = configuration.isQueryEnabled();
    boolean queryEnabled = newConfiguration.isQueryEnabled();
    if (queryAlreadyEnabled && (!queryEnabled || queryPortChanged)) {
      this.cm.close(new InetSocketAddress(
          configuration.getBind().getHostString(), configuration.getQueryPort()));
    }
    if (queryEnabled && (!queryAlreadyEnabled || queryPortChanged)) {
      this.cm.queryBind(newConfiguration.getBind().getHostString(),
          newConfiguration.getQueryPort());
    }

    commandManager.setAnnounceProxyCommands(newConfiguration.isAnnounceProxyCommands());
    ipAttemptLimiter = Ratelimiters.createWithMilliseconds(newConfiguration.getLoginRatelimit());
    this.configuration = newConfiguration;
    eventManager.fireAndForget(new ProxyReloadEvent());

    if (queueManager != null) {
      queueManager.reload();
    }

    if (!this.getConfiguration().getServerLinks().isEmpty()) {
      for (ConnectedPlayer player : this.getOnlinePlayers()) {
        if (player.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_21)) {
          try {
            if (player.getProtocolState() == ProtocolState.CONFIGURATION || player.getProtocolState() == ProtocolState.PLAY) {
              String serverName = player.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse("");
              List<ServerLink> scopedLinks = getConfiguration().getServerLinksFor(serverName);
              player.setServerLinks(scopedLinks);
            }
          } catch (IllegalStateException ignored) {
            // Ignore illegal state to ensure each viable reload is successful.
          }
        }
      }
    }

    return true;
  }

  private void unregisterCommands() {
    for (BuiltinCommandDefinition command : registeredBuiltinCommands) {
      unregisterCommand(command.label());
    }
    registeredBuiltinCommands.clear();

    for (String alias : configuration.getProxyCommandAliases().keySet()) {
      unregisterCommand(alias);
    }
  }

  private void unregisterCommand(String command) {
    CommandMeta meta = commandManager.getCommandMeta(command);
    if (meta != null) {
      if (meta.getPlugin() == VelocityVirtualPlugin.INSTANCE) {
        commandManager.unregister(meta);
      } else {
        LOGGER.debug("Could not unregister command /{}, command not registered by Velocity.", command);
      }
    }
  }

  private void registerCommands() {
    registerCommand(VelocityCommand::new);
    registerCommand(CallbackCommand::new);
    registerCommand(ShutdownCommand::new);
    registerCommand(configuration.isAlertEnabled(), AlertCommand::new);
    registerCommand(configuration.isAlertRawEnabled(), AlertRawCommand::new);
    registerCommand(configuration.isFindEnabled(), FindCommand::new);
    registerCommand(configuration.isGkickEnabled(), GkickCommand::new);
    registerCommand(configuration.isGipEnabled(), GipCommand::new);
    registerCommand(configuration.isTransferEnabled(), TransferCommand::new);
    registerCommand(configuration.isGlistEnabled(), GlistCommand::new);
    registerCommand(configuration.isPlistEnabled(), PlistCommand::new);
    registerCommand(configuration.isPingEnabled(), PingCommand::new);
    registerCommand(configuration.isSendEnabled(), SendCommand::new);
    registerCommand(configuration.isHubEnabled(), HubCommand::new);
    registerCommand(configuration.getQueue().isEnabled(), QueueAdminCommand::new);
    registerCommand(configuration.getQueue().isEnabled(), LeaveQueueCommand::new);
    registerCommand(configuration.isServerEnabled(), ServerCommand::new);

    // /<server_name> commands
    for (Map.Entry<String, List<String>> entry : configuration.getSlashServers().entrySet()) {
      String serverName = entry.getKey();
      List<String> commandLabels = entry.getValue();

      for (String commandLabel : commandLabels) {
        registerCommand(SlashServerCommand.factory(serverName, commandLabel));
      }
    }

    // Proxy command aliases
    for (Map.Entry<String, List<String>> entry : configuration.getProxyCommandAliases().entrySet()) {
      String alias = entry.getKey();
      List<String> commands = entry.getValue();

      if (commandManager.hasCommand(alias)) {
        LOGGER.warn("Proxy command alias '{}' conflicts with existing command, skipping", alias);
        continue;
      }

      ProxyAliasCommand proxyAliasCommand = new ProxyAliasCommand(this, alias, commands);
      commandManager.register(
          commandManager.metaBuilder(alias)
              .plugin(VelocityVirtualPlugin.INSTANCE)
              .build(),
          proxyAliasCommand
      );
    }
  }

  private void registerCommand(Function<VelocityServer, ? extends BuiltinCommandDefinition> commandConstructor) {
    BuiltinCommandDefinition command = commandConstructor.apply(this);
    if (commandManager.hasCommand(command.label())) {
      LOGGER.debug("Not registering built-in command /{}, command already exists.", command.label());
      return;
    }

    BrigadierCommand brigadierCommand = command.build();
    if (brigadierCommand == null) {
      LOGGER.debug("Not registering built-in command /{}, returned null.", command.label());
      return;
    }

    if (!brigadierCommand.getNode().getName().equals(command.label())) {
      throw new IllegalStateException("BuiltinCommand#label and BrigadierCommand node name mismatch.");
    }

    String[] aliases = findAliases(command);

    commandManager.register(
            commandManager.metaBuilder(brigadierCommand)
                    .aliases(aliases)
                    .plugin(VelocityVirtualPlugin.INSTANCE)
                    .build(),
            brigadierCommand
    );

    registeredBuiltinCommands.add(command);

    LOGGER.debug("Registered built-in command /{}", command.label());
  }

  private void registerCommand(boolean condition, Function<VelocityServer, ? extends BuiltinCommandDefinition> commandConstructor) {
    if (condition) {
      registerCommand(commandConstructor);
    }
  }

  private String[] findAliases(BuiltinCommandDefinition command) {
    List<String> aliases = new ArrayList<>(command.aliases());

    List<String> configuredAliases = configuration.getCommandAliases().get(command.label());
    if (configuredAliases != null) {
      aliases.addAll(configuredAliases);
    }

    return aliases.toArray(String[]::new);
  }

  /**
   * Reloads the list of servers based on the updated configuration.
   *
   * <p>This is exclusively implemented within VelocityServer as it
   * is not a function necessary and present for generic purposes
   * within ServerCommand and is exclusive to reload's functionality.</p>
   */
  public void reloadServerList() {
    VelocityConfiguration config = getConfiguration();
    List<ServerInfo> newConfigServers = loadServersFromNewList(config);

    getAllServers().forEach(server -> {
      if (!newConfigServers.contains(server.getServerInfo())) {
        unregisterServer(server.getServerInfo());
      }
    });

    newConfigServers.forEach(serverInfo -> {
      if (getServer(serverInfo.getName()).isEmpty()) {
        registerServer(serverInfo);
      }
    });
  }

  /**
   * Loads servers from the [servers] section of the configuration.
   *
   * @param config the Velocity configuration
   * @return list of configured ServerInfo objects
   */
  private static List<ServerInfo> loadServersFromNewList(VelocityConfiguration config) {
    List<ServerInfo> serverList = new ArrayList<>();

    config.getBackendServers().forEach((serverName, backendConfig) -> {
      InetSocketAddress socketAddress = AddressUtil.parseAddress(backendConfig.address());
      serverList.add(new ServerInfo(serverName, socketAddress, backendConfig.forwardingMode()));
    });

    return serverList;
  }

  /**
   * Shuts down the proxy, kicking players with the specified reason.
   *
   * @param explicitExit whether the user explicitly shut down the proxy
   * @param reason       message to kick online players with
   */
  public void shutdown(boolean explicitExit, Component reason) {
    if (eventManager == null || pluginManager == null || cm == null || scheduler == null) {
      throw new AssertionError();
    }

    if (!shutdownInProgress.compareAndSet(false, true)) {
      return;
    }

    Runnable shutdownProcess = () -> {
      LOGGER.info("Shutting down the proxy...");

      // Shutdown the connection manager, this should be
      // done first to refuse new connections
      cm.shutdown();

      try {
        eventManager.fire(new ProxyPreShutdownEvent())
            .toCompletableFuture()
            .get(PRE_SHUTDOWN_TIMEOUT, TimeUnit.SECONDS);
      } catch (TimeoutException ignored) {
        LOGGER.warn("Your plugins took over {} seconds during pre shutdown.", PRE_SHUTDOWN_TIMEOUT);
      } catch (ExecutionException ee) {
        LOGGER.error("Exception in ProxyPreShutdownEvent handler; continuing shutdown.", ee);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
        LOGGER.warn("Interrupted while waiting for ProxyPreShutdownEvent; continuing shutdown.");
      }

      ImmutableList<@NotNull ConnectedPlayer> players = ImmutableList.copyOf(playerRegistry.getPlayers());
      ImmutableList<@NotNull UUID> playerUuids = players.stream()
          .map(ConnectedPlayer::getUniqueId)
          .collect(ImmutableList.toImmutableList());

      if (!getConfiguration().isAcceptTransfers()) {
        for (ConnectedPlayer player : players) {
          player.disconnect(reason);
        }
      } else {
        ProxyAddress chosen = getProxyAddressToUse();
        if (chosen == null) {
          for (ConnectedPlayer player : players) {
            player.disconnect(reason);
          }
        } else {
          try {
            LOGGER.log(Level.INFO, "Transferring all players to new proxy...");
            Thread.sleep(1000);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }

          for (ConnectedPlayer player : players) {
            if (player.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
              player.transferToHost(new InetSocketAddress(chosen.ip(), chosen.port()));
            } else {
              player.disconnect(reason);
            }
          }
        }
      }

      try {
        boolean timedOut = false;

        try {
          // Wait for the connections finish tearing down, this
          // makes sure that all the disconnect events are being fired

          CompletableFuture<Void> playersTeardownFuture = CompletableFuture.allOf(players.stream()
                  .map(ConnectedPlayer::getTeardownFuture)
                  .toArray(CompletableFuture[]::new));

          playersTeardownFuture.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
          timedOut = true;
        } catch (ExecutionException e) {
          timedOut = true;
          LOGGER.error("Exception while tearing down player connections", e);
        }

        eventManager.fire(new ProxyShutdownEvent()).join();

        timedOut = !scheduler.shutdown() || timedOut;

        if (timedOut) {
          LOGGER.error("Your plugins took over 10 seconds to shut down.");
        }
      } catch (InterruptedException e) {
        // Not much we can do about this...
        Thread.currentThread().interrupt();
      }

      if (this.queueManager != null) {
        playerUuids.forEach(u -> this.queueManager.removePlayerEntirely(u));
        this.queueManager.teardown();
      }

      // Disable Redis if we have it enabled
      if (this.configuration.getRedis().isEnabled()) {
        this.redis.shutdown();
      }

      this.playerRegistry.shutdown();

      // Since we manually removed the shutdown hook, we need to handle the shutdown ourselves.
      LogManager.shutdown();

      shutdown = true;

      if (explicitExit) {
        System.exit(0);
      }
    };

    if (explicitExit) {
      Thread thread = new Thread(shutdownProcess);
      thread.start();
    } else {
      shutdownProcess.run();
    }
  }

  /**
   * Calls {@link #shutdown(boolean, Component)} with the default reason "Proxy shutting down.".
   *
   * @param explicitExit whether the user explicitly shut down the proxy
   */
  public void shutdown(boolean explicitExit) {
    shutdown(explicitExit, Component.translatable("velocity.kick.shutdown"));
  }

  @Override
  public void shutdown(Component reason) {
    shutdown(true, reason);
  }

  @Override
  public void shutdown() {
    shutdown(true);
  }

  private @Nullable ProxyAddress getProxyAddressToUse() {
    if (!this.getClusterProxyService().isMultiProxy()) {
      return null;
    }

    DynamicProxyFilterMode filter = getConfiguration().getDynamicProxyFilter();
    List<ProxyAddress> addresses = new ArrayList<>(getConfiguration().getProxyAddresses().stream().toList());
    addresses.removeIf(address -> getProxyId().equalsIgnoreCase(address.proxyId()));

    if (addresses.isEmpty()) {
      return null;
    }

    switch (filter) {
      case FIRST_FOUND -> {
        // Don't sort
      }
      case MOST_EMPTY -> {
        // Sort to get most empty first
        addresses.sort((o1, o2) -> {
          int connectedSize1 = redis.getPlayerService().getPlayerEntriesOnProxy(o1.proxyId()).size();
          int connectedSize2 = redis.getPlayerService().getPlayerEntriesOnProxy(o2.proxyId()).size();
          return Long.compare(connectedSize1, connectedSize2);
        });
      }
      case LEAST_EMPTY -> {
        // Sort to get least empty first
        addresses.sort((o1, o2) -> {
          int connectedSize1 = redis.getPlayerService().getPlayerEntriesOnProxy(o1.proxyId()).size();
          int connectedSize2 = redis.getPlayerService().getPlayerEntriesOnProxy(o2.proxyId()).size();
          return Long.compare(connectedSize2, connectedSize1);
        });
      }
      case NONE -> {
        // No next address
        return null;
      }
      default -> {
        throw new IllegalStateException("Invalid filter '" + filter + "'.");
      }
    }

    return addresses.getFirst();
  }

  @Override
  public void closeListeners() {
    this.cm.closeEndpoints(false);
  }

  public ConnectionManager getConnectionManager() {
    return cm;
  }

  public @MonotonicNonNull Ratelimiter<InetAddress> getIpAttemptLimiter() {
    return ipAttemptLimiter;
  }

  public @MonotonicNonNull Ratelimiter<UUID> getCommandRateLimiter() {
    return commandRateLimiter;
  }

  public @MonotonicNonNull Ratelimiter<UUID> getTabCompleteRateLimiter() {
    return tabCompleteRateLimiter;
  }

  public PlayerRegistry getPlayerRegistry() {
    return playerRegistry;
  }

  /**
   * Attempts to register the {@code connection} with the proxy, kicking any existing
   * player under the same name or UUID if present and if
   * {@link VelocityConfiguration#isKickExistingPlayers()} is {@code true}.
   *
   * @param connection the connection to register
   * @return a future resolving to {@code true} if we registered the connection, {@code false} if not
   */
  public CompletableFuture<Boolean> registerConnection(ConnectedPlayer connection) {
    return playerRegistry.registerConnection(connection);
  }

  @Override
  public Optional<ConnectedPlayer> getPlayer(String username) {
    Preconditions.checkNotNull(username, "username");
    return playerRegistry.getPlayer(username);
  }

  @Override
  public Optional<ConnectedPlayer> getPlayer(UUID uuid) {
    Preconditions.checkNotNull(uuid, "uuid");
    return playerRegistry.getPlayer(uuid);
  }

  @Override
  public Collection<ConnectedPlayer> matchPlayer(String partialName) {
    Objects.requireNonNull(partialName);

    return getOnlinePlayers().stream().filter(p -> p.getUsername()
            .regionMatches(true, 0, partialName, 0, partialName.length()))
        .collect(Collectors.toList());
  }

  @Override
  public Collection<VelocityRegisteredServer> matchServer(String partialName) {
    Objects.requireNonNull(partialName);

    return getAllServers().stream().filter(s -> s.getServerInfo().getName()
            .regionMatches(true, 0, partialName, 0, partialName.length()))
        .collect(Collectors.toList());
  }

  @Override
  public Collection<ConnectedPlayer> getAllPlayers() {
    return ImmutableList.copyOf(playerRegistry.getPlayers());
  }

  @Override
  public @UnmodifiableView Collection<ConnectedPlayer> getOnlinePlayers() {
    return playerRegistry.getPlayers();
  }

  /**
   * Returns whether the given player is currently registered as online on this proxy. Uses an
   * O(1) UUID lookup against the underlying connection map.
   *
   * @param player the player to check
   * @return {@code true} if the same player instance is registered under its UUID
   */
  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public boolean isPlayerOnline(ConnectedPlayer player) {
    return playerRegistry.isPlayerOnline(player);
  }

  @Override
  public int getPlayerCount() {
    return clusterPlayerService.getTotalPlayerCount();
  }

  /**
   * Gets the number of players currently connected to the proxy.
   * Regardless of if Redis is enabled or not, this returns the local
   * player count only.
   *
   * @return the number of locally connected players
   */
  public int getLocalPlayerCount() {
    return playerRegistry.getLocalPlayerCount();
  }

  @Override
  public Optional<VelocityRegisteredServer> getServer(String name) {
    return servers.getServer(name);
  }

  @Override
  public Collection<VelocityRegisteredServer> getAllServers() {
    return servers.getAllServers();
  }

  @Override
  public VelocityRegisteredServer createRawRegisteredServer(ServerInfo server) {
    return servers.createRawRegisteredServer(server);
  }

  @Override
  public VelocityRegisteredServer registerServer(ServerInfo server) {
    return servers.register(server);
  }

  @Override
  public void unregisterServer(ServerInfo server) {
    servers.unregister(server);
  }

  @Override
  public VelocityConsole getConsoleCommandSource() {
    return console;
  }

  @Override
  public PluginManager getPluginManager() {
    return pluginManager;
  }

  @Override
  public VelocityEventManager getEventManager() {
    return eventManager;
  }

  @Override
  public VelocityScheduler getScheduler() {
    return scheduler;
  }

  @Override
  public VelocityChannelRegistrar getChannelRegistrar() {
    return channelRegistrar;
  }

  @Override
  public boolean isShuttingDown() {
    return shutdownInProgress.get();
  }

  @Override
  public InetSocketAddress getBoundAddress() {
    if (configuration == null) {
      throw new IllegalStateException(
          "No configuration"); // even though you'll never get the chance... heh, heh
    }

    return configuration.getBind();
  }

  @Override
  public @NonNull Iterable<? extends Audience> audiences() {
    Collection<ConnectedPlayer> connectedPlayers = getOnlinePlayers();

    Collection<Audience> audiences = new ArrayList<>(connectedPlayers.size() + 1);
    audiences.add(console);
    audiences.addAll(connectedPlayers);

    return audiences;
  }

  /**
   * Returns a Gson instance for use in serializing server ping instances.
   *
   * @param version the protocol version in use
   * @return the Gson instance
   */
  public static Gson getPingGsonInstance(ProtocolVersion version) {
    if (version == ProtocolVersion.UNKNOWN
        || version.noLessThan(ProtocolVersion.MINECRAFT_1_20_3)) {
      return MODERN_PING_SERIALIZER;
    }

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_16)) {
      return PRE_1_20_3_PING_SERIALIZER;
    }

    return PRE_1_16_PING_SERIALIZER;
  }

  @Override
  public ResourcePackInfo.Builder createResourcePackBuilder(String url) {
    return new VelocityResourcePackInfo.BuilderImpl(url);
  }

  public final Logger getLogger() {
    return LOGGER;
  }

  /**
   * Check whether the queue system is enabled for the proxy.
   *
   * @return true if the queue system is enabled, otherwise false
   */
  @Override
  public boolean isQueueEnabled() {
    return this.configuration.getQueue().isEnabled();
  }

  /**
   * Gets the cluster player service.
   *
   * @return the cluster player service
   */
  @Override
  public VelocityClusterPlayerService getClusterPlayerService() {
    return clusterPlayerService;
  }

  /**
   * Gets the cluster proxy service.
   *
   * @return the cluster proxy service
   */
  @Override
  public VelocityClusterProxyService getClusterProxyService() {
    return clusterProxyService;
  }

  /**
   * Returns the proxy id for the current proxy from the proxy configuration.
   *
   * @return the proxy id for the current proxy
   */
  public String getProxyId() {
    if (this.configuration.getRedis().isEnabled()) {
      return this.configuration.getRedis().getProxyId();
    }

    return "single_proxy";
  }
}
