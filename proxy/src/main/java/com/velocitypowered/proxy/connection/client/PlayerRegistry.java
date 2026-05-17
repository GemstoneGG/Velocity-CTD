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

package com.velocitypowered.proxy.connection.client;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent.LoginStatus;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.client.PlayerIdentityLock.LockHandle;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.UnmodifiableView;

/**
 * Owns the registry of {@link ConnectedPlayer} instances and serializes registration,
 * disconnection and {@link DisconnectEvent} firing on a per-identity basis via a
 * {@link PlayerIdentityLock}.
 *
 * <p>The lock acquired at {@link #registerConnection(ConnectedPlayer)} is held by the connection
 * until either {@link #finalizeLogin(ConnectedPlayer)} runs (after {@code PostLoginEvent}) or
 * {@link #unregisterConnection(ConnectedPlayer)} runs (firing {@code DisconnectEvent}). A
 * timeout forcibly releases the lock and closes the connection if login does not complete
 * within {@link #LOGIN_LOCK_TIMEOUT_SECONDS}.
 */
public final class PlayerRegistry {

  private static final Logger LOGGER = LogManager.getLogger(PlayerRegistry.class);

  /**
   * Maximum time to wait for {@link DisconnectEvent} handlers to complete before unregistering
   * the player and continuing.
   */
  private static final long DISCONNECT_EVENT_TIMEOUT_SECONDS = 30;

  /**
   * Maximum time the identity lock may be held while a connection completes its login sequence.
   * After this timeout the lock is forcibly released and the connection is closed.
   */
  private static final long LOGIN_LOCK_TIMEOUT_SECONDS = 30;

