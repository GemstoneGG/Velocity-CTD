/*
 * Copyright (C) 2018-2025 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UuidUtils}.
 *
 * <p>These tests validate correct parsing and formatting of UUIDs, particularly ensuring that:
 * <ul>
 *   <li>Dashed and undashed conversions are consistent</li>
 *   <li>Leading zero bits are preserved correctly</li>
 *   <li>Offline player UUID generation is deterministic</li>
 * </ul>
 */
class UuidUtilsTest {

  /**
   * A UUID used to verify conversion to and from undashed format.
   */
  private static final UUID EXPECTED_DASHED_UUID = UUID
      .fromString("6b501978-d3be-4f33-bcf6-6e7808f37a0d");

  /**
   * The undashed string version of {@link #EXPECTED_DASHED_UUID}.
   */
  private static final String ACTUAL_UNDASHED_UUID = EXPECTED_DASHED_UUID.toString()
      .replace("-", "");

  /**
   * A UUID consisting entirely of zero bits, used to validate serialization edge cases.
   */
  private static final UUID ISSUE_109_ZERO_UUID = new UUID(0, 0);

  /**
   * The undashed representation of {@link #ISSUE_109_ZERO_UUID}.
   */
  private static final String ISSUE_109_ZERO_UUID_UNDASHED = "00000000000000000000000000000000";

  /**
   * A UUID with only the least significant bit set.
   */
  private static final UUID ISSUE_109_ONE_LSB_UUID = new UUID(0, 1);

  /**
   * The undashed representation of {@link #ISSUE_109_ONE_LSB_UUID}.
   */
  private static final String ISSUE_109_ONE_LSB_UUID_UNDASHED = "00000000000000000000000000000001";

  /**
   * A UUID with both most and least significant bits set to 1.
   */
  private static final UUID ISSUE_109_ONE_MLSB_UUID = new UUID(1, 1);

  /**
   * The undashed representation of {@link #ISSUE_109_ONE_MLSB_UUID}.
   */
  private static final String ISSUE_109_ONE_MLSB_UUID_UNDASHED = "00000000000000010000000000000001";

  /**
   * A UUID with leading zeros in several segments, used to ensure preservation of formatting.
   */
  private static final UUID ISSUE_109_LEADING_ZERO_UUID = UUID
      .fromString("0d470a25-0416-48a1-b7a6-2a27aa5eb251");

  /**
   * The undashed representation of {@link #ISSUE_109_LEADING_ZERO_UUID}.
   */
  private static final String ISSUE_109_LEADING_ZERO_UNDASHED = "0d470a25041648a1b7a62a27aa5eb251";

  /**
   * A deterministic offline UUID for the player name "tuxed", based on the offline UUID algorithm.
   */
  private static final UUID TEST_OFFLINE_PLAYER_UUID = UUID
      .fromString("708f6260-183d-3912-bbde-5e279a5e739a");

  /**
   * The test player name used to generate {@link #TEST_OFFLINE_PLAYER_UUID}.
   */
  private static final String TEST_OFFLINE_PLAYER = "tuxed";

  @Test
  void generateOfflinePlayerUuid() {
    assertEquals(TEST_OFFLINE_PLAYER_UUID, UuidUtils.generateOfflinePlayerUuid(TEST_OFFLINE_PLAYER),
        "UUIDs do not match");
  }

  @Test
  void fromUndashed() {
    assertEquals(EXPECTED_DASHED_UUID, UuidUtils.fromUndashed(ACTUAL_UNDASHED_UUID),
        "UUIDs do not match");
  }

  @Test
  void toUndashed() {
    assertEquals(ACTUAL_UNDASHED_UUID, UuidUtils.toUndashed(EXPECTED_DASHED_UUID),
        "UUIDs do not match");
  }

  @Test
  void zeroUuidIssue109() {
    assertEquals(ISSUE_109_ZERO_UUID, UuidUtils.fromUndashed(ISSUE_109_ZERO_UUID_UNDASHED),
        "UUIDs do not match");
    assertEquals(ISSUE_109_ZERO_UUID_UNDASHED, UuidUtils.toUndashed(ISSUE_109_ZERO_UUID),
        "UUIDs do not match");
  }

  @Test
  void leadingZeroUuidIssue109() {
    assertEquals(ISSUE_109_LEADING_ZERO_UUID,
        UuidUtils.fromUndashed(ISSUE_109_LEADING_ZERO_UNDASHED), "UUIDs do not match");
    assertEquals(ISSUE_109_LEADING_ZERO_UNDASHED, UuidUtils.toUndashed(ISSUE_109_LEADING_ZERO_UUID),
        "UUIDs do not match");
  }

  @Test
  void oneUuidLsbIssue109() {
    assertEquals(ISSUE_109_ONE_LSB_UUID, UuidUtils.fromUndashed(ISSUE_109_ONE_LSB_UUID_UNDASHED),
        "UUIDs do not match");
    assertEquals(ISSUE_109_ONE_LSB_UUID_UNDASHED, UuidUtils.toUndashed(ISSUE_109_ONE_LSB_UUID),
        "UUIDs do not match");
  }

  @Test
  void oneUuidMsbAndLsbIssue109() {
    assertEquals(ISSUE_109_ONE_MLSB_UUID, UuidUtils.fromUndashed(ISSUE_109_ONE_MLSB_UUID_UNDASHED),
        "UUIDs do not match");
    assertEquals(ISSUE_109_ONE_MLSB_UUID_UNDASHED, UuidUtils.toUndashed(ISSUE_109_ONE_MLSB_UUID),
        "UUIDs do not match");
  }
}
