/*
 * Copyright (C) 2026 Velocity-CTD Contributors
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

package com.velocityctd.proxy.tablist;

import com.velocityctd.proxy.util.WeakInterner;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.crypto.IdentifiedKey;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.crypto.IdentifiedKeyImpl;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.chat.RemoteChatSession;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Interns the data held by tab list entries, so the tab lists of all players on the proxy share
 * one copy of each player's data instead of one per viewer.
 *
 * <p>Shared values are immutable and held weakly. All methods are thread-safe.</p>
 */
public final class SharedTabData {

  private static final WeakInterner<PropertyKey, GameProfile.Property> PROPERTIES =
      new WeakInterner<>();
  private static final WeakInterner<ProfileKey, GameProfile> PROFILES = new WeakInterner<>();
  private static final WeakInterner<PlayerKeyKey, IdentifiedKey> PLAYER_KEYS =
      new WeakInterner<>();
  private static final WeakInterner<ChatSessionKey, RemoteChatSession> CHAT_SESSIONS =
      new WeakInterner<>();

  private SharedTabData() {
    throw new AssertionError();
  }

  /**
   * Returns the shared profile with the same id, undashed id, name and properties, in order.
   *
   * @param profile the profile to share, or {@code null}
   * @return the shared profile, or {@code null} when {@code profile} is {@code null}
   */
  public static @Nullable GameProfile profile(@Nullable GameProfile profile) {
    if (profile == null) {
      return null;
    }

    final List<GameProfile.Property> properties = profile.getProperties();
    final List<GameProfile.Property> sharedProperties = new ArrayList<>(properties.size());
    boolean alreadyShared = true;
    for (GameProfile.Property property : properties) {
      final GameProfile.Property shared = property(property);
      sharedProperties.add(shared);
      alreadyShared &= shared == property;
    }

    // Properties are interned first, so ProfileKey can compare them by identity.
    final ProfileKey key = new ProfileKey(profile.getId(), profile.getUndashedId(), profile.getName(),
        sharedProperties.toArray(GameProfile.Property[]::new));
    final boolean candidateIsShared = alreadyShared;
    return PROFILES.intern(key, ignored -> candidateIsShared
        ? profile : profile.withProperties(sharedProperties));
  }

  /**
   * Returns the shared property with the same name, value and signature.
   *
   * @param property the property to share
   * @return the shared property
   */
  public static GameProfile.Property property(GameProfile.Property property) {
    return PROPERTIES.intern(
        new PropertyKey(property.getName(), property.getValue(), property.getSignature()),
        property);
  }


  /**
   * Reads a chat session like {@link RemoteChatSession#RemoteChatSession(ProtocolVersion, ByteBuf)}
   * and returns the shared copy. Known sessions are looked up by their raw bytes, so the public key
   * is only parsed once.
   *
   * @param version the protocol version of the packet
   * @param buf the buffer to read from
   * @return the shared chat session
   */
  public static RemoteChatSession readChatSession(ProtocolVersion version, ByteBuf buf) {
    final UUID sessionId = ProtocolUtils.readUuid(buf);
    final IdentifiedKey playerKey = readPlayerKey(version, buf);
    return CHAT_SESSIONS.intern(new ChatSessionKey(sessionId, playerKey),
        key -> new RemoteChatSession(key.sessionId, key.playerKey));
  }

  /**
   * Reads a player key like {@link ProtocolUtils#readPlayerKey(ProtocolVersion, ByteBuf)} and
   * returns the shared copy.
   *
   * <p>Shared keys must never be passed to {@link IdentifiedKeyImpl#internalAddHolder(UUID)}, which
   * is only for a connecting player's own key.</p>
   *
   * @param version the protocol version of the packet
   * @param buf the buffer to read from
   * @return the shared player key
   */
  public static IdentifiedKey readPlayerKey(ProtocolVersion version, ByteBuf buf) {
    final long expiry = buf.readLong();
    final byte[] publicKey = ProtocolUtils.readByteArray(buf);
    final byte[] signature = ProtocolUtils.readByteArray(buf, 4096);
    final IdentifiedKey.Revision revision = version.noGreaterOrLessThan(ProtocolVersion.MINECRAFT_1_19)
        ? IdentifiedKey.Revision.GENERIC_V1 : IdentifiedKey.Revision.LINKED_V2;

    return PLAYER_KEYS.intern(new PlayerKeyKey(revision, expiry, publicKey, signature),
        key -> new IdentifiedKeyImpl(key.revision, key.publicKey, key.expiry, key.signature));
  }

  private record PropertyKey(String name, String value, String signature) {
  }

  /**
   * Holds properties that are already shared, so they compare by identity.
   */
  private record ProfileKey(UUID id, String undashedId, String name,
                            GameProfile.Property[] properties) {

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }

      if (!(other instanceof ProfileKey that) || this.properties.length != that.properties.length) {
        return false;
      }

      for (int i = 0; i < this.properties.length; i++) {
        if (this.properties[i] != that.properties[i]) {
          return false;
        }
      }

      return this.id.equals(that.id) && this.undashedId.equals(that.undashedId)
          && this.name.equals(that.name);
    }

    @Override
    public int hashCode() {
      int hash = this.id.hashCode();
      hash = 31 * hash + this.name.hashCode();
      for (GameProfile.Property property : this.properties) {
        hash = 31 * hash + System.identityHashCode(property);
      }

      return hash;
    }
  }

  /**
   * Holds the raw key bytes. The shared key keeps the same signature array.
   */
  private record PlayerKeyKey(IdentifiedKey.Revision revision, long expiry, byte[] publicKey,
                              byte[] signature) {

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }

      return other instanceof PlayerKeyKey that && this.revision == that.revision
          && this.expiry == that.expiry && Arrays.equals(this.publicKey, that.publicKey)
          && Arrays.equals(this.signature, that.signature);
    }

    @Override
    public int hashCode() {
      int hash = this.revision.hashCode();
      hash = 31 * hash + Long.hashCode(this.expiry);
      hash = 31 * hash + Arrays.hashCode(this.publicKey);
      return 31 * hash + Arrays.hashCode(this.signature);
    }
  }

  /**
   * Holds a key that is already shared, so it compares by identity.
   */
  private record ChatSessionKey(UUID sessionId, IdentifiedKey playerKey) {

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }

      return other instanceof ChatSessionKey that && this.playerKey == that.playerKey
          && this.sessionId.equals(that.sessionId);
    }

    @Override
    public int hashCode() {
      return 31 * this.sessionId.hashCode() + System.identityHashCode(this.playerKey);
    }
  }
}
