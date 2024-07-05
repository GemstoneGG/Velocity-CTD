/*
 * Copyright (C) 2024 Velocity Contributors
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
 * This event is executed when a player is about to enter the configuration state.
 * It is <b>not</b> called for the initial configuration of a player after login,
 * because no backed server connection has been established yet.
 * In that case, as soon as a server connection is established and has entered configuration
 * state, the {@link PlayerEnteredConfigurationEvent} is fired.
 * <p>Velocity will wait for this event before asking the client to enter configuration state.</p>
 *
 * @param player The player who is about to enter configuration state.
 * @param server The server that wants to reconfigure the player.
 * @since 3.3.0
 * @sinceMinecraft 1.20.2
 */
@AwaitingEvent
public record PlayerEnterConfigurationEvent(@NotNull Player player, ServerConnection server) {
}
