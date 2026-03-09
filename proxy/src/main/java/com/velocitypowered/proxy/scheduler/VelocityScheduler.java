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

package com.velocitypowered.proxy.scheduler;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginManager;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import com.velocitypowered.api.scheduler.TaskStatus;
import com.velocitypowered.proxy.plugin.loader.VelocityPluginContainer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * The Velocity "scheduler", which is actually a thin wrapper around
 * {@link ScheduledExecutorService} and a dynamically-sized {@link ExecutorService}.
 * Many plugins are accustomed to the Bukkit Scheduler model, although it is not relevant
 * in a proxy context.
 */
public class VelocityScheduler implements Scheduler {

  /**
   * The plugin manager used to resolve plugin instances.
   */
  private final PluginManager pluginManager;

  /**
   * The scheduler backend that's used to manage scheduling delays and repeats.
   */
  private final SchedulerBackend backend;

  /**
   * A multimap of plugin instances to their active scheduled tasks.
   */
  private final Multimap<Object, ScheduledTask> tasksByPlugin = Multimaps.synchronizedMultimap(
      Multimaps.newSetMultimap(new IdentityHashMap<>(), HashSet::new));

  /**
   * Initializes the scheduler.
   *
   * @param pluginManager the Velocity plugin manager
   */
  public VelocityScheduler(final PluginManager pluginManager) {
    this(pluginManager, new ExecutorSchedulerBackend());
  }

  @VisibleForTesting
  VelocityScheduler(PluginManager pluginManager, SchedulerBackend backend) {
    this.pluginManager = pluginManager;
    this.backend = backend;
  }

  /**
   * Creates a new {@link TaskBuilder} to construct a scheduled task that runs the given {@link Runnable}.
   *
   * @param plugin the plugin creating the task
   * @param runnable the logic to execute
   * @return a builder for scheduling the task
   * @throws NullPointerException if {@code plugin} or {@code runnable} is {@code null}
   * @throws IllegalArgumentException if {@code plugin} is not registered
   */
  @Override
  public TaskBuilder buildTask(final @NotNull Object plugin, final @NotNull Runnable runnable) {
    checkNotNull(plugin, "plugin");
    checkNotNull(runnable, "runnable");
    final Optional<PluginContainer> container = pluginManager.fromInstance(plugin);
    checkArgument(container.isPresent(), "plugin is not registered");
    return new TaskBuilderImpl(container.get(), runnable);
  }

  /**
   * Creates a new {@link TaskBuilder} to construct a scheduled task that executes the given {@link Consumer}.
   *
   * <p>The consumer receives the {@link ScheduledTask} instance when the task is run.</p>
   *
   * @param plugin the plugin creating the task
   * @param consumer the task logic receiving the task context
   * @return a builder for scheduling the task
   * @throws NullPointerException if {@code plugin} or {@code consumer} is {@code null}
   * @throws IllegalArgumentException if {@code plugin} is not registered
   */
  @Override
  public TaskBuilder buildTask(final @NotNull Object plugin, final @NotNull Consumer<ScheduledTask> consumer) {
    checkNotNull(plugin, "plugin");
    checkNotNull(consumer, "consumer");
    final Optional<PluginContainer> container = pluginManager.fromInstance(plugin);
    checkArgument(container.isPresent(), "plugin is not registered");
    return new TaskBuilderImpl(container.get(), consumer);
  }

  /**
   * Retrieves all active tasks associated with the given plugin.
   *
   * @param plugin the plugin to query tasks for
   * @return an immutable view of tasks owned by the plugin
   * @throws NullPointerException if {@code plugin} is {@code null}
   * @throws IllegalArgumentException if {@code plugin} is not registered
   */
  @Override
  public @NonNull Collection<ScheduledTask> tasksByPlugin(final @NonNull Object plugin) {
    checkNotNull(plugin, "plugin");
    checkArgument(pluginManager.fromInstance(plugin).isPresent(), "plugin is not registered");
    final Collection<ScheduledTask> tasks = tasksByPlugin.get(plugin);
    synchronized (tasksByPlugin) {
      return Set.copyOf(tasks);
    }
  }

  /**
   * Shuts down the Velocity scheduler.
   *
   * @return {@code true} if all tasks finished, {@code false} otherwise
   * @throws InterruptedException if the current thread was interrupted
   */
  public boolean shutdown() throws InterruptedException {
    Collection<ScheduledTask> terminating;
    synchronized (tasksByPlugin) {
      terminating = ImmutableList.copyOf(tasksByPlugin.values());
    }

    for (ScheduledTask task : terminating) {
      task.cancel();
    }

    backend.shutdown();
    final List<PluginContainer> plugins = new ArrayList<>(this.pluginManager.getPlugins());
    final Iterator<PluginContainer> pluginIterator = plugins.iterator();
    while (pluginIterator.hasNext()) {
      final PluginContainer container = pluginIterator.next();
      if (container instanceof VelocityPluginContainer pluginContainer) {
        if (pluginContainer.hasExecutorService()) {
          container.getExecutorService().shutdown();
        } else {
          pluginIterator.remove();
        }
      } else {
        pluginIterator.remove();
      }
    }

    boolean allShutdown = true;
    for (final PluginContainer container : plugins) {
      final String id = container.getDescription().getId();
      final ExecutorService service = (container).getExecutorService();

      try {
        if (!service.awaitTermination(10, TimeUnit.SECONDS)) {
          service.shutdownNow();
          Log.logger.warn("Executor for plugin {} did not shut down within 10 seconds. "
              + "Continuing with shutdown...", id);
          allShutdown = false;
        }

      } catch (final InterruptedException e) {
        Log.logger.warn("Executor for plugin {} did not shut down within 10 seconds. "
            + "Continuing with shutdown...", id);
      }
    }

    return allShutdown;
  }

