/*
 * Copyright (C) 2018-2025 Velocity Contributors
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

package com.velocitypowered.proxy.connection.util;

import com.spotify.futures.CompletableFutures;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.util.ModInfo;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.PingPassthroughMode;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;

/**
 * Common utilities for handling server list ping results.
 */
public class ServerListPingHandler {

  /**
   * The {@link VelocityServer} instance associated with this ping handler.
   *
   * <p>Used to retrieve configuration options, player counts, proxy version info,
   * and server data during ping construction or passthrough processing.</p>
   */
  private final VelocityServer server;

  /**
   * Constructs a new {@link ServerListPingHandler} for managing initial server list ping responses.
   *
   * <p>This utility class determines how the proxy responds to Minecraft server list pings
   * based on configuration and protocol version. It supports both static (local) and passthrough
   * ping responses.</p>
   *
   * @param server the {@link VelocityServer} instance to use for configuration and server access
   * @throws NullPointerException if {@code server} is null
   */
  public ServerListPingHandler(final VelocityServer server) {
    this.server = server;
  }

  private boolean displayFallbackPing(final ProtocolVersion clientVersion) {
    String minVersion = server.getConfiguration().getMinimumVersion();
    ProtocolVersion minimumVersion = ProtocolVersion.getVersionByName(minVersion);
    return clientVersion.lessThan(minimumVersion);
  }

  @SuppressWarnings("checkstyle:FinalParameters")
  private ServerPing constructLocalPing(ProtocolVersion version) {
    boolean fallback = displayFallbackPing(version);
    VelocityConfiguration configuration = server.getConfiguration();

    if (version == ProtocolVersion.UNKNOWN || fallback) {
      version = ProtocolVersion.MAXIMUM_VERSION;
    }

    if (configuration.getAlwaysFallBackPing()) {
      version = ProtocolVersion.LEGACY;
    }

    List<ServerPing.SamplePlayer> samplePlayers;
    if (configuration.getSamplePlayersInPing()) {
      List<ServerPing.SamplePlayer> unshuffledPlayers;
      if (server.getMultiProxyHandler().isRedisEnabled()) {
        unshuffledPlayers = server.getMultiProxyHandler().getAllPlayers().stream()
            .map(player -> new ServerPing.SamplePlayer(player.getUsername(), player.getUuid()))
            .collect(Collectors.toList());
      } else {
        unshuffledPlayers = server.getAllPlayers().stream()
            .map(player -> {
              if (player.getPlayerSettings().isClientListingAllowed()) {
                return new ServerPing.SamplePlayer(player.getUsername(), player.getUniqueId());
              } else {
                return ServerPing.SamplePlayer.ANONYMOUS;
              }
            })
            .collect(Collectors.toList());
      }

      Collections.shuffle(unshuffledPlayers);
      int limit = Math.min(12, unshuffledPlayers.size());
      samplePlayers = new ArrayList<>(unshuffledPlayers.subList(0, limit));
    } else {
      samplePlayers = new ArrayList<>();
    }

    String serverPingVersion = configuration.getFallbackVersionPing();

    final int online;
    if (server.getMultiProxyHandler().isRedisEnabled()) {
      online = server.getMultiProxyHandler().getTotalPlayerCount();
    } else {
      online = server.getPlayerCount();
    }

    for (Component s : server.getConfiguration().getMotdHover()) {
      samplePlayers.add(new ServerPing.SamplePlayer(s, UUID.randomUUID()));
    }

    return new ServerPing(
        new ServerPing.Version(version.getProtocol(), formatVersionString(serverPingVersion, version)),
        new ServerPing.Players(online, configuration.getShowMaxPlayers(), samplePlayers),
        configuration.getMotd(),
        configuration.getFavicon().orElse(null),
        configuration.isAnnounceForge() ? ModInfo.DEFAULT : null
    );
  }

  private String formatVersionString(final String raw, final ProtocolVersion version) {
    final String minVersionIntroducedIn =
        ProtocolVersion.getVersionByName(this.server.getConfiguration().getMinimumVersion()).getVersionIntroducedIn();
    return raw
        .replaceAll("\\{protocol-min}", minVersionIntroducedIn)
        .replaceAll("\\{protocol-max}", ProtocolVersion.MAXIMUM_VERSION.getMostRecentSupportedVersion())
        .replaceAll("\\{protocol}", version.getVersionIntroducedIn())
        .replaceAll("\\{proxy-brand}", this.server.getVersion().getName())
        .replaceAll("\\{proxy-brand-custom}", this.server.getConfiguration().getProxyBrandCustom())
        .replaceAll("\\{proxy-version}", this.server.getVersion().getVersion())
        .replaceAll("\\{proxy-vendor}", this.server.getVersion().getVendor())
        .replaceAll("\\{player-count}", this.server.getMultiProxyHandler().isRedisEnabled()
            ? String.valueOf(this.server.getMultiProxyHandler().getTotalPlayerCount())
            : String.valueOf(this.server.getPlayerCount()))
        .replaceAll("\\{max-players}", String.valueOf(this.server.getConfiguration().getShowMaxPlayers()));
  }

