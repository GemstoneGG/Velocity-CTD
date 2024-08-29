/*
 * Copyright (C) 2023 Velocity Contributors
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

package com.velocitypowered.proxy.adventure;

import com.google.auto.service.AutoService;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Implementation of {@link ClickCallback.Provider}.
 */
@AutoService(ClickCallback.Provider.class)
@SuppressWarnings("UnstableApiUsage") // permitted provider
public class ClickCallbackProviderImpl implements ClickCallback.Provider {
  @Override
  public @NotNull ClickEvent create(
      final @NotNull ClickCallback<Audience> callback,
      final ClickCallback.@NotNull Options options
  ) {
    final UUID id = ClickCallbackManager.INSTANCE.register(callback, options);
    return ClickEvent.runCommand(ClickCallbackManager.COMMAND + id);
  }
}
