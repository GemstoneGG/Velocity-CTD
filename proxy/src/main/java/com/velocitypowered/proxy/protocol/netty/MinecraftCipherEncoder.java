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
import io.netty.handler.codec.MessageToMessageEncoder;
import java.util.List;

/**
 * Encrypts Minecraft protocol packets using {@link VelocityCipher}.
 */
public class MinecraftCipherEncoder extends MessageToMessageEncoder<ByteBuf> {

  /**
   * The {@link VelocityCipher} used to encrypt outgoing {@link ByteBuf} packets.
   *
   * <p>This cipher applies symmetric encryption and is closed when the handler is removed
   * from the Netty pipeline.</p>
   */
  private final VelocityCipher cipher;

  /**
   * Creates a new {@code MinecraftCipherEncoder} with the given {@link VelocityCipher}.
   *
   * @param cipher the cipher to use for encrypting outbound packets
   * @throws NullPointerException if {@code cipher} is {@code null}
   */
  public MinecraftCipherEncoder(final VelocityCipher cipher) {
    this.cipher = Preconditions.checkNotNull(cipher, "cipher");
  }

  /**
   * Encrypts the given Minecraft {@link ByteBuf} using the configured {@link VelocityCipher}.
   *
   * <p>This method ensures the input buffer is compatible with native cipher operations,
   * then encrypts the contents in-place. The encrypted buffer is added to the output list.</p>
   *
   * <p>If encryption fails, the buffer is released and the exception is propagated.</p>
   *
   * @param ctx the Netty channel context
   * @param msg the outgoing unencrypted Minecraft packet
   * @param out the list to which the encrypted buffer is added
   * @throws Exception if an error occurs during encryption
   */
  @Override
  protected void encode(final ChannelHandlerContext ctx, final ByteBuf msg, final List<Object> out) throws Exception {
    ByteBuf compatible = MoreByteBufUtils.ensureCompatible(ctx.alloc(), cipher, msg);
    try {
      cipher.process(compatible);
      out.add(compatible);
    } catch (Exception e) {
      compatible.release(); // compatible will never be used if we throw an exception
      throw e;
    }
  }

  /**
   * Called when the encoder is removed from the Netty pipeline.
   *
   * <p>This ensures that the associated {@link VelocityCipher} is properly closed and
   * any native resources are released.</p>
   *
   * @param ctx the Netty channel context
   */
  @Override
  public void handlerRemoved(final ChannelHandlerContext ctx) {
    cipher.close();
  }
}