  private CompletableFuture<ServerPing> attemptPingPassthrough(final VelocityInboundConnection connection,
                                                               final PingPassthroughMode mode, final List<String> servers,
                                                               final ProtocolVersion responseProtocolVersion,
                                                               final String virtualHostStr) {
    ServerPing fallback = constructLocalPing(connection.getProtocolVersion());
    List<CompletableFuture<ServerPing>> pings = new ArrayList<>();
    for (String s : servers) {
      Optional<RegisteredServer> rs = server.getServer(s);
      if (rs.isEmpty()) {
        continue;
      }

      VelocityRegisteredServer vrs = (VelocityRegisteredServer) rs.get();
      pings.add(vrs.ping(connection.getConnection().eventLoop(), PingOptions.builder()
              .version(responseProtocolVersion).virtualHost(virtualHostStr).build()));
    }

    if (pings.isEmpty()) {
      return CompletableFuture.completedFuture(fallback);
    }

    CompletableFuture<List<ServerPing>> pingResponses = CompletableFutures.successfulAsList(pings,
        (ex) -> fallback);
    switch (mode) {
      case ALL:
        return pingResponses.thenApply(responses -> {
          // Find the first non-fallback
          for (ServerPing response : responses) {
            if (response == fallback) {
              continue;
            }
            return response;
          }

          return fallback;
        });
      case MODS:
        return pingResponses.thenApply(responses -> {
          // Find the first non-fallback that contains a mod list
          for (ServerPing response : responses) {
            if (response == fallback) {
              continue;
            }
            Optional<ModInfo> modInfo = response.getModinfo();
            if (modInfo.isPresent()) {
              return fallback.asBuilder().mods(modInfo.get()).build();
            }
          }

          return fallback;
        });
      case DESCRIPTION:
        return pingResponses.thenApply(responses -> {
          // Find the first non-fallback. If it includes a modlist, add it too.
          for (ServerPing response : responses) {
            if (response == fallback) {
              continue;
            }

            if (response.getDescriptionComponent() == null) {
              continue;
            }

            return new ServerPing(
                fallback.getVersion(),
                fallback.getPlayers().orElse(null),
                response.getDescriptionComponent(),
                fallback.getFavicon().orElse(null),
                response.getModinfo().orElse(null)
            );
          }

          return fallback;
        });
      // Not possible, but covered for completeness.
      default:
        return CompletableFuture.completedFuture(fallback);
    }
  }

  /**
   * Fetches the "default" server ping for a player.
   *
   * @param connection the connection
   * @return a future with the initial ping result
   */
  public CompletableFuture<ServerPing> getInitialPing(final VelocityInboundConnection connection) {
    VelocityConfiguration configuration = server.getConfiguration();
    ProtocolVersion shownVersion = connection.getProtocolVersion().isSupported()
        ? connection.getProtocolVersion() : ProtocolVersion.MAXIMUM_VERSION;
    PingPassthroughMode passthroughMode = configuration.getPingPassthrough();

    if (passthroughMode == PingPassthroughMode.DISABLED) {
      return CompletableFuture.completedFuture(constructLocalPing(shownVersion));
    } else {
      String virtualHostStr = connection.getVirtualHost().map(InetSocketAddress::getHostString)
          .map(str -> str.toLowerCase(Locale.ROOT))
          .orElse("");

      List<String> serversToTry = server.getConfiguration().getForcedHosts().get(virtualHostStr);
      if (serversToTry == null || serversToTry.isEmpty()) {
        for (Map.Entry<String, List<String>> entry : server.getConfiguration().getForcedHosts().entrySet()) {
          String pattern = entry.getKey().toLowerCase(Locale.ROOT);
          if (pattern.startsWith("*.") && virtualHostStr.endsWith(pattern.substring(1))) {
            serversToTry = entry.getValue();
            break;
          }
        }
      }

      if (serversToTry == null || serversToTry.isEmpty()) {
        serversToTry = server.getConfiguration().getAttemptConnectionOrder();
      }

      return attemptPingPassthrough(connection, passthroughMode, serversToTry, shownVersion, virtualHostStr);
    }
  }
}
