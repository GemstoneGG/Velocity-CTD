/*
 * Copyright (C) 2018-2026 Velocity Contributors
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

package com.velocitypowered.proxy.protocol.packet;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents the client settings packet in Minecraft, which is sent by the client
 * to the server to communicate its settings such as locale, view distance, chat preferences,
 * skin customization, and other client-side configurations.
 */
public class ClientSettingsPacket implements MinecraftPacket {

  /**
   * The client's locale, such as {@code en_us}.
   *
   * <p>This is used for determining language preferences in translatable messages.</p>
   *
   * <p>May be {@code null} if unset before decoding or initialization.</p>
   */
  private @Nullable String locale;

  /**
   * The client's view distance setting in chunks.
   */
  private byte viewDistance;

  /**
   * The client's chat visibility setting.
   * <ul>
   *   <li>0 - Full</li>
   *   <li>1 - System</li>
   *   <li>2 - Hidden</li>
   * </ul>
   */
  private int chatVisibility;

  /**
   * Whether the client has enabled colored chat text.
   */
  private boolean chatColors;

  /**
   * The client's selected difficulty setting (1.7 protocol only).
   *
   * <p>This field is ignored in modern protocol versions.</p>
   */
  private byte difficulty;

  /**
   * Bitfield representing which skin parts are visible (e.g. hat, cape, jacket).
   */
  private short skinParts;

  /**
   * The client's selected main hand.
   * <ul>
   *   <li>0 - Left</li>
   *   <li>1 - Right</li>
   * </ul>
   */
  private int mainHand;

  /**
   * Whether text filtering is enabled on the client.
   *
   * <p>Introduced in Minecraft 1.17.</p>
   */
  private boolean textFilteringEnabled;

  /**
   * Whether the client allows their presence to appear in server player lists.
   *
   * <p>Introduced in Minecraft 1.18 to support privacy preferences.</p>
   */
  private boolean clientListingAllowed;

  /**
   * The client's particle rendering status.
   * <ul>
   *   <li>0 - Minimal</li>
   *   <li>1 - Decreased</li>
   *   <li>2 - All</li>
   * </ul>
   *
   * <p>Introduced in Minecraft 1.21.2.</p>
   */
  private int particleStatus;

  /**
   * Constructs a new, empty {@link ClientSettingsPacket}.
   *
   * <p>All values are uninitialized until explicitly set or populated by {@link #decode}.</p>
   */
  public ClientSettingsPacket() {
  }

  /**
   * Constructs a new {@code ClientSettingsPacket} with the specified settings.
   *
   * @param locale the client's locale setting
   * @param viewDistance the view distance
   * @param chatVisibility the client's chat visibility setting
   * @param chatColors whether chat colors are enabled
   * @param skinParts the customization for skin parts
   * @param mainHand the client's main hand preference
   * @param textFilteringEnabled whether text filtering is enabled
   * @param clientListingAllowed whether the client allows listing
   * @param particleStatus whether particles are enabled
   */
  public ClientSettingsPacket(final @Nullable String locale, final byte viewDistance, final int chatVisibility, final boolean chatColors,
                              final short skinParts, final int mainHand, final boolean textFilteringEnabled, final boolean clientListingAllowed,
                              final int particleStatus) {
    this.locale = locale;
    this.viewDistance = viewDistance;
    this.chatVisibility = chatVisibility;
    this.chatColors = chatColors;
    this.skinParts = skinParts;
    this.mainHand = mainHand;
    this.textFilteringEnabled = textFilteringEnabled;
    this.clientListingAllowed = clientListingAllowed;
    this.particleStatus = particleStatus;
  }

  /**
   * Gets the client's locale.
   *
   * @return the locale
   * @throws IllegalStateException if no locale is specified
   */
  public String getLocale() {
    if (locale == null) {
      throw new IllegalStateException("No locale specified");
    }

    return locale;
  }

  /**
   * Sets the client's locale string.
   *
   * @param locale the locale to set, or {@code null} to clear it
   */
  public void setLocale(final @Nullable String locale) {
    this.locale = locale;
  }

  /**
   * Gets the client's view distance.
   *
   * @return the view distance
   */
  public byte getViewDistance() {
    return viewDistance;
  }

