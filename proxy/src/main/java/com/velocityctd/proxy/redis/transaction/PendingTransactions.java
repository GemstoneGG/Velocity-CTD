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

package com.velocityctd.proxy.redis.transaction;

import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks in-flight Redis transactions awaiting a reply, keyed by transaction id.
 */
public final class PendingTransactions {

  /**
   * The in-flight transactions, keyed by transaction id.
   */
  private final Map<UUID, PendingTransaction> transactions = new ConcurrentHashMap<>();

  /**
   * The scheduler used to schedule transaction timeout tasks.
   */
  private final Scheduler scheduler;

  /**
   * Constructs a new {@link PendingTransactions}.
   *
   * @param scheduler the scheduler to use for timeout tasks
   */
  public PendingTransactions(@NotNull Scheduler scheduler) {
    this.scheduler = scheduler;
  }

  /**
   * Registers a transaction and schedules its timeout. If a transaction with the same id is already
   * pending, it is replaced and its timeout task is cancelled.
   *
   * @param transaction the transaction to track; must not be null
   * @param delay       the delay after which the transaction times out
   * @param timeUnit    the time unit of {@code delay}; must not be null
   */
  public void put(@NotNull Transaction<?, ?> transaction, int delay, @NotNull TimeUnit timeUnit) {
    UUID key = transaction.getTransactionId();
    PendingTransaction pending = new PendingTransaction(transaction);

    PendingTransaction previous = this.transactions.put(key, pending);
    if (previous != null) {
      previous.cancelRefreshTask();
    }

    pending.setRefreshTask(this.scheduler
        .buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
          if (this.transactions.remove(key, pending)) {
            transaction.timeout();
          }
        })
        .delay(delay, timeUnit)
        .schedule());
  }

  /**
   * Removes and returns the transaction with the given id, cancelling its timeout task.
   *
   * @param key the transaction id; must not be null
   * @return the removed transaction, or {@code null} if no transaction was pending for the id
   */
  public @Nullable Transaction<?, ?> remove(@NotNull UUID key) {
    PendingTransaction removed = this.transactions.remove(key);
    if (removed == null) {
      return null;
    }

    removed.cancelRefreshTask();
    return removed.transaction();
  }

  /**
   * Pairs a pending transaction with its timeout task so both can be stored and evicted as a single
   * atomic map value.
   */
  private static final class PendingTransaction {

    private final Transaction<?, ?> transaction;
    private volatile @Nullable ScheduledTask refreshTask;

    private PendingTransaction(@NotNull Transaction<?, ?> transaction) {
      this.transaction = transaction;
    }

    private Transaction<?, ?> transaction() {
      return this.transaction;
    }

    private void setRefreshTask(@NotNull ScheduledTask refreshTask) {
      this.refreshTask = refreshTask;
    }

    private void cancelRefreshTask() {
      ScheduledTask task = this.refreshTask;
      if (task != null) {
        task.cancel();
        this.refreshTask = null;
      }
    }
  }
}
