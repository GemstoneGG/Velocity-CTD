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

package com.velocitypowered.proxy.protocol;

import static com.google.common.base.Preconditions.checkArgument;
import static com.velocitypowered.proxy.protocol.util.NettyPreconditions.checkFrame;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.crypto.IdentifiedKey;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.crypto.IdentifiedKeyImpl;
import com.velocitypowered.proxy.protocol.netty.MinecraftDecoder;
import com.velocitypowered.proxy.util.except.QuietDecoderException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.BinaryTagType;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.json.JSONOptions;
import net.kyori.adventure.text.serializer.json.legacyimpl.NBTLegacyHoverEventSerializer;
import net.kyori.option.OptionSchema;

/**
 * Utilities for writing and reading data in the Minecraft protocol.
 */
@SuppressWarnings("unchecked")
public enum ProtocolUtils {
  ;

  /**
   * JSON serializer for pre-1.16 clients.
   *
   * <p>This serializer disables RGB color output and uses legacy field names and hover/click event serialization
   * styles compatible with versions prior to Minecraft 1.16.</p>
   */
  private static final GsonComponentSerializer PRE_1_16_SERIALIZER =
      GsonComponentSerializer.builder()
          .downsampleColors()
          .legacyHoverEventSerializer(NBTLegacyHoverEventSerializer.get())
          .options(
              OptionSchema.globalSchema().stateBuilder()
              // general options
              .value(JSONOptions.EMIT_CLICK_URL_HTTPS, Boolean.TRUE)
              // before 1.16
              .value(JSONOptions.EMIT_RGB, Boolean.FALSE)
              .value(JSONOptions.EMIT_HOVER_EVENT_TYPE, JSONOptions.HoverEventValueMode.VALUE_FIELD)
              .value(JSONOptions.EMIT_CLICK_EVENT_TYPE, JSONOptions.ClickEventValueMode.CAMEL_CASE)
              // before 1.20.3
              .value(JSONOptions.EMIT_COMPACT_TEXT_COMPONENT, Boolean.FALSE)
              .value(JSONOptions.EMIT_HOVER_SHOW_ENTITY_ID_AS_INT_ARRAY, Boolean.FALSE)
              .value(JSONOptions.VALIDATE_STRICT_EVENTS, Boolean.FALSE)
              // before 1.21.5
              .value(JSONOptions.EMIT_CHANGE_PAGE_CLICK_EVENT_PAGE_AS_STRING, Boolean.TRUE)
              .build()
          )
          .build();

  /**
   * JSON serializer for clients using protocol versions from 1.16 up to (but not including) 1.20.3.
   *
   * <p>This serializer enables RGB output and uses modern hover/click event styles, while
   * maintaining compatibility with pre-1.20.3 expectations such as non-compact text and
   * looser validation.</p>
   */
  private static final GsonComponentSerializer PRE_1_20_3_SERIALIZER =
          GsonComponentSerializer.builder()
          .legacyHoverEventSerializer(NBTLegacyHoverEventSerializer.get())
          .options(
              OptionSchema.globalSchema().stateBuilder()
              // general options
              .value(JSONOptions.EMIT_CLICK_URL_HTTPS, Boolean.TRUE)
              // after 1.16
              .value(JSONOptions.EMIT_RGB, Boolean.TRUE)
              .value(JSONOptions.EMIT_HOVER_EVENT_TYPE, JSONOptions.HoverEventValueMode.CAMEL_CASE)
              .value(JSONOptions.EMIT_CLICK_EVENT_TYPE, JSONOptions.ClickEventValueMode.CAMEL_CASE)
              .value(JSONOptions.EMIT_HOVER_SHOW_ENTITY_KEY_AS_TYPE_AND_UUID_AS_ID, true)
              // before 1.20.3
              .value(JSONOptions.EMIT_COMPACT_TEXT_COMPONENT, Boolean.FALSE)
              .value(JSONOptions.EMIT_HOVER_SHOW_ENTITY_ID_AS_INT_ARRAY, Boolean.FALSE)
              .value(JSONOptions.VALIDATE_STRICT_EVENTS, Boolean.FALSE)
              // before 1.21.5
              .value(JSONOptions.EMIT_CHANGE_PAGE_CLICK_EVENT_PAGE_AS_STRING, Boolean.TRUE)
              .build()
          )
          .build();

