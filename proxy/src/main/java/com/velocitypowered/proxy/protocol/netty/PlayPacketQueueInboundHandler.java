/*
 * Copyright (C) 2023 Velocity Contributors
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

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.DisconnectPacket;
import com.velocitypowered.proxy.protocol.packet.KeepAlivePacket;
import com.velocitypowered.proxy.util.except.QuietDecoderException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;

/**
 * Queues up any pending PLAY packets while the client is in the CONFIG state.
 *
 * <p>Much of the Velocity API (i.e., chat messages) utilize PLAY packets; however, the client is
 * incapable of receiving these packets during the CONFIG state. Certain events such as the
 * ServerPreConnectEvent may be called during this time, and we need to ensure that any API that
 * uses these packets will work as expected.
 *
 * <p>This handler will queue up any packets that are sent to the client during this time, and send
 * them once the client has (re)entered the PLAY state.
 */
public class PlayPacketQueueInboundHandler extends ChannelDuplexHandler {

  private static final int MAXIMUM_SIZE = Integer.getInteger("velocity.maximum-play-queue-size", 128 * 1024 * 1024); // 128MiB by default
  private static final QuietDecoderException QUEUE_LIMIT_FAILED = new QuietDecoderException(
      "Queue too big (greater than " + MAXIMUM_SIZE + " bytes)");

  private final Predicate<MinecraftPacket> passThrough;
  private final boolean discardStaleInbound;

  private final Queue<Object> queue = new ArrayDeque<>();
  private int queueSize = 0;

  /**
   * Provides registries for "client" &amp; server bound packets.
   *
   * @param version the protocol version
   * @param direction the direction of the packet flow (typically {@code SERVERBOUND})
   */
  public PlayPacketQueueInboundHandler(ProtocolVersion version, ProtocolUtils.Direction direction,
                                       boolean discardStaleInbound) {
    this(StateRegistry.CONFIG.getProtocolRegistry(direction, version)::containsPacket,
        discardStaleInbound);
  }

  private PlayPacketQueueInboundHandler(Predicate<MinecraftPacket> passThrough,
                                        boolean discardStaleInbound) {
    this.passThrough = passThrough;
    this.discardStaleInbound = discardStaleInbound;
  }

  /**
   * Creates a queue for a backend that has moved on to PLAY while its player is still being
   * configured. Everything the backend sends is PLAY and waits in order behind its JoinGame, even a
   * packet whose class CONFIG also has (a plugin message, a resource pack, tags): let through
   * early, it would reach the player out of order or be dropped before the JoinGame. Only a
   * keepalive, which the backend times the connection out over, and a disconnect, which ends it,
   * go ahead.
   *
   * @return the queue
   */
  public static PlayPacketQueueInboundHandler forBackendAheadOfPlayer() {
    return new PlayPacketQueueInboundHandler(
        packet -> packet instanceof KeepAlivePacket || packet instanceof DisconnectPacket, false);
  }

  @Override
  public void channelRead(@NotNull ChannelHandlerContext ctx, @NotNull Object msg) {
    if (msg instanceof MinecraftPacket packet) {
      // Packets this queue lets through (see the constructor and the factory) are handled by the
      // current handler right away
      if (this.passThrough.test(packet)) {
        ctx.fireChannelRead(msg);
        return;
      }
    }

    if (this.discardStaleInbound) {
      // Re-entering configuration: this play packet belongs to the previous play session and
      // must not be replayed into the next one, so drop it rather than queueing it.
      ReferenceCountUtil.release(msg);
      return;
    }

    int length = 0;
    if (msg instanceof ByteBuf) {
      // keep track of raw packets
      length = ((ByteBuf) msg).readableBytes();
    } else if (msg instanceof ByteBufHolder) {
      // keep track of bytebufs wrapped inside packets
      length = ((ByteBufHolder) msg).content().readableBytes();
    }
    if (this.queueSize + length > MAXIMUM_SIZE) {
      ReferenceCountUtil.release(msg);
      throw QUEUE_LIMIT_FAILED;
    }
    this.queueSize += length;

    // Otherwise, queue the packet
    this.queue.offer(msg);
  }

  @Override
  public void channelInactive(@NotNull ChannelHandlerContext ctx) throws Exception {
    this.releaseQueue(ctx, false);

    super.channelInactive(ctx);
  }

  @Override
  public void handlerRemoved(ChannelHandlerContext ctx) {
    this.releaseQueue(ctx, ctx.channel().isActive());
  }

  private void releaseQueue(ChannelHandlerContext ctx, boolean active) {
    // Handle all the queued packets
    Object msg;
    while ((msg = this.queue.poll()) != null) {
      if (active) {
        ctx.fireChannelRead(msg);
      } else {
        ReferenceCountUtil.release(msg);
      }
    }
    this.queueSize = 0;
  }
}
