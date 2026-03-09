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

package com.velocitypowered.proxy.protocol.packet.chat;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.BitSet;

/**
 * Represents a collection of the last seen messages by a player or client.
 * This class tracks the recent chat messages that the player has viewed.
 */
public class LastSeenMessages {

  /**
   * The number of messages in the sliding acknowledgment window.
   */
  public static final int WINDOW_SIZE = 20;

  /**
   * The number of bytes needed to store a WINDOW_SIZE-sized BitSet.
   */
  private static final int DIV_FLOOR = -Math.floorDiv(-WINDOW_SIZE, 8);

  /**
   * The base offset of the message window, relative to the message history index.
   */
  private final int offset;

  /**
   * A {@link BitSet} indicating which messages in the window were seen.
   */
  private final BitSet acknowledged;

  /**
   * A one-byte checksum included in protocol versions 1.21.5 and later.
   */
  private byte checksum;

  /**
   * Constructs an empty {@link LastSeenMessages} with offset 0 and an empty acknowledgment set.
   */
  public LastSeenMessages() {
    this(0, new BitSet(), (byte) 0);
  }

  /**
   * Creates a new {@link LastSeenMessages} instance with the specified offset, acknowledged messages, and checksum.
   *
   * @param offset the starting index of the message window
   * @param acknowledged a BitSet representing which messages have been acknowledged
   * @param checksum the checksum for the message window data
   */
  public LastSeenMessages(final int offset, final BitSet acknowledged, final byte checksum) {
    this.offset = offset;
    this.acknowledged = acknowledged;
    this.checksum = checksum;
  }

  /**
   * Constructs a new {@link LastSeenMessages} instance by decoding data from the provided
   * {@link ByteBuf}.
   *
   * @param buf the buffer containing the serialized last seen messages data
   * @param protocolVersion the protocol version (determines if checksum is written)
   */
  public LastSeenMessages(final ByteBuf buf, final ProtocolVersion protocolVersion) {
    this.offset = ProtocolUtils.readVarInt(buf);

    byte[] bytes = new byte[DIV_FLOOR];
    buf.readBytes(bytes);
    this.acknowledged = BitSet.valueOf(bytes);

    if (protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_21_5)) {
      this.checksum = buf.readByte();
    }
  }

  /**
   * Encodes this {@link LastSeenMessages} instance into the provided {@link ByteBuf}.
   *
   * @param buf the buffer to write the data to
   * @param protocolVersion the protocol version used for encoding
   */
  public void encode(final ByteBuf buf, final ProtocolVersion protocolVersion) {
    ProtocolUtils.writeVarInt(buf, offset);
    buf.writeBytes(Arrays.copyOf(acknowledged.toByteArray(), DIV_FLOOR));
    if (protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_21_5)) {
      buf.writeByte(this.checksum);
    }
  }

  /**
   * Gets the current offset of the message tracking window.
   *
   * @return the offset value
   */
  public int getOffset() {
    return this.offset;
  }

  /**
   * Gets the {@link BitSet} of messages acknowledged in this window.
   *
   * @return the bitset of seen messages
   */
  public BitSet getAcknowledged() {
    return acknowledged;
  }

  /**
   * Creates a new {@link LastSeenMessages} instance with an adjusted offset.
   *
   * <p>The returned instance shares the same acknowledgment and checksum state,
   * but its offset is incremented by the specified amount.</p>
   *
   * @param offset the amount to shift the offset by
   * @return a new {@code LastSeenMessages} instance with updated offset
   */
  public LastSeenMessages offset(final int offset) {
    return new LastSeenMessages(this.offset + offset, acknowledged, checksum);
  }

  /**
   * Returns a string representation of this {@code LastSeenMessages} instance.
   *
   * <p>The output includes the current offset, acknowledged messages {@link BitSet},
   * and checksum value used for integrity verification in newer protocol versions.</p>
   *
   * @return a human-readable string describing this instance
   */
  @Override
  public String toString() {
    return "LastSeenMessages{"
        + "offset=" + offset
        + ", acknowledged=" + acknowledged
        + ", checksum=" + checksum
        + '}';
  }
}
