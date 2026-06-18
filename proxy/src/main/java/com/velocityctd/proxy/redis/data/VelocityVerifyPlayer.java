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

package com.velocityctd.proxy.redis.data;

import com.velocityctd.proxy.redis.transaction.TransactionData;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * Data record representing a request asking the proxy identified by {@code proxyId} whether it
 * still has the given player connected.
 *
 * @param uniqueId the player's unique ID
 * @param proxyId  the id of the proxy the entry claims the player is connected to
 */
public record VelocityVerifyPlayer(UUID uniqueId, String proxyId) implements TransactionData<Boolean> {

  @Override
  public @NotNull Class<Boolean> responseClass() {
    return Boolean.class;
  }
}