  private final class TaskBuilderImpl implements TaskBuilder {

    /**
     * The plugin that owns the task being built.
     */
    private final PluginContainer container;

    /**
     * The runnable logic to execute when the task runs. May be {@code null} if a consumer is used instead.
     */
    private final Runnable runnable;

    /**
     * The consumer that receives the {@link ScheduledTask} instance when the task runs.
     * May be {@code null} if a runnable is used instead.
     */
    private final Consumer<ScheduledTask> consumer;

    /**
     * The delay (in milliseconds) before the task is first executed.
     */
    private long delay;

    /**
     * The repeat interval (in milliseconds) for executing the task repeatedly.
     * A value of {@code 0} indicates the task should only run once.
     */
    private long repeat;

    private TaskBuilderImpl(final PluginContainer container, final Consumer<ScheduledTask> consumer) {
      this.container = container;
      this.consumer = consumer;
      this.runnable = null;
    }

    private TaskBuilderImpl(final PluginContainer container, final Runnable runnable) {
      this.container = container;
      this.consumer = null;
      this.runnable = runnable;
    }

    @Override
    public TaskBuilder delay(final long time, final @NotNull TimeUnit unit) {
      this.delay = unit.toMillis(time);
      return this;
    }

    @Override
    public TaskBuilder repeat(final long time, final @NotNull TimeUnit unit) {
      this.repeat = unit.toMillis(time);
      return this;
    }

    @Override
    public TaskBuilder clearDelay() {
      this.delay = 0;
      return this;
    }

    @Override
    public TaskBuilder clearRepeat() {
      this.repeat = 0;
      return this;
    }

    @Override
    public ScheduledTask schedule() {
      VelocityTask task = new VelocityTask(container, runnable, consumer, delay, repeat);
      container.getInstance().ifPresent(instance -> tasksByPlugin.put(instance, task));
      task.schedule();
      return task;
    }
  }

  @VisibleForTesting
  final class VelocityTask implements Runnable, ScheduledTask {

    /**
     * The plugin container that owns this scheduled task.
     */
    private final PluginContainer container;

    /**
     * The runnable logic to execute for this task.
     * May be {@code null} if {@code consumer} is used instead.
     */
    private final Runnable runnable;

    /**
     * The consumer logic to execute for this task, receiving the {@link ScheduledTask} as input.
     * May be {@code null} if {@code runnable} is used instead.
     */
    private final Consumer<ScheduledTask> consumer;

    /**
     * The initial delay (in milliseconds) before the task is first run.
     */
    private final long delay;

    /**
     * The repeat interval (in milliseconds) for this task. A value of {@code 0} means the task runs only once.
     */
    private final long repeat;

    /**
     * The scheduled future that tracks the execution state of this task.
     */
    private @Nullable ScheduledFuture<?> future;

    /**
     * The thread currently running this task, if any.
     * Used to allow interruption on cancellation.
     */
    private volatile @Nullable Thread currentTaskThread;

    private VelocityTask(final PluginContainer container, final Runnable runnable,
                         final Consumer<ScheduledTask> consumer, final long delay, final long repeat) {
      this.container = container;
      this.runnable = runnable;
      this.consumer = consumer;
      this.delay = delay;
      this.repeat = repeat;
    }

    void schedule() {
      if (repeat == 0) {
        this.future = backend.schedule(this, delay, TimeUnit.MILLISECONDS);
      } else {
        this.future = backend.scheduleAtFixedRate(this, delay, repeat, TimeUnit.MILLISECONDS);
      }
    }

    @Override
    public @NotNull Object plugin() {
      // noinspection OptionalGetWithoutIsPresent
      return container.getInstance().get();
    }

    @Override
    public TaskStatus status() {
      if (future == null) {
        return TaskStatus.SCHEDULED;
      }

      if (future.isCancelled()) {
        return TaskStatus.CANCELLED;
      }

      if (future.isDone()) {
        return TaskStatus.FINISHED;
      }

      return TaskStatus.SCHEDULED;
    }

    @Override
    public void cancel() {
      if (future != null) {
        future.cancel(false);

        Thread cur = currentTaskThread;
        if (cur != null) {
          cur.interrupt();
        }

        onFinish();
      }
    }

    @Override
    public void run() {
      container.getExecutorService().execute(() -> {
        currentTaskThread = Thread.currentThread();
        try {
          if (runnable != null) {
            runnable.run();
          } else {
            consumer.accept(this);
          }
        } catch (Throwable e) {
          // noinspection ConstantConditions
          if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
          } else {
            String friendlyPluginName = container.getDescription().getName()
                .orElse(container.getDescription().getId());
            Object unit = consumer == null ? runnable : consumer;
            Log.logger.error("Exception in task {} by plugin {}", unit, friendlyPluginName,
                e);
          }
        } finally {
          if (repeat == 0) {
            onFinish();
          }
          currentTaskThread = null;
        }
      });
    }

    private void onFinish() {
      tasksByPlugin.remove(plugin(), this);
    }

    public void awaitCompletion() {
      try {
        Objects.requireNonNull(future).get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (ExecutionException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static final class Log {

    /**
     * The Log4j logger instance used for reporting task-related exceptions and shutdown warnings.
     */
    private static final Logger logger = LogManager.getLogger(VelocityTask.class);
  }
}
