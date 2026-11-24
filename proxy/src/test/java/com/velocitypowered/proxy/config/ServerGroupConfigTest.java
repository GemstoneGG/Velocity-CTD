/*
 * Copyright (C) 2026 Velocity Contributors
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.velocityctd.api.queue.SelectionStrategy;
import java.lang.reflect.Constructor;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for parsing logical server groups from the queue configuration.
 */
class ServerGroupConfigTest {

  private static VelocityConfiguration.Queue queue(CommentedConfig root) throws Exception {
    Constructor<VelocityConfiguration.Queue> constructor =
        VelocityConfiguration.Queue.class.getDeclaredConstructor(CommentedConfig.class);
    constructor.setAccessible(true);
    return constructor.newInstance(root);
  }

  private static CommentedConfig section(CommentedConfig root) {
    CommentedConfig section = CommentedConfig.inMemory();
    root.set("groups", section);
    return section;
  }

  private static void group(CommentedConfig container, String name,
                            List<String> servers, String strategy, int maxPlayers) {
    CommentedConfig entry = CommentedConfig.inMemory();
    entry.set("servers", servers);
    if (strategy != null) {
      entry.set("strategy", strategy);
    }
    entry.set("max-players", maxPlayers);
    container.set(name, entry);
  }

  @Test
  void missingGroupSectionIsBackwardsCompatible() throws Exception {
    assertTrue(queue(CommentedConfig.inMemory()).getGroups().isEmpty());
  }

  @Test
  void emptyGroupIsIgnored() throws Exception {
    CommentedConfig root = CommentedConfig.inMemory();
    CommentedConfig section = section(root);
    section.set("empty", List.of());
    section.set("blank", "");

    assertTrue(queue(root).getGroups().isEmpty());
  }

  @Test
  void duplicateMembersAreRemovedKeepingConfiguredOrder() throws Exception {
    CommentedConfig root = CommentedConfig.inMemory();
    group(section(root), "lobby",
        List.of("Lobby-01", "Lobby-02", "Lobby-01", "Lobby-03", "Lobby-02"), null, -1);

    assertEquals(List.of("Lobby-01", "Lobby-02", "Lobby-03"),
        queue(root).getGroups().get("lobby").servers());
  }

  @Test
  void reservedGroupNamesAreRejected() throws Exception {
    CommentedConfig root = CommentedConfig.inMemory();
    CommentedConfig section = section(root);
    group(section, "all", List.of("A"), null, -1);
    group(section, "CURRENT", List.of("B"), null, -1);

    assertFalse(queue(root).getGroups().containsKey("all"));
    assertFalse(queue(root).getGroups().containsKey("CURRENT"));
  }

  @Test
  void invalidStrategyWarnsAndDefaultsToLeastPlayers() throws Exception {
    CommentedConfig root = CommentedConfig.inMemory();
    group(section(root), "g", List.of("A"), "not-a-strategy", -1);

    assertEquals(SelectionStrategy.LEAST_PLAYERS,
        queue(root).getGroups().get("g").strategy());
  }

  @Test
  void strategyNamesAreNormalized() throws Exception {
    CommentedConfig root = CommentedConfig.inMemory();
    group(section(root), "g", List.of("A"), "ROUND-ROBIN", -1);

    assertEquals(SelectionStrategy.ROUND_ROBIN,
        queue(root).getGroups().get("g").strategy());
  }

  @Test
  void plainListGroupUsesDefaultStrategyAndNoLimit() throws Exception {
    CommentedConfig root = CommentedConfig.inMemory();
    section(root).set("lobby", List.of("Lobby-01", "Lobby-02"));

    assertEquals(SelectionStrategy.LEAST_PLAYERS,
        queue(root).getGroups().get("lobby").strategy());
    assertEquals(-1, queue(root).getGroups().get("lobby").maxPlayers());
  }

  @Test
  void nonPositivePlayerLimitIsNormalized() throws Exception {
    CommentedConfig root = CommentedConfig.inMemory();
    group(section(root), "g", List.of("A"), null, 0);

    assertEquals(-1, queue(root).getGroups().get("g").maxPlayers());
  }

  @Test
  void validGroupDefinitionKeepsItsPlayerLimit() throws Exception {
    CommentedConfig root = CommentedConfig.inMemory();
    group(section(root), "g", List.of("A", "B"), "last_server", 60);

    VelocityConfiguration.Queue.ServerGroupDefinition definition =
        queue(root).getGroups().get("g");
    assertEquals(List.of("A", "B"), definition.servers());
    assertEquals(SelectionStrategy.LAST_SERVER, definition.strategy());
    assertEquals(60, definition.maxPlayers());
  }
}