  /**
   * JSON serializer for clients using protocol versions from 1.20.3 up to (but not including) 1.21.5.
   *
   * <p>This serializer outputs compact JSON components, strict hover event validation,
   * and entity identifiers in a modern UUID-based format. Used for improved
   * display accuracy and validation behavior on newer clients.</p>
   */
  private static final GsonComponentSerializer PRE_1_21_5_SERIALIZER =
      GsonComponentSerializer.builder()
          .legacyHoverEventSerializer(NBTLegacyHoverEventSerializer.get())
          .options(
              OptionSchema.globalSchema().stateBuilder()
              // general options
              .value(JSONOptions.EMIT_CLICK_URL_HTTPS, Boolean.TRUE)
              // after 1.16
              .value(JSONOptions.EMIT_RGB, Boolean.TRUE)
              .value(JSONOptions.EMIT_HOVER_EVENT_TYPE, JSONOptions.HoverEventValueMode.CAMEL_CASE)
              .value(JSONOptions.EMIT_CLICK_EVENT_TYPE, JSONOptions.ClickEventValueMode.CAMEL_CASE)
              .value(JSONOptions.EMIT_HOVER_SHOW_ENTITY_KEY_AS_TYPE_AND_UUID_AS_ID, true)
              // after 1.20.3
              .value(JSONOptions.EMIT_COMPACT_TEXT_COMPONENT, Boolean.TRUE)
              .value(JSONOptions.EMIT_HOVER_SHOW_ENTITY_ID_AS_INT_ARRAY, Boolean.TRUE)
              .value(JSONOptions.VALIDATE_STRICT_EVENTS, Boolean.TRUE)
              // before 1.21.5
              .value(JSONOptions.EMIT_CHANGE_PAGE_CLICK_EVENT_PAGE_AS_STRING, Boolean.TRUE)
              .build()
          )
          .build();

  /**
   * JSON serializer for clients using protocol versions 1.21.5 and above.
   *
   * <p>This serializer uses snake_case formatting for click/hover event types,
   * disables legacy entity hover serialization, and enables all strict validation
   * and formatting rules introduced in modern protocol versions.</p>
   */
  private static final GsonComponentSerializer MODERN_SERIALIZER =
      GsonComponentSerializer.builder()
          .legacyHoverEventSerializer(NBTLegacyHoverEventSerializer.get())
          .options(
              OptionSchema.globalSchema().stateBuilder()
              // general options
              .value(JSONOptions.EMIT_CLICK_URL_HTTPS, Boolean.TRUE)
              // after 1.16
              .value(JSONOptions.EMIT_RGB, Boolean.TRUE)
              .value(JSONOptions.EMIT_HOVER_EVENT_TYPE, JSONOptions.HoverEventValueMode.SNAKE_CASE)
              .value(JSONOptions.EMIT_CLICK_EVENT_TYPE, JSONOptions.ClickEventValueMode.SNAKE_CASE)
              // after 1.20.3
              .value(JSONOptions.EMIT_COMPACT_TEXT_COMPONENT, Boolean.TRUE)
              .value(JSONOptions.EMIT_HOVER_SHOW_ENTITY_ID_AS_INT_ARRAY, Boolean.TRUE)
              // after 1.21.5
              .value(JSONOptions.EMIT_HOVER_SHOW_ENTITY_KEY_AS_TYPE_AND_UUID_AS_ID, Boolean.FALSE)
              .value(JSONOptions.VALIDATE_STRICT_EVENTS, Boolean.TRUE)
              .value(JSONOptions.EMIT_CHANGE_PAGE_CLICK_EVENT_PAGE_AS_STRING, Boolean.FALSE)
              .build()
          )
          .build();

  /**
   * The default maximum allowed length for strings in Minecraft protocol messages.
   *
   * <p>This limit is set to 65,536 bytes (64 KiB) to match the protocol’s safe string bound.</p>
   */
  public static final int DEFAULT_MAX_STRING_SIZE = 65536;

  /**
   * The maximum number of bytes a VarInt may occupy in the Minecraft protocol.
   *
   * <p>Any VarInt taking more than 5 bytes is considered malformed.</p>
   */
  private static final int MAXIMUM_VARINT_SIZE = 5;

  /**
   * Table of all possible {@link BinaryTagType}s used for reading and writing NBT structures.
   *
   * <p>This array is indexed by tag ID and must match Mojang’s binary encoding layout.</p>
   */
  private static final BinaryTagType<? extends BinaryTag>[] BINARY_TAG_TYPES = new BinaryTagType[] {
      BinaryTagTypes.END, BinaryTagTypes.BYTE, BinaryTagTypes.SHORT, BinaryTagTypes.INT,
      BinaryTagTypes.LONG, BinaryTagTypes.FLOAT, BinaryTagTypes.DOUBLE,
      BinaryTagTypes.BYTE_ARRAY, BinaryTagTypes.STRING, BinaryTagTypes.LIST,
      BinaryTagTypes.COMPOUND, BinaryTagTypes.INT_ARRAY, BinaryTagTypes.LONG_ARRAY
  };

