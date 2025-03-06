/*
 * Copyright (C) 2018-2023 Velocity Contributors
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

package com.velocitypowered.proxy.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.annotations.Expose;
import com.velocitypowered.api.proxy.config.ProxyConfig;
import com.velocitypowered.api.util.Favicon;
import com.velocitypowered.api.util.ServerLink;
import com.velocitypowered.proxy.config.migration.ConfigurationMigration;
import com.velocitypowered.proxy.config.migration.ForwardingMigration;
import com.velocitypowered.proxy.config.migration.KeyAuthenticationMigration;
import com.velocitypowered.proxy.config.migration.MiniMessageTranslationsMigration;
import com.velocitypowered.proxy.config.migration.MotdMigration;
import com.velocitypowered.proxy.config.migration.TransferIntegrationMigration;
import com.velocitypowered.proxy.util.AddressUtil;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Velocity's configuration.
 */
public final class VelocityConfiguration implements ProxyConfig {

  private static final Logger logger = LogManager.getLogger(VelocityConfiguration.class);

  @Expose
  private String bind = "0.0.0.0:25565";
  @Expose
  private String motd = "<aqua>A Velocity Server";
  private net.kyori.adventure.text.@MonotonicNonNull Component motdAsComponent;
  @Expose
  private List<String> motdHover = List.of("");
  private List<net.kyori.adventure.text.@MonotonicNonNull Component> motdHoverComponents;
  @Expose
  private int showMaxPlayers = 500;
  @Expose
  private boolean onlineMode = true;
  @Expose
  private boolean preventClientProxyConnections = false;
  @Expose
  private PlayerInfoForwarding playerInfoForwardingMode = PlayerInfoForwarding.NONE;
  private byte[] forwardingSecret = generateRandomString(12).getBytes(StandardCharsets.UTF_8);
  @Expose
  private boolean announceForge = false;
  @Expose
  private boolean onlineModeKickExistingPlayers = false;
  @Expose
  private PingPassthroughMode pingPassthrough = PingPassthroughMode.DISABLED;
  private final Servers servers;
  private final ForcedHosts forcedHosts;
  @Expose
  private final Commands commands;
  @Expose
  private final Advanced advanced;
  @Expose
  private final Query query;
  private final Metrics metrics;
  @Expose
  private int maxCommandsPerSecond = 10;
  @Expose
  private final Redis redis;
  @Expose
  private final Queue queue;
  @Expose
  private boolean enablePlayerAddressLogging = true;
  private @Nullable Favicon favicon;
  @Expose
  private boolean forceKeyAuthentication = true; // Added in 1.19
  @Expose
  private boolean logPlayerConnections = true;
  @Expose
  private boolean logPlayerDisconnections = true;
  @Expose
  private boolean logOfflineConnections = true;
  @Expose
  private boolean disableForge = false;
  @Expose
  private boolean enforceChatSigning = true;
  @Expose
  private boolean translateHeaderFooter = true;
  @Expose
  private boolean logMinimumVersion = false;
  @Expose
  private String minimumVersion = "1.7.2";
  @Expose
  private Map<String, List<String>> slashServers = new HashMap<>();
  @Expose
  private List<ServerLink> serverLinks = new ArrayList<>();
  @Expose
  private List<ProxyAddress> proxyAddresses = new ArrayList<>();
  @Expose
  private String dynamicProxyFilter;
  @Expose
  private Map<String, Integer> playerCaps;

  private VelocityConfiguration(final Servers servers, final ForcedHosts forcedHosts, final Commands commands,
                                final Advanced advanced, final Query query, final Metrics metrics, final Redis redis, final Queue queue) {
    this.servers = servers;
    this.forcedHosts = forcedHosts;
    this.commands = commands;
    this.advanced = advanced;
    this.query = query;
    this.metrics = metrics;
    this.redis = redis;
    this.queue = queue;
  }

  private VelocityConfiguration(final String bind, final String motd, final List<String> motdHover,
                                final int showMaxPlayers, final boolean onlineMode,
                                final boolean preventClientProxyConnections, final boolean announceForge,
                                final PlayerInfoForwarding playerInfoForwardingMode, final byte[] forwardingSecret,
                                final boolean onlineModeKickExistingPlayers, final PingPassthroughMode pingPassthrough,
                                final boolean enablePlayerAddressLogging, final Servers servers, final ForcedHosts forcedHosts,
                                final Commands commands, final Advanced advanced, final Query query, final Metrics metrics,
                                final boolean forceKeyAuthentication, final boolean logPlayerConnections, final boolean logPlayerDisconnections,
                                final boolean logOfflineConnections, final int maxCommandsPerSecond, final boolean disableForge,
                                final boolean enforceChatSigning, final boolean translateHeaderFooter, final boolean logMinimumVersion,
                                final String minimumVersion, final Redis redis, final Queue queue, final Map<String, List<String>> slashServers,
                                final List<ServerLink> serverLinks, final List<ProxyAddress> proxyAddresses, final String dynamicProxyFilter,
                                final Map<String, Integer> playerCaps) {
    this.bind = bind;
    this.motd = motd;
    this.motdHover = motdHover;
    this.showMaxPlayers = showMaxPlayers;
    this.onlineMode = onlineMode;
    this.preventClientProxyConnections = preventClientProxyConnections;
    this.announceForge = announceForge;
    this.playerInfoForwardingMode = playerInfoForwardingMode;
    this.forwardingSecret = forwardingSecret;
    this.onlineModeKickExistingPlayers = onlineModeKickExistingPlayers;
    this.pingPassthrough = pingPassthrough;
    this.enablePlayerAddressLogging = enablePlayerAddressLogging;
    this.servers = servers;
    this.forcedHosts = forcedHosts;
    this.commands = commands;
    this.advanced = advanced;
    this.query = query;
    this.metrics = metrics;
    this.forceKeyAuthentication = forceKeyAuthentication;
    this.logPlayerConnections = logPlayerConnections;
    this.logPlayerDisconnections = logPlayerDisconnections;
    this.maxCommandsPerSecond = maxCommandsPerSecond;
    this.logOfflineConnections = logOfflineConnections;
    this.disableForge = disableForge;
    this.enforceChatSigning = enforceChatSigning;
    this.translateHeaderFooter = translateHeaderFooter;
    this.logMinimumVersion = logMinimumVersion;
    this.minimumVersion = minimumVersion;
    this.redis = redis;
    this.queue = queue;
    this.slashServers = slashServers;
    this.serverLinks = serverLinks;
    this.proxyAddresses = proxyAddresses;
    this.dynamicProxyFilter = dynamicProxyFilter;
    this.playerCaps = playerCaps;
  }

