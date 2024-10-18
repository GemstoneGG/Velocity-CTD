package com.velocitypowered.api.queue;

import java.util.UUID;

/**
 * The {@code QueueManager} interface provides methods to manage player queues for server connections
 * in a Velocity proxy environment.
 * <p>
 * This interface allows adding players to a queue for a specific server and removing players from the queue.
 * </p>
 */
public interface QueueManager {

  /**
   * Adds a player to the queue for a specified server.
   *
   * @param serverName the name of the server to which the player is being queued.
   * @param playerUuid the unique identifier ({@link UUID}) of the player being added to the queue.
   * @param priority   the priority level for the player in the queue; a higher value indicates higher priority.
   */
  void add(String serverName, UUID playerUuid, int priority);

  /**
   * Removes a player from the queue based on their {@link UUID}.
   *
   * @param uuid the unique identifier ({@link UUID}) of the player to be removed from the queue.
   */
  void remove(UUID uuid);
}