  /**
   * Cached decoder exception for malformed or oversized VarInts.
   *
   * <p>This is used in production to avoid the overhead of new exception construction
   * unless debug mode is enabled.</p>
   */
  private static final QuietDecoderException BAD_VARINT_CACHED =
      new QuietDecoderException("Bad VarInt decoded");

  /**
   * Lookup table mapping leading zero count to VarInt byte length.
   *
   * <p>This is used to determine how many bytes are needed to encode
   * a given integer as a Minecraft VarInt.</p>
   */
  private static final int[] VAR_INT_LENGTHS = new int[33];

  static {
    for (int i = 0; i <= 32; ++i) {
      VAR_INT_LENGTHS[i] = (int) Math.ceil((31d - (i - 1)) / 7d);
    }

    VAR_INT_LENGTHS[32] = 1; // Special case for the number 0.
  }

  private static DecoderException badVarint() {
    return MinecraftDecoder.DEBUG ? new CorruptedFrameException("Bad VarInt decoded")
        : BAD_VARINT_CACHED;
  }

  /**
   * Reads a Minecraft-style VarInt from the specified {@code buf}.
   *
   * @param buf the buffer to read from
   * @return the decoded VarInt
   */
  public static int readVarInt(final ByteBuf buf) {
    int readable = buf.readableBytes();
    if (readable == 0) {
      // special case for empty buffer
      throw badVarint();
    }

    // we can read at least one byte, and this should be a common case
    int k = buf.readByte();
    if ((k & 0x80) != 128) {
      return k;
    }

    // in case decoding one byte was not enough, use a loop to decode up to the next 4 bytes
    int maxRead = Math.min(MAXIMUM_VARINT_SIZE, readable);
    int i = k & 0x7F;
    for (int j = 1; j < maxRead; j++) {
      k = buf.readByte();
      i |= (k & 0x7F) << j * 7;
      if ((k & 0x80) != 128) {
        return i;
      }
    }

    throw badVarint();
  }

  /**
   * Returns the exact byte size of {@code value} if it were encoded as a VarInt.
   *
   * @param value the value to encode
   * @return the byte size of {@code value} if encoded as a VarInt
   */
  public static int varIntBytes(final int value) {
    return VAR_INT_LENGTHS[Integer.numberOfLeadingZeros(value)];
  }

  /**
   * Writes a Minecraft-style VarInt to the specified {@code buf}.
   *
   * @param buf   the buffer to read from
   * @param value the integer to write
   */
  public static void writeVarInt(final ByteBuf buf, final int value) {
    // Peel the one and two byte count cases explicitly as they are the most common VarInt sizes
    // that the proxy will write, to improve inlining.
    if ((value & (0xFFFFFFFF << 7)) == 0) {
      buf.writeByte(value);
    } else if ((value & (0xFFFFFFFF << 14)) == 0) {
      int w = (value & 0x7F | 0x80) << 8 | (value >>> 7);
      buf.writeShort(w);
    } else {
      writeVarIntFull(buf, value);
    }
  }

  private static void writeVarIntFull(final ByteBuf buf, final int value) {
    // See https://steinborn.me/posts/performance/how-fast-can-you-write-a-varint/

    // This essentially is an unrolled version of the "traditional" VarInt encoding.
    if ((value & (0xFFFFFFFF << 7)) == 0) {
      buf.writeByte(value);
    } else if ((value & (0xFFFFFFFF << 14)) == 0) {
      int w = (value & 0x7F | 0x80) << 8 | (value >>> 7);
      buf.writeShort(w);
    } else if ((value & (0xFFFFFFFF << 21)) == 0) {
      int w = (value & 0x7F | 0x80) << 16 | ((value >>> 7) & 0x7F | 0x80) << 8 | (value >>> 14);
      buf.writeMedium(w);
    } else if ((value & (0xFFFFFFFF << 28)) == 0) {
      int w = (value & 0x7F | 0x80) << 24 | (((value >>> 7) & 0x7F | 0x80) << 16)
          | ((value >>> 14) & 0x7F | 0x80) << 8 | (value >>> 21);
      buf.writeInt(w);
    } else {
      int w = (value & 0x7F | 0x80) << 24 | ((value >>> 7) & 0x7F | 0x80) << 16
          | ((value >>> 14) & 0x7F | 0x80) << 8 | ((value >>> 21) & 0x7F | 0x80);
      buf.writeInt(w);
      buf.writeByte(value >>> 28);
    }
  }