  /**
   * Attempts to validate the configuration.
   *
   * @return {@code true} if the configuration is sound, {@code false} if not
   */
  public boolean validate() {
    boolean valid = true;

    if (bind.isEmpty()) {
      logger.error("'bind' option is empty.");
      valid = false;
    } else {
      try {
        AddressUtil.parseAddress(bind);
      } catch (IllegalArgumentException e) {
        logger.error("'bind' option does not specify a valid IP address.", e);
        valid = false;
      }
    }

    if (!onlineMode) {
      logger.warn("The proxy is running in offline mode! This is a security risk and you will NOT "
          + "receive any support!");
    }

    boolean requireForwardingSecret = false;
    for (Map.Entry<String, PlayerInfoForwarding> entry : servers.getServerForwardingModes().entrySet()) {
      switch (entry.getValue()) {
        case NONE:
          logger.warn("Player info forwarding is disabled for {}!"
                  + " All players will appear to be connecting from the proxy and will have offline-mode UUIDs.", entry.getKey());
          break;
        case MODERN:
        case BUNGEEGUARD:
          requireForwardingSecret = true;
          break;
        default:
          break;
      }
    }

    switch (playerInfoForwardingMode) {
      case NONE:
        logger.warn("Player info forwarding is disabled by default! All players will appear to be connecting "
            + "from the proxy and will have offline-mode UUIDs.");
        break;
      case MODERN:
      case BUNGEEGUARD:
        requireForwardingSecret = true;
        break;
      default:
        break;
    }

    if (requireForwardingSecret && (forwardingSecret == null || forwardingSecret.length == 0)) {
      logger.error("You don't have a forwarding secret set. This is required for security. "
          + "See https://docs.papermc.io/velocity for more details.");
      valid = false;
    }

    if (servers.getServers().isEmpty()) {
      logger.warn("You don't have any servers configured.");
    }

    for (Map.Entry<String, String> entry : servers.getServers().entrySet()) {
      try {
        AddressUtil.parseAddress(entry.getValue());
      } catch (IllegalArgumentException e) {
        logger.error("Server {} does not have a valid IP address.", entry.getKey(), e);
        valid = false;
      }
    }

    for (String s : servers.getAttemptConnectionOrder()) {
      if (!servers.getServers().containsKey(s)) {
        logger.error("Fallback server {} is not registered in your configuration!", s);
        valid = false;
      }
    }

    final Map<String, List<String>> configuredForcedHosts = forcedHosts.getForcedHosts();
    if (!configuredForcedHosts.isEmpty()) {
      for (Map.Entry<String, List<String>> entry : configuredForcedHosts.entrySet()) {
        if (entry.getValue().isEmpty()) {
          logger.error("Forced host '{}' does not contain any servers", entry.getKey());
          valid = false;
          continue;
        }

        for (String server : entry.getValue()) {
          if (!servers.getServers().containsKey(server)) {
            logger.error("Server '{}' for forced host '{}' does not exist", server, entry.getKey());
            valid = false;
          }
        }
      }
    }

    for (Map.Entry<String, List<String>> entry : slashServers.entrySet()) {
      if (entry.getValue().isEmpty()) {
        logger.error("Slash server alias '{}' does not contain any servers", entry.getKey());
        valid = false;
        continue;
      }

      if (!servers.getServers().containsKey(entry.getKey())) {
        logger.error("Server '{}' does not exist in slash server aliases", entry.getKey());
        valid = false;
      }
    }

    try {
      getMotd();
    } catch (Exception e) {
      logger.error("Can't parse your MOTD", e);
      valid = false;
    }

    try {
      getMotdHover();
    } catch (Exception e) {
      logger.error("Can't parse your MOTD hover", e);
      valid = false;
    }

    if (advanced.compressionLevel < -1 || advanced.compressionLevel > 9) {
      logger.error("Invalid compression level {}", advanced.compressionLevel);
      valid = false;
    } else if (advanced.compressionLevel == 0) {
      logger.warn("ALL packets going through the proxy will be uncompressed. This will increase "
          + "bandwidth usage.");
    }

    if (advanced.compressionThreshold < -1) {
      logger.error("Invalid compression threshold {}", advanced.compressionLevel);
      valid = false;
    } else if (advanced.compressionThreshold == 0) {
      logger.warn("ALL packets going through the proxy will be compressed. This will compromise "
          + "throughput and increase CPU usage!");
    }

    if (advanced.loginRatelimit < 0) {
      logger.error("Invalid login ratelimit {}ms", advanced.loginRatelimit);
      valid = false;
    }

    loadFavicon();

    return valid;
  }

  private void loadFavicon() {
    Path faviconPath = Path.of("server-icon.png");
    if (Files.exists(faviconPath)) {
      try {
        this.favicon = Favicon.create(faviconPath);
      } catch (Exception e) {
        logger.info("Unable to load your server-icon.png, continuing without it.", e);
      }
    }
  }

  public InetSocketAddress getBind() {
    return AddressUtil.parseAndResolveAddress(bind);
  }

  @Override
  public boolean isQueryEnabled() {
    return query.isQueryEnabled();
  }

  @Override
  public int getQueryPort() {
    return query.getQueryPort();
  }

  @Override
  public String getQueryMap() {
    return query.getQueryMap();
  }

  @Override
  public boolean shouldQueryShowPlugins() {
    return query.shouldQueryShowPlugins();
  }

  @Override
  public net.kyori.adventure.text.Component getMotd() {
    if (motdAsComponent == null) {
      motdAsComponent = MiniMessage.miniMessage().deserialize(motd);
    }
    return motdAsComponent;
  }

  @Override
  public List<net.kyori.adventure.text.Component> getMotdHover() {
    if (motdHoverComponents == null) {
      motdHoverComponents = motdHover.stream()
          .map(MiniMessage.miniMessage()::deserialize)
          .toList();
    }
    return motdHoverComponents;
  }

  @Override
  public int getShowMaxPlayers() {
    return showMaxPlayers;
  }

  @Override
  public boolean isOnlineMode() {
    return onlineMode;
  }

  @Override
  public boolean shouldPreventClientProxyConnections() {
    return preventClientProxyConnections;
  }

  public PlayerInfoForwarding getPlayerInfoForwardingMode() {
    return playerInfoForwardingMode;
  }

  public PlayerInfoForwarding getServerForwardingMode(final String server) {
    return servers.getServerForwardingModes().getOrDefault(server, playerInfoForwardingMode);
  }

  public byte[] getForwardingSecret() {
    return forwardingSecret.clone();
  }

  @Override
  public Map<String, String> getServers() {
    return servers.getServers();
  }

