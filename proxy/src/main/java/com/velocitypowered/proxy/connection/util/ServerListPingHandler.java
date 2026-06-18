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

package com.velocitypowered.proxy.connection.util;

import com.github.f4b6a3.uuid.UuidCreator;
import com.spotify.futures.CompletableFutures;
import com.velocityctd.proxy.util.ComponentUtils;
import com.velocityctd.proxy.util.PlaceholderSubstitutor;
import com.velocityctd.proxy.util.SamplePlayersPicker;
import com.velocityctd.proxy.util.SamplePlayersPlaceholderResolver;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.util.ModInfo;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.PingPassthroughMode;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

/**
 * Common utilities for handling server list ping results.
 */
public class ServerListPingHandler {

  private final VelocityServer server;

  public ServerListPingHandler(VelocityServer server) {
    this.server = server;
  }

  private boolean displayFallbackPing(ProtocolVersion clientVersion) {
    String minVersion = server.getConfiguration().getMinimumVersion();
    ProtocolVersion minimumVersion = ProtocolVersion.getVersionByName(minVersion);
    ProtocolVersion maximumVersion = server.getConfiguration().getMaximumVersion()
        .map(ProtocolVersion::getVersionByName)
        .orElse(ProtocolVersion.MAXIMUM_VERSION);

    return clientVersion.lessThan(minimumVersion) || clientVersion.greaterThan(maximumVersion);
  }

  private ServerPing constructLocalPing(ProtocolVersion clientVersion) {
    VelocityConfiguration configuration = server.getConfiguration();
    boolean outOfRange = displayFallbackPing(clientVersion);

    ProtocolVersion displayVersion =
        (clientVersion == ProtocolVersion.UNKNOWN || outOfRange)
            ? ProtocolVersion.MAXIMUM_VERSION
            : clientVersion;

    // When forcing a mismatch, prefer displayVersion's protocol so the client still shows an
    // informative "Client out of date, update to X" label. Fall back to LEGACY (-2) only when
    // displayVersion coincides with the client's protocol (e.g., a client on the proxy's
    // actual maximum while the maximum-version is configured lower), since that would otherwise
    // collapse into a normal online state.
    boolean forceMismatch = configuration.isAlwaysFallBackPing()
        || clientVersion == ProtocolVersion.UNKNOWN
        || outOfRange;
    int responseProtocol;
    if (forceMismatch) {
      int candidate = displayVersion.getProtocol();
      responseProtocol = candidate != clientVersion.getProtocol()
          ? candidate
          : ProtocolVersion.LEGACY.getProtocol();
    } else {
      responseProtocol = clientVersion.getProtocol();
    }

    PlaceholderSubstitutor.Resolver basicResolver = new ServerListPingPlaceholderResolver(displayVersion);

    SamplePlayersPicker sharedPicker = configuration.isPoolPlayersAcrossSections() ? SamplePlayersPicker.create(server) : null;

    List<String> motd = PlaceholderSubstitutor.substitute(configuration.getMotdLines(), basicResolver,
            samplePlayersResolver(sharedPicker, 8, 4, "None", ", "));

    List<String> motdHover = PlaceholderSubstitutor.substitute(configuration.getMotdHoverLines(), basicResolver,
            samplePlayersResolver(sharedPicker, 12, 1, "", ""));
    if (motdHover.size() == 1 && motdHover.getFirst().isEmpty()) {
      motdHover.clear();
    }

    String versionName = PlaceholderSubstitutor.substitute(configuration.getFallbackVersionPing(), basicResolver,
            samplePlayersResolver(sharedPicker, 2, Integer.MAX_VALUE, "None", ", "));

    return new ServerPing(
        new ServerPing.Version(
            responseProtocol,
            versionName),
        new ServerPing.Players(
            server.getClusterPlayerService().getTotalPlayerCount(),
            configuration.getShowMaxPlayers(),
            motdHover.stream()
                .map(ComponentUtils::parse)
                .map(line -> new ServerPing.SamplePlayer(line, UuidCreator.getTimeOrderedEpochFast()))
                .toList()
        ),
        motd.stream()
            .map(ComponentUtils::parse)
            .reduce((a, b) -> a.appendNewline().append(b))
            .orElseGet(Component::empty),
        configuration.getFavicon().orElse(null),
        configuration.isAnnounceForge() ? ModInfo.DEFAULT : null,
        configuration.doesPreventChatReports()
    );
  }

