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

package com.velocityctd.bootstrap;

/**
 * A single library required to run the proxy, described entirely by its Maven coordinates plus a
 * SHA-256 checksum and an {@link Origin} indicating where the bytes come from.
 *
 * @param origin where the artifact bytes are obtained from
 * @param group the Maven group id
 * @param name the Maven artifact id
 * @param version the artifact version
 * @param extension the file extension (usually {@code jar})
 * @param classifier the Maven classifier, or {@code null} when there is none
 * @param sha256 the lowercase hex SHA-256 of the artifact
 * @param embeddedResource the in-jar resource path for {@link Origin#EMBEDDED} artifacts, otherwise {@code null}
 */
public record Dependency(
    Origin origin,
    String group,
    String name,
    String version,
    String extension,
    String classifier,
    String sha256,
    String embeddedResource) {

  /**
   * Where the bytes for a {@link Dependency} are obtained from.
   */
  public enum Origin {
    /**
     * Downloaded from one of the configured Maven repositories.
     */
    MAVEN,

    /**
     * Extracted from a jar-in-jar resource embedded in the bootstrap jar.
     */
    EMBEDDED
  }

  /**
   * Returns the artifact file name, including the classifier when present.
   *
   * @return the artifact file name, e.g. {@code component-1.0.1.jar}
   */
  public String fileName() {
    String classifierSuffix = classifier == null ? "" : "-" + classifier;
    return name + "-" + version + classifierSuffix + "." + extension;
  }

  /**
   * Returns the artifact location relative to a Maven repository root or the local libraries
   * directory.
   *
   * @return the relative path, e.g. {@code gg/gemstone/component/1.0.1/component-1.0.1.jar}
   */
  public String relativePath() {
    return group.replace('.', '/') + '/' + name + '/' + version + '/' + fileName();
  }
}
