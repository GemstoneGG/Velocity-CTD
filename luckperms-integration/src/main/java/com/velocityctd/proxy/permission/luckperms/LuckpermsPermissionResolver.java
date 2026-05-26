/*
 * Copyright (C) 2026 Velocity-CTD Contributors
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

package com.velocityctd.proxy.permission.luckperms;

import com.velocityctd.api.permission.PermissionResolver;
import com.velocitypowered.api.permission.PermissionFunction;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import java.util.Map;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.Unmodifiable;

/**
 * An implementation of {@link PermissionResolver} that delegates {@link #getPermissionValue(String)}
 * to LuckPerm's {@link PermissionFunction#getPermissionValue(String)} implementation, and provides more
 * advanced permission methods through the use of the LuckPerms API.
 */
public final class LuckpermsPermissionResolver implements PermissionResolver {

  private final Player player;
  private final PermissionFunction delegate;

  /**
   * Instantiates a {@link LuckpermsPermissionResolver}.
   *
   * @param player the Velocity player
   * @param delegate the LuckPerms PermissionFunction delegate. It's expected, though not required, that this
   *                 is a {@code me.lucko.luckperms.velocity.service.PlayerPermissionProvider}.
   */
  LuckpermsPermissionResolver(Player player, PermissionFunction delegate) {
    this.player = player;
    this.delegate = delegate;
  }

  @Override
  public @NonNull Tristate getPermissionValue(String permission) {
    return delegate.getPermissionValue(permission);
  }

  @Override
  public @NonNull @Unmodifiable Map<String, Boolean> getPermissionMap() {
    LuckPerms api = LuckPermsProvider.get();
    User user = api.getPlayerAdapter(Player.class).getUser(player);

    QueryOptions queryOptions = api.getContextManager().getQueryOptions(player);
    return user.getCachedData().getPermissionData(queryOptions).getPermissionMap();
  }
}
