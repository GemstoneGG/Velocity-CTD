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

package com.velocitypowered.proxy.connection.forge.legacy;

import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.config.PlayerInfoForwarding;
import com.velocitypowered.proxy.connection.ConnectionTypes;
import com.velocitypowered.proxy.connection.util.ConnectionTypeImpl;

/**
 * Contains extra logic for {@link ConnectionTypes#LEGACY_FORGE}.
 */
public class LegacyForgeConnectionType extends ConnectionTypeImpl {

  /**
   * A static {@link GameProfile.Property} used to indicate that a player is using a Forge client.
   * This is used in legacy forwarding scenarios where the FML token cannot be sent directly
   * via the handshake hostname field.
   */
  private static final GameProfile.Property IS_FORGE_CLIENT_PROPERTY = new GameProfile.Property("forgeClient", "true", "");

  /**
   * Constructs a new {@code LegacyForgeConnectionType} instance.
   *
   * <p>Initializes the connection phase tracking with default handshake states for legacy Forge.</p>
   */
  public LegacyForgeConnectionType() {
    super(LegacyForgeHandshakeClientPhase.NOT_STARTED, LegacyForgeHandshakeBackendPhase.NOT_STARTED);
  }

  @Override
  public final GameProfile addGameProfileTokensIfRequired(final GameProfile original,
                                                    final PlayerInfoForwarding forwardingType) {
    // We can't forward the FML token to the server when we are running in legacy forwarding mode,
    // since both use the "hostname" field in the handshake. We add a special property to the
    // profile instead, which will be ignored by non-Forge servers and can be intercepted by a
    // Forge coremod, such as SpongeForge.
    if (forwardingType == PlayerInfoForwarding.LEGACY) {
      return original.addProperty(IS_FORGE_CLIENT_PROPERTY);
    }

    return original;
  }
}
