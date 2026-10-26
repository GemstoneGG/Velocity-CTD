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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.crypto.IdentifiedKey;
import com.velocitypowered.api.proxy.player.TabListEntry;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.crypto.IdentifiedKeyImpl;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.UpsertPlayerInfoPacket;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import com.velocitypowered.proxy.protocol.packet.chat.RemoteChatSession;
import com.velocitypowered.proxy.tablist.VelocityTabList;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.ref.WeakReference;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Every viewer's tab list must point at the same profile, chat key and display name for a player.
 */
class SharedTabDataTest {

  private static final ProtocolVersion VERSION = ProtocolVersion.MAXIMUM_VERSION;

  private static PublicKey publicKey;

  @BeforeAll
  static void generateKey() throws NoSuchAlgorithmException {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    publicKey = generator.generateKeyPair().getPublic();
  }

  @Test
  void equalProfilesShareOneInstance() {
    UUID id = UUID.randomUUID();
    GameProfile first = SharedTabData.profile(profile(id, "Steve", "skin", "signature"));
    GameProfile second = SharedTabData.profile(profile(id, "Steve", "skin", "signature"));

    assertSame(first, second);
    assertSame(first.getProperties().getFirst(), second.getProperties().getFirst());
  }

  @Test
  void profilesThatDifferAnywhereStayApart() {
    UUID id = UUID.randomUUID();
    GameProfile base = SharedTabData.profile(profile(id, "Steve", "skin", "signature"));

    assertNotSame(base, SharedTabData.profile(profile(UUID.randomUUID(), "Steve", "skin",
        "signature")));
    assertNotSame(base, SharedTabData.profile(profile(id, "Alex", "skin", "signature")));
    assertNotSame(base, SharedTabData.profile(profile(id, "Steve", "other", "signature")));
    assertNotSame(base, SharedTabData.profile(profile(id, "Steve", "skin", "")));
    assertNotSame(base, SharedTabData.profile(new GameProfile(id, "Steve", List.of())));
  }

  @Test
  void propertyOrderMatters() {
    UUID id = UUID.randomUUID();
    GameProfile.Property a = new GameProfile.Property("a", "1", "");
    GameProfile.Property b = new GameProfile.Property("b", "2", "");

    GameProfile forward = SharedTabData.profile(new GameProfile(id, "Steve", List.of(a, b)));
    GameProfile backward = SharedTabData.profile(new GameProfile(id, "Steve", List.of(b, a)));

    assertNotSame(forward, backward);
    assertEquals("a", forward.getProperties().getFirst().getName());
    assertEquals("b", backward.getProperties().getFirst().getName());
  }

  @Test
  void sharedSkinIsReusedAcrossProfiles() {
    GameProfile first = SharedTabData.profile(profile(UUID.randomUUID(), "One", "skin", "sig"));
    GameProfile second = SharedTabData.profile(profile(UUID.randomUUID(), "Two", "skin", "sig"));

    assertNotSame(first, second);
    assertSame(first.getProperties().getFirst(), second.getProperties().getFirst());
  }

  @Test
  void unusedProfilesAreReleased() throws InterruptedException {
    WeakReference<GameProfile> reference = new WeakReference<>(
        SharedTabData.profile(profile(UUID.randomUUID(), "Gone", "skin-gone", "sig-gone")));

    for (int attempt = 0; attempt < 100 && reference.get() != null; attempt++) {
      System.gc();
      Thread.sleep(20);
    }

    assertNull(reference.get());
  }

  @Test
  void equalDisplayNamesShareOneInstance() {
    Component first = SharedTabData.displayName(
        Component.text("[VIP] ", NamedTextColor.GOLD).append(Component.text("Steve")));
    Component second = SharedTabData.displayName(
        Component.text("[VIP] ", NamedTextColor.GOLD).append(Component.text("Steve")));
    Component other = SharedTabData.displayName(
        Component.text("[VIP] ", NamedTextColor.RED).append(Component.text("Steve")));

    assertSame(first, second);
    assertNotSame(first, other);
    assertNull(SharedTabData.displayName(null));
  }

  @Test
  void equalChatSessionsShareOneInstanceAndKey() {
    UUID sessionId = UUID.randomUUID();
    long expiry = Instant.now().plusSeconds(3600).toEpochMilli();
    byte[] signature = randomSignature(1);

    RemoteChatSession first = readSession(sessionId, expiry, signature);
    RemoteChatSession second = readSession(sessionId, expiry, signature);

    assertSame(first, second);
    assertEquals(sessionId, first.getSessionId());
    assertEquals(publicKey, first.getIdentifiedKey().getSignedPublicKey());
    assertEquals(expiry, first.getIdentifiedKey().getExpiryTemporal().toEpochMilli());
    assertArrayEquals(signature, first.getIdentifiedKey().getSignature());

    RemoteChatSession otherSession = readSession(UUID.randomUUID(), expiry, signature);
    assertNotSame(first, otherSession);
    assertSame(first.getIdentifiedKey(), otherSession.getIdentifiedKey());

    assertNotSame(first.getIdentifiedKey(),
        readSession(sessionId, expiry + 1, signature).getIdentifiedKey());
    assertNotSame(first.getIdentifiedKey(),
        readSession(sessionId, expiry, randomSignature(2)).getIdentifiedKey());
  }

