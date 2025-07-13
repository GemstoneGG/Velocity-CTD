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

package com.velocitypowered.proxy.adventure;

import com.google.auto.service.AutoService;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.bossbar.BossBarImplementation;
import org.jetbrains.annotations.NotNull;

/**
 * Provides an implementation of {@link BossBarImplementation.Provider} for Velocity.
 * This class is responsible for creating instances of {@link BossBarImplementation}
 * that are associated with {@link BossBar} objects in the Velocity proxy.
 *
 * <p>The {@link VelocityBossBarImplementation} listens to the boss bar's state and
 * updates the proxy-side boss bar accordingly.</p>
 */
@AutoService(BossBarImplementation.Provider.class)
@SuppressWarnings("UnstableApiUsage")
public class BossBarImplementationProvider implements BossBarImplementation.Provider {

  @Override
  public final @NotNull BossBarImplementation create(final @NotNull BossBar bar) {
    final VelocityBossBarImplementation impl = new VelocityBossBarImplementation(bar);
    bar.addListener(impl);
    return impl;
  }
}