  private final ScheduledExecutorService loginTimeoutScheduler =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Velocity Login Lock Watchdog");
        t.setDaemon(true);
        return t;
      });

  private final Map<UUID, ConnectedPlayer> byUuid = new ConcurrentHashMap<>();
  private final Map<String, ConnectedPlayer> byName = new ConcurrentHashMap<>();
  private final PlayerIdentityLock identityLock = new PlayerIdentityLock();

  private final VelocityServer server;

  public PlayerRegistry(@NonNull VelocityServer server) {
    this.server = server;
  }

  /**
   * Acquires the identity lock for the given profile, then either registers the connection or
   * kicks the existing player(s) with the same uuid or name (per {@code kick-existing-players}).
   *
   * <p>On successful registration the lock is transferred to the {@link ConnectedPlayer} via
   * {@link ConnectedPlayer#setIdentityLock(LockHandle)} and held until {@link #finalizeLogin}
   * or {@link #unregisterConnection} runs. The caller must call exactly one of those.
   *
   * @return a future resolving to {@code true} if the connection was registered, {@code false} if not
   */
  public @NonNull CompletableFuture<Boolean> registerConnection(@NonNull ConnectedPlayer player) {
    UUID uuid = player.getUniqueId();
    String name = player.getUsername().toLowerCase(Locale.ROOT);
    return identityLock.acquire(uuid, name)
        .thenCompose(lock -> withLockReleasedOnFailure(lock, () -> tryRegisterLocked(player, lock)));
  }

  /**
   * Releases the identity lock for a player that has finished its full login sequence.
   * Cancels the login-lock watchdog. Must be called exactly once per successful registration,
   * after a successful login flow.
   */
  public void finalizeLogin(@NonNull ConnectedPlayer player) {
    player.markLoginCompleted();
    LockHandle lock = player.consumeIdentityLock();
    if (lock != null) {
      lock.release();
    }
  }

  /**
   * Fires {@link DisconnectEvent} (if not already fired by a kick path), removes the connection
   * from the registry maps, and releases the identity lock.
   *
   * <p>This is the disconnect/unregister counterpart to {@link #registerConnection}. It is safe
   * to call multiple times: {@link ConnectedPlayer#markDisconnectFired()} ensures the event is
   * fired only once.
   *
   * @return a future that completes once {@code DisconnectEvent} handlers have run and the
   *         player has been removed from the registry
   */
  public @NonNull CompletableFuture<Void> unregisterConnection(@NonNull ConnectedPlayer player) {
    LockHandle held = player.consumeIdentityLock();
    if (held != null) {
      return withLockReleasedOnFailure(held, () -> doUnregisterLocked(player))
          .whenComplete((v, ex) -> held.release());
    }

    UUID uuid = player.getUniqueId();
    String name = player.getUsername().toLowerCase(Locale.ROOT);
    return identityLock.acquire(uuid, name)
        .thenCompose(lock -> withLockReleasedOnFailure(lock, () -> doUnregisterLocked(player))
            .whenComplete((v, ex) -> lock.release()));
  }

  private static <T> CompletableFuture<T> withLockReleasedOnFailure(LockHandle lock, Supplier<CompletableFuture<T>> function) {
    CompletableFuture<T> result;
    try {
      result = function.get();
    } catch (Throwable t) {
      lock.release();
      return failedFuture(t);
    }
    return result.exceptionallyCompose(ex -> {
      lock.release();
      return failedFuture(ex);
    });
  }

  private CompletableFuture<Boolean> tryRegisterLocked(ConnectedPlayer player, LockHandle lock) {
    UUID uuid = player.getUniqueId();
    String name = player.getUsername().toLowerCase(Locale.ROOT);

    if (!server.getConfiguration().isKickExistingPlayers()) {
      if (byUuid.containsKey(uuid) || byName.containsKey(name)) {
        lock.release();
        return completedFuture(false);
      }

      byUuid.put(uuid, player);
      byName.put(name, player);
      attachLockToPlayer(player, lock);
      return completedFuture(true);
    }

    ConnectedPlayer existingByUuid = byUuid.get(uuid);
    ConnectedPlayer existingByName = byName.get(name);

    if (server.getConfiguration().isKickExistingPlayersCheckIp()) {
      InetAddress address = player.getRemoteAddress().getAddress();

      if (existingByUuid != null
          && !address.equals(existingByUuid.getRemoteAddress().getAddress())) {
        lock.release();
        return completedFuture(false);
      }

      if (existingByName != null
          && !address.equals(existingByName.getRemoteAddress().getAddress())) {
        lock.release();
        return completedFuture(false);
      }
    }

    CompletableFuture<Void> evictFuture;
    if (existingByUuid != null && existingByName != null && existingByUuid != existingByName) {
      evictFuture = CompletableFuture.allOf(
          forciblyEvict(existingByUuid),
          forciblyEvict(existingByName));
    } else if (existingByUuid != null) {
      evictFuture = forciblyEvict(existingByUuid);
    } else if (existingByName != null) {
      evictFuture = forciblyEvict(existingByName);
    } else {
      evictFuture = completedFuture(null);
    }

    return evictFuture.thenApply(v -> {
      if (player.getConnection().isClosed()) {
        lock.release();
        return false;
      }

      byUuid.put(uuid, player);
      byName.put(name, player);
      attachLockToPlayer(player, lock);
      return true;
    });
  }

  private void attachLockToPlayer(ConnectedPlayer player, LockHandle lock) {
    player.setIdentityLock(lock);
    ScheduledFuture<?> timeout = loginTimeoutScheduler.schedule(
        () -> onLoginLockTimeout(player), LOGIN_LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    player.setLoginLockTimeout(timeout);
  }

  private void onLoginLockTimeout(ConnectedPlayer player) {
    LockHandle lock = player.consumeIdentityLock();
    if (lock == null) {
      return;
    }
    LOGGER.warn("Player {} held the identity lock for over {}s without completing login; "
            + "forcibly releasing and closing the connection.",
        player, LOGIN_LOCK_TIMEOUT_SECONDS);
    try {
      player.disconnect(Component.translatable("multiplayer.disconnect.slow_login"));
    } finally {
      lock.release();
    }
  }

  /**
   * Fires {@link DisconnectEvent} for an existing player whose slot is being taken over by a
   * new connection, then removes the existing player from the registry maps. Assumes the
   * identity lock for the player's identity is currently held (by the new connection's
   * registration path).
   */
  private CompletableFuture<Void> forciblyEvict(ConnectedPlayer existing) {
    existing.disconnect(Component.translatable("multiplayer.disconnect.duplicate_login"));

    if (!existing.markDisconnectFired()) {
      // Some other path already fired DisconnectEvent for this player; just wait for its
      // teardown to complete so we observe the same ordering as if we had fired it ourselves.
      return existing.getTeardownFuture().exceptionally(ex -> null);
    }

    return fireDisconnectAndCleanup(existing, "kicked player", LoginStatus.CONFLICTING_LOGIN);
  }

  private CompletableFuture<Void> doUnregisterLocked(ConnectedPlayer player) {
    if (!player.markDisconnectFired()) {
      // DisconnectEvent was already fired (e.g. by the kick path). Cleanup has already run
      // and teardownFuture has already been completed by the kicker; nothing more to do.
      return completedFuture(null);
    }

    return fireDisconnectAndCleanup(player, "player", computeDisconnectStatus(player));
  }

  /**
   * Fires {@link DisconnectEvent} (only if {@code LoginEvent} previously fired for this player),
   * removes the player from the registry maps, runs {@link ConnectedPlayer#disconnected()}
   * cleanup, and completes the teardown future. Callers must have won {@link
   * ConnectedPlayer#markDisconnectFired()} before invoking this so only one path runs cleanup.
   */
  private CompletableFuture<Void> fireDisconnectAndCleanup(ConnectedPlayer player,
                                                           String label,
                                                           LoginStatus status) {
    if (!player.isLoginEventFired()) {
      // The connection was rejected or aborted before LoginEvent fired. Run cleanup but skip the event.
      runCleanup(player, null, label);
      return completedFuture(null);
    }

    DisconnectEvent event = new DisconnectEvent(player, status);
    return server.getEventManager().fire(event)
        .completeOnTimeout(event, DISCONNECT_EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .handle((v, ex) -> {
          if (ex != null) {
            LOGGER.error("Exception firing DisconnectEvent for {} {}", label, player, ex);
          }
          runCleanup(player, ex, label);
          return null;
        });
  }

  /**
   * Resolves to all {@link LoginStatus} values except for {@link LoginStatus#CONFLICTING_LOGIN}.
   */
  private LoginStatus computeDisconnectStatus(ConnectedPlayer player) {
    ConnectedPlayer registered = byUuid.get(player.getUniqueId());
    if (registered != player) {
      return player.isKnownDisconnect() ? LoginStatus.CANCELLED_BY_PROXY : LoginStatus.CANCELLED_BY_USER;
    }
    if (!player.isLoginCompleted() && !player.isKnownDisconnect()) {
      return LoginStatus.CANCELLED_BY_USER_BEFORE_COMPLETE;
    }
    return player.isFirstServerConnected() ? LoginStatus.SUCCESSFUL_LOGIN : LoginStatus.PRE_SERVER_JOIN;
  }

  private void runCleanup(ConnectedPlayer player, Throwable error, String label) {
    removeFromMaps(player);
    try {
      player.disconnected();
    } catch (Throwable t) {
      LOGGER.error("Exception during cleanup of {} {}", label, player, t);
    }
    player.completeTeardown(error);
  }

  private void removeFromMaps(ConnectedPlayer player) {
    byName.remove(player.getUsername().toLowerCase(Locale.ROOT), player);
    byUuid.remove(player.getUniqueId(), player);
  }

  public Optional<ConnectedPlayer> getPlayer(@NonNull UUID uuid) {
    return Optional.ofNullable(byUuid.get(uuid));
  }

  public Optional<ConnectedPlayer> getPlayer(@NonNull String username) {
    return Optional.ofNullable(byName.get(username.toLowerCase(Locale.ROOT)));
  }

  public @UnmodifiableView Collection<ConnectedPlayer> getPlayers() {
    return Collections.unmodifiableCollection(byUuid.values());
  }

  public boolean isPlayerOnline(@NonNull ConnectedPlayer player) {
    return byUuid.get(player.getUniqueId()) == player;
  }

  public int getLocalPlayerCount() {
    return byUuid.size();
  }

  public void shutdown() {
    loginTimeoutScheduler.shutdownNow();
  }
}
