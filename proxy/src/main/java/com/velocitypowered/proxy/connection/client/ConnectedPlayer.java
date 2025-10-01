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

package com.velocitypowered.proxy.connection.client;

import static com.velocitypowered.api.proxy.ConnectionRequestBuilder.Status.ALREADY_CONNECTED;
import static com.velocitypowered.proxy.connection.util.ConnectionRequestResults.plainResult;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;

import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent.LoginStatus;
import com.velocitypowered.api.event.connection.PreTransferEvent;
import com.velocitypowered.api.event.player.CookieRequestEvent;
import com.velocitypowered.api.event.player.CookieStoreEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent.DisconnectPlayer;
import com.velocitypowered.api.event.player.KickedFromServerEvent.Notify;
import com.velocitypowered.api.event.player.KickedFromServerEvent.RedirectPlayer;
import com.velocitypowered.api.event.player.KickedFromServerEvent.ServerKickResult;
import com.velocitypowered.api.event.player.PlayerModInfoEvent;
import com.velocitypowered.api.event.player.PlayerSettingsChangedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.player.configuration.PlayerEnterConfigurationEvent;
import com.velocitypowered.api.network.HandshakeIntent;
import com.velocitypowered.api.network.ProtocolState;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.permission.PermissionFunction;
import com.velocitypowered.api.permission.PermissionProvider;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.crypto.IdentifiedKey;
import com.velocitypowered.api.proxy.crypto.KeyIdentifiable;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.PluginMessageEncoder;
import com.velocitypowered.api.proxy.player.PlayerSettings;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.api.util.ModInfo;
import com.velocitypowered.api.util.ServerLink;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.adventure.VelocityBossBarImplementation;
import com.velocitypowered.proxy.config.PlayerInfoForwarding;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.MinecraftConnectionAssociation;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.player.bossbar.BossBarManager;
import com.velocitypowered.proxy.connection.player.bundle.BundleDelimiterHandler;
import com.velocitypowered.proxy.connection.player.resourcepack.VelocityResourcePackInfo;
import com.velocitypowered.proxy.connection.player.resourcepack.handler.ResourcePackHandler;
import com.velocitypowered.proxy.connection.util.ConnectionMessages;
import com.velocitypowered.proxy.connection.util.ConnectionRequestResults.Impl;
import com.velocitypowered.proxy.connection.util.VelocityInboundConnection;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.netty.MinecraftEncoder;
import com.velocitypowered.proxy.protocol.packet.BundleDelimiterPacket;
import com.velocitypowered.proxy.protocol.packet.ClientSettingsPacket;
import com.velocitypowered.proxy.protocol.packet.ClientboundCookieRequestPacket;
import com.velocitypowered.proxy.protocol.packet.ClientboundSoundEntityPacket;
import com.velocitypowered.proxy.protocol.packet.ClientboundStopSoundPacket;
import com.velocitypowered.proxy.protocol.packet.ClientboundStoreCookiePacket;
import com.velocitypowered.proxy.protocol.packet.DisconnectPacket;
import com.velocitypowered.proxy.protocol.packet.HeaderAndFooterPacket;
import com.velocitypowered.proxy.protocol.packet.KeepAlivePacket;
import com.velocitypowered.proxy.protocol.packet.PluginMessagePacket;
import com.velocitypowered.proxy.protocol.packet.RemoveResourcePackPacket;
import com.velocitypowered.proxy.protocol.packet.TransferPacket;
import com.velocitypowered.proxy.protocol.packet.chat.ChatQueue;
import com.velocitypowered.proxy.protocol.packet.chat.ChatType;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import com.velocitypowered.proxy.protocol.packet.chat.PlayerChatCompletionPacket;
import com.velocitypowered.proxy.protocol.packet.chat.builder.ChatBuilderFactory;
import com.velocitypowered.proxy.protocol.packet.chat.builder.ChatBuilderV2;
import com.velocitypowered.proxy.protocol.packet.chat.legacy.LegacyChatPacket;
import com.velocitypowered.proxy.protocol.packet.config.ClientboundServerLinksPacket;
import com.velocitypowered.proxy.protocol.packet.config.StartUpdatePacket;
import com.velocitypowered.proxy.protocol.packet.title.GenericTitlePacket;
import com.velocitypowered.proxy.protocol.util.ByteBufDataOutput;
import com.velocitypowered.proxy.queue.ServerQueueStatus;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import com.velocitypowered.proxy.tablist.InternalTabList;
import com.velocitypowered.proxy.tablist.KeyedVelocityTabList;
import com.velocitypowered.proxy.tablist.VelocityTabList;
import com.velocitypowered.proxy.tablist.VelocityTabListLegacy;
import com.velocitypowered.proxy.util.ClosestLocaleMatcher;
import com.velocitypowered.proxy.util.DurationUtils;
import com.velocitypowered.proxy.util.TranslatableMapper;
import com.velocitypowered.proxy.util.collect.CappedSet;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.permission.PermissionChecker;
import net.kyori.adventure.platform.facet.FacetPointers;
import net.kyori.adventure.platform.facet.FacetPointers.Type;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.pointer.PointersSupplier;
import net.kyori.adventure.resource.ResourcePackInfoLike;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackRequestLike;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.text.minimessage.translation.Argument;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import net.kyori.adventure.title.TitlePart;
import net.kyori.adventure.translation.GlobalTranslator;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a player connected to the proxy.
 */
@SuppressWarnings("UnstableApiUsage")
public class ConnectedPlayer implements MinecraftConnectionAssociation, Player, KeyIdentifiable, VelocityInboundConnection {

  /**
   * The maximum number of plugin message channels a client may register before being rejected.
   *
   * <p>This value is configurable via the {@code velocity.max-clientside-plugin-channels} system property.</p>
   * Defaults to {@code 1024}.
   */
  public static final int MAX_CLIENTSIDE_PLUGIN_CHANNELS = Integer.getInteger("velocity.max-clientside-plugin-channels", 1024);

  /**
   * A plain text serializer that flattens translatable components.
   *
   * <p>Used for extracting plain disconnect reasons from structured components.</p>
   */
  private static final PlainTextComponentSerializer PASS_THRU_TRANSLATE =
      PlainTextComponentSerializer.builder().flattener(TranslatableMapper.FLATTENER).build();

  /**
   * The default permission provider used if none is explicitly assigned to the player.
   *
   * <p>Always returns {@link Tristate#UNDEFINED} for any permission query.</p>
   */
  static final PermissionProvider DEFAULT_PERMISSIONS = s -> PermissionFunction.ALWAYS_UNDEFINED;

  /**
   * A structured Adventure component logger instance for logging player-related messages.
   */
  private static final ComponentLogger logger = ComponentLogger.logger(ConnectedPlayer.class);

  /**
   * Provides structured pointers for {@link ConnectedPlayer} to resolve
   * identity, locale, and permission-related values used across Adventure APIs.
   */
  private static final @NotNull PointersSupplier<ConnectedPlayer> POINTERS_SUPPLIER = PointersSupplier.<ConnectedPlayer>builder()
      .resolving(Identity.UUID, Player::getUniqueId)
      .resolving(Identity.NAME, Player::getUsername)
      .resolving(Identity.DISPLAY_NAME, player -> Component.text(player.getUsername()))
      .resolving(Identity.LOCALE, Player::getEffectiveLocale)
      .resolving(PermissionChecker.POINTER, Player::getPermissionChecker)
      .resolving(FacetPointers.TYPE, player -> Type.PLAYER)
      .build();

  /**
   * The actual Minecraft connection. This is actually a wrapper object around the Netty channel.
   */
  private final MinecraftConnection connection;

  /**
   * The virtual host address that the player used to connect, or {@code null} if not known.
   */
  private final @Nullable InetSocketAddress virtualHost;

  /**
   * The raw string version of the virtual host, or {@code null} if not available.
   */
  private final @Nullable String rawVirtualHost;

  /**
   * The handshake intent sent by the client during connection.
   */
  private final HandshakeIntent handshakeIntent;

  /**
   * The game profile for the player, containing their UUID and name.
   */
  private GameProfile profile;

  /**
   * The permission function used to evaluate permission checks for this player.
   */
  private PermissionFunction permissionFunction;

  /**
   * The current index into the server fallback list.
   */
  private int tryIndex = 0;

  /**
   * The current round-trip ping in milliseconds.
   */
  private long ping = -1;

  /**
   * Whether the player connected using online mode authentication.
   */
  private final boolean onlineMode;

  /**
   * The currently connected backend server, or {@code null} if not connected.
   */
  private @Nullable VelocityServerConnection connectedServer;

  /**
   * The server connection currently being attempted, or {@code null} if none is in flight.
   */
  private @Nullable VelocityServerConnection connectionInFlight;

  /**
   * The parsed player settings, or {@code null} if not yet sent by the client.
   */
  private @Nullable PlayerSettings settings;

  /**
   * The mod information sent by the client, or {@code null} if none was sent.
   */
  private @Nullable ModInfo modInfo;

  /**
   * The set of boss bars currently displayed to the player.
   */
  private final Set<VelocityBossBarImplementation> bossBars = new HashSet<>();

  /**
   * The current header line of the player's tab list.
   */
  private Component playerListHeader = Component.empty();

  /**
   * The current footer line of the player's tab list.
   */
  private Component playerListFooter = Component.empty();

  /**
   * The player's tab list implementation, varying by protocol version.
   */
  private final InternalTabList tabList;

  /**
   * The Velocity proxy server instance.
   */
  private final VelocityServer server;

  /**
   * The current connection phase used for mod or protocol negotiation.
   */
  private ClientConnectionPhase connectionPhase;

  /**
   * Plugin message channels registered by the client.
   */
  private final Collection<ChannelIdentifier> clientsideChannels;

  /**
   * A future that completes once the teardown logic is finished for this player.
   */
  private final CompletableFuture<Void> teardownFuture = new CompletableFuture<>();

  /**
   * The list of servers to attempt connecting to, used for fallbacks.
   */
  private @MonotonicNonNull List<String> serversToTry = null;

  /**
   * The handler responsible for managing resource pack offers and responses.
   */
  private final ResourcePackHandler resourcePackHandler;

