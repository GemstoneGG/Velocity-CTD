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

import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SimpleCommand}.
 */
public class SimpleCommandTests extends CommandTestSuite {

  // Execution

  @Test
  void testExecutesAlias() {
    var callCount = new AtomicInteger();

    var meta = manager.metaBuilder("hello").build();
    manager.register(meta, (SimpleCommand) invocation -> {
      assertEquals(source, invocation.source());
      assertEquals("hello", invocation.alias());
      assertArrayEquals(new String[0], invocation.arguments());
      callCount.incrementAndGet();
    });

    assertHandled("hello");
    assertEquals(1, callCount.get());
  }

  @Test
  void testExecuteIgnoresAliasCase() {
    var callCount = new AtomicInteger();

    var meta = manager.metaBuilder("hello").build();
    manager.register(meta, (SimpleCommand) invocation -> {
      assertEquals("hello", invocation.alias());
      callCount.incrementAndGet();
    });

    assertHandled("Hello");
    assertEquals(1, callCount.get());
  }

  @Test
  void testExecuteInputIsTrimmed() {
    var callCount = new AtomicInteger();

    var meta = manager.metaBuilder("hello").build();
    manager.register(meta, (SimpleCommand) invocation -> {
      assertEquals("hello", invocation.alias());
      assertArrayEquals(new String[0], invocation.arguments());
      callCount.incrementAndGet();
    });

    assertHandled(" hello");
    assertHandled("  hello");
    assertHandled("hello ");
    assertHandled("hello   ");
    assertEquals(4, callCount.get());
  }

  @Test
  void testExecuteAfterUnregisterForwards() {
    var meta = manager.metaBuilder("hello").build();
    manager.register(meta, (SimpleCommand) invocation -> fail());
    manager.unregister("hello");

    assertForwarded("hello");
  }

  @Test
  void testForwardsAndDoesNotExecuteImpermissibleAlias() {
    var callCount = new AtomicInteger();

    var meta = manager.metaBuilder("hello").build();
    manager.register(meta, new SimpleCommand() {
      @Override
      public void execute(Invocation invocation) {
        fail();
      }

      @Override
      public boolean hasPermission(Invocation invocation) {
        assertEquals(source, invocation.source());
        assertEquals("hello", invocation.alias());
        assertArrayEquals(new String[0], invocation.arguments());
        callCount.incrementAndGet();
        return false;
      }
    });

    assertForwarded("hello");
    assertEquals(1, callCount.get());
  }

  @Test
  void testExecutesWithArguments() {
    var callCount = new AtomicInteger();

    var meta = manager.metaBuilder("hello").build();
    manager.register(meta, (SimpleCommand) invocation -> {
      assertEquals("hello", invocation.alias());
      assertArrayEquals(new String[]{"dear", "world"}, invocation.arguments());
      callCount.incrementAndGet();
    });

    assertHandled("hello dear world");
    assertEquals(1, callCount.get());
  }

  @Test
  void testHandlesAndDoesNotExecuteWithImpermissibleArgs() {
    var callCount = new AtomicInteger();

    var meta = manager.metaBuilder("color").build();
    manager.register(meta, new SimpleCommand() {
      @Override
      public void execute(Invocation invocation) {
        fail();
      }

      @Override
      public boolean hasPermission(Invocation invocation) {
        assertEquals("color", invocation.alias());
        assertArrayEquals(new String[]{"red"}, invocation.arguments());
        callCount.incrementAndGet();
        return false;
      }
    });

    assertHandled("color red");
    assertEquals(1, callCount.get());
  }

  // Suggestions

  @Test
  void testDoesNotSuggestAliasIfImpermissible() {
    var meta = manager.metaBuilder("hello").build();
    manager.register(meta, new SimpleCommand() {
      @Override
      public void execute(Invocation invocation) {
        fail();
      }

      @Override
      public boolean hasPermission(Invocation invocation) {
        assertEquals("hello", invocation.alias());
        assertArrayEquals(new String[0], invocation.arguments());
        return false;
      }

      @Override
      public List<String> suggest(Invocation invocation) {
        return fail();
      }
    });
  }

  @Test
  void testDoesNotSuggestAliasAfterUnregister() {
    var meta = manager.metaBuilder("hello").build();
    manager.register(meta, new SimpleCommand() {
      @Override
      public void execute(Invocation invocation) {
        fail();
      }

      @Override
      public List<String> suggest(Invocation invocation) {
        return fail();
      }
    });
    manager.unregister("hello");

    assertSuggestions("");
  }

  @Test
  void testSuggestsArgumentsAfterAlias() {
    var meta = manager.metaBuilder("hello").build();
    manager.register(meta, new SimpleCommand() {
      @Override
      public void execute(Invocation invocation) {
        fail();
      }

      @Override
      public List<String> suggest(Invocation invocation) {
        assertEquals("hello", invocation.alias());
        assertArrayEquals(new String[0], invocation.arguments());
        return ImmutableList.of("world", "people"); // ensures we don't mutate the user's list
      }
    });

    assertSuggestions("hello ", "people", "world"); // in alphabetical order
  }

  @Test
  void testSuggestsArgumentsAfterAliasIgnoresAliasCase() {
    var meta = manager.metaBuilder("hello").build();
    manager.register(meta, new SimpleCommand() {
      @Override
      public void execute(Invocation invocation) {
        fail();
      }

      @Override
      public List<String> suggest(Invocation invocation) {
        assertEquals("hello", invocation.alias());
        return ImmutableList.of("world");
      }
    });

    assertSuggestions("Hello ", "world");
  }

