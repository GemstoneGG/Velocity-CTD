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

package com.velocityctd.proxy.cluster.local;

import com.velocityctd.proxy.cluster.ClusterProxyService;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.proxy.VelocityServer;
import java.util.Collection;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Local (single-proxy) implementation of {@link ClusterProxyService}.
 */
public final class LocalClusterProxyService implements ClusterProxyService {

  private final VelocityServer server;

  public LocalClusterProxyService(final VelocityServer server) {
    this.server = server;
  }

  @Override
  public Collection<String> getAllProxyIds() {
    return List.of(getSelfProxyId());
  }

  @Override
  public String getSelfProxyId() {
    return this.server.getProxyId();
  }

  @Override
  public boolean isMultiProxy() {
    return false;
  }

  @Override
  public void reloadProxy(final CommandSource source, final String proxyId) {
    try {
      if (this.server.reloadConfiguration()) {
        source.sendMessage(Component.translatable("velocity.command.reload-success",
            NamedTextColor.GREEN));
      } else {
        source.sendMessage(Component.translatable("velocity.command.reload-failure",
            NamedTextColor.RED));
      }
    } catch (Exception e) {
      source.sendMessage(Component.translatable("velocity.command.reload-failure",
          NamedTextColor.RED));
    }
  }

  @Override
  public void queryProxyUptime(final CommandSource source, final String proxyId) {
    // Delegate to local uptime - handled in the command itself
  }
}
