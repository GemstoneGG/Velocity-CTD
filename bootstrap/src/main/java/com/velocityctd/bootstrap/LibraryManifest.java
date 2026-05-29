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

import com.velocityctd.bootstrap.Dependency.Origin;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The parsed contents of the bootstrap library list resource: the ordered set of Maven repositories
 * to try and the full list of {@link Dependency dependencies} the proxy needs to run.
 *
 * <p>The on-disk format is a simple tab-separated, line-oriented text file.
 * Blank lines and lines starting with {@code #} are ignored.
 *
 * @param mainClass the fully qualified name of the class to launch once the libraries are resolved
 * @param repositories the Maven repository base URLs, in the order they should be tried
 * @param dependencies the dependencies required to run the proxy
 */
public record LibraryManifest(
    String mainClass,
    List<String> repositories,
    List<Dependency> dependencies) {

  /**
   * Parses a library manifest from the given input stream.
   *
   * @param input the stream to read the manifest from
   * @return the parsed manifest
   */
  public static LibraryManifest parse(InputStream input) {
    String mainClass = null;
    List<String> repositories = new ArrayList<>();
    List<Dependency> dependencies = new ArrayList<>();

    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        String trimmed = line.strip();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
          continue;
        }
        String[] parts = trimmed.split("\t");
        switch (parts[0]) {
          case "main-class" -> mainClass = parts[1];
          case "repo" -> repositories.add(parts[1]);
          case "dep" -> dependencies.add(parseDependency(parts));
          default -> throw new IllegalStateException("Unknown manifest entry: " + trimmed);
        }
      }
    } catch (IOException exception) {
      throw new UncheckedIOException("Failed to read library manifest", exception);
    }

    if (mainClass == null) {
      throw new IllegalStateException("Library manifest is missing a main-class entry");
    }

    return new LibraryManifest(mainClass, List.copyOf(repositories), List.copyOf(dependencies));
  }

  private static Dependency parseDependency(String[] parts) {
    Origin origin = Origin.valueOf(parts[1]);
    String group = parts[2];
    String name = parts[3];
    String version = parts[4];
    String extension = parts[5];
    String classifier = "-".equals(parts[6]) ? null : parts[6];
    String sha256 = parts[7];
    String embeddedResource = parts.length > 8 ? parts[8] : null;
    return new Dependency(origin, group, name, version, extension, classifier, sha256, embeddedResource);
  }
}
