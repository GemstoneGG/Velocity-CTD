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

import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class ConfigStateSnapshot {

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

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private final MessageDigest digest;
    private final List<String> entries = new ArrayList<>();

    private Builder() {
      try {
        this.digest = MessageDigest.getInstance("SHA-256");
      } catch (NoSuchAlgorithmException e) {
        throw new AssertionError("SHA-256 not available", e);
      }
    }

    /**
     * Doesn't consume the buffer.
     */
    public void addRegistrySync(ByteBuf content) {
      // Copies the readable bytes without advancing the reader index.
      byte[] data = ByteBufUtil.getBytes(content);
      update((byte) 'R', peekRegistryName(content), data);
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
        update((byte) 'T', "tags", ByteBufUtil.getBytes(buf));
      } finally {
        buf.release();
      }
    }

    private void update(byte tag, String name, byte[] data) {
      digest.update(tag);
      // Length-prefix so concatenation boundaries can't collide.
      digest.update((byte) (data.length >>> 24));
      digest.update((byte) (data.length >>> 16));
      digest.update((byte) (data.length >>> 8));
      digest.update((byte) data.length);
      digest.update(data);

      CRC32 crc = new CRC32();
      crc.update(data);
      entries.add((char) tag + ":" + name + " len=" + data.length + " crc=" + Long.toHexString(crc.getValue()));
    }

    private static String peekRegistryName(ByteBuf content) {
      try {
        return ProtocolUtils.readString(content.duplicate());
      } catch (Exception e) {
        return "?";
      }
    }

    public ConfigStateSnapshot build() {
      return new ConfigStateSnapshot(digest.digest(), List.copyOf(entries));
    }
  }
}