  private SamplePlayersPlaceholderResolver samplePlayersResolver(
          @Nullable SamplePlayersPicker sharedPicker, int defaultMax,
          int defaultMaxPerLine, String defaultEmpty, String defaultSeparator) {
    SamplePlayersPicker picker = sharedPicker != null ? sharedPicker : SamplePlayersPicker.create(server);
    return SamplePlayersPlaceholderResolver.builder(picker)
        .defaultMax(defaultMax)
        .defaultMaxPerLine(defaultMaxPerLine)
        .defaultEmpty(defaultEmpty)
        .defaultSeparator(defaultSeparator)
        .ignoreAnonymousPlayerRequest(server.getConfiguration().isIgnoreAnonymousPlayerRequest())
        .build();
  }

  private CompletableFuture<ServerPing> attemptPingPassthrough(VelocityInboundConnection connection,
                                                               PingPassthroughMode mode, List<String> servers,
                                                               ProtocolVersion responseProtocolVersion,
                                                               String virtualHostStr) {
    ServerPing fallback = constructLocalPing(connection.getProtocolVersion());
    List<CompletableFuture<ServerPing>> pings = new ArrayList<>();
    for (String s : servers) {
      Optional<VelocityRegisteredServer> rs = server.getServer(s);
      if (rs.isEmpty()) {
        continue;
      }

      VelocityRegisteredServer vrs = rs.get();
      pings.add(vrs.ping(connection.getConnection().eventLoop(), PingOptions.builder()
          .version(responseProtocolVersion).virtualHost(virtualHostStr).build()));
    }

    if (pings.isEmpty()) {
      return CompletableFuture.completedFuture(fallback);
    }

    CompletableFuture<List<ServerPing>> pingResponses = CompletableFutures.successfulAsList(pings,
        (ex) -> fallback);
    return switch (mode) {
      case ALL -> pingResponses.thenApply(responses -> {
        // Find the first non-fallback
        for (ServerPing response : responses) {
          if (response == fallback) {
            continue;
          }

          if (response.getDescriptionComponent() == null) {
            return response.asBuilder()
                .description(Component.empty())
                .build();
          }

          return response;
        }

        return fallback;
      });
      case MODS -> pingResponses.thenApply(responses -> {
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
      case DESCRIPTION -> pingResponses.thenApply(responses -> {
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
              response.getModinfo().orElse(null),
              fallback.getPreventsChatReports()
          );
        }
        return fallback;
      });
      // Not possible, but covered for completeness.
      default -> CompletableFuture.completedFuture(fallback);
    };
  }

  /**
   * Fetches the "default" server ping for a player.
   *
   * @param connection the connection
   * @return a future with the initial ping result
   */
  public CompletableFuture<ServerPing> getInitialPing(VelocityInboundConnection connection) {
    VelocityConfiguration configuration = server.getConfiguration();
    ProtocolVersion shownVersion = connection.getProtocolVersion().isSupported()
        ? connection.getProtocolVersion() : ProtocolVersion.MAXIMUM_VERSION;
    PingPassthroughMode passthroughMode = configuration.getPingPassthrough();

    if (passthroughMode == PingPassthroughMode.DISABLED) {
      return CompletableFuture.completedFuture(constructLocalPing(connection.getProtocolVersion()));
    } else {
      FallbackServers fallbackServers = FallbackServers.resolveFallbackServers(server, connection);

      return attemptPingPassthrough(connection, passthroughMode, fallbackServers.serversToTry(),
          shownVersion, fallbackServers.virtualHost());
    }
  }

  private class ServerListPingPlaceholderResolver implements PlaceholderSubstitutor.Resolver {

    private final ProtocolVersion displayVersion;

    private ServerListPingPlaceholderResolver(ProtocolVersion displayVersion) {
      this.displayVersion = displayVersion;
    }

    @Override
    public @Nullable String resolve(String name, Map<String, String> arguments) {
      return switch (name) {
        case "protocol-min" -> ProtocolVersion.getVersionByName(
            server.getConfiguration().getMinimumVersion()).getVersionIntroducedIn();
        case "protocol-max" -> server.getConfiguration().getMaximumVersion()
            .orElse(ProtocolVersion.MAXIMUM_VERSION.getMostRecentSupportedVersion());
        case "protocol" -> displayVersion.getVersionIntroducedIn();
        case "proxy-brand" -> server.getVersion().getName();
        case "proxy-brand-custom" -> server.getConfiguration().getProxyBrandCustom();
        case "proxy-version" -> server.getVersion().getVersion();
        case "proxy-vendor" -> server.getVersion().getVendor();
        case "player-count" -> String.valueOf(server.getClusterPlayerService().getTotalPlayerCount());
        case "max-players" -> String.valueOf(server.getConfiguration().getShowMaxPlayers());
        default -> null;
      };
    }
  }
}
