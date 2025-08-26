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

package com.velocitypowered.proxy.queue;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.Supplier;

/**
 * Centralized thread pool manager for queue operations.
 * 
 * <p>This class provides a dedicated thread pool for queue-related async operations,
 * replacing scattered CompletableFuture.runAsync() calls with better resource management.</p>
 */
public class QueueThreadManager {
    
    private static final Logger logger = LoggerFactory.getLogger(QueueThreadManager.class);
    
    /**
     * The dedicated executor service for queue operations.
     */
    private static final ExecutorService queueExecutor;
    
    /**
     * The dedicated executor service for Redis operations.
     */
    private static final ExecutorService redisExecutor;
    
    static {
        // Create thread factories with descriptive names
        ThreadFactory queueThreadFactory = new ThreadFactoryBuilder()
            .setNameFormat("queue-worker-%d")
            .setDaemon(true)
            .setUncaughtExceptionHandler((thread, throwable) -> 
                logger.error("Uncaught exception in queue thread: {}", thread.getName(), throwable))
            .build();
            
        ThreadFactory redisThreadFactory = new ThreadFactoryBuilder()
            .setNameFormat("redis-worker-%d")
            .setDaemon(true)
            .setUncaughtExceptionHandler((thread, throwable) -> 
                logger.error("Uncaught exception in Redis thread: {}", thread.getName(), throwable))
            .build();
        
        // Create dedicated thread pools
        queueExecutor = Executors.newFixedThreadPool(4, queueThreadFactory);
        redisExecutor = Executors.newFixedThreadPool(2, redisThreadFactory);
        
        // Add shutdown hook for graceful cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down queue thread pools...");
            shutdown();
        }));
    }
    
    /**
     * Executes a queue operation asynchronously using the dedicated queue thread pool.
     *
     * @param runnable the operation to execute
     * @return a CompletableFuture representing the operation
     */
    public static CompletableFuture<Void> executeQueueOperation(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, queueExecutor);
    }
    
    /**
     * Executes a Redis operation asynchronously using the dedicated Redis thread pool.
     *
     * @param runnable the operation to execute
     * @return a CompletableFuture representing the operation
     */
    public static CompletableFuture<Void> executeRedisOperation(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, redisExecutor);
    }
    
    /**
     * Executes a queue operation asynchronously with timeout.
     *
     * @param runnable the operation to execute
     * @param timeout the timeout duration
     * @param unit the timeout unit
     * @return a CompletableFuture representing the operation
     */
    public static CompletableFuture<Void> executeQueueOperationWithTimeout(Runnable runnable, long timeout, TimeUnit unit) {
        return CompletableFuture.runAsync(runnable, queueExecutor)
            .orTimeout(timeout, unit);
    }
    
    /**
     * Executes a Redis operation asynchronously with timeout.
     *
     * @param runnable the operation to execute
     * @param timeout the timeout duration
     * @param unit the timeout unit
     * @return a CompletableFuture representing the operation
     */
    public static CompletableFuture<Void> executeRedisOperationWithTimeout(Runnable runnable, long timeout, TimeUnit unit) {
        return CompletableFuture.runAsync(runnable, redisExecutor)
            .orTimeout(timeout, unit);
    }

    /**
     * Executes a queue operation asynchronously that returns a value.
     *
     * @param supplier the operation to execute
     * @param <T> the type of the result
     * @return a CompletableFuture containing the result
     */
    public static <T> CompletableFuture<T> executeQueueOperation(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, queueExecutor);
    }

    /**
     * Executes a Redis operation asynchronously that returns a value.
     *
     * @param supplier the operation to execute
     * @param <T> the type of the result
     * @return a CompletableFuture containing the result
     */
    public static <T> CompletableFuture<T> executeRedisOperation(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, redisExecutor);
    }

    /**
     * Executes a queue operation asynchronously that returns a value with timeout.
     *
     * @param supplier the operation to execute
     * @param timeout the timeout duration
     * @param unit the timeout unit
     * @param <T> the type of the result
     * @return a CompletableFuture containing the result
     */
    public static <T> CompletableFuture<T> executeQueueOperationWithTimeout(Supplier<T> supplier, long timeout, TimeUnit unit) {
        return CompletableFuture.supplyAsync(supplier, queueExecutor)
            .orTimeout(timeout, unit);
    }

    /**
     * Executes a Redis operation asynchronously that returns a value with timeout.
     *
     * @param supplier the operation to execute
     * @param timeout the timeout duration
     * @param unit the timeout unit
     * @param <T> the type of the result
     * @return a CompletableFuture containing the result
     */
    public static <T> CompletableFuture<T> executeRedisOperationWithTimeout(Supplier<T> supplier, long timeout, TimeUnit unit) {
        return CompletableFuture.supplyAsync(supplier, redisExecutor)
            .orTimeout(timeout, unit);
    }
    
    /**
     * Shuts down the thread pools gracefully.
     */
    public static void shutdown() {
        try {
            // Shutdown queue executor
            queueExecutor.shutdown();
            if (!queueExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                queueExecutor.shutdownNow();
                if (!queueExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.error("Queue executor did not terminate");
                }
            }
            
            // Shutdown Redis executor
            redisExecutor.shutdown();
            if (!redisExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                redisExecutor.shutdownNow();
                if (!redisExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.error("Redis executor did not terminate");
                }
            }
            
            logger.info("Queue thread pools shutdown complete");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during thread pool shutdown", e);
        }
    }
    
    /**
     * Gets the queue executor service for direct access if needed.
     *
     * @return the queue executor service
     */
    public static ExecutorService getQueueExecutor() {
        return queueExecutor;
    }
    
    /**
     * Gets the Redis executor service for direct access if needed.
     *
     * @return the Redis executor service
     */
    public static ExecutorService getRedisExecutor() {
        return redisExecutor;
    }
}
