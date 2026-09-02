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

package com.velocityctd.proxy.connection.fasttransition;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.BinaryTagType;
import net.kyori.adventure.nbt.ByteArrayBinaryTag;
import net.kyori.adventure.nbt.ByteBinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.DoubleBinaryTag;
import net.kyori.adventure.nbt.FloatBinaryTag;
import net.kyori.adventure.nbt.IntArrayBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.LongArrayBinaryTag;
import net.kyori.adventure.nbt.LongBinaryTag;
import net.kyori.adventure.nbt.ShortBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class ConfigStateSnapshot {

  private static final Logger LOGGER = LogManager.getLogger(ConfigStateSnapshot.class);

  /**
   * When set to a directory path, every registry-sync / tags payload fed into a labelled builder is
   * written there as {@code <dumpDir>/<label>/NN-<tag>-<name>.bin}, for byte-level diffing of what
   * each backend sends during configuration. Debug-only; unset in normal operation.
   */
  private static final @Nullable String DUMP_DIR = System.getProperty("velocityctd.fasttransition.dumpDir");

  private static final Set<String> IGNORED_REGISTRIES = Set.of(
      "minecraft:chat_type",
      "minecraft:test_environment",
      "minecraft:test_instance");

  /**
   * Whether tag data is excluded from the fingerprint. Tag sets (block/item/etc.) commonly differ
   * between server implementations and only affect client-side prediction, not world decoding.
   * Override with {@code -Dvelocityctd.fasttransition.ignoreTags=false}.
   */
  private static final boolean IGNORE_TAGS = Boolean.parseBoolean(System.getProperty(
      "velocityctd.fasttransition.ignoreTags", "true"));

  private final byte[] fingerprint;
  private final List<String> entries;

  private ConfigStateSnapshot(byte[] fingerprint, List<String> entries) {
    this.fingerprint = fingerprint;
    this.entries = entries;
  }

  public boolean matches(@Nullable ConfigStateSnapshot other) {
    return other != null && Arrays.equals(this.fingerprint, other.fingerprint);
  }

  public List<String> entries() {
    return entries;
  }

  public String fingerprintHex() {
    return ByteBufUtil.hexDump(fingerprint);
  }

  public static Builder builder() {
    return new Builder(null);
  }

  /**
   * Creates a builder that, when {@code -Dvelocityctd.fasttransition.dumpDir=...} is set, also writes
   * each captured payload to disk under the given label (e.g. the backend server name).
   */
  public static Builder builder(@Nullable String dumpLabel) {
    return new Builder(dumpLabel);
  }

  public static final class Builder {

    private final Map<String, byte[]> registryData = new LinkedHashMap<>();
    private byte @Nullable [] tagsData;
    private final @Nullable String dumpLabel;
    private int seq;

    private Builder(@Nullable String dumpLabel) {
      this.dumpLabel = dumpLabel;
    }

    /**
     * Doesn't consume the buffer.
     */
    public void addRegistrySync(ByteBuf content, ProtocolVersion version) {
      // Copies the readable bytes without advancing the reader index.
      byte[] raw = ByteBufUtil.getBytes(content);
      String name = peekRegistryName(content);
      // Fingerprint the canonical form (NBT compound keys are unordered, so different server
      // implementations serialize them in different orders while being fully compatible); dump the raw
      // bytes so the on-disk capture stays a faithful copy of the wire.
      registryData.put(name, canonicalize(content, version, raw));
      dumpPayload((byte) 'R', name, raw);
    }

    /**
     * Returns a canonicalized copy of a registry-sync payload in which every entry's NBT has been
     * re-serialized with compound keys sorted recursively. NBT compounds are unordered maps, so two
     * server implementations can emit the same registry with different key orderings while being fully
     * compatible; sorting removes that noise so equal registries fingerprint equal. Falls back to the
     * raw bytes for pre-1.20.5 formats or on any parse error.
     */
    private static byte[] canonicalize(ByteBuf content, ProtocolVersion version, byte[] raw) {
      if (version.lessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
        return raw;
      }
      ByteBuf in = content.duplicate();
      ByteBuf out = Unpooled.buffer(raw.length);
      try {
        ProtocolUtils.writeString(out, ProtocolUtils.readString(in));
        int count = ProtocolUtils.readVarInt(in);
        ProtocolUtils.writeVarInt(out, count);
        for (int i = 0; i < count; i++) {
          ProtocolUtils.writeString(out, ProtocolUtils.readString(in));
          boolean hasData = in.readBoolean();
          out.writeBoolean(hasData);
          if (hasData) {
            canonicalizeTag(ProtocolUtils.readBinaryTag(in, version, null), out);
          }
        }
        return ByteBufUtil.getBytes(out);
      } catch (Exception e) {
        LOGGER.debug("Failed to canonicalize registry-sync payload; fingerprinting raw bytes", e);
        return raw;
      } finally {
        out.release();
      }
    }

    /**
     * Writes {@code tag} to {@code out} in a deterministic form: compound keys are emitted in sorted
     * order and every value is written verbatim, so semantically equal NBT always produces identical
     * bytes regardless of the source's key ordering. This is a fingerprint form, not valid wire NBT.
     */
    private static void canonicalizeTag(BinaryTag tag, ByteBuf out) {
      BinaryTagType<?> type = tag.type();
      out.writeByte(type.id());
      if (tag instanceof CompoundBinaryTag compound) {
        List<String> keys = new ArrayList<>(compound.keySet());
        Collections.sort(keys);
        for (String childKey : keys) {
          writeUtf(out, childKey);
          canonicalizeTag(compound.get(childKey), out);
        }
        out.writeByte(0); // terminator so key lists of differing length can't collide
      } else if (tag instanceof ListBinaryTag list) {
        out.writeByte(list.elementType().id());
        out.writeInt(list.size());
        for (BinaryTag element : list) {
          canonicalizeTag(element, out);
        }
      } else if (tag instanceof ByteBinaryTag value) {
        out.writeByte(value.value());
      } else if (tag instanceof ShortBinaryTag value) {
        out.writeShort(value.value());
      } else if (tag instanceof IntBinaryTag value) {
        out.writeInt(value.value());
      } else if (tag instanceof LongBinaryTag value) {
        out.writeLong(value.value());
      } else if (tag instanceof FloatBinaryTag value) {
        out.writeFloat(value.value());
      } else if (tag instanceof DoubleBinaryTag value) {
        out.writeDouble(value.value());
      } else if (tag instanceof StringBinaryTag value) {
        writeUtf(out, value.value());
      } else if (tag instanceof ByteArrayBinaryTag value) {
        byte[] array = value.value();
        out.writeInt(array.length);
        out.writeBytes(array);
      } else if (tag instanceof IntArrayBinaryTag value) {
        int[] array = value.value();
        out.writeInt(array.length);
        for (int element : array) {
          out.writeInt(element);
        }
      } else if (tag instanceof LongArrayBinaryTag value) {
        long[] array = value.value();
        out.writeInt(array.length);
        for (long element : array) {
          out.writeLong(element);
        }
      } else {
        // EndBinaryTag and any unknown type carry no payload.
        if (type.id() != 0) {
          throw new IllegalStateException("Unhandled NBT tag type " + type.id());
        }
      }
    }

    private static void writeUtf(ByteBuf out, String value) {
      byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
      out.writeShort(bytes.length);
      out.writeBytes(bytes);
    }

    public void addTags(Map<String, Map<String, int[]>> tags) {
      ByteBuf buf = Unpooled.buffer();
      try {
        List<String> registries = new ArrayList<>(tags.keySet());
        Collections.sort(registries);
        for (String registry : registries) {
          buf.writeBytes(registry.getBytes(StandardCharsets.UTF_8));
          buf.writeByte(0);
          Map<String, int[]> inner = tags.get(registry);
          List<String> tagNames = new ArrayList<>(inner.keySet());
          Collections.sort(tagNames);
          for (String tagName : tagNames) {
            buf.writeBytes(tagName.getBytes(StandardCharsets.UTF_8));
            buf.writeByte(0);
            int[] entries = inner.get(tagName).clone();
            Arrays.sort(entries);
            for (int entry : entries) {
              buf.writeInt(entry);
            }
            buf.writeByte(1);
          }
        }
        tagsData = ByteBufUtil.getBytes(buf);
        dumpPayload((byte) 'T', "tags", tagsData);
      } finally {
        buf.release();
      }
    }

    private void dumpPayload(byte tag, String name, byte[] data) {
      if (DUMP_DIR == null || dumpLabel == null) {
        return;
      }
      String safeName = name.replaceAll("[^a-zA-Z0-9._-]", "_");
      String file = String.format(Locale.ROOT, "%02d-%c-%s.bin", seq++, (char) tag, safeName);
      try {
        Path dir = Path.of(DUMP_DIR, dumpLabel);
        Files.createDirectories(dir);
        Files.write(dir.resolve(file), data);
      } catch (IOException e) {
        LOGGER.warn("Failed to dump config payload {} for label {}", file, dumpLabel, e);
      }
    }

    private static void hashInto(MessageDigest digest, byte tag, byte[] data) {
      digest.update(tag);
      // Length-prefix so concatenation boundaries can't collide.
      digest.update((byte) (data.length >>> 24));
      digest.update((byte) (data.length >>> 16));
      digest.update((byte) (data.length >>> 8));
      digest.update((byte) data.length);
      digest.update(data);
    }

    private static String describe(byte tag, String name, byte[] data, boolean ignored) {
      CRC32 crc = new CRC32();
      crc.update(data);
      return (char) tag + ":" + name + " len=" + data.length
          + " crc=" + Long.toHexString(crc.getValue()) + (ignored ? "  [IGNORED]" : "");
    }

    private static String peekRegistryName(ByteBuf content) {
      try {
        return ProtocolUtils.readString(content.duplicate());
      } catch (Exception e) {
        return "?";
      }
    }

    public ConfigStateSnapshot build() {
      MessageDigest digest;
      try {
        digest = MessageDigest.getInstance("SHA-256");
      } catch (NoSuchAlgorithmException e) {
        throw new AssertionError("SHA-256 not available", e);
      }

      List<String> entries = new ArrayList<>();
      List<String> names = new ArrayList<>(registryData.keySet());
      Collections.sort(names); // order-insensitive fingerprint
      for (String name : names) {
        byte[] data = registryData.get(name);
        boolean ignored = IGNORED_REGISTRIES.contains(name);
        entries.add(describe((byte) 'R', name, data, ignored));
        if (!ignored) {
          hashInto(digest, (byte) 'R', data);
        }
      }
      if (tagsData != null) {
        entries.add(describe((byte) 'T', "tags", tagsData, IGNORE_TAGS));
        if (!IGNORE_TAGS) {
          hashInto(digest, (byte) 'T', tagsData);
        }
      }
      return new ConfigStateSnapshot(digest.digest(), List.copyOf(entries));
    }
  }
}