  /**
   * Directly encodes a 21-bit Minecraft VarInt, ready to be written with {@link ByteBuf#writeMedium(int)}.
   * The upper 11 bits will be discarded.
   *
   * @param value the value to encode
   * @return the encoded value
   */
  public static int encode21BitVarInt(final int value) {
    // See https://steinborn.me/posts/performance/how-fast-can-you-write-a-varint/
    return (value & 0x7F | 0x80) << 16 | ((value >>> 7) & 0x7F | 0x80) << 8 | (value >>> 14);
  }

  /**
   * Reads a VarInt-prefixed UTF-8 string from the given {@link ByteBuf}.
   *
   * <p>The string length is limited to {@link #DEFAULT_MAX_STRING_SIZE} characters.</p>
   *
   * @param buf the buffer to read from
   * @return the decoded string
   * @throws CorruptedFrameException if the string exceeds maximum allowed length or buffer limits
   */
  public static String readString(final ByteBuf buf) {
    return readString(buf, DEFAULT_MAX_STRING_SIZE);
  }

  /**
   * Reads a VarInt length-prefixed UTF-8 string from the {@code buf}, making sure to not go over
   * {@code cap} size.
   *
   * @param buf the buffer to read from
   * @param cap the maximum size of the string, in UTF-8 character length
   * @return the decoded string
   */
  public static String readString(final ByteBuf buf, final int cap) {
    int length = readVarInt(buf);
    return readString(buf, cap, length);
  }

  private static String readString(final ByteBuf buf, final int cap, final int length) {
    checkFrame(length >= 0, "Got a negative-length string (%s)", length);
    // `cap` is interpreted as a UTF-8 character length. To cover the full Unicode plane, we must
    // consider the length of a UTF-8 character, which can be up to 3 bytes. We do an initial
    // sanity check and then check again to make sure our optimistic guess was good.
    checkFrame(length <= cap * 3, "Bad string size (got %s, maximum is %s)", length, cap);
    checkFrame(buf.isReadable(length),
        "Trying to read a string that is too long (wanted %s, only have %s)", length,
        buf.readableBytes());
    String str = buf.readString(length, StandardCharsets.UTF_8);
    checkFrame(str.length() <= cap, "Got a too-long string (got %s, max %s)", str.length(), cap);
    return str;
  }

  /**
   * Determines the size of the written {@code str} if encoded as a VarInt-prefixed UTF-8 string.
   *
   * @param str the string to write
   * @return the encoded size
   */
  public static int stringSizeHint(final CharSequence str) {
    int size = ByteBufUtil.utf8Bytes(str);
    return varIntBytes(size) + size;
  }

  /**
   * Writes the specified {@code str} to the {@code buf} with a VarInt prefix.
   *
   * @param buf the buffer to write to
   * @param str the string to write
   */
  public static void writeString(final ByteBuf buf, final CharSequence str) {
    int size = ByteBufUtil.utf8Bytes(str);
    writeVarInt(buf, size);
    buf.writeCharSequence(str, StandardCharsets.UTF_8);
  }

  /**
   * Reads a standard Mojang Text namespaced:key from the buffer.
   *
   * @param buf the buffer to read from
   * @return the decoded key
   */
  public static Key readKey(final ByteBuf buf) {
    return Key.key(readString(buf), Key.DEFAULT_SEPARATOR);
  }

  /**
   * Writes a standard Mojang Text namespaced:key to the buffer.
   *
   * @param buf the buffer to write to
   * @param key the key to write
   */
  public static void writeKey(final ByteBuf buf, final Key key) {
    writeString(buf, key.asString());
  }

  /**
   * Writes the key to the buffer, dropping the "minecraft:" namespace when present.
   *
   * @param buf the buffer to write to
   * @param key the key to write
   */
  public static void writeMinimalKey(final ByteBuf buf, final Key key) {
    writeString(buf, key.asMinimalString());
  }

  /**
   * Reads a standard Mojang Text namespaced:key array from the buffer.
   *
   * @param buf the buffer to read from
   * @return the decoded key array
   */
  public static Key[] readKeyArray(final ByteBuf buf) {
    int length = readVarInt(buf);
    checkFrame(length >= 0, "Got a negative-length array (%s)", length);
    checkFrame(buf.isReadable(length),
        "Trying to read an array that is too long (wanted %s, only have %s)", length,
        buf.readableBytes());
    Key[] ret = new Key[length];

    for (int i = 0; i < ret.length; i++) {
      ret[i] = ProtocolUtils.readKey(buf);
    }

    return ret;
  }

  /**
   * Writes a standard Mojang Text namespaced:key array to the buffer.
   *
   * @param buf  the buffer to write to
   * @param keys the keys to write
   */
  public static void writeKeyArray(final ByteBuf buf, final Key[] keys) {
    writeVarInt(buf, keys.length);
    for (Key key : keys) {
      writeKey(buf, key);
    }
  }

