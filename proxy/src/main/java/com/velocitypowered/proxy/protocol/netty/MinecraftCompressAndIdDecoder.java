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

package com.velocitypowered.proxy.protocol.netty;

import static com.velocitypowered.natives.util.MoreByteBufUtils.ensureCompatible;
import static com.velocitypowered.natives.util.MoreByteBufUtils.preferredBuffer;
import static com.velocitypowered.proxy.protocol.util.NettyPreconditions.checkFrame;

import com.velocitypowered.natives.compression.VelocityCompressor;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.netty.data.CompressedPacket;
import com.velocitypowered.proxy.protocol.netty.data.UncompressedPacket;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;

/**
 * Decompresses a Minecraft packet and decodes id.
 */
public class MinecraftCompressAndIdDecoder extends MessageToMessageDecoder<ByteBuf> {

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
   * Java-backed compressor used for partial inflation to read the packet id varInt.
   */
  private final VelocityCompressor javaCompressor;

  /**
   * Velocity server instance used for configuration lookups (e.g., decompression threshold).
   */
  private final VelocityServer server;

  /**
   * Constructs new Minecraft packet decompressor and id decoder.
   *
   * @param threshold compression threshold
   * @param compressor preferred compressor
   * @param javaCompressor Java-backed compressor for partial decompression
   * @param server the Velocity server instance
   */
  public MinecraftCompressAndIdDecoder(final int threshold, final VelocityCompressor compressor,
                                       final VelocityCompressor javaCompressor, final VelocityServer server) {
    this.threshold = threshold;
    this.compressor = compressor;
    this.javaCompressor = javaCompressor;
    this.server = server;
  }

  /**
   * Constructs a decoder with compression disabled.
   *
   * @param server the Velocity server instance
   */
  public MinecraftCompressAndIdDecoder(final VelocityServer server) {
    this(0, null, null, server);
  }

  /**
   * Decodes a compressed Minecraft packet using the configured compressors.
   *
   * <p>If the claimed uncompressed size is {@code 0} (or compression is disabled via
   * {@code threshold <= 0}), the frame is treated as uncompressed: the packet id is read
   * directly and the payload is wrapped as an {@link UncompressedPacket}. When
   * {@code -Dvelocity.skip-uncompressed-packet-size-validation=true} is not set, the actual
   * size is also validated against the threshold.</p>
   *
   * <p>Otherwise, the declared size is validated against configured limits. Frames below the
   * server’s decompression threshold are fully inflated and emitted as
   * {@link UncompressedPacket}; larger frames use partial inflation to read only the id and are
   * emitted as {@link CompressedPacket} retaining the original compressed bytes.</p>
   *
   * <p>If (partial) decompression fails, any allocated buffers are released and the exception
   * is propagated.</p>
   *
   * @param ctx the Netty channel context
   * @param in the input buffer
   * @param out the list to which the decoded output will be added
   * @throws Exception if validation or (partial) decompression fails
   */
  @Override
  protected void decode(final ChannelHandlerContext ctx, final ByteBuf in, final List<Object> out) throws Exception {
    if (threshold <= 0) {
      int originalReaderIndex = in.readerIndex();
      int packetId = ProtocolUtils.readVarInt(in);
      out.add(new UncompressedPacket(packetId, in.readerIndex(originalReaderIndex).retain()));
      return;
    }

    int claimedUncompressedSize = ProtocolUtils.readVarInt(in);
    if (claimedUncompressedSize == 0) {
      if (!SKIP_COMPRESSION_VALIDATION) {
        int actualUncompressedSize = in.readableBytes();
        checkFrame(actualUncompressedSize < threshold, "Actual uncompressed size %s is greater than"
            + " threshold %s", actualUncompressedSize, threshold);
      }

      int originalReaderIndex = in.readerIndex();
      int packetId = ProtocolUtils.readVarInt(in);
      out.add(new UncompressedPacket(packetId, in.readerIndex(originalReaderIndex).retain()));
      return;
    }

    checkFrame(claimedUncompressedSize >= threshold, "Uncompressed size %s is less than"
        + " threshold %s", claimedUncompressedSize, threshold);
    checkFrame(claimedUncompressedSize <= UNCOMPRESSED_CAP,
        "Uncompressed size %s exceeds hard threshold of %s", claimedUncompressedSize,
        UNCOMPRESSED_CAP);

    if (claimedUncompressedSize < server.getConfiguration().getDecompressionThreshold()) {
      ByteBuf compatibleIn = ensureCompatible(ctx.alloc(), compressor, in);
      ByteBuf uncompressed = preferredBuffer(ctx.alloc(), compressor, claimedUncompressedSize);
      try {
        compressor.inflate(compatibleIn, uncompressed, claimedUncompressedSize);
        int originalReaderIndex = uncompressed.readerIndex();
        int packetId = ProtocolUtils.readVarInt(uncompressed);
        out.add(new UncompressedPacket(packetId, uncompressed.readerIndex(originalReaderIndex)));
      } catch (Exception e) {
        uncompressed.release();
        throw e;
      } finally {
        compatibleIn.release();
      }
    } else {
      ByteBuf packetIdBuf = preferredBuffer(ctx.alloc(), this.javaCompressor, 5);
      int readerIndex = in.readerIndex();
      javaCompressor.inflatePartial(in, packetIdBuf, 5);
      in.readerIndex(readerIndex);
      int packetId = ProtocolUtils.readVarInt(packetIdBuf);
      packetIdBuf.release();

      out.add(new CompressedPacket(packetId, claimedUncompressedSize, in.retain(), this.compressor));
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
    if (compressor != null) {
      compressor.close();
    }

    if (javaCompressor != null) {
      javaCompressor.close();
    }
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
