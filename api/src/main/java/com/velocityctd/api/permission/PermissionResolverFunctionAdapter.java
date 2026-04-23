/*
 * Copyright (C) 2018-2026 Velocity Contributors
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
 * Adapter implementation of {@link PermissionResolver} backed by a {@link PermissionFunction}.
 *
 * <p>
 * Permission checks are delegated to the provided {@code PermissionFunction}. Advanced/bulk operations are not
 * supported by the backing API and therefore behave as no-ops, and/or returning null results.
 */
public class PermissionResolverFunctionAdapter implements PermissionResolver {

  private final PermissionFunction permissionFunction;

  /**
   * Instantiates a {@link PermissionResolverFunctionAdapter} which delegates
   * {@link PermissionResolver#getPermissionValue(String)} to {@code permissionFunction#getPermissionValue(String)}.
   *
   * @param permissionFunction the {@link PermissionFunction} to use as a delegate.
   */
  public PermissionResolverFunctionAdapter(PermissionFunction permissionFunction) {
    this.permissionFunction = permissionFunction;
  }

  @Override
  @NonNull
  public Tristate getPermissionValue(String permission) {
    return permissionFunction.getPermissionValue(permission);
  }

  @Override
  @Nullable
  @Unmodifiable
  public Map<String, Boolean> getPermissionMap() {
    return null;
  }
}