  /**
   * Reads a VarInt-prefixed byte array from the given {@link ByteBuf}.
   *
   * <p>The byte array length is limited to {@link #DEFAULT_MAX_STRING_SIZE} bytes.</p>
   *
   * @param buf the buffer to read from
   * @return the decoded byte array
   * @throws CorruptedFrameException if the array length is invalid or exceeds buffer limits
   */
  public static byte[] readByteArray(final ByteBuf buf) {
    return readByteArray(buf, DEFAULT_MAX_STRING_SIZE);
  }

  /**
   * Reads a VarInt length-prefixed byte array from the {@code buf}, making sure to not go over
   * {@code cap} size.
   *
   * @param buf the buffer to read from
   * @param cap the maximum size of the string, in UTF-8 character length
   * @return the byte array
   */
  public static byte[] readByteArray(final ByteBuf buf, final int cap) {
    int length = readVarInt(buf);
    checkFrame(length >= 0, "Got a negative-length array (%s)", length);
    checkFrame(length <= cap, "Bad array size (got %s, maximum is %s)", length, cap);
    checkFrame(buf.isReadable(length),
        "Trying to read an array that is too long (wanted %s, only have %s)", length,
        buf.readableBytes());
    byte[] array = new byte[length];
    buf.readBytes(array);
    return array;
  }

  /**
   * Writes a VarInt-prefixed byte array to the given {@link ByteBuf}.
   *
   * @param buf the buffer to write to
   * @param array the byte array to write
   */
  public static void writeByteArray(final ByteBuf buf, final byte[] array) {
    writeVarInt(buf, array.length);
    buf.writeBytes(array);
  }

  /**
   * Reads an VarInt-prefixed array of VarInt integers from the {@code buf}.
   *
   * @param buf the buffer to read from
   * @return an array of integers
   */
  public static int[] readIntegerArray(final ByteBuf buf) {
    int len = readVarInt(buf);
    checkArgument(len >= 0, "Got a negative-length integer array (%s)", len);
    int[] array = new int[len];
    for (int i = 0; i < len; i++) {
      array[i] = readVarInt(buf);
    }

    return array;
  }

  /**
   * Reads a UUID from the {@code buf}.
   *
   * @param buf the buffer to read from
   * @return the UUID from the buffer
   */
  public static UUID readUuid(final ByteBuf buf) {
    long msb = buf.readLong();
    long lsb = buf.readLong();
    return new UUID(msb, lsb);
  }

  /**
   * Writes a {@link UUID} as two longs to the given {@link ByteBuf}.
   *
   * @param buf the buffer to write to
   * @param uuid the UUID to write
   */
  public static void writeUuid(final ByteBuf buf, final UUID uuid) {
    buf.writeLong(uuid.getMostSignificantBits());
    buf.writeLong(uuid.getLeastSignificantBits());
  }

  /**
   * Reads a UUID stored as an Integer Array from the {@code buf}.
   *
   * @param buf the buffer to read from
   * @return the UUID from the buffer
   */
  public static UUID readUuidIntArray(final ByteBuf buf) {
    long msbHigh = (long) buf.readInt() << 32;
    long msbLow = (long) buf.readInt() & 0xFFFFFFFFL;
    long msb = msbHigh | msbLow;
    long lsbHigh = (long) buf.readInt() << 32;
    long lsbLow = (long) buf.readInt() & 0xFFFFFFFFL;
    long lsb = lsbHigh | lsbLow;
    return new UUID(msb, lsb);
  }

  /**
   * Writes a UUID as an Integer Array to the {@code buf}.
   *
   * @param buf  the buffer to write to
   * @param uuid the UUID to write
   */
  public static void writeUuidIntArray(final ByteBuf buf, final UUID uuid) {
    buf.writeInt((int) (uuid.getMostSignificantBits() >> 32));
    buf.writeInt((int) uuid.getMostSignificantBits());
    buf.writeInt((int) (uuid.getLeastSignificantBits() >> 32));
    buf.writeInt((int) uuid.getLeastSignificantBits());
  }

  /**
   * Reads a {@link CompoundBinaryTag} from the given {@link ByteBuf}.
   *
   * @param buf the buffer to read from
   * @param version the protocol version used to determine tag parsing behavior
   * @param reader the {@link BinaryTagIO.Reader} used to parse the tag (maybe ignored depending on version)
   * @return the decoded {@link net.kyori.adventure.nbt.CompoundBinaryTag}
   * @throws DecoderException if the root tag is not a compound tag
   */
  public static CompoundBinaryTag readCompoundTag(final ByteBuf buf, final ProtocolVersion version,
                                                  final BinaryTagIO.Reader reader) {
    BinaryTag binaryTag = readBinaryTag(buf, version, reader);
    if (binaryTag.type() != BinaryTagTypes.COMPOUND) {
      throw new DecoderException(
          "Expected root tag to be CompoundTag, but is " + binaryTag.getClass().getSimpleName());
    }

    return (CompoundBinaryTag) binaryTag;
  }

