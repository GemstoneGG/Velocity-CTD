/*
 * Copyright (C) 2018-2023 Velocity Contributors
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

package com.velocitypowered.proxy.command.builtin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.util.ProxyVersion;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.VelocityCommands;
import com.velocitypowered.proxy.redis.multiproxy.MultiProxyHandler;
import com.velocitypowered.proxy.util.InformationUtils;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implements the {@code /velocity} command and friends.
 */
public final class VelocityCommand {
  private static final String USAGE = "/velocity <%s>";

  /**
   * Creates a BrigadierCommand for various administrative tasks such as dump, heap, info, plugins, reload, sudo, and uptime.
   *
   * @param server the VelocityServer instance used for executing the commands.
   * @return the root BrigadierCommand containing all subcommands.
   */
  public static BrigadierCommand create(final VelocityServer server) {
    final LiteralCommandNode<CommandSource> dump = BrigadierCommand.literalArgumentBuilder("dump")
        .requires(source -> source.getPermissionValue("velocity.command.dump") == Tristate.TRUE)
        .executes(new Dump(server))
        .build();
    final LiteralCommandNode<CommandSource> heap = BrigadierCommand.literalArgumentBuilder("heap")
        .requires(source -> source.getPermissionValue("velocity.command.heap") == Tristate.TRUE)
        .executes(new Heap())
        .build();
    final LiteralCommandNode<CommandSource> info = BrigadierCommand.literalArgumentBuilder("info")
        .requires(source -> source.getPermissionValue("velocity.command.info") != Tristate.FALSE)
        .executes(new Info(server))
        .build();
    final LiteralCommandNode<CommandSource> plugins = BrigadierCommand
        .literalArgumentBuilder("plugins")
        .requires(source -> source.getPermissionValue("velocity.command.plugins") == Tristate.TRUE)
        .executes(new Plugins(server))
        .build();
    LiteralArgumentBuilder<CommandSource> reload = BrigadierCommand
        .literalArgumentBuilder("reload")
        .requires(source -> source.getPermissionValue("velocity.command.reload") == Tristate.TRUE)
        .executes(new Reload(server));
    final LiteralCommandNode<CommandSource> sudo = BrigadierCommand
        .literalArgumentBuilder("sudo")
        .requires(source -> source.getPermissionValue("velocity.command.sudo") == Tristate.TRUE)
        .executes(ctx -> {
          ctx.getSource().sendMessage(
              Component.translatable("velocity.command.sudo.usage", NamedTextColor.YELLOW)
                  .arguments(Component.text("velocity sudo"))
          );
          return Command.SINGLE_SUCCESS;
        })
        .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
        .suggests((ctx, builder) -> {
          final String argument = ctx.getArguments().containsKey("player")
              ? ctx.getArgument("player", String.class)
              : "";

          if ("all".regionMatches(true, 0, argument, 0, argument.length())) {
            builder.suggest("all");
          }

          if (server.getMultiProxyHandler().isEnabled()) {
            for (MultiProxyHandler.RemotePlayerInfo i : server.getMultiProxyHandler().getAllPlayers()) {
              if (i.getName().regionMatches(true, 0, argument, 0, argument.length())) {
                builder.suggest(i.getName());
              }
            }

            return builder.buildFuture();
          }

          for (final Player player : server.getAllPlayers()) {
            final String playerName = player.getUsername();
            if (playerName.regionMatches(true, 0, argument, 0, argument.length())) {
              builder.suggest(playerName);
            }
          }

          return builder.buildFuture();
        })
        .executes(ctx -> {
          ctx.getSource().sendMessage(
              Component.translatable("velocity.command.sudo.usage", NamedTextColor.YELLOW)
                  .arguments(Component.text("velocity sudo"))
          );
          return Command.SINGLE_SUCCESS;
        })
        .then(BrigadierCommand.requiredArgumentBuilder("message/command", StringArgumentType.greedyString())
        .executes(new Sudo(server))))
        .build();
    LiteralArgumentBuilder<CommandSource> uptime = BrigadierCommand
        .literalArgumentBuilder("uptime")
        .requires(source -> source.getPermissionValue("velocity.command.uptime") == Tristate.TRUE)
        .executes(new Uptime(server));

    if (server.getConfiguration().getRedis().isEnabled()) {
      reload = reload.then(
          BrigadierCommand.requiredArgumentBuilder("proxy", StringArgumentType.string())
              .suggests((ctx, builder) -> VelocityCommands.suggestProxy(server, ctx, builder))
              .executes(new ReloadRemote(server))
      );

      uptime = uptime.then(
          BrigadierCommand.requiredArgumentBuilder("proxy", StringArgumentType.string())
              .suggests((ctx, builder) -> VelocityCommands.suggestProxy(server, ctx, builder))
              .executes(new UptimeRemote(server))
      );
    }

