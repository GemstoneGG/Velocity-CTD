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

package com.velocityctd.proxy.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.velocitypowered.api.permission.PermissionSubject;
import com.velocitypowered.api.permission.Tristate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PermissionUtilsTest {

  @Test
  void rejectsNullSubject() {
    assertThrows(NullPointerException.class,
        () -> PermissionUtils.findHighestPermissionValue(null, "perm.", 10));
  }

  @Test
  void rejectsNullPrefix() {
    PermissionSubject subject = new TestPermissionSubject(Map.of(), Map.of());
    assertThrows(NullPointerException.class,
        () -> PermissionUtils.findHighestPermissionValue(subject, null, 10));
  }

  @Test
  void rejectsPrefixWithoutTrailingDot() {
    PermissionSubject subject = new TestPermissionSubject(Map.of(), Map.of());
    assertThrows(IllegalArgumentException.class,
        () -> PermissionUtils.findHighestPermissionValue(subject, "prefix", 10));
  }

  @Test
  void rejectsMaxLessThanOrEqualToZero() {
    PermissionSubject subject = new TestPermissionSubject(Map.of(), Map.of());
    assertThrows(IllegalArgumentException.class,
        () -> PermissionUtils.findHighestPermissionValue(subject, "perm.", 0));
    assertThrows(IllegalArgumentException.class,
        () -> PermissionUtils.findHighestPermissionValue(subject, "perm.", -1));
  }

  @Test
  void returnsEmptyWhenPermissionMapIsEmptyAndFastTrackFails() {
    PermissionSubject subject = new TestPermissionSubject(Map.of(), Map.of());

    assertEquals(Optional.empty(),
        PermissionUtils.findHighestPermissionValue(subject, "perm.", 100));
  }

  @Test
  void returnsMaxFromPermissionMap() {
    PermissionSubject subject = new TestPermissionSubject(
        Map.of("timeout.30", true), Map.of());

    assertEquals(Optional.of(30),
        PermissionUtils.findHighestPermissionValue(subject, "timeout.", 100));
  }

  @Test
  void returnsHighestFromMultiplePermissionMapEntries() {
    PermissionSubject subject = new TestPermissionSubject(Map.of(
        "timeout.10", true,
        "timeout.25", true,
        "timeout.50", true,
        "timeout.5", true
    ), Map.of());

    assertEquals(Optional.of(50),
        PermissionUtils.findHighestPermissionValue(subject, "timeout.", 100));
  }

  @Test
  void filtersOutValuesAboveMaxFromPermissionMap() {
    PermissionSubject subject = new TestPermissionSubject(Map.of(
        "timeout.10", true,
        "timeout.200", true,
        "timeout.50", true
    ), Map.of());

    assertEquals(Optional.of(50),
        PermissionUtils.findHighestPermissionValue(subject, "timeout.", 100));
  }

  @Test
  void fastTrackReturnsMaxWhenPermissionGranted() {
    PermissionSubject subject = new TestPermissionSubject(
        Map.of(), Map.of("timeout.100", true));

    assertEquals(Optional.of(100),
        PermissionUtils.findHighestPermissionValue(subject, "timeout.", 100));
  }

  @Test
  void skipsFalseEntriesInPermissionMap() {
    PermissionSubject subject = new TestPermissionSubject(Map.of(
        "timeout.50", false,
        "timeout.30", true,
        "timeout.10", false
    ), Map.of());

    assertEquals(Optional.of(30),
        PermissionUtils.findHighestPermissionValue(subject, "timeout.", 100));
  }

  @Test
  void skipsNonNumericSuffixesGracefully() {
    PermissionSubject subject = new TestPermissionSubject(Map.of(
        "timeout.abc", true,
        "timeout.30", true,
        "timeout.!@#", true
    ), Map.of());

    assertEquals(Optional.of(30),
        PermissionUtils.findHighestPermissionValue(subject, "timeout.", 100));
  }

  @Test
  void findsHighestViaGetPermissionValueWhenMapIsNull() {
    PermissionSubject subject = TestPermissionSubject.withNullMap(Map.of(
        "timeout.10", false,
        "timeout.9", false,
        "timeout.8", true
    ));

    assertEquals(Optional.of(8),
        PermissionUtils.findHighestPermissionValue(subject, "timeout.", 10));
  }

  @Test
  void returnsEmptyWhenPermissionMapIsNullAndNoneGranted() {
    PermissionSubject subject = TestPermissionSubject.withNullMap(Map.of());

    assertEquals(Optional.empty(),
        PermissionUtils.findHighestPermissionValue(subject, "timeout.", 10));
  }

  @Test
  void returnsValueWhenMaxIsOneAndGranted() {
    PermissionSubject subject = TestPermissionSubject.withNullMap(
        Map.of("timeout.1", true));

    assertEquals(Optional.of(1),
        PermissionUtils.findHighestPermissionValue(subject, "timeout.", 1));
  }

  private static class TestPermissionSubject implements PermissionSubject {

    private final Map<String, Boolean> permissionMap;
    private final Map<String, Boolean> additionalPermissions;
    private final boolean returnNullMap;

    TestPermissionSubject(
        Map<String, Boolean> permissionMap,
        Map<String, Boolean> additionalPermissions
    ) {
      this(permissionMap, additionalPermissions, false);
    }

    private TestPermissionSubject(
        Map<String, Boolean> permissionMap,
        Map<String, Boolean> additionalPermissions,
        boolean returnNullMap
    ) {
      this.permissionMap = permissionMap;
      this.additionalPermissions = additionalPermissions;
      this.returnNullMap = returnNullMap;
    }

    static TestPermissionSubject withNullMap(Map<String, Boolean> additionalPermissions) {
      return new TestPermissionSubject(Map.of(), additionalPermissions, true);
    }

    @Override
    public Tristate getPermissionValue(String permission) {
      Boolean additional = additionalPermissions.get(permission);
      if (additional != null) {
        return Tristate.fromBoolean(additional);
      }
      Boolean v = permissionMap.get(permission);
      return v != null ? Tristate.fromBoolean(v) : Tristate.UNDEFINED;
    }

    @Override
    public Map<String, Boolean> getPermissionMap() {
      return returnNullMap ? null : permissionMap;
    }
  }
}
