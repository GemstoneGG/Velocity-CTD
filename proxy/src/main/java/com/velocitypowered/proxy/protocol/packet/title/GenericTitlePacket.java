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

package com.velocitypowered.proxy.protocol.packet.title;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import io.netty.buffer.ByteBuf;

/**
 * The {@code GenericTitlePacket} class serves as the base class for all title-related packets
 * in Minecraft. This class provides common functionality and properties for handling title, subtitle,
 * action bar, and timing-related packets.
 *
 * <p>Subclasses of {@code GenericTitlePacket} implement specific behavior for different types of title
 * packets, such as titles, subtitles, and action bars.</p>
 */
public abstract class GenericTitlePacket implements MinecraftPacket {

  /**
   * The {@code ActionType} enum represents the different actions that can be performed with a title packet.
   * Each action corresponds to a specific type of title operation, such as setting a title or subtitle,
   * updating timing information, or resetting and hiding titles.
   */
  public enum ActionType {

    /**
     * Sends a main title message to the player.
     */
    SET_TITLE(0),

    /**
     * Sends a subtitle message displayed below the main title.
     */
    SET_SUBTITLE(1),

    /**
     * Sends a message to the action bar (above the hotbar).
     */
    SET_ACTION_BAR(2),

    /**
     * Updates the fade-in, stay, and fade-out timing for titles and subtitles.
     */
    SET_TIMES(3),

    /**
     * Hides the currently displayed title and subtitle without resetting internal timings.
     */
    HIDE(4),

    /**
     * Resets all title-related state, including currently displayed messages and configured timings.
     */
    RESET(5);

    /**
     * The numeric action ID used in older protocol versions.
     */
    private final int action;

    ActionType(final int action) {
      this.action = action;
    }

    /**
     * Returns the protocol-dependent action ID.
     *
     * <p>In Minecraft versions before 1.11, RESET and HIDE share the same action ID.
     * This method ensures correct translation for backward compatibility.</p>
     *
     * @param version the target protocol version
     * @return the protocol-specific action ID
     */
    public int getAction(final ProtocolVersion version) {
      return version.lessThan(ProtocolVersion.MINECRAFT_1_11)
          ? action > 2 ? action - 1 : action : action;
    }
  }

  /**
   * The action this packet will invoke when sent.
   */
  private ActionType action;

  /**
   * Sets the action type for this title packet.
   *
   * @param action the title action (e.g., SET_TITLE, HIDE)
   */
  protected void setAction(final ActionType action) {
    this.action = action;
  }

  /**
   * Returns the action this packet is configured to perform.
   *
   * @return the {@link ActionType}
   */
  public final ActionType getAction() {
    return action;
  }

  /**
   * Returns the title component of this packet, if applicable.
   *
   * @return the {@link ComponentHolder} used for display
   * @throws UnsupportedOperationException if not supported for this packet type
   */
  public ComponentHolder getComponent() {
    throw new UnsupportedOperationException("Invalid function for this TitlePacket ActionType");
  }

  /**
   * Sets the title component of this packet, if applicable.
   *
   * @param component the component to assign
   * @throws UnsupportedOperationException if not supported for this packet type
   */
  public void setComponent(final ComponentHolder component) {
    throw new UnsupportedOperationException("Invalid function for this TitlePacket ActionType");
  }

  /**
   * Returns the fade-in time, if supported.
   *
   * @return the fade-in duration in ticks
   * @throws UnsupportedOperationException if not supported
   */
  public int getFadeIn() {
    throw new UnsupportedOperationException("Invalid function for this TitlePacket ActionType");
  }

  /**
   * Sets the fade-in time, if supported.
   *
   * @param fadeIn the fade-in duration in ticks
   * @throws UnsupportedOperationException if not supported
   */
  public void setFadeIn(final int fadeIn) {
    throw new UnsupportedOperationException("Invalid function for this TitlePacket ActionType");
  }

  /**
   * Returns the stay duration, if supported.
   *
   * @return the stay duration in ticks
   * @throws UnsupportedOperationException if not supported
   */
  public int getStay() {
    throw new UnsupportedOperationException("Invalid function for this TitlePacket ActionType");
  }

  /**
   * Sets the stay duration, if supported.
   *
   * @param stay the duration to display the title in ticks
   * @throws UnsupportedOperationException if not supported
   */
  public void setStay(final int stay) {
    throw new UnsupportedOperationException("Invalid function for this TitlePacket ActionType");
  }

  /**
   * Returns the fade-out time, if supported.
   *
   * @return the fade-out duration in ticks
   * @throws UnsupportedOperationException if not supported
   */
  public int getFadeOut() {
    throw new UnsupportedOperationException("Invalid function for this TitlePacket ActionType");
  }

  /**
   * Sets the fade-out time, if supported.
   *
   * @param fadeOut the fade-out duration in ticks
   * @throws UnsupportedOperationException if not supported
   */
  public void setFadeOut(final int fadeOut) {
    throw new UnsupportedOperationException("Invalid function for this TitlePacket ActionType");
  }

  @Override
  public final void decode(final ByteBuf buf, final ProtocolUtils.Direction direction,
                           final ProtocolVersion version) {
    throw new UnsupportedOperationException(); // encode only
  }

  /**
   * Creates a version and type-dependent TitlePacket.
   *
   * @param type    Action the packet should invoke
   * @param version Protocol version of the target player
   * @return GenericTitlePacket instance that follows the invoker type/version
   */
  public static GenericTitlePacket constructTitlePacket(final ActionType type, final ProtocolVersion version) {
    GenericTitlePacket packet;
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_17)) {
      packet = switch (type) {
        case SET_ACTION_BAR -> new TitleActionbarPacket();
        case SET_SUBTITLE -> new TitleSubtitlePacket();
        case SET_TIMES -> new TitleTimesPacket();
        case SET_TITLE -> new TitleTextPacket();
        case HIDE, RESET -> new TitleClearPacket();
      };
    } else {
      packet = new LegacyTitlePacket();
    }

    packet.setAction(type);
    return packet;
  }
}
