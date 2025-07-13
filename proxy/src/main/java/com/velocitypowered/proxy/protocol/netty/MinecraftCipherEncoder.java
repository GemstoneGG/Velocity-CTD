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

  @Override
  protected final void encode(final ChannelHandlerContext ctx, final ByteBuf msg, final List<Object> out) throws Exception {
    ByteBuf compatible = MoreByteBufUtils.ensureCompatible(ctx.alloc(), cipher, msg);
    try {
      cipher.process(compatible);
      out.add(compatible);
    } catch (Exception e) {
      compatible.release(); // compatible will never be used if we throw an exception
      throw e;
    }
  }

  @Override
  public final void handlerRemoved(final ChannelHandlerContext ctx) {
    cipher.close();
  }
}
