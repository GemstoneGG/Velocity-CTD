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
  private final PluginContainer containerVelocity = new FakePluginContainer("velocityctd",
      VelocityVirtualPlugin.INSTANCE);

  /**
   * Shared executor service used to simulate plugin async operations during tests.
   *
   * <p>Threads are created with the name format {@code Test Async Thread} and are marked as daemon.</p>
   */
  private final ExecutorService service = Executors.newCachedThreadPool(
      new ThreadFactoryBuilder().setNameFormat("Test Async Thread").setDaemon(true).build());

  /**
   * Returns a {@link PluginContainer} based on a known test plugin instance.
   *
   * @param instance the plugin instance
   * @return the associated plugin container, or {@code Optional.empty()} if unknown
   */
  @Override
  public @NonNull Optional<PluginContainer> fromInstance(final @NonNull Object instance) {
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

  /**
   * Returns a {@link PluginContainer} based on a known test plugin ID.
   *
   * @param id the plugin ID
   * @return the plugin container if registered, or {@code Optional.empty()}
   */
  @Override
  public @NonNull Optional<PluginContainer> getPlugin(final @NonNull String id) {
    return switch (id) {
      case "a" -> Optional.of(containerA);
      case "b" -> Optional.of(containerB);
      case "velocityctd" -> Optional.of(containerVelocity);
      default -> Optional.empty();
    };
  }

  /**
   * Returns all registered plugin containers including {@code velocity}, {@code a}, and {@code b}.
   *
   * @return an immutable list of known plugin containers
   */
  @Override
  public @NonNull Collection<PluginContainer> getPlugins() {
    return ImmutableList.of(containerVelocity, containerA, containerB);
  }

  /**
   * Determines whether the specified plugin is loaded.
   *
   * <p>Only test plugins with the IDs {@code "a"} or {@code "b"} are considered loaded
   * in this simulated environment.</p>
   *
   * @param id the plugin ID to check
   * @return {@code true} if the plugin is "a" or "b", otherwise {@code false}
   */
  @Override
  public boolean isLoaded(final @NonNull String id) {
    return id.equals("a") || id.equals("b");
  }

  /**
   * Unsupported operation in the mock plugin manager.
   *
   * <p>This method is not implemented in test environments and always throws
   * {@link UnsupportedOperationException} if called.</p>
   *
   * @param plugin the plugin instance
   * @param path the path to add to the plugin's classpath
   * @throws UnsupportedOperationException always
   */
  @Override
  public void addToClasspath(final @NonNull Object plugin, final @NonNull Path path) {
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
