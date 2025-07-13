/*
 * Copyright (C) 2018-2025 Velocity Contributors
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

package com.velocitypowered.proxy.util;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Matches a player's locale to the "closest" Velocity locale, for message localization.
 */
public final class ClosestLocaleMatcher {

  /**
   * Singleton instance of the {@code ClosestLocaleMatcher}.
   */
  public static final ClosestLocaleMatcher INSTANCE = new ClosestLocaleMatcher();

  /**
   * A mapping from base language tags (e.g., "en") to known Velocity-supported locales.
   */
  private final Map<String, Locale> byLanguage;

  /**
   * A cache that resolves any given {@link Locale} to its closest supported equivalent.
   */
  private final LoadingCache<Locale, Locale> closest;

  private ClosestLocaleMatcher() {
    this.byLanguage = new ConcurrentHashMap<>();
    this.closest = Caffeine.newBuilder().build(sublocale -> {
      final String tag = sublocale.getLanguage();
      return byLanguage.getOrDefault(tag, sublocale);
    });
  }

  /**
   * Registers a known locale.
   *
   * @param locale locale to register
   */
  public void registerKnown(final Locale locale) {
    if (locale.getLanguage().equals(new Locale("zh").getLanguage())) {
      return;
    }

    this.byLanguage.put(locale.getLanguage(), locale);
  }

  /**
   * Attempts to resolve the closest known locale for the given input locale.
   *
   * <p>Falls back to matching just the language if the full locale isn't registered.</p>
   *
   * @param locale the input locale
   * @return the closest supported {@link Locale}, or the original if no match found
   */
  public Locale lookupClosest(final Locale locale) {
    return closest.get(locale);
  }
}
