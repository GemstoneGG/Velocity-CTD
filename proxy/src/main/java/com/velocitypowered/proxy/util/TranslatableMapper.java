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

package com.velocitypowered.proxy.util;

import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.translation.GlobalTranslator;

/**
 * Velocity Translation Mapper.
 */
public enum TranslatableMapper implements BiConsumer<TranslatableComponent, Consumer<Component>> {

  /**
   * Singleton instance of the {@link TranslatableMapper} used to resolve and render
   * {@link TranslatableComponent} objects to localized {@link Component} instances
   * using the {@link GlobalTranslator}.
   *
   * <p>This instance is registered in the {@link #FLATTENER} for use during
   * text flattening, enabling proper localization of translatable messages based on
   * the user's locale or a best-match fallback.</p>
   */
  INSTANCE;

  /**
   * A {@link ComponentFlattener} instance
   * customized to handle {@link TranslatableComponent}s
   * using Velocity’s {@link TranslatableMapper}.
   *
   * <p>This flattener is used to convert complex Adventure components into plain text
   * for display in legacy contexts (e.g. console logs, plugin messages).</p>
   */
  public static final ComponentFlattener FLATTENER = ComponentFlattener.basic().toBuilder()
      .complexMapper(TranslatableComponent.class, TranslatableMapper.INSTANCE)
      .build();

  @Override
  public void accept(final TranslatableComponent translatableComponent,
                     final Consumer<Component> componentConsumer) {
    final Locale locale = ClosestLocaleMatcher.INSTANCE.lookupClosest(Locale.getDefault());
    if (GlobalTranslator.translator().canTranslate(translatableComponent.key(), locale)) {
      componentConsumer.accept(GlobalTranslator.render(translatableComponent, locale));
    } else {
      String fallback = translatableComponent.fallback();
      if (fallback == null || fallback.isBlank()) {
        fallback = translatableComponent.key();
      }

      componentConsumer.accept(Component.text(fallback));
    }
  }
}
