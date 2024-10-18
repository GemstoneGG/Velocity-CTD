/*
 * Copyright (C) 2018-2024 Velocity Contributors
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

package com.velocitypowered.proxy.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

/**
 * The {@code VelocityQueueConfiguration} class is responsible for managing the configuration
 * settings for the queue system in the Velocity proxy.
 * <p>
 * This configuration allows customization of various aspects of the queue system, such as enabling
 * multiple queues, configuring message delays, specifying server aliases, and handling specific
 * queue behavior when players switch servers or are offline.
 * </p>
 */
public record VelocityQueueConfiguration(boolean queueSystemEnabled, boolean replaceServerOutput, List<String> disabledQueueAliases,
    List<String> noQueueServers, boolean multipleQueues, String multipleServerMessagingSelection,
    double sendDelay, double messageDelay, int maxSendRetries, boolean removePlayerOnServerSwitch,
    boolean velocityKickReason, List<String> kickReasonsBlacklist, double offlineDelay,
    double returnOnlineSendDelay, boolean preventPausedQueueJoining, boolean sendAllUsersBackWhenOnline) {

  /**
   * Reads the configuration from the specified {@link Path}.
   *
   * @param path the {@link Path} to the configuration file.
   * @return a {@link VelocityQueueConfiguration} object representing the configuration.
   * @throws IOException if there is an error reading the configuration file.
   */
  public static VelocityQueueConfiguration read(Path path) throws IOException {
    URL defaultConfigLocation = VelocityConfiguration.class.getClassLoader()
        .getResource("default-velocity-queue.toml");

    if (defaultConfigLocation == null) {
      throw new RuntimeException("Default configuration file does not exist.");
    }

    try (final CommentedFileConfig config = CommentedFileConfig.builder(path)
        .defaultData(defaultConfigLocation)
        .autosave()
        .preserveInsertionOrder()
        .sync()
        .build()
    ) {
      config.load();

      final boolean queueSystemEnabled = config.get("queue-system-enabled");
      final boolean replaceServerOutput = config.get("replace-server-output");
      final List<String> disabledQueueAliases = config.get("disabled-queue-aliases");
      final List<String> noQueueServers = config.get("no-queue-servers");
      final boolean multipleQueues = config.get("multiple-queues");
      final String multipleServerMessagingSelection = config.get("multiple-server-messaging-selection");
      final double sendDelay = config.get("send-delay");
      final double messageDelay = config.get("message-delay");
      final int maxSendRetries = config.get("max-send-retries");
      final boolean removePlayerOnServerSwitch = config.get("remove-player-on-server-switch");
      final boolean velocityKickReason = config.get("velocity-kick-reason");
      final List<String> kickReasonsBlacklist = config.get("kick-reasons-blacklist");
      final double offlineDelay = config.get("offline-delay");
      final double returnOnlineSendDelay = config.get("return-online-send-delay");
      final boolean preventPausedQueueJoining = config.get("prevent-paused-queue-joining");
      final boolean sendAllUsersBackWhenOnline = config.get("send-all-users-back-when-online");

      return new VelocityQueueConfiguration(queueSystemEnabled, replaceServerOutput, disabledQueueAliases,
          noQueueServers, multipleQueues, multipleServerMessagingSelection, sendDelay,
          messageDelay, maxSendRetries, removePlayerOnServerSwitch, velocityKickReason,
          kickReasonsBlacklist, offlineDelay, returnOnlineSendDelay, preventPausedQueueJoining,
          sendAllUsersBackWhenOnline);
    }
  }
}