  /**
   * Reads a {@link BinaryTag} from the given {@link ByteBuf}.
   *
   * @param buf the buffer to read from
   * @param version the protocol version used to determine parsing behavior
   * @param ignoredReader the {@link BinaryTagIO.Reader} instance (ignored in current implementation)
   * @return the decoded {@link net.kyori.adventure.nbt.BinaryTag}
   * @throws DecoderException if an I/O error occurs during tag parsing
   */
  public static BinaryTag readBinaryTag(final ByteBuf buf, final ProtocolVersion version,
                                        final BinaryTagIO.Reader ignoredReader) {
    BinaryTagType<?> type = BINARY_TAG_TYPES[buf.readByte()];
    if (version.lessThan(ProtocolVersion.MINECRAFT_1_20_2)) {
      buf.skipBytes(buf.readUnsignedShort());
    }

    try {
      return type.read(new ByteBufInputStream(buf));
    } catch (IOException thrown) {
      throw new DecoderException("Unable to parse BinaryTag, full error: " + thrown.getMessage());
    }
  }

  /**
   * Writes a {@link net.kyori.adventure.nbt.BinaryTag} to the {@code buf}.
   *
   * @param buf the buffer to write to
   * @param version the protocol version used to determine encoding behavior
   * @param tag the {@link BinaryTag} to encode and write
   * @param <T> the tag type
   * @throws EncoderException if encoding fails due to I/O errors
   */
  @SuppressWarnings("unchecked")
  public static <T extends BinaryTag> void writeBinaryTag(final ByteBuf buf, final ProtocolVersion version,
                                                          final T tag) {
    BinaryTagType<T> type = (BinaryTagType<T>) tag.type();
    buf.writeByte(type.id());
    try {
      if (version.lessThan(ProtocolVersion.MINECRAFT_1_20_2)) {
        // Empty name
        buf.writeShort(0);
      }

      type.write(tag, new ByteBufOutputStream(buf));
    } catch (IOException e) {
      throw new EncoderException("Unable to encode BinaryTag");
    }
  }

  /**
   * Reads a String array from the {@code buf}.
   *
   * @param buf the buffer to read from
   * @return the String array from the buffer
   */
  public static String[] readStringArray(final ByteBuf buf) {
    int length = readVarInt(buf);
    String[] ret = new String[length];
    for (int i = 0; i < length; i++) {
      ret[i] = readString(buf);
    }

    return ret;
  }

  /**
   * Writes a String Array to the {@code buf}.
   *
   * @param buf         the buffer to write to
   * @param stringArray the array to write
   */
  public static void writeStringArray(final ByteBuf buf, final String[] stringArray) {
    writeVarInt(buf, stringArray.length);
    for (String s : stringArray) {
      writeString(buf, s);
    }
  }

  /**
   * Reads an Integer array from the {@code buf}.
   *
   * @param buf the buffer to read from
   * @return the Integer array from the buffer
   */
  public static int[] readVarIntArray(final ByteBuf buf) {
    int length = readVarInt(buf);
    checkFrame(length >= 0, "Got a negative-length array (%s)", length);
    checkFrame(buf.isReadable(length),
        "Trying to read an array that is too long (wanted %s, only have %s)", length,
        buf.readableBytes());
    int[] ret = new int[length];
    for (int i = 0; i < length; i++) {
      ret[i] = readVarInt(buf);
    }

    return ret;
  }

  /**
   * Writes an Integer Array to the {@code buf}.
   *
   * @param buf      the buffer to write to
   * @param intArray the array to write
   */
  public static void writeVarIntArray(final ByteBuf buf, final int[] intArray) {
    writeVarInt(buf, intArray.length);
    for (int j : intArray) {
      writeVarInt(buf, j);
    }
  }

  /**
   * Writes a list of {@link com.velocitypowered.api.util.GameProfile.Property} to the buffer.
   *
   * @param buf        the buffer to write to
   * @param properties the properties to serialize
   */
  public static void writeProperties(final ByteBuf buf, final List<GameProfile.Property> properties) {
    writeVarInt(buf, properties.size());
    for (GameProfile.Property property : properties) {
      writeString(buf, property.getName());
      writeString(buf, property.getValue());
      String signature = property.getSignature();
      if (signature != null && !signature.isEmpty()) {
        buf.writeBoolean(true);
        writeString(buf, signature);
      } else {
        buf.writeBoolean(false);
      }
    }
  }

