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

package com.velocitypowered.proxy.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.reflect.TypeToken;
import com.velocitypowered.api.event.AwaitingEventExecutor;
import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.proxy.testutil.FakePluginManager;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Event firing tests.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EventTest {

  /**
   * Thread name used to identify continuation execution threads in tests.
   */
  public static final String CONTINUATION_TEST_THREAD_NAME = "Continuation test thread";

  /**
   * Simulated plugin manager instance for test execution.
   */
  private final FakePluginManager pluginManager = new FakePluginManager();

  /**
   * Event manager used to fire and handle Velocity events in unit tests.
   */
  private final VelocityEventManager eventManager = new VelocityEventManager(pluginManager);

  /**
   * Shuts down the plugin manager after all tests have completed.
   */
  @AfterAll
  void shutdown() {
    pluginManager.shutdown();
  }

  static final class TestEvent {
  }

  static void assertSyncThread(final Thread thread) {
    assertEquals(Thread.currentThread(), thread);
  }

  static void assertAsyncThread(final Thread thread) {
    assertTrue(thread.getName().contains("Test Async Thread"));
  }

  static void assertContinuationThread(final Thread thread) {
    assertEquals(CONTINUATION_TEST_THREAD_NAME, thread.getName());
  }

  private void handleMethodListener(final Object listener) throws Exception {
    eventManager.register(FakePluginManager.PLUGIN_A, listener);
    try {
      eventManager.fire(new TestEvent()).get();
    } finally {
      eventManager.unregisterListeners(FakePluginManager.PLUGIN_A);
    }
  }

  @Test
  void listenerOrderPreserved() throws Exception {
    final AtomicLong listener1Invoked = new AtomicLong();
    final AtomicLong listener2Invoked = new AtomicLong();
    final AtomicLong listener3Invoked = new AtomicLong();

    eventManager.register(FakePluginManager.PLUGIN_A, TestEvent.class, event -> listener1Invoked.set(System.nanoTime()));
    eventManager.register(FakePluginManager.PLUGIN_B, TestEvent.class, event -> listener2Invoked.set(System.nanoTime()));
    eventManager.register(FakePluginManager.PLUGIN_A, TestEvent.class, event -> listener3Invoked.set(System.nanoTime()));

    try {
      eventManager.fire(new TestEvent()).get();
    } finally {
      eventManager.unregisterListeners(FakePluginManager.PLUGIN_A);
      eventManager.unregisterListeners(FakePluginManager.PLUGIN_B);
    }

    // Check that the order is A < B < C.
    assertTrue(listener1Invoked.get() < listener2Invoked.get(), "Listener B invoked before A!");
    assertTrue(listener2Invoked.get() < listener3Invoked.get(), "Listener C invoked before B!");
  }

  @Test
  void listenerOrderPreservedWithContinuation() throws Exception {
    final AtomicLong listener1Invoked = new AtomicLong();
    final AtomicLong listener2Invoked = new AtomicLong();
    final AtomicLong listener3Invoked = new AtomicLong();

    eventManager.register(FakePluginManager.PLUGIN_A, TestEvent.class, event ->
        listener1Invoked.set(System.nanoTime()));
    eventManager.register(FakePluginManager.PLUGIN_B, TestEvent.class,
        (AwaitingEventExecutor<TestEvent>) event -> EventTask.withContinuation(continuation -> new Thread(() -> {
          listener2Invoked.set(System.nanoTime());
          continuation.resume();
        }).start()));
    eventManager.register(FakePluginManager.PLUGIN_A, TestEvent.class, event ->
        listener3Invoked.set(System.nanoTime()));

    try {
      eventManager.fire(new TestEvent()).get();
    } finally {
      eventManager.unregisterListeners(FakePluginManager.PLUGIN_A);
      eventManager.unregisterListeners(FakePluginManager.PLUGIN_B);
    }

    // Check that the order is A < B < C.
    assertTrue(listener1Invoked.get() < listener2Invoked.get(), "Listener B invoked before A!");
    assertTrue(listener2Invoked.get() < listener3Invoked.get(), "Listener C invoked before B!");
  }

  @Test
  void testAlwaysSync() throws Exception {
    final AlwaysSyncListener listener = new AlwaysSyncListener();
    handleMethodListener(listener);
    assertSyncThread(listener.thread);
    assertEquals(1, listener.result);
  }

  static final class AlwaysSyncListener {

    /**
     * The thread on which the event handler was executed. Used to assert the
     * correct thread context (sync or async).
     */
    @MonotonicNonNull Thread thread;

    /**
     * A result counter used to verify the number of times the handler methods were executed.
     */
    int result;

    @Subscribe(async = false)
    void sync(final TestEvent event) {
      result++;
      thread = Thread.currentThread();
    }
  }

  @Test
  void testAlwaysAsync() throws Exception {
    final AlwaysAsyncListener listener = new AlwaysAsyncListener();
    handleMethodListener(listener);
    assertAsyncThread(listener.threadA);
    assertAsyncThread(listener.threadB);
    assertAsyncThread(listener.threadC);
    assertEquals(3, listener.result);
  }

  static final class AlwaysAsyncListener {

    /**
     * The thread that executed the first subscribed event handler.
     */
    @MonotonicNonNull Thread threadA;

    /**
     * The thread that executed the second subscribed event handler or the continuation logic.
     */
    @MonotonicNonNull Thread threadB;

    /**
     * The thread that executed the final subscribed event handler, usually after a continuation.
     */
    @MonotonicNonNull Thread threadC;

    /**
     * The result counter used to verify how many times the listener methods were invoked.
     */
    int result;

    @Subscribe(async = true, order = PostOrder.EARLY)
    void firstAsync(final TestEvent event) {
      result++;
      threadA = Thread.currentThread();
    }

    @Subscribe
    EventTask secondAsync(final TestEvent event) {
      threadB = Thread.currentThread();
      return EventTask.async(() -> result++);
    }

    @Subscribe(order = PostOrder.LATE)
    void thirdAsync(final TestEvent event) {
      result++;
      threadC = Thread.currentThread();
    }
  }

  @Test
  void testSometimesAsync() throws Exception {
    final SometimesAsyncListener listener = new SometimesAsyncListener();
    handleMethodListener(listener);
    assertSyncThread(listener.threadA);
    assertSyncThread(listener.threadB);
    assertAsyncThread(listener.threadC);
    assertAsyncThread(listener.threadD);
    assertEquals(3, listener.result);
  }

  static final class SometimesAsyncListener {

    /**
     * The thread that executed the first subscribed handler or synchronous method.
     */
    @MonotonicNonNull Thread threadA;

    /**
     * The thread that executed the second subscribed handler or initiated an asynchronous task.
     */
    @MonotonicNonNull Thread threadB;

    /**
     * The thread that executed the continuation or asynchronous part of the event task.
     */
    @MonotonicNonNull Thread threadC;

    /**
     * The thread that executed the final handler, typically invoked after asynchronous logic.
     */
    @MonotonicNonNull Thread threadD;

    /**
     * Counter tracking how many handlers or continuations were invoked during the event test.
     */
    int result;

    @Subscribe(order = PostOrder.EARLY, async = false)
    void notAsync(final TestEvent event) {
      result++;
      threadA = Thread.currentThread();
    }

    @Subscribe
    EventTask notAsyncUntilTask(final TestEvent event) {
      threadB = Thread.currentThread();
      return EventTask.async(() -> {
        threadC = Thread.currentThread();
        result++;
      });
    }

    @Subscribe(order = PostOrder.LATE, async = false)
    void stillAsyncAfterTask(final TestEvent event) {
      threadD = Thread.currentThread();
      result++;
    }
  }

  @Test
  void testContinuation() throws Exception {
    final ContinuationListener listener = new ContinuationListener();
    handleMethodListener(listener);
    assertSyncThread(listener.threadA);
    assertSyncThread(listener.threadB);
    assertAsyncThread(listener.threadC);
    assertEquals(2, listener.value.get());
  }

  static final class ContinuationListener {

    /**
     * The thread that executed the first part of the event handler.
     */
    @MonotonicNonNull Thread threadA;

    /**
     * The thread that executed the continuation-resume logic inside the event task.
     */
    @MonotonicNonNull Thread threadB;

    /**
     * The thread that executed the final handler invoked after the continuation resumes.
     */
    @MonotonicNonNull Thread threadC;

    /**
     * An atomic counter used to track the number of times the continuation logic has progressed.
     */
    final AtomicInteger value = new AtomicInteger();

    @Subscribe(order = PostOrder.EARLY)
    EventTask continuation(final TestEvent event) {
      threadA = Thread.currentThread();
      return EventTask.withContinuation(continuation -> {
        value.incrementAndGet();
        threadB = Thread.currentThread();
        new Thread(() -> {
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
          value.incrementAndGet();
          continuation.resume();
        }).start();
      });
    }

    @Subscribe(order = PostOrder.LATE)
    void afterContinuation(final TestEvent event) {
      threadC = Thread.currentThread();
    }
  }

  @Test
  void testResumeContinuationImmediately() throws Exception {
    final ResumeContinuationImmediatelyListener listener =
        new ResumeContinuationImmediatelyListener();
    handleMethodListener(listener);
    assertSyncThread(listener.threadA);
    assertSyncThread(listener.threadB);
    assertSyncThread(listener.threadC);
    assertEquals(2, listener.result);
  }

  static final class ResumeContinuationImmediatelyListener {

    /**
     * The thread that executed the first event handler.
     */
    @MonotonicNonNull Thread threadA;

    /**
     * The thread that executed the continuation logic.
     */
    @MonotonicNonNull Thread threadB;

    /**
     * The thread that executed the final event handler after the continuation resumed.
     */
    @MonotonicNonNull Thread threadC;

    /**
     * A counter tracking how many times event handlers have executed.
     */
    int result;

    @Subscribe(order = PostOrder.EARLY)
    EventTask continuation(final TestEvent event) {
      threadA = Thread.currentThread();
      return EventTask.withContinuation(continuation -> {
        threadB = Thread.currentThread();
        result++;
        continuation.resume();
      });
    }

    @Subscribe(order = PostOrder.LATE)
    void afterContinuation(final TestEvent event) {
      threadC = Thread.currentThread();
      result++;
    }
  }

  @Test
  void testContinuationParameter() throws Exception {
    final ContinuationParameterListener listener = new ContinuationParameterListener();
    handleMethodListener(listener);
    assertSyncThread(listener.threadA);
    assertSyncThread(listener.threadB);
    assertAsyncThread(listener.threadC);
    assertEquals(3, listener.result.get());
  }

  static final class ContinuationParameterListener {

    /**
     * The thread that executed the first event handler.
     */
    @MonotonicNonNull Thread threadA;

    /**
     * The thread that executed the second event handler, expected to run asynchronously.
     */
    @MonotonicNonNull Thread threadB;

    /**
     * The thread that executed the third event handler, expected to run after continuation.
     */
    @MonotonicNonNull Thread threadC;

    /**
     * Tracks the number of times the event logic has been executed.
     */
    final AtomicInteger result = new AtomicInteger();

    @Subscribe
    void resume(final TestEvent event, final Continuation continuation) {
      threadA = Thread.currentThread();
      result.incrementAndGet();
      continuation.resume();
    }

    @Subscribe(order = PostOrder.LATE)
    void resumeFromCustomThread(final TestEvent event, final Continuation continuation) {
      threadB = Thread.currentThread();
      new Thread(() -> {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        result.incrementAndGet();
        continuation.resume();
      }).start();
    }

    @Subscribe(order = PostOrder.LAST)
    void afterCustomThread(final TestEvent event, final Continuation continuation) {
      threadC = Thread.currentThread();
      result.incrementAndGet();
      continuation.resume();
    }
  }

  interface FancyContinuation {

    void resume();

    void resumeWithError(Exception exception);
  }

  private static final class FancyContinuationImpl implements FancyContinuation {

    /**
     * The {@link Continuation} instance used to control the resumption of event execution.
     */
    private final Continuation continuation;

    private FancyContinuationImpl(final Continuation continuation) {
      this.continuation = continuation;
    }

    @Override
    public void resume() {
      continuation.resume();
    }

    @Override
    public void resumeWithError(final Exception exception) {
      continuation.resumeWithException(exception);
    }
  }

  interface TriConsumer<A, B, C> {

    void accept(A a, B b, C c);
  }

  @Test
  void testFancyContinuationParameter() throws Exception {
    eventManager.registerHandlerAdapter(
        "fancy",
        method -> method.getParameterCount() > 1
            && method.getParameterTypes()[1] == FancyContinuation.class,
        (method, errors) -> {
          if (method.getReturnType() != void.class) {
            errors.add("method return type must be void");
          }
          if (method.getParameterCount() != 2) {
            errors.add("method must have exactly two parameters, the first is the event and "
                + "the second is the fancy continuation");
          }
        },
        new TypeToken<TriConsumer<Object, Object, FancyContinuation>>() {
        },
        invokeFunction -> (instance, event) ->
            EventTask.withContinuation(continuation ->
                invokeFunction.accept(instance, event, new FancyContinuationImpl(continuation))
            ));
    final FancyContinuationListener listener = new FancyContinuationListener();
    handleMethodListener(listener);
    assertEquals(1, listener.result);
  }

  static final class FancyContinuationListener {

    /**
     * The result counter used to track the number of completed event handler stages.
     */
    int result;

    @Subscribe
    void continuation(final TestEvent event, final FancyContinuation continuation) {
      result++;
      continuation.resume();
    }
  }
}
