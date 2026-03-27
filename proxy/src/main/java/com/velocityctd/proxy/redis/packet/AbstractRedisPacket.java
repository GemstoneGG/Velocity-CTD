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

package com.velocityctd.proxy.redis.packet;

import com.velocityctd.proxy.redis.VelocityRedis;
import com.velocityctd.proxy.redis.packet.annotation.OneWayPacket;
import java.util.Objects;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base implementation of a {@link RedisPacket}.
 *
 * <p>This class provides common metadata used across all Redis packets,
 * including internal packet identifiers, transaction metadata, reply flags,
 * and publish behavior. Concrete packet types extend this class either
 * directly or through typed packet subclasses.</p>
 */
public abstract sealed class AbstractRedisPacket implements RedisPacket
    permits DataPacket, GenericPacket {

  /**
   * A unique internal identifier automatically assigned to this packet.
   * Used for equality and tracking packet flow across the Redis pipeline.
   */
  private final UUID packetId;

  /**
   * The fully qualified class name of the packet implementation.
   * Used to deserialize the correct packet type remotely.
   */
  private final String type;

  /**
   * Whether this packet is explicitly marked as one-way using
   * {@link OneWayPacket}. One-way packets never expect replies.
   */
  private final boolean oneWay;

  /**
   * Indicates whether this packet instance represents a reply to another packet.
   */
  private boolean reply;

  /**
   * The transaction identifier associated with this packet, if any.
   * Present when a packet participates in a request–reply transaction.
   */
  private @MonotonicNonNull UUID transactionId;

  /**
   * The transaction type associated with this packet, identifying the
   * request this packet belongs to.
   */
  private @MonotonicNonNull String transactionType;

  /**
   * Constructs a new {@link AbstractRedisPacket}, initializing its
   * internal identifiers and determining whether it is marked as one-way.
   */
  public AbstractRedisPacket() {
    this.packetId = UUID.randomUUID();
    this.type = this.getClass().getName();
    this.oneWay = this.getClass().isAnnotationPresent(OneWayPacket.class);
  }

  /**
   * Gets the unique internal identifier assigned to this packet.
   *
   * @return the packet's unique identifier
   */
  @Override
  public UUID getId() {
    return packetId;
  }

  /**
   * Gets the packet's type, represented as the fully qualified class name.
   *
   * @return the packet type string
   */
  @Override
  public String getType() {
    return type;
  }

  /**
   * Determines whether this packet is marked as one-way.
   *
   * @return {@code true} if the packet is annotated with {@link OneWayPacket}
   */
  @Override
  public boolean isOneWay() {
    return oneWay;
  }

  /**
   * Checks whether this packet represents a reply to another packet.
   *
   * @return {@code true} if this packet is a reply
   */
  @Override
  public boolean isReply() {
    return reply;
  }

  /**
   * Marks whether this packet is a reply to another packet.
   *
   * @param reply {@code true} if this packet represents a reply
   */
  @Override
  public void setReply(final boolean reply) {
    this.reply = reply;
  }

  /**
   * Gets the transaction identifier associated with this packet, if present.
   *
   * @return the transaction ID, or {@code null} if none is set
   */
  @Override
  public @Nullable UUID getTransactionId() {
    return this.transactionId;
  }

  /**
   * Sets the transaction identifier for this packet.
   *
   * @param transactionId the transaction ID to assign
   */
  @Override
  public void setTransactionId(final @NotNull UUID transactionId) {
    this.transactionId = transactionId;
  }

  /**
   * Gets the transaction type associated with this packet.
   *
   * @return the transaction type string, or {@code null} if none is set
   */
  @Override
  public @Nullable String getTransactionType() {
    return this.transactionType;
  }

  /**
   * Sets the transaction type for this packet.
   *
   * @param transactionType the transaction type identifier
   */
  @Override
  public void setTransactionType(final @NotNull String transactionType) {
    this.transactionType = transactionType;
  }

  /**
   * Publishes this packet using the active {@link VelocityRedis} provider.
   *
   * <p>A packet cannot be published if Redis has not been initialized.
   * In such cases, an {@link IllegalStateException} is thrown.</p>
   *
   * @throws IllegalStateException if Redis is not yet initialized
   */
  @Override
  public void publish() {
    final VelocityRedis redis = VelocityRedis.INSTANCE;
    if (redis == null) {
      throw new IllegalStateException("Tried to publish packet without Redis being initialized.");
    }

    redis.getProvider().publish(this);
  }

  /**
   * Compares this packet to another object for equality based on the packet ID.
   *
   * @param o the object to compare with
   * @return {@code true} if the objects represent the same packet
   */
  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    AbstractRedisPacket that = (AbstractRedisPacket) o;
    return Objects.equals(packetId, that.packetId);
  }

  /**
   * Computes a hash code for this packet based on its unique ID.
   *
   * @return the hash code of this packet
   */
  @Override
  public int hashCode() {
    return Objects.hashCode(packetId);
  }
}
