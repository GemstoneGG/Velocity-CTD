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

package com.velocityctd.proxy.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocityctd.api.queue.QueueState;
import com.velocityctd.api.queue.SelectionStrategy;
import com.velocityctd.api.queue.ServerStatus;
import com.velocityctd.proxy.cluster.VelocityClusterPlayer;
import com.velocityctd.proxy.cluster.VelocityClusterPlayerService;
import com.velocityctd.proxy.command.builtin.LeaveQueueCommand;
import com.velocityctd.proxy.command.builtin.PlistCommand;
import com.velocityctd.proxy.command.builtin.QueueAdminCommand;
import com.velocityctd.proxy.queue.VelocityQueue;
import com.velocityctd.proxy.queue.VelocityQueueEntry;
import com.velocityctd.proxy.queue.VelocityQueueManager;
import com.velocityctd.proxy.queue.group.ServerGroupManager;
import com.velocityctd.proxy.queue.group.VelocityServerGroup;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.builtin.GlistCommand;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Regression tests for group-aware command suggestions, resolution and listing.
 */
class GroupCommandTest {

  private static final String GROUP = "survival";
  private static final List<String> MEMBERS = List.of("Survival-01", "Survival-02");

  private VelocityServer server;
  private VelocityConfiguration.Queue queueConfig;
  private VelocityQueueManager queueManager;
  private VelocityQueue<?> groupQueue;
  private CommandSource source;
  private VelocityClusterPlayerService clusterPlayers;

