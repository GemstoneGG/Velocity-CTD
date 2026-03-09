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

import com.google.common.base.Preconditions;
import com.velocitypowered.natives.encryption.VelocityCipher;
import com.velocitypowered.natives.util.MoreByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;

/**
 * Handler for decrypting Minecraft packets.
 */
public class MinecraftCipherDecoder extends MessageToMessageDecoder<ByteBuf> {

  /**
   * The {@link VelocityCipher} used to decrypt incoming {@link ByteBuf} packets.
   *
   * <p>This cipher performs symmetric decryption and is closed when the handler is removed
   * from the Netty pipeline.</p>
   */
  private final VelocityCipher cipher;

  /**
   * Constructs a new {@code MinecraftCipherDecoder} using the specified {@link VelocityCipher}.
   *
   * @param cipher the cipher to use for decrypting incoming packets
   * @throws NullPointerException if {@code cipher} is {@code null}
   */
  public MinecraftCipherDecoder(final VelocityCipher cipher) {
    this.cipher = Preconditions.checkNotNull(cipher, "cipher");
  }

  /**
   * Decrypts an incoming Minecraft {@link ByteBuf} using the configured {@link VelocityCipher}.
   *
   * <p>This method ensures the buffer is compatible with the cipher (e.g. direct buffer),
   * then applies decryption in-place. The decrypted buffer is added to the output list.</p>
   *
   * <p>If an exception occurs during decryption, the buffer is released and the exception is rethrown.</p>
   *
   * @param ctx the Netty channel context
   * @param in the encrypted incoming packet buffer
   * @param out the list to which the decrypted packet should be added
   * @throws Exception if decryption fails
   */
  @Override
  protected void decode(final ChannelHandlerContext ctx, final ByteBuf in, final List<Object> out) throws Exception {
    ByteBuf compatible = MoreByteBufUtils.ensureCompatible(ctx.alloc(), cipher, in).slice();
    try {
      cipher.process(compatible);
      out.add(compatible);
    } catch (Exception e) {
      compatible.release(); // compatible will never be used if we throw an exception
      throw e;
    }
  }

  /**
   * Invoked when the decoder is removed from the Netty pipeline.
   *
   * <p>This method ensures that the underlying {@link VelocityCipher} is properly closed
   * and any associated native resources are released.</p>
   *
   * @param ctx the Netty channel context
   */
  @Override
  public void handlerRemoved(final ChannelHandlerContext ctx) {
    cipher.close();
  }
}
