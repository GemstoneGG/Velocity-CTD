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

package com.velocitypowered.proxy.connection.client;

import com.velocitypowered.api.proxy.player.PlayerSettings;
import com.velocitypowered.api.proxy.player.SkinParts;
import com.velocitypowered.proxy.protocol.packet.ClientSettingsPacket;
import java.util.Locale;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Wraps the settings received in the Client Settings packet.
 */
public class ClientSettingsWrapper implements PlayerSettings {

  /**
   * A default fallback {@link PlayerSettings} instance used when no client settings have been received.
   */
  static final PlayerSettings DEFAULT = new ClientSettingsWrapper(
      new ClientSettingsPacket("en_us", (byte) 2, 0, true, (short) 0, 1, false, false, 0));

  /**
   * The raw {@link ClientSettingsPacket} received from the client, containing raw configuration
   * such as language, view distance, and UI preferences.
   */
  private final ClientSettingsPacket settings;

  /**
   * The parsed skin parts from the client, representing which visual features are enabled
   * (e.g., hat, jacket, sleeves).
   */
  private final SkinParts parts;

  /**
   * The cached {@link Locale} object derived from the raw language string in the settings packet.
   * Computed lazily from the {@code settings.getLocale()} field.
   */
  private @Nullable Locale locale;

  ClientSettingsWrapper(final ClientSettingsPacket settings) {
    this.settings = settings;
    this.parts = new SkinParts((byte) settings.getSkinParts());
  }

  /**
   * Returns the client's preferred {@link Locale}, derived from their language setting.
   *
   * <p>This is lazily computed and cached from the locale string in the client settings packet,
   * replacing underscores with hyphens to be compatible with {@link Locale#forLanguageTag(String)}.</p>
   *
   * @return the {@link Locale} specified by the client
   */
  @Override
  public Locale getLocale() {
    if (locale == null) {
      locale = Locale.forLanguageTag(settings.getLocale().replaceAll("_", "-"));
    }

    return locale;
  }

  /**
   * Returns the client's configured view distance (in chunks).
   *
   * @return the view distance specified in the client settings
   */
  @Override
  public byte getViewDistance() {
    return settings.getViewDistance();
  }

  /**
   * Returns the client's configured chat visibility preference.
   *
   * @return the {@link ChatMode} representing chat visibility
   */
  @Override
  public ChatMode getChatMode() {
    return switch (settings.getChatVisibility()) {
      case 1 -> ChatMode.COMMANDS_ONLY;
      case 2 -> ChatMode.HIDDEN;
      default -> ChatMode.SHOWN;
    };
  }

  /**
   * Indicates whether the client has chat colors enabled in their settings.
   *
   * @return {@code true} if chat colors are enabled, {@code false} otherwise
   */
  @Override
  public boolean hasChatColors() {
    return settings.isChatColors();
  }

  /**
   * Returns the set of enabled skin parts (e.g., hat, jacket, sleeves).
   *
   * @return the {@link SkinParts} object representing enabled visual features
   */
  @Override
  public SkinParts getSkinParts() {
    return parts;
  }

  /**
   * Returns the client's preferred main hand setting.
   *
   * @return {@link MainHand#RIGHT} if the value is 1, otherwise {@link MainHand#LEFT}
   */
  @Override
  public MainHand getMainHand() {
    return settings.getMainHand() == 1 ? MainHand.RIGHT : MainHand.LEFT;
  }

  /**
   * Indicates whether the client allows their profile to be included in public listings
   * (i.e., whether the player is discoverable by others).
   *
   * @return {@code true} if allowed, {@code false} otherwise
   */
  @Override
  public boolean isClientListingAllowed() {
    return settings.isClientListingAllowed();
  }

  /**
   * Indicates whether the client has enabled Mojang's text filtering feature.
   *
   * @return {@code true} if filtering is enabled, {@code false} otherwise
   */
  @Override
  public boolean isTextFilteringEnabled() {
    return settings.isTextFilteringEnabled();
  }

  /**
   * Returns the client's selected particle detail level.
   *
   * @return the {@link ParticleStatus} representing particle visibility
   */
  @Override
  public ParticleStatus getParticleStatus() {
    return switch (settings.getParticleStatus()) {
      case 1 -> ParticleStatus.DECREASED;
      case 2 -> ParticleStatus.MINIMAL;
      default -> ParticleStatus.ALL;
    };
  }

  /**
   * Compares this {@code ClientSettingsWrapper} to another object for equality.
   *
   * <p>Two {@code ClientSettingsWrapper} instances are considered equal if they wrap
   * identical {@link ClientSettingsPacket} objects, {@link SkinParts} values,
   * and computed {@link Locale} instances.</p>
   *
   * @param o the object to compare against
   * @return {@code true} if the given object is a {@code ClientSettingsWrapper} with equal content,
   *         {@code false} otherwise
   */
  @Override
  public boolean equals(final @Nullable Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ClientSettingsWrapper that = (ClientSettingsWrapper) o;
    return Objects.equals(settings, that.settings) && Objects.equals(parts, that.parts) && Objects.equals(locale, that.locale);
  }

  /**
   * Computes the hash code for this {@code ClientSettingsWrapper} based on its internal state.
   *
   * @return a hash code derived from the {@link ClientSettingsPacket}, {@link SkinParts}, and
   *         {@link Locale} values
   */
  @Override
  public int hashCode() {
    return Objects.hash(settings, parts, locale);
  }
}
