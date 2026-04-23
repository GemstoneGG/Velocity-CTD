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

package com.velocitypowered.proxy.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.RawCommand;
import com.velocitypowered.api.command.SimpleCommand;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * Command manager tests.
 */
public class CommandManagerTests extends CommandTestSuite {

  // Registration

  @Test
  void testRegisterWithMeta() {
    var meta = manager.metaBuilder("hello").build();
    manager.register(meta, DummyCommand.INSTANCE);

    assertTrue(manager.hasCommand("hello"));
    assertRegisteredAliases("hello");
    assertEquals(meta, manager.getCommandMeta("hello"));
  }

  @Test
  void testRegisterWithMetaContainingMultipleAliases() {
    var meta = manager.metaBuilder("foo")
        .aliases("bar")
        .aliases("baz", "qux")
        .build();
    manager.register(meta, DummyCommand.INSTANCE);

    assertTrue(manager.hasCommand("foo"));
    assertTrue(manager.hasCommand("bar"));
    assertTrue(manager.hasCommand("baz"));
    assertTrue(manager.hasCommand("qux"));
    assertRegisteredAliases("foo", "bar", "baz", "qux");
    assertEquals(meta, manager.getCommandMeta("foo"));
    assertEquals(meta, manager.getCommandMeta("bar"));
    assertEquals(meta, manager.getCommandMeta("baz"));
    assertEquals(meta, manager.getCommandMeta("qux"));
  }

  @Test
  void testRegisterAliasesAreCaseInsensitive() {
    var meta = manager.metaBuilder("Foo")
        .aliases("Bar")
        .build();
    manager.register(meta, DummyCommand.INSTANCE);

    assertTrue(manager.hasCommand("foo"));
    assertTrue(manager.hasCommand("bar"));
    assertRegisteredAliases("foo", "bar");
  }

  @Test
  void testRegisterBrigadierCommand() {
    var node = LiteralArgumentBuilder
        .<CommandSource>literal("hello")
        .build();
    manager.register(new BrigadierCommand(node));

    assertTrue(manager.hasCommand("hello"));
    assertRegisteredAliases("hello");
  }

  @Test
  void testRegisterOverridesPreviousCommand() {
    var called = new AtomicBoolean();

    var oldMeta = manager.metaBuilder("foo").build();
    manager.register(oldMeta, DummyCommand.INSTANCE); // fails on execution
    assertEquals(oldMeta, manager.getCommandMeta("foo"));

    var newMeta = manager.metaBuilder("foo").build();
    manager.register(newMeta, (RawCommand) invocation -> called.set(true));
    assertEquals(newMeta, manager.getCommandMeta("foo"));
    manager.executeAsync(MockCommandSource.INSTANCE, "foo").join();

    assertTrue(called.get());
  }

  @Test
  void testAddingExecutableHintToMetaThrows() {
    var hintNode = LiteralArgumentBuilder
        .<CommandSource>literal("hint")
        .executes(context -> fail())
        .build();

    assertThrows(IllegalArgumentException.class, () -> manager.metaBuilder("hello").hint(hintNode));
  }

  @Test
  void testAddingHintWithRedirectToMetaThrows() {
    var targetNode = LiteralArgumentBuilder
        .<CommandSource>literal("target")
        .build();
    var hintNode = LiteralArgumentBuilder
        .<CommandSource>literal("origin")
        .redirect(targetNode)
        .build();

    assertThrows(IllegalArgumentException.class, () -> manager.metaBuilder("hello").hint(hintNode));
  }

  // Un-registration

  @Test
  void testUnregisterUnregisteredAliasIsIgnored() {
    manager.unregister("hello");

    assertFalse(manager.hasCommand("hello"));
    assertRegisteredAliases();
  }

  @Test
  void testUnregisterRegisteredAlias() {
    var meta = manager.metaBuilder("hello").build();
    manager.register(meta, DummyCommand.INSTANCE);
    manager.unregister("hello");

    assertFalse(manager.hasCommand("hello"));
    assertRegisteredAliases();
  }

  @Test
  void testUnregisterSecondaryAlias() {
    var meta = manager.metaBuilder("foo")
        .aliases("bar")
        .build();
    manager.register(meta, DummyCommand.INSTANCE);
    manager.unregister("bar");

    assertFalse(manager.hasCommand("bar"));
    assertTrue(manager.hasCommand("foo"));
    assertEquals(meta, manager.getCommandMeta("foo"));
    assertRegisteredAliases("foo");
  }

  @Test
  void testUnregisterAllAliases() {
    var meta = manager.metaBuilder("foo")
        .aliases("bar")
        .build();
    manager.register(meta, DummyCommand.INSTANCE);
    manager.unregister(meta);

    assertFalse(manager.hasCommand("bar"));
    assertFalse(manager.hasCommand("foo"));
  }

  @Test
  void testUnregisterAliasOverlap() {
    var meta1 = manager.metaBuilder("foo")
        .aliases("bar")
        .build();
    manager.register(meta1, DummyCommand.INSTANCE);
    var meta2 = manager.metaBuilder("bar")
        .build();
    manager.register(meta2, DummyCommand.INSTANCE);
    assertEquals(meta1, manager.getCommandMeta("foo"));
    assertEquals(meta2, manager.getCommandMeta("bar"));

    manager.unregister(meta1);
    assertNull(manager.getCommandMeta("foo"));
    assertEquals(meta2, manager.getCommandMeta("bar"));
  }

  // Execution

  @Test
  void testExecuteUnknownAliasIsForwarded() {
    assertForwarded("");
    assertForwarded("hello");
  }

  // Suggestions

  @Test
  void testEmptyManagerSuggestNoAliases() {
    assertSuggestions("");
  }

  static final class DummyCommand implements SimpleCommand {

    /**
     * Singleton instance of {@link DummyCommand}, used as a placeholder
     * for command registration tests where execution should fail.
     */
    static final DummyCommand INSTANCE = new DummyCommand();

    private DummyCommand() {
    }

    @Override
    public void execute(Invocation invocation) {
      fail();
    }

    @Override
    public List<String> suggest(Invocation invocation) {
      return fail();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
      return fail();
    }
  }
}