  /**
   * Reads a list of {@link com.velocitypowered.api.util.GameProfile.Property} from the buffer.
   *
   * @param buf the buffer to read from
   * @return the read properties
   */
  public static List<GameProfile.Property> readProperties(final ByteBuf buf) {
    List<GameProfile.Property> properties = new ArrayList<>();
    int size = readVarInt(buf);
    for (int i = 0; i < size; i++) {
      String name = readString(buf);
      String value = readString(buf);
      String signature = "";
      boolean hasSignature = buf.readBoolean();
      if (hasSignature) {
        signature = readString(buf);
      }

      properties.add(new GameProfile.Property(name, value, signature));
    }

    return properties;
  }

  /**
   * The maximum array length supported for Forge 1.7-style packets.
   *
   * <p>This limit is derived from the maximum allowed by Forge's 21-bit "extended short" encoding.</p>
   */
  private static final int FORGE_MAX_ARRAY_LENGTH = Integer.MAX_VALUE & 0x1FFF9A;

  /**
   * Reads a byte array for legacy version 1.7 from the specified {@code buf}.
   *
   * @param buf the buffer to read from
   * @return the read byte array
   */
  public static byte[] readByteArray17(final ByteBuf buf) {
    // Read in a 2 or 3 byte number that represents the length of the packet. (3 byte "shorts" for
    // Forge only)
    // No vanilla packet should give a 3-byte packet
    int len = readExtendedForgeShort(buf);

    checkArgument(len <= FORGE_MAX_ARRAY_LENGTH,
        "Cannot receive array longer than %s (got %s bytes)", FORGE_MAX_ARRAY_LENGTH, len);

    byte[] ret = new byte[len];
    buf.readBytes(ret);
    return ret;
  }

  /**
   * Reads a retained {@link ByteBuf} slice of the specified {@code buf} with the 1.7-style length.
   *
   * @param buf the buffer to read from
   * @return the retained slice
   */
  public static ByteBuf readRetainedByteBufSlice17(final ByteBuf buf) {
    // Read in a 2 or 3 byte number that represents the length of the packet. (3 byte "shorts" for
    // Forge only)
    // No vanilla packet should give a 3-byte packet
    int len = readExtendedForgeShort(buf);

    checkFrame(len <= FORGE_MAX_ARRAY_LENGTH,
        "Cannot receive array longer than %s (got %s bytes)", FORGE_MAX_ARRAY_LENGTH, len);

    return buf.readRetainedSlice(len);
  }

  /**
   * Writes a byte array for legacy version 1.7 to the specified {@code buf}.
   *
   * @param b             array
   * @param buf           buf
   * @param allowExtended forge
   */
  public static void writeByteArray17(final byte[] b, final ByteBuf buf, final boolean allowExtended) {
    if (allowExtended) {
      checkFrame(b.length <= FORGE_MAX_ARRAY_LENGTH,
          "Cannot send array longer than %s (got %s bytes)", FORGE_MAX_ARRAY_LENGTH,
          b.length);
    } else {
      checkFrame(b.length <= Short.MAX_VALUE,
          "Cannot send array longer than Short.MAX_VALUE (got %s bytes)", b.length);
    }

    // Write a 2 or 3 byte number that represents the length of the packet. (3 byte "shorts" for
    // Forge only)
    // No vanilla packet should give a 3-byte packet, this method will still retain vanilla
    // behavior.
    writeExtendedForgeShort(buf, b.length);
    buf.writeBytes(b);
  }

  /**
   * Writes an {@link ByteBuf} for legacy version 1.7 to the specified {@code buf}.
   *
   * @param b             array
   * @param buf           buf
   * @param allowExtended forge
   */
  public static void writeByteBuf17(final ByteBuf b, final ByteBuf buf, final boolean allowExtended) {
    if (allowExtended) {
      checkFrame(b.readableBytes() <= FORGE_MAX_ARRAY_LENGTH,
          "Cannot send array longer than %s (got %s bytes)", FORGE_MAX_ARRAY_LENGTH,
          b.readableBytes());
    } else {
      checkFrame(b.readableBytes() <= Short.MAX_VALUE,
          "Cannot send array longer than Short.MAX_VALUE (got %s bytes)", b.readableBytes());
    }

    // Write a 2 or 3 byte number that represents the length of the packet. (3 byte "shorts" for
    // Forge only)
    // No vanilla packet should give a 3-byte packet, this method will still retain vanilla
    // behavior.
    writeExtendedForgeShort(buf, b.readableBytes());
    buf.writeBytes(b);
  }

