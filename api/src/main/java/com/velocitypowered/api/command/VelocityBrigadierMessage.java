/*
 * Copyright (C) 2018-2025 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.command;

import com.google.common.base.Preconditions;
import com.mojang.brigadier.Message;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an implementation of brigadier's {@link Message}, providing support for {@link
 * Component} messages.
 */
public final class VelocityBrigadierMessage implements Message, ComponentLike {

  /**
   * Creates a new {@link VelocityBrigadierMessage} using the given {@link Component} as the message.
   *
   * @param message the component to use as the tooltip message
   * @return a new instance of {@link VelocityBrigadierMessage}
   */
  public static VelocityBrigadierMessage tooltip(final Component message) {
    return new VelocityBrigadierMessage(message);
  }

  /**
   * The component backing this brigadier message.
   *
   * <p>This component provides both rich formatting for client display and a fallback
   * plain-text representation via {@link #getString()}.</p>
   */
  private final Component message;

  private VelocityBrigadierMessage(final Component message) {
    this.message = Preconditions.checkNotNull(message, "message");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NotNull
  public Component asComponent() {
    return message;
  }

  /**
   * Returns the message as a plain text.
   *
   * @return message as plain text
   */
  @Override
  public String getString() {
    return PlainTextComponentSerializer.plainText().serialize(message);
  }
}
