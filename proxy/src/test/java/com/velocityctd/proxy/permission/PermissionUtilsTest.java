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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    PermissionSubject subject = mock(PermissionSubject.class);
    assertThrows(NullPointerException.class,
        () -> PermissionUtils.findHighestPermissionValue(subject, null, 10));
  }

  @Test
  void rejectsPrefixWithoutTrailingDot() {
    PermissionSubject subject = mock(PermissionSubject.class);
    assertThrows(IllegalArgumentException.class,
        () -> PermissionUtils.findHighestPermissionValue(subject, "prefix", 10));
  }

  @Test
  void rejectsMaxLessThanOrEqualToZero() {
    PermissionSubject subject = mock(PermissionSubject.class);
    assertThrows(IllegalArgumentException.class,
        () -> PermissionUtils.findHighestPermissionValue(subject, "perm.", 0));
    assertThrows(IllegalArgumentException.class,
        () -> PermissionUtils.findHighestPermissionValue(subject, "perm.", -1));
  }

  @Test
  void returnsEmptyWhenPermissionMapIsEmptyAndFastTrackFails() {
    PermissionSubject subject = mock(PermissionSubject.class);
    when(subject.getPermissionMap()).thenReturn(Map.of());
    when(subject.getPermissionValue("perm.100")).thenReturn(Tristate.FALSE);

    assertEquals(Optional.empty(),
        PermissionUtils.findHighestPermissionValue(subject, "perm.", 100));
  }

  @Test
  void returnsMaxFromPermissionMap() {
    PermissionSubject subject = mock(PermissionSubject.class);
    when(subject.getPermissionMap()).thenReturn(Map.of("timeout.30", true));
    when(subject.getPermissionValue("timeout.100")).thenReturn(Tristate.FALSE);

    assertEquals(Optional.of(30),
        PermissionUtils.findHighestPermissionValue(subject, "timeout.", 100));
  }

  @Test
  void returnsHighestFromMultiplePermissionMapEntries() {
    PermissionSubject subject = mock(PermissionSubject.class);
    when(subject.getPermissionMap()).thenReturn(Map.of(
        "timeout.10", true,
        "timeout.25", true,
        "timeout.50", true,
        "timeout.5", true
    ));
    when(subject.getPermissionValue("timeout.100")).thenReturn(Tristate.FALSE);

    assertEquals(Optional.of(50),
        PermissionUtils.findHighestPermissionValue(subject, "timeout.", 100));
  }

  @Test
  void filtersOutValuesAboveMaxFromPermissionMap() {
    PermissionSubject subject = mock(PermissionSubject.class);
    when(subject.getPermissionMap()).thenReturn(Map.of(
        "timeout.10", true,
        "timeout.200", true,
        "timeout.50", true
    ));
    when(subject.getPermissionValue("timeout.100")).thenReturn(Tristate.FALSE);

    assertEquals(Optional.of(50),
        PermissionUtils.findHighestPermissionValue(subject, "timeout.", 100));
  }

  @Test
  void fastTrackReturnsMaxWhenPermissionGranted() {
    PermissionSubject subject = mock(PermissionSubject.class);
    when(subject.getPermissionMap()).thenReturn(Map.of());
    when(subject.getPermissionValue("timeout.100")).thenReturn(Tristate.TRUE);

    assertEquals(Optional.of(100),
        PermissionUtils.findHighestPermissionValue(subject, "timeout.", 100));
  }

  @Test
  void skipsFalseEntriesInPermissionMap() {
    PermissionSubject subject = mock(PermissionSubject.class);
    when(subject.getPermissionMap()).thenReturn(Map.of(
        "timeout.50", false,
        "timeout.30", true,
        "timeout.10", false
    ));
    when(subject.getPermissionValue("timeout.100")).thenReturn(Tristate.FALSE);

    assertEquals(Optional.of(30),
        PermissionUtils.findHighestPermissionValue(subject, "timeout.", 100));
  }

  @Test
  void skipsNonNumericSuffixesGracefully() {
    PermissionSubject subject = mock(PermissionSubject.class);
    when(subject.getPermissionMap()).thenReturn(Map.of(
        "timeout.abc", true,
        "timeout.30", true,
        "timeout.!@#", true
    ));
    when(subject.getPermissionValue("timeout.100")).thenReturn(Tristate.FALSE);

    assertEquals(Optional.of(30),
        PermissionUtils.findHighestPermissionValue(subject, "timeout.", 100));
  }

  @Test
  void findsHighestViaGetPermissionValueWhenMapIsNull() {
    PermissionSubject subject = mock(PermissionSubject.class);
    when(subject.getPermissionMap()).thenReturn(null);
    when(subject.getPermissionValue("timeout.10")).thenReturn(Tristate.FALSE);
    when(subject.getPermissionValue("timeout.9")).thenReturn(Tristate.FALSE);
    when(subject.getPermissionValue("timeout.8")).thenReturn(Tristate.TRUE);

    assertEquals(Optional.of(8),
        PermissionUtils.findHighestPermissionValue(subject, "timeout.", 10));
  }

  @Test
  void returnsEmptyWhenPermissionMapIsNullAndNoneGranted() {
    PermissionSubject subject = mock(PermissionSubject.class);
    when(subject.getPermissionMap()).thenReturn(null);
    for (int i = 1; i <= 10; i++) {
      when(subject.getPermissionValue("timeout." + i)).thenReturn(Tristate.FALSE);
    }

    assertEquals(Optional.empty(),
        PermissionUtils.findHighestPermissionValue(subject, "timeout.", 10));
  }

  @Test
  void returnsValueWhenMaxIsOneAndGranted() {
    PermissionSubject subject = mock(PermissionSubject.class);
    when(subject.getPermissionMap()).thenReturn(null);
    when(subject.getPermissionValue("timeout.1")).thenReturn(Tristate.TRUE);

    assertEquals(Optional.of(1),
        PermissionUtils.findHighestPermissionValue(subject, "timeout.", 1));
  }
}
