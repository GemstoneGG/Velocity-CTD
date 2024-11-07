/*
 * Copyright (C) 2020-2024 Velocity Contributors
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

package com.velocitypowered.proxy.queue;

import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.util.concurrent.CompletableFuture;

/**
 * Stores the status of a single server queue entry for a specific player.
 */
public class ServerQueueEntry {
  public final ConnectedPlayer player;
  public final VelocityRegisteredServer target;
  public final CompletableFuture<ConnectionRequestBuilder.Result> future;
  public int connectionAttempts = 0;
  public boolean waitingForConnection = false;

  /**
   * Constructs a new {@link ServerQueueEntry} instance.
   *
   * @param player the player who is queueing
   * @param target the target server
   * @param future a future that will be resolved when the player connects. If {@code null}, Velocity's default connection error handling will be used
   */
  public ServerQueueEntry(final ConnectedPlayer player, final VelocityRegisteredServer target,
                          final CompletableFuture<ConnectionRequestBuilder.Result> future) {
    this.player = player;
    this.target = target;
    this.future = future;
  }

  /**
   * Executes this queue entry, sending the player to their target server.
   *
   * @return a future that will complete when the player has been sent successfully.
   */
  public CompletableFuture<Boolean> send() {
    waitingForConnection = true;

    if (future == null) {
      return player.createConnectionRequest(target)
          .connectWithIndication()
          .whenComplete((success, error) -> waitingForConnection = false);
    } else {
      CompletableFuture<Boolean> returnedFuture = new CompletableFuture<>();
      player.createConnectionRequest(target)
          .connect()
          .whenComplete((result, error) -> {
            if (error != null) {
              this.future.completeExceptionally(error);
              returnedFuture.complete(false);
            } else {
              this.future.complete(result);
              returnedFuture.complete(result.isSuccessful());
            }

            waitingForConnection = false;
          });

      return returnedFuture;
    }
  }
}