  /**
   * Sets the client's view distance.
   *
   * @param viewDistance the view distance to set
   */
  public void setViewDistance(final byte viewDistance) {
    this.viewDistance = viewDistance;
  }

  /**
   * Gets the chat visibility setting.
   *
   * @return the chat visibility (0 = full, 1 = system, 2 = hidden)
   */
  public int getChatVisibility() {
    return chatVisibility;
  }

  /**
   * Sets the chat visibility setting.
   *
   * @param chatVisibility the new chat visibility value
   */
  public void setChatVisibility(final int chatVisibility) {
    this.chatVisibility = chatVisibility;
  }

  /**
   * Returns whether the client has chat colors enabled.
   *
   * @return {@code true} if chat colors are enabled; {@code false} otherwise
   */
  public boolean isChatColors() {
    return chatColors;
  }

  /**
   * Sets whether chat colors are enabled.
   *
   * @param chatColors {@code true} to enable chat colors, {@code false} to disable
   */
  public void setChatColors(final boolean chatColors) {
    this.chatColors = chatColors;
  }

  /**
   * Gets the value representing which skin parts are enabled.
   *
   * @return a bitfield representing the enabled skin parts
   */
  public short getSkinParts() {
    return skinParts;
  }

  /**
   * Sets the skin parts bitfield.
   *
   * @param skinParts the bitfield representing enabled skin parts
   */
  public void setSkinParts(final short skinParts) {
    this.skinParts = skinParts;
  }

  /**
   * Gets the client's selected main hand.
   *
   * @return 0 for left, 1 for right
   */
  public int getMainHand() {
    return mainHand;
  }

  /**
   * Sets the client's selected main hand.
   *
   * @param mainHand 0 for left, 1 for right
   */
  public void setMainHand(final int mainHand) {
    this.mainHand = mainHand;
  }

  /**
   * Returns whether the client has text filtering enabled.
   *
   * @return {@code true} if text filtering is enabled; {@code false} otherwise
   */
  public boolean isTextFilteringEnabled() {
    return textFilteringEnabled;
  }

  /**
   * Sets whether text filtering is enabled.
   *
   * @param textFilteringEnabled {@code true} to enable text filtering; {@code false} to disable
   */
  public void setTextFilteringEnabled(final boolean textFilteringEnabled) {
    this.textFilteringEnabled = textFilteringEnabled;
  }

  /**
   * Returns whether the client allows its presence to be shown in the player list.
   *
   * @return {@code true} if the client allows listing; {@code false} otherwise
   */
  public boolean isClientListingAllowed() {
    return clientListingAllowed;
  }

  /**
   * Sets whether the client allows its presence to be shown in the player list.
   *
   * @param clientListingAllowed {@code true} to allow listing; {@code false} to prevent it
   */
  public void setClientListingAllowed(final boolean clientListingAllowed) {
    this.clientListingAllowed = clientListingAllowed;
  }

  /**
   * Gets the client's particle status setting.
   *
   * @return the particle status (0 = minimal, 1 = decreased, 2 = all)
   */
  public int getParticleStatus() {
    return particleStatus;
  }

  /**
   * Sets the particle status preference.
   *
   * @param particleStatus 0 for minimal, 1 for decreased, 2 for all
   */
  public void setParticleStatus(final int particleStatus) {
    this.particleStatus = particleStatus;
  }

  /**
   * Returns a string representation of this client settings packet.
   *
   * <p>This includes locale, view distance, chat, and UI preferences.</p>
   *
   * @return a string describing the packet's contents
   */
  @Override
  public String toString() {
    return "ClientSettings{"
        + "locale='" + locale + '\''
        + ", viewDistance=" + viewDistance
        + ", chatVisibility=" + chatVisibility
        + ", chatColors=" + chatColors + ", skinParts=" + skinParts
        + ", mainHand=" + mainHand
        + ", chatFilteringEnabled=" + textFilteringEnabled
        + ", clientListingAllowed=" + clientListingAllowed
        + ", particleStatus=" + particleStatus
        + '}';
  }

