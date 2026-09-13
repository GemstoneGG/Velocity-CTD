/*
 * Copyright (C) 2018-2026 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.event.player.configuration;

import com.velocitypowered.api.event.annotation.AwaitingEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import org.jetbrains.annotations.NotNull;

/**
 * This event is executed when Velocity starts the backend server's configuration state for a player.
 *
 * <p>Velocity will wait for this event before processing backend configuration packets.</p>
 *
 * @param player The player whose backend connection is being configured.
 * @param server The backend server currently configuring the connection.
 * @since Minecraft 1.20.2
 */
@AwaitingEvent
public record PlayerBackendConfigurationEvent(@NotNull Player player, ServerConnection server) {
}
