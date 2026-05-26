/*
 * Copyright (C) 2026 Velocity-CTD Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocityctd.api.permission;

import com.velocitypowered.api.permission.PermissionFunction;
import com.velocitypowered.api.permission.PermissionSubject;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Provides {@link PermissionResolver}s for {@link PermissionSubject}s.
 */
public interface PermissionResolverProvider {

  /**
   * Should perform a check to see if this {@link PermissionResolverProvider} is available or not.
   * If this method returns {@code false}, subsequent calls to the provider's functions may
   * result in errors or undefined behavior.
   *
   * @return {@code true} if this {@link PermissionResolverProvider} is available, {@code false} if not
   */
  boolean isAvailable();

  /**
   * Creates a {@link PermissionResolver} for the subject with an optional {@link PermissionFunction} delegate.
   *
   * @param subject the subject
   * @param delegate the optional delegate to use
   * @return the permission resolver
   */
  @Nullable
  PermissionResolver createResolver(PermissionSubject subject, @Nullable PermissionFunction delegate);

  /**
   * Creates a {@link PermissionResolver} for the subject without a delegate.
   *
   * @param subject the subject
   * @return the permission resolver
   */
  default @Nullable PermissionResolver createResolver(PermissionSubject subject) {
    return createResolver(subject, null);
  }
}
