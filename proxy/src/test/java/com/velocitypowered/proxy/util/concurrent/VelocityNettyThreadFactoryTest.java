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

package com.velocitypowered.proxy.util.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.util.concurrent.FastThreadLocalThread;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * Tests the threads {@code VelocityNettyThreadFactory} makes for the event loop groups.
 */
public class VelocityNettyThreadFactoryTest {

  @Test
  void threadsCleanUpTheirFastThreadLocals() {
    Thread thread = new VelocityNettyThreadFactory("Test Worker #%d").newThread(() -> { });

    assertTrue(thread instanceof FastThreadLocalThread, "Not a FastThreadLocalThread");
    assertTrue(((FastThreadLocalThread) thread).willCleanupFastThreadLocals(),
        "Netty only gives a thread that cleans up its FastThreadLocals its own allocator magazines");
  }

  @Test
  void threadsRunTheirTaskUnderTheirName() throws InterruptedException {
    AtomicBoolean ran = new AtomicBoolean();
    VelocityNettyThreadFactory factory = new VelocityNettyThreadFactory("Test Worker #%d");
    Thread first = factory.newThread(() -> ran.set(true));
    Thread second = factory.newThread(() -> { });

    first.start();
    first.join();

    assertTrue(ran.get(), "Task did not run");
    assertEquals("Test Worker #0", first.getName());
    assertEquals("Test Worker #1", second.getName());
  }
}
