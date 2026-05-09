/*
 * Copyright (C) 2018-2026 Velocity-CTD Contributors
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
}
