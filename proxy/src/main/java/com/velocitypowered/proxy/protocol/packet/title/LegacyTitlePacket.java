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
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import io.netty.buffer.ByteBuf;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The {@code LegacyTitlePacket} class represents a packet that handles title-related functionality
 * for older versions of Minecraft where title handling differs.
 *
 * <p>This packet is used to send title and subtitle information using legacy methods for clients
 * that do not support the newer title packet format.</p>
 *
 * <p>It extends the {@link GenericTitlePacket}, inheriting basic title properties but is specifically
 * focused on legacy title implementations.</p>
 */
public class LegacyTitlePacket extends GenericTitlePacket {

  /**
   * The text component (title, subtitle, or action bar), if applicable for the current action.
   */
  private @Nullable ComponentHolder component;

  /**
   * Fade-in time in ticks for {@link ActionType#SET_TIMES}.
   */
  private int fadeIn;

  /**
   * Duration in ticks the title should remain visible for {@link ActionType#SET_TIMES}.
   */
  private int stay;

  /**
   * Fade-out time in ticks for {@link ActionType#SET_TIMES}.
   */
  private int fadeOut;

  /**
   * Encodes this {@code LegacyTitlePacket} into the provided {@link ByteBuf}.
   *
   * <p>This method serializes the packet depending on the current {@link ActionType}.
   * For versions older than 1.11, {@link ActionType#SET_ACTION_BAR} is disallowed and
   * will throw an exception. Based on the action, it may write a {@link ComponentHolder}
   * or timing values for fade-in, stay, and fade-out durations.</p>
   *
   * @param buf the buffer to write to
   * @param direction the direction of the packet
   * @param version the protocol version used during encoding
   * @throws IllegalStateException if required data is missing or the action is unsupported
   */
  @Override
  public void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    if (version.lessThan(ProtocolVersion.MINECRAFT_1_11)
        && getAction() == ActionType.SET_ACTION_BAR) {
      throw new IllegalStateException("Action bars are only supported on 1.11 and newer");
    }

    ProtocolUtils.writeVarInt(buf, getAction().getAction(version));

    switch (getAction()) {
      case SET_TITLE, SET_SUBTITLE, SET_ACTION_BAR -> {
        if (component == null) {
          throw new IllegalStateException("No component found for " + getAction());
        }
        component.write(buf);
      }
      case SET_TIMES -> {
        buf.writeInt(fadeIn);
        buf.writeInt(stay);
        buf.writeInt(fadeOut);
      }
      case HIDE, RESET -> {
      }
      default -> throw new UnsupportedOperationException("Unknown action " + getAction());
    }
  }

  /**
   * Sets the action type for this legacy title packet.
   *
   * <p>This method delegates to {@link GenericTitlePacket#setAction(ActionType)}.</p>
   *
   * @param action the action type to set
   */
  @Override
  public void setAction(final ActionType action) {
    super.setAction(action);
  }

  /**
   * Returns the {@link ComponentHolder} used in this packet.
   *
   * <p>This component represents the text used for title, subtitle,
   * or action bar based on the {@link ActionType}.</p>
   *
   * @return the component used in this packet, or {@code null} if unset
   */
  @Override
  public @Nullable ComponentHolder getComponent() {
    return component;
  }

  /**
   * Sets the {@link ComponentHolder} to be used in this packet.
   *
   * @param component the component to assign; may be {@code null}
   */
  @Override
  public void setComponent(final @Nullable ComponentHolder component) {
    this.component = component;
  }

  /**
   * Gets the fade-in duration in ticks.
   *
   * <p>This value is only relevant when the action is {@link ActionType#SET_TIMES}.</p>
   *
   * @return the fade-in time in ticks
   */
  @Override
  public int getFadeIn() {
    return fadeIn;
  }

  /**
   * Sets the fade-in duration in ticks.
   *
   * <p>This value is only used for {@link ActionType#SET_TIMES}.</p>
   *
   * @param fadeIn the number of ticks to fade in the title
   */
  @Override
  public void setFadeIn(final int fadeIn) {
    this.fadeIn = fadeIn;
  }

  /**
   * Gets the duration in ticks for which the title remains on screen.
   *
   * <p>This value is only applicable for {@link ActionType#SET_TIMES}.</p>
   *
   * @return the stay time in ticks
   */
  @Override
  public int getStay() {
    return stay;
  }

  /**
   * Sets the duration in ticks for which the title remains on screen.
   *
   * <p>This value is only used for {@link ActionType#SET_TIMES}.</p>
   *
   * @param stay the number of ticks the title should stay
   */
  @Override
  public void setStay(final int stay) {
    this.stay = stay;
  }

  /**
   * Gets the fade-out duration in ticks.
   *
   * <p>This value is only used for {@link ActionType#SET_TIMES}.</p>
   *
   * @return the fade-out time in ticks
   */
  @Override
  public int getFadeOut() {
    return fadeOut;
  }

  /**
   * Sets the fade-out duration in ticks.
   *
   * <p>This value is only used for {@link ActionType#SET_TIMES}.</p>
   *
   * @param fadeOut the number of ticks to fade out the title
   */
  @Override
  public void setFadeOut(final int fadeOut) {
    this.fadeOut = fadeOut;
  }

  /**
   * Returns a string representation of this {@code LegacyTitlePacket}.
   *
   * <p>The output includes the current title action, component (if any), and
   * timing fields such as fade-in, stay, and fade-out durations.</p>
   *
   * @return a human-readable string describing this packet
   */
  @Override
  public String toString() {
    return "GenericTitlePacket{"
        + "action=" + getAction()
        + ", component='" + component + '\''
        + ", fadeIn=" + fadeIn
        + ", stay=" + stay
        + ", fadeOut=" + fadeOut
        + '}';
  }

  /**
   * Handles this {@code LegacyTitlePacket} by passing it to the provided session handler.
   *
   * <p>This delegates processing to {@link MinecraftSessionHandler#handle(LegacyTitlePacket)}.</p>
   *
   * @param handler the session handler responsible for handling the packet
   * @return {@code true} if handled successfully, otherwise {@code false}
   */
  @Override
  public boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
