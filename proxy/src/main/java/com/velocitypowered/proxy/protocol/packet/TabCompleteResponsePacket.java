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

package com.velocitypowered.proxy.protocol.packet;

import static com.velocitypowered.api.network.ProtocolVersion.MINECRAFT_1_13;

import com.google.common.base.MoreObjects;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents the packet used to send tab-completion suggestions to the client.
 */
public class TabCompleteResponsePacket implements MinecraftPacket {

  /**
   * The transaction ID of this tab-complete response.
   */
  private int transactionId;

  /**
   * The start index of the range being replaced in the client's input.
   */
  private int start;

  /**
   * The length of the range being replaced in the client's input.
   */
  private int length;

  /**
   * The list of offers (suggestions) sent to the client.
   */
  private final List<Offer> offers = new ArrayList<>();

  /**
   * Gets the transaction ID of this tab-complete response.
   *
   * @return the transaction ID
   */
  public int getTransactionId() {
    return transactionId;
  }

  /**
   * Sets the transaction ID for this tab-complete response.
   *
   * @param transactionId the transaction ID
   */
  public void setTransactionId(final int transactionId) {
    this.transactionId = transactionId;
  }

  /**
   * Gets the start index of the replacement range.
   *
   * @return the start index
   */
  public int getStart() {
    return start;
  }

  /**
   * Sets the start index of the replacement range.
   *
   * @param start the start index
   */
  public void setStart(final int start) {
    this.start = start;
  }

  /**
   * Gets the length of the replacement range.
   *
   * @return the length
   */
  public int getLength() {
    return length;
  }

  /**
   * Sets the length of the replacement range.
   *
   * @param length the length
   */
  public void setLength(final int length) {
    this.length = length;
  }

  /**
   * Gets the list of tab-complete offers sent to the client.
   *
   * @return the list of {@link Offer}s
   */
  public List<Offer> getOffers() {
    return offers;
  }

  @Override
  public final String toString() {
    return "TabCompleteResponse{"
        + "transactionId=" + transactionId
        + ", start=" + start
        + ", length=" + length
        + ", offers=" + offers
        + '}';
  }

  @Override
  public final void decode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    if (version.noLessThan(MINECRAFT_1_13)) {
      this.transactionId = ProtocolUtils.readVarInt(buf);
      this.start = ProtocolUtils.readVarInt(buf);
      this.length = ProtocolUtils.readVarInt(buf);
      int offersAvailable = ProtocolUtils.readVarInt(buf);
      for (int i = 0; i < offersAvailable; i++) {
        String offer = ProtocolUtils.readString(buf);
        ComponentHolder tooltip = buf.readBoolean() ? ComponentHolder.read(buf, version) : null;
        offers.add(new Offer(offer, tooltip));
      }
    } else {
      int offersAvailable = ProtocolUtils.readVarInt(buf);
      for (int i = 0; i < offersAvailable; i++) {
        offers.add(new Offer(ProtocolUtils.readString(buf), null));
      }
    }
  }

  @Override
  public final void encode(final ByteBuf buf, final ProtocolUtils.Direction direction, final ProtocolVersion version) {
    if (version.noLessThan(MINECRAFT_1_13)) {
      ProtocolUtils.writeVarInt(buf, this.transactionId);
      ProtocolUtils.writeVarInt(buf, this.start);
      ProtocolUtils.writeVarInt(buf, this.length);
      ProtocolUtils.writeVarInt(buf, offers.size());
      for (Offer offer : offers) {
        ProtocolUtils.writeString(buf, offer.text);
        buf.writeBoolean(offer.tooltip != null);
        if (offer.tooltip != null) {
          offer.tooltip.write(buf);
        }
      }
    } else {
      ProtocolUtils.writeVarInt(buf, offers.size());
      for (Offer offer : offers) {
        ProtocolUtils.writeString(buf, offer.text);
      }
    }
  }

  @Override
  public final boolean handle(final MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  /**
   * Represents an individual tab-completion suggestion (offer) sent to the client.
   */
  public static class Offer implements Comparable<Offer> {

    /**
     * The text of the suggestion.
     */
    private final String text;

    /**
     * An optional tooltip describing the suggestion.
     */
    private final @Nullable ComponentHolder tooltip;

    /**
     * Constructs a new {@code Offer} with the given suggestion text.
     *
     * @param text the suggestion text
     */
    public Offer(final String text) {
      this(text, null);
    }

    /**
     * Constructs a new {@code Offer} with the given suggestion text and tooltip.
     *
     * @param text the suggestion text
     * @param tooltip an optional tooltip component
     */
    public Offer(final String text, @Nullable final ComponentHolder tooltip) {
      this.text = text;
      this.tooltip = tooltip;
    }

    @Override
    public final boolean equals(final Object o) {
      if (this == o) {
        return true;
      }

      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      Offer offer = (Offer) o;

      return text.equals(offer.text);
    }

    @Override
    public final int hashCode() {
      return text.hashCode();
    }

    @Override
    public final String toString() {
      return MoreObjects.toStringHelper(this)
          .add("text", text)
          .add("tooltip", tooltip)
          .toString();
    }

    @Override
    public final int compareTo(final Offer o) {
      return this.text.compareTo(o.text);
    }

    /**
     * Gets the text of this suggestion.
     *
     * @return the suggestion text
     */
    public String getText() {
      return text;
    }
  }
}
