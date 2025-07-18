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

package com.velocitypowered.proxy.connection;

import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.packet.AvailableCommandsPacket;
import com.velocitypowered.proxy.protocol.packet.BossBarPacket;
import com.velocitypowered.proxy.protocol.packet.BundleDelimiterPacket;
import com.velocitypowered.proxy.protocol.packet.ClientSettingsPacket;
import com.velocitypowered.proxy.protocol.packet.ClientboundCookieRequestPacket;
import com.velocitypowered.proxy.protocol.packet.ClientboundStoreCookiePacket;
import com.velocitypowered.proxy.protocol.packet.DisconnectPacket;
import com.velocitypowered.proxy.protocol.packet.EncryptionRequestPacket;
import com.velocitypowered.proxy.protocol.packet.EncryptionResponsePacket;
import com.velocitypowered.proxy.protocol.packet.HandshakePacket;
import com.velocitypowered.proxy.protocol.packet.HeaderAndFooterPacket;
import com.velocitypowered.proxy.protocol.packet.JoinGamePacket;
import com.velocitypowered.proxy.protocol.packet.KeepAlivePacket;
import com.velocitypowered.proxy.protocol.packet.LegacyHandshakePacket;
import com.velocitypowered.proxy.protocol.packet.LegacyPingPacket;
import com.velocitypowered.proxy.protocol.packet.LegacyPlayerListItemPacket;
import com.velocitypowered.proxy.protocol.packet.LoginAcknowledgedPacket;
import com.velocitypowered.proxy.protocol.packet.LoginPluginMessagePacket;
import com.velocitypowered.proxy.protocol.packet.LoginPluginResponsePacket;
import com.velocitypowered.proxy.protocol.packet.PingIdentifyPacket;
import com.velocitypowered.proxy.protocol.packet.PluginMessagePacket;
import com.velocitypowered.proxy.protocol.packet.RemovePlayerInfoPacket;
import com.velocitypowered.proxy.protocol.packet.RemoveResourcePackPacket;
import com.velocitypowered.proxy.protocol.packet.ResourcePackRequestPacket;
import com.velocitypowered.proxy.protocol.packet.ResourcePackResponsePacket;
import com.velocitypowered.proxy.protocol.packet.RespawnPacket;
import com.velocitypowered.proxy.protocol.packet.ServerDataPacket;
import com.velocitypowered.proxy.protocol.packet.ServerLoginPacket;
import com.velocitypowered.proxy.protocol.packet.ServerLoginSuccessPacket;
import com.velocitypowered.proxy.protocol.packet.ServerboundCookieResponsePacket;
import com.velocitypowered.proxy.protocol.packet.SetCompressionPacket;
import com.velocitypowered.proxy.protocol.packet.StatusPingPacket;
import com.velocitypowered.proxy.protocol.packet.StatusRequestPacket;
import com.velocitypowered.proxy.protocol.packet.StatusResponsePacket;
import com.velocitypowered.proxy.protocol.packet.TabCompleteRequestPacket;
import com.velocitypowered.proxy.protocol.packet.TabCompleteResponsePacket;
import com.velocitypowered.proxy.protocol.packet.TransferPacket;
import com.velocitypowered.proxy.protocol.packet.UpsertPlayerInfoPacket;
import com.velocitypowered.proxy.protocol.packet.chat.ChatAcknowledgementPacket;
import com.velocitypowered.proxy.protocol.packet.chat.PlayerChatCompletionPacket;
import com.velocitypowered.proxy.protocol.packet.chat.SystemChatPacket;
import com.velocitypowered.proxy.protocol.packet.chat.keyed.KeyedPlayerChatPacket;
import com.velocitypowered.proxy.protocol.packet.chat.keyed.KeyedPlayerCommandPacket;
import com.velocitypowered.proxy.protocol.packet.chat.legacy.LegacyChatPacket;
import com.velocitypowered.proxy.protocol.packet.chat.session.SessionPlayerChatPacket;
import com.velocitypowered.proxy.protocol.packet.chat.session.SessionPlayerCommandPacket;
import com.velocitypowered.proxy.protocol.packet.config.ActiveFeaturesPacket;
import com.velocitypowered.proxy.protocol.packet.config.ClientboundCustomReportDetailsPacket;
import com.velocitypowered.proxy.protocol.packet.config.ClientboundServerLinksPacket;
import com.velocitypowered.proxy.protocol.packet.config.FinishedUpdatePacket;
import com.velocitypowered.proxy.protocol.packet.config.KnownPacksPacket;
import com.velocitypowered.proxy.protocol.packet.config.RegistrySyncPacket;
import com.velocitypowered.proxy.protocol.packet.config.StartUpdatePacket;
import com.velocitypowered.proxy.protocol.packet.config.TagsUpdatePacket;
import com.velocitypowered.proxy.protocol.packet.title.LegacyTitlePacket;
import com.velocitypowered.proxy.protocol.packet.title.TitleActionbarPacket;
import com.velocitypowered.proxy.protocol.packet.title.TitleClearPacket;
import com.velocitypowered.proxy.protocol.packet.title.TitleSubtitlePacket;
import com.velocitypowered.proxy.protocol.packet.title.TitleTextPacket;
import com.velocitypowered.proxy.protocol.packet.title.TitleTimesPacket;
import io.netty.buffer.ByteBuf;

