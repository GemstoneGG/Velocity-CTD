/*
 * Copyright (C) 2018-2025 Velocity Contributors
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

package com.velocitypowered.proxy.testutil;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.plugin.PluginManager;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * A fake plugin manager.
 */
public class FakePluginManager implements PluginManager {

  /**
   * A shared mock plugin instance representing plugin "a".
   *
   * <p>This object is used in testing scenarios to identify {@code PLUGIN_A} by reference
   * when resolving plugin containers via {@link #fromInstance(Object)}.</p>
   */
  public static final Object PLUGIN_A = new Object();

  /**
   * A shared mock plugin instance representing plugin "b".
   *
   * <p>This object is used in testing scenarios to identify {@code PLUGIN_B} by reference
   * when resolving plugin containers via {@link #fromInstance(Object)}.</p>
   */
  public static final Object PLUGIN_B = new Object();

  /**
   * A plugin container for {@link #PLUGIN_A}.
   */
  private final PluginContainer containerA = new FakePluginContainer("a", PLUGIN_A);

  /**
   * A plugin container for {@link #PLUGIN_B}.
   */
  private final PluginContainer containerB = new FakePluginContainer("b", PLUGIN_B);

  /**
   * A plugin container representing the built-in {@code velocity} plugin.
   */
  private final PluginContainer containerVelocity = new FakePluginContainer("velocity",
      VelocityVirtualPlugin.INSTANCE);

  /**
   * Shared executor service used to simulate plugin async operations during tests.
   *
   * <p>Threads are created with the name format {@code Test Async Thread} and are marked as daemon.</p>
   */
  private final ExecutorService service = Executors.newCachedThreadPool(
      new ThreadFactoryBuilder().setNameFormat("Test Async Thread").setDaemon(true).build());

  @Override
  public final @NonNull Optional<PluginContainer> fromInstance(@NonNull final Object instance) {
    if (instance == PLUGIN_A) {
      return Optional.of(containerA);
    } else if (instance == PLUGIN_B) {
      return Optional.of(containerB);
    } else if (instance == VelocityVirtualPlugin.INSTANCE) {
      return Optional.of(containerVelocity);
    } else {
      return Optional.empty();
    }
  }

  @Override
  public final @NonNull Optional<PluginContainer> getPlugin(@NonNull final String id) {
    return switch (id) {
      case "a" -> Optional.of(containerA);
      case "b" -> Optional.of(containerB);
      case "velocity" -> Optional.of(containerVelocity);
      default -> Optional.empty();
    };
  }

  @Override
  public final @NonNull Collection<PluginContainer> getPlugins() {
    return ImmutableList.of(containerVelocity, containerA, containerB);
  }

  @Override
  public final boolean isLoaded(@NonNull final String id) {
    return id.equals("a") || id.equals("b");
  }

  @Override
  public final void addToClasspath(@NonNull final Object plugin, @NonNull final Path path) {
    throw new UnsupportedOperationException();
  }

  /**
   * Shuts down the backing executor service immediately, cancelling all pending tasks.
   *
   * <p>This should be invoked after testing completes to ensure proper cleanup of test threads.</p>
   */
  public void shutdown() {
    this.service.shutdownNow();
  }

  private final class FakePluginContainer implements PluginContainer {

    /**
     * The plugin ID associated with this {@link PluginContainer}.
     */
    private final String id;

    /**
     * The plugin instance associated with this {@link PluginContainer}.
     */
    private final Object instance;

    private FakePluginContainer(final String id, final Object instance) {
      this.id = id;
      this.instance = instance;
    }

    @Override
    public @NonNull PluginDescription getDescription() {
      return () -> id;
    }

    @Override
    public Optional<?> getInstance() {
      return Optional.of(instance);
    }

    @Override
    public ExecutorService getExecutorService() {
      return service;
    }
  }
}
