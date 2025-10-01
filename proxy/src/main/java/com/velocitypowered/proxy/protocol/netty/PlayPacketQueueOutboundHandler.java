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

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import java.util.ArrayDeque;
import java.util.Queue;
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
public class PlayPacketQueueOutboundHandler extends ChannelDuplexHandler {

  /**
   * The CONFIG-state protocol registry used to determine which packets are safe to send early.
   */
  private final StateRegistry.PacketRegistry.ProtocolRegistry registry;

  /**
   * The queue of outbound packets to be released once the PLAY state is reached.
   */
  private final Queue<MinecraftPacket> queue = new ArrayDeque<>();

  /**
   * Provides registries for "client" &amp; server bound packets.
   *
   * @param version the protocol version
   * @param direction the direction of packet flow (typically {@code CLIENTBOUND})
   */
  public PlayPacketQueueOutboundHandler(final ProtocolVersion version, final ProtocolUtils.Direction direction) {
    this.registry = StateRegistry.CONFIG.getProtocolRegistry(direction, version);
  }

  /**
   * Intercepts outbound {@link MinecraftPacket}s during the CONFIG protocol state.
   *
   * <p>If the packet type is valid for the CONFIG state, it is written immediately.
   * Otherwise, it is queued until the channel enters the PLAY state.</p>
   *
   * @param ctx the Netty channel context
   * @param msg the outbound message
   * @param promise the write promise for asynchronous completion
   * @throws Exception if an I/O error occurs
   */
  @Override
  public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) throws Exception {
    if (!(msg instanceof final MinecraftPacket packet)) {
      ctx.write(msg, promise);
      return;
    }

    // If the packet exists in the CONFIG state, we want to always
    // ensure that it gets sent out to the client
    if (this.registry.containsPacket(packet)) {
      ctx.write(msg, promise);
      return;
    }

    // Otherwise, queue the packet
    this.queue.offer(packet);
  }

  /**
   * Invoked when the channel becomes inactive.
   *
   * <p>This clears and releases all queued packets since they can no longer be delivered.</p>
   *
   * @param ctx the Netty channel context
   * @throws Exception if an error occurs during cleanup
   */
  @Override
  public void channelInactive(final @NotNull ChannelHandlerContext ctx) throws Exception {
    this.releaseQueue(ctx, false);

    super.channelInactive(ctx);
  }

  /**
   * Called when this handler is removed from the pipeline.
   *
   * <p>If the channel is still active, all queued packets are flushed downstream.
   * Otherwise, queued packets are released to prevent memory leaks.</p>
   *
   * @param ctx the Netty channel context
   */
  @Override
  public void handlerRemoved(final ChannelHandlerContext ctx) {
    this.releaseQueue(ctx, ctx.channel().isActive());
  }

  private void releaseQueue(final ChannelHandlerContext ctx, final boolean active) {
    // Send out all the queued packets
    MinecraftPacket packet;
    while ((packet = this.queue.poll()) != null) {
      if (active) {
        ctx.write(packet, ctx.voidPromise());
      } else {
        ReferenceCountUtil.release(packet);
      }
    }

    if (active) {
      ctx.flush();
    }
  }
}