  /**
   * Handles bundling of packets into delimiter frames for supported versions.
   */
  private final BundleDelimiterHandler bundleHandler = new BundleDelimiterHandler(this);

  /**
   * Whether the player should be excluded from Redis player removal on disconnect.
   */
  private boolean dontRemoveFromRedis;

  /**
   * The brand name reported by the client (e.g. "vanilla", "forge"), or {@code null} if not sent.
   */
  private @Nullable String clientBrand;

  /**
   * The effective locale used to render messages for this player.
   */
  private @Nullable Locale effectiveLocale;

  /**
   * The player's identified public key, used for message signing and verification.
   */
  private final @Nullable IdentifiedKey playerKey;

  /**
   * The client settings packet most recently received from the player.
   */
  private @Nullable ClientSettingsPacket clientSettingsPacket;

  /**
   * The chat queue used to manage chat and command messages from this player.
   */
  private volatile ChatQueue chatQueue;

  /**
   * The factory for building version-specific chat packets.
   */
  private final ChatBuilderFactory chatBuilderFactory;

  /**
   * The manager responsible for tracking and controlling boss bars shown to this player.
   *
   * <p>Handles suppression of boss bar update packets during login and server switches
   * (to avoid client disconnects in 1.20.2+), and ensures bars are re-sent when the
   * player transitions between servers.</p>
   */
  private final BossBarManager bossBarManager;

  ConnectedPlayer(final VelocityServer server, final GameProfile profile, final MinecraftConnection connection,
                  final @Nullable InetSocketAddress virtualHost, final @Nullable String rawVirtualHost, final boolean onlineMode,
                  final HandshakeIntent handshakeIntent, final @Nullable IdentifiedKey playerKey) {
    this.server = server;
    this.profile = profile;
    this.connection = connection;
    this.virtualHost = virtualHost;
    this.rawVirtualHost = rawVirtualHost;
    this.handshakeIntent = handshakeIntent;
    this.permissionFunction = PermissionFunction.ALWAYS_UNDEFINED;
    this.connectionPhase = connection.getType().getInitialClientPhase();
    this.onlineMode = onlineMode;
    this.clientsideChannels = CappedSet.create(MAX_CLIENTSIDE_PLUGIN_CHANNELS);

    if (connection.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_19_3)) {
      this.tabList = new VelocityTabList(this);
    } else if (connection.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      this.tabList = new KeyedVelocityTabList(this, server);
    } else {
      this.tabList = new VelocityTabListLegacy(this, server);
    }