  @Test
  void testSuggestsArgumentsAfterPartialArguments() {
    var meta = manager.metaBuilder("numbers").build();
    manager.register(meta, new SimpleCommand() {
      @Override
      public void execute(Invocation invocation) {
        fail();
      }

      @Override
      public List<String> suggest(Invocation invocation) {
        assertArrayEquals(new String[]{"12345678"}, invocation.arguments());
        return Collections.singletonList("9");
      }
    });

    assertSuggestions("numbers 12345678", "9");
  }

  @Test
  void testDoesNotSuggestFirstArgumentIfImpermissibleAlias() {
    var callCount = new AtomicInteger();

    var meta = manager.metaBuilder("hello").build();
    manager.register(meta, new SimpleCommand() {
      @Override
      public void execute(Invocation invocation) {
        fail();
      }

      @Override
      public boolean hasPermission(Invocation invocation) {
        assertEquals("hello", invocation.alias());
        assertArrayEquals(new String[0], invocation.arguments());
        callCount.incrementAndGet();
        return false;
      }

      @Override
      public List<String> suggest(Invocation invocation) {
        return fail();
      }
    });

    assertSuggestions("hello ");
    assertEquals(1, callCount.get());
  }

  @Test
  void testDoesNotSuggestArgumentsAfterPartialImpermissibleArguments() {
    var callCount = new AtomicInteger();

    var meta = manager.metaBuilder("foo").build();
    manager.register(meta, new SimpleCommand() {
      @Override
      public void execute(Invocation invocation) {
        fail();
      }

      @Override
      public boolean hasPermission(Invocation invocation) {
        assertEquals("foo", invocation.alias());
        assertArrayEquals(new String[]{"bar", "baz", ""}, invocation.arguments());
        callCount.incrementAndGet();
        return false;
      }

      @Override
      public List<String> suggest(Invocation invocation) {
        return fail();
      }
    });

    assertSuggestions("foo bar baz ");
    assertEquals(1, callCount.get());
  }

  @Test
  void testDoesNotSuggestIfFutureCompletesExceptionally() {
    var meta = manager.metaBuilder("hello").build();
    manager.register(meta, new SimpleCommand() {
      @Override
      public void execute(Invocation invocation) {
        fail();
      }

      @Override
      public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.failedFuture(new RuntimeException());
      }
    });

    assertSuggestions("hello ");
  }

  @Test
  void testDoesNotSuggestIfSuggestAsyncThrows() {
    var meta = manager.metaBuilder("hello").build();
    manager.register(meta, new SimpleCommand() {
      @Override
      public void execute(Invocation invocation) {
        fail();
      }

      @Override
      public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        throw new RuntimeException();
      }
    });

    // Also logs an error to the console, but testing this is quite involved
    assertSuggestions("hello ");
  }

  @Test
  void testSuggestCompletesExceptionallyIfHasPermissionThrows() {
    var meta = manager.metaBuilder("hello").build();
    manager.register(meta, new SimpleCommand() {
      @Override
      public void execute(Invocation invocation) {
        fail();
      }

      @Override
      public boolean hasPermission(Invocation invocation) {
        throw new RuntimeException();
      }

      @Override
      public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return fail();
      }
    });

    assertThrows(CompletionException.class, () ->
        manager.offerSuggestions(source, "hello ").join());
  }

  // Hinting

  // Even if the following 2 cases look really similar, they test
  // different parts of SuggestionsProvider.
  @Test
  void testDoesNotSuggestHintIfImpermissibleAlias() {
    var hint = LiteralArgumentBuilder
        .<CommandSource>literal("hint")
        .build();
    var meta = manager.metaBuilder("hello")
        .hint(hint)
        .build();
    manager.register(meta, new SimpleCommand() {
      @Override
      public void execute(Invocation invocation) {
        fail();
      }

      @Override
      public boolean hasPermission(Invocation invocation) {
        return false;
      }
    });

    assertSuggestions("hello ");
  }

  @Test
  void testDoesNotSuggestHintIfImpermissibleArguments() {
    var hint = LiteralArgumentBuilder
        .<CommandSource>literal("hint")
        .build();
    var meta = manager.metaBuilder("hello")
        .hint(hint)
        .build();
    manager.register(meta, new SimpleCommand() {
      @Override
      public void execute(Invocation invocation) {
        fail();
      }

      @Override
      public boolean hasPermission(Invocation invocation) {
        return false;
      }
    });

    assertSuggestions("hello hin");
  }

  @Test
  void testSuggestsMergesIgnoringHintsWhoseCustomSuggestionProviderFutureCompletesExceptionally() {
    var hint = RequiredArgumentBuilder
        .<CommandSource, String>argument("hint", word())
        .suggests((context, builder) -> CompletableFuture.failedFuture(new RuntimeException()))
        .build();
    var meta = manager.metaBuilder("hello")
        .hint(hint)
        .build();
    manager.register(meta, new SimpleCommand() {
      @Override
      public void execute(Invocation invocation) {
        fail();
      }

      @Override
      public List<String> suggest(Invocation invocation) {
        return ImmutableList.of("world");
      }
    });

    assertSuggestions("hello ", "world");
  }

  @Test
  void testSuggestsMergesIgnoringHintsWhoseCustomSuggestionProviderThrows() {
    var hint = RequiredArgumentBuilder
        .<CommandSource, String>argument("hint", word())
        .suggests((context, builder) -> {
          throw new RuntimeException();
        })
        .build();
    var meta = manager.metaBuilder("hello")
        .hint(hint)
        .build();
    manager.register(meta, new SimpleCommand() {
      @Override
      public void execute(Invocation invocation) {
        fail();
      }

      @Override
      public List<String> suggest(Invocation invocation) {
        return ImmutableList.of("world");
      }
    });

    assertSuggestions("hello ", "world");
  }
}