  /**
   * Reads a Minecraft-style extended short from the specified {@code buf}.
   *
   * @param buf buf to write
   * @return read extended short
   */
  public static int readExtendedForgeShort(final ByteBuf buf) {
    int low = buf.readUnsignedShort();
    int high = 0;
    if ((low & 0x8000) != 0) {
      low = low & 0x7FFF;
      high = buf.readUnsignedByte();
    }

    return ((high & 0xFF) << 15) | low;
  }

  /**
   * Writes a Minecraft-style extended short to the specified {@code buf}.
   *
   * @param buf     buf to write
   * @param toWrite the extended short to write
   */
  public static void writeExtendedForgeShort(final ByteBuf buf, final int toWrite) {
    int low = toWrite & 0x7FFF;
    int high = (toWrite & 0x7F8000) >> 15;
    if (high != 0) {
      low = low | 0x8000;
    }

    buf.writeShort(low);
    if (high != 0) {
      buf.writeByte(high);
    }
  }

  /**
   * Reads a non-length-prefixed string from the {@code buf}.
   * We need this for the legacy 1.7 version, being
   * inconsistent when sending the brand.
   *
   * @param buf the buffer to read from
   * @return the decoded string
   */
  public static String readStringWithoutLength(final ByteBuf buf) {
    return readString(buf, DEFAULT_MAX_STRING_SIZE, buf.readableBytes());
  }

  /**
   * Returns the appropriate {@link GsonComponentSerializer} for the given protocol version. This is
   * used to constrain messages sent to older clients.
   *
   * @param version the protocol version used by the client.
   * @return the appropriate {@link GsonComponentSerializer}
   */
  public static GsonComponentSerializer getJsonChatSerializer(final ProtocolVersion version) {
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_21_5)) {
      return MODERN_SERIALIZER;
    }

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_20_3)) {
      return PRE_1_21_5_SERIALIZER;
    }

    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_16)) {
      return PRE_1_20_3_SERIALIZER;
    }

    return PRE_1_16_SERIALIZER;
  }

  /**
   * Writes a players {@link IdentifiedKey} to the buffer.
   *
   * @param buf       the buffer
   * @param playerKey the key to write
   */
  public static void writePlayerKey(final ByteBuf buf, final IdentifiedKey playerKey) {
    buf.writeLong(playerKey.getExpiryTemporal().toEpochMilli());
    ProtocolUtils.writeByteArray(buf, playerKey.getSignedPublicKey().getEncoded());
    ProtocolUtils.writeByteArray(buf, Objects.requireNonNull(playerKey.getSignature()));
  }

  /**
   * Reads a players {@link IdentifiedKey} from the buffer.
   *
   * @param version the protocol version to determine key revision strategy
   * @param buf the buffer to read from
   * @return the decoded {@link IdentifiedKey}
   */
  public static IdentifiedKey readPlayerKey(final ProtocolVersion version, final ByteBuf buf) {
    long expiry = buf.readLong();
    byte[] key = ProtocolUtils.readByteArray(buf);
    byte[] signature = ProtocolUtils.readByteArray(buf, 4096);
    IdentifiedKey.Revision revision = version.noGreaterOrLessThan(ProtocolVersion.MINECRAFT_1_19)
        ? IdentifiedKey.Revision.GENERIC_V1 : IdentifiedKey.Revision.LINKED_V2;
    return new IdentifiedKeyImpl(revision, key, expiry, signature);
  }

  /**
   * Reads a {@link Sound.Source} from the buffer.
   *
   * @param buf the buffer
   * @param version the protocol version
   * @return the sound source
   */
  public static Sound.Source readSoundSource(final ByteBuf buf, final ProtocolVersion version) {
    int ordinal = readVarInt(buf);

    if (version.lessThan(ProtocolVersion.MINECRAFT_1_21_5)
        && ordinal == Sound.Source.UI.ordinal()) {
      throw new UnsupportedOperationException("UI sound-source is only supported in 1.21.5+");
    }

    return Sound.Source.values()[ordinal];
  }

  /**
   * Writes a {@link Sound.Source} to the buffer.
   *
   * @param buf the buffer
   * @param version the protocol version
   * @param source the sound source to write
   */
  public static void writeSoundSource(final ByteBuf buf, final ProtocolVersion version, final Sound.Source source) {
    if (version.lessThan(ProtocolVersion.MINECRAFT_1_21_5)
        && source == Sound.Source.UI) {
      throw new UnsupportedOperationException("UI sound-source is only supported in 1.21.5+");
    }

    writeVarInt(buf, source.ordinal());
  }

  /**
   * Represents the direction in which a packet flows.
   */
  public enum Direction {

    /**
     * Indicates that the packet is sent from the client to the server.
     */
    SERVERBOUND,

    /**
     * Indicates that the packet is sent from the server to the client.
     */
    CLIENTBOUND
  }
}
