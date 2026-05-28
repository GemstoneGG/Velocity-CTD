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

package com.velocitypowered.proxy.connection.client;

import static com.velocityctd.proxy.permission.PermissionResolverAdapterFactory.createPermissionResolverAdapter;
import static com.velocitypowered.api.proxy.ConnectionRequestBuilder.Status.ALREADY_CONNECTED;
import static com.velocitypowered.proxy.connection.PlayerDataForwarding.LEGACY_MODERN_FORWARDING;
import static com.velocitypowered.proxy.connection.util.ConnectionRequestResults.plainResult;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;

import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.velocityctd.api.permission.PermissionResolver;
import com.velocityctd.api.queue.QueueState;
import com.velocityctd.proxy.permission.PermissionUtils;
import com.velocityctd.proxy.queue.VelocityQueue;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.command.PlayerAvailableCommandsEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
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
import com.velocitypowered.api.proxy.crypto.IdentifiedKey;
import com.velocitypowered.api.proxy.crypto.KeyIdentifiable;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.PluginMessageEncoder;
import com.velocitypowered.api.proxy.player.PlayerSettings;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import com.velocitypowered.api.proxy.server.PlayerInfoForwarding;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.api.util.ModInfo;
import com.velocitypowered.api.util.ServerLink;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.adventure.ClickCallbackManager;
import com.velocitypowered.proxy.adventure.VelocityBossBarImplementation;
import com.velocitypowered.proxy.command.CommandGraphInjector;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.MinecraftConnectionAssociation;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.player.bossbar.BossBarManager;
import com.velocitypowered.proxy.connection.player.bundle.BundleDelimiterHandler;
import com.velocitypowered.proxy.connection.player.resourcepack.ResourcePackTransfer;
import com.velocitypowered.proxy.connection.player.resourcepack.VelocityResourcePackInfo;
import com.velocitypowered.proxy.connection.player.resourcepack.handler.ResourcePackHandler;
import com.velocitypowered.proxy.connection.util.ConnectionMessages;
import com.velocitypowered.proxy.connection.util.ConnectionRequestResults.Impl;
import com.velocitypowered.proxy.connection.util.FallbackServers;
import com.velocitypowered.proxy.connection.util.VelocityInboundConnection;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.netty.MinecraftEncoder;
import com.velocitypowered.proxy.protocol.packet.AvailableCommandsPacket;
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
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import com.velocitypowered.proxy.tablist.InternalTabList;
import com.velocitypowered.proxy.tablist.KeyedVelocityTabList;
import com.velocitypowered.proxy.tablist.VelocityTabList;
import com.velocitypowered.proxy.tablist.VelocityTabListLegacy;
import com.velocitypowered.proxy.util.ComponentUtils;
import com.velocitypowered.proxy.util.DurationUtils;
import com.velocitypowered.proxy.util.TranslatableMapper;
import com.velocitypowered.proxy.util.collect.CappedSet;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

@SuppressWarnings("UnstableApiUsage")
public class ConnectedPlayer implements MinecraftConnectionAssociation, Player, KeyIdentifiable, VelocityInboundConnection {

  public static final int MAX_CLIENTSIDE_PLUGIN_CHANNELS = Integer.getInteger("velocity.max-clientside-plugin-channels", 1024);

  private static final PlainTextComponentSerializer PASS_THRU_TRANSLATE =
      PlainTextComponentSerializer.builder().flattener(TranslatableMapper.FLATTENER).build();

  private static final ComponentLogger LOGGER = ComponentLogger.logger(ConnectedPlayer.class);

  static final PermissionProvider DEFAULT_PERMISSIONS = s -> PermissionResolver.ALWAYS_UNDEFINED;

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

  private final @Nullable InetSocketAddress virtualHost;

  private final @Nullable String rawVirtualHost;

  private final HandshakeIntent handshakeIntent;

  private GameProfile profile;

  /**
   * The permission resolver used to evaluate permission checks for this player.
   */
  private PermissionResolver permissionResolver;

  private long ping = -1;

  private final boolean onlineMode;

  private @Nullable VelocityServerConnection connectedServer;

  private @Nullable VelocityServerConnection connectionInFlight;

  private @Nullable PlayerSettings settings;

  private @Nullable ModInfo modInfo;

  private final Set<VelocityBossBarImplementation> bossBars = new HashSet<>();

  private Component playerListHeader = Component.empty();

  private Component playerListFooter = Component.empty();

  private final InternalTabList tabList;

  private final VelocityServer server;

  private ClientConnectionPhase connectionPhase;

  private final Collection<ChannelIdentifier> clientsideChannels;

  private final CompletableFuture<Void> teardownFuture = new CompletableFuture<>();

  /**
   * Per-identity lock held while this connection is in the registration/login pipeline.
   * Transferred to this player by {@code PlayerRegistry.registerConnection} and released by
   * exactly one of {@code finalizeLogin}, {@code unregisterConnection}, or the login-lock
   * watchdog. {@code null} once consumed.
   */
  private final AtomicReference<PlayerIdentityLock.LockHandle> identityLock = new AtomicReference<>();

  /**
   * Watchdog cancelling the forced lock release once login completes (or the lock is
   * otherwise consumed). May be {@code null} until registration completes.
   */
  private final AtomicReference<ScheduledFuture<?>> loginLockTimeout = new AtomicReference<>();

  /**
   * Set the first time a {@code DisconnectEvent} is fired for this player; subsequent attempts
   * are no-ops. Used to coordinate between the kick path (where the new connection fires the
   * old player's DisconnectEvent) and the natural teardown path.
   */
  private final AtomicBoolean disconnectFired = new AtomicBoolean(false);

  /**
   * Set the first time {@code LoginEvent} is fired for this player. A {@code false} value at
   * teardown time means the connection was rejected or aborted before LoginEvent ran, so no
   * {@code DisconnectEvent} should be fired (a {@code DisconnectEvent} without a preceding
   * {@code LoginEvent} breaks plugins that pair the two events).
   */
  private final AtomicBoolean loginEventFired = new AtomicBoolean(false);

  /**
   * Set when the full login sequence (through {@code PostLoginEvent}) has completed
   * successfully, i.e. when {@code PlayerRegistry.finalizeLogin} runs.
   */
  private final AtomicBoolean loginCompleted = new AtomicBoolean(false);

  private final ResourcePackHandler resourcePackHandler;

  private final BundleDelimiterHandler bundleHandler = new BundleDelimiterHandler(this);

  /**
   * Whether the player should be excluded from Redis player removal on disconnect.
   */
  private boolean dontRemoveFromRedis;

  /**
   * Whether this player has fully completed the login handshake and is considered
   * truly connected to this proxy. Only {@code true} after the login success packet
   * has been sent. Used to suppress cleanup callbacks (queue, depot) for connections
   * that were registered but then immediately rejected (e.g. duplicate login on a
   * remote proxy).
   */
  private boolean fullyConnected = false;

  /**
   * Whether the player has fully connected to the first server it's connecting to.
   * Set on the first non-null call to {@link #setConnectedServer(VelocityServerConnection)}
   * and never cleared, so that {@link DisconnectEvent.LoginStatus} correctly reports
   * {@link DisconnectEvent.LoginStatus#SUCCESSFUL_LOGIN} even when {@code connectedServer}
   * has been nulled by a kick handler.
   */
  private boolean firstServerConnected = false;

