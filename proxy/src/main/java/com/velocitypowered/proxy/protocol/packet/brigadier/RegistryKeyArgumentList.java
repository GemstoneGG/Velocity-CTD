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

package com.velocitypowered.proxy.protocol.packet.brigadier;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;

/**
 * Represents a list of {@link RegistryKeyArgument} objects.
 *
 * <p>Used to manage and store multiple registry key arguments.</p>
 */
public final class RegistryKeyArgumentList {

  /**
   * Represents a registry key argument that can either be a resource or a tag.
   */
  public static class ResourceOrTag extends RegistryKeyArgument {

    /**
     * Constructs a new {@link ResourceOrTag} argument with the given identifier.
     *
     * @param identifier the registry identifier
     */
    public ResourceOrTag(final String identifier) {
      super(identifier);
    }

    /**
     * Serializer for {@link ResourceOrTag}.
     */
    public static class Serializer implements ArgumentPropertySerializer<ResourceOrTag> {

      /**
       * A shared singleton instance of the {@link ResourceOrTag.Serializer}.
       */
      static final ResourceOrTag.Serializer REGISTRY = new ResourceOrTag.Serializer();

      /**
       * Deserializes a {@link ResourceOrTag} argument from the given buffer.
       *
       * @param buf the buffer containing the serialized argument
       * @param protocolVersion the protocol version being used
       * @return the deserialized {@link ResourceOrTag} instance
       */
      @Override
      public ResourceOrTag deserialize(final ByteBuf buf, final ProtocolVersion protocolVersion) {
        return new ResourceOrTag(ProtocolUtils.readString(buf));
      }

      /**
       * Serializes the given {@link ResourceOrTag} argument to the specified buffer.
       *
       * @param object the argument to serialize
       * @param buf the buffer to write to
       * @param protocolVersion the protocol version being used
       */
      @Override
      public void serialize(final ResourceOrTag object, final ByteBuf buf, final ProtocolVersion protocolVersion) {
        ProtocolUtils.writeString(buf, object.getIdentifier());
      }
    }
  }

  /**
   * Represents a registry key argument specifically for a resource or tag key.
   */
  public static class ResourceOrTagKey extends RegistryKeyArgument {

    /**
     * Constructs a new {@link ResourceOrTagKey} argument with the given identifier.
     *
     * @param identifier the registry identifier
     */
    public ResourceOrTagKey(final String identifier) {
      super(identifier);
    }

    /**
     * Serializer for {@link ResourceOrTagKey}.
     */
    public static class Serializer implements ArgumentPropertySerializer<ResourceOrTagKey> {

      /**
       * A shared singleton instance of the {@link ResourceOrTagKey.Serializer}.
       */
      static final ResourceOrTagKey.Serializer REGISTRY = new ResourceOrTagKey.Serializer();

      /**
       * Deserializes a {@link ResourceOrTagKey} argument from the given buffer.
       *
       * @param buf the buffer containing the serialized argument
       * @param protocolVersion the protocol version being used
       * @return the deserialized {@link ResourceOrTagKey} instance
       */
      @Override
      public ResourceOrTagKey deserialize(final ByteBuf buf, final ProtocolVersion protocolVersion) {
        return new ResourceOrTagKey(ProtocolUtils.readString(buf));
      }

      /**
       * Serializes the given {@link ResourceOrTagKey} argument to the specified buffer.
       *
       * @param object the argument to serialize
       * @param buf the buffer to write to
       * @param protocolVersion the protocol version being used
       */
      @Override
      public void serialize(final ResourceOrTagKey object, final ByteBuf buf, final ProtocolVersion protocolVersion) {
        ProtocolUtils.writeString(buf, object.getIdentifier());
      }
    }
  }

  /**
   * Represents a registry key argument for a resource.
   */
  public static class ResourceSelector extends RegistryKeyArgument {

    /**
     * Constructs a new {@link ResourceSelector} argument with the given identifier.
     *
     * @param identifier the registry identifier
     */
    public ResourceSelector(final String identifier) {
      super(identifier);
    }

    /**
     * Serializer for {@link ResourceSelector}.
     */
    public static class Serializer implements ArgumentPropertySerializer<ResourceSelector> {

      /**
       * A shared singleton instance of the {@link ResourceSelector.Serializer}.
       */
      static final ResourceSelector.Serializer REGISTRY = new ResourceSelector.Serializer();

      /**
       * Deserializes a {@link ResourceSelector} argument from the given buffer.
       *
       * @param buf the buffer containing the serialized argument
       * @param protocolVersion the protocol version being used
       * @return the deserialized {@link ResourceSelector} instance
       */
      @Override
      public ResourceSelector deserialize(final ByteBuf buf, final ProtocolVersion protocolVersion) {
        return new ResourceSelector(ProtocolUtils.readString(buf));
      }

      /**
       * Serializes the given {@link ResourceSelector} argument to the specified buffer.
       *
       * @param object the argument to serialize
       * @param buf the buffer to write to
       * @param protocolVersion the protocol version being used
       */
      @Override
      public void serialize(final ResourceSelector object, final ByteBuf buf, final ProtocolVersion protocolVersion) {
        ProtocolUtils.writeString(buf, object.getIdentifier());
      }
    }
  }

  /**
   * Represents a registry key argument for a resource key.
   */
  public static class ResourceKey extends RegistryKeyArgument {

    /**
     * Constructs a new {@link ResourceKey} argument with the given identifier.
     *
     * @param identifier the registry identifier
     */
    public ResourceKey(final String identifier) {
      super(identifier);
    }

    /**
     * Serializer for {@link ResourceKey}.
     */
    public static class Serializer implements ArgumentPropertySerializer<ResourceKey> {

      /**
       * A shared singleton instance of the {@link ResourceKey.Serializer}.
       */
      static final ResourceKey.Serializer REGISTRY = new ResourceKey.Serializer();

      /**
       * Deserializes a {@link ResourceKey} argument from the given buffer.
       *
       * @param buf the buffer containing the serialized argument
       * @param protocolVersion the protocol version being used
       * @return the deserialized {@link ResourceKey} instance
       */
      @Override
      public ResourceKey deserialize(final ByteBuf buf, final ProtocolVersion protocolVersion) {
        return new ResourceKey(ProtocolUtils.readString(buf));
      }

      /**
       * Serializes the given {@link ResourceKey} argument to the specified buffer.
       *
       * @param object the argument to serialize
       * @param buf the buffer to write to
       * @param protocolVersion the protocol version being used
       */
      @Override
      public void serialize(final ResourceKey object, final ByteBuf buf, final ProtocolVersion protocolVersion) {
        ProtocolUtils.writeString(buf, object.getIdentifier());
      }
    }
  }

  RegistryKeyArgumentList() {
  }
}
