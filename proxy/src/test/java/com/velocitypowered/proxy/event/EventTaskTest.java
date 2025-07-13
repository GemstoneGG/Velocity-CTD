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

package com.velocitypowered.proxy.event;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.EventTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link EventTask}.
 */
public class EventTaskTest {

  /**
   * Tests that a completed {@link CompletableFuture} resumes the continuation successfully.
   */
  @Test
  public void testResumeWhenCompleteNormal() {
    WitnessContinuation continuation = new WitnessContinuation();
    CompletableFuture<Void> completed = CompletableFuture.completedFuture(null);
    EventTask.resumeWhenComplete(completed).execute(continuation);
    assertTrue(continuation.completedSuccessfully(), "Completed future did not "
        + "complete successfully");
  }

  /**
   * Tests that a failed {@link CompletableFuture} resumes the continuation with an error.
   */
  @Test
  public void testResumeWhenCompleteException() {
    WitnessContinuation continuation = new WitnessContinuation();
    CompletableFuture<Void> failed = CompletableFuture.failedFuture(new Throwable());
    EventTask.resumeWhenComplete(failed).execute(continuation);
    assertTrue(continuation.completedWithError(), "Failed future completed successfully");
  }

  /**
   * Tests that a successful {@link CompletableFuture} resumed from another thread
   * resumes the continuation normally.
   *
   * @throws InterruptedException if the latch await is interrupted
   */
  @Test
  public void testResumeWhenCompleteFromOtherThread() throws InterruptedException {
    WitnessContinuation continuation = new WitnessContinuation();
    CountDownLatch latch = new CountDownLatch(1);
    continuation.onComplete = (ignored) -> latch.countDown();
    CompletableFuture<Void> async = CompletableFuture.supplyAsync(() -> null);
    EventTask.resumeWhenComplete(async).execute(continuation);
    latch.await();

    assertTrue(continuation.completedSuccessfully(), "Completed future did not "
        + "complete successfully");
  }

  /**
   * Tests that a failed {@link CompletableFuture} resumed from another thread
   * resumes the continuation with an exception.
   *
   * @throws InterruptedException if the latch await is interrupted
   */
  @Test
  public void testResumeWhenFailFromOtherThread() throws InterruptedException {
    WitnessContinuation continuation = new WitnessContinuation();
    CountDownLatch latch = new CountDownLatch(1);
    continuation.onComplete = (ignored) -> latch.countDown();
    CompletableFuture<Void> async = CompletableFuture.supplyAsync(() -> {
      throw new RuntimeException();
    });
    EventTask.resumeWhenComplete(async).execute(continuation);
    latch.await();

    assertTrue(continuation.completedWithError(), "Failed future completed successfully");
  }

  /**
   * Tests that a complex asynchronous chain that fails mid-way resumes the continuation with an error.
   *
   * @throws InterruptedException if the latch await is interrupted
   */
  @Test
  public void testResumeWhenFailFromOtherThreadComplexChain() throws InterruptedException {
    WitnessContinuation continuation = new WitnessContinuation();
    CountDownLatch latch = new CountDownLatch(1);
    continuation.onComplete = (ignored) -> latch.countDown();
    CompletableFuture<Void> async = CompletableFuture.supplyAsync(() -> null)
        .thenAccept((v) -> {
          throw new RuntimeException();
        })
        .thenCompose((v) -> CompletableFuture.completedFuture(null));
    EventTask.resumeWhenComplete(async).execute(continuation);
    latch.await();

    assertTrue(continuation.completedWithError(), "Failed future completed successfully");
  }

  /**
   * An extremely simplified implementation of {@link Continuation} for verifying the completion of
   * an operation.
   */
  private static final class WitnessContinuation implements Continuation {

    /**
     * Atomic updater for the {@link #status} field to ensure thread-safe transitions
     * between uncompleted, successful, and exceptional states.
     */
    private static final AtomicIntegerFieldUpdater<WitnessContinuation> STATUS_UPDATER =
        AtomicIntegerFieldUpdater.newUpdater(WitnessContinuation.class, "status");

    /**
     * Internal status constant indicating the continuation has not yet completed.
     */
    private static final int UNCOMPLETED = 0;

    /**
     * Internal status constant indicating the continuation completed successfully.
     */
    private static final int COMPLETED = 1;

    /**
     * Internal status constant indicating the continuation completed with an exception.
     */
    private static final int COMPLETED_WITH_EXCEPTION = 2;

    /**
     * Tracks the current completion status of the continuation.
     * Updated atomically by {@link #resume()} and {@link #resumeWithException(Throwable)}.
     */
    private volatile int status = UNCOMPLETED;

    /**
     * Optional consumer invoked when the continuation completes, either normally or exceptionally.
     */
    private Consumer<Throwable> onComplete;

    @Override
    public void resume() {
      if (!STATUS_UPDATER.compareAndSet(this, UNCOMPLETED, COMPLETED)) {
        throw new IllegalStateException("Continuation is already completed");
      }

      this.onComplete.accept(null);
    }

    @Override
    public void resumeWithException(final Throwable exception) {
      if (!STATUS_UPDATER.compareAndSet(this, UNCOMPLETED, COMPLETED_WITH_EXCEPTION)) {
        throw new IllegalStateException("Continuation is already completed");
      }

      this.onComplete.accept(exception);
    }

    public boolean completedSuccessfully() {
      return STATUS_UPDATER.get(this) == COMPLETED;
    }

    public boolean completedWithError() {
      return STATUS_UPDATER.get(this) == COMPLETED_WITH_EXCEPTION;
    }
  }
}