  @Override
  public List<String> getAttemptConnectionOrder() {
    return servers.getAttemptConnectionOrder();
  }

  @Override
  public Map<String, List<String>> getForcedHosts() {
    return forcedHosts.getForcedHosts();
  }

  public long getMaxCommandsPerSecond() {
    return maxCommandsPerSecond;
  }

  @Override
  public int getCompressionThreshold() {
    return advanced.getCompressionThreshold();
  }

  @Override
  public int getCompressionLevel() {
    return advanced.getCompressionLevel();
  }

  @Override
  public int getLoginRatelimit() {
    return advanced.getLoginRatelimit();
  }

  @Override
  public Optional<Favicon> getFavicon() {
    return Optional.ofNullable(favicon);
  }

  @Override
  public boolean isAnnounceForge() {
    return announceForge;
  }

  @Override
  public int getConnectTimeout() {
    return advanced.getConnectionTimeout();
  }

  public boolean isServerEnabled() {
    return commands.isServerEnabled();
  }

  public boolean isAlertEnabled() {
    return commands.isAlertEnabled();
  }

  public boolean isAlertRawEnabled() {
    return commands.isAlertRawEnabled();
  }

  public boolean isFindEnabled() {
    return commands.isFindEnabled();
  }

  public boolean isTransferEnabled() {
    return commands.isTransferEnabled();
  }

  public boolean isGlistEnabled() {
    return commands.isGlistEnabled();
  }

  public boolean isPlistEnabled() {
    return commands.isPlistEnabled();
  }

  public boolean isHubEnabled() {
    return commands.isHubEnabled();
  }

  public boolean isPingEnabled() {
    return commands.isPingEnabled();
  }

  public boolean isSendEnabled() {
    return commands.isSendEnabled();
  }

  public boolean isOverrideServerCommandUsage() {
    return commands.isOverrideServerCommandUsage();
  }

  @Override
  public int getReadTimeout() {
    return advanced.getReadTimeout();
  }

  public boolean isProxyProtocol() {
    return advanced.isProxyProtocol();
  }

  public void setProxyProtocol(final boolean proxyProtocol) {
    advanced.setProxyProtocol(proxyProtocol);
  }

  public boolean useTcpFastOpen() {
    return advanced.isTcpFastOpen();
  }

  public Metrics getMetrics() {
    return metrics;
  }

  public PingPassthroughMode getPingPassthrough() {
    return pingPassthrough;
  }

  public boolean isPlayerAddressLoggingEnabled() {
    return enablePlayerAddressLogging;
  }

  public boolean isBungeePluginChannelEnabled() {
    return advanced.isBungeePluginMessageChannel();
  }

  public boolean isShowPingRequests() {
    return advanced.isShowPingRequests();
  }

  public boolean isFailoverOnUnexpectedServerDisconnect() {
    return advanced.isFailoverOnUnexpectedServerDisconnect();
  }

  public boolean isAnnounceProxyCommands() {
    return advanced.isAnnounceProxyCommands();
  }

  public boolean isLogCommandExecutions() {
    return advanced.isLogCommandExecutions();
  }

  public boolean isAcceptTransfers() {
    return this.advanced.isAcceptTransfers();
  }

  public boolean isAllowIllegalCharactersInChat() {
    return advanced.isAllowIllegalCharactersInChat();
  }

  public String getServerBrand() {
    return advanced.getServerBrand();
  }

  public String getFallbackVersionPing() {
    return advanced.getFallbackVersionPing();
  }

  public boolean getAlwaysFallBackPing() {
    return advanced.getAlwaysFallBackPing();
  }

  public String getProxyBrandCustom() {
    return advanced.getProxyBrandCustom();
  }

  public String getBackendBrandCustom() {
    return advanced.getBackendBrandCustom();
  }

  public String getDynamicFallbackFilter() {
    return servers.getDynamicFallbackFilter();
  }

  public boolean isForceKeyAuthentication() {
    return forceKeyAuthentication;
  }

  public boolean isEnableReusePort() {
    return advanced.isEnableReusePort();
  }

  public @NotNull Redis getRedis() {
    return redis;
  }

  public @NotNull Queue getQueue() {
    return queue;
  }

  public Map<String, List<String>> getSlashServers() {
    return slashServers;
  }

  public List<ServerLink> getServerLinks() {
    return serverLinks;
  }

  public List<ProxyAddress> getProxyAddresses() {
    return proxyAddresses;
  }

  public String getDynamicProxyFilter() {
    return this.dynamicProxyFilter;
  }

  public Map<String, Integer> getPlayerCaps() {
    return this.playerCaps;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("bind", bind)
        .add("motd", motd)
        .add("motdHover", motdHover)
        .add("showMaxPlayers", showMaxPlayers)
        .add("onlineMode", onlineMode)
        .add("playerInfoForwardingMode", playerInfoForwardingMode)
        .add("forwardingSecret", forwardingSecret)
        .add("announceForge", announceForge)
        .add("servers", servers)
        .add("forcedHosts", forcedHosts)
        .add("commands", commands)
        .add("advanced", advanced)
        .add("query", query)
        .add("redis", redis)
        .add("queue", queue)
        .add("favicon", favicon)
        .add("enablePlayerAddressLogging", enablePlayerAddressLogging)
        .add("forceKeyAuthentication", forceKeyAuthentication)
        .add("logPlayerConnections", logPlayerConnections)
        .add("logPlayerDisconnections", logPlayerDisconnections)
        .add("logOfflineConnections", logOfflineConnections)
        .add("disableForge", disableForge)
        .add("enforceChatSigning", enforceChatSigning)
        .add("translateHeaderFooter", translateHeaderFooter)
        .add("logMinimumVersion", logMinimumVersion)
        .add("minimumVersion", minimumVersion)
        .add("slashServers", slashServers)
        .add("playerCaps", playerCaps)
        .toString();
  }

