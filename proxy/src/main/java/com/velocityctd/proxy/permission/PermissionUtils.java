/*
 * Copyright (C) 2018-2026 Velocity-CTD Contributors
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

package com.velocityctd.proxy.permission;

import static java.util.Objects.requireNonNull;

import com.velocitypowered.api.permission.PermissionSubject;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

public class PermissionUtils {

  /**
   * Instance for logging.
   */
  private static final Logger LOGGER = LogManager.getLogger(PermissionUtils.class);

  /**
   * Flag that stores whether we have sent a warning log that the slower
   * {@link #findHighestPermissionValueWithHasPermission} is being used.
   */
  private static boolean slowWarningLogged = false;

  /**
   * Returns the highest positive integer {@code v} ({@code 1}..{@code max}) for which {@code subject} has the permission
   * {@code permissionPrefix + v}. If no such permission is granted, returns {@link Optional#empty()}.
   *
   * <p>
   * The {@code permissionPrefix} must end with {@code '.'} and {@code max} must be {@code > 0}.
   *
   * <p>
   * <b>Performance note:</b> keep {@code max} as low as practical. When the permission provider does not implement
   * {@code getPermissionMap()} (i.e., it returns {@code null}), this method may perform up to {@code max} permission
   * checks (O({@code max})) by querying {@code hasPermission(prefix + v)} in a descending loop.
   *
   * <p>
   * Implementation uses one of two strategies depending on the permission provider:
   *
   * <ul>
   *   <li><b>Permission-map path</b> (when {@code subject.getPermissionMap()} is non-null):
   *       checks {@code hasPermission(prefix + max)} as a fast-path to handle wildcard-style grants that may not
   *       appear in the map, then scans the map for granted entries whose keys start with {@code permissionPrefix},
   *       parses the numeric suffix, and returns the maximum parsed value {@code <= max}.</li>
   *   <li><b>hasPermission path</b> (when {@code subject.getPermissionMap()} is null):
   *       queries {@code hasPermission(prefix + v)} for {@code v} descending from {@code max} to {@code 1} and returns
   *       the first match.</li>
   * </ul>
   *
   * @param subject the permission subject to query
   * @param permissionPrefix permission node prefix, ending with {@code '.'} (e.g. {@code "example.permission."})
   * @param max the maximum value to consider (must be {@code > 0})
   * @return the highest granted value in the range {@code [1, max]}, or empty if none are granted
   * @throws NullPointerException if {@code subject} or {@code permissionPrefix} is null
   * @throws IllegalArgumentException if {@code permissionPrefix} does not end with {@code '.'} or {@code max <= 0}
   */
  public static Optional<Integer> findHighestPermissionValue(
      PermissionSubject subject, String permissionPrefix, int max) {

    requireNonNull(subject, "subject");
    requireNonNull(permissionPrefix, "permissionPrefix");

    if (!permissionPrefix.endsWith(".")) {
      throw new IllegalArgumentException("permissionPrefix should end with '.'.");
    }

    if (max <= 0) {
      throw new IllegalArgumentException("max should be greater than 0.");
    }

    Map<String, Boolean> permissionMap = subject.getPermissionMap();
    if (permissionMap != null) {
      return findHighestPermissionValueWithPermissionMap(subject, permissionPrefix, max, permissionMap);
    } else {
      if (!slowWarningLogged) {
        slowWarningLogged = true;

        LOGGER.warn(
            "The permission provider did not expose a permission map, so a slower method will be used "
                + "for more complex permission checks. If possible, consider switching to a permission "
                + "provider that integrates with Velocity-CTD's PermissionResolver, or use LuckPerms "
                + "which has built-in support for this by Velocity-CTD."
        );
      }

      return findHighestPermissionValueWithHasPermission(subject, permissionPrefix, max);
    }
  }

  private static Optional<Integer> findHighestPermissionValueWithPermissionMap(
      PermissionSubject subject, String permissionPrefix,
      int max, Map<String, Boolean> permissionMap) {

    // Fast-track max permission check
    // Needed for subjects with '*' permission with some permission providers (permission map won't match this)
    if (subject.hasPermission(permissionPrefix + String.valueOf(max))) {
      return Optional.of(max);
    }

    Integer highestPermissionValue = null;

    for (Map.Entry<String, Boolean> permissionEntry : permissionMap.entrySet()) {
      if (!permissionEntry.getValue()) {
        continue;
      }

      String permission = permissionEntry.getKey();
      if (!permission.startsWith(permissionPrefix)) {
        continue;
      }

      Integer value = safeParseInt(permission.substring(permissionPrefix.length()));
      if (value != null && value > 0 && value <= max) {
        if (highestPermissionValue == null || value > highestPermissionValue) {
          highestPermissionValue = value;
        }
      }
    }

    return Optional.ofNullable(highestPermissionValue);
  }

  private static Optional<Integer> findHighestPermissionValueWithHasPermission(
      PermissionSubject subject, String permissionPrefix, int max) {

    for (int value = max; value > 0; value--) {
      if (subject.hasPermission(permissionPrefix + String.valueOf(value))) {
        return Optional.of(value);
      }
    }

    return Optional.empty();
  }

  private static @Nullable Integer safeParseInt(String s) {
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
