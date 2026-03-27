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

package com.velocityctd.proxy.redis.transaction;

import com.velocityctd.proxy.redis.VelocityRedis;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a map implementation of a transaction cache.
 */
public final class TransactionCache extends HashMap<UUID, Transaction<?, ?>> {

  /**
   * Tracks scheduled timeout tasks for each transaction ID stored in the cache.
   */
  private final @MonotonicNonNull HashMap<UUID, ScheduledTask> refreshTasks;

  /**
   * Default delay used for scheduling transaction timeout tasks.
   */
  private final double delay;

  /**
   * Time unit associated with the default delay.
   */
  private final TimeUnit timeUnit;

  /**
   * Consumer invoked when a transaction is purged from the cache due to timeout.
   */
  private BiConsumer<UUID, Transaction<?, ?>> purgeConsumer;

  /**
   * Constructs a new {@link TransactionCache}.
   */
  public TransactionCache() {
    this(Transaction.DEFAULT_TIMEOUT, Transaction.DEFAULT_TIME_UNIT);
  }

  /**
   * Constructs a new {@link TransactionCache}.
   *
   * @param delay the delay of the refresh tasks
   * @param timeUnit the time unit of the delay argument
   */
  public TransactionCache(final double delay, final TimeUnit timeUnit) {
    this.refreshTasks = new HashMap<>();
    this.delay = delay;
    this.timeUnit = timeUnit;
  }

  /**
   * Constructs a new {@link TransactionCache}.
   *
   * @param purgeConsumer the purge consumer to call when a transaction is purged
   */
  public TransactionCache(final BiConsumer<UUID, Transaction<?, ?>> purgeConsumer) {
    this();
    this.purgeConsumer = purgeConsumer;
  }

  @Override
  public Transaction<?, ?> put(final UUID key, final Transaction<?, ?> value) {
    this.queue(value, this.delay, this.timeUnit);
    return super.put(key, value);
  }

  /**
   * Adds the specified transaction to the cache, associates it with its unique transaction ID,
   * and schedules it for refresh based on the provided delay and time unit. If a transaction with
   * the same ID already exists, it will be replaced, and any previous scheduling tasks will
   * be canceled.
   *
   * @param value the transaction to be added to the cache; must not be null
   * @param delay the delay, in the specified time unit, after which the transaction will be processed
   * @param timeUnit the time unit of the delay parameter; must not be null
   * @return the previous transaction associated with the transaction ID, or null if there was no mapping
   */
  public Transaction<?, ?> put(final @NotNull Transaction<?, ?> value, final int delay, final TimeUnit timeUnit) {
    this.queue(value, delay, timeUnit);
    return super.put(value.getTransactionId(), value);
  }

  @Override
  public Transaction<?, ?> remove(final Object key) {
    final ScheduledTask scheduledTask = this.refreshTasks.remove(key);

    if (scheduledTask != null) {
      scheduledTask.cancel();
    }

    return super.remove(key);
  }

  /**
   * Schedules a task to process the specified transaction after a defined delay. If there is an
   * existing-scheduled task for the same transaction ID, it will be canceled before scheduling the new task.
   *
   * @param transaction the transaction to be processed; must not be null
   * @param delay the delay, after which the transaction will be processed
   * @param timeUnit the time unit used for the delay parameter; must not be null
   */
  private void queue(final @NotNull Transaction<?, ?> transaction, final double delay, final TimeUnit timeUnit) {
    final UUID key = transaction.getTransactionId();

    if (this.refreshTasks.containsKey(key)) {
      this.refreshTasks.get(key).cancel();
      this.refreshTasks.remove(key);
    }

    final ScheduledTask scheduledTask = VelocityRedis.INSTANCE.getServer().getScheduler()
            .buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
              this.remove(key);
              this.purgeConsumer.accept(key, transaction);
            })
            .delay((long) delay, timeUnit)
            .schedule();

    this.refreshTasks.put(key, scheduledTask);
  }
}
