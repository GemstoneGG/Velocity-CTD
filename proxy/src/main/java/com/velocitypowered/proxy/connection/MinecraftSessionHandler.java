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

  default boolean beforeHandle() {
    return false;
  }

  default void handleGeneric(final MinecraftPacket packet) {
  }

  default void handleUnknown(final ByteBuf buf) {
  }

  default void connected() {
  }

  default void disconnected() {
  }

  default void activated() {
  }

  default void deactivated() {
  }

  default void exception(final Throwable throwable) {
  }

  default void writabilityChanged() {
  }

  default void readCompleted() {
  }

  default boolean handle(final AvailableCommandsPacket commands) {
    return false;
  }

  default boolean handle(final BossBarPacket packet) {
    return false;
  }

  default boolean handle(final LegacyChatPacket packet) {
    return false;
  }

  default boolean handle(final ClientSettingsPacket packet) {
    return false;
  }

  default boolean handle(final DisconnectPacket packet) {
    return false;
  }

  default boolean handle(final EncryptionRequestPacket packet) {
    return false;
  }

  default boolean handle(final EncryptionResponsePacket packet) {
    return false;
  }

  default boolean handle(final HandshakePacket packet) {
    return false;
  }

  default boolean handle(final HeaderAndFooterPacket packet) {
    return false;
  }

  default boolean handle(final JoinGamePacket packet) {
    return false;
  }

  default boolean handle(final KeepAlivePacket packet) {
    return false;
  }

  default boolean handle(final LegacyHandshakePacket packet) {
    return false;
  }

  default boolean handle(final LegacyPingPacket packet) {
    return false;
  }

  default boolean handle(final LoginPluginMessagePacket packet) {
    return false;
  }

  default boolean handle(final LoginPluginResponsePacket packet) {
    return false;
  }

  default boolean handle(final PluginMessagePacket packet) {
    return false;
  }

  default boolean handle(final RespawnPacket packet) {
    return false;
  }

  default boolean handle(final ServerLoginPacket packet) {
    return false;
  }

  default boolean handle(final ServerLoginSuccessPacket packet) {
    return false;
  }

  default boolean handle(final SetCompressionPacket packet) {
    return false;
  }

  default boolean handle(final StatusPingPacket packet) {
    return false;
  }

  default boolean handle(final StatusRequestPacket packet) {
    return false;
  }

  default boolean handle(final StatusResponsePacket packet) {
    return false;
  }

  default boolean handle(final TabCompleteRequestPacket packet) {
    return false;
  }

  default boolean handle(final TabCompleteResponsePacket packet) {
    return false;
  }

  default boolean handle(final LegacyTitlePacket packet) {
    return false;
  }

  default boolean handle(final TitleTextPacket packet) {
    return false;
  }

  default boolean handle(final TitleSubtitlePacket packet) {
    return false;
  }

  default boolean handle(final TitleActionbarPacket packet) {
    return false;
  }

  default boolean handle(final TitleTimesPacket packet) {
    return false;
  }

  default boolean handle(final TitleClearPacket packet) {
    return false;
  }

  default boolean handle(final LegacyPlayerListItemPacket packet) {
    return false;
  }

  default boolean handle(final ResourcePackRequestPacket packet) {
    return false;
  }

  default boolean handle(final RemoveResourcePackPacket packet) {
    return false;
  }

  default boolean handle(final ResourcePackResponsePacket packet) {
    return false;
  }

  default boolean handle(final KeyedPlayerChatPacket packet) {
    return false;
  }

  default boolean handle(final SessionPlayerChatPacket packet) {
    return false;
  }

  default boolean handle(final SystemChatPacket packet) {
    return false;
  }

  default boolean handle(final KeyedPlayerCommandPacket packet) {
    return false;
  }

  default boolean handle(final SessionPlayerCommandPacket packet) {
    return false;
  }

  default boolean handle(final PlayerChatCompletionPacket packet) {
    return false;
  }

  default boolean handle(final ServerDataPacket serverData) {
    return false;
  }

  default boolean handle(final RemovePlayerInfoPacket packet) {
    return false;
  }

  default boolean handle(final UpsertPlayerInfoPacket packet) {
    return false;
  }

  default boolean handle(final LoginAcknowledgedPacket packet) {
    return false;
  }

  default boolean handle(final ActiveFeaturesPacket packet) {
    return false;
  }

  default boolean handle(final FinishedUpdatePacket packet) {
    return false;
  }

  default boolean handle(final RegistrySyncPacket packet) {
    return false;
  }

  default boolean handle(final TagsUpdatePacket packet) {
    return false;
  }

  default boolean handle(final StartUpdatePacket packet) {
    return false;
  }

  default boolean handle(final PingIdentifyPacket pingIdentify) {
    return false;
  }

  default boolean handle(final ChatAcknowledgementPacket chatAcknowledgement) {
    return false;
  }

  default boolean handle(final BundleDelimiterPacket bundleDelimiterPacket) {
    return false;
  }

  default boolean handle(final TransferPacket transfer) {
    return false;
  }

  default boolean handle(final KnownPacksPacket packet) {
    return false;
  }

  default boolean handle(final ClientboundStoreCookiePacket packet) {
    return false;
  }

  default boolean handle(final ClientboundCookieRequestPacket packet) {
    return false;
  }

  default boolean handle(final ServerboundCookieResponsePacket packet) {
    return false;
  }

  default boolean handle(final ClientboundCustomReportDetailsPacket packet) {
    return false;
  }

  default boolean handle(final ClientboundServerLinksPacket packet) {
    return false;
  }

  default boolean handle(final DialogClearPacket packet) {
    return false;
  }

  default boolean handle(final DialogShowPacket packet) {
    return false;
  }

  default boolean handle(final ServerboundCustomClickActionPacket packet) {
    return false;
  }

  default boolean handle(final CodeOfConductPacket packet) {
    return false;
  }

  default boolean handle(final CodeOfConductAcceptPacket packet) {
    return false;
  }

  default boolean handle(final ClientboundSoundEntityPacket packet) {
    return false;
  }

  default boolean handle(final ClientboundStopSoundPacket packet) {
    return false;
  }
}
