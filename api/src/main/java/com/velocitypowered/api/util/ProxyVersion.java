/*
 * Copyright (C) 2018-2021 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.util;

import com.google.common.base.Preconditions;

/**
 * Provides a version object for the proxy.
 */
public record ProxyVersion(String name, String vendor, String version) {

  /**
   * Creates a new {@link ProxyVersion} instance.
   *
   * @param name    the name for the proxy implementation
   * @param vendor  the vendor for the proxy implementation
   * @param version the version for the proxy implementation
   */
  public ProxyVersion(String name, String vendor, String version) {
    this.name = Preconditions.checkNotNull(name, "name");
    this.vendor = Preconditions.checkNotNull(vendor, "vendor");
    this.version = Preconditions.checkNotNull(version, "version");
  }

  @Override
  public String toString() {
    return "ProxyVersion{"
        + "name='" + name + '\''
        + ", vendor='" + vendor + '\''
        + ", version='" + version + '\''
        + '}';
  }
}
