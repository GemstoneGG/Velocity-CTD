/*
 * Copyright (C) 2018-2026 Velocity Contributors
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

import com.google.common.base.Suppliers;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocityctd.proxy.cluster.VelocityClusterPlayer;
import com.velocityctd.proxy.command.CommandUtils;
import com.velocityctd.proxy.command.PlayerIdentifier;
import com.velocityctd.proxy.util.CompletableUtils;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.util.ProxyVersion;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.ConfigDetector;
import com.velocitypowered.proxy.config.ConfigDetector.ConfigAnalysis;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import com.velocitypowered.proxy.util.InformationUtils;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.URI;
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
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Implements Velocity's {@code /velocity} command.
 *
 * <p>This command provides access to administrative utilities such as:</p>
 * <ul>
 *   <li>{@code /velocity dump} - Creates a diagnostic dump</li>
 *   <li>{@code /velocity heap} - Triggers a heap dump</li>
 *   <li>{@code /velocity info} - Displays version and environment info</li>
 *   <li>{@code /velocity plugins} - Lists installed plugins</li>
 *   <li>{@code /velocity reload} - Reloads the proxy configuration</li>
 *   <li>{@code /velocity sudo} - Forces player(s) to run a command or message</li>
 *   <li>{@code /velocity uptime} - Shows how long the proxy has been running</li>
 * </ul>
 */
public class VelocityCommand implements BuiltinCommandDefinition {

  private static final String USAGE = "/velocity <%s>";

  private final VelocityServer server;

  public VelocityCommand(VelocityServer server) {
    this.server = server;
  }

  @Override
  public String label() {
    return "velocity";
  }

  @Override
  public @NonNull List<String> aliases() {
    return List.of("velocityctd");
  }

