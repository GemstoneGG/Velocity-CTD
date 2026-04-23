/*
 * Copyright (C) 2018-2026 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.util;

import com.google.common.base.Preconditions;
import java.net.URI;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a custom URL servers can show in player pause menus.
 * Links can be of a built-in type or use a custom component text label.
 */
public final class ServerLink {

  /**
   * The predefined link type, if one is used.
   */
  private @Nullable Type type;

  /**
   * The custom text label shown to the user, if no built-in type is used.
   */
  private @Nullable Component label;

  /**
   * The URL the player will be directed to when clicking the link.
   */
  private final URI url;

  private ServerLink(Component label, String url) {
    this.label = Preconditions.checkNotNull(label, "label");
    this.url = URI.create(url);
  }

  private ServerLink(Type type, String url) {
    this.type = Preconditions.checkNotNull(type, "type");
    this.url = URI.create(url);
  }

  /**
   * Creates a {@link ServerLink} with a custom component label.
   *
   * @param label the Component to display to the user
   * @param link the URL to open when clicked
   * @return a {@link ServerLink} instance with the given label and URL
   */
  public static ServerLink serverLink(Component label, String link) {
    return new ServerLink(label, link);
  }

  /**
   * Creates a {@link ServerLink} with a built-in type label.
   *
   * @param type the predefined type of the link
   * @param link the URL to open when clicked
   * @return a {@link ServerLink} instance with the given type and URL
   */
  public static ServerLink serverLink(Type type, String link) {
    return new ServerLink(type, link);
  }

  /**
   * Get the type of the server link.
   *
   * @return the type of the server link
   */
  public Optional<Type> getBuiltInType() {
    return Optional.ofNullable(type);
  }

  /**
   * Get the custom component label of the server link.
   *
   * @return the custom component label of the server link
   */
  public Optional<Component> getCustomLabel() {
    return Optional.ofNullable(label);
  }

  /**
   * Get the link {@link URI}.
   *
   * @return the link {@link URI}
   */
  public URI getUrl() {
    return url;
  }

  /**
   * Built-in types of server links.
   *
   * <p>Note: {@link Type#BUG_REPORT} links are shown on the connection error screen.</p>
   */
  public enum Type {

    /**
     * A link to report bugs related to the server or gameplay.
     */
    BUG_REPORT,

    /**
     * A link to the server's community guidelines or rules.
     */
    COMMUNITY_GUIDELINES,

    /**
     * A link to the server’s support or help desk.
     */
    SUPPORT,

    /**
     * A link showing the current server or service status.
     */
    STATUS,

    /**
     * A link to provide feedback to the server staff or developers.
     */
    FEEDBACK,

    /**
     * A link to the server’s community hub or Discord.
     */
    COMMUNITY,

    /**
     * A link to the server's main website.
     */
    WEBSITE,

    /**
     * A link to the server's forums.
     */
    FORUMS,

    /**
     * A link to server or game-related news.
     */
    NEWS,

    /**
     * A link to announcements from the server team.
     */
    ANNOUNCEMENTS
  }
}
