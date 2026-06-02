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

import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.DynamicFallbackFilter;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.config.VelocityConfiguration.ForcedHostEntry;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the resolved fallback server configuration for an inbound connection, including the ordered
 * list of backend servers to try, the applicable dynamic fallback filter, the connection's virtual
 * host, and the forced-host pattern that was matched (if any).
 *
 * <p>Use {@link #resolveFallbackServers(VelocityServer, InboundConnection)} to obtain an instance.
 *
 * @param serversToTry               the ordered list of backend server names to attempt
 * @param dynamicFallbackFilter      the dynamic fallback filter to apply when selecting a server
 * @param virtualHost                the lowercase virtual host from the connection, or {@code null}
 *                                   if none could be found
 * @param matchedVirtualHostPattern  the forced-host pattern (exact or wildcard) that was matched,
 *                                   or {@code null} if the default connection order is used
 */
public record FallbackServers(
    @NotNull List<String> serversToTry,
    @NotNull DynamicFallbackFilter dynamicFallbackFilter,
    @Nullable String virtualHost,
    @Nullable String matchedVirtualHostPattern
) {

  /**
   * Builds an ordered deque of server names to attempt for this fallback configuration.
   *
   * <p>The order is determined by {@link #dynamicFallbackFilter()}:
   * <ul>
   *   <li>{@link DynamicFallbackFilter#FIRST_AVAILABLE} -> preserves the configured order as-is.</li>
   *   <li>{@link DynamicFallbackFilter#LEAST_POPULATED} -> sorts servers ascending by current player count.</li>
   *   <li>{@link DynamicFallbackFilter#MOST_POPULATED} -> sorts servers descending by current player count.</li>
   * </ul>
   * Servers that are not registered in {@code server} are treated as having zero players.
   *
   * @param server the Velocity server instance used to look up current player counts
   * @return a deque of server names in the order they should be tried
   */
  public Deque<String> calculateRetryDeque(VelocityServer server) {
    List<String> retryList = new ArrayList<>(serversToTry);

    switch (dynamicFallbackFilter) {
      case FIRST_AVAILABLE -> {
        // nop
      }
      case MOST_POPULATED, LEAST_POPULATED -> {
        Map<String, Integer> playerCounts = calculatePlayerCountMap(server, retryList);
        Comparator<String> comparator = Comparator.comparingInt(playerCounts::get);
        if (dynamicFallbackFilter == DynamicFallbackFilter.MOST_POPULATED) {
          comparator = comparator.reversed();
        }

        retryList.sort(comparator);
      }
      default -> throw new IllegalStateException("Unknown dynamic fallback filter " + dynamicFallbackFilter + ".");
    }

    return new ArrayDeque<>(retryList);
  }

  /**
   * Builds a map from the server name to the current player count for the given set of server names.
   * Servers not found in {@code server} are mapped to {@code 0}.
   *
   * @param server      the Velocity server instance used to look up registered servers
   * @param serverNames the names of the servers to include in the map
   * @return a map of server name to player count
   */
  private static Map<String, Integer> calculatePlayerCountMap(VelocityServer server, Collection<String> serverNames) {
    Map<String, Integer> result = new HashMap<>(serverNames.size());
    for (String serverName : serverNames) {
      int playerCount = server.getServer(serverName)
          .map(s -> (int) s.getTotalPlayerCount())
          .orElse(0);

      result.put(serverName, playerCount);
    }

    return result;
  }

  /**
   * Looks up a forced-host entry for the given {@code virtualHost}, trying an exact
   * case-insensitive match first, then a wildcard match (e.g. {@code *.example.com}).
   *
   * @return the matching {@link FallbackServers}, or {@link Optional#empty()} if no rule matches
   *     (or the matched rule opted out of fallback behavior)
   */
  private static Optional<FallbackServers> getForcedHostFallbacks(VelocityConfiguration config, String virtualHost) {
    Map<String, ForcedHostEntry> forcedHosts = config.getForcedHostEntries();
    ForcedHostEntry exactMatch = forcedHosts.get(virtualHost);
    if (exactMatch != null) {
      return forcedHostFallbacks(config, virtualHost, virtualHost, exactMatch);
    }

    // Check for wildcard ("*.example.com" matches "anything.example.com")
    for (Map.Entry<String, ForcedHostEntry> entry : forcedHosts.entrySet()) {
      String pattern = entry.getKey().toLowerCase(Locale.ROOT);
      if (pattern.startsWith("*.") && virtualHost.endsWith(pattern.substring(1))) {
        return forcedHostFallbacks(config, virtualHost, entry.getKey(), entry.getValue());
      }
    }

    return Optional.empty();
  }

  /**
   * Wraps a matched forced-host entry as {@link FallbackServers}, honoring the entry's
   * {@code forced-host-as-fallback} option. When that option is disabled the forced host is not
   * used as a fallback chain and {@link Optional#empty()} is returned so the caller falls back to
   * the global {@code attempt-connection-order}.
   */
  private static Optional<FallbackServers> forcedHostFallbacks(VelocityConfiguration config,
                                                               String virtualHost, String pattern, ForcedHostEntry entry) {
    if (!entry.isForcedHostAsFallback()) {
      return Optional.empty();
    }

    return Optional.of(new FallbackServers(
        entry.getServers(),
        Optional.ofNullable(entry.getDynamicFallbackFilter())
            .orElseGet(config::getDynamicFallbackFilter),
        virtualHost,
        pattern.toLowerCase(Locale.ROOT)
    ));
  }

  /**
   * Resolves the fallback server configuration for an inbound connection.
   *
   * <p>If the connection supplies a virtual host that matches a forced-host rule (exact or
   * wildcard), the servers and filter from that rule are used. Otherwise, the global
   * {@code attempt-connection-order} and dynamic fallback filter from the proxy configuration
   * are used, with {@link #matchedVirtualHostPattern()} left as {@code null}.
   *
   * @param server     the Velocity server instance providing configuration access
   * @param connection the inbound connection whose virtual host is used for forced-host resolution
   * @return the resolved {@link FallbackServers} for this connection
   */
  public static FallbackServers resolveFallbackServers(VelocityServer server, InboundConnection connection) {
    String virtualHost = connection.getVirtualHost()
        .map(InetSocketAddress::getHostString)
        .map(host -> host.toLowerCase(Locale.ROOT))
        .orElse(null);

    if (virtualHost != null) {
      FallbackServers fromVirtualHost = getForcedHostFallbacks(server.getConfiguration(), virtualHost).orElse(null);
      if (fromVirtualHost != null) {
        return fromVirtualHost;
      }
    }

    return new FallbackServers(
        server.getConfiguration().getAttemptConnectionOrder(),
        server.getConfiguration().getDynamicFallbackFilter(),
        virtualHost,
        null
    );
  }
}