    this.playerKey = playerKey;
    this.chatQueue = new ChatQueue(this);
    this.chatBuilderFactory = new ChatBuilderFactory(this.getProtocolVersion());
    this.resourcePackHandler = ResourcePackHandler.create(this, server);
    this.bossBarManager = new BossBarManager(this);
  }

  /**
   * Used for cleaning up resources during a disconnection.
   */
  public void disconnected() {
    for (final VelocityBossBarImplementation bar : this.bossBars) {
      bar.viewerDisconnected(this);
    }

    if (this.server.getMultiProxyHandler().isRedisEnabled()) {
      this.server.getMultiProxyHandler().onPlayerLeave(this);
    }

    if (this.server.getQueueManager().isQueueEnabled()) {
      this.server.getQueueManager().onPlayerLeave(this);
    }
  }

  /**
   * Gets the {@link ChatBuilderFactory} associated with this player.
   *
   * <p>The factory is used to construct chat packets and completions based on
   * the player's protocol version and session context.</p>
   *
   * @return the chat builder factory for this player
   */
  public ChatBuilderFactory getChatBuilderFactory() {
    return chatBuilderFactory;
  }

  /**
   * Gets the {@link ChatQueue} responsible for managing ordered chat message
   * delivery and acknowledgement tracking for this player.
   *
   * @return the player's chat queue
   */
  public ChatQueue getChatQueue() {
    return chatQueue;
  }

  /**
   * Discards any messages still being processed by the {@link ChatQueue}, and creates a fresh state for future packets.
   * This should be used on server switches, or whenever the client resets its own 'last seen' state.
   */
  public void discardChatQueue() {
    // No need for atomic swap should only be called from event loop
    final ChatQueue oldChatQueue = chatQueue;
    chatQueue = new ChatQueue(this);
    oldChatQueue.close();
  }

  /**
   * Gets the {@link BundleDelimiterHandler} for this player.
   *
   * <p>This handler manages the state of bundle-delimited packet sequences
   * used during configuration and play phases for bundling multiple packets together.</p>
   *
   * @return the bundle delimiter handler for this player
   */
  public BundleDelimiterHandler getBundleHandler() {
    return this.bundleHandler;
  }

  /**
   * Returns the {@link Identity} for this player using their unique UUID.
   *
   * @return the identity representing this player
   */
  @Override
  public @NonNull Identity identity() {
    return Identity.identity(this.getUniqueId());
  }

  /**
   * Gets the username of the player as provided by the {@link GameProfile}.
   *
   * @return the player's username
   */
  @Override
  public String getUsername() {
    return profile.getName();
  }

  /**
   * Gets the effective {@link Locale} used for translating messages to this player.
   *
   * <p>If not explicitly set, this falls back to the locale provided by the player's client settings,
   * if available.</p>
   *
   * @return the player's effective locale, or {@code null} if not available
   */
  @Override
  public Locale getEffectiveLocale() {
    if (effectiveLocale == null && settings != null) {
      return settings.getLocale();
    }

    return effectiveLocale;
  }

  /**
   * Sets the effective {@link Locale} for the player.
   *
   * <p>This overrides any locale provided by the player's client settings.</p>
   *
   * @param locale the locale to use, or {@code null} to unset
   */
  @Override
  public void setEffectiveLocale(final @Nullable Locale locale) {
    effectiveLocale = locale;
  }

  /**
   * Gets the UUID of the player.
   *
   * @return the player's unique identifier
   */
  @Override
  public UUID getUniqueId() {
    return profile.getId();
  }

  /**
   * Gets the backend server the player is currently connected to.
   *
   * @return an {@link Optional} containing the connected server, if any
   */
  @Override
  public Optional<ServerConnection> getCurrentServer() {
    return Optional.ofNullable(connectedServer);
  }

  /**
   * Makes sure the player is connected to a server and returns the server they are connected to.
   *
   * @return the server the player is connected to
   */
  public VelocityServerConnection ensureAndGetCurrentServer() {
    VelocityServerConnection con = this.connectedServer;
    if (con == null) {
      throw new IllegalStateException("Not connected to server!");
    }

    return con;
  }

  /**
   * Returns the {@link GameProfile} associated with this player.
   *
   * <p>This includes the player's UUID, name, and any profile properties such as skin data.</p>
   *
   * @return the game profile of the player
   */
  @Override
  public GameProfile getGameProfile() {
    return profile;
  }

  /**
   * Gets the Minecraft connection associated with this player.
   *
   * <p>This represents the underlying Netty channel and associated state
   * for communication with the client.</p>
   *
   * @return the Minecraft connection for this player
   */
  public MinecraftConnection getConnection() {
    return connection;
  }

  /**
   * Gets the current round-trip ping to the client, in milliseconds.
   *
   * @return the ping in ms, or {@code -1} if unknown
   */
  @Override
  public long getPing() {
    return this.ping;
  }

  /**
   * Sets the current ping (in milliseconds) for this player.
   *
   * <p>Used internally when a keep-alive response is received.</p>
   *
   * @param ping the ping time in milliseconds
   */
  void setPing(final long ping) {
    this.ping = ping;
  }

  /**
   * Returns whether the player connected using online mode authentication.
   *
   * @return {@code true} if the player is authenticated in online mode
   */
  @Override
  public boolean isOnlineMode() {
    return onlineMode;
  }

  /**
   * Returns the {@link PlayerSettings} last sent by the client.
   *
   * <p>If no settings were sent yet, returns default settings.</p>
   *
   * @return the player's current or default settings
   */
  @Override
  public PlayerSettings getPlayerSettings() {
    return settings == null ? ClientSettingsWrapper.DEFAULT : this.settings;
  }

  /**
   * Gets the raw {@link ClientSettingsPacket} sent by the client, if available.
   *
   * <p>This packet includes raw client preferences such as locale, render distance,
   * and chat settings, before being wrapped into {@link PlayerSettings}.</p>
   *
   * @return the client settings packet, or {@code null} if not yet received
   */
  @Nullable
  public ClientSettingsPacket getClientSettingsPacket() {
    return clientSettingsPacket;
  }

  /**
   * Returns whether the player has sent their initial {@link PlayerSettings}.
   *
   * @return {@code true} if player settings were received, otherwise false
   */
  @Override
  public boolean hasSentPlayerSettings() {
    return settings != null;
  }

  /**
   * Sets player settings.
   *
   * @param clientSettingsPacket the player settings packet
   */
  public void setClientSettings(final ClientSettingsPacket clientSettingsPacket) {
    this.clientSettingsPacket = clientSettingsPacket;
    final ClientSettingsWrapper cs = new ClientSettingsWrapper(clientSettingsPacket);
    this.settings = cs;
    server.getEventManager().fireAndForget(new PlayerSettingsChangedEvent(this, cs));
  }

  /**
   * Gets the {@link ModInfo} sent by the client, if available.
   *
   * <p>This typically contains a list of mods and versions used by modded clients such as
   * Forge, Fabric, or NeoForge. If the client did not send mod information,
   * this will return an empty {@link Optional}.</p>
   *
   * @return an {@link Optional} containing the mod info, or empty if none was provided
   */
  @Override
  public Optional<ModInfo> getModInfo() {
    return Optional.ofNullable(modInfo);
  }

  /**
   * Sets the {@link ModInfo} for this player and fires a {@link PlayerModInfoEvent}.
   *
   * <p>This is typically used during the login process to register the mods
   * that the player has installed when using modded clients such as Fabric or Forge.</p>
   *
   * @param modInfo the mod info to associate with this player
   */
  public void setModInfo(final ModInfo modInfo) {
    this.modInfo = modInfo;
    server.getEventManager().fireAndForget(new PlayerModInfoEvent(this, modInfo));
  }

  /**
   * Returns the {@link Pointers} view exposing various identity, locale, and permission
   * related properties for this player.
   *
   * @return the Adventure pointer registry for this player
   */
  @Override
  public @NotNull Pointers pointers() {
    return POINTERS_SUPPLIER.view(this);
  }

  /**
   * Gets the IP address of the player from their connection to the proxy.
   *
   * @return the remote IP address of the player
   */
  @Override
  public InetSocketAddress getRemoteAddress() {
    return (InetSocketAddress) connection.getRemoteAddress();
  }

  /**
   * Returns the virtual host address that the player connected to.
   *
   * @return the virtual host, or empty if not available
   */
  @Override
  public Optional<InetSocketAddress> getVirtualHost() {
    return Optional.ofNullable(virtualHost);
  }

  /**
   * Returns the string form of the virtual host that the player connected to.
   *
   * @return the raw virtual host, or empty if not available
   */
  @Override
  public Optional<String> getRawVirtualHost() {
    return Optional.ofNullable(rawVirtualHost);
  }

  /**
   * Sets the permission function to evaluate permissions for this player.
   *
   * @param permissionFunction the new permission function
   */
  void setPermissionFunction(final PermissionFunction permissionFunction) {
    this.permissionFunction = permissionFunction;
  }

  /**
   * Checks if the player's connection is still active.
   *
   * @return {@code true} if the channel is open and active
   */
  @Override
  public boolean isActive() {
    return connection.getChannel().isActive();
  }

  /**
   * Returns the Minecraft protocol version used by the client.
   *
   * @return the player's protocol version
   */
  @Override
  public ProtocolVersion getProtocolVersion() {
    return connection.getProtocolVersion();
  }

  /**
   * Translates the message in the user's locale, falling back to the default locale if not set.
   *
   * @param message the message to translate
   * @return the translated message
   */
  public Component translateMessage(final Component message) {
    Locale locale = this.getEffectiveLocale();
    if (locale == null && settings != null) {
      locale = settings.getLocale();
    }

    if (locale == null) {
      locale = Locale.getDefault();
    }

    locale = ClosestLocaleMatcher.INSTANCE.lookupClosest(locale);
    return GlobalTranslator.render(message, locale);
  }

  /**
   * Sends a chat message to the player from the specified {@link Identity}.
   *
   * <p>The message will be translated using the player's effective locale before delivery.</p>
   *
   * @param identity the identity of the message sender
   * @param message the message to send
   */
  @Override
  @SuppressWarnings("deprecation")
  public void sendMessage(final @NonNull Identity identity, final @NonNull Component message) {
    final Component translated = translateMessage(message);

    connection.write(getChatBuilderFactory().builder().component(translated).forIdentity(identity).toClient());
  }

  /**
   * Sends a chat or system message to the player from the specified {@link Identity}, with a message type.
   *
   * <p>The message will be translated using the player's effective locale before delivery.</p>
   *
   * @param identity the identity of the message sender
   * @param message the message to send
   * @param type the type of message to send (chat or system)
   */
  @Override
  @SuppressWarnings("deprecation")
  public void sendMessage(final @NonNull Identity identity, final @NonNull Component message,
                          final @NonNull MessageType type) {
    Preconditions.checkNotNull(message, "message");
    Preconditions.checkNotNull(type, "type");

    Component translated = translateMessage(message).replaceText(TextReplacementConfig.builder().match("''").replacement("'").build());

    connection.write(getChatBuilderFactory().builder()
        .component(translated).forIdentity(identity)
        .setType(type == MessageType.CHAT ? ChatType.CHAT : ChatType.SYSTEM)
        .toClient());
  }

  /**
   * Sends an action bar message to the player.
   *
   * <p>Uses title packets for versions 1.11 and higher, and falls back to legacy chat packets otherwise.</p>
   *
   * @param message the action bar message to send
   */
  @Override
  public void sendActionBar(final @NonNull Component message) {
    Component translated = translateMessage(message);

    ProtocolVersion playerVersion = getProtocolVersion();
    if (playerVersion.noLessThan(ProtocolVersion.MINECRAFT_1_11)) {
      // Use the title packet instead.
      GenericTitlePacket pkt = GenericTitlePacket.constructTitlePacket(GenericTitlePacket.ActionType.SET_ACTION_BAR, playerVersion);
      pkt.setComponent(new ComponentHolder(playerVersion, translated));
      connection.write(pkt);
    } else {
      // Due to issues with action bar packets, we'll need to convert the text message into a
      // legacy message and then inject the legacy text into a component... yuck!
      JsonObject object = new JsonObject();
      object.addProperty("text", LegacyComponentSerializer.legacySection().serialize(translated));
      LegacyChatPacket legacyChat = new LegacyChatPacket();
      legacyChat.setMessage(object.toString());
      legacyChat.setType(LegacyChatPacket.GAME_INFO_TYPE);
      connection.write(legacyChat);
    }
  }

  /**
   * Gets the current tab list header being displayed to the player.
   *
   * @return the tab list header component
   */
  @Override
  public Component getPlayerListHeader() {
    return this.playerListHeader;
  }

  /**
   * Gets the current tab list footer being displayed to the player.
   *
   * @return the tab list footer component
   */
  @Override
  public Component getPlayerListFooter() {
    return this.playerListFooter;
  }

  /**
   * Sends a new header for the player's tab list while keeping the current footer.
   */
  @Override
  public void sendPlayerListHeader(final @NonNull Component header) {
    this.sendPlayerListHeaderAndFooter(header, this.playerListFooter);
  }

  /**
   * Sends a new footer for the player's tab list while keeping the current header.
   */
  @Override
  public void sendPlayerListFooter(final @NonNull Component footer) {
    this.sendPlayerListHeaderAndFooter(this.playerListHeader, footer);
  }

  /**
   * Sends both the header and footer for the player's tab list.
   *
   * <p>If header/footer translation is enabled in the proxy configuration,
   * the components will be localized before being sent.</p>
   */
  @Override
  public void sendPlayerListHeaderAndFooter(final @NotNull Component header,
                                            final @NotNull Component footer) {
    Component translatedHeader = header;
    Component translatedFooter = footer;
    if (server.getConfiguration().isTranslateHeaderFooter()) {
      translatedHeader = translateMessage(header);
      translatedFooter = translateMessage(footer);
    }

    this.playerListHeader = translatedHeader;
    this.playerListFooter = translatedFooter;
    if (this.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      this.connection.write(HeaderAndFooterPacket.create(translatedHeader, translatedFooter, this.getProtocolVersion()));
    }
  }

  /**
   * Displays a title and subtitle to the player using the appropriate packet format.
   *
   * @param title the full title object to send
   */
  @Override
  public void showTitle(final @NonNull Title title) {
    if (this.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      GenericTitlePacket timesPkt = GenericTitlePacket.constructTitlePacket(
          GenericTitlePacket.ActionType.SET_TIMES, this.getProtocolVersion());

      Times times = title.times();
      if (times != null) {
        timesPkt.setFadeIn((int) DurationUtils.toTicks(times.fadeIn()));
        timesPkt.setStay((int) DurationUtils.toTicks(times.stay()));
        timesPkt.setFadeOut((int) DurationUtils.toTicks(times.fadeOut()));
      }

      connection.delayedWrite(timesPkt);

      GenericTitlePacket subtitlePkt = GenericTitlePacket.constructTitlePacket(
          GenericTitlePacket.ActionType.SET_SUBTITLE, this.getProtocolVersion());
      subtitlePkt.setComponent(new ComponentHolder(
          this.getProtocolVersion(), translateMessage(title.subtitle())));
      connection.delayedWrite(subtitlePkt);

      GenericTitlePacket titlePkt = GenericTitlePacket.constructTitlePacket(
          GenericTitlePacket.ActionType.SET_TITLE, this.getProtocolVersion());
      titlePkt.setComponent(new ComponentHolder(
          this.getProtocolVersion(), translateMessage(title.title())));
      connection.delayedWrite(titlePkt);

      connection.flush();
    }
  }

  /**
   * Sends an individual part of a title to the player, such as main title, subtitle, or timing.
   *
   * @param part the part of the title to send
   * @param value the value to assign to that part
   * @param <T> the type of value (Component or Title.Times)
   */
  @SuppressWarnings("ConstantValue")
  @Override
  public <T> void sendTitlePart(final @NotNull TitlePart<T> part, final @NotNull T value) {
    if (part == null) {
      throw new NullPointerException("part");
    }

    if (value == null) {
      throw new NullPointerException("value");
    }

    if (this.getProtocolVersion().lessThan(ProtocolVersion.MINECRAFT_1_8)) {
      return;
    }

    if (part == TitlePart.TITLE) {
      GenericTitlePacket titlePkt = GenericTitlePacket.constructTitlePacket(
          GenericTitlePacket.ActionType.SET_TITLE, this.getProtocolVersion());
      titlePkt.setComponent(new ComponentHolder(
          this.getProtocolVersion(), translateMessage((Component) value)));
      connection.write(titlePkt);
    } else if (part == TitlePart.SUBTITLE) {
      GenericTitlePacket titlePkt = GenericTitlePacket.constructTitlePacket(
          GenericTitlePacket.ActionType.SET_SUBTITLE, this.getProtocolVersion());
      titlePkt.setComponent(new ComponentHolder(
          this.getProtocolVersion(), translateMessage((Component) value)));
      connection.write(titlePkt);
    } else if (part == TitlePart.TIMES) {
      Times times = (Times) value;
      GenericTitlePacket timesPkt = GenericTitlePacket.constructTitlePacket(
          GenericTitlePacket.ActionType.SET_TIMES, this.getProtocolVersion());
      timesPkt.setFadeIn((int) DurationUtils.toTicks(times.fadeIn()));
      timesPkt.setStay((int) DurationUtils.toTicks(times.stay()));
      timesPkt.setFadeOut((int) DurationUtils.toTicks(times.fadeOut()));
      connection.write(timesPkt);
    } else {
      throw new IllegalArgumentException("Title part " + part + " is not valid");
    }
  }

  /**
   * Clears the currently displayed title from the player's screen.
   */
  @Override
  public void clearTitle() {
    if (this.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      connection.write(GenericTitlePacket.constructTitlePacket(
          GenericTitlePacket.ActionType.HIDE, this.getProtocolVersion()));
    }
  }

  /**
   * Resets the title state for the player, removing all parts and timings.
   */
  @Override
  public void resetTitle() {
    if (this.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      connection.write(GenericTitlePacket.constructTitlePacket(
          GenericTitlePacket.ActionType.RESET, this.getProtocolVersion()));
    }
  }

  /**
   * Hides a boss bar from the player.
   *
   * @param bar the boss bar to hide
   */
  @Override
  public void hideBossBar(final @NonNull BossBar bar) {
    if (this.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_9)) {
      final VelocityBossBarImplementation impl = VelocityBossBarImplementation.get(bar);
      if (impl.viewerRemove(this)) {
        this.bossBars.remove(impl);
      }
    }
  }

  /**
   * Shows a boss bar to the player.
   *
   * @param bar the boss bar to show
   */
  @Override
  public void showBossBar(final @NonNull BossBar bar) {
    if (this.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_9)) {
      final VelocityBossBarImplementation impl = VelocityBossBarImplementation.get(bar);
      if (impl.viewerAdd(this)) {
        this.bossBars.add(impl);
      }
    }
  }

  /**
   * Creates a new connection request for the specified backend server.
   *
   * @param server the target server
   * @return a connection request builder
   */
  @Override
  public ConnectionRequestBuilder createConnectionRequest(final RegisteredServer server) {
    return new ConnectionRequestBuilderImpl(server, this.connectedServer);
  }

  private ConnectionRequestBuilder createConnectionRequest(final RegisteredServer server,
                                                           final @Nullable VelocityServerConnection previousConnection) {
    return new ConnectionRequestBuilderImpl(server, previousConnection);
  }

  /**
   * Gets the profile properties (e.g. skin textures) from the player's {@link GameProfile}.
   *
   * @return a list of profile properties
   */
  @Override
  public List<GameProfile.Property> getGameProfileProperties() {
    return this.profile.getProperties();
  }

  /**
   * Replaces the profile properties in the player's {@link GameProfile}.
   *
   * @param properties the new list of properties
   */
  @Override
  public void setGameProfileProperties(final List<GameProfile.Property> properties) {
    this.profile = profile.withProperties(Preconditions.checkNotNull(properties));
  }

  /**
   * Clears the tab list header and footer and sends the reset packet to the player.
   */
  @Override
  public void clearPlayerListHeaderAndFooter() {
    clearPlayerListHeaderAndFooterSilent();
    if (this.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      this.connection.write(HeaderAndFooterPacket.reset(this.getProtocolVersion()));
    }
  }

  /**
   * Clears the player list header and footer without sending an update to the client.
   *
   * <p>This is used internally when transitioning servers or resetting state
   * without modifying the visual appearance for the player immediately.</p>
   */
  public void clearPlayerListHeaderAndFooterSilent() {
    this.playerListHeader = Component.empty();
    this.playerListFooter = Component.empty();
  }

  /**
   * Returns the internal tab list used to manage player list entries for this player.
   *
   * <p>This implementation varies based on the client's protocol version.</p>
   *
   * @return the internal tab list
   */
  @Override
  public InternalTabList getTabList() {
    return tabList;
  }

  /**
   * Sets whether the disconnect event should remove the player from the Redis cache.
   *
   * @param remove Whether to remove the player or not.
   */
  public void setDontRemoveFromRedis(final boolean remove) {
    this.dontRemoveFromRedis = remove;
  }

  /**
   * Gets whether the disconnect event should remove the player from the Redis cache.
   *
   * @return Whether to remove the player or not.
   */
  public boolean isDontRemoveFromRedis() {
    return this.dontRemoveFromRedis;
  }

  /**
   * Disconnects the player from the proxy with the specified reason.
   *
   * <p>If called from outside the Netty event loop, the disconnect will be scheduled asynchronously.</p>
   *
   * @param reason the reason to display to the player on disconnect
   */
  @Override
  public void disconnect(final Component reason) {
    if (connection.eventLoop().inEventLoop()) {
      disconnect0(reason, false);
    } else {
      connection.eventLoop().execute(() -> disconnect0(reason, false));
    }
  }

  /**
   * Disconnects the player from the proxy.
   *
   * @param reason      the reason for disconnecting the player
   * @param duringLogin whether the disconnect happened during login
   */
  public void disconnect0(final Component reason, final boolean duringLogin) {
    Component translated = this.translateMessage(reason);

    if (server.getConfiguration().isLogPlayerDisconnections()) {
      logger.info(Component.text(this + " has disconnected: ").append(translated));
    }

    connection.closeWith(DisconnectPacket.create(translated, this.getProtocolVersion(), connection.getState()));
  }

  /**
   * Gets the server this player is currently connected to, if any.
   *
   * @return the connected {@link VelocityServerConnection}, or {@code null} if not connected
   */
  public @Nullable VelocityServerConnection getConnectedServer() {
    return connectedServer;
  }

  /**
   * Gets the server connection currently in flight (i.e., being attempted), if any.
   *
   * @return the in-flight {@link VelocityServerConnection}, or {@code null} if none
   */
  public @Nullable VelocityServerConnection getConnectionInFlight() {
    return connectionInFlight;
  }

  /**
   * Gets either the in-flight connection (if one exists), or the currently connected server.
   *
   * <p>This is useful for determining where packets or events should be routed
   * during a transition between servers.</p>
   *
   * @return the active or transitioning {@link VelocityServerConnection}, or {@code null} if neither is present
   */
  public VelocityServerConnection getConnectionInFlightOrConnectedServer() {
    return connectionInFlight != null ? connectionInFlight : connectedServer;
  }

  /**
   * Resets and clears the in-flight server connection, typically after it has completed or failed.
   *
   * <p>This should be called once a transition is finalized to avoid inconsistencies
   * in connection state tracking.</p>
   */
  public void resetInFlightConnection() {
    connectionInFlight = null;
  }

  /**
   * Handles unexpected disconnects.
   *
   * @param server    the server we disconnected from
   * @param throwable the exception
   * @param safe      whether we can safely reconnect to a new server
   */
  public void handleConnectionException(final RegisteredServer server, final Throwable throwable,
                                        final boolean safe) {
    if (!isActive()) {
      // If the connection is no longer active, it makes no sense to try and recover it.
      return;
    }

    if (throwable == null) {
      throw new NullPointerException("throwable");
    }

    Throwable wrapped = throwable;
    if (throwable instanceof CompletionException) {
      Throwable cause = throwable.getCause();
      if (cause != null) {
        wrapped = cause;
      }
    }

    Component friendlyError;
    if (connectedServer != null && connectedServer.getServerInfo().equals(server.getServerInfo())) {
      friendlyError = Component.translatable("velocity.error.connected-server-error",
          Argument.string("server", server.getServerInfo().getName()));
    } else {
      if (Boolean.getBoolean("velocity.suppress-connection-timeout-logs")) {
        logger.error("{}: unable to connect to server {}", this, server.getServerInfo().getName());
      } else {
        logger.error("{}: unable to connect to server {}", this, server.getServerInfo().getName(), wrapped);
      }

      friendlyError = Component.translatable("velocity.error.connecting-server-error",
          Argument.string("server", server.getServerInfo().getName()));
    }

    handleConnectionException(server, null, friendlyError.color(NamedTextColor.RED), safe);
  }

  /**
   * Handles unexpected disconnects.
   *
   * @param server     the server we disconnected from
   * @param disconnect the disconnect packet
   * @param safe       whether we can safely reconnect to a new server
   */
  public void handleConnectionException(final RegisteredServer server, final DisconnectPacket disconnect,
                                        final boolean safe) {
    if (!isActive()) {
      // If the connection is no longer active, it makes no sense to try and recover it.
      return;
    }

    Component disconnectReason = disconnect.getReason().getComponent();
    String plainTextReason = PASS_THRU_TRANSLATE.serialize(disconnectReason);
    if (connectedServer != null && connectedServer.getServerInfo().equals(server.getServerInfo())) {
      if (this.server.getConfiguration().isLogPlayerConnections()) {
        logger.info("{}: kicked from server {}: {}", this, server.getServerInfo().getName(), plainTextReason);
      }

      handleConnectionException(server, disconnectReason,
          Component.translatable("velocity.error.moved-to-new-server", NamedTextColor.RED)
              .arguments(
                  Argument.string("server", server.getServerInfo().getName()),
                  Argument.component("reason", disconnectReason)), safe);
    } else {
      if (this.server.getConfiguration().isLogPlayerConnections()) {
        logger.error("{}: disconnected while connecting to {}: {}", this,
            server.getServerInfo().getName(), plainTextReason);
      }

      handleConnectionException(server, disconnectReason,
          Component.translatable("velocity.error.cant-connect", NamedTextColor.RED)
              .arguments(
                  Argument.string("server", server.getServerInfo().getName()),
                  Argument.component("reason", disconnectReason)), safe);
    }
  }

  private void handleConnectionException(final RegisteredServer rs,
                                         final @Nullable Component kickReason, final Component friendlyReason,
                                         final boolean safe) {
    if (!isActive()) {
      // If the connection is no longer active, it makes no sense to try and recover it.
      return;
    }

    if (!safe) {
      // /!\ IT IS UNSAFE TO CONTINUE /!\
      //
      // This is usually triggered by a failed Forge handshake.
      disconnect(friendlyReason);
      return;
    }

    boolean kickedFromCurrent = connectedServer == null || connectedServer.getServer().equals(rs);
    ServerKickResult result;
    if (kickedFromCurrent) {
      Optional<RegisteredServer> next = getNextServerToTry(rs);
      result = next.map(RedirectPlayer::create).orElseGet(() -> DisconnectPlayer.create(friendlyReason));
    } else {
      // If we were kicked by going to another server, the connection should not be in flight
      if (connectionInFlight != null && connectionInFlight.getServer().equals(rs)) {
        resetInFlightConnection();
      }

      result = Notify.create(friendlyReason);
    }

    KickedFromServerEvent originalEvent = new KickedFromServerEvent(this, rs, kickReason,
        !kickedFromCurrent, result);
    handleKickEvent(originalEvent, friendlyReason, kickedFromCurrent);
  }

  private void handleKickEvent(final KickedFromServerEvent originalEvent, final Component friendlyReason,
                               final boolean kickedFromCurrent) {
    server.getEventManager().fire(originalEvent).thenAcceptAsync(event -> {
      // There can't be any connection in flight now.
      connectionInFlight = null;

      // Make sure we clear the current connected server as the connection is invalid.
      VelocityServerConnection previousConnection = connectedServer;
      if (kickedFromCurrent) {
        connectedServer = null;
      }

      if (!isActive()) {
        // If the connection is no longer active, it makes no sense to try and recover it.
        return;
      }

      switch (event.getResult()) {
        case final DisconnectPlayer res -> disconnect(res.getReasonComponent());
        case final RedirectPlayer res -> createConnectionRequest(res.getServer(), previousConnection).connect()
            .whenCompleteAsync((status, throwable) -> {
              if (throwable != null) {
                handleConnectionException(
                    status != null ? status.getAttemptedConnection() : res.getServer(), throwable,
                    true);
                return;
              }

              switch (status.getStatus()) {
                // Impossible/nonsensical cases
                case ALREADY_CONNECTED -> logger.error("{}: already connected to {}", this,
                      status.getAttemptedConnection().getServerInfo().getName());

                // Fatal case
                case CONNECTION_IN_PROGRESS, CONNECTION_CANCELLED -> {
                  Component fallbackMsg = res.getMessageComponent();
                  if (fallbackMsg == null) {
                    fallbackMsg = friendlyReason;
                  }

                  disconnect(status.getReasonComponent().orElse(fallbackMsg));
                }
                case SERVER_DISCONNECTED -> {
                  Component reason = status.getReasonComponent()
                      .orElse(ConnectionMessages.INTERNAL_SERVER_CONNECTION_ERROR);
                  handleConnectionException(res.getServer(),
                      DisconnectPacket.create(reason, getProtocolVersion(), connection.getState()),
                      ((Impl) status).isSafe());
                }
                case SUCCESS -> {
                  Component requestedMessage = res.getMessageComponent();

                  if (requestedMessage == null) {
                    requestedMessage = friendlyReason;
                  }

                  if (requestedMessage != Component.empty()) {
                    sendMessage(requestedMessage);
                  }

                  if (this.server.getConfiguration().getQueue().isQueueOnShutdown()) {
                    String targetServerName = originalEvent.getServer().getServerInfo().getName();

                    if (!this.server.getConfiguration().getQueue().getNoQueueServers().contains(targetServerName)) {
                      TextComponent kickMsg = (TextComponent) originalEvent.getServerKickReason().orElse(Component.empty());
                      ServerQueueStatus s = this.server.getQueueManager().getQueue(targetServerName);

                      // Checks if the kick reason is valid for a re-queue
                      // This is done to make sure players don't get constantly sent over and over again in a kick loop
                      boolean isValidReason = this.server.getConfiguration().getQueue().getBannedReason()
                          .stream()
                          .noneMatch(text -> containsString(kickMsg, text));

                      if (isValidReason && (!s.isPaused() || this.server.getConfiguration().getQueue().isAllowPausedQueueJoining())) {
                        s.queue(getUniqueId(),
                            getQueuePriority(targetServerName),
                            server.getQueueManager().isQueueEnabled() && hasPermission("velocity.queue.full.bypass"),
                            server.getQueueManager().isQueueEnabled() && hasPermission("velocity.queue.bypass")
                        );
                      }
                    }
                  }
                }
                default -> {
                }
                // The only remaining value is successful (no need to do anything!)
              }
            }, connection.eventLoop());
        case final Notify res -> {
          if (event.kickedDuringServerConnect() && previousConnection != null) {
            sendMessage(res.getMessageComponent());
          } else {
            disconnect(res.getMessageComponent());
          }
        }
        default ->
            // In case someone gets creative, assume we want to disconnect the player.
            disconnect(friendlyReason);
      }
    }, connection.eventLoop());
  }

  /**
   * Finds another server to attempt to log into if we were unexpectedly disconnected from the
   * server.
   *
   * @return the next server to try
   */
  public Optional<RegisteredServer> getNextServerToTry() {
    if (this.server.getMultiProxyHandler().getTransferringServers().containsKey(getUniqueId())) {
      return this.server.getServer(this.server.getMultiProxyHandler().getTransferringServers().get(getUniqueId()));
    }

    return this.getNextServerToTry(null);
  }

  /**
   * Finds another server to attempt to log into if we were unexpectedly disconnected from the
   * server.
   *
   * @param current the "current" server that the player is on, useful as an override
   * @return the next server to try
   */
  private Optional<RegisteredServer> getNextServerToTry(final @Nullable RegisteredServer current) {
    if (serversToTry == null || serversToTry.isEmpty()) {
      String virtualHostStr = getVirtualHost().map(InetSocketAddress::getHostString)
          .orElse("")
          .toLowerCase(Locale.ROOT);

      List<String> forcedHosts = server.getConfiguration().getForcedHosts().get(virtualHostStr);
      if (forcedHosts == null || forcedHosts.isEmpty()) {
        for (Map.Entry<String, List<String>> entry : server.getConfiguration().getForcedHosts().entrySet()) {
          String pattern = entry.getKey().toLowerCase(Locale.ROOT);
          if (pattern.startsWith("*.") && virtualHostStr.endsWith(pattern.substring(1))) {
            forcedHosts = entry.getValue();
            break;
          }
        }
      }

      if (forcedHosts != null && !forcedHosts.isEmpty()) {
        serversToTry = forcedHosts;
      } else {
        serversToTry = server.getConfiguration().getAttemptConnectionOrder();
      }
    }

    String strategy = server.getConfiguration().getDynamicFallbackFilter().toUpperCase(Locale.ROOT);
    Optional<RegisteredServer> selectedServer = Optional.empty();

    for (int i = tryIndex; i < serversToTry.size(); i++) {
      String toTryName = serversToTry.get(i);
      if ((connectedServer != null && hasSameName(connectedServer.getServer(), toTryName))
          || (connectionInFlight != null && hasSameName(connectionInFlight.getServer(), toTryName))
          || (current != null && hasSameName(current, toTryName))) {
        continue;
      }

      Optional<RegisteredServer> potentialServer = server.getServer(toTryName);
      if (potentialServer.isEmpty()) {
        continue;
      }

      RegisteredServer registeredServer = potentialServer.get();

      if (selectedServer.isEmpty()) {
        if (strategy.equalsIgnoreCase("FIRST_AVAILABLE")) {
          tryIndex = i;
          return Optional.of(registeredServer);
        }

        selectedServer = Optional.of(registeredServer);
        tryIndex = i;
      } else if (strategy.equalsIgnoreCase("MOST_POPULATED")) {
        if (registeredServer.getTotalPlayerCount() > selectedServer.get().getTotalPlayerCount()) {
          selectedServer = Optional.of(registeredServer);
          tryIndex = i;
        }
      } else if (strategy.equalsIgnoreCase("LEAST_POPULATED")) {
        if (registeredServer.getTotalPlayerCount() < selectedServer.get().getTotalPlayerCount()) {
          selectedServer = Optional.of(registeredServer);
          tryIndex = i;
        }
      }
    }

    return selectedServer;
  }

  private static boolean hasSameName(final RegisteredServer server, final String name) {
    return server.getServerInfo().getName().equalsIgnoreCase(name);
  }

  /**
   * Sets the player's new connected server and clears the in-flight connection.
   *
   * @param serverConnection the new server connection
   */
  public void setConnectedServer(final @Nullable VelocityServerConnection serverConnection) {
    this.connectedServer = serverConnection;
    this.tryIndex = 0;

    if (serverConnection == connectionInFlight) {
      connectionInFlight = null;
    }
  }

  /**
   * Sends a signal to reset the current connection phase back to the beginning of
   * the legacy Forge handshake process.
   *
   * <p>This is typically used when reconnecting to a Forge server or when restarting
   * the modded handshake flow due to plugin logic or player redirection.</p>
   */
  public void sendLegacyForgeHandshakeResetPacket() {
    connectionPhase.resetConnectionPhase(this);
  }

  private MinecraftConnection ensureBackendConnection() {
    VelocityServerConnection sc = this.connectedServer;
    if (sc == null) {
      throw new IllegalStateException("No backend connection");
    }

    MinecraftConnection mc = sc.getConnection();
    if (mc == null) {
      throw new IllegalStateException("Backend connection is not connected to a server");
    }

    return mc;
  }

  /**
   * Disconnects any ongoing or established connections. This
   * method ensures that any connection currently in flight or any
   * connected server is properly disconnected to clean up resources and
   * prevent potential memory leaks and is made public to "fix" the ongoing
   * unexpected disconnection error for some users, on top of making it easily accessible.
   */
  public void teardown() {
    if (connectionInFlight != null) {
      connectionInFlight.disconnect();
    }

    if (connectedServer != null) {
      connectedServer.disconnect();
    }

    Optional<Player> connectedPlayer = server.getPlayer(this.getUniqueId());
    server.unregisterConnection(this);

    DisconnectEvent.LoginStatus status;
    if (connectedPlayer.isPresent()) {
      if (connectedPlayer.get().getCurrentServer().isEmpty()) {
        status = LoginStatus.PRE_SERVER_JOIN;
      } else {
        status = connectedPlayer.get() == this ? LoginStatus.SUCCESSFUL_LOGIN : LoginStatus.CONFLICTING_LOGIN;
      }
    } else {
      status = connection.isKnownDisconnect() ? LoginStatus.CANCELLED_BY_PROXY : LoginStatus.CANCELLED_BY_USER;
    }

    DisconnectEvent event = new DisconnectEvent(this, status);
    server.getEventManager().fire(event).whenComplete((val, ex) -> {
      if (ex == null) {
        this.teardownFuture.complete(null);
      } else {
        this.teardownFuture.completeExceptionally(ex);
      }
    });
  }

  /**
   * Returns a {@link CompletableFuture} that completes when the player
   * has been fully torn down and the {@link DisconnectEvent} has been fired.
   *
   * <p>This can be used to wait for all teardown logic to finish before proceeding with
   * further cleanup or dependent operations.</p>
   *
   * @return the teardown future
   */
  public CompletableFuture<Void> getTeardownFuture() {
    return teardownFuture;
  }

  /**
   * Get instance of itself for other classes to retrieve.
   *
   * @return The current labeled class so others can retrieve.
   */
  public ConnectedPlayer get() {
    return this;
  }

  /**
   * Returns a string representation of the player, including their username and optionally their IP address.
   *
   * @return a string identifying the connected player
   */
  @Override
  public String toString() {
    final boolean isPlayerAddressLoggingEnabled = server.getConfiguration().isPlayerAddressLoggingEnabled();
    final String playerIp = isPlayerAddressLoggingEnabled ? getRemoteAddress().toString() : "<ip address withheld>";
    return "[connected player] " + profile.getName() + " (" + playerIp + ")";
  }

  /**
   * Evaluates the given permission string using the current {@link PermissionFunction}.
   *
   * @param permission the permission to check
   * @return the tristate result of the permission evaluation
   */
  @Override
  public Tristate getPermissionValue(final String permission) {
    return permissionFunction.getPermissionValue(permission);
  }

  /**
   * Sends a plugin message to the client using a raw byte array.
   *
   * @param identifier the plugin message channel
   * @param data the payload data to send
   * @return {@code true} if the message was sent
   */
  @Override
  public boolean sendPluginMessage(final @NotNull ChannelIdentifier identifier, final byte @NotNull [] data) {
    Preconditions.checkNotNull(identifier, "identifier");
    Preconditions.checkNotNull(data, "data");
    final PluginMessagePacket message = new PluginMessagePacket(identifier.getId(),
            Unpooled.wrappedBuffer(data));
    connection.write(message);
    return true;
  }

  /**
   * Sends a plugin message to the client using a {@link PluginMessageEncoder}.
   *
   * @param identifier the plugin message channel
   * @param dataEncoder the encoder for the payload
   * @return {@code true} if the message was sent
   */
  @Override
  public boolean sendPluginMessage(final @NotNull ChannelIdentifier identifier, final @NotNull PluginMessageEncoder dataEncoder) {
    requireNonNull(identifier);
    requireNonNull(dataEncoder);
    final ByteBuf buf = Unpooled.buffer();
    final ByteBufDataOutput dataOutput = new ByteBufDataOutput(buf);
    dataEncoder.encode(dataOutput);
    if (buf.isReadable()) {
      final PluginMessagePacket message = new PluginMessagePacket(identifier.getId(), buf);
      connection.write(message);
      return true;
    } else {
      buf.release();
      return false;
    }
  }

  /**
   * Gets the brand string reported by the client (e.g., "vanilla", "forge").
   *
   * @return the client brand, or {@code null} if not reported
   */
  @Override
  @Nullable
  public String getClientBrand() {
    return clientBrand;
  }

  /**
   * Sets the brand string reported by the client.
   *
   * @param clientBrand the client brand string
   */
  void setClientBrand(final @Nullable String clientBrand) {
    this.clientBrand = clientBrand;
  }

  /**
   * Plays a sound for the player, routed through the current backend when supported.
   *
   * <p>No-op for unsupported protocol states/versions or mismatched emitters.</p>
   *
   * @param sound the sound to play
   * @param emitter the sound emitter (self or another player on the same server)
   */
  @Override
  public void playSound(final @NotNull Sound sound, final @NotNull Sound.Emitter emitter) {
    Preconditions.checkNotNull(sound, "sound");
    Preconditions.checkNotNull(emitter, "emitter");
    VelocityServerConnection soundTargetServerConn = getConnectedServer();
    if (getProtocolVersion().lessThan(ProtocolVersion.MINECRAFT_1_19_3)
        || connection.getState() != StateRegistry.PLAY
        || soundTargetServerConn == null
        || (sound.source() == Sound.Source.UI && getProtocolVersion().lessThan(ProtocolVersion.MINECRAFT_1_21_5))) {
      return;
    }

    VelocityServerConnection soundEmitterServerConn;
    if (emitter == Sound.Emitter.self()) {
      soundEmitterServerConn = soundTargetServerConn;
    } else if (emitter instanceof ConnectedPlayer player) {
      if ((soundEmitterServerConn = player.getConnectedServer()) == null) {
        return;
      }

      if (!soundEmitterServerConn.getServer().equals(soundTargetServerConn.getServer())) {
        return;
      }
    } else {
      return;
    }

    connection.write(new ClientboundSoundEntityPacket(sound, null, soundEmitterServerConn.getEntityId()));
  }

  /**
   * Stops a sound on the client when supported.
   *
   * <p>No-op for unsupported protocol states/versions.</p>
   *
   * @param stop the stop instruction
   */
  @Override
  public void stopSound(final @NotNull SoundStop stop) {
    Preconditions.checkNotNull(stop, "stop");
    if (getProtocolVersion().lessThan(ProtocolVersion.MINECRAFT_1_19_3)
        || connection.getState() != StateRegistry.PLAY
        || (stop.source() == Sound.Source.UI
            && getProtocolVersion().lessThan(ProtocolVersion.MINECRAFT_1_21_5))) {
      return;
    }

    connection.write(new ClientboundStopSoundPacket(stop));
  }

  /**
   * Transfers the player to a new host address, using the Transfer packet (1.20.5+).
   *
   * @param address the address to transfer the player to
   */
  @Override
  public void transferToHost(final @NotNull InetSocketAddress address) {
    Preconditions.checkNotNull(address);
    Preconditions.checkArgument(
        this.getProtocolVersion().compareTo(ProtocolVersion.MINECRAFT_1_20_5) >= 0,
        "Player version must be 1.20.5 to be able to transfer to another host");

    server.getEventManager().fire(new PreTransferEvent(this, address)).thenAccept((event) -> {
      if (event.getResult().isAllowed()) {
        InetSocketAddress resultedAddress = event.getResult().address();
        if (resultedAddress == null) {
          resultedAddress = address;
        }

        connection.write(new TransferPacket(resultedAddress.getHostName(), resultedAddress.getPort()));
      }
    });
  }

  /**
   * Stores a client cookie (Minecraft 1.20.5+), optionally firing a {@link CookieStoreEvent}.
   *
   * @param key the key associated with the cookie
   * @param data the cookie payload
   */
  @Override
  public void storeCookie(final Key key, final byte[] data) {
    Preconditions.checkNotNull(key);
    Preconditions.checkNotNull(data);
    Preconditions.checkArgument(
        this.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_5),
        "Player version must be at least 1.20.5 to be able to store cookies");

    if (connection.getState() != StateRegistry.PLAY
        && connection.getState() != StateRegistry.CONFIG) {
      throw new IllegalStateException("Can only store cookie in CONFIGURATION or PLAY protocol");
    }

    server.getEventManager().fire(new CookieStoreEvent(this, key, data))
        .thenAcceptAsync(event -> {
          if (event.getResult().isAllowed()) {
            final Key resultedKey = event.getResult().getKey() == null
                ? event.getOriginalKey() : event.getResult().getKey();
            final byte[] resultedData = event.getResult().getData() == null
                ? event.getOriginalData() : event.getResult().getData();

            connection.write(new ClientboundStoreCookiePacket(resultedKey, resultedData));
          }
        }, connection.eventLoop());
  }

  /**
   * Requests a cookie from the client (Minecraft 1.20.5+), optionally firing a {@link CookieRequestEvent}.
   *
   * @param key the key of the cookie to request
   */
  @Override
  public void requestCookie(final Key key) {
    Preconditions.checkNotNull(key);
    Preconditions.checkArgument(
        this.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_5),
        "Player version must be at least 1.20.5 to be able to retrieve cookies");

    server.getEventManager().fire(new CookieRequestEvent(this, key))
        .thenAcceptAsync(event -> {
          if (event.getResult().isAllowed()) {
            final Key resultedKey = event.getResult().getKey() == null
                ? event.getOriginalKey() : event.getResult().getKey();

            connection.write(new ClientboundCookieRequestPacket(resultedKey));
          }
        }, connection.eventLoop());
  }

  /**
   * Sets the server links displayed in the client's escape menu (Minecraft 1.21+).
   *
   * @param links the list of server links to send
   */
  @Override
  public void setServerLinks(final @NotNull List<ServerLink> links) {
    Preconditions.checkNotNull(links, "links");
    Preconditions.checkArgument(
        this.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_21),
        "Player version must be at least 1.21 to be able to set server links");

    if (connection.getState() != StateRegistry.PLAY && connection.getState() != StateRegistry.CONFIG) {
      throw new IllegalStateException("Can only send server links in CONFIGURATION or PLAY protocol");
    }

    connection.write(new ClientboundServerLinksPacket(links.stream()
        .map(l -> new ClientboundServerLinksPacket.ServerLink(
            l.getBuiltInType().map(Enum::ordinal).orElse(-1),
            l.getCustomLabel()
                .map(c -> new ComponentHolder(getProtocolVersion(), translateMessage(c)))
                .orElse(null),
            l.getUrl().toString()))
        .toList()));
  }

  /**
   * Returns the player's priority level for queueing into the specified server.
   *
   * <p>Priority is based on permissions such as {@code velocity.queue.priority.<server>.<level>}.</p>
   *
   * @param serverName the name of the target server
   * @return the priority level, or {@code 0} if none
   */
  @Override
  public int getQueuePriority(final String serverName) {
    if (!server.getQueueManager().isQueueEnabled()) {
      return 0;
    }

    // First check for global permissions (higher priority for staff members)
    for (int i = 100; i > 0; i--) {
      if (hasPermission("velocity.queue.priority.all." + i)) {
        return i;
      }
    }

    // Then check for server-specific permissions (lower priority)
    for (int i = 100; i > 0; i--) {
      if (hasPermission("velocity.queue.priority." + serverName + "." + i)) {
        return i;
      }
    }

    return 0;
  }

  /**
   * Adds the given chat completions to the player's client (1.19.1+).
   *
   * @param completions the completions to add
   */
  @Override
  public void addCustomChatCompletions(final @NotNull Collection<String> completions) {
    Preconditions.checkNotNull(completions, "completions");
    this.sendCustomChatCompletionPacket(completions, PlayerChatCompletionPacket.Action.ADD);
  }

  /**
   * Removes the given chat completions from the player's client (1.19.1+).
   *
   * @param completions the completions to remove
   */
  @Override
  public void removeCustomChatCompletions(final @NotNull Collection<String> completions) {
    Preconditions.checkNotNull(completions, "completions");
    this.sendCustomChatCompletionPacket(completions, PlayerChatCompletionPacket.Action.REMOVE);
  }

  /**
   * Sets the full list of custom chat completions for the player's client (1.19.1+).
   *
   * @param completions the completions to set
   */
  @Override
  public void setCustomChatCompletions(final @NotNull Collection<String> completions) {
    Preconditions.checkNotNull(completions, "completions");
    this.sendCustomChatCompletionPacket(completions, PlayerChatCompletionPacket.Action.SET);
  }

  private void sendCustomChatCompletionPacket(final @NotNull Collection<String> completions,
                                              final PlayerChatCompletionPacket.Action action) {
    if (connection.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_19_1)) {
      connection.write(new PlayerChatCompletionPacket(completions.toArray(new String[0]), action));
    }
  }

  /**
   * Spoofs a chat message as if the player had typed it.
   *
   * @param input the message to spoof
   */
  @Override
  public void spoofChatInput(final String input) {
    Preconditions.checkArgument(input.length() <= LegacyChatPacket.MAX_SERVERBOUND_MESSAGE_LENGTH,
        "input cannot be greater than " + LegacyChatPacket.MAX_SERVERBOUND_MESSAGE_LENGTH
            + " characters in length");
    if (getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_19)) {
      ChatBuilderV2 message = getChatBuilderFactory().builder().asPlayer(this).message(input);
      this.chatQueue.queuePacket(chatState -> {
        message.setTimestamp(chatState.lastTimestamp);
        message.setLastSeenMessages(chatState.createLastSeen());
        return message.toServer();
      });
    } else {
      ensureBackendConnection().write(getChatBuilderFactory().builder()
          .asPlayer(this).message(input).toServer());
    }
  }

  /**
   * Get the ResourcePackHandler corresponding to the player's version.
   *
   * @return the ResourcePackHandler of this player
   */
  public ResourcePackHandler resourcePackHandler() {
    return this.resourcePackHandler;
  }

  /**
   * Sends a legacy resource pack offer to the player by URL (deprecated).
   *
   * @param url the resource pack URL
   */
  @Override
  @Deprecated
  public void sendResourcePack(final String url) {
    sendResourcePackOffer(new VelocityResourcePackInfo.BuilderImpl(url).build());
  }

  /**
   * Sends a legacy resource pack offer to the player with hash (deprecated).
   *
   * @param url the resource pack URL
   * @param hash the SHA-1 hash of the pack
   */
  @Override
  @Deprecated
  public void sendResourcePack(final String url, final byte[] hash) {
    sendResourcePackOffer(new VelocityResourcePackInfo.BuilderImpl(url).setHash(hash).build());
  }

  /**
   * Sends a resource pack offer to the player.
   *
   * @param packInfo the resource pack metadata
   */
  @Override
  public void sendResourcePackOffer(final ResourcePackInfo packInfo) {
    this.resourcePackHandler.checkAlreadyAppliedPack(packInfo.getHash());
    if (this.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      Preconditions.checkNotNull(packInfo, "packInfo");
      this.resourcePackHandler.queueResourcePack(packInfo);
    }
  }

  /**
   * Sends a multipack resource request to the player (1.20+).
   *
   * @param request the resource pack request
   */
  @Override
  public void sendResourcePacks(final @NotNull ResourcePackRequest request) {
    if (this.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      Preconditions.checkNotNull(request, "packRequest");
      this.resourcePackHandler.queueResourcePack(request);
    }
  }

  /**
   * Clears all resource packs from the player's client (1.20.3+).
   */
  @Override
  public void clearResourcePacks() {
    if (this.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_3)) {
      connection.write(new RemoveResourcePackPacket());
      this.resourcePackHandler.clearAppliedResourcePacks();
    }
  }

  /**
   * Removes specific resource packs by UUID.
   *
   * @param id the first pack UUID to remove
   * @param others optional additional UUIDs
   */
  @Override
  public void removeResourcePacks(final @NotNull UUID id, final @NotNull UUID @NotNull... others) {
    if (this.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_3)) {
      Preconditions.checkNotNull(id, "packUUID");
      if (this.resourcePackHandler.remove(id)) {
        connection.write(new RemoveResourcePackPacket(id));
      }

      for (final UUID other : others) {
        if (this.resourcePackHandler.remove(other)) {
          connection.write(new RemoveResourcePackPacket(other));
        }
      }
    }
  }

  /**
   * Removes all packs in the given {@link ResourcePackRequest}.
   *
   * @param request the request object
   */
  @Override
  public void removeResourcePacks(final @NotNull ResourcePackRequest request) {
    for (final net.kyori.adventure.resource.ResourcePackInfo resourcePackInfo : request.packs()) {
      removeResourcePacks(resourcePackInfo.id());
    }
  }

  /**
   * Removes all packs in the given {@link ResourcePackRequestLike}.
   *
   * @param request the request object
   */
  @Override
  public void removeResourcePacks(final @NotNull ResourcePackRequestLike request) {
    removeResourcePacks(request.asResourcePackRequest());
  }

  /**
   * Removes the given resource packs individually.
   *
   * @param request the first pack to remove
   * @param others additional packs to remove
   */
  @Override
  public void removeResourcePacks(final @NotNull ResourcePackInfoLike request,
                                  final @NotNull ResourcePackInfoLike @NotNull... others) {
    removeResourcePacks(request.asResourcePackInfo().id());
    for (final ResourcePackInfoLike other : others) {
      removeResourcePacks(other.asResourcePackInfo().id());
    }
  }

  /**
   * Gets the currently applied resource pack (deprecated).
   *
   * @return the applied pack, or {@code null} if none
   */
  @Override
  @Deprecated
  public @Nullable ResourcePackInfo getAppliedResourcePack() {
    return this.resourcePackHandler.getFirstAppliedPack();
  }

  /**
   * Gets the next resource pack to be applied (deprecated).
   *
   * @return the pending pack, or {@code null} if none
   */
  @Override
  @Deprecated
  public @Nullable ResourcePackInfo getPendingResourcePack() {
    return this.resourcePackHandler.getFirstPendingPack();
  }

  /**
   * Gets the list of applied resource packs for this player.
   *
   * @return the collection of applied packs
   */
  @Override
  public @NotNull Collection<ResourcePackInfo> getAppliedResourcePacks() {
    return this.resourcePackHandler.getAppliedResourcePacks();
  }

  /**
   * Gets the list of resource packs that are pending application.
   *
   * @return the collection of pending packs
   */
  @Override
  public @NotNull Collection<ResourcePackInfo> getPendingResourcePacks() {
    return this.resourcePackHandler.getPendingResourcePacks();
  }

  /**
   * Sends a {@link KeepAlivePacket} packet to the player with a random ID.
   * Velocity will ignore the response as it will not match
   * the ID last sent by the server.
   */
  public void sendKeepAlive() {
    if (connection.getState() == StateRegistry.PLAY || connection.getState() == StateRegistry.CONFIG) {
      KeepAlivePacket keepAlive = new KeepAlivePacket();
      keepAlive.setRandomId(ThreadLocalRandom.current().nextLong());
      connection.write(keepAlive);
    }
  }

  /**
   * Forwards a received {@link KeepAlivePacket} to the appropriate backend server.
   *
   * <p>The packet is first attempted against the currently connected server; if that
   * fails to match a pending ping, it is then attempted against the in-flight connection.</p>
   *
   * @param packet the keepalive packet received from the client
   * @return {@code true} if the packet was forwarded to a backend server, {@code false} otherwise
   */
  public boolean forwardKeepAlive(final KeepAlivePacket packet) {
    if (!this.sendKeepAliveToBackend(connectedServer, packet)) {
      return this.sendKeepAliveToBackend(connectionInFlight, packet);
    }

    return false;
  }

  private boolean sendKeepAliveToBackend(final @Nullable VelocityServerConnection serverConnection, final @NotNull KeepAlivePacket packet) {
    if (serverConnection != null) {
      final Long sentTime = serverConnection.getPendingPings().remove(packet.getRandomId());
      if (sentTime != null) {
        final MinecraftConnection smc = serverConnection.getConnection();
        if (smc != null) {
          setPing(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sentTime));
          smc.write(packet);
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Switches the connection to the client into config state.
   */
  public void switchToConfigState() {
    server.getEventManager().fire(new PlayerEnterConfigurationEvent(this, getConnectionInFlightOrConnectedServer()))
        .completeOnTimeout(null, 5, TimeUnit.SECONDS).thenRunAsync(() -> {
          // if the connection was closed earlier, there is a risk that the player is no longer connected
          if (!connection.getChannel().isActive()) {
            return;
          }

          if (bundleHandler.isInBundleSession()) {
            bundleHandler.toggleBundleSession();
            connection.write(BundleDelimiterPacket.INSTANCE);
          }

          connection.write(StartUpdatePacket.INSTANCE);
          connection.pendingConfigurationSwitch = true;
          connection.getChannel().pipeline().get(MinecraftEncoder.class).setState(StateRegistry.CONFIG);
          // Make sure we don't send any play packets to the player after update start
          connection.addPlayPacketQueueHandler();
        }, connection.eventLoop()).exceptionally((ex) -> {
          logger.error("Error switching player connection to config state", ex);
          return null;
        });
  }

  /**
   * Gets the current "phase" of the connection, mostly used for tracking modded negotiation for
   * legacy forge servers and provides methods for performing phase-specific actions.
   *
   * @return The {@link ClientConnectionPhase}
   */
  public ClientConnectionPhase getPhase() {
    return connectionPhase;
  }

  /**
   * Sets the current "phase" of the connection. See {@link #getPhase()}
   *
   * @param connectionPhase The {@link ClientConnectionPhase}
   */
  public void setPhase(final ClientConnectionPhase connectionPhase) {
    this.connectionPhase = connectionPhase;
  }

  /**
   * Return all the plugin message channels that registered by client.
   *
   * @return the channels
   */
  public Collection<ChannelIdentifier> getClientsideChannels() {
    return clientsideChannels;
  }

  /**
   * Returns the {@link IdentifiedKey} used by the player for secure message signing,
   * if available.
   *
   * <p>This key is exchanged during the login phase in modern Minecraft clients
   * to verify chat messages and ensure authenticity.</p>
   *
   * @return the player's identified key, or {@code null} if not available
   */
  @Override
  public @Nullable IdentifiedKey getIdentifiedKey() {
    return playerKey;
  }

  /**
   * Gets the current {@link ProtocolState} of the player's connection.
   *
   * <p>This represents the phase of the connection, such as HANDSHAKE, CONFIGURATION, or PLAY.</p>
   *
   * @return the current protocol state
   */
  @Override
  public ProtocolState getProtocolState() {
    return connection.getState().toProtocolState();
  }

  /**
   * Gets the {@link HandshakeIntent} that the player sent when initiating the connection.
   *
   * <p>This intent identifies whether the player intended to login or request server status.</p>
   *
   * @return the handshake intent
   */
  @Override
  public HandshakeIntent getHandshakeIntent() {
    return handshakeIntent;
  }

  /**
   * Returns the {@link BossBarManager} responsible for handling boss bar
   * state and packet suppression for this player.
   *
   * <p>The manager tracks boss bars across server switches and prevents
   * sending update packets during login/config phases that would otherwise
   * disconnect clients (1.20.2+).</p>
   *
   * @return the boss bar manager for this player
   */
  public BossBarManager getBossBarManager() {
    return bossBarManager;
  }

  private final class ConnectionRequestBuilderImpl implements ConnectionRequestBuilder {

    /**
     * The {@link RegisteredServer} the player is attempting to connect to.
     */
    private final RegisteredServer toConnect;

    /**
     * The previously connected {@link VelocityRegisteredServer}, if any.
     *
     * <p>This is used to fire {@link com.velocitypowered.api.event.player.ServerPreConnectEvent}
     * with the correct context and to track switching from one server to another.</p>
     */
    private final @Nullable VelocityRegisteredServer previousServer;

    ConnectionRequestBuilderImpl(final RegisteredServer toConnect,
                                 final @Nullable VelocityServerConnection previousConnection) {
      this.toConnect = Preconditions.checkNotNull(toConnect, "info");
      this.previousServer = previousConnection == null ? null : previousConnection.getServer();
    }

    @Override
    public RegisteredServer getServer() {
      return toConnect;
    }

    private Optional<ConnectionRequestBuilder.Status> checkServer(final RegisteredServer server) {
      Preconditions.checkArgument(server instanceof VelocityRegisteredServer, "Not a valid Velocity server.");
      if (connectionInFlight != null || (connectedServer != null && !connectedServer.hasCompletedJoin())) {
        return Optional.of(ConnectionRequestBuilder.Status.CONNECTION_IN_PROGRESS);
      }

      if (connectedServer != null && connectedServer.getServer().getServerInfo().equals(server.getServerInfo())) {
        return Optional.of(ALREADY_CONNECTED);
      }

      return Optional.empty();
    }

    private CompletableFuture<Optional<Status>> getInitialStatus() {
      return CompletableFuture.supplyAsync(() -> checkServer(toConnect), connection.eventLoop());
    }

    private CompletableFuture<Impl> internalConnect() {
      return this.getInitialStatus().thenCompose(initialCheck -> {
        if (initialCheck.isPresent()) {
          return completedFuture(plainResult(initialCheck.get(), toConnect));
        }

        ServerPreConnectEvent event = new ServerPreConnectEvent(ConnectedPlayer.this, toConnect, previousServer);
        return server.getEventManager().fire(event).thenComposeAsync(newEvent -> {
          Optional<RegisteredServer> newDest = newEvent.getResult().getServer();
          if (newDest.isEmpty()) {
            return completedFuture(plainResult(ConnectionRequestBuilder.Status.CONNECTION_CANCELLED, toConnect));
          }

          RegisteredServer realDestination = newDest.get();
          Optional<ConnectionRequestBuilder.Status> check = checkServer(realDestination);
          if (check.isPresent()) {
            return completedFuture(plainResult(check.get(), realDestination));
          }

          // Check if the player's version is compatible with the server's minimum version
          if (checkVersionCompatibility(realDestination)) {
            return completedFuture(plainResult(ConnectionRequestBuilder.Status.CONNECTION_CANCELLED, realDestination));
          }

          VelocityRegisteredServer vrs = (VelocityRegisteredServer) realDestination;
          VelocityServerConnection con = new VelocityServerConnection(vrs, previousServer, ConnectedPlayer.this, server);
          connectionInFlight = con;
          return con.connect().whenCompleteAsync((result, exception) -> {
            if (result != null && !result.isSuccessful() && !result.isSafe()) {
              result.getReasonComponent()
                  .ifPresent(reason -> handleConnectionException(result.getAttemptedConnection(),
                      DisconnectPacket.create(reason, getProtocolVersion(), connection.getState()), false));
            }
            this.resetIfInFlightIs(con);
          }, connection.eventLoop());
        }, connection.eventLoop());
      });
    }

    private void resetIfInFlightIs(final VelocityServerConnection establishedConnection) {
      if (establishedConnection == connectionInFlight) {
        resetInFlightConnection();
      }
    }

    @Override
    public CompletableFuture<Result> connect() {
      return this.internalConnect().whenCompleteAsync((status, throwable) -> {
        if (status != null && !status.isSuccessful()) {
          if (!status.isSafe()) {
            handleConnectionException(status.getAttemptedConnection(), throwable, false);
          }
        } else if (status != null && status.isSuccessful() && status.isSafe()) {
          if (server.getConfiguration().getQueue().isRemovePlayerOnServerSwitch()) {
            server.getQueueManager().removeFromAll(get());
          }
        }

        // Optionals cannot be null in this instance
        final Component reason = requireNonNull(status).getReasonComponent()
            .orElse(ConnectionMessages.INTERNAL_SERVER_CONNECTION_ERROR);

        if (server.getQueueManager().isQueueEnabled()) {
          for (String r : server.getConfiguration().getQueue().getBannedReason()) {
            if (reason.contains(Component.text(r))) {
              server.getQueueManager().removeFromAll(get());
            }
          }
        }
      }, connection.eventLoop()).thenApply(x -> x);
    }

    @Override
    public CompletableFuture<Boolean> connectWithIndication() {
      return internalConnect().whenCompleteAsync((status, throwable) -> {
        if (throwable != null) {
          // TODO: The exception handling from this is not very good. Find a better way.
          handleConnectionException(status != null ? status.getAttemptedConnection() : toConnect,
              throwable, true);
          return;
        }

        switch (status.getStatus()) {
          case ALREADY_CONNECTED -> sendMessage(ConnectionMessages.ALREADY_CONNECTED);
          case CONNECTION_IN_PROGRESS -> sendMessage(ConnectionMessages.IN_PROGRESS);
          case CONNECTION_CANCELLED -> {
            // Ignored; the plugin probably already handled this.
          }
          case SERVER_DISCONNECTED -> {
            final Component reason = status.getReasonComponent()
                    .orElse(ConnectionMessages.INTERNAL_SERVER_CONNECTION_ERROR);

            if (connectedServer == null && connection.getState() == StateRegistry.CONFIG) {
              connection.closeWith(DisconnectPacket.create(reason, getProtocolVersion(), connection.getState()));
            } else {
              handleConnectionException(toConnect,
                      DisconnectPacket.create(reason, getProtocolVersion(), connection.getState()), status.isSafe());
            }

            TextComponent textComponent = (TextComponent) reason;
            if (server.getQueueManager().isQueueEnabled()) {
              for (String r : server.getConfiguration().getQueue().getBannedReason()) {
                if (containsString(textComponent, r)) {
                  server.getQueueManager().removeFromAll(get());
                }
              }
            }
          }
          default -> {
            // In this case, the default handler removes the user on server switch.
            if (server.getConfiguration().getQueue().isRemovePlayerOnServerSwitch()) {
              server.getQueueManager().removeFromAll(get());
            }
          }
        }
      }, connection.eventLoop()).thenApply(Result::isSuccessful);
    }

    @Override
    public void fireAndForget() {
      connectWithIndication();
    }
  }

  private static boolean containsString(final TextComponent component, final String searchString) {
    if (component.content().contains(searchString)) {
      return true;
    }

    // Recursively check children components
    for (Component child : component.children()) {
      if (child instanceof TextComponent textChild) {
        if (containsString(textChild, searchString)) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Checks if the player's protocol version is compatible with the server's minimum version requirement
   * and modern forwarding compatibility.
   *
   * @param server the server to check compatibility with
   * @return {@code true} if the player's version is compatible, {@code false} otherwise
   */
  public boolean checkVersionCompatibility(final RegisteredServer server) {
    String serverName = server.getServerInfo().getName();
    String serverMinimumVersion = ConnectedPlayer.this.server.getConfiguration().getMinimumVersionForServer(serverName);
    
    ProtocolVersion minimumProtocolVersion = ProtocolVersion.getVersionByName(serverMinimumVersion);
    ProtocolVersion maximumProtocolVersion = ProtocolVersion.MAXIMUM_VERSION;
    ProtocolVersion clientProtocolVersion = getProtocolVersion();

    // Compare the client's protocol version with the server's minimum required version
    if (clientProtocolVersion.lessThan(minimumProtocolVersion)
        || clientProtocolVersion.greaterThan(maximumProtocolVersion)) {
      // Send a message to the player instead of disconnecting them from the proxy
      sendMessage(Component.translatable("velocity.error.modern-forwarding-needs-new-client", NamedTextColor.RED)
          .arguments(
              Argument.string("min", serverMinimumVersion),
              Argument.string("max", ProtocolVersion.MAXIMUM_VERSION.getMostRecentSupportedVersion())));
      return true;
    }

    // Check if the server uses modern forwarding and the client is too old
    PlayerInfoForwarding serverForwardingMode = ((VelocityRegisteredServer) server).getConfiguredPlayerInfoForwarding();
    if (serverForwardingMode == PlayerInfoForwarding.MODERN && clientProtocolVersion.lessThan(ProtocolVersion.MINECRAFT_1_13)) {
      // Disconnect the player with an appropriate message
      disconnect(Component.translatable("velocity.error.modern-forwarding-needs-new-client", NamedTextColor.RED)
          .arguments(
              Argument.string("min", "1.13"),
              Argument.string("max", ProtocolVersion.MAXIMUM_VERSION.getMostRecentSupportedVersion())));
      return true;
    }

    return false;
  }
}