/**
 * Interface for dispatching received Minecraft packets.
 */
public interface MinecraftSessionHandler {

  /**
   * Called before any packet is handled.
   *
   * <p>This method is invoked prior to processing any packet. If it returns {@code true},
   * the packet will be ignored and no further handling will occur.</p>
   *
   * @return {@code true} to prevent packet processing, {@code false} to proceed
   */
  default boolean beforeHandle() {
    return false;
  }

  /**
   * Handles a generic packet that was not matched to any specific handler.
   *
   * @param packet the packet to process
   */
  default void handleGeneric(MinecraftPacket packet) {
  }

  /**
   * Handles a raw unknown packet that could not be decoded into a known packet type.
   *
   * @param buf the raw data buffer of the packet
   */
  default void handleUnknown(ByteBuf buf) {
  }

  /**
   * Called when the connection becomes active.
   *
   * <p>This method is called once the channel is successfully established and ready for use.</p>
   */
  default void connected() {
  }

  /**
   * Called when the connection is closed.
   *
   * <p>This method is triggered after the underlying channel is closed and the session is no longer active.</p>
   */
  default void disconnected() {
  }

  /**
   * Called when this session handler is activated.
   *
   * <p>This typically occurs when the handler is registered or promoted to handle packets for a particular state.</p>
   */
  default void activated() {
  }

  /**
   * Called when this session handler is deactivated.
   *
   * <p>This typically occurs when a different session handler takes over the connection's state.</p>
   */
  default void deactivated() {
  }

  /**
   * Called when an exception is thrown during channel operations.
   *
   * @param throwable the exception that was caught
   */
  default void exception(Throwable throwable) {
  }

  /**
   * Called when the channel’s writability changes.
   *
   * <p>Useful for implementing flow control or logging backpressure events.</p>
   */
  default void writabilityChanged() {
  }

  /**
   * Called when a read operation on the channel is complete.
   *
   * <p>This method is triggered when a full batch of packets has been read from the network.</p>
   */
  default void readCompleted() {
  }