  @BeforeEach
  void setUp() {
    queueConfig = mock(VelocityConfiguration.Queue.class);
    when(queueConfig.isEnabled()).thenReturn(true);
    when(queueConfig.getNoQueueServers()).thenReturn(List.of());
    when(queueConfig.getLeaveQueueAliases()).thenReturn(List.of("leavequeue"));

    VelocityConfiguration config = mock(VelocityConfiguration.class);
    when(config.getQueue()).thenReturn(queueConfig);

    server = mock(VelocityServer.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    when(server.getConfiguration()).thenReturn(config);
    when(server.isQueueEnabled()).thenReturn(true);

    VelocityServerGroup group = new VelocityServerGroup(
        GROUP, MEMBERS, SelectionStrategy.LEAST_PLAYERS, -1);
    ServerGroupManager groupManager = mock(ServerGroupManager.class);
    when(groupManager.getGroups()).thenReturn(List.of(group));
    when(groupManager.hasGroup(GROUP)).thenReturn(true);
    when(groupManager.getGroup(GROUP)).thenReturn(group);

    queueManager = mock(VelocityQueueManager.class);
    when(queueManager.getServerGroupManager()).thenReturn(groupManager);
    groupQueue = mock(VelocityQueue.class);
    when(groupQueue.getName()).thenReturn(GROUP);
    when(groupQueue.getState()).thenReturn(QueueState.ACTIVE);
    when(groupQueue.getServerStatus()).thenReturn(ServerStatus.ONLINE);
    doReturn(groupQueue).when(queueManager).getQueue(GROUP);
    when(queueManager.getQueues()).thenReturn(List.of(groupQueue));
    when(server.getQueueManager()).thenReturn(queueManager);

    VelocityRegisteredServer physical = mock(VelocityRegisteredServer.class);
    when(physical.getServerInfo()).thenReturn(
        new ServerInfo("Survival-01", new InetSocketAddress("127.0.0.1", 1)));
    doReturn(groupQueue).when(physical).getQueue();
    when(server.getServer("Survival-01")).thenReturn(Optional.of(physical));
    when(server.getServer(GROUP)).thenReturn(Optional.empty());
    when(server.getAllServers()).thenReturn(List.of(physical));

    source = mock(CommandSource.class);
    when(source.getPermissionValue(anyString())).thenReturn(Tristate.UNDEFINED);
    clusterPlayers = mock(VelocityClusterPlayerService.class);
    when(clusterPlayers.getTotalPlayerCount()).thenReturn(2);
    when(server.getClusterPlayerService()).thenReturn(clusterPlayers);
  }

  private CommandContext<CommandSource> context(String value) {
    CommandContext<CommandSource> ctx = mock(CommandContext.class);
    when(ctx.getArgument("server", String.class)).thenReturn(value);
    when(ctx.getArgument("proxy", String.class)).thenReturn("all");
    when(ctx.getArgument("from", String.class)).thenReturn(value);
    when(ctx.getArgument("to", String.class)).thenReturn(value);
    com.mojang.brigadier.context.ParsedArgument<CommandSource, String> parsed =
        new com.mojang.brigadier.context.ParsedArgument<>(0, value.length(), value);
    doReturn(Map.<String, com.mojang.brigadier.context.ParsedArgument<CommandSource, ?>>of(
        "server", parsed)).when(ctx).getArguments();
    when(ctx.getSource()).thenReturn(source);
    return ctx;
  }

  private List<String> suggestions(String input) throws Exception {
    SuggestionsBuilder builder = new SuggestionsBuilder(input, 0);
    return CommandUtils.suggestServer(server, "server", false, true)
        .getSuggestions(context(input), builder).join().getList().stream()
        .map(suggestion -> suggestion.getText()).toList();
  }

  private static String key(Component component) {
    return ((TranslatableComponent) component).key();
  }

  @Test
  void suggestionsAndResolutionAgreeForGroups() throws Exception {
    assertTrue(suggestions("sur").contains(GROUP));
    assertSame(groupQueue, CommandUtils.resolveQueue(server, context(GROUP), "server", false));
  }

  @Test
  void permissionDenialHidesAndBlocksTheGroup() throws Exception {
    when(source.getPermissionValue("velocity.command.server." + GROUP)).thenReturn(Tristate.FALSE);

    assertFalse(suggestions("sur").contains(GROUP));
    assertNull(CommandUtils.resolveQueue(server, context(GROUP), "server", false));
  }

  @Test
  void listQueuesIncludesTheSharedGroupQueue() throws Exception {
    List<Component> messages = captureMessages(() ->
        invoke(new QueueAdminCommand(server), "listQueues", context("")));

    // The manager exposes only the shared group queue, so one item row proves the group
    // is listed without depending on how Adventure renders named translation arguments.
    assertTrue(messages.stream().anyMatch(component ->
        key(component).equals("velocity.queue.command.listqueues.item")));
  }

  @Test
  void pauseAndUnpauseAcceptTheGroupName() throws Exception {
    QueueAdminCommand command = new QueueAdminCommand(server);
    when(groupQueue.getState()).thenReturn(QueueState.ACTIVE);

    invoke(command, "pause", context(GROUP));
    verify(groupQueue).setState(QueueState.PAUSED);

    when(groupQueue.getState()).thenReturn(QueueState.PAUSED);
    invoke(command, "unpause", context(GROUP));
    verify(groupQueue).setState(QueueState.ACTIVE);
  }

  @Test
  void queueAdminListShowsPlayersFromTheGroupQueue() throws Exception {
    VelocityQueueEntry entry = mock(VelocityQueueEntry.class);
    when(entry.getUsername()).thenReturn("alice");
    doReturn(List.of(entry)).when(groupQueue).getEntries();
    List<Component> messages = captureMessages(() ->
        invoke(new QueueAdminCommand(server), "list", context(GROUP)));

    assertTrue(messages.stream().anyMatch(component ->
        key(component).equals("velocity.queue.command.list.server")));
    verify(entry).getUsername();
  }

  @Test
  void leaveQueueAcceptsGroupName() throws Exception {
    ConnectedPlayer player = mock(ConnectedPlayer.class);
    when(player.getPermissionValue(anyString())).thenReturn(Tristate.UNDEFINED);
    when(groupQueue.contains(player)).thenReturn(true);
    CommandContext<CommandSource> ctx = context(GROUP);
    when(ctx.getSource()).thenReturn(player);

    invoke(new LeaveQueueCommand(server), "leaveQueue", ctx);

    verify(groupQueue).dequeue(player);
  }

  @Test
  void glistAggregatesAndDeduplicatesGroupBackends() throws Exception {
    UUID shared = UUID.randomUUID();
    UUID unique = UUID.randomUUID();
    VelocityClusterPlayer alice = clusterPlayer(shared, "alice");
    VelocityClusterPlayer bob = clusterPlayer(unique, "bob");
    when(clusterPlayers.getPlayersOnServer("Survival-01")).thenReturn(List.of(alice, bob));
    when(clusterPlayers.getPlayersOnServer("Survival-02")).thenReturn(List.of(alice));
    Method method = GlistCommand.class.getDeclaredMethod(
        "collectPlayers", Collection.class, Set.class);
    method.setAccessible(true);

    @SuppressWarnings("unchecked")
    List<VelocityClusterPlayer> found = (List<VelocityClusterPlayer>) method.invoke(
        new GlistCommand(server), MEMBERS, new HashSet<>());

    assertEquals(2, found.size());
    assertEquals(Set.of("alice", "bob"),
        found.stream().map(VelocityClusterPlayer::getUsername).collect(Collectors.toSet()));
  }

  @Test
  void plistAggregatesAndDeduplicatesGroupBackends() throws Exception {
    UUID shared = UUID.randomUUID();
    VelocityClusterPlayer alice = clusterPlayer(shared, "alice");
    when(clusterPlayers.getPlayersOnServer("Survival-01")).thenReturn(List.of(alice));
    when(clusterPlayers.getPlayersOnServer("Survival-02")).thenReturn(List.of(alice));
    List<Component> messages = captureMessages(() ->
        invoke(new PlistCommand(server), "serverCount", context(GROUP)));

    assertTrue(messages.stream().anyMatch(component ->
        key(component).equals("velocity.command.plist-server")));
    verify(clusterPlayers).getPlayersOnServer("Survival-01");
    verify(clusterPlayers).getPlayersOnServer("Survival-02");
  }

  private static VelocityClusterPlayer clusterPlayer(UUID id, String name) {
    VelocityClusterPlayer player = mock(VelocityClusterPlayer.class);
    when(player.getUniqueId()).thenReturn(id);
    when(player.getUsername()).thenReturn(name);
    when(player.getProxyId()).thenReturn("proxy-a");
    return player;
  }

  private static Object invoke(Object target, String name, CommandContext<CommandSource> ctx)
      throws Exception {
    Method method = target.getClass().getDeclaredMethod(name, CommandContext.class);
    method.setAccessible(true);
    return method.invoke(target, ctx);
  }

  private interface Action {
    void run() throws Exception;
  }

  private List<Component> captureMessages(Action action) throws Exception {
    ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
    action.run();
    verify(source, atLeastOnce()).sendMessage(captor.capture());
    return captor.getAllValues();
  }
}
