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

package com.velocitypowered.proxy.protocol.netty;

import static com.velocitypowered.natives.util.MoreByteBufUtils.ensureCompatible;
import static com.velocitypowered.natives.util.MoreByteBufUtils.preferredBuffer;
import static com.velocitypowered.proxy.protocol.util.NettyPreconditions.checkFrame;

import com.velocitypowered.natives.compression.VelocityCompressor;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;

/**
 * Decompresses a Minecraft packet.
 */
public class MinecraftCompressDecoder extends MessageToMessageDecoder<ByteBuf> {

  /**
   * Default maximum allowed uncompressed packet size (8 MiB).
   */
  private static final int VANILLA_MAXIMUM_UNCOMPRESSED_SIZE = 8 * 1024 * 1024;

  /**
   * Hard upper limit for uncompressed size (128 MiB), used if override is enabled.
   */
  private static final int HARD_MAXIMUM_UNCOMPRESSED_SIZE = 128 * 1024 * 1024;

  /**
   * Maximum uncompressed size permitted during decompression.
   *
   * <p>Can be overridden with {@code -Dvelocity.increased-compression-cap=true} to allow up to 128 MiB.</p>
   */
  private static final int UNCOMPRESSED_CAP =
      Boolean.getBoolean("velocity.increased-compression-cap")
          ? HARD_MAXIMUM_UNCOMPRESSED_SIZE : VANILLA_MAXIMUM_UNCOMPRESSED_SIZE;

  /**
   * If {@code true}, disables strict threshold validation of uncompressed sizes.
   *
   * <p>Set via {@code -Dvelocity.skip-uncompressed-packet-size-validation=true}.</p>
   */
  private static final boolean SKIP_COMPRESSION_VALIDATION = Boolean.getBoolean("velocity.skip-uncompressed-packet-size-validation");

  /**
   * Compression threshold. Packets smaller than this are not compressed.
   */
  private int threshold;

  /**
   * The {@link VelocityCompressor} responsible for decompressing incoming packets.
   */
  private final VelocityCompressor compressor;

  /**
   * Constructs a new {@code MinecraftCompressDecoder}.
   *
   * @param threshold  the compression threshold (packets below this are not compressed)
   * @param compressor the compressor to use for decompression
   */
  public MinecraftCompressDecoder(final int threshold, final VelocityCompressor compressor) {
    this.threshold = threshold;
    this.compressor = compressor;
  }

  /**
   * Decompresses a compressed Minecraft packet using the configured {@link VelocityCompressor}.
   *
   * <p>If the incoming packet's uncompressed size is {@code 0}, it is treated as uncompressed and
   * forwarded directly (with optional validation). Otherwise, the decoder verifies that the declared
   * uncompressed size falls within acceptable limits and decompresses the payload.</p>
   *
   * <p>The uncompressed buffer is added to the output list. If decompression fails,
   * any allocated resources are released and the exception is propagated.</p>
   *
   * @param ctx the Netty channel context
   * @param in the compressed input buffer
   * @param out the list to which the decompressed output will be added
   * @throws Exception if decompression fails or validation fails
   */
  @Override
  protected void decode(final ChannelHandlerContext ctx, final ByteBuf in, final List<Object> out) throws Exception {
    int claimedUncompressedSize = ProtocolUtils.readVarInt(in);
    if (claimedUncompressedSize == 0) {
      if (!SKIP_COMPRESSION_VALIDATION) {
        int actualUncompressedSize = in.readableBytes();
        checkFrame(actualUncompressedSize < threshold, "Actual uncompressed size %s is greater than"
            + " threshold %s", actualUncompressedSize, threshold);
      }

      // This message is not compressed.
      out.add(in.retain());
      return;
    }

    checkFrame(claimedUncompressedSize >= threshold, "Uncompressed size %s is less than"
        + " threshold %s", claimedUncompressedSize, threshold);
    checkFrame(claimedUncompressedSize <= UNCOMPRESSED_CAP,
        "Uncompressed size %s exceeds hard threshold of %s", claimedUncompressedSize,
        UNCOMPRESSED_CAP);

    ByteBuf compatibleIn = ensureCompatible(ctx.alloc(), compressor, in);
    ByteBuf uncompressed = preferredBuffer(ctx.alloc(), compressor, claimedUncompressedSize);
    try {
      compressor.inflate(compatibleIn, uncompressed, claimedUncompressedSize);
      out.add(uncompressed);
    } catch (Exception e) {
      uncompressed.release();
      throw e;
    } finally {
      compatibleIn.release();
    }
  }

  /**
   * Called when this decoder is removed from the Netty pipeline.
   *
   * <p>This method closes the associated {@link VelocityCompressor} to release any native resources.</p>
   *
   * @param ctx the Netty channel context
   */
  @Override
  public void handlerRemoved(final ChannelHandlerContext ctx) {
    compressor.close();
  }

  /**
   * Updates the compression threshold.
   *
   * @param threshold the new compression threshold
   */
  public void setThreshold(final int threshold) {
    this.threshold = threshold;
  }
}
