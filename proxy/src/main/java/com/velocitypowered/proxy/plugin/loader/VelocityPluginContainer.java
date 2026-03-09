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

package com.velocitypowered.proxy.plugin.loader;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implements {@link PluginContainer}.
 */
public class VelocityPluginContainer implements PluginContainer {

  /**
   * The plugin's metadata, including ID, name, version, and dependencies.
   */
  private final PluginDescription description;

  /**
   * The instance of the plugin's main class, set after instantiation.
   */
  private Object instance;

  /**
   * Lazily initialized executor service used to run plugin tasks asynchronously.
   */
  private volatile ExecutorService service;

  /**
   * Constructs a new {@link VelocityPluginContainer} for the specified plugin.
   *
   * @param description the plugin's description metadata
   */
  public VelocityPluginContainer(final PluginDescription description) {
    this.description = description;
  }

  /**
   * Returns the plugin's {@link PluginDescription}.
   *
   * @return the plugin description
   */
  @Override
  public PluginDescription getDescription() {
    return this.description;
  }

  /**
   * Returns the instance of the plugin's main class, if it has been initialized.
   *
   * @return an {@link Optional} containing the plugin instance, or empty if not yet available
   */
  @Override
  public Optional<?> getInstance() {
    return Optional.ofNullable(instance);
  }

  /**
   * Sets the plugin instance created by the plugin loader.
   *
   * @param instance the plugin main class instance
   */
  public void setInstance(final Object instance) {
    this.instance = instance;
  }

  /**
   * Returns the {@link ExecutorService} used by this plugin for asynchronous task execution.
   *
   * <p>The service is created lazily and is named after the plugin for visibility in thread dumps.</p>
   *
   * @return the plugin's executor service
   */
  @Override
  public ExecutorService getExecutorService() {
    if (this.service == null) {
      synchronized (this) {
        if (this.service == null) {
          String name = this.description.getName().orElse(this.description.getId());
          this.service = Executors.unconfigurableExecutorService(
              Executors.newCachedThreadPool(new ThreadFactoryBuilder().setDaemon(true)
                  .setNameFormat(name + " - Task Executor #%d")
                  .setDaemon(true)
                  .build()
              )
          );
        }
      }
    }

    return this.service;
  }

  /**
   * Returns whether the executor service has already been created.
   *
   * @return {@code true} if the executor service is initialized, {@code false} otherwise
   */
  public boolean hasExecutorService() {
    return this.service != null;
  }
}
