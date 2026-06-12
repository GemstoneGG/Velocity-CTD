/*
 * Copyright (C) 2026 Velocity-CTD Contributors
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

package com.velocityctd.permission.spi;

import com.velocityctd.api.permission.PermissionResolver;
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