  @Test
  void sharedChatSessionReencodesToTheSameBytes() {
    UUID sessionId = UUID.randomUUID();
    long expiry = Instant.now().plusSeconds(3600).toEpochMilli();
    ByteBuf original = encodeSession(sessionId, expiry, randomSignature(3));
    ByteBuf copy = original.copy();

    RemoteChatSession session = SharedTabData.readChatSession(VERSION, copy);
    ByteBuf written = Unpooled.buffer();
    session.write(written);

    assertEquals(original, written);
  }

  @Test
  void thousandViewersHoldOnePlayer() {
    UUID playerId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    long expiry = Instant.now().plusSeconds(3600).toEpochMilli();
    byte[] signature = randomSignature(4);
    Component displayName = Component.text("[VIP] ", NamedTextColor.GOLD)
        .append(Component.text("Steve"));

    List<VelocityTabList> viewers = new ArrayList<>();
    for (int viewer = 0; viewer < 1_000; viewer++) {
      VelocityTabList tabList = new VelocityTabList(player());
      tabList.processUpdate(decodeAddPlayer(playerId, sessionId, expiry, signature, displayName));
      viewers.add(tabList);
    }

    TabListEntry first = viewers.getFirst().getEntry(playerId).orElseThrow();
    assertNotNull(first.getChatSession());
    assertEquals(displayName, first.getDisplayNameComponent().orElseThrow());

    for (VelocityTabList tabList : viewers) {
      TabListEntry entry = tabList.getEntry(playerId).orElseThrow();
      assertSame(first.getProfile(), entry.getProfile());
      assertSame(first.getChatSession(), entry.getChatSession());
      assertSame(first.getDisplayNameComponent().orElseThrow(),
          entry.getDisplayNameComponent().orElseThrow());
    }
  }

  private static GameProfile profile(UUID id, String name, String value, String signature) {
    // New strings each time, the way a decoder produces them.
    return new GameProfile(id, new String(name),
        List.of(new GameProfile.Property(new String("textures"), new String(value),
            new String(signature))));
  }

  private static byte[] randomSignature(long seed) {
    byte[] signature = new byte[512];
    new Random(seed).nextBytes(signature);
    return signature;
  }

  private static ByteBuf encodeSession(UUID sessionId, long expiry, byte[] signature) {
    ByteBuf buf = Unpooled.buffer();
    new RemoteChatSession(sessionId,
        new IdentifiedKeyImpl(IdentifiedKey.Revision.LINKED_V2, publicKey,
            Instant.ofEpochMilli(expiry), signature.clone()))
        .write(buf);
    return buf;
  }

  private static RemoteChatSession readSession(UUID sessionId, long expiry, byte[] signature) {
    return SharedTabData.readChatSession(VERSION, encodeSession(sessionId, expiry, signature));
  }

  private static UpsertPlayerInfoPacket decodeAddPlayer(UUID playerId, UUID sessionId,
                                                        long expiry, byte[] signature,
                                                        Component displayName) {
    UpsertPlayerInfoPacket.Entry entry = new UpsertPlayerInfoPacket.Entry(playerId);
    entry.setProfile(new GameProfile(playerId, "Steve",
        List.of(new GameProfile.Property("textures", "skin-value", "skin-signature"))));
    entry.setChatSession(new RemoteChatSession(sessionId,
        new IdentifiedKeyImpl(IdentifiedKey.Revision.LINKED_V2, publicKey,
            Instant.ofEpochMilli(expiry), signature.clone())));
    entry.setDisplayName(new ComponentHolder(VERSION, displayName));
    entry.setListed(true);

    UpsertPlayerInfoPacket packet = new UpsertPlayerInfoPacket(EnumSet.of(
        UpsertPlayerInfoPacket.Action.ADD_PLAYER,
        UpsertPlayerInfoPacket.Action.INITIALIZE_CHAT,
        UpsertPlayerInfoPacket.Action.UPDATE_LISTED,
        UpsertPlayerInfoPacket.Action.UPDATE_DISPLAY_NAME), List.of(entry));

    ByteBuf buf = Unpooled.buffer();
    packet.encode(buf, ProtocolUtils.Direction.CLIENTBOUND, VERSION);

    UpsertPlayerInfoPacket decoded = new UpsertPlayerInfoPacket();
    decoded.decode(buf, ProtocolUtils.Direction.CLIENTBOUND, VERSION);
    return decoded;
  }

  private static ConnectedPlayer player() {
    ConnectedPlayer player = mock(ConnectedPlayer.class);
    when(player.getConnection()).thenReturn(mock(MinecraftConnection.class));
    when(player.getProtocolVersion()).thenReturn(VERSION);
    return player;
  }
}
