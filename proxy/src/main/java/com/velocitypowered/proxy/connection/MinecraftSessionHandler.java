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
import com.velocitypowered.proxy.protocol.packet.ClientboundSoundEntityPacket;
import com.velocitypowered.proxy.protocol.packet.ClientboundStopSoundPacket;
import com.velocitypowered.proxy.protocol.packet.ClientboundStoreCookiePacket;
import com.velocitypowered.proxy.protocol.packet.DialogClearPacket;
import com.velocitypowered.proxy.protocol.packet.DialogShowPacket;
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
import com.velocitypowered.proxy.protocol.packet.ServerboundCustomClickActionPacket;
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
import com.velocitypowered.proxy.protocol.packet.config.CodeOfConductAcceptPacket;
import com.velocitypowered.proxy.protocol.packet.config.CodeOfConductPacket;
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
  default void handleGeneric(final MinecraftPacket packet) {
  }

  /**
   * Handles a raw unknown packet that could not be decoded into a known packet type.
   *
   * @param buf the raw data buffer of the packet
   */
  default void handleUnknown(final ByteBuf buf) {
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
  default void exception(final Throwable throwable) {
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
  default boolean handle(final AvailableCommandsPacket commands) {
    return false;
  }

  /**
   * Handles {@link BossBarPacket}.
   *
   * @param packet the boss bar packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final BossBarPacket packet) {
    return false;
  }

  /**
   * Handles {@link LegacyChatPacket}.
   *
   * @param packet the legacy chat packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final LegacyChatPacket packet) {
    return false;
  }

  /**
   * Handles {@link ClientSettingsPacket}.
   *
   * @param packet the client settings packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final ClientSettingsPacket packet) {
    return false;
  }

  /**
   * Handles {@link DisconnectPacket}.
   *
   * @param packet the disconnect packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final DisconnectPacket packet) {
    return false;
  }

  /**
   * Handles {@link EncryptionRequestPacket}.
   *
   * @param packet the encryption request packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final EncryptionRequestPacket packet) {
    return false;
  }

  /**
   * Handles {@link EncryptionResponsePacket}.
   *
   * @param packet the encryption response packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final EncryptionResponsePacket packet) {
    return false;
  }

  /**
   * Handles {@link HandshakePacket}.
   *
   * @param packet the handshake packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final HandshakePacket packet) {
    return false;
  }

  /**
   * Handles {@link HeaderAndFooterPacket}.
   *
   * @param packet the header and footer packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final HeaderAndFooterPacket packet) {
    return false;
  }

  /**
   * Handles {@link JoinGamePacket}.
   *
   * @param packet the join game packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final JoinGamePacket packet) {
    return false;
  }

  /**
   * Handles {@link KeepAlivePacket}.
   *
   * @param packet the keep-alive packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final KeepAlivePacket packet) {
    return false;
  }

  /**
   * Handles {@link LegacyHandshakePacket}.
   *
   * @param packet the legacy handshake packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final LegacyHandshakePacket packet) {
    return false;
  }

  /**
   * Handles {@link LegacyPingPacket}.
   *
   * @param packet the legacy ping packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final LegacyPingPacket packet) {
    return false;
  }

  /**
   * Handles {@link LoginPluginMessagePacket}.
   *
   * @param packet the login plugin message packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final LoginPluginMessagePacket packet) {
    return false;
  }

  /**
   * Handles {@link LoginPluginResponsePacket}.
   *
   * @param packet the login plugin response packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final LoginPluginResponsePacket packet) {
    return false;
  }

  /**
   * Handles {@link PluginMessagePacket}.
   *
   * @param packet the plugin message packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final PluginMessagePacket packet) {
    return false;
  }

  /**
   * Handles {@link RespawnPacket}.
   *
   * @param packet the respawn packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final RespawnPacket packet) {
    return false;
  }

  /**
   * Handles {@link ServerLoginPacket}.
   *
   * @param packet the server login packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final ServerLoginPacket packet) {
    return false;
  }

  /**
   * Handles {@link ServerLoginSuccessPacket}.
   *
   * @param packet the server login success packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final ServerLoginSuccessPacket packet) {
    return false;
  }

  /**
   * Handles {@link SetCompressionPacket}.
   *
   * @param packet the set compression packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final SetCompressionPacket packet) {
    return false;
  }

  /**
   * Handles {@link StatusPingPacket}.
   *
   * @param packet the status ping packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final StatusPingPacket packet) {
    return false;
  }

  /**
   * Handles {@link StatusRequestPacket}.
   *
   * @param packet the status request packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final StatusRequestPacket packet) {
    return false;
  }

  /**
   * Handles {@link StatusResponsePacket}.
   *
   * @param packet the status response packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final StatusResponsePacket packet) {
    return false;
  }

  /**
   * Handles {@link TabCompleteRequestPacket}.
   *
   * @param packet the tab complete request packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final TabCompleteRequestPacket packet) {
    return false;
  }

  /**
   * Handles {@link TabCompleteResponsePacket}.
   *
   * @param packet the tab complete response packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final TabCompleteResponsePacket packet) {
    return false;
  }

  /**
   * Handles {@link LegacyTitlePacket}.
   *
   * @param packet the legacy title packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final LegacyTitlePacket packet) {
    return false;
  }

  /**
   * Handles {@link TitleTextPacket}.
   *
   * @param packet the title text packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final TitleTextPacket packet) {
    return false;
  }

  /**
   * Handles {@link TitleSubtitlePacket}.
   *
   * @param packet the title subtitle packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final TitleSubtitlePacket packet) {
    return false;
  }

  /**
   * Handles {@link TitleActionbarPacket}.
   *
   * @param packet the title actionbar packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final TitleActionbarPacket packet) {
    return false;
  }

  /**
   * Handles {@link TitleTimesPacket}.
   *
   * @param packet the title times packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final TitleTimesPacket packet) {
    return false;
  }

  /**
   * Handles {@link TitleClearPacket}.
   *
   * @param packet the title clear packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final TitleClearPacket packet) {
    return false;
  }

  /**
   * Handles {@link LegacyPlayerListItemPacket}.
   *
   * @param packet the legacy player list item packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final LegacyPlayerListItemPacket packet) {
    return false;
  }

  /**
   * Handles {@link ResourcePackRequestPacket}.
   *
   * @param packet the resource pack request packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final ResourcePackRequestPacket packet) {
    return false;
  }

  /**
   * Handles {@link RemoveResourcePackPacket}.
   *
   * @param packet the remove resource pack packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final RemoveResourcePackPacket packet) {
    return false;
  }

  /**
   * Handles {@link ResourcePackResponsePacket}.
   *
   * @param packet the resource pack response packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final ResourcePackResponsePacket packet) {
    return false;
  }

  /**
   * Handles {@link KeyedPlayerChatPacket}.
   *
   * @param packet the keyed player chat packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final KeyedPlayerChatPacket packet) {
    return false;
  }

  /**
   * Handles {@link SessionPlayerChatPacket}.
   *
   * @param packet the session player chat packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final SessionPlayerChatPacket packet) {
    return false;
  }

  /**
   * Handles {@link SystemChatPacket}.
   *
   * @param packet the system chat packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final SystemChatPacket packet) {
    return false;
  }

  /**
   * Handles {@link KeyedPlayerCommandPacket}.
   *
   * @param packet the keyed player command packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final KeyedPlayerCommandPacket packet) {
    return false;
  }

  /**
   * Handles {@link SessionPlayerCommandPacket}.
   *
   * @param packet the session player command packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final SessionPlayerCommandPacket packet) {
    return false;
  }

  /**
   * Handles {@link PlayerChatCompletionPacket}.
   *
   * @param packet the player chat completion packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final PlayerChatCompletionPacket packet) {
    return false;
  }

  /**
   * Handles {@link ServerDataPacket}.
   *
   * @param serverData the server data packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final ServerDataPacket serverData) {
    return false;
  }

  /**
   * Handles {@link RemovePlayerInfoPacket}.
   *
   * @param packet the remove player info packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final RemovePlayerInfoPacket packet) {
    return false;
  }

  /**
   * Handles {@link UpsertPlayerInfoPacket}.
   *
   * @param packet the upsert player info packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final UpsertPlayerInfoPacket packet) {
    return false;
  }

  /**
   * Handles {@link LoginAcknowledgedPacket}.
   *
   * @param packet the login acknowledged packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final LoginAcknowledgedPacket packet) {
    return false;
  }

  /**
   * Handles {@link ActiveFeaturesPacket}.
   *
   * @param packet the active features packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final ActiveFeaturesPacket packet) {
    return false;
  }

  /**
   * Handles {@link FinishedUpdatePacket}.
   *
   * @param packet the finished update packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final FinishedUpdatePacket packet) {
    return false;
  }

  /**
   * Handles {@link RegistrySyncPacket}.
   *
   * @param packet the registry sync packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final RegistrySyncPacket packet) {
    return false;
  }

  /**
   * Handles {@link TagsUpdatePacket}.
   *
   * @param packet the tags update packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final TagsUpdatePacket packet) {
    return false;
  }

  /**
   * Handles {@link StartUpdatePacket}.
   *
   * @param packet the start update packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final StartUpdatePacket packet) {
    return false;
  }

  /**
   * Handles {@link PingIdentifyPacket}.
   *
   * @param pingIdentify the ping identify packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final PingIdentifyPacket pingIdentify) {
    return false;
  }

  /**
   * Handles {@link ChatAcknowledgementPacket}.
   *
   * @param chatAcknowledgement the chat acknowledgement packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final ChatAcknowledgementPacket chatAcknowledgement) {
    return false;
  }

  /**
   * Handles {@link BundleDelimiterPacket}.
   *
   * @param bundleDelimiterPacket the bundle delimiter packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final BundleDelimiterPacket bundleDelimiterPacket) {
    return false;
  }

  /**
   * Handles {@link TransferPacket}.
   *
   * @param transfer the transfer packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final TransferPacket transfer) {
    return false;
  }

  /**
   * Handles {@link KnownPacksPacket}.
   *
   * @param packet the known packs packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final KnownPacksPacket packet) {
    return false;
  }

  /**
   * Handles {@link ClientboundStoreCookiePacket}.
   *
   * @param packet the store cookie packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final ClientboundStoreCookiePacket packet) {
    return false;
  }

  /**
   * Handles {@link ClientboundCookieRequestPacket}.
   *
   * @param packet the cookie request packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final ClientboundCookieRequestPacket packet) {
    return false;
  }

  /**
   * Handles {@link ServerboundCookieResponsePacket}.
   *
   * @param packet the cookie response packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final ServerboundCookieResponsePacket packet) {
    return false;
  }

  /**
   * Handles {@link ClientboundCustomReportDetailsPacket}.
   *
   * @param packet the custom report details packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final ClientboundCustomReportDetailsPacket packet) {
    return false;
  }

  /**
   * Handles {@link ClientboundServerLinksPacket}.
   *
   * @param packet the server links packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final ClientboundServerLinksPacket packet) {
    return false;
  }

  /**
   * Handles {@link DialogClearPacket}.
   *
   * @param packet the dialog clear packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final DialogClearPacket packet) {
    return false;
  }

  /**
   * Handles {@link DialogShowPacket}.
   *
   * @param packet the dialog show packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final DialogShowPacket packet) {
    return false;
  }

  /**
   * Handles {@link ServerboundCustomClickActionPacket}.
   *
   * @param packet the custom click action packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final ServerboundCustomClickActionPacket packet) {
    return false;
  }

  /**
   * Handles {@link CodeOfConductPacket}.
   *
   * @param packet the code-of-conduct packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final CodeOfConductPacket packet) {
    return false;
  }

  /**
   * Handles {@link CodeOfConductAcceptPacket}.
   *
   * @param packet the code-of-conduct accept packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final CodeOfConductAcceptPacket packet) {
    return false;
  }

  /**
   * Handles {@link ClientboundSoundEntityPacket}.
   *
   * @param packet the sound entity packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final ClientboundSoundEntityPacket packet) {
    return false;
  }

  /**
   * Handles {@link ClientboundStopSoundPacket}.
   *
   * @param packet the stop sound packet
   * @return {@code true} if the packet was handled, {@code false} otherwise
   */
  default boolean handle(final ClientboundStopSoundPacket packet) {
    return false;
  }
}
