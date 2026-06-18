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

package com.velocityctd.proxy.util;

import com.velocityctd.proxy.cluster.VelocityClusterPlayer;
import com.velocitypowered.api.proxy.server.ServerPing;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.Nullable;

public class SamplePlayersPlaceholderResolver implements PlaceholderSubstitutor.Resolver {

  private final SamplePlayersPicker samplePlayersPicker;
  private final int defaultMax;
  private final int defaultMaxPerLine;
  private final SamplePlayersPicker.Ordering defaultOrdering;
  private final String defaultEmpty;
  private final String defaultPrefix;
  private final String defaultSeparator;
  private final boolean ignoreAnonymousPlayerRequest;

  private SamplePlayersPlaceholderResolver(Builder builder) {
    this.samplePlayersPicker = builder.samplePlayersPicker;
    this.defaultMax = builder.defaultMax;
    this.defaultMaxPerLine = builder.defaultMaxPerLine;
    this.defaultOrdering = builder.defaultOrdering;
    this.defaultEmpty = builder.defaultEmpty;
    this.defaultPrefix = builder.defaultPrefix;
    this.defaultSeparator = builder.defaultSeparator;
    this.ignoreAnonymousPlayerRequest = builder.ignoreAnonymousPlayerRequest;
  }

  public static Builder builder(SamplePlayersPicker samplePlayersPicker) {
    return new Builder(samplePlayersPicker);
  }

  public Builder toBuilder() {
    return new Builder(samplePlayersPicker)
        .defaultMax(defaultMax)
        .defaultMaxPerLine(defaultMaxPerLine)
        .defaultOrdering(defaultOrdering)
        .defaultEmpty(defaultEmpty)
        .defaultPrefix(defaultPrefix)
        .defaultSeparator(defaultSeparator)
        .ignoreAnonymousPlayerRequest(ignoreAnonymousPlayerRequest);
  }

  @Override
  public @Nullable String resolve(String name, Map<String, String> arguments) {
    if (!name.equals("players")) {
      return null;
    }

    return samplePlayers(
        parse(arguments, "max", defaultMax, Integer::parseInt),
        parse(arguments, "maxPerLine", defaultMaxPerLine, Integer::parseInt),
        parse(arguments, "ordering", defaultOrdering, SamplePlayersPicker.Ordering::valueOf),
        parse(arguments, "empty", defaultEmpty),
        parse(arguments, "prefix", defaultPrefix),
        parse(arguments, "separator", defaultSeparator)
    );
  }

  private String samplePlayers(int max, int maxPerLine, SamplePlayersPicker.Ordering ordering, String emptyString, String prefix, String separator) {
    if (maxPerLine < 1) {
      maxPerLine = 1;
    }

    List<VelocityClusterPlayer> sample = samplePlayersPicker.samplePlayers(max, ordering);
    if (sample.isEmpty()) {
      return emptyString;
    }

    StringBuilder result = new StringBuilder();
    for (int i = 0; i < sample.size(); i++) {
      VelocityClusterPlayer player = sample.get(i);

      String name;
      if (!ignoreAnonymousPlayerRequest && !player.isClientListingAllowed()) {
        name = ServerPing.SamplePlayer.ANONYMOUS.getName();
      } else {
        name = player.getUsername();
      }

      result.append(prefix);
      result.append(name);

      if (i != sample.size() - 1) {
        if ((i + 1) % maxPerLine == 0) {
          result.append(separator.trim());
          result.append('\n');
        } else {
          result.append(separator);
        }
      }
    }

    return result.toString();
  }

  private static <T> T parse(Map<String, String> arguments, String key, T def, Function<String, T> mapper) {
    String value = arguments.get(key);
    if (value == null) {
      return def;
    }

    try {
      return mapper.apply(value);
    } catch (Exception ignored) {
      return def;
    }
  }

  private static String parse(Map<String, String> arguments, String key, String def) {
    return parse(arguments, key, def, Function.identity());
  }

  public static class Builder {

    private final SamplePlayersPicker samplePlayersPicker;
    private int defaultMax = 12;
    private int defaultMaxPerLine = 1;
    private SamplePlayersPicker.Ordering defaultOrdering = SamplePlayersPicker.Ordering.RANDOM;
    private String defaultEmpty = "None";
    private String defaultPrefix = "";
    private String defaultSeparator = ", ";
    private boolean ignoreAnonymousPlayerRequest = false;

    private Builder(SamplePlayersPicker samplePlayersPicker) {
      this.samplePlayersPicker = samplePlayersPicker;
    }

    public Builder defaultMax(int defaultMax) {
      this.defaultMax = defaultMax;
      return this;
    }

    public Builder defaultMaxPerLine(int defaultMaxPerLine) {
      this.defaultMaxPerLine = defaultMaxPerLine;
      return this;
    }

    public Builder defaultOrdering(SamplePlayersPicker.Ordering defaultOrdering) {
      this.defaultOrdering = defaultOrdering;
      return this;
    }

    public Builder defaultEmpty(String defaultEmpty) {
      this.defaultEmpty = defaultEmpty;
      return this;
    }

    public Builder defaultPrefix(String defaultPrefix) {
      this.defaultPrefix = defaultPrefix;
      return this;
    }

    public Builder defaultSeparator(String defaultSeparator) {
      this.defaultSeparator = defaultSeparator;
      return this;
    }

    public Builder ignoreAnonymousPlayerRequest(boolean ignoreAnonymousPlayerRequest) {
      this.ignoreAnonymousPlayerRequest = ignoreAnonymousPlayerRequest;
      return this;
    }

    public SamplePlayersPlaceholderResolver build() {
      return new SamplePlayersPlaceholderResolver(this);
    }
  }
}
