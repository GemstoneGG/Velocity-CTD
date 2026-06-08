/*
 * Copyright (C) 2026 Velocity-CTD Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocityctd.api.permission;

import com.velocitypowered.api.permission.PermissionFunction;
import com.velocitypowered.api.permission.Tristate;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.Unmodifiable;

/**
 * An extension of {@link PermissionFunction} that allows the implementation of some more complex methods.
 */
public interface PermissionResolver extends PermissionFunction {

  /**
   * A simple permission resolver adapter backed by {@link PermissionFunction#ALWAYS_TRUE}.
   */
  PermissionResolver ALWAYS_TRUE = new PermissionResolverFunctionAdapter(PermissionFunction.ALWAYS_TRUE);

  /**
   * A simple permission resolver adapter backed by {@link PermissionFunction#ALWAYS_FALSE}.
   */
  PermissionResolver ALWAYS_FALSE = new PermissionResolverFunctionAdapter(PermissionFunction.ALWAYS_FALSE);

  /**
   * A simple permission resolver adapter backed by {@link PermissionFunction#ALWAYS_UNDEFINED}.
   */
  PermissionResolver ALWAYS_UNDEFINED = new PermissionResolverFunctionAdapter(PermissionFunction.ALWAYS_UNDEFINED);

  /**
   * Gets the subjects setting for a particular permission.
   *
   * @param permission the permission
   * @return the value the permission is set to
   */
  @Override
  @NonNull
  Tristate getPermissionValue(String permission);

  /**
   * Gets the subjects permission map.
   * Should return null when the permission map is unavailable.
   *
   * @return the permission map or {@code null if unavailable}
   */
  @Nullable
  @Unmodifiable
  Map<String, Boolean> getPermissionMap();

  /**
   * Subscribes the given {@link PermissionChangeListener} to changes in this resolver's subject's
   * effective permissions.
   *
   * <p>If the underlying permission system supports change notifications, the listener should be
   * invoked when the subject's effective permissions change. Resolvers that cannot emit such
   * notifications should leave this method as the default no-op.
   *
   * <p>Resolvers should avoid spamming the listener: a burst of changes that amount to a single
   * logical change should be coalesced into a single invocation. Multiple listeners may be
   * registered.
   *
   * @param listener the listener to notify of permission changes
   * @return an {@link AutoCloseable} that unsubscribes the listener when closed. Callers should close
   *         it once the listener is no longer needed to stop notifications and release any resources
   *         held for the subscription. The default implementation returns a no-op handle.
   */
  default @NonNull AutoCloseable subscribeToPermissionChanges(PermissionChangeListener listener) {
    return () -> {
    };
  }
}
