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

package com.velocitypowered.proxy.network;

/**
 * Constants used for the pipeline.
 */
public final class Connections {

  /**
   * Name for the cipher decoder in the pipeline.
   */
  public static final String CIPHER_DECODER = "cipher-decoder";

  /**
   * Name for the cipher encoder in the pipeline.
   */
  public static final String CIPHER_ENCODER = "cipher-encoder";

  /**
   * Name for the compression decoder in the pipeline.
   */
  public static final String COMPRESSION_DECODER = "compression-decoder";

  /**
   * Name for the compression encoder in the pipeline.
   */
  public static final String COMPRESSION_ENCODER = "compression-encoder";

  /**
   * Name for the flow-control handler in the pipeline.
   */
  public static final String FLOW_HANDLER = "flow-handler";

  /**
   * Name for the VarInt frame decoder in the pipeline.
   */
  public static final String FRAME_DECODER = "frame-decoder";

  /**
   * Name for the VarInt frame encoder in the pipeline.
   */
  public static final String FRAME_ENCODER = "frame-encoder";

  /**
   * Name for the primary Minecraft handler responsible for managing session state.
   */
  public static final String HANDLER = "handler";

  /**
   * Name for the decoder handling legacy server ping requests.
   */
  public static final String LEGACY_PING_DECODER = "legacy-ping-decoder";

  /**
   * Name for the encoder handling legacy server ping responses.
   */
  public static final String LEGACY_PING_ENCODER = "legacy-ping-encoder";

  /**
   * Name for the Minecraft protocol decoder.
   */
  public static final String MINECRAFT_DECODER = "minecraft-decoder";

  /**
   * Name for the Minecraft protocol encoder.
   */
  public static final String MINECRAFT_ENCODER = "minecraft-encoder";

  /**
   * Name for the read timeout handler.
   */
  public static final String READ_TIMEOUT = "read-timeout";

  /**
   * Name for the outbound packet queue used during the play state.
   */
  public static final String PLAY_PACKET_QUEUE_OUTBOUND = "play-packet-queue-outbound";

  /**
   * Name for the inbound packet queue used during the play state.
   */
  public static final String PLAY_PACKET_QUEUE_INBOUND = "play-packet-queue-inbound";

  private Connections() {
    throw new AssertionError();
  }
}