    final List<LiteralCommandNode<CommandSource>> commands = List
            .of(dump, heap, info, plugins, reload.build(), sudo, uptime.build());
    return new BrigadierCommand(
      commands.stream()
        .reduce(
          BrigadierCommand.literalArgumentBuilder("velocity")
            .executes(ctx -> {
              final CommandSource source = ctx.getSource();
              final String availableCommands = commands.stream()
                      .filter(e -> e.getRequirement().test(source))
                      .map(LiteralCommandNode::getName)
                      .collect(Collectors.joining("|"));
              final String commandText = USAGE.formatted(availableCommands);
              source.sendMessage(Component.text(commandText, NamedTextColor.RED));
              return Command.SINGLE_SUCCESS;
            })
            .requires(commands.stream()
                    .map(CommandNode::getRequirement)
                    .reduce(Predicate::or)
                    .orElseThrow()),
          ArgumentBuilder::then,
          ArgumentBuilder::then
        )
    );
  }

  /**
   * Returns the component used by {@code /velocity uptime}.
   *
   * @param server the proxy server
   * @return the component used by {@code /velocity uptime}
   */
  public static Component getUptimeComponent(VelocityServer server) {
    long timeInSeconds = (System.currentTimeMillis() - server.getStartTime()) / 1000;
    int days = (int) TimeUnit.SECONDS.toDays(timeInSeconds);
    long hours = TimeUnit.SECONDS.toHours(timeInSeconds) - (days * 24L);
    long minutes = TimeUnit.SECONDS.toMinutes(timeInSeconds) - (TimeUnit.SECONDS.toHours(timeInSeconds) * 60);
    long seconds = TimeUnit.SECONDS.toSeconds(timeInSeconds) - (TimeUnit.SECONDS.toMinutes(timeInSeconds) * 60);

    return Component.translatable("velocity.command.uptime",
        NamedTextColor.GREEN,
        Component.text(days),
        Component.text(hours),
        Component.text(minutes),
        Component.text(seconds)
    );
  }

  private record Uptime(VelocityServer server) implements Command<CommandSource> {

    @Override
    public int run(final CommandContext<CommandSource> context) {
      final CommandSource source = context.getSource();
      source.sendMessage(getUptimeComponent(server));
      return Command.SINGLE_SUCCESS;
    }
  }

  private record UptimeRemote(VelocityServer server) implements Command<CommandSource> {

    @Override
    public int run(final CommandContext<CommandSource> context) {
      final CommandSource source = context.getSource();
      final String proxyId = StringArgumentType.getString(context, "proxy");

      if (!server.getMultiProxyHandler().getAllProxyIds().contains(proxyId)) {
        source.sendMessage(Component.translatable("velocity.command.proxy-does-not-exist")
            .arguments(Component.text(proxyId)));
        return -1;
      }

      source.sendMessage(Component.translatable("velocity.command.uptime-remote")
          .arguments(Component.text(proxyId)));

      server.getMultiProxyHandler().requestUptime(proxyId, source);
      return Command.SINGLE_SUCCESS;
    }
  }

  private record Sudo(VelocityServer server) implements Command<CommandSource> {

    @Override
    public int run(final CommandContext<CommandSource> context) {
      final CommandSource source = context.getSource();
      final String playerName = context.getArgument("player", String.class);
      final String messageOrCommand = context.getArgument("message/command", String.class);

      final MultiProxyHandler multiProxyHandler = server.getMultiProxyHandler();

      if (multiProxyHandler.isEnabled()) {
        for (String proxyId : multiProxyHandler.getAllProxyIds()) {
          if (proxyId.equals(multiProxyHandler.getOwnProxyId())) {
            continue;
          }

          if (playerName.equalsIgnoreCase("all")) {
            source.sendMessage(Component.translatable(
                "velocity.command.sudo.executed-remotely-all",
                NamedTextColor.GREEN,
                Component.text(proxyId),
                Component.text(playerName),
                Component.text(messageOrCommand)
            ));
            for (MultiProxyHandler.RemotePlayerInfo player : multiProxyHandler.getPlayers(proxyId)) {
              multiProxyHandler.sudo(player, source, messageOrCommand);
            }
          } else {
            for (MultiProxyHandler.RemotePlayerInfo player : multiProxyHandler.getPlayers(proxyId)) {
              if (player.getName().equalsIgnoreCase(playerName)) {
                source.sendMessage(Component.translatable(
                    "velocity.command.sudo.executed-remotely",
                    NamedTextColor.GREEN,
                    Component.text(proxyId),
                    Component.text(playerName),
                    Component.text(messageOrCommand)
                ));

                multiProxyHandler.sudo(player, source, messageOrCommand);
                return Command.SINGLE_SUCCESS;
              }
            }
          }
        }
      }

      if (this.server.getPlayerCount() == 0) {
        context.getSource().sendMessage(Component.translatable("velocity.command.sudo.no-players"));
        return Command.SINGLE_SUCCESS;
      }

      if (playerName.equalsIgnoreCase("all")) {
        if (messageOrCommand.startsWith("/")) {
          source.sendMessage(Component.translatable(
              "velocity.command.sudo.command-executed-all",
              NamedTextColor.GREEN,
              Component.text(messageOrCommand)
          ));
        } else {
          source.sendMessage(Component.translatable(
              "velocity.command.sudo.message-sent-all",
              NamedTextColor.GREEN,
              Component.text(messageOrCommand)
          ));
        }
        server.getAllPlayers().forEach(player -> {
          if (messageOrCommand.startsWith("/")) {
            String[] split = messageOrCommand.split(" ");
            String command = split[0].substring(1);
            if (this.server.getCommandManager().hasCommand(command)) {
              this.server.getCommandManager().executeAsync(player, messageOrCommand.substring(1));
            } else {
              player.spoofChatInput(messageOrCommand);
            }

          } else {
            player.spoofChatInput(messageOrCommand);
          }
        });
        return Command.SINGLE_SUCCESS;
      }

      server.getPlayer(playerName).ifPresentOrElse(player -> {
        if (messageOrCommand.startsWith("/")) {
          String[] split = messageOrCommand.split(" ");
          String command = split[0].substring(1);
          if (this.server.getCommandManager().hasCommand(command)) {
            source.sendMessage(Component.translatable(
                "velocity.command.sudo.command-executed",
                NamedTextColor.GREEN,
                Component.text(playerName),
                Component.text(messageOrCommand)
            ));
            this.server.getCommandManager().executeAsync(player, messageOrCommand.substring(1));
          } else {
            source.sendMessage(Component.translatable(
                "velocity.command.sudo.command-executed",
                NamedTextColor.GREEN,
                Component.text(playerName),
                Component.text(messageOrCommand)
            ));
            player.spoofChatInput(messageOrCommand);
          }
        } else {
          player.spoofChatInput(messageOrCommand);
          source.sendMessage(Component.translatable(
              "velocity.command.sudo.message-sent",
              NamedTextColor.GREEN,
              Component.text(playerName),
              Component.text(messageOrCommand)
          ));
        }
      },
          () -> source.sendMessage(Component.translatable(
              "velocity.command.player-not-found",
              NamedTextColor.RED,
              Component.text(playerName)
          ))
      );
      return Command.SINGLE_SUCCESS;
    }
  }

  private record Reload(VelocityServer server) implements Command<CommandSource> {

    private static final Logger logger = LogManager.getLogger(Reload.class);

    @Override
    public int run(final CommandContext<CommandSource> context) {
      final CommandSource source = context.getSource();
      try {
        if (server.reloadConfiguration()) {
          source.sendMessage(Component.translatable("velocity.command.reload-success",
              NamedTextColor.GREEN));
        } else {
          source.sendMessage(Component.translatable("velocity.command.reload-failure",
              NamedTextColor.RED));
        }
      } catch (Exception e) {
        logger.error("Unable to reload configuration", e);
        source.sendMessage(Component.translatable("velocity.command.reload-failure",
            NamedTextColor.RED));
      }
      return Command.SINGLE_SUCCESS;
    }
  }

  private record ReloadRemote(VelocityServer server) implements Command<CommandSource> {
    @Override
    public int run(final CommandContext<CommandSource> context) {
      final CommandSource source = context.getSource();
      final String proxyId = StringArgumentType.getString(context, "proxy");

      if (!server.getMultiProxyHandler().getAllProxyIds().contains(proxyId)) {
        source.sendMessage(Component.translatable("velocity.command.proxy-does-not-exist")
            .arguments(Component.text(proxyId)));
        return -1;
      }

      source.sendMessage(Component.translatable("velocity.command.reload-remote")
          .arguments(Component.text(proxyId)));

      server.getMultiProxyHandler().requestReload(proxyId, source);
      return Command.SINGLE_SUCCESS;
    }
  }

  private record Info(ProxyServer server) implements Command<CommandSource> {

    private static final TextColor VELOCITY_COLOR = TextColor.color(0xff3a4c);

    @Override
    public int run(final CommandContext<CommandSource> context) {
      final CommandSource source = context.getSource();
      final ProxyVersion version = server.getVersion();

      final Component velocity = Component.text()
          .content(version.getName() + " ")
          .decoration(TextDecoration.BOLD, true)
          .color(VELOCITY_COLOR)
          .append(Component.text()
                  .content(version.getVersion())
                  .decoration(TextDecoration.BOLD, false))
          .build();
      final Component copyright = Component
          .translatable("velocity.command.version-copyright",
              Component.text(version.getVendor()),
                  Component.text(version.getName()),
                  Component.text(LocalDate.now().getYear()));
      source.sendMessage(velocity);
      source.sendMessage(copyright);

      if (version.getName().equals("Velocity")) {
        final TextComponent embellishment = Component.text()
            .append(Component.text()
                .content("discord.gg/beer")
                .color(NamedTextColor.RED)
                .clickEvent(
                    ClickEvent.openUrl("https://discord.gg/beer"))
                .build())
            .append(Component.text(" - "))
            .append(Component.text()
                .content("GitHub")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.UNDERLINED, true)
                .clickEvent(ClickEvent.openUrl(
                    "https://github.com/GemstoneGG/Velocity-CTD"))
                .build())
            .build();
        source.sendMessage(embellishment);
      }
      return Command.SINGLE_SUCCESS;
    }
  }

  private record Plugins(ProxyServer server) implements Command<CommandSource> {

    @Override
    public int run(final CommandContext<CommandSource> context) {
      final CommandSource source = context.getSource();

      final List<PluginContainer> plugins = List.copyOf(server.getPluginManager().getPlugins());
      final int pluginCount = plugins.size();

      if (pluginCount == 0) {
        source.sendMessage(Component.translatable("velocity.command.no-plugins",
            NamedTextColor.YELLOW));
        return Command.SINGLE_SUCCESS;
      }

      final TextComponent.Builder listBuilder = Component.text();
      for (int i = 0; i < pluginCount; i++) {
        final PluginContainer plugin = plugins.get(i);
        listBuilder.append(componentForPlugin(plugin.getDescription()));
        if (i + 1 < pluginCount) {
          listBuilder.append(Component.text(", "));
        }
      }

      final TranslatableComponent output = Component.translatable()
          .key("velocity.command.plugins-list")
          .color(NamedTextColor.YELLOW)
          .arguments(listBuilder.build())
          .build();
      source.sendMessage(output);
      return Command.SINGLE_SUCCESS;
    }

    private TextComponent componentForPlugin(final PluginDescription description) {
      final String pluginInfo = description.getName().orElse(description.getId())
          + description.getVersion().map(v -> " " + v).orElse("");

      final TextComponent.Builder hoverText = Component.text().content(pluginInfo);

      description.getUrl().ifPresent(url -> {
        hoverText.append(Component.newline());
        hoverText.append(Component.translatable(
            "velocity.command.plugin-tooltip-website",
            Component.text(url)));
      });
      if (!description.getAuthors().isEmpty()) {
        hoverText.append(Component.newline());
        if (description.getAuthors().size() == 1) {
          hoverText.append(Component.translatable("velocity.command.plugin-tooltip-author",
              Component.text(description.getAuthors().get(0))));
        } else {
          hoverText.append(
              Component.translatable("velocity.command.plugin-tooltip-author",
                  Component.text(String.join(", ", description.getAuthors()))
              )
          );
        }
      }
      description.getDescription().ifPresent(pdesc -> {
        hoverText.append(Component.newline());
        hoverText.append(Component.newline());
        hoverText.append(Component.text(pdesc));
      });

      return Component.text()
              .content(description.getId())
              .color(NamedTextColor.GRAY)
              .hoverEvent(HoverEvent.showText(hoverText.build()))
              .build();
    }
  }

  private record Dump(ProxyServer server) implements Command<CommandSource> {
    private static final Logger logger = LogManager.getLogger(Dump.class);


    @Override
    public int run(final CommandContext<CommandSource> context) {
      final CommandSource source = context.getSource();

      final Collection<RegisteredServer> allServers = Set.copyOf(server.getAllServers());
      final JsonObject servers = new JsonObject();
      for (final RegisteredServer iter : allServers) {
        servers.add(iter.getServerInfo().getName(),
            InformationUtils.collectServerInfo(iter));
      }
      final JsonArray connectOrder = new JsonArray();
      final List<String> attemptedConnectionOrder = List.copyOf(
          server.getConfiguration().getAttemptConnectionOrder());
      for (final String s : attemptedConnectionOrder) {
        connectOrder.add(s);
      }

      final JsonObject proxyConfig = InformationUtils.collectProxyConfig(server.getConfiguration());
      proxyConfig.add("servers", servers);
      proxyConfig.add("connectOrder", connectOrder);
      proxyConfig.add("forcedHosts",
          InformationUtils.collectForcedHosts(server.getConfiguration()));

      final JsonObject dump = new JsonObject();
      dump.add("versionInfo", InformationUtils.collectProxyInfo(server.getVersion()));
      dump.add("platform", InformationUtils.collectEnvironmentInfo());
      dump.add("config", proxyConfig);
      dump.add("plugins", InformationUtils.collectPluginInfo(server));

      final Path dumpPath = Path.of("velocity-dump-"
          + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date())
          + ".json");
      try (BufferedWriter bw = Files.newBufferedWriter(
          dumpPath, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
        bw.write(InformationUtils.toHumanReadableString(dump));

        source.sendMessage(Component.text(
            "An anonymised report containing useful information about "
                + "this proxy has been saved at " + dumpPath.toAbsolutePath(),
            NamedTextColor.GREEN));
      } catch (IOException e) {
        logger.error("Failed to complete dump command, the executor was interrupted: {}", e.getMessage(), e);
        source.sendMessage(Component.text(
            "We could not save the anonymized dump. Check the console for more details.",
            NamedTextColor.RED)
        );
      }
      return Command.SINGLE_SUCCESS;
    }
  }

  /**
   * Heap SubCommand.
   */
  public static final class Heap implements Command<CommandSource> {
    private static final Logger logger = LogManager.getLogger(Heap.class);
    private MethodHandle heapGenerator;
    private Consumer<CommandSource> heapConsumer;
    private final Path dir = Path.of("./dumps");

    @Override
    public int run(final CommandContext<CommandSource> context) throws CommandSyntaxException {
      final CommandSource source = context.getSource();

      try {
        if (Files.notExists(dir)) {
          Files.createDirectories(dir);
        }

        // A single lookup of the heap dump generator method is performed on execution
        // to avoid assigning variables unnecessarily in case the user never executes the command
        if (heapGenerator == null || heapConsumer == null) {
          javax.management.MBeanServer server = ManagementFactory.getPlatformMBeanServer();
          MethodHandles.Lookup lookup = MethodHandles.lookup();
          SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
          MethodType type;
          try {
            Class<?> clazz = Class.forName("openj9.lang.management.OpenJ9DiagnosticsMXBean");
            type = MethodType.methodType(String.class, String.class, String.class);

            this.heapGenerator = lookup.findVirtual(clazz, "triggerDumpToFile", type);
            this.heapConsumer = (src) -> {
              String name = "heap-dump-" + format.format(new Date());
              Path file = dir.resolve(name + ".phd");
              try {
                Object openj9Mbean = ManagementFactory.newPlatformMXBeanProxy(
                    server, "openj9.lang.management:type=OpenJ9Diagnostics", clazz);
                heapGenerator.invoke(openj9Mbean, "heap", file.toString());
              } catch (Throwable e) {
                // This should not occur
                throw new RuntimeException(e);
              }
              src.sendMessage(Component.text("Heap dump saved to " + file, NamedTextColor.GREEN));
            };
          } catch (ClassNotFoundException e) {
            Class<?> clazz = Class.forName("com.sun.management.HotSpotDiagnosticMXBean");
            type = MethodType.methodType(void.class, String.class, boolean.class);

            this.heapGenerator = lookup.findVirtual(clazz, "dumpHeap", type);
            this.heapConsumer = (src) -> {
              String name = "heap-dump-" + format.format(new Date());
              Path file = dir.resolve(name + ".hprof");
              try {
                Object hotspotMbean = ManagementFactory.newPlatformMXBeanProxy(
                    server, "com.sun.management:type=HotSpotDiagnostic", clazz);
                this.heapGenerator.invoke(hotspotMbean, file.toString(), true);
              } catch (Throwable e1) {
                // This should not occur
                throw new RuntimeException(e1);
              }
              src.sendMessage(Component.text("Heap dump saved to " + file, NamedTextColor.GREEN));
            };
          }
        }

        this.heapConsumer.accept(source);
      } catch (Throwable t) {
        source.sendMessage(Component.text("Failed to write heap dump, see server log for details",
            NamedTextColor.RED));
        logger.error("Could not write heap", t);
      }
      return Command.SINGLE_SUCCESS;
    }
  }
}