  private @Nullable String clientBrand;

  private @Nullable Locale effectiveLocale;

  private final @Nullable IdentifiedKey playerKey;

  private @Nullable ClientSettingsPacket clientSettingsPacket;

  private volatile ChatQueue chatQueue;

  private final ChatBuilderFactory chatBuilderFactory;

  private final BossBarManager bossBarManager;

  /**
   * The currently active server retry session, or `null` if there is no active session.
   * Used for choosing fallback servers with consistent ordering.
   */
  private @Nullable ServerRetrySession serverRetrySession;

  /**
   * Tasks that delay the queueing operations. These are kept track of to cancel these
   * tasks on disconnect.
   */
  private @Nullable ScheduledTask tryAutoQueueTask;
  private @Nullable ScheduledTask tryQueueOnJoinTask;

  ConnectedPlayer(VelocityServer server, GameProfile profile, MinecraftConnection connection,
                  @Nullable InetSocketAddress virtualHost, @Nullable String rawVirtualHost, boolean onlineMode,
                  HandshakeIntent handshakeIntent, @Nullable IdentifiedKey playerKey) {
    this.server = server;
    this.profile = profile;
    this.connection = connection;
    this.virtualHost = virtualHost;
    this.rawVirtualHost = rawVirtualHost;
    this.handshakeIntent = handshakeIntent;
    this.setPermissionFunction(DEFAULT_PERMISSIONS.createFunction(this));
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
    for (VelocityBossBarImplementation bar : this.bossBars) {
      bar.viewerDisconnected(this);
    }

    if (tryAutoQueueTask != null) {
      tryAutoQueueTask.cancel();
      tryAutoQueueTask = null;
    }

    if (tryQueueOnJoinTask != null) {
      tryQueueOnJoinTask.cancel();
      tryQueueOnJoinTask = null;
    }

    if (this.fullyConnected) {
      this.server.getClusterPlayerService().onPlayerDisconnect(this);

      if (this.server.isQueueEnabled()) {
        this.server.getQueueManager().onLocalPlayerDisconnect(this);
      }
    }
  }

  public ChatBuilderFactory getChatBuilderFactory() {
    return chatBuilderFactory;
  }

  public ChatQueue getChatQueue() {
    return chatQueue;
  }

  /**
   * Discards any messages still being processed by the {@link ChatQueue}, and creates a fresh state for future packets.
   * This should be used on server switches, or whenever the client resets its own 'last seen' state.
   */
  public void discardChatQueue() {
    // No need for atomic swap should only be called from event loop
    ChatQueue oldChatQueue = chatQueue;
    chatQueue = new ChatQueue(this);
    oldChatQueue.close();
  }

  public BundleDelimiterHandler getBundleHandler() {
    return this.bundleHandler;
  }

  @Override
  public @NonNull Identity identity() {
    return Identity.identity(this.getUniqueId());
  }

  @Override
  public String getUsername() {
    return profile.getName();
  }

  @Override
  public Locale getEffectiveLocale() {
    if (effectiveLocale == null && settings != null) {
      return settings.getLocale();
    }

    return effectiveLocale;
  }

  @Override
  public void setEffectiveLocale(@Nullable Locale locale) {
    effectiveLocale = locale;
  }

  @Override
  public UUID getUniqueId() {
    return profile.getId();
  }