  @Override
  public BrigadierCommand build() {
    LiteralCommandNode<CommandSource> dump = BrigadierCommand.literalArgumentBuilder("dump")
            .requires(source -> source.getPermissionValue("velocity.command.dump") == Tristate.TRUE)
            .executes(new Dump(server))
            .build();
    LiteralCommandNode<CommandSource> heap = BrigadierCommand.literalArgumentBuilder("heap")
            .requires(source -> source.getPermissionValue("velocity.command.heap") == Tristate.TRUE)
            .executes(new Heap())
            .build();
    LiteralCommandNode<CommandSource> info = BrigadierCommand.literalArgumentBuilder("info")
            .requires(source -> source.getPermissionValue("velocity.command.info") == Tristate.TRUE)
            .executes(new Info(server))
            .build();
    LiteralCommandNode<CommandSource> plugins = BrigadierCommand
            .literalArgumentBuilder("plugins")
            .requires(source -> source.getPermissionValue("velocity.command.plugins") == Tristate.TRUE)
            .executes(new Plugins(server))
            .build();
    LiteralArgumentBuilder<CommandSource> reload = BrigadierCommand
            .literalArgumentBuilder("reload")
            .requires(source -> source.getPermissionValue("velocity.command.reload") == Tristate.TRUE)
            .executes(new Reload(server));
    LiteralCommandNode<CommandSource> sudo = BrigadierCommand
            .literalArgumentBuilder("sudo")
            .requires(source -> source.getPermissionValue("velocity.command.sudo") == Tristate.TRUE)
            .executes(ctx -> {
              ctx.getSource().sendMessage(
                      Component.translatable("velocity.command.sudo.usage", NamedTextColor.YELLOW)
                              .arguments(Argument.string("command", "velocity sudo"))
              );

              return 0;
            })
            .then(BrigadierCommand.requiredArgumentBuilder("target", StringArgumentType.word())
                    .suggests(PlayerIdentifier.suggest(server, "target"))
                    .executes(ctx -> {
                      ctx.getSource().sendMessage(
                              Component.translatable("velocity.command.sudo.usage", NamedTextColor.YELLOW)
                                      .arguments(Argument.string("command", "velocity sudo"))
                      );

                      return 0;
                    })
                    .then(BrigadierCommand.requiredArgumentBuilder("message/command", StringArgumentType.greedyString())
                            .executes(new Sudo(server))))
            .build();
    LiteralArgumentBuilder<CommandSource> uptime = BrigadierCommand
            .literalArgumentBuilder("uptime")
            .requires(source -> source.getPermissionValue("velocity.command.uptime") == Tristate.TRUE)
            .executes(new Uptime(server));

    if (server.getClusterProxyService().isMultiProxy()) {
      reload = reload.then(
              BrigadierCommand.requiredArgumentBuilder("proxy", StringArgumentType.string())
                      .suggests(CommandUtils.suggestProxy(server, "proxy"))
                      .executes(new ReloadRemote(server))
      );

      uptime = uptime.then(
              BrigadierCommand.requiredArgumentBuilder("proxy", StringArgumentType.string())
                      .suggests(CommandUtils.suggestProxy(server, "proxy"))
                      .executes(new UptimeRemote(server))
      );
    }

    LiteralCommandNode<CommandSource> configcheck = BrigadierCommand.literalArgumentBuilder("configcheck")
            .requires(source -> source.getPermissionValue("velocity.command.configcheck") == Tristate.TRUE)
            .executes(new ConfigCheck(server))
            .build();

    List<LiteralCommandNode<CommandSource>> commands = List
            .of(dump, heap, info, plugins, reload.build(), sudo, uptime.build(), configcheck);
    return new BrigadierCommand(
            commands.stream()
                    .reduce(
                            BrigadierCommand.literalArgumentBuilder(label())
                                    .executes(ctx -> {
                                      CommandSource source = ctx.getSource();
                                      String availableCommands = commands.stream()
                                              .filter(e -> e.getRequirement().test(source))
                                              .map(LiteralCommandNode::getName)
                                              .collect(Collectors.joining("|"));
                                      String commandText = USAGE.formatted(availableCommands);
                                      source.sendMessage(Component.text(commandText, NamedTextColor.RED));
                                      return 0;
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
   * @param timeInSeconds the uptime in seconds
   * @return the component used by {@code /velocity uptime}
   */
  private static Component getUptimeComponent(long timeInSeconds) {
    int days = (int) TimeUnit.SECONDS.toDays(timeInSeconds);
    long hours = TimeUnit.SECONDS.toHours(timeInSeconds) - (days * 24L);
    long minutes = TimeUnit.SECONDS.toMinutes(timeInSeconds) - (TimeUnit.SECONDS.toHours(timeInSeconds) * 60);
    long seconds = TimeUnit.SECONDS.toSeconds(timeInSeconds) - (TimeUnit.SECONDS.toMinutes(timeInSeconds) * 60);

    return Component.translatable("velocity.command.uptime",
            NamedTextColor.GREEN).arguments(
            Argument.numeric("days", days),
            Argument.numeric("hours", hours),
            Argument.numeric("minutes", minutes),
            Argument.numeric("seconds", seconds)
    );
  }

  private record Uptime(VelocityServer server) implements Command<CommandSource> {

    @Override
    public int run(CommandContext<CommandSource> context) {
      CommandSource source = context.getSource();
      source.sendMessage(getUptimeComponent((System.currentTimeMillis() - server.getStartTime()) / 1000));
      return SINGLE_SUCCESS;
    }
  }

  private record UptimeRemote(VelocityServer server) implements Command<CommandSource> {

    private static final Logger LOGGER = LogManager.getLogger(UptimeRemote.class);

    @Override
    public int run(CommandContext<CommandSource> context) {
      CommandSource source = context.getSource();
      String proxyId = StringArgumentType.getString(context, "proxy");

      String realId = server.getClusterProxyService().getAllProxyIds().stream()
          .filter(s -> s.equalsIgnoreCase(proxyId))
          .findFirst().orElse(null);

      if (realId == null) {
        source.sendMessage(Component.translatable("velocity.command.proxy-does-not-exist")
                .arguments(Component.text(proxyId)));
        return 0;
      }

      server.getClusterProxyService().queryProxyUptime(realId).thenAccept(uptimeSeconds -> {
        source.sendMessage(getUptimeComponent(uptimeSeconds));
      }).exceptionally(ex -> {
        if (CompletableUtils.cause(ex) instanceof TimeoutException) {
          source.sendMessage(Component.translatable("velocity.command.uptime.timeout", NamedTextColor.RED));
        } else {
          LOGGER.error("Failed to query proxy uptime for {}", realId, ex);
        }
        return null;
      });
      return SINGLE_SUCCESS;
    }
  }

  private record Sudo(VelocityServer server) implements Command<CommandSource> {

    @Override
    public int run(CommandContext<CommandSource> context) {
      CommandSource source = context.getSource();
      String sudoTarget = context.getArgument("target", String.class);
      String messageOrCommand = context.getArgument("message/command", String.class);

      PlayerIdentifier.Result result = PlayerIdentifier.resolve(server, sudoTarget, source);
      if (!result.success()) {
        switch (result.type()) {
          case PLAYER -> source.sendMessage(Component.translatable("velocity.command.sudo.invalid-player")
              .arguments(Argument.string("player", result.name())));
          case SERVER -> source.sendMessage(Component.translatable("velocity.command.sudo.invalid-server")
              .arguments(Component.text(result.name())));
          case PROXY -> source.sendMessage(Component.translatable("velocity.command.sudo.invalid-proxy")
              .arguments(Component.text(result.name())));
          case PLAYER_EXECUTOR_REQUIRED -> source.sendMessage(CommandMessages.PLAYERS_ONLY);
          default -> {
          }
        }
        return 0;
      }

      if (result.players().isEmpty()) {
        source.sendMessage(Component.translatable("velocity.command.sudo.no-players"));
        return 0;
      }

      for (VelocityClusterPlayer player : result.players()) {
        player.sudo(messageOrCommand);
      }

      String targetDisplay = switch (result.type()) {
        case ALL -> "everyone";
        case PLAYER -> result.players().size() == 1
            ? result.players().iterator().next().getUsername()
            : sudoTarget;
        default -> result.name() != null ? result.name() : sudoTarget;
      };
      source.sendMessage(Component.translatable("velocity.command.sudo.success")
              .arguments(Argument.string("target", targetDisplay),
                      Argument.string("message", messageOrCommand)));

      return result.players().size();
    }
  }

  private record Reload(VelocityServer server) implements Command<CommandSource> {

    private static final Logger LOGGER = LogManager.getLogger(Reload.class);

    @Override
    public int run(CommandContext<CommandSource> context) {
      CommandSource source = context.getSource();
      try {
        if (server.reloadConfiguration()) {
          source.sendMessage(Component.translatable("velocity.command.reload-success",
                  NamedTextColor.GREEN));
          return SINGLE_SUCCESS;
        } else {
          source.sendMessage(Component.translatable("velocity.command.reload-failure",
                  NamedTextColor.RED));
          return 0;
        }
      } catch (Exception e) {
        LOGGER.error("Unable to reload configuration", e);
        source.sendMessage(Component.translatable("velocity.command.reload-failure",
                NamedTextColor.RED));
        return 0;
      }
    }
  }

  private record ReloadRemote(VelocityServer server) implements Command<CommandSource> {

    private static final Logger LOGGER = LogManager.getLogger(ReloadRemote.class);

    @Override
    public int run(CommandContext<CommandSource> context) {
      CommandSource source = context.getSource();
      String proxyId = StringArgumentType.getString(context, "proxy");

      String realId = server.getClusterProxyService().getAllProxyIds().stream()
          .filter(s -> s.equalsIgnoreCase(proxyId))
          .findFirst().orElse(null);

      if (realId == null) {
        source.sendMessage(Component.translatable("velocity.command.proxy-does-not-exist")
                .arguments(Component.text(proxyId)));
        return 0;
      }

      server.getClusterProxyService().reloadProxy(realId).thenAccept(success -> {
        if (success) {
          source.sendMessage(Component.translatable("velocity.command.reload-success", NamedTextColor.GREEN));
        } else {
          source.sendMessage(Component.translatable("velocity.command.reload-failure", NamedTextColor.RED));
        }
      }).exceptionally(ex -> {
        if (CompletableUtils.cause(ex) instanceof TimeoutException) {
          source.sendMessage(Component.translatable("velocity.command.reload.timeout", NamedTextColor.RED));
        } else {
          LOGGER.error("Failed to reload proxy {}", realId, ex);
        }
        return null;
      });
      return SINGLE_SUCCESS;
    }
  }

  private static class Info implements Command<CommandSource> {

    private static final Logger LOGGER = LogManager.getLogger(Info.class);

    private static final TextColor VELOCITY_COLOR = TextColor.color(0xff3a4c);

    /**
     * Version distance constant indicating the current version is up to date with GitHub.
     */
    private static final int DISTANCE_LATEST = 0;

    /**
     * Version distance constant indicating an error occurred during GitHub comparison.
     */
    private static final int DISTANCE_ERROR = -1;

    /**
     * Version distance constant indicating the specified commit hash was not found.
     */
    private static final int DISTANCE_UNKNOWN = -2;

    /**
     * Memoized supplier that builds the {@code /velocity info} output component.
     */
    private final Supplier<Component> infoSupplier;

    private Info(VelocityServer server) {
      ProxyVersion version = server.getVersion();
      this.infoSupplier = Suppliers.memoizeWithExpiration(() -> {
        TextComponent.Builder infoBuilder = Component.text();
        Component velocity = Component.text()
                .content(version.getName() + " ")
                .decoration(TextDecoration.BOLD, true)
                .color(VELOCITY_COLOR)
                .append(Component.text()
                        .content(version.getVersion())
                        .decoration(TextDecoration.BOLD, false))
                .hoverEvent(Component.translatable("velocity.command.version-offer-copy-version"))
                .clickEvent(ClickEvent.copyToClipboard(version.getName() + " " + version.getVersion()))
                .build();
        Component copyright = Component
                .translatable("velocity.command.version-copyright",
                        Argument.string("vendor", version.getVendor()),
                        Argument.string("name", version.getName()),
                        Argument.component("year", Component.text(LocalDate.now().getYear())));
        infoBuilder.append(velocity)
                .appendNewline()
                .append(copyright);

        TextComponent embellishment = Component.text()
            .append(Component.text()
                .content("discord.gg/beer")
                .color(NamedTextColor.RED)
                .clickEvent(ClickEvent.openUrl(VelocityServer.DISCORD_URL))
                .build())
            .append(Component.text(" - "))
            .append(Component.text()
                .content("GitHub")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.UNDERLINED, true)
                .clickEvent(ClickEvent.openUrl(VelocityServer.VELOCITY_URL))
                .build())
            .build();
        infoBuilder.appendNewline().append(embellishment);

        infoBuilder.appendNewline();
        if (version.isDevelopmentVersion()) {
          infoBuilder.append(Component.text("You are running a development build of Velocity.", NamedTextColor.RED));
        } else {
          int dist = fetchDistanceFromGitHub(version.getVersion());
          switch (dist) {
            case DISTANCE_ERROR -> infoBuilder.append(Component.translatable(
                    "velocity.command.version-error", NamedTextColor.RED));
            case DISTANCE_UNKNOWN -> infoBuilder.append(Component.translatable(
                    "velocity.command.version-unknown", NamedTextColor.RED));
            case DISTANCE_LATEST -> infoBuilder.append(Component.translatable(
                    "velocity.command.version-latest", NamedTextColor.GREEN));
            default -> infoBuilder.append(Component.translatable(
                            "velocity.command.version-behind", NamedTextColor.YELLOW)
                    .arguments(Argument.numeric("distance", dist)));
          }
        }

        return infoBuilder.build();
      }, 10, TimeUnit.MINUTES);
    }

    private static final Pattern GIT_HASH = Pattern.compile("-git-([0-9a-fA-F]+)");

    private static final Gson VERSION_GSON = new Gson();

    private static int fetchDistanceFromGitHub(String version) {
      Matcher matcher = GIT_HASH.matcher(version);
      if (!matcher.find()) {
        return DISTANCE_UNKNOWN;
      }

      String hash = matcher.group(1);
      try {
        HttpURLConnection connection = (HttpURLConnection) URI.create("https://api.github.com/repos/GemstoneGG/Velocity-CTD/compare/libdeflate..." + hash).toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", "Velocity-CTD/" + version + " (+https://github.com/GemstoneGG/Velocity-CTD)");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.connect();
        if (connection.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
          return DISTANCE_UNKNOWN; // Unidentifiable commit
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
          JsonObject obj = VERSION_GSON.fromJson(reader, JsonObject.class);
          String status = obj.get("status").getAsString();
          return switch (status) {
            case "identical" -> DISTANCE_LATEST;
            case "behind" -> obj.get("behind_by").getAsInt();
            default -> DISTANCE_ERROR;
          };
        } catch (JsonSyntaxException | NumberFormatException e) {
          LOGGER.error("Error parsing version-comparison response from GitHub for hash {}", hash, e);
          return DISTANCE_ERROR;
        }
      } catch (IOException e) {
        LOGGER.error("Error contacting GitHub for version comparison of hash {}", hash, e);
        return DISTANCE_ERROR;
      }
    }

    @Override
    public int run(CommandContext<CommandSource> context) {
      CommandSource source = context.getSource();
      source.sendMessage(infoSupplier.get());
      return SINGLE_SUCCESS;
    }
  }

  private record Plugins(VelocityServer server) implements Command<CommandSource> {

    @Override
    public int run(CommandContext<CommandSource> context) {
      CommandSource source = context.getSource();

      List<PluginContainer> plugins = List.copyOf(server.getPluginManager().getPlugins());
      int pluginCount = plugins.size();

      if (pluginCount == 0) {
        source.sendMessage(Component.translatable("velocity.command.no-plugins",
                NamedTextColor.YELLOW));
        return SINGLE_SUCCESS;
      }

      TextComponent.Builder listBuilder = Component.text();
      for (int i = 0; i < pluginCount; i++) {
        PluginContainer plugin = plugins.get(i);
        listBuilder.append(componentForPlugin(plugin.getDescription()));
        if (i + 1 < pluginCount) {
          listBuilder.append(Component.text(", "));
        }
      }

      TranslatableComponent output = Component.translatable()
              .key("velocity.command.plugins-list")
              .color(NamedTextColor.YELLOW)
              .arguments(Argument.component("plugins", listBuilder.build()))
              .build();
      source.sendMessage(output);
      return SINGLE_SUCCESS;
    }

    private TextComponent componentForPlugin(PluginDescription description) {
      String pluginInfo = description.getName().orElse(description.getId())
              + description.getVersion().map(v -> " " + v).orElse("");

      TextComponent.Builder hoverText = Component.text().content(pluginInfo);

      description.getUrl().ifPresent(url -> {
        hoverText.append(Component.newline());
        hoverText.append(Component.translatable(
                "velocity.command.plugin-tooltip-website",
                Argument.component("url", Component.text(url))));
      });
      if (!description.getAuthors().isEmpty()) {
        hoverText.append(Component.newline());
        if (description.getAuthors().size() == 1) {
          hoverText.append(
              Component.translatable("velocity.command.plugin-tooltip-author")
                  .arguments(Argument.string("author", description.getAuthors().getFirst()))
          );
        } else {
          hoverText.append(
              Component.translatable("velocity.command.plugin-tooltip-authors")
                  .arguments(Argument.string("authors", String.join(", ", description.getAuthors())))
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

  private record Dump(VelocityServer server) implements Command<CommandSource> {

    private static final Logger LOGGER = LogManager.getLogger(Dump.class);

    @Override
    public int run(CommandContext<CommandSource> context) {
      CommandSource source = context.getSource();

      Collection<VelocityRegisteredServer> allServers = Set.copyOf(server.getAllServers());
      JsonObject servers = new JsonObject();
      for (VelocityRegisteredServer iter : allServers) {
        servers.add(iter.getServerInfo().getName(),
                InformationUtils.collectServerInfo(iter));
      }
      JsonArray connectOrder = new JsonArray();
      List<String> attemptedConnectionOrder = List.copyOf(
              server.getConfiguration().getAttemptConnectionOrder());
      for (String s : attemptedConnectionOrder) {
        connectOrder.add(s);
      }

      JsonObject proxyConfig = InformationUtils.collectProxyConfig(server.getConfiguration());
      proxyConfig.add("servers", servers);
      proxyConfig.add("connectOrder", connectOrder);
      proxyConfig.add("forcedHosts",
              InformationUtils.collectForcedHosts(server.getConfiguration()));

      JsonObject dump = new JsonObject();
      dump.add("versionInfo", InformationUtils.collectProxyInfo(server.getVersion()));
      dump.add("platform", InformationUtils.collectEnvironmentInfo());
      dump.add("config", proxyConfig);
      dump.add("plugins", InformationUtils.collectPluginInfo(server));

      Path dumpPath = Path.of("velocity-dump-"
              + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date())
              + ".json");
      try (BufferedWriter bw = Files.newBufferedWriter(
              dumpPath, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
        bw.write(InformationUtils.toHumanReadableString(dump));

        source.sendMessage(
            Component.translatable("velocity.command.dump-created", NamedTextColor.GREEN)
                .arguments(Argument.string("path", dumpPath.toAbsolutePath().toString()))
        );
        return SINGLE_SUCCESS;
      } catch (IOException e) {
        LOGGER.error("Failed to complete dump command, the executor was interrupted: {}", e.getMessage(), e);
        source.sendMessage(
            Component.translatable("velocity.command.dump-failed", NamedTextColor.RED)
        );
        return 0;
      }
    }
  }

  /**
   * Heap SubCommand.
   */
  public static class Heap implements Command<CommandSource> {

    private static final Logger LOGGER = LogManager.getLogger(Heap.class);

    private MethodHandle heapGenerator;

    private Consumer<CommandSource> heapConsumer;

    private final Path dir = Path.of("./dumps");

    @Override
    public int run(CommandContext<CommandSource> context) throws CommandSyntaxException {
      CommandSource source = context.getSource();

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
              src.sendMessage(
                  Component.translatable("velocity.command.heapdump-created", NamedTextColor.GREEN)
                      .arguments(Argument.string("path", file.toAbsolutePath().toString()))
              );
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
              src.sendMessage(
                  Component.translatable("velocity.command.heapdump-created", NamedTextColor.GREEN)
                      .arguments(Argument.string("path", file.toAbsolutePath().toString()))
              );
            };
          }
        }

        this.heapConsumer.accept(source);
        return SINGLE_SUCCESS;
      } catch (Throwable t) {
        LOGGER.error("Could not write heap", t);
        source.sendMessage(
            Component.translatable("velocity.command.heapdump-failed", NamedTextColor.RED)
        );
        return 0;
      }
    }
  }

  private record ConfigCheck(VelocityServer server) implements Command<CommandSource> {

    private static final Logger LOGGER = LogManager.getLogger(ConfigCheck.class);

    @Override
    public int run(CommandContext<CommandSource> context) {
      CommandSource source = context.getSource();

      // Get the default config path
      Path configPath = Path.of("velocity.toml");

      try {
        ConfigDetector detector = new ConfigDetector(LOGGER);
        ConfigAnalysis analysis = detector.analyzeConfiguration(configPath);

        // Send formatted results to the command source
        source.sendMessage(Component.translatable("velocity.command.config-check.header", NamedTextColor.GOLD));

        if (!analysis.isOutdated()) {
          source.sendMessage(Component.translatable("velocity.command.config-check.up-to-date", NamedTextColor.GREEN)
                  .arguments(Argument.string("version", analysis.currentVersion())));
        } else {
          source.sendMessage(Component.translatable("velocity.command.config-check.needs-updates", NamedTextColor.YELLOW));
          source.sendMessage(Component.translatable("velocity.command.config-check.current-version", NamedTextColor.GRAY)
                  .arguments(Argument.string("version", analysis.currentVersion())));
          source.sendMessage(Component.translatable("velocity.command.config-check.latest-version", NamedTextColor.GRAY)
                  .arguments(Argument.string("version", analysis.latestVersion())));

          if (!analysis.missingOptions().isEmpty()) {
            source.sendMessage(Component.translatable("velocity.command.config-check.missing-options.header", NamedTextColor.RED));
            for (String option : analysis.missingOptions()) {
              source.sendMessage(Component.translatable("velocity.command.config-check.missing-options.item", NamedTextColor.RED)
                      .arguments(Argument.string("option", option)));
            }
          }

          if (!analysis.deprecatedOptions().isEmpty()) {
            source.sendMessage(Component.translatable("velocity.command.config-check.deprecated-options.header", NamedTextColor.YELLOW));
            for (String option : analysis.deprecatedOptions()) {
              source.sendMessage(Component.translatable("velocity.command.config-check.deprecated-options.item", NamedTextColor.YELLOW)
                      .arguments(Argument.string("option", option)));
            }
          }

          source.sendMessage(Component.translatable("velocity.command.config-check.recommendations.header", NamedTextColor.GOLD));
          for (String recommendation : analysis.recommendations()) {
            source.sendMessage(Component.translatable("velocity.command.config-check.recommendations.item", NamedTextColor.WHITE)
                    .arguments(Argument.string("recommendation", recommendation)));
          }
        }

        return SINGLE_SUCCESS;
      } catch (IOException e) {
        source.sendMessage(Component.translatable("velocity.command.config-check.error", NamedTextColor.RED)
                .arguments(Argument.string("message", e.getMessage())));
        LOGGER.error("Failed to analyze configuration file: {}", configPath, e);
        return 0;
      }
    }
  }
}
