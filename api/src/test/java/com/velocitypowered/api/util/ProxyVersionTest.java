/*
 * Copyright (C) 2018-2026 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProxyVersionTest {

  private static ProxyVersion make(String version) {
    return new ProxyVersion("Velocity-CTD", "Velocity(-CTD) Contributors", version);
  }

  @Test
  void unknownVersionIsDev() {
    assertTrue(make("<unknown>").isDevelopmentVersion(),
        "Manifest missing — running from IDE or unpacked classes");
  }

  @Test
  void snapshotWithoutBuildNumberIsDev() {
    assertTrue(make("3.5.0-SNAPSHOT-git-abc12345").isDevelopmentVersion(),
        "Local checkout build, no BUILD_NUMBER env at gradle time");
  }

  @Test
  void snapshotWithBuildNumberIsRelease() {
    assertFalse(make("3.5.0-SNAPSHOT-git-abc12345-b42").isDevelopmentVersion(),
        "CI release build — BUILD_NUMBER env set, manifest stamped with -b<N>");
  }

  @Test
  void taggedNonSnapshotIsRelease() {
    assertFalse(make("3.5.0").isDevelopmentVersion(),
        "Hypothetical future tagged release with non-SNAPSHOT gradle.properties");
  }

  @Test
  void singleDigitBuildNumberIsRelease() {
    assertFalse(make("3.5.0-SNAPSHOT-git-abc12345-b1").isDevelopmentVersion(),
        "First CI build (build_nr=1) should be recognized as release");
  }

  @Test
  void largeBuildNumberIsRelease() {
    assertFalse(make("3.5.0-SNAPSHOT-git-abc12345-b999999").isDevelopmentVersion(),
        "Build number is unbounded; regex must allow arbitrary digit count");
  }

  @Test
  void letterSuffixIsNotMistakenForBuildNumber() {
    assertTrue(make("3.5.0-SNAPSHOT-beta").isDevelopmentVersion(),
        "Letters after -b must not match the build-number regex");
  }

  @Test
  void buildNumberMustBeTerminalSuffix() {
    assertTrue(make("3.5.0-SNAPSHOT-git-abc-b42-something").isDevelopmentVersion(),
        "Build-number suffix must be at the end of the version string");
  }
}
