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

import com.velocitypowered.api.proxy.server.PlayerInfoForwarding;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.proxy.util.AddressUtil;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.ValueConversionException;
import joptsimple.ValueConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Holds parsed command line options.
 */
public final class ProxyOptions {

  private static final Logger LOGGER = LogManager.getLogger(ProxyOptions.class);

  private final boolean help;

  private final @Nullable Integer port;

  private final @Nullable Boolean haproxy;

  private final boolean ignoreConfigServers;

  private final List<ServerInfo> servers;

  /**
   * List of additional plugin jars specified via {@code --add-plugin} or {@code --add-extra-plugin-jar}.
   */
  private final List<String> additionalPlugins;

  ProxyOptions(String[] args) {
    OptionParser parser = new OptionParser();

    OptionSpec<Void> help = parser.acceptsAll(Arrays.asList("h", "help"), "Print help")
        .forHelp();
    OptionSpec<Integer> port = parser.acceptsAll(Arrays.asList("p", "port"),
            "Specify the bind port to be used. The configuration bind port will be ignored.")
        .withRequiredArg().ofType(Integer.class);
    OptionSpec<Boolean> haproxy = parser.acceptsAll(
            Arrays.asList("haproxy", "haproxy-protocol"),
            "Choose whether to enable haproxy protocol. "
                    + "The configuration haproxy protocol will be ignored.")
        .withRequiredArg().ofType(Boolean.class);
    OptionSpec<ServerInfo> servers = parser.accepts("add-server",
            "Define a server mapping. "
                    + "You must ensure that server name is not also registered in the config or use --ignore-config-servers.")
        .withRequiredArg().withValuesConvertedBy(new ServerInfoConverter());
    OptionSpec<Void> ignoreConfigServers = parser.accepts("ignore-config-servers",
            "Skip registering servers from the config file. "
                    + "Useful in dynamic setups or with the --add-server flag.");
    OptionSpec<String> additionalPlugins = parser.acceptsAll(Arrays.asList("add-plugin", "add-extra-plugin-jar"),
            "Specify paths to extra plugin jars to be loaded in addition to those in the plugins folder. "
                    + "This argument can be specified multiple times, once for each extra plugin jar path."
            ).withRequiredArg().ofType(String.class);
    OptionSet set = parser.parse(args);

    this.help = set.has(help);
    this.port = port.value(set);
    this.haproxy = haproxy.value(set);
    this.servers = servers.values(set);
    this.ignoreConfigServers = set.has(ignoreConfigServers);
    this.additionalPlugins = additionalPlugins.values(set);

    if (this.help) {
      try {
        parser.printHelpOn(System.out);
      } catch (IOException e) {
        LOGGER.error("Could not print help", e);
      }
    }
  }

  boolean isHelp() {
    return this.help;
  }

  public @Nullable Integer getPort() {
    return this.port;
  }

  public @Nullable Boolean isHaproxy() {
    return this.haproxy;
  }

  public boolean isIgnoreConfigServers() {
    return this.ignoreConfigServers;
  }

  public List<ServerInfo> getServers() {
    return this.servers;
  }

  /**
   * Returns the list of additional plugin jars specified via {@code --add-plugin}.
   *
   * @return the list of extra plugin jar paths
   */
  public List<String> getAdditionalPlugins() {
    return this.additionalPlugins;
  }

  private static final class ServerInfoConverter implements ValueConverter<ServerInfo> {

    @Override
    public ServerInfo convert(String s) {
      String[] split = s.split(":", 2);
      if (split.length < 2) {
        throw new ValueConversionException("Invalid server format. Use <name>:<host>:[port]:[forwardingmode]:[minimumversion]:[maximumversion]");
      }

      InetSocketAddress address;
      PlayerInfoForwarding mode = null;
      try {
        if (split.length >= 3) {
          address = AddressUtil.parseAddress(split[1] + ":" + split[2]);
        } else {
          address = AddressUtil.parseAddress(split[1]);
        }
      } catch (IllegalStateException e) {
        throw new ValueConversionException("Invalid hostname for server flag with name: " + split[0]);
      }

      if (split.length == 4) {
        try {
          mode = PlayerInfoForwarding.valueOf(split[3].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
          throw new ValueConversionException("Invalid forwarding mode for server flag with name: " + split[0]);
        }
      }

      return new ServerInfo(split[0], address, mode);
    }

    @Override
    public Class<? extends ServerInfo> valueType() {
      return ServerInfo.class;
    }

    @Override
    public String valuePattern() {
      return "name>:<host>:[port]:[forwardingmode]:[minimumversion]:[maximumversion]";
    }
  }
}
