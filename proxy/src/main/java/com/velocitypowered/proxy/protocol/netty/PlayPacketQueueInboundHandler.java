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

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
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
public class PlayPacketQueueInboundHandler extends ChannelDuplexHandler {

  /**
   * The packet registry for the CONFIG state, used to detect which packets must bypass queuing.
   */
  private final StateRegistry.PacketRegistry.ProtocolRegistry registry;

  /**
   * Internal queue of messages waiting to be forwarded once the PLAY state is reached.
   */
  private final Queue<Object> queue = new ArrayDeque<>();

  /**
   * Provides registries for "client" &amp; server bound packets.
   *
   * @param version the protocol version
   * @param direction the direction of the packet flow (typically {@code SERVERBOUND})
   */
  public PlayPacketQueueInboundHandler(final ProtocolVersion version, final ProtocolUtils.Direction direction) {
    this.registry = StateRegistry.CONFIG.getProtocolRegistry(direction, version);
  }

  /**
   * Intercepts incoming packets and conditionally queues them based on the current protocol state.
   *
   * <p>If the packet is part of the {@code CONFIG} state, it is immediately passed through.
   * Otherwise, it is queued and deferred until the channel transitions to the {@code PLAY} state.</p>
   *
   * @param ctx the Netty channel context
   * @param msg the incoming message (typically a {@link MinecraftPacket})
   */
  @Override
  public void channelRead(final @NotNull ChannelHandlerContext ctx, final @NotNull Object msg) {
    if (msg instanceof final MinecraftPacket packet) {
      // If the packet exists in the CONFIG state, we want to always
      // ensure that it gets handled by the current handler
      if (this.registry.containsPacket(packet)) {
        ctx.fireChannelRead(msg);
        return;
      }
    }

    // Otherwise, queue the packet
    this.queue.offer(msg);
  }

  /**
   * Invoked when the channel becomes inactive.
   *
   * <p>This method clears and releases all queued packets, as the connection
   * will no longer reach the {@code PLAY} state.</p>
   *
   * @param ctx the Netty channel context
   * @throws Exception if an error occurs during release
   */
  @Override
  public void channelInactive(final @NotNull ChannelHandlerContext ctx) throws Exception {
    this.releaseQueue(ctx, false);

    super.channelInactive(ctx);
  }

  /**
   * Called when this handler is removed from the pipeline.
   *
   * <p>Flushes all queued packets. If the channel is still active,
   * they are forwarded downstream. Otherwise, their buffers are released.</p>
   *
   * @param ctx the Netty channel context
   */
  @Override
  public void handlerRemoved(final ChannelHandlerContext ctx) {
    this.releaseQueue(ctx, ctx.channel().isActive());
  }

  private void releaseQueue(final ChannelHandlerContext ctx, final boolean active) {
    // Handle all the queued packets
    Object msg;
    while ((msg = this.queue.poll()) != null) {
      if (active) {
        ctx.fireChannelRead(msg);
      } else {
        ReferenceCountUtil.release(msg);
      }
    }
  }
}
