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

package com.velocityctd.proxy.queue;

import org.openjdk.jmh.annotations.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Microbenchmark for {@link QueuePlayerList} performance.
 *
 * <p>Measures throughput and allocation rates for insertByPriority operations.
 * Run with: {@code java -jar target/benchmarks.jar QueuePlayerListBenchmark}</p>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 10, time = 5)
@Fork(2)
public class QueuePlayerListBenchmark {

  private QueuePlayerList list;
  private VelocityQueueEntry[] entries;

  @Setup
  public void setup() {
    list = new QueuePlayerList();

    // Pre-create 1000 entries with random priorities
    entries = new VelocityQueueEntry[1000];
    for (int i = 0; i < 1000; i++) {
      entries[i] = createEntry(i);
    }
  }

  @Benchmark
  public void benchmarkInsertByPriority() {
    list.insertByPriority(entries[(int) (Math.random() * entries.length)]);
  }

  @Benchmark
  public void benchmarkSnapshot() {
    list.snapshot();
  }

  private VelocityQueueEntry createEntry(int index) {
    UUID uuid = UUID.nameUUIDFromBytes(("entry-" + index).getBytes());
    // Using null for server/queue as they're not needed for basic benchmark
    QueueEntryData data = new QueueEntryData(uuid, "player" + index, index % 50, false, false);
    return new VelocityQueueEntry(null, null, data);
  }
}