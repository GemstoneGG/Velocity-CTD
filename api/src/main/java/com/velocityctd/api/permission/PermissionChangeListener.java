/*
 * Copyright (C) 2026 Velocity-CTD Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocityctd.api.permission;

/**
 * A listener that is notified when the effective permissions of a {@link PermissionResolver}'s
 * subject change.
 *
 * <p>Register one through
 * {@link PermissionResolver#subscribeToPermissionChanges(PermissionChangeListener)}. Resolvers
 * backed by a permission system capable of emitting change notifications (such as LuckPerms) deliver
 * those notifications to this listener.
 */
@FunctionalInterface
public interface PermissionChangeListener {

  /**
   * Called when the effective permissions of the resolver's subject have changed.
   *
   * <p>This method may be invoked from an arbitrary thread, depending on the underlying permission
   * system. Implementations are responsible for any thread confinement they require.
   *
   * <p>Resolvers may coalesce a rapid burst of changes into a single invocation, so this method is
   * not guaranteed to be called once per underlying change.
   */
  void onPermissionChange();
}
