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

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

public record VelocityQueueConfiguration(boolean queueSystemEnabled, boolean replaceServerOutput, List<String> disabledQueueAliases,
                                         List<String> noQueueServers, boolean multipleQueues, String multipleServerMessagingSelection,
                                         double sendDelay, double messageDelay, int maxSendRetries, boolean removePlayerOnServerSwitch,
                                         boolean velocityKickReason, List<String> kickReasonsBlacklist, double offlineDelay,
                                         double returnOnlineSendDelay, boolean preventPausedQueueJoining, boolean sendAllUsersBackWhenOnline) {

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

      boolean queueSystemEnabled = config.get("queue-system-enabled");
      boolean replaceServerOutput = config.get("replace-server-output");
      List<String> disabledQueueAliases = config.get("disabled-queue-aliases");
      List<String> noQueueServers = config.get("no-queue-servers");
      boolean multipleQueues = config.get("multiple-queues");
      String multipleServerMessagingSelection = config.get("multiple-server-messaging-selection");
      double sendDelay = config.get("send-delay");
      double messageDelay = config.get("message-delay");
      int maxSendRetries = config.get("max-send-retries");
      boolean removePlayerOnServerSwitch = config.get("remove-player-on-server-switch");
      boolean velocityKickReason = config.get("velocity-kick-reason");
      List<String> kickReasonsBlacklist = config.get("kick-reasons-blacklist");
      double offlineDelay = config.get("offline-delay");
      double returnOnlineSendDelay = config.get("return-online-send-delay");
      boolean preventPausedQueueJoining = config.get("prevent-paused-queue-joining");
      boolean sendAllUsersBackWhenOnline = config.get("send-all-users-back-when-online");

      return new VelocityQueueConfiguration(queueSystemEnabled, replaceServerOutput, disabledQueueAliases,
              noQueueServers, multipleQueues, multipleServerMessagingSelection, sendDelay,
              messageDelay, maxSendRetries, removePlayerOnServerSwitch, velocityKickReason,
              kickReasonsBlacklist, offlineDelay, returnOnlineSendDelay, preventPausedQueueJoining,
              sendAllUsersBackWhenOnline);
    }
  }
}