  /**
   * Decodes this client settings packet from the provided {@link ByteBuf}.
   *
   * <p>This reads the client's locale, view distance, chat settings, skin parts, main hand,
   * and additional flags depending on the protocol version (e.g., filtering, listing, particles).</p>
   *
   * @param buf the buffer to read from
   * @param direction the direction of the packet
   * @param version the Minecraft protocol version
   */
  @Override
  public void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    this.locale = ProtocolUtils.readString(buf, 16);
    this.viewDistance = buf.readByte();
    this.chatVisibility = ProtocolUtils.readVarInt(buf);
    this.chatColors = buf.readBoolean();

    if (version.noGreaterThan(ProtocolVersion.MINECRAFT_1_7_6)) {
      this.difficulty = buf.readByte();
    }

    this.skinParts = buf.readUnsignedByte();

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_9)) {
      this.mainHand = ProtocolUtils.readVarInt(buf);

      if (version.noLessThan(ProtocolVersion.MINECRAFT_1_17)) {
        this.textFilteringEnabled = buf.readBoolean();

        if (version.noLessThan(ProtocolVersion.MINECRAFT_1_18)) {
          this.clientListingAllowed = buf.readBoolean();

          if (version.noLessThan(ProtocolVersion.MINECRAFT_1_21_2)) {
            this.particleStatus = ProtocolUtils.readVarInt(buf);
          }
        }
      }
    }
  }

  /**
   * Encodes this client settings packet into the provided {@link ByteBuf}.
   *
   * <p>This writes the client's locale, preferences, and optional settings depending on
   * the target protocol version.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet
   * @param version the Minecraft protocol version
   * @throws IllegalStateException if required fields like locale are not set
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    if (locale == null) {
      throw new IllegalStateException("No locale specified");
    }

    ProtocolUtils.writeString(buf, locale);
    buf.writeByte(viewDistance);
    ProtocolUtils.writeVarInt(buf, chatVisibility);
    buf.writeBoolean(chatColors);

    if (version.noGreaterThan(ProtocolVersion.MINECRAFT_1_7_6)) {
      buf.writeByte(difficulty);
    }

    buf.writeByte(skinParts);

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_9)) {
      ProtocolUtils.writeVarInt(buf, mainHand);

      if (version.noLessThan(ProtocolVersion.MINECRAFT_1_17)) {
        buf.writeBoolean(textFilteringEnabled);

        if (version.noLessThan(ProtocolVersion.MINECRAFT_1_18)) {
          buf.writeBoolean(clientListingAllowed);
        }

        if (version.noLessThan(ProtocolVersion.MINECRAFT_1_21_2)) {
          ProtocolUtils.writeVarInt(buf, particleStatus);
        }
      }
    }
  }

  /**
   * Handles this client settings packet using the specified {@link MinecraftSessionHandler}.
   *
   * <p>This delegates packet processing to {@code handler.handle(this)} to apply the client’s
   * settings to the session.</p>
   *
   * @param handler the session handler responsible for processing this packet
   * @return {@code true} if the packet was handled successfully
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  /**
   * Compares this client settings packet with another for equality.
   *
   * <p>Two packets are equal if all setting fields are equivalent.</p>
   *
   * @param o the object to compare against
   * @return {@code true} if equal, {@code false} otherwise
   */
  @Override
  public boolean equals(final @Nullable Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final ClientSettingsPacket that = (ClientSettingsPacket) o;
    return viewDistance == that.viewDistance
        && chatVisibility == that.chatVisibility
        && chatColors == that.chatColors
        && difficulty == that.difficulty
        && skinParts == that.skinParts
        && mainHand == that.mainHand
        && textFilteringEnabled == that.textFilteringEnabled
        && clientListingAllowed == that.clientListingAllowed
        && particleStatus == that.particleStatus
        && Objects.equals(locale, that.locale);
  }

  /**
   * Returns a hash code for this client settings packet.
   *
   * <p>This is based on all relevant setting fields such as locale, distance,
   * visibility, filtering, and hand preferences.</p>
   *
   * @return the computed hash code
   */
  @Override
  public int hashCode() {
    return Objects.hash(
        locale,
        viewDistance,
        chatVisibility,
        chatColors,
        difficulty,
        skinParts,
        mainHand,
        textFilteringEnabled,
        clientListingAllowed,
        particleStatus
    );
  }
}