  /**
   * Reads the Velocity configuration from {@code path}.
   *
   * @param path the path to read from
   * @return the deserialized Velocity configuration
   * @throws IOException if we could not read from the {@code path}.
   */
  @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE",
      justification = "I looked carefully and there's no way SpotBugs is right.")
  public static VelocityConfiguration read(final Path path) throws IOException {
    URL defaultConfigLocation = VelocityConfiguration.class.getClassLoader()
        .getResource("default-velocity.toml");
    if (defaultConfigLocation == null) {
      throw new RuntimeException("Default configuration file does not exist.");
    }

    // Create the forwarding-secret file on first-time startup if it doesn't exist
    final Path defaultForwardingSecretPath = Path.of("forwarding.secret");
    if (Files.notExists(path) && Files.notExists(defaultForwardingSecretPath)) {
      Files.writeString(defaultForwardingSecretPath, generateRandomString(12));
    }

    try (CommentedFileConfig config = CommentedFileConfig.builder(path)
            .defaultData(defaultConfigLocation)
            .autosave()
            .preserveInsertionOrder()
            .sync()
            .build()
    ) {
      config.load();

      final ConfigurationMigration[] migrations = {
          new ForwardingMigration(),
          new KeyAuthenticationMigration(),
          new MotdMigration(),
          new MiniMessageTranslationsMigration(),
          new TransferIntegrationMigration()
      };

      for (final ConfigurationMigration migration : migrations) {
        if (migration.shouldMigrate(config)) {
          migration.migrate(config, logger);
        }
      }

      String forwardingSecretString = System.getenv().getOrDefault(
          "VELOCITY_FORWARDING_SECRET", "");
      if (forwardingSecretString.isEmpty()) {
        final String forwardSecretFile = config.get("forwarding-secret-file");
        final Path secretPath = forwardSecretFile == null
            ? defaultForwardingSecretPath
            : Path.of(forwardSecretFile);
        if (Files.exists(secretPath)) {
          if (Files.isRegularFile(secretPath)) {
            forwardingSecretString = String.join("", Files.readAllLines(secretPath));
          } else {
            throw new RuntimeException(
                "The file " + forwardSecretFile + " is not a valid file or it is a directory.");
          }
        } else {
          throw new RuntimeException("The forwarding-secret-file does not exist.");
        }
      }
      final byte[] forwardingSecret = forwardingSecretString.getBytes(StandardCharsets.UTF_8);
      final String motd = config.getOrElse("motd", "<#09add3>A Velocity Server");
      final List<String> motdHover = config.getOrElse("motd-hover", new ArrayList<>());

      // Read the rest of the config
      final CommentedConfig serversConfig = config.get("servers");
      final CommentedConfig forcedHostsConfig = config.get("forced-hosts");
      final CommentedConfig commandsConfig = config.get("commands");
      final CommentedConfig advancedConfig = config.get("advanced");
      final CommentedConfig queryConfig = config.get("query");
      final CommentedConfig metricsConfig = config.get("metrics");
      final CommentedConfig redisConfig = config.get("redis");
      final CommentedConfig queueConfig = config.get("queue");
      final CommentedConfig serverLinksConfig = config.get("server-links");
      final CommentedConfig proxyAddressesConfig = config.get("proxy-addresses");
      final CommentedConfig playerCapsConfig = config.get("playercaps");
      final PlayerInfoForwarding forwardingMode = config.getEnumOrElse(
              "player-info-forwarding-mode", PlayerInfoForwarding.NONE);
      final PingPassthroughMode pingPassthroughMode = config.getEnumOrElse("ping-passthrough",
              PingPassthroughMode.DISABLED);
      final String bind = config.getOrElse("bind", "0.0.0.0:25565");
      final int maxPlayers = config.getIntOrElse("show-max-players", 500);
      final boolean onlineMode = config.getOrElse("online-mode", true);
      final boolean forceKeyAuthentication = config.getOrElse("force-key-authentication", true);
      final boolean announceForge = config.getOrElse("announce-forge", true);
      final boolean preventClientProxyConnections = config.getOrElse(
              "prevent-client-proxy-connections", false);
      final boolean kickExisting = config.getOrElse("kick-existing-players", false);
      final boolean enablePlayerAddressLogging = config.getOrElse(
              "enable-player-address-logging", true);
      final int maxCommandsPerSecond = config.getOrElse(
              "max-commands-per-second", 10);
      final boolean logPlayerConnections = config.getOrElse(
              "log-player-connections", true);
      final boolean logPlayerDisconnections = config.getOrElse(
              "log-player-disconnections", true);
      final boolean logOfflineConnections = config.getOrElse(
              "log-offline-connections", true);
      final boolean disableForge = config.getOrElse("disable-forge", false);
      final boolean enforceChatSigning = config.getOrElse(
              "enforce-chat-signing", false);
      final boolean translateHeaderFooter = config.getOrElse(
              "translate-header-footer", true);
      final boolean logMinimumVersion = config.getOrElse(
              "log-minimum-version", false);
      final String minimumVersion = config.getOrElse("minimum-version", "1.7.2");
      final CommentedConfig slashServersConfig = config.getOrElse("slash-servers", (CommentedConfig) null);

      final Map<String, List<String>> slashServers = new HashMap<>();

      if (slashServersConfig != null) {
        for (UnmodifiableConfig.Entry entry : slashServersConfig.entrySet()) {
          if (entry.getValue() instanceof String) {
            slashServers.put(entry.getKey(), ImmutableList.of(entry.getValue()));
          } else if (entry.getValue() instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> value = ImmutableList.copyOf((List<String>) entry.getValue());
            slashServers.put(entry.getKey(), value);
          } else {
            throw new IllegalStateException(
                "Invalid value of type " + entry.getValue().getClass() + " in slash servers!");
          }
        }
      }

      final List<ServerLink> links = new ArrayList<>();

      if (serverLinksConfig != null) {
        for (CommentedConfig.Entry entry : serverLinksConfig.entrySet()) {
          CommentedConfig link = entry.getValue();
          links.add(ServerLink.serverLink(MiniMessage.miniMessage().deserialize(link.get("label")),
              link.get("link")));
        }
      }

      final List<ProxyAddress> addresses = new ArrayList<>();
      String filter = "MOST_EMPTY";

      if (proxyAddressesConfig != null) {
        filter = proxyAddressesConfig.getOrElse("dynamic-proxy-filter", "MOST_EMPTY");
        for (CommentedConfig.Entry entry : proxyAddressesConfig.entrySet()) {
          if (entry.getKey().equalsIgnoreCase("dynamic-proxy-filter")) {
            continue;
          }

          CommentedConfig link = entry.getValue();
          addresses.add(new ProxyAddress(link.get("proxy-id"),
              link.get("ip"),
              link.get("port")));
        }
      }

      final Map<String, Integer> playerCaps = new HashMap<>();

      if (playerCapsConfig != null) {
        for (CommentedConfig.Entry entry : playerCapsConfig.entrySet()) {
          playerCaps.put(entry.getKey(), entry.getValue());
        }
      }

      // Throw an exception if the forwarding-secret file is empty and the proxy is using a
      // forwarding mode that requires it.
      if (forwardingSecret.length == 0
              && (forwardingMode == PlayerInfoForwarding.MODERN
              || forwardingMode == PlayerInfoForwarding.BUNGEEGUARD)) {
        throw new RuntimeException("The forwarding-secret file must not be empty.");
      }

      return new VelocityConfiguration(
              bind,
              motd,
              motdHover,
              maxPlayers,
              onlineMode,
              preventClientProxyConnections,
              announceForge,
              forwardingMode,
              forwardingSecret,
              kickExisting,
              pingPassthroughMode,
              enablePlayerAddressLogging,
              new Servers(serversConfig),
              new ForcedHosts(forcedHostsConfig),
              new Commands(commandsConfig),
              new Advanced(advancedConfig),
              new Query(queryConfig),
              new Metrics(metricsConfig),
              forceKeyAuthentication,
              logPlayerConnections,
              logPlayerDisconnections,
              logOfflineConnections,
              maxCommandsPerSecond,
              disableForge,
              enforceChatSigning,
              translateHeaderFooter,
              logMinimumVersion,
              minimumVersion,
              new Redis(redisConfig),
              new Queue(queueConfig),
              slashServers,
              links,
              addresses,
              filter,
              playerCaps
      );
    }
  }

  /**
   * Generates a Random String.
   *
   * @param length the required string size.
   * @return a new random string.
   */
  public static String generateRandomString(final int length) {
    final String chars = "AaBbCcDdEeFfGgHhIiJjKkLlMmNnOoPpQqRrSsTtUuVvWwXxYyZz1234567890";
    final StringBuilder builder = new StringBuilder();
    final Random rnd = new SecureRandom();
    for (int i = 0; i < length; i++) {
      builder.append(chars.charAt(rnd.nextInt(chars.length())));
    }
    return builder.toString();
  }

  public boolean isOnlineModeKickExistingPlayers() {
    return onlineModeKickExistingPlayers;
  }

  public boolean isLogPlayerConnections() {
    return logPlayerConnections;
  }

  public boolean isLogPlayerDisconnections() {
    return logPlayerDisconnections;
  }

  public boolean isLogOfflineConnections() {
    return logOfflineConnections;
  }

  public boolean isDisableForge() {
    return disableForge;
  }

  public boolean enforceChatSigning() {
    return enforceChatSigning;
  }

  public boolean isTranslateHeaderFooter() {
    return translateHeaderFooter;
  }

  public boolean isLogMinimumVersion() {
    return logMinimumVersion;
  }

  public String getMinimumVersion() {
    return minimumVersion;
  }

  public List<String> getServerAliases() {
    return this.servers.getServerAliases();
  }

  private static final class Servers {

    private Map<String, String> servers = ImmutableMap.of(
        "lobby", "127.0.0.1:30066",
        "factions", "127.0.0.1:30067",
        "minigames", "127.0.0.1:30068"
    );
    private List<String> attemptConnectionOrder = ImmutableList.of("lobby");
    private Map<String, PlayerInfoForwarding> serverForwardingModes = ImmutableMap.of();

    private String dynamicFallbackFilter;
    @Expose
    private List<String> serverAliases;

    private Servers() {
    }

    private Servers(final CommentedConfig config) {
      if (config != null) {
        Map<String, String> servers = new HashMap<>();
        Map<String, PlayerInfoForwarding> serverForwardingModes = new HashMap<>();
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
          if (entry.getKey().equalsIgnoreCase("dynamic-fallbacks-filter")) {
            continue; // Intended despite uselessness.
          } else if (entry.getValue() instanceof String) {
            servers.put(cleanServerName(entry.getKey()), entry.getValue());
          } else if (entry.getValue() instanceof UnmodifiableConfig) {
            UnmodifiableConfig unmodifiableConfig = entry.getValue();
            String name = entry.getKey();

            String address = unmodifiableConfig.get("address");
            if (address == null) {
              throw new IllegalArgumentException("Server " + name + " doesn't have an address!");
            }

            PlayerInfoForwarding mode = unmodifiableConfig.getEnum("forwarding-mode", PlayerInfoForwarding.class);
            if (mode != null) {
              serverForwardingModes.put(name, mode);
            }

            servers.put(cleanServerName(name), address);
          } else {
            if (!entry.getKey().equalsIgnoreCase("try")
                && !entry.getKey().equalsIgnoreCase("dynamic-fallbacks-filter")
                && !entry.getKey().equalsIgnoreCase("server-aliases")) {
              throw new IllegalArgumentException(
                  "Server entry " + entry.getKey() + " is not a string!");
            }
          }
        }
        this.servers = ImmutableMap.copyOf(servers);
        this.serverForwardingModes = ImmutableMap.copyOf(serverForwardingModes);
        this.attemptConnectionOrder = config.getOrElse("try", attemptConnectionOrder)
            .stream()
            .toList();
        this.dynamicFallbackFilter = config.getOrElse("dynamic-fallbacks-filter", "FIRST_AVAILABLE");
        this.serverAliases = config.getOrElse("server-aliases", List.of("joinqueue", "queue", "server"));
      }
    }

    private Servers(final Map<String, String> servers, final List<String> attemptConnectionOrder,
                    final Map<String, PlayerInfoForwarding> serverForwardingModes) {
      this.servers = servers;
      this.attemptConnectionOrder = attemptConnectionOrder;
    }

    public List<String> getServerAliases() {
      return serverAliases;
    }

    private Map<String, String> getServers() {
      return servers;
    }

    public void setServers(final Map<String, String> servers) {
      this.servers = servers;
    }

    public List<String> getAttemptConnectionOrder() {
      return attemptConnectionOrder;
    }

    public String getDynamicFallbackFilter() {
      return dynamicFallbackFilter;
    }

    public void setAttemptConnectionOrder(final List<String> attemptConnectionOrder) {
      this.attemptConnectionOrder = attemptConnectionOrder;
      this.serverForwardingModes = ImmutableMap.copyOf(serverForwardingModes);
    }

    public Map<String, PlayerInfoForwarding> getServerForwardingModes() {
      return serverForwardingModes;
    }

    public void setServerForwardingModes(final Map<String, PlayerInfoForwarding> serverForwardingModes) {
      this.serverForwardingModes = serverForwardingModes;
    }

    /**
     * TOML requires keys to match a regex of {@code [A-Za-z0-9_-]} unless it is wrapped in quotes;
     * however, the TOML parser returns the key with the quotes so we need to clean the server name
     * before we pass it onto server registration to keep proper server name behavior.
     *
     * @param name the server name to clean
     * @return the cleaned server name
     */
    private String cleanServerName(final String name) {
      return name.replace("\"", "");
    }

    @Override
    public String toString() {
      return "Servers{"
          + "servers=" + servers
          + ", attemptConnectionOrder=" + attemptConnectionOrder
          + ", serverForwardingModes=" + serverForwardingModes
          + '}';
    }
  }

  private static final class ForcedHosts {

    private Map<String, List<String>> forcedHosts = ImmutableMap.of();

    private ForcedHosts() {
    }

    private ForcedHosts(final CommentedConfig config) {
      if (config != null) {
        Map<String, List<String>> forcedHosts = new HashMap<>();
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
          if (entry.getValue() instanceof String) {
            forcedHosts.put(entry.getKey(),
                ImmutableList.of(entry.getValue()));
          } else if (entry.getValue() instanceof List) {
            forcedHosts.put(entry.getKey(),
                ImmutableList.copyOf((List<String>) entry.getValue()));
          } else {
            throw new IllegalStateException(
                "Invalid value of type " + entry.getValue().getClass() + " in forced hosts!");
          }
        }
        this.forcedHosts = ImmutableMap.copyOf(forcedHosts);
      }
    }

    private ForcedHosts(final Map<String, List<String>> forcedHosts) {
      this.forcedHosts = forcedHosts;
    }

    private Map<String, List<String>> getForcedHosts() {
      return forcedHosts;
    }

    private void setForcedHosts(final Map<String, List<String>> forcedHosts) {
      this.forcedHosts = forcedHosts;
    }

    @Override
    public String toString() {
      return "ForcedHosts{"
          + "forcedHosts=" + forcedHosts
          + '}';
    }
  }

  private static final class Commands {
    @Expose
    private boolean serverCommand = true;
    @Expose
    private boolean alertCommand = true;
    @Expose
    private boolean alertRawCommand = true;
    @Expose
    private boolean findCommand = true;
    @Expose
    private boolean glistCommand = true;
    @Expose
    private boolean plistCommand = true;
    @Expose
    private boolean hubCommand = true;
    @Expose
    private boolean pingCommand = true;
    @Expose
    private boolean sendCommand = true;
    @Expose
    private boolean overrideServerCommandUsage = false;
    @Expose
    private boolean transferEnabled = true;

    private Commands() {
    }

    private Commands(final CommentedConfig config) {
      if (config != null) {
        this.serverCommand = config.getOrElse("server-enabled", true);
        this.alertCommand = config.getOrElse("alert-enabled", true);
        this.alertRawCommand = config.getOrElse("alertraw-enabled", true);
        this.findCommand = config.getOrElse("find-enabled", true);
        this.glistCommand = config.getOrElse("glist-enabled", true);
        this.plistCommand = config.getOrElse("plist-enabled", true);
        this.hubCommand = config.getOrElse("hub-enabled", true);
        this.pingCommand = config.getOrElse("ping-enabled", true);
        this.sendCommand = config.getOrElse("send-enabled", true);
        this.overrideServerCommandUsage = config.getOrElse("override-server-command-usage", false);
        this.transferEnabled = config.getOrElse("transfer-enabled", true);
      }
    }

    public boolean isServerEnabled() {
      return serverCommand;
    }

    public boolean isAlertEnabled() {
      return alertCommand;
    }

    public boolean isAlertRawEnabled() {
      return alertRawCommand;
    }

    public boolean isFindEnabled() {
      return findCommand;
    }

    public boolean isGlistEnabled() {
      return glistCommand;
    }

    public boolean isPlistEnabled() {
      return plistCommand;
    }

    public boolean isHubEnabled() {
      return hubCommand;
    }

    public boolean isPingEnabled() {
      return pingCommand;
    }

    public boolean isSendEnabled() {
      return sendCommand;
    }

    public boolean isOverrideServerCommandUsage() {
      return overrideServerCommandUsage;
    }

    public boolean isTransferEnabled() {
      return transferEnabled;
    }

    @Override
    public String toString() {
      return "Commands{"
          + "serverCommand=" + serverCommand
          + ", alertCommand=" + alertCommand
          + ", alertRawCommand=" + alertRawCommand
          + ", findCommand=" + findCommand
          + ", glistCommand=" + glistCommand
          + ", plistCommand=" + plistCommand
          + ", hubCommand=" + hubCommand
          + ", pingCommand=" + pingCommand
          + ", sendCommand=" + sendCommand
          + ", overrideServerCommandUsage=" + overrideServerCommandUsage
          + '}';
    }
  }

  private static final class Advanced {

    @Expose
    private int compressionThreshold = 256;
    @Expose
    private int compressionLevel = -1;
    @Expose
    private int loginRatelimit = 3000;
    @Expose
    private int connectionTimeout = 5000;
    @Expose
    private int readTimeout = 30000;
    @Expose
    private boolean proxyProtocol = false;
    @Expose
    private boolean tcpFastOpen = false;
    @Expose
    private boolean bungeePluginMessageChannel = true;
    @Expose
    private boolean showPingRequests = false;
    @Expose
    private boolean failoverOnUnexpectedServerDisconnect = true;
    @Expose
    private boolean announceProxyCommands = true;
    @Expose
    private boolean logCommandExecutions = false;
    @Expose
    private boolean acceptTransfers = false;
    @Expose
    private boolean enableReusePort = false;
    @Expose
    private boolean allowIllegalCharactersInChat = false;
    @Expose
    private String serverBrand = "{backend-brand} ({proxy-brand})";
    private String serverBrandAsString;
    @Expose
    private String fallbackVersionPing = "{proxy-brand} {protocol-min}-{protocol-max}";
    private String fallbackVersionPingAsString;
    @Expose
    private boolean alwaysFallBackPing = true;
    @Expose
    private String proxyBrandCustom = "Velocity";
    @Expose
    private String backendBrandCustom = "Paper";

    private Advanced() {
    }

    private Advanced(final CommentedConfig config) {
      if (config != null) {
        this.compressionThreshold = config.getIntOrElse("compression-threshold", 256);
        this.compressionLevel = config.getIntOrElse("compression-level", -1);
        this.loginRatelimit = config.getIntOrElse("login-ratelimit", 3000);
        this.connectionTimeout = config.getIntOrElse("connection-timeout", 5000);
        this.readTimeout = config.getIntOrElse("read-timeout", 30000);
        if (config.contains("haproxy-protocol")) {
          this.proxyProtocol = config.getOrElse("haproxy-protocol", false);
        } else {
          this.proxyProtocol = config.getOrElse("proxy-protocol", false);
        }
        this.tcpFastOpen = config.getOrElse("tcp-fast-open", false);
        this.bungeePluginMessageChannel = config.getOrElse("bungee-plugin-message-channel", true);
        this.showPingRequests = config.getOrElse("show-ping-requests", false);
        this.failoverOnUnexpectedServerDisconnect = config
            .getOrElse("failover-on-unexpected-server-disconnect", true);
        this.announceProxyCommands = config.getOrElse("announce-proxy-commands", true);
        this.logCommandExecutions = config.getOrElse("log-command-executions", false);
        this.acceptTransfers = config.getOrElse("accepts-transfers", false);
        this.enableReusePort = config.getOrElse("enable-reuse-port", false);
        this.allowIllegalCharactersInChat = config.getOrElse("allow-illegal-characters-in-chat", false);
        this.serverBrand = config.getOrElse("server-brand", "{backend-brand} ({proxy-brand})");
        this.fallbackVersionPing = config.getOrElse("fallback-version-ping", "{proxy-brand} {protocol-min}-{protocol-max}");
        this.alwaysFallBackPing = config.getOrElse("always-fallback-ping", false);
        this.proxyBrandCustom = config.getOrElse("custom-brand-proxy", "Velocity");
        this.backendBrandCustom = config.getOrElse("custom-brand-backend", "Paper");
      }

      this.serverBrandAsString = LegacyComponentSerializer.legacySection()
          .serialize(MiniMessage.miniMessage().deserialize(this.serverBrand));

      this.fallbackVersionPingAsString = LegacyComponentSerializer.legacySection()
          .serialize(MiniMessage.miniMessage().deserialize(this.fallbackVersionPing));
    }

    public int getCompressionThreshold() {
      return compressionThreshold;
    }

    public int getCompressionLevel() {
      return compressionLevel;
    }

    public int getLoginRatelimit() {
      return loginRatelimit;
    }

    public int getConnectionTimeout() {
      return connectionTimeout;
    }

    public int getReadTimeout() {
      return readTimeout;
    }

    public boolean isProxyProtocol() {
      return proxyProtocol;
    }

    public void setProxyProtocol(final boolean proxyProtocol) {
      this.proxyProtocol = proxyProtocol;
    }

    public boolean isTcpFastOpen() {
      return tcpFastOpen;
    }

    public boolean isBungeePluginMessageChannel() {
      return bungeePluginMessageChannel;
    }

    public boolean isShowPingRequests() {
      return showPingRequests;
    }

    public boolean isFailoverOnUnexpectedServerDisconnect() {
      return failoverOnUnexpectedServerDisconnect;
    }

    public boolean isAnnounceProxyCommands() {
      return announceProxyCommands;
    }

    public boolean isLogCommandExecutions() {
      return logCommandExecutions;
    }

    public boolean isAcceptTransfers() {
      return this.acceptTransfers;
    }

    public boolean isEnableReusePort() {
      return enableReusePort;
    }

    public boolean isAllowIllegalCharactersInChat() {
      return allowIllegalCharactersInChat;
    }

    public String getServerBrand() {
      return this.serverBrandAsString;
    }

    public String getFallbackVersionPing() {
      return this.fallbackVersionPingAsString;
    }

    public boolean getAlwaysFallBackPing() {
      return this.alwaysFallBackPing;
    }

    public String getProxyBrandCustom() {
      return this.proxyBrandCustom;
    }

    public String getBackendBrandCustom() {
      return this.backendBrandCustom;
    }

    @Override
    public String toString() {
      return "Advanced{"
          + "compressionThreshold=" + compressionThreshold
          + ", compressionLevel=" + compressionLevel
          + ", loginRatelimit=" + loginRatelimit
          + ", connectionTimeout=" + connectionTimeout
          + ", readTimeout=" + readTimeout
          + ", proxyProtocol=" + proxyProtocol
          + ", tcpFastOpen=" + tcpFastOpen
          + ", bungeePluginMessageChannel=" + bungeePluginMessageChannel
          + ", showPingRequests=" + showPingRequests
          + ", failoverOnUnexpectedServerDisconnect=" + failoverOnUnexpectedServerDisconnect
          + ", announceProxyCommands=" + announceProxyCommands
          + ", logCommandExecutions=" + logCommandExecutions
          + ", acceptTransfers=" + acceptTransfers
          + ", enableReusePort=" + enableReusePort
          + ", allowIllegalCharactersInChat=" + allowIllegalCharactersInChat
          + '}';
    }
  }

  private static final class Query {

    @Expose
    private boolean queryEnabled = false;
    @Expose
    private int queryPort = 25565;
    @Expose
    private String queryMap = "Velocity";
    @Expose
    private boolean showPlugins = false;

    private Query() {
    }

    private Query(final boolean queryEnabled, final int queryPort, final String queryMap, final boolean showPlugins) {
      this.queryEnabled = queryEnabled;
      this.queryPort = queryPort;
      this.queryMap = queryMap;
      this.showPlugins = showPlugins;
    }

    private Query(final CommentedConfig config) {
      if (config != null) {
        this.queryEnabled = config.getOrElse("enabled", false);
        this.queryPort = config.getIntOrElse("port", 25565);
        this.queryMap = config.getOrElse("map", "Velocity");
        this.showPlugins = config.getOrElse("show-plugins", false);
      }
    }

    public boolean isQueryEnabled() {
      return queryEnabled;
    }

    public int getQueryPort() {
      return queryPort;
    }

    public String getQueryMap() {
      return queryMap;
    }

    public boolean shouldQueryShowPlugins() {
      return showPlugins;
    }

    @Override
    public String toString() {
      return "Query{"
          + "queryEnabled=" + queryEnabled
          + ", queryPort=" + queryPort
          + ", queryMap='" + queryMap + '\''
          + ", showPlugins=" + showPlugins
          + '}';
    }
  }

  /**
   * Configuration for metrics.
   */
  public static final class Metrics {

    private boolean enabled = true;

    private Metrics(final CommentedConfig toml) {
      if (toml != null) {
        this.enabled = toml.getOrElse("enabled", true);
      }
    }

    public boolean isEnabled() {
      return enabled;
    }
  }

  /**
   * Redis configuration settings for the Velocity proxy.
   *
   * <p>This class provides the configuration options required to establish a connection
   * to a Redis server.
   * It supports settings for connection details (host, port),
   * authentication, SSL usage, and connection management parameters.
   * These settings
   * enable the Velocity proxy to leverage Redis for features such as data caching,
   * synchronization across multiple instances, and custom proxy functionalities.
   *
   * <p>The {@code Redis} configuration class includes options for:
   * <ul>
   * <li>Basic connection parameters, such as {@code host} and {@code port},
   * which specify the target Redis server.</li>
   * <li>Authentication details, including {@code username} and {@code password},
   * which are optional depending on the server's security configuration.</li>
   * <li>SSL support through {@code useSsl} for secure connections, especially
   * recommended for public or cloud-hosted Redis servers.</li>
   * <li>Connection management settings, such as {@code maxConcurrentConnections},
   * that control the number of parallel connections allowed.</li>
   * <li>Health check intervals via {@code pingIntervalMs} and timeout settings
   * for identifying unresponsive Redis connections or proxies.</li>
   * </ul>
   * Example usage might include using Redis to synchronize player data, manage
   * distributed cache, or coordinate proxy configurations in a multi-instance environment.
   */
  public static final class Redis {
    @Expose
    private boolean enabled;
    @Expose
    private String host;
    @Expose
    private int port;
    @Expose
    private @Nullable String username;
    @Expose
    private String password;
    @Expose
    private boolean useSsl;
    @Expose
    private int maxConcurrentConnections;
    @Expose
    private @Nullable String proxyId;

    private Redis(final CommentedConfig config) {
      if (config == null) {
        return;
      }

      this.enabled = config.getOrElse("enabled", false);
      this.host = config.getOrElse("host", "127.0.0.1");
      this.port = config.getOrElse("port", 6379);
      this.username = config.getOrElse("username", "");

      if (this.username.isEmpty()) {
        this.username = null;
      }

      this.password = config.get("password");
      this.useSsl = config.getOrElse("use-ssl", true);
      this.maxConcurrentConnections = config.getOrElse("max-concurrent-connections", 10);

      this.proxyId = config.get("proxy-id");

      if (this.proxyId == null || this.proxyId.isEmpty()) {
        this.proxyId = null;
      }
    }

    public boolean isEnabled() {
      return enabled;
    }

    public String getHost() {
      return host;
    }

    public int getPort() {
      return port;
    }

    public @Nullable String getUsername() {
      return username;
    }

    public String getPassword() {
      return password;
    }

    public boolean isUseSsl() {
      return useSsl;
    }

    public int getMaxConcurrentConnections() {
      return maxConcurrentConnections;
    }

    public @Nullable String getProxyId() {
      return proxyId;
    }

    @Override
    public String toString() {
      return "Redis{"
          + "enabled=" + enabled
          + ", host=" + host
          + ", port=" + port
          + ", username=" + username
          // password excluded for security
          + ", useSsl" + useSsl
          + ", maxConcurrentConnections" + maxConcurrentConnections
          + '}';
    }
  }

  /**
   * Queue configuration data.
   */
  public static final class Queue {
    @Expose
    private boolean enabled;
    @Expose
    private List<String> noQueueServers;
    @Expose
    private boolean allowMultiQueue;
    @Expose
    private double sendDelay;
    @Expose
    private double queueDelay;
    @Expose
    private double messageDelay;
    @Expose
    private double backendPingInterval;
    @Expose
    private int maxSendRetries;
    @Expose
    private boolean removePlayerOnServerSwitch;
    @Expose
    private boolean forwardKickReason;
    @Expose
    private boolean allowPausedQueueJoining;
    @Expose
    private boolean queueOnShutdown;
    @Expose
    private boolean overrideBungeeMessaging;
    @Expose
    private List<String> leaveQueueAliases;
    @Expose
    private List<String> queueAdminAliases;
    private List<String> masterProxyIds;
    private List<String> bannedReason;

    private Queue(final CommentedConfig config) {
      if (config == null) {
        return;
      }

      this.enabled = config.getOrElse("enabled", false);
      this.noQueueServers = config.getOrElse("no-queue-servers", List.of());
      this.allowMultiQueue = config.getOrElse("allow-multi-queue", false);
      this.sendDelay = config.getOrElse("send-delay", 1.0);
      this.queueDelay = config.getOrElse("queue-delay", 0.0);
      this.messageDelay = config.getOrElse("message-delay", 1.0);
      this.backendPingInterval = config.getOrElse("backend-ping-interval", 1.0);
      this.maxSendRetries = config.getOrElse("max-send-retries", 10);
      this.removePlayerOnServerSwitch = config.getOrElse("remove-player-on-server-switch", true);
      this.forwardKickReason = config.getOrElse("forward-kick-reason", true);
      this.allowPausedQueueJoining = config.getOrElse("allow-paused-queue-joining", false);
      this.queueOnShutdown = config.getOrElse("queue-on-shutdown", true);
      this.overrideBungeeMessaging = config.getOrElse("override-bungee-messaging", true);
      this.leaveQueueAliases = config.getOrElse("leave-queue-aliases", new ArrayList<>());
      this.queueAdminAliases = config.getOrElse("queue-admin-aliases", new ArrayList<>());
      this.masterProxyIds = config.getOrElse("master-proxy-ids", new ArrayList<>());
      this.bannedReason = config.getOrElse("banned-reason", new ArrayList<>());
    }

    public boolean isEnabled() {
      return enabled;
    }

    public boolean isQueueOnShutdown() {
      return queueOnShutdown;
    }

    public boolean isAllowPausedQueueJoining() {
      return allowPausedQueueJoining;
    }

    public List<String> getBannedReason() {
      return this.bannedReason;
    }

    public boolean isForwardKickReason() {
      return forwardKickReason;
    }

    public boolean isRemovePlayerOnServerSwitch() {
      return removePlayerOnServerSwitch;
    }

    public int getMaxSendRetries() {
      return maxSendRetries;
    }

    public double getMessageDelay() {
      return messageDelay;
    }

    public double getSendDelay() {
      return sendDelay;
    }

    public double getQueueDelay() {
      return this.queueDelay;
    }

    public double getBackendPingInterval() {
      return backendPingInterval;
    }

    public boolean isAllowMultiQueue() {
      return allowMultiQueue;
    }

    public List<String> getNoQueueServers() {
      return noQueueServers;
    }

    public boolean shouldOverrideBungeeMessaging() {
      return overrideBungeeMessaging;
    }

    public List<String> getLeaveQueueAliases() {
      return leaveQueueAliases;
    }

    public List<String> getQueueAdminAliases() {
      return queueAdminAliases;
    }

    public List<String> getMasterProxyIds() {
      return masterProxyIds;
    }

    @Override
    public String toString() {
      return "Queue{"
          + "enabled=" + enabled
          + ", allowPausedQueueJoining=" + allowPausedQueueJoining
          + ", forwardKickReason=" + forwardKickReason
          + ", removePlayerOnServerSwitch=" + removePlayerOnServerSwitch
          + ", maxSendRetries=" + maxSendRetries
          + ", messageDelay=" + messageDelay
          + ", sendDelay=" + sendDelay
          + ", queueDelay=" + queueDelay
          + ", allowMultiQueue=" + allowMultiQueue
          + ", noQueueServers=" + noQueueServers
          + ", overrideBungeeMessaging=" + overrideBungeeMessaging
          + ", leaveQueueAliases=" + leaveQueueAliases
          + ", queueAdminAliases=" + queueAdminAliases
          + ", masterProxyIds=" + masterProxyIds
          + '}';
    }
  }
}
