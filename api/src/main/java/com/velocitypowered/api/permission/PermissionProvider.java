/*
 * Copyright (C) 2018-2026 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.permission;

import com.velocityctd.api.permission.PermissionResolver;

/**
 * Provides {@link PermissionFunction}s for {@link PermissionSubject}s.
 */
@FunctionalInterface
public interface PermissionProvider {

  /**
   * Creates a {@link PermissionFunction} for the subject.
   * Implementation may return a {@link PermissionResolver} instead.
   * The caller may check for this to use the more advanced permission operations.
   *
   * @param subject the subject
   * @return the function
   */
  PermissionFunction createFunction(PermissionSubject subject);
}
