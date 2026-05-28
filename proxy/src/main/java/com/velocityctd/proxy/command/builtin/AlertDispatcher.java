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

package com.velocityctd.proxy.command.builtin;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

import com.velocityctd.proxy.cluster.VelocityClusterPlayer;
import com.velocityctd.proxy.command.PlayerIdentifier;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.builtin.CommandMessages;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.translation.Argument;

/**
 * Shared dispatch logic for {@code /alert} and {@code /alertraw}.
 *
 * <p>Handles parsing of the optional {@link PlayerIdentifier} target and routing the
 * alert either to all players (legacy behavior) or to a specific subset.</p>
 */
final class AlertDispatcher {

  private AlertDispatcher() {
    throw new AssertionError();
  }

  /**
   * Dispatches an alert. The raw {@code input} is split on its first whitespace into a
   * candidate target and the remaining message text; using a single greedy-string argument
   * upstream means callers can include characters that Brigadier's {@code word()} parser
   * rejects (notably {@code &} colour codes).
   *
   * <p>Behaviour after the split:</p>
   * <ul>
   *   <li>If the first token resolves as a {@link PlayerIdentifier}, the rest of the input
   *       is sent to those players.</li>
   *   <li>If it has an explicit identifier prefix ({@code +}, {@code -}) or is an identifier
   *       keyword ({@code all}, {@code current}) but fails to resolve, an error is sent.</li>
   *   <li>Otherwise (e.g. an unknown bare word) the entire input is treated as a message and
   *       broadcast to all players, preserving backwards compatibility with the legacy
   *       {@code /alert <message>} form.</li>
   * </ul>
   *
   * @param server       the proxy server
   * @param source       the command source
   * @param input        the full argument input (target + message, or just a message)
   * @param noMessageKey translation key to use when there is no message to send
   * @param formatter    builds the final alert component from the raw message text
   * @return {@link com.mojang.brigadier.Command#SINGLE_SUCCESS} on success, {@code 0} on failure
   */
  static int dispatch(VelocityServer server, CommandSource source,
                      String input, String noMessageKey,
                      Function<String, Component> formatter) {
    int split = indexOfWhitespace(input);
    String target = split == -1 ? input : input.substring(0, split);
    String message = split == -1 ? "" : input.substring(split + 1);

    PlayerIdentifier.Result result = PlayerIdentifier.resolve(server, target, source);
    if (result.success()) {
      if (message.isEmpty()) {
        source.sendMessage(Component.translatable(noMessageKey, NamedTextColor.YELLOW));
        return 0;
      }

      Component component = formatter.apply(message);
      for (VelocityClusterPlayer player : result.players()) {
        player.sendMessage(component);
      }
      return SINGLE_SUCCESS;
    }

    // Resolution failed. If the target looked like an explicit identifier, surface the error,
    // otherwise fall back to treating the whole input as a legacy message broadcast.
    if (looksLikeIdentifier(target)) {
      sendResolveError(source, result);
      return 0;
    }

    if (input.isEmpty()) {
      source.sendMessage(Component.translatable(noMessageKey, NamedTextColor.YELLOW));
      return 0;
    }

    server.getClusterPlayerService().broadcastAlert(formatter.apply(input));
    return SINGLE_SUCCESS;
  }

  private static int indexOfWhitespace(String s) {
    for (int i = 0; i < s.length(); i++) {
      if (Character.isWhitespace(s.charAt(i))) {
        return i;
      }
    }
    return -1;
  }

  private static boolean looksLikeIdentifier(String target) {
    return target.startsWith("+")
        || target.startsWith("-")
        || target.equalsIgnoreCase("all")
        || target.equalsIgnoreCase("current");
  }

  private static void sendResolveError(CommandSource source, PlayerIdentifier.Result result) {
    switch (result.type()) {
      case PLAYER -> source.sendMessage(CommandMessages.PLAYER_NOT_FOUND
          .arguments(Argument.string("player", result.name())));
      case SERVER -> source.sendMessage(CommandMessages.SERVER_DOES_NOT_EXIST
          .arguments(Component.text(result.name())));
      case PLAYER_EXECUTOR_REQUIRED -> source.sendMessage(CommandMessages.PLAYERS_ONLY);
      default -> {
      }
    }
  }
}