  /**
   * Handles {@link AvailableCommandsPacket}.
   *
   * @param commands the available commands packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(AvailableCommandsPacket commands) {
    return false;
  }

  /**
   * Handles {@link BossBarPacket}.
   *
   * @param packet the boss bar packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(BossBarPacket packet) {
    return false;
  }

  /**
   * Handles {@link LegacyChatPacket}.
   *
   * @param packet the legacy chat packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(LegacyChatPacket packet) {
    return false;
  }

  /**
   * Handles {@link ClientSettingsPacket}.
   *
   * @param packet the client settings packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(ClientSettingsPacket packet) {
    return false;
  }

  /**
   * Handles {@link DisconnectPacket}.
   *
   * @param packet the disconnect packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(DisconnectPacket packet) {
    return false;
  }

  /**
   * Handles {@link EncryptionRequestPacket}.
   *
   * @param packet the encryption request packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(EncryptionRequestPacket packet) {
    return false;
  }

  /**
   * Handles {@link EncryptionResponsePacket}.
   *
   * @param packet the encryption response packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(EncryptionResponsePacket packet) {
    return false;
  }

  /**
   * Handles {@link HandshakePacket}.
   *
   * @param packet the handshake packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(HandshakePacket packet) {
    return false;
  }

  /**
   * Handles {@link HeaderAndFooterPacket}.
   *
   * @param ignoredPacket the header and footer packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(HeaderAndFooterPacket ignoredPacket) {
    return false;
  }

  /**
   * Handles {@link JoinGamePacket}.
   *
   * @param packet the join game packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(JoinGamePacket packet) {
    return false;
  }

  /**
   * Handles {@link KeepAlivePacket}.
   *
   * @param packet the keep-alive packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(KeepAlivePacket packet) {
    return false;
  }

  /**
   * Handles {@link LegacyHandshakePacket}.
   *
   * @param packet the legacy handshake packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(LegacyHandshakePacket packet) {
    return false;
  }

  /**
   * Handles {@link LegacyPingPacket}.
   *
   * @param packet the legacy ping packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(LegacyPingPacket packet) {
    return false;
  }

  /**
   * Handles {@link LoginPluginMessagePacket}.
   *
   * @param packet the login plugin message packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(LoginPluginMessagePacket packet) {
    return false;
  }

  /**
   * Handles {@link LoginPluginResponsePacket}.
   *
   * @param packet the login plugin response packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(LoginPluginResponsePacket packet) {
    return false;
  }

  /**
   * Handles {@link PluginMessagePacket}.
   *
   * @param packet the plugin message packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(PluginMessagePacket packet) {
    return false;
  }

  /**
   * Handles {@link RespawnPacket}.
   *
   * @param ignoredPacket the respawn packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(RespawnPacket ignoredPacket) {
    return false;
  }

  /**
   * Handles {@link ServerLoginPacket}.
   *
   * @param packet the server login packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(ServerLoginPacket packet) {
    return false;
  }

  /**
   * Handles {@link ServerLoginSuccessPacket}.
   *
   * @param packet the server login success packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(ServerLoginSuccessPacket packet) {
    return false;
  }

  /**
   * Handles {@link SetCompressionPacket}.
   *
   * @param packet the set compression packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(SetCompressionPacket packet) {
    return false;
  }

  /**
   * Handles {@link StatusPingPacket}.
   *
   * @param packet the status ping packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(StatusPingPacket packet) {
    return false;
  }

  /**
   * Handles {@link StatusRequestPacket}.
   *
   * @param packet the status request packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(StatusRequestPacket packet) {
    return false;
  }

  /**
   * Handles {@link StatusResponsePacket}.
   *
   * @param packet the status response packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(StatusResponsePacket packet) {
    return false;
  }

  /**
   * Handles {@link TabCompleteRequestPacket}.
   *
   * @param packet the tab complete request packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(TabCompleteRequestPacket packet) {
    return false;
  }

  /**
   * Handles {@link TabCompleteResponsePacket}.
   *
   * @param packet the tab complete response packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(TabCompleteResponsePacket packet) {
    return false;
  }

  /**
   * Handles {@link LegacyTitlePacket}.
   *
   * @param ignoredPacket the legacy title packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(LegacyTitlePacket ignoredPacket) {
    return false;
  }

  /**
   * Handles {@link TitleTextPacket}.
   *
   * @param ignoredPacket the title text packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(TitleTextPacket ignoredPacket) {
    return false;
  }

  /**
   * Handles {@link TitleSubtitlePacket}.
   *
   * @param ignoredPacket the title subtitle packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(TitleSubtitlePacket ignoredPacket) {
    return false;
  }

  /**
   * Handles {@link TitleActionbarPacket}.
   *
   * @param ignoredPacket the title actionbar packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(TitleActionbarPacket ignoredPacket) {
    return false;
  }

  /**
   * Handles {@link TitleTimesPacket}.
   *
   * @param ignoredPacket the title times packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(TitleTimesPacket ignoredPacket) {
    return false;
  }

  /**
   * Handles {@link TitleClearPacket}.
   *
   * @param ignoredPacket the title clear packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(TitleClearPacket ignoredPacket) {
    return false;
  }

  /**
   * Handles {@link LegacyPlayerListItemPacket}.
   *
   * @param packet the legacy player list item packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(LegacyPlayerListItemPacket packet) {
    return false;
  }

  /**
   * Handles {@link ResourcePackRequestPacket}.
   *
   * @param packet the resource pack request packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(ResourcePackRequestPacket packet) {
    return false;
  }

  /**
   * Handles {@link RemoveResourcePackPacket}.
   *
   * @param packet the remove resource pack packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(RemoveResourcePackPacket packet) {
    return false;
  }

  /**
   * Handles {@link ResourcePackResponsePacket}.
   *
   * @param packet the resource pack response packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(ResourcePackResponsePacket packet) {
    return false;
  }

  /**
   * Handles {@link KeyedPlayerChatPacket}.
   *
   * @param packet the keyed player chat packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(KeyedPlayerChatPacket packet) {
    return false;
  }

  /**
   * Handles {@link SessionPlayerChatPacket}.
   *
   * @param packet the session player chat packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(SessionPlayerChatPacket packet) {
    return false;
  }

  /**
   * Handles {@link SystemChatPacket}.
   *
   * @param ignoredPacket the system chat packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(SystemChatPacket ignoredPacket) {
    return false;
  }

  /**
   * Handles {@link KeyedPlayerCommandPacket}.
   *
   * @param packet the keyed player command packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(KeyedPlayerCommandPacket packet) {
    return false;
  }

  /**
   * Handles {@link SessionPlayerCommandPacket}.
   *
   * @param packet the session player command packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(SessionPlayerCommandPacket packet) {
    return false;
  }

  /**
   * Handles {@link PlayerChatCompletionPacket}.
   *
   * @param ignoredPacket the player chat completion packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(PlayerChatCompletionPacket ignoredPacket) {
    return false;
  }

  /**
   * Handles {@link ServerDataPacket}.
   *
   * @param serverData the server data packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(ServerDataPacket serverData) {
    return false;
  }

  /**
   * Handles {@link RemovePlayerInfoPacket}.
   *
   * @param packet the remove player info packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(RemovePlayerInfoPacket packet) {
    return false;
  }

  /**
   * Handles {@link UpsertPlayerInfoPacket}.
   *
   * @param packet the upsert player info packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(UpsertPlayerInfoPacket packet) {
    return false;
  }

  /**
   * Handles {@link LoginAcknowledgedPacket}.
   *
   * @param packet the login acknowledged packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(LoginAcknowledgedPacket packet) {
    return false;
  }

  /**
   * Handles {@link ActiveFeaturesPacket}.
   *
   * @param ignoredPacket the active features packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(ActiveFeaturesPacket ignoredPacket) {
    return false;
  }

  /**
   * Handles {@link FinishedUpdatePacket}.
   *
   * @param packet the finished update packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(FinishedUpdatePacket packet) {
    return false;
  }

  /**
   * Handles {@link RegistrySyncPacket}.
   *
   * @param packet the registry sync packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(RegistrySyncPacket packet) {
    return false;
  }

  /**
   * Handles {@link TagsUpdatePacket}.
   *
   * @param packet the tags update packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(TagsUpdatePacket packet) {
    return false;
  }

  /**
   * Handles {@link StartUpdatePacket}.
   *
   * @param packet the start update packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(StartUpdatePacket packet) {
    return false;
  }

  /**
   * Handles {@link PingIdentifyPacket}.
   *
   * @param pingIdentify the ping identify packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(PingIdentifyPacket pingIdentify) {
    return false;
  }

  /**
   * Handles {@link ChatAcknowledgementPacket}.
   *
   * @param chatAcknowledgement the chat acknowledgement packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(ChatAcknowledgementPacket chatAcknowledgement) {
    return false;
  }

  /**
   * Handles {@link BundleDelimiterPacket}.
   *
   * @param bundleDelimiterPacket the bundle delimiter packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(BundleDelimiterPacket bundleDelimiterPacket) {
    return false;
  }

  /**
   * Handles {@link TransferPacket}.
   *
   * @param transfer the transfer packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(TransferPacket transfer) {
    return false;
  }

  /**
   * Handles {@link KnownPacksPacket}.
   *
   * @param packet the known packs packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(KnownPacksPacket packet) {
    return false;
  }

  /**
   * Handles {@link ClientboundStoreCookiePacket}.
   *
   * @param packet the store cookie packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(ClientboundStoreCookiePacket packet) {
    return false;
  }

  /**
   * Handles {@link ClientboundCookieRequestPacket}.
   *
   * @param packet the cookie request packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(ClientboundCookieRequestPacket packet) {
    return false;
  }

  /**
   * Handles {@link ServerboundCookieResponsePacket}.
   *
   * @param packet the cookie response packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(ServerboundCookieResponsePacket packet) {
    return false;
  }

  /**
   * Handles {@link ClientboundCustomReportDetailsPacket}.
   *
   * @param packet the custom report details packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(ClientboundCustomReportDetailsPacket packet) {
    return false;
  }

  /**
   * Handles {@link ClientboundServerLinksPacket}.
   *
   * @param packet the server links packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(ClientboundServerLinksPacket packet) {
    return false;
  }
}