  @Override
  public Optional<VelocityServerConnection> getCurrentServer() {
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

  @Override
  public GameProfile getGameProfile() {
    return profile;
  }

  public MinecraftConnection getConnection() {
    return connection;
  }

  @Override
  public long getPing() {
    return this.ping;
  }

  void setPing(long ping) {
    this.ping = ping;
  }

  @Override
  public boolean isOnlineMode() {
    return onlineMode;
  }

  @Override
  public PlayerSettings getPlayerSettings() {
    return settings == null ? ClientSettingsWrapper.DEFAULT : this.settings;
  }

  @Nullable
  public ClientSettingsPacket getClientSettingsPacket() {
    return clientSettingsPacket;
  }

  @Override
  public boolean hasSentPlayerSettings() {
    return settings != null;
  }

  /**
   * Sets player settings.
   *
   * @param clientSettingsPacket the player settings packet
   */
  public void setClientSettings(ClientSettingsPacket clientSettingsPacket) {
    this.clientSettingsPacket = clientSettingsPacket;
    ClientSettingsWrapper cs = new ClientSettingsWrapper(clientSettingsPacket);
    this.settings = cs;
    server.getEventManager().fireAndForget(new PlayerSettingsChangedEvent(this, cs));

    this.server.getClusterPlayerService().onPlayerSettingsChange(this, cs);
  }

  @Override
  public Optional<ModInfo> getModInfo() {
    return Optional.ofNullable(modInfo);
  }

  public void setModInfo(ModInfo modInfo) {
    this.modInfo = modInfo;
    server.getEventManager().fireAndForget(new PlayerModInfoEvent(this, modInfo));
  }

  @Override
  public @NotNull Pointers pointers() {
    return POINTERS_SUPPLIER.view(this);
  }

  @Override
  public InetSocketAddress getRemoteAddress() {
    return (InetSocketAddress) connection.getRemoteAddress();
  }

  @Override
  public Optional<InetSocketAddress> getVirtualHost() {
    return Optional.ofNullable(virtualHost);
  }

  @Override
  public Optional<String> getRawVirtualHost() {
    return Optional.ofNullable(rawVirtualHost);
  }

  void setPermissionFunction(PermissionFunction permissionFunction) {
    if (permissionFunction instanceof PermissionResolver resolver) {
      this.permissionResolver = resolver;
    } else {
      this.permissionResolver = createPermissionResolverAdapter(this, permissionFunction);
    }
  }

  @Override
  public boolean isActive() {
    return connection.getChannel().isActive();
  }

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
  public Component translateMessage(Component message) {
    Locale locale = this.getEffectiveLocale();
    if (locale == null && settings != null) {
      locale = settings.getLocale();
    }

    if (locale == null) {
      locale = Locale.getDefault();
    }

    return GlobalTranslator.render(message, locale);
  }

  @Override
  @SuppressWarnings("deprecation")
  public void sendMessage(@NonNull Identity identity, @NonNull Component message) {
    Component translated = translateMessage(message);

    connection.write(getChatBuilderFactory().builder().component(translated).forIdentity(identity).toClient());
  }

  @Override
  @SuppressWarnings("deprecation")
  public void sendMessage(@NonNull Identity identity, @NonNull Component message,
                          @NonNull MessageType type) {
    Preconditions.checkNotNull(message, "message");
    Preconditions.checkNotNull(type, "type");

    Component translated = translateMessage(message).replaceText(TextReplacementConfig.builder().match("''").replacement("'").build());

    connection.write(getChatBuilderFactory().builder()
        .component(translated).forIdentity(identity)
        .setType(type == MessageType.CHAT ? ChatType.CHAT : ChatType.SYSTEM)
        .toClient());
  }

  @Override
  public void sendActionBar(@NonNull Component message) {
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

  @Override
  public Component getPlayerListHeader() {
    return this.playerListHeader;
  }

  @Override
  public Component getPlayerListFooter() {
    return this.playerListFooter;
  }

  @Override
  public void sendPlayerListHeader(@NonNull Component header) {
    this.sendPlayerListHeaderAndFooter(header, this.playerListFooter);
  }

  @Override
  public void sendPlayerListFooter(@NonNull Component footer) {
    this.sendPlayerListHeaderAndFooter(this.playerListHeader, footer);
  }

  @Override
  public void sendPlayerListHeaderAndFooter(@NotNull Component header,
                                            @NotNull Component footer) {
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

  @Override
  public void showTitle(@NonNull Title title) {
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

  @SuppressWarnings("ConstantValue")
  @Override
  public <T> void sendTitlePart(@NotNull TitlePart<T> part, @NotNull T value) {
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

  @Override
  public void clearTitle() {
    if (this.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      connection.write(GenericTitlePacket.constructTitlePacket(
          GenericTitlePacket.ActionType.HIDE, this.getProtocolVersion()));
    }
  }

  @Override
  public void resetTitle() {
    if (this.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      connection.write(GenericTitlePacket.constructTitlePacket(
          GenericTitlePacket.ActionType.RESET, this.getProtocolVersion()));
    }
  }

  @Override
  public void hideBossBar(@NonNull BossBar bar) {
    if (this.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_9)) {
      VelocityBossBarImplementation impl = VelocityBossBarImplementation.get(bar);
      if (impl.viewerRemove(this)) {
        this.bossBars.remove(impl);
      }
    }
  }

  @Override
  public void showBossBar(@NonNull BossBar bar) {
    if (this.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_9)) {
      VelocityBossBarImplementation impl = VelocityBossBarImplementation.get(bar);
      if (impl.viewerAdd(this)) {
        this.bossBars.add(impl);
      }
    }
  }

  @Override
  public ConnectionRequestBuilder createConnectionRequest(RegisteredServer server) {
    return createConnectionRequest((VelocityRegisteredServer) server);
  }

  private ConnectionRequestBuilder createConnectionRequest(VelocityRegisteredServer server) {
    return new ConnectionRequestBuilderImpl(server, this.connectedServer);
  }

  private ConnectionRequestBuilder createConnectionRequest(VelocityRegisteredServer server,
                                                           @Nullable VelocityServerConnection previousConnection) {
    return new ConnectionRequestBuilderImpl(server, previousConnection);
  }

  @Override
  public List<GameProfile.Property> getGameProfileProperties() {
    return this.profile.getProperties();
  }

  @Override
  public void setGameProfileProperties(List<GameProfile.Property> properties) {
    this.profile = profile.withProperties(Preconditions.checkNotNull(properties));
  }

  @Override
  public void clearPlayerListHeaderAndFooter() {
    clearPlayerListHeaderAndFooterSilent();
    if (this.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      this.connection.write(HeaderAndFooterPacket.reset(this.getProtocolVersion()));
    }
  }

  public void clearPlayerListHeaderAndFooterSilent() {
    this.playerListHeader = Component.empty();
    this.playerListFooter = Component.empty();
  }

  @Override
  public InternalTabList getTabList() {
    return tabList;
  }

  /**
   * Sets whether the disconnect event should remove the player from the Redis cache.
   *
   * @param remove Whether to remove the player or not.
   */
  public void setDontRemoveFromRedis(boolean remove) {
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
   * Executed when this player is fully connected, meaning the login success packet has been sent
   * and the player is considered truly online on this proxy.
   */
  public void fullyConnected() {
    this.fullyConnected = true;

    if (this.server.isQueueEnabled()) {
      this.server.getQueueManager().onLocalPlayerConnect(this);
    }
  }

  @Override
  public void disconnect(Component reason) {
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
  public void disconnect0(Component reason, boolean duringLogin) {
    if (connection.isKnownDisconnect()) {
      return;
    }

    Component translated = this.translateMessage(reason);

    if (server.getConfiguration().isLogPlayerDisconnections()) {
      LOGGER.info(Component.text(this + " has disconnected: ").append(translated));
    }

    connection.closeWith(DisconnectPacket.create(translated, this.getProtocolVersion(), connection.getState()));
  }

  public @Nullable VelocityServerConnection getConnectedServer() {
    return connectedServer;
  }

  public @Nullable VelocityServerConnection getConnectionInFlight() {
    return connectionInFlight;
  }

  public VelocityServerConnection getConnectionInFlightOrConnectedServer() {
    return connectionInFlight != null ? connectionInFlight : connectedServer;
  }

  public void resetInFlightConnection() {
    connectionInFlight = null;
  }

  /**
   * Builds and sends an {@link AvailableCommandsPacket} to this player using the last known
   * backend command tree from the currently connected server (if any) merged with the current
   * proxy command set. Safe to call even when the backend has never sent its own command tree;
   * in that case only proxy commands are included.
   *
   * <p>This method is non-blocking: the packet is written asynchronously on the player's event
   * loop after the {@link PlayerAvailableCommandsEvent} has been fired.
   *
   * @return a future that completes once the packet has been written (or an error has been logged)
   */
  @Override
  public CompletableFuture<Void> sendAvailableCommands() {
    return sendAvailableCommands(this.connectedServer);
  }

  /**
   * Builds and sends an {@link AvailableCommandsPacket} to this player using the backend command
   * tree from the given server connection (if any) merged with the current proxy command set.
   * Used by the backend session handler to ensure the packet is built from the exact connection
   * that received the source tree, even if the player has since moved to another backend.
   *
   * @param conn the server connection whose backend command tree should be used, or null to send
   *             only proxy commands
   * @return a future that completes once the packet has been written (or an error has been logged)
   */
  public CompletableFuture<Void> sendAvailableCommands(@Nullable VelocityServerConnection conn) {
    RootCommandNode<CommandSource> workingNode = new RootCommandNode<>();
    if (conn != null) {
      RootCommandNode<CommandSource> backendNode = conn.getBackendCommandsNode();
      if (backendNode != null) {
        for (CommandNode<CommandSource> child : backendNode.getChildren()) {
          workingNode.addChild(child);
        }
      }
    }

    if (server.getConfiguration().isAnnounceProxyCommands()) {
      // Inject commands from the proxy.
      CommandGraphInjector<CommandSource> injector = server.getCommandManager().getInjector();
      injector.inject(workingNode, this);

      // Omit the click-callback command from the client's command tree unless:
      // - the client is 1.21.6+ (needs it to suppress the unknown-command confirmation prompt), AND
      // - at least one callback has been registered since proxy startup (i.e. some plugin is
      //   using the click-callback feature).
      if (this.connection.getProtocolVersion().lessThan(ProtocolVersion.MINECRAFT_1_21_6)
          || !ClickCallbackManager.INSTANCE.hasHadRegistrations()) {
        workingNode.removeChildByName(ClickCallbackManager.COMMAND_LABEL);
      }
    }

    AvailableCommandsPacket packet = new AvailableCommandsPacket(workingNode);
    return server.getEventManager()
        .fire(new PlayerAvailableCommandsEvent(this, workingNode))
        .thenAcceptAsync(event -> connection.write(packet), connection.eventLoop())
        .exceptionally(ex -> {
          LOGGER.error("Exception while sending available commands to {}", this, ex);
          return null;
        });
  }

  /**
   * Handles unexpected disconnects.
   *
   * @param server    the server we disconnected from
   * @param throwable the exception
   * @param safe      whether we can safely reconnect to a new server
   */
  public void handleConnectionException(VelocityRegisteredServer server,
                                        Throwable throwable,
                                        boolean safe) {
    if (!isActive() || connection.isKnownDisconnect()) {
      // No point trying to recover an inactive connection or one already being torn down.
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
        LOGGER.error("{}: unable to connect to server {}", this, server.getServerInfo().getName());
      } else {
        LOGGER.error("{}: unable to connect to server {}", this, server.getServerInfo().getName(), wrapped);
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
  public void handleConnectionException(VelocityRegisteredServer server,
                                        DisconnectPacket disconnect,
                                        boolean safe) {
    if (!isActive() || connection.isKnownDisconnect()) {
      // No point trying to recover an inactive connection or one already being torn down.
      return;
    }

    Component disconnectReason = disconnect.getReason().getComponent();
    String plainTextReason = PASS_THRU_TRANSLATE.serialize(disconnectReason);
    if (connectedServer != null && connectedServer.getServerInfo().equals(server.getServerInfo())) {
      if (this.server.getConfiguration().isLogPlayerConnections()) {
        LOGGER.info("{}: kicked from server {}: {}", this, server.getServerInfo().getName(), plainTextReason);
      }

      handleConnectionException(server, disconnectReason,
          Component.translatable("velocity.error.moved-to-new-server", NamedTextColor.RED)
              .arguments(
                  Argument.string("server", server.getServerInfo().getName()),
                  Argument.component("reason", disconnectReason)), safe);
    } else {
      if (this.server.getConfiguration().isLogPlayerConnections()) {
        LOGGER.error("{}: disconnected while connecting to {}: {}", this,
            server.getServerInfo().getName(), plainTextReason);
      }

      handleConnectionException(server, disconnectReason,
          Component.translatable("velocity.error.cant-connect", NamedTextColor.RED)
              .arguments(
                  Argument.string("server", server.getServerInfo().getName()),
                  Argument.component("reason", disconnectReason)), safe);
    }

    if (this.server.isQueueEnabled()) {
      for (String reason : this.server.getConfiguration().getQueue().getBannedReason()) {
        if (ComponentUtils.containsString(disconnectReason, reason)) {
          this.server.getQueueManager().removePlayerEntirely(this);
          break;
        }
      }
    }
  }

  private void handleConnectionException(VelocityRegisteredServer rs,
                                         @Nullable Component kickReason,
                                         Component friendlyReason,
                                         boolean safe) {
    if (!isActive() || connection.isKnownDisconnect()) {
      // No point trying to recover an inactive connection or one already being torn down.
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
      var retrySession = currentServerRetrySession();
      retrySession.exclude(rs);
      Optional<VelocityRegisteredServer> next = retrySession.getNextServerToTry();

      result = next.map(RedirectPlayer::create)
          .orElseGet(() -> DisconnectPlayer.create(friendlyReason));
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

  private void handleKickEvent(KickedFromServerEvent originalEvent, Component friendlyReason,
                               boolean kickedFromCurrent) {
    server.getEventManager().fire(originalEvent).thenAcceptAsync(event -> {
      // There can't be any connection in flight now.
      connectionInFlight = null;

      // Make sure we clear the current connected server as the connection is invalid.
      VelocityServerConnection previousConnection = connectedServer;
      if (kickedFromCurrent) {
        connectedServer = null;
      }

      if (!isActive() || connection.isKnownDisconnect()) {
        // No point trying to recover an inactive connection or one already being torn down.
        return;
      }

      switch (event.getResult()) {
        case DisconnectPlayer res -> disconnect(res.getReasonComponent());
        // Cast required (API event class)
        case RedirectPlayer res -> createConnectionRequest((VelocityRegisteredServer) res.getServer(), previousConnection).connect()
            .whenCompleteAsync((status, throwable) -> {
              // Cast required (API event class)
              VelocityRegisteredServer server = (VelocityRegisteredServer) res.getServer();
              if (throwable != null) {
                handleConnectionException(server, throwable, true);
                return;
              }

              switch (status.getStatus()) {
                // Impossible/nonsensical cases
                case ALREADY_CONNECTED -> LOGGER.error("{}: already connected to {}", this,
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
                  handleConnectionException(server,
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
                      Component kickMsg = originalEvent.getServerKickReason().orElse(Component.empty());
                      VelocityQueue<?> queue = this.server.getQueueManager().getQueue(targetServerName);

                      // Checks if the kick reason is valid for a re-queue
                      // This is done to make sure players don't get constantly sent over and over again in a kick loop
                      boolean isValidReason = this.server.getConfiguration().getQueue().getBannedReason()
                          .stream()
                          .noneMatch(text -> ComponentUtils.containsString(kickMsg, text));

                      if (isValidReason && (queue.getState() != QueueState.PAUSED
                          || this.server.getConfiguration().getQueue().isAllowPausedQueueJoining())) {
                        queue.enqueue(this);
                      }
                    }
                  }
                }
                default -> {
                }
                // The only remaining value is successful (no need to do anything!)
              }
            }, connection.eventLoop());
        case Notify res -> {
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
   * Returns the currently active retry session, or creates one if there is no active session.
   * Used for choosing fallback servers with consistent ordering.
   */
  public @NonNull ServerRetrySession currentServerRetrySession() {
    if (serverRetrySession == null) {
      LOGGER.debug("Creating new server retry session.");
      serverRetrySession = new ServerRetrySession();
    }

    if (serverRetrySession.exhausted()) {
      LOGGER.debug("Fallback server retry session is exhausted.");
    }

    return serverRetrySession;
  }

  /**
   * Resets the server retry session. Should be called once the session is complete, meaning
   * a server to connect to has been found and the session can be discarded.
   * After calling this, the next call of {@code currentServerRetrySession()} will result
   * in a new session being created.
   * Used for choosing fallback servers with consistent ordering.
   */
  private void resetServerRetrySession() {
    if (serverRetrySession != null) {
      LOGGER.debug("Resetting server retry session.");
      serverRetrySession = null;
    }
  }

  /**
   * Sets the player's new connected server and clears the in-flight connection.
   *
   * @param serverConnection the new server connection
   */
  public void setConnectedServer(@Nullable VelocityServerConnection serverConnection) {
    this.connectedServer = serverConnection;
    resetServerRetrySession();

    if (serverConnection == connectionInFlight) {
      connectionInFlight = null;
    }

    if (serverConnection != null) {
      if (server.isQueueEnabled()) {
        String queueServerName = server.getConfiguration().getQueue().getQueueServer();
        boolean destinationIsQueueServer = !queueServerName.isEmpty()
            && serverConnection.getServerInfo().getName().equals(queueServerName);

        if (!destinationIsQueueServer
            && server.getConfiguration().getQueue().isRemovePlayerOnServerSwitch()
            && firstServerConnected) {
          // Only remove player from all queues entirely if this is NOT the first server we connect to (firstServerConnected flag)
          // to ensure timeouts work correctly when a player re-joins the network.
          // Also skip removal when moving to the queue-server so the player stays in their queue.
          server.getQueueManager().removePlayerEntirely(this);
        }

        // Auto-queue is mutually exclusive with queue-server
        if (queueServerName.isEmpty()) {
          tryAutoQueue(serverConnection);
        }

        if (!firstServerConnected) {
          tryQueueOnJoin(serverConnection);
        }
      }

      if (!firstServerConnected) {
        firstServerConnected = true;
      }
    }
  }

  /**
   * Returns {@code true} if the given server switch should be blocked because the player is
   * in a queue while on the configured queue-server, and does not have the bypass permission.
   *
   * <p>The switch is allowed (returns {@code false}) if the destination is one of the servers
   * the player is currently queued for — this ensures the queue manager can still transfer the
   * player to their destination without being blocked.</p>
   */
  private boolean shouldBlockQueueServerSwitch(VelocityRegisteredServer destination) {
    if (!server.isQueueEnabled()) {
      return false;
    }

    String queueServerName = server.getConfiguration().getQueue().getQueueServer();
    if (queueServerName.isEmpty()) {
      return false;
    }

    // Only block if the player is currently on the queue-server
    if (connectedServer == null || !connectedServer.getServerInfo().getName().equals(queueServerName)) {
      return false;
    }

    // Don't block if the player is not in any queue
    if (!server.getQueueManager().isQueued(this)) {
      return false;
    }

    // Allow the switch if the destination is a server the player is queued for
    // (i.e. the queue manager is sending them to their destination)
    for (var queue : server.getQueueManager().getQueues()) {
      if (queue.contains(this) && queue.getName().equals(destination.getServerInfo().getName())) {
        return false;
      }
    }

    // Allow if the player has the bypass permission
    return !hasPermission("velocity.queue.server-switch.bypass");
  }

  private void tryAutoQueue(@NonNull VelocityServerConnection joinedServer) {
    if (!server.isQueueEnabled() || server.getQueueManager().isQueued(this)) {
      return;
    }

    Map<String, List<String>> autoQueueServers = server.getConfiguration().getQueue().getAutoQueueServers();

    String currentServerName = joinedServer.getServerInfo().getName();
    List<VelocityRegisteredServer> queueServers = autoQueueServers.getOrDefault(currentServerName, emptyList())
        .stream()
        .map(server::getServer)
        .flatMap(Optional::stream)
        .toList();

    if (queueServers.isEmpty()) {
      return;
    }

    if (tryAutoQueueTask != null) {
      LOGGER.debug("Cancelling previous auto-queue for player {}.", getUsername());

      tryAutoQueueTask.cancel();
      tryAutoQueueTask = null;
    }

    LOGGER.debug("Scheduling auto-queue for player {}.", getUsername());

    tryAutoQueueTask = server.getScheduler().buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
      // Safeguard if this player is offline. Should never be reached because the task
      // should be cancelled by ConnectedPlayer#disconnected.
      if (!server.isPlayerOnline(this)) {
        LOGGER.debug("Aborting auto-queueing player {} (now offline).", getUsername());
        return;
      }

      if (connectedServer != joinedServer || connectionInFlight != null) {
        LOGGER.debug("Aborting auto-queueing player {} (server mismatch).", getUsername());
        return;
      }

      if (server.getQueueManager().isQueued(this)) {
        LOGGER.debug("Aborting auto-queueing player {} (now enqueued).", getUsername());
        return;
      }

      LOGGER.debug("Auto-queueing player {}.", getUsername());
      if (server.getConfiguration().getQueue().isAllowMultiQueue()) {
        for (VelocityRegisteredServer target : queueServers) {
          server.getQueueManager().queue(this, target);
        }
      } else {
        VelocityRegisteredServer target = queueServers.getFirst();
        server.getQueueManager().queue(this, target);
      }

      tryAutoQueueTask = null;
    })
        .delay(Duration.ofSeconds(2))
        .schedule();
  }

  private void tryQueueOnJoin(@NonNull VelocityServerConnection firstServer) {
    List<String> queueOnJoinServers = server.getConfiguration().getQueue().getQueueOnJoinServers();
    if (queueOnJoinServers.isEmpty()) {
      return;
    }

    LOGGER.debug("Scheduling queue-on-join for player {}.", getUsername());

    tryQueueOnJoinTask = server.getScheduler().buildTask(VelocityVirtualPlugin.INSTANCE, () -> {
      // Safeguard if this player is offline. Should never be reached because the task
      // should be cancelled by ConnectedPlayer#disconnected.
      if (!server.isPlayerOnline(this)) {
        LOGGER.debug("Aborting queue-on-join for player {} (now offline).", getUsername());
        return;
      }

      if (connectedServer != firstServer || connectionInFlight != null) {
        LOGGER.debug("Aborting queue-on-join for player {} (server mismatch).", getUsername());
        return;
      }

      LOGGER.debug("Applying queue-on-join for player {}.", getUsername());

      for (String targetName : queueOnJoinServers) {
        server.getServer(targetName).ifPresentOrElse(
            target -> server.getQueueManager().queue(this, target),
            () -> LOGGER.warn("queue-on-join server '{}' is not registered!", targetName)
        );
      }

      tryQueueOnJoinTask = null;
    })
        .delay(Duration.ofSeconds(2))
        .schedule();
  }

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

  void teardown() {
    if (connectionInFlight != null) {
      connectionInFlight.disconnect();
    }

    if (connectedServer != null) {
      connectedServer.disconnect();
    }

    server.getPlayerRegistry().unregisterConnection(this);
  }

  void setIdentityLock(@NonNull PlayerIdentityLock.LockHandle lock) {
    if (!identityLock.compareAndSet(null, lock)) {
      throw new IllegalStateException("Identity lock already set on " + this);
    }
  }

  @Nullable PlayerIdentityLock.LockHandle consumeIdentityLock() {
    PlayerIdentityLock.LockHandle lock = identityLock.getAndSet(null);
    if (lock != null) {
      ScheduledFuture<?> timeout = loginLockTimeout.getAndSet(null);
      if (timeout != null) {
        timeout.cancel(false);
      }
    }
    return lock;
  }

  void setLoginLockTimeout(@NonNull ScheduledFuture<?> timeout) {
    ScheduledFuture<?> previous = loginLockTimeout.getAndSet(timeout);
    if (previous != null) {
      previous.cancel(false);
    }
  }

  boolean markDisconnectFired() {
    return disconnectFired.compareAndSet(false, true);
  }

  void markLoginEventFired() {
    loginEventFired.set(true);
  }

  boolean isLoginEventFired() {
    return loginEventFired.get();
  }

  void markLoginCompleted() {
    loginCompleted.set(true);
  }

  boolean isLoginCompleted() {
    return loginCompleted.get();
  }

  void completeTeardown(@Nullable Throwable error) {
    if (error == null) {
      teardownFuture.complete(null);
    } else {
      teardownFuture.completeExceptionally(error);
    }
  }

  boolean isFirstServerConnected() {
    return firstServerConnected;
  }

  boolean isKnownDisconnect() {
    return connection.isKnownDisconnect();
  }

  public CompletableFuture<Void> getTeardownFuture() {
    return teardownFuture;
  }

  @Override
  public String toString() {
    boolean isPlayerAddressLoggingEnabled = server.getConfiguration().isPlayerAddressLoggingEnabled();
    String playerIp = isPlayerAddressLoggingEnabled ? getRemoteAddress().toString() : "<ip address withheld>";
    return "[connected player] " + profile.getName() + " (" + playerIp + ")";
  }

  @Override
  @NonNull
  public Tristate getPermissionValue(String permission) {
    return permissionResolver.getPermissionValue(permission);
  }

  @Override
  @Nullable
  @Unmodifiable
  public Map<String, Boolean> getPermissionMap() {
    return permissionResolver.getPermissionMap();
  }

  @Override
  public boolean sendPluginMessage(@NotNull ChannelIdentifier identifier, byte @NotNull [] data) {
    Preconditions.checkNotNull(identifier, "identifier");
    Preconditions.checkNotNull(data, "data");
    PluginMessagePacket message = new PluginMessagePacket(identifier.getId(),
            Unpooled.wrappedBuffer(data));
    connection.write(message);
    return true;
  }

  @Override
  public boolean sendPluginMessage(@NotNull ChannelIdentifier identifier, @NotNull PluginMessageEncoder dataEncoder) {
    requireNonNull(identifier);
    requireNonNull(dataEncoder);
    ByteBuf buf = Unpooled.buffer();
    ByteBufDataOutput dataOutput = new ByteBufDataOutput(buf);
    dataEncoder.encode(dataOutput);
    if (buf.isReadable()) {
      PluginMessagePacket message = new PluginMessagePacket(identifier.getId(), buf);
      connection.write(message);
      return true;
    } else {
      buf.release();
      return false;
    }
  }

  @Override
  @Nullable
  public String getClientBrand() {
    return clientBrand;
  }

  void setClientBrand(@Nullable String clientBrand) {
    this.clientBrand = clientBrand;
  }

  @Override
  public void playSound(@NotNull Sound sound, @NotNull Sound.Emitter emitter) {
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

  @Override
  public void stopSound(@NotNull SoundStop stop) {
    Preconditions.checkNotNull(stop, "stop");
    if (getProtocolVersion().lessThan(ProtocolVersion.MINECRAFT_1_19_3)
        || connection.getState() != StateRegistry.PLAY
        || (stop.source() == Sound.Source.UI
            && getProtocolVersion().lessThan(ProtocolVersion.MINECRAFT_1_21_5))) {
      return;
    }

    connection.write(new ClientboundStopSoundPacket(stop));
  }

  @Override
  public void transferToHost(@NotNull InetSocketAddress address) {
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

        storeAppliedPacks();
        connection.write(new TransferPacket(resultedAddress.getHostName(), resultedAddress.getPort()));
      }
    });
  }

  private void storeAppliedPacks() {
    if (connection.getState() != StateRegistry.PLAY && connection.getState() != StateRegistry.CONFIG) {
      return;
    }

    ResourcePackTransfer.TransferSession session = new ResourcePackTransfer.TransferSession(
        resourcePackHandler.getAppliedResourcePacks());
    byte[] cookieData = ResourcePackTransfer.createCookieData(
        server.getTransferPackSecret().get(), session);

    // When there are no packs to carry we still store an empty payload. Cookies persist on the
    // client across transfers, so skipping the write would leave a cookie a previous proxy stored
    // intact, and a later hop would then read stale applied-pack state. The receiving side decodes
    // an empty payload as "no applied packs".
    connection.write(new ClientboundStoreCookiePacket(
        ResourcePackTransfer.APPLIED_RESOURCE_PACKS_KEY,
        cookieData == null ? new byte[0] : cookieData));
  }

  @Override
  public void storeCookie(Key key, byte[] data) {
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
            Key resultedKey = event.getResult().getKey() == null
                ? event.getOriginalKey() : event.getResult().getKey();
            byte[] resultedData = event.getResult().getData() == null
                ? event.getOriginalData() : event.getResult().getData();

            connection.write(new ClientboundStoreCookiePacket(resultedKey, resultedData));
          }
        }, connection.eventLoop());
  }

  @Override
  public void requestCookie(Key key) {
    Preconditions.checkNotNull(key);
    Preconditions.checkArgument(
        this.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_5),
        "Player version must be at least 1.20.5 to be able to retrieve cookies");

    server.getEventManager().fire(new CookieRequestEvent(this, key))
        .thenAcceptAsync(event -> {
          if (event.getResult().isAllowed()) {
            Key resultedKey = event.getResult().getKey() == null
                ? event.getOriginalKey() : event.getResult().getKey();

            connection.write(new ClientboundCookieRequestPacket(resultedKey));
          }
        }, connection.eventLoop());
  }

  @Override
  public void setServerLinks(@NotNull List<ServerLink> links) {
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
  public int getQueuePriority(String serverName) {
    if (!server.isQueueEnabled()) {
      return 0;
    }

    int maxPriority = 100;

    return PermissionUtils.findHighestPermissionValue(this, "velocity.queue.priority.all.", maxPriority)
        .or(() -> {
          if (serverName.equals("all")) {
            // Skip check if serverName == "all" (already checked above).
            return Optional.empty();
          } else {
            return PermissionUtils.findHighestPermissionValue(this, "velocity.queue.priority." + serverName + ".", maxPriority);
          }
        })
        .orElse(0);
  }

  /**
   * Computes all queue priority levels assigned to this player across every
   * registered backend server.
   *
   * <p>Each entry corresponds to {@code velocity.queue.priority.<server>.<level>} or
   * the global {@code velocity.queue.priority.all.<level>} permissions. Higher
   * values indicate higher priority when entering queues.</p>
   *
   * @return a map of server names to their resolved queue priority values
   */
  @Override
  public Map<String, Integer> getQueuePriorities() {
    Map<String, Integer> priorities = new HashMap<>();

    for (VelocityRegisteredServer server : server.getAllServers()) {
      String serverName = server.getServerInfo().getName();
      priorities.put(serverName, getQueuePriority(serverName));
    }

    priorities.put("all", getQueuePriority("all"));

    return priorities;
  }

  @Override
  public void addCustomChatCompletions(@NotNull Collection<String> completions) {
    Preconditions.checkNotNull(completions, "completions");
    this.sendCustomChatCompletionPacket(completions, PlayerChatCompletionPacket.Action.ADD);
  }

  @Override
  public void removeCustomChatCompletions(@NotNull Collection<String> completions) {
    Preconditions.checkNotNull(completions, "completions");
    this.sendCustomChatCompletionPacket(completions, PlayerChatCompletionPacket.Action.REMOVE);
  }

  @Override
  public void setCustomChatCompletions(@NotNull Collection<String> completions) {
    Preconditions.checkNotNull(completions, "completions");
    this.sendCustomChatCompletionPacket(completions, PlayerChatCompletionPacket.Action.SET);
  }

  private void sendCustomChatCompletionPacket(@NotNull Collection<String> completions,
                                              PlayerChatCompletionPacket.Action action) {
    if (connection.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_19_1)) {
      connection.write(new PlayerChatCompletionPacket(completions.toArray(new String[0]), action));
    }
  }

  @Override
  public void spoofChatInput(String input) {
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

  @Override
  @Deprecated
  public void sendResourcePack(String url) {
    sendResourcePackOffer(new VelocityResourcePackInfo.BuilderImpl(url).build());
  }

  @Override
  @Deprecated
  public void sendResourcePack(String url, byte[] hash) {
    sendResourcePackOffer(new VelocityResourcePackInfo.BuilderImpl(url).setHash(hash).build());
  }

  @Override
  public void sendResourcePackOffer(ResourcePackInfo packInfo) {
    this.resourcePackHandler.checkAlreadyAppliedPack(packInfo.getHash());
    if (this.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      Preconditions.checkNotNull(packInfo, "packInfo");
      this.resourcePackHandler.queueResourcePack(packInfo);
    }
  }

  @Override
  public void sendResourcePacks(@NotNull ResourcePackRequest request) {
    if (this.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
      Preconditions.checkNotNull(request, "packRequest");
      this.resourcePackHandler.queueResourcePack(request);
    }
  }

  @Override
  public void clearResourcePacks() {
    if (this.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_3)) {
      connection.write(new RemoveResourcePackPacket());
      this.resourcePackHandler.clearAppliedResourcePacks();
    }
  }

  @Override
  public void removeResourcePacks(@NotNull UUID id, @NotNull UUID @NotNull ... others) {
    if (this.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_3)) {
      Preconditions.checkNotNull(id, "packUUID");
      if (this.resourcePackHandler.remove(id)) {
        connection.write(new RemoveResourcePackPacket(id));
      }

      for (UUID other : others) {
        if (this.resourcePackHandler.remove(other)) {
          connection.write(new RemoveResourcePackPacket(other));
        }
      }
    }
  }

  @Override
  public void removeResourcePacks(@NotNull ResourcePackRequest request) {
    for (net.kyori.adventure.resource.ResourcePackInfo resourcePackInfo : request.packs()) {
      removeResourcePacks(resourcePackInfo.id());
    }
  }

  @Override
  public void removeResourcePacks(@NotNull ResourcePackRequestLike request) {
    removeResourcePacks(request.asResourcePackRequest());
  }

  @Override
  public void removeResourcePacks(@NotNull ResourcePackInfoLike request,
                                  @NotNull ResourcePackInfoLike @NotNull ... others) {
    removeResourcePacks(request.asResourcePackInfo().id());
    for (ResourcePackInfoLike other : others) {
      removeResourcePacks(other.asResourcePackInfo().id());
    }
  }

  @Override
  @Deprecated
  public @Nullable ResourcePackInfo getAppliedResourcePack() {
    return this.resourcePackHandler.getFirstAppliedPack();
  }

  @Override
  @Deprecated
  public @Nullable ResourcePackInfo getPendingResourcePack() {
    return this.resourcePackHandler.getFirstPendingPack();
  }

  @Override
  public @NotNull Collection<ResourcePackInfo> getAppliedResourcePacks() {
    return this.resourcePackHandler.getAppliedResourcePacks();
  }

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
  public boolean forwardKeepAlive(KeepAlivePacket packet) {
    if (!this.sendKeepAliveToBackend(connectedServer, packet)) {
      return this.sendKeepAliveToBackend(connectionInFlight, packet);
    }

    return false;
  }

  private boolean sendKeepAliveToBackend(@Nullable VelocityServerConnection serverConnection, @NotNull KeepAlivePacket packet) {
    if (serverConnection != null) {
      Long sentTime = serverConnection.getPendingPings().remove(packet.getRandomId());
      if (sentTime != null) {
        MinecraftConnection smc = serverConnection.getConnection();
        StateRegistry clientState = connection.getState();
        boolean stateAllowsForward = smc != null
            && !smc.isClosed()
            && clientState == smc.getState()
            && (clientState == StateRegistry.CONFIG || clientState == StateRegistry.PLAY);
        if (stateAllowsForward) {
          setPing(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sentTime));
          smc.write(packet);
        }
        // We removed this, and so this is ours
        return true;
      }
    }

    return false;
  }

  /**
   * Switches the connection to the client into config state.
   */
  public void switchToConfigState() {
    VelocityServerConnection targetServer = getConnectionInFlightOrConnectedServer();
    server.getEventManager().fire(new PlayerEnterConfigurationEvent(this, targetServer))
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
          // Queue clientbound play packets, and drop stale serverbound ones, during reconfiguration
          connection.addReconfigurationPlayPacketQueueHandler();
        }, connection.eventLoop()).exceptionally((ex) -> {
          LOGGER.error("Error switching player connection to config state", ex);
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
  public void setPhase(ClientConnectionPhase connectionPhase) {
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

  @Override
  public @Nullable IdentifiedKey getIdentifiedKey() {
    return playerKey;
  }

  @Override
  public ProtocolState getProtocolState() {
    return connection.getState().toProtocolState();
  }

  @Override
  public HandshakeIntent getHandshakeIntent() {
    return handshakeIntent;
  }

  public BossBarManager getBossBarManager() {
    return bossBarManager;
  }

  private final class ConnectionRequestBuilderImpl implements ConnectionRequestBuilder {

    private final VelocityRegisteredServer toConnect;

    private final @Nullable VelocityRegisteredServer previousServer;

    ConnectionRequestBuilderImpl(VelocityRegisteredServer toConnect,
                                 @Nullable VelocityServerConnection previousConnection) {
      this.toConnect = Preconditions.checkNotNull(toConnect, "info");
      this.previousServer = previousConnection == null ? null : previousConnection.getServer();
    }

    @Override
    public VelocityRegisteredServer getServer() {
      return toConnect;
    }

    private Optional<ConnectionRequestBuilder.Status> checkServer(VelocityRegisteredServer server) {
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

        if (shouldBlockQueueServerSwitch(toConnect)) {
          sendMessage(Component.translatable("velocity.queue.error.server-switch-blocked"));
          return completedFuture(plainResult(ConnectionRequestBuilder.Status.CONNECTION_CANCELLED, toConnect));
        }

        ServerPreConnectEvent event = new ServerPreConnectEvent(ConnectedPlayer.this, toConnect, previousServer);
        return server.getEventManager().fire(event).thenComposeAsync(newEvent -> {
          // Cast required (API event class)
          VelocityRegisteredServer realDestination = (VelocityRegisteredServer) newEvent.getResult().getServer().orElse(null);
          if (realDestination == null) {
            return completedFuture(plainResult(ConnectionRequestBuilder.Status.CONNECTION_CANCELLED, toConnect));
          }

          Optional<ConnectionRequestBuilder.Status> check = checkServer(realDestination);
          if (check.isPresent()) {
            return completedFuture(plainResult(check.get(), realDestination));
          }

          // Check if the player's version is compatible with the server's minimum version
          if (!checkVersionCompatibility(realDestination)) {
            return completedFuture(plainResult(ConnectionRequestBuilder.Status.CONNECTION_CANCELLED, realDestination));
          }

          VelocityServerConnection con = new VelocityServerConnection(
              realDestination, previousServer, ConnectedPlayer.this, server);
          connectionInFlight = con;

          return con.connect().whenCompleteAsync((result, exception) -> {
            if (result != null && !result.isSuccessful() && !result.isSafe()) {
              handleConnectionException(result.getAttemptedConnection(),
                  // The only way for the reason to be null is if the result is safe
                  DisconnectPacket.create(result.getReasonComponent().orElseThrow(),
                      getProtocolVersion(), connection.getState()), false);
            }

            this.resetIfInFlightIs(con);
          }, connection.eventLoop());
        }, connection.eventLoop());
      });
    }

    private void resetIfInFlightIs(VelocityServerConnection establishedConnection) {
      if (establishedConnection == connectionInFlight) {
        resetInFlightConnection();
      }
    }

    @Override
    public CompletableFuture<Result> connect() {
      return this.internalConnect().thenApply(x -> x);
    }

    @Override
    public CompletableFuture<Boolean> connectWithIndication() {
      return internalConnect().whenCompleteAsync((status, throwable) -> {
        if (throwable != null) {
          handleConnectionException(toConnect, throwable, true);
          return;
        }

        switch (status.getStatus()) {
          case ALREADY_CONNECTED -> sendMessage(ConnectionMessages.ALREADY_CONNECTED);
          case CONNECTION_IN_PROGRESS -> sendMessage(ConnectionMessages.IN_PROGRESS);
          case CONNECTION_CANCELLED -> {
            // Ignored; the plugin probably already handled this.
          }
          case SERVER_DISCONNECTED -> {
            Component reason = status.getReasonComponent()
                    .orElse(ConnectionMessages.INTERNAL_SERVER_CONNECTION_ERROR);
            handleConnectionException(toConnect, DisconnectPacket.create(reason, getProtocolVersion(), connection.getState()), status.isSafe());

            if (server.isQueueEnabled()) {
              for (String r : server.getConfiguration().getQueue().getBannedReason()) {
                if (ComponentUtils.containsString(reason, r)) {
                  server.getQueueManager().removePlayerEntirely(ConnectedPlayer.this);
                }
              }
            }
          }
          default -> {
            // The only remaining value is successful (no need to do anything!)
          }
        }
      }, connection.eventLoop()).thenApply(Result::isSuccessful);
    }

    @Override
    public void fireAndForget() {
      connectWithIndication();
    }
  }

  /**
   * Used for choosing fallback servers with consistent ordering during a "fallback session".
   */
  public final class ServerRetrySession {

    private final Deque<String> serversToTry;

    private ServerRetrySession() {
      serversToTry = FallbackServers.resolveFallbackServers(server, ConnectedPlayer.this).calculateRetryDeque(server);
    }

    /**
     * When a {@code ServerRetrySession} is exhausted, calling {@code getNextServerToTry} will always result in an empty Optional.
     * This method can be used to check this preemptively.
     *
     * @return Whether this retry session is exhausted.
     */
    public boolean exhausted() {
      return serversToTry.isEmpty();
    }

    /**
     * Excludes a server name from this retry session, meaning it will never be returned by {@code getNextServerToTry()} after this call.
     *
     * @param serverName The name of the server to exclude from this session.
     */
    public void exclude(String serverName) {
      serversToTry.remove(serverName);
    }

    /**
     * Excludes a server from this retry session, meaning it will never be returned by {@code getNextServerToTry()} after this call.
     *
     * @param server The server to exclude from this session.
     */
    public void exclude(VelocityRegisteredServer server) {
      exclude(server.getServerInfo().getName());
    }

    /**
     * Finds another server to attempt to log into if we were unexpectedly disconnected from the
     * server.
     *
     * @return the next server to try
     */
    public Optional<VelocityRegisteredServer> getNextServerToTry() {
      while (!serversToTry.isEmpty()) {
        String nextServerName = serversToTry.pop();

        if ((connectedServer != null && hasSameName(connectedServer.getServer(), nextServerName))
            || (connectionInFlight != null && hasSameName(connectionInFlight.getServer(), nextServerName))) {
          // skip server
          continue;
        }

        Optional<VelocityRegisteredServer> maybeNextServer = server.getServer(nextServerName);
        if (maybeNextServer.isEmpty()) {
          // invalid server
          continue;
        }

        return maybeNextServer;
      }

      // serversToTry is exhausted
      return Optional.empty();
    }

    private boolean hasSameName(VelocityRegisteredServer server, String name) {
      return server.getServerInfo().getName().equalsIgnoreCase(name);
    }
  }

  /**
   * Checks if the player's protocol version is compatible with the server's minimum version requirement
   * and modern forwarding compatibility.
   *
   * @param server the server to check compatibility with
   * @return {@code true} if the player's version is compatible, {@code false} otherwise
   */
  public boolean checkVersionCompatibility(VelocityRegisteredServer server) {
    String serverName = server.getServerInfo().getName();
    String serverMinimumVersion = this.server.getConfiguration().getMinimumVersionForServer(serverName);
    String serverMaximumVersion = this.server.getConfiguration().getMaximumVersionForServer(serverName)
        .orElse(ProtocolVersion.MAXIMUM_VERSION.getMostRecentSupportedVersion());

    ProtocolVersion minimumProtocolVersion = ProtocolVersion.getVersionByName(serverMinimumVersion);
    ProtocolVersion maximumProtocolVersion = ProtocolVersion.getVersionByName(serverMaximumVersion);
    ProtocolVersion clientProtocolVersion = getProtocolVersion();

    // Compare the client's protocol version with the server's minimum and maximum required versions
    if (clientProtocolVersion.lessThan(minimumProtocolVersion)
        || clientProtocolVersion.greaterThan(maximumProtocolVersion)) {
      // Send a message to the player instead of disconnecting them from the proxy
      sendMessage(Component.translatable("velocity.error.modern-forwarding-needs-new-client", NamedTextColor.RED)
          .arguments(
              Argument.string("min", serverMinimumVersion),
              Argument.string("max", serverMaximumVersion)));
      return false;
    }

    // Check if the server uses modern forwarding and the client is too old
    PlayerInfoForwarding serverForwardingMode = server.getPlayerInfoForwardingMode();
    ProtocolVersion modernForwardingMinVersion = LEGACY_MODERN_FORWARDING
        ? ProtocolVersion.MINECRAFT_1_7_2
        : ProtocolVersion.MINECRAFT_1_13;
    if (serverForwardingMode == PlayerInfoForwarding.MODERN && clientProtocolVersion.lessThan(modernForwardingMinVersion)) {
      // Disconnect the player with an appropriate message
      disconnect(Component.translatable("velocity.error.modern-forwarding-needs-new-client", NamedTextColor.RED)
          .arguments(
              Argument.string("min", modernForwardingMinVersion.getMostRecentSupportedVersion()),
              Argument.string("max", serverMaximumVersion)));
      return false;
    }

    return true;
  }
}
