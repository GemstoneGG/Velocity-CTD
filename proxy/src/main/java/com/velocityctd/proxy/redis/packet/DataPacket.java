/*
 * Copyright (C) 2018-2026 Velocity-CTD Contributors
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

package com.velocityctd.proxy.redis.packet;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The single envelope type for all Redis communication.
 *
 * <p>A {@code DataPacket} wraps any GSON-serializable payload alongside its
 * fully qualified class name, allowing type-safe deserialization on the
 * receiving end without needing concrete packet subclasses per data type.</p>
 *
 * <p>The packet carries transport metadata (packet ID, transaction info,
 * reply flag) used by the Redis provider for routing and correlation.</p>
 */
public final class DataPacket {

  /**
   * A unique internal identifier automatically assigned to this packet.
   * Used for equality and tracking packet flow across the Redis pipeline.
   */
  private final UUID packetId;

  /**
   * The GSON-serialized JSON representation of the payload.
   */
  private final String payload;

  /**
   * The fully qualified class name of the payload type,
   * used for deserialization and routing.
   */
  private final String payloadType;

  /**
   * Indicates whether this packet instance represents a reply to another packet.
   */
  private boolean reply;

  /**
   * The transaction identifier associated with this packet, if any.
   * Present when a packet participates in a request–reply transaction.
   * If {@code null}, the packet is treated as one-way.
   */
  private @MonotonicNonNull UUID transactionId;

  /**
   * The deserialized payload object, cached after first access.
   */
  private transient Object rawPayload;

  /**
   * Constructs a new {@link DataPacket} by serializing the given payload.
   *
   * @param serializedPayload the serialized payload
   * @param rawPayload the raw payload
   * @param <T> the type of the payload
   */
  private <T> DataPacket(@NotNull String serializedPayload, @NotNull T rawPayload) {
    this.packetId = UuidCreator.getTimeOrderedEpochFast();
    this.payload = serializedPayload;
    this.payloadType = rawPayload.getClass().getName();
    this.rawPayload = rawPayload;
  }

  /**
   * Creates a new {@link DataPacket} by serializing the given payload.
   *
   * @param payload the payload to serialize
   * @param serializer the serializer to use for JSON conversion
   * @param <T> the type of the payload
   * @return a new data packet
   */
  public static <T> DataPacket of(@NotNull T payload, @NotNull PacketSerializer serializer) {
    return new DataPacket(serializer.serializePayload(payload), payload);
  }

  /**
   * Gets the unique internal identifier assigned to this packet.
   *
   * @return the packet's unique identifier
   */
  public UUID getId() {
    return packetId;
  }

  /**
   * Gets the fully qualified class name of the payload type.
   *
   * @return the payload type string
   */
  public String getPayloadType() {
    return payloadType;
  }

  /**
   * Determines whether this packet is one-way (not part of a transaction).
   *
   * @return {@code true} if this packet has no transaction ID
   */
  public boolean isOneWay() {
    return transactionId == null;
  }

  /**
   * Checks whether this packet represents a reply to another packet.
   *
   * @return {@code true} if this packet is a reply
   */
  public boolean isReply() {
    return reply;
  }

  /**
   * Marks whether this packet is a reply to another packet.
   *
   * @param reply {@code true} if this packet represents a reply
   */
  public void setReply(boolean reply) {
    this.reply = reply;
  }

  /**
   * Gets the transaction identifier associated with this packet, if present.
   *
   * @return the transaction ID, or {@code null} if this is a one-way packet
   */
  public @Nullable UUID getTransactionId() {
    return this.transactionId;
  }

  /**
   * Sets the transaction identifier for this packet.
   *
   * @param transactionId the transaction ID to assign
   */
  public void setTransactionId(@NotNull UUID transactionId) {
    this.transactionId = transactionId;
  }

  /**
   * Deserializes and returns the payload using the given serializer.
   *
   * <p>The caller passes the expected {@link Class} so the return is statically typed and
   * the runtime cast is performed via {@link Class#cast}, which throws {@link ClassCastException}
   * if the payload's actual type does not match.</p>
   *
   * @param serializer   the serializer to use for deserialization
   * @param expectedType the expected payload class
   * @param <T>          the target type
   * @return the deserialized payload
   * @throws IllegalArgumentException if {@code expectedType} does not match this packet's
   *                                  declared {@link #getPayloadType() payload type}
   */
  public <T> T getPayload(@NotNull PacketSerializer serializer, @NotNull Class<T> expectedType) {
    if (!payloadType.equals(expectedType.getName())) {
      throw new IllegalArgumentException(
          "Expected payload type %s but packet contains %s"
              .formatted(expectedType.getName(), payloadType));
    }

    if (rawPayload == null) {
      rawPayload = serializer.deserializePayload(payload, expectedType);
    }

    try {
      return expectedType.cast(rawPayload);
    } catch (ClassCastException e) {
      throw new IllegalStateException(
          "Deserialized payload is inconsistent with declared type %s"
              .formatted(payloadType), e);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    DataPacket that = (DataPacket) o;
    return Objects.equals(packetId, that.packetId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(packetId);
  }
}
