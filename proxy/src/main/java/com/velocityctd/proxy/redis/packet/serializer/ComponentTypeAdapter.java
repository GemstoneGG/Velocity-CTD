/*
 * Copyright (C) 2018-2026 Velocity-CTD Contributors
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

package com.velocityctd.proxy.redis.packet.serializer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

/**
 * GSON type adapter that bridges Adventure's {@link Component} type with
 * the {@link GsonComponentSerializer}, allowing components to be used as
 * direct fields in data records without manual serialization.
 */
public final class ComponentTypeAdapter implements JsonSerializer<Component>, JsonDeserializer<Component> {

  private static final GsonComponentSerializer SERIALIZER = GsonComponentSerializer.gson();

  @Override
  public JsonElement serialize(Component src, Type typeOfSrc,
                               JsonSerializationContext context) {
    return SERIALIZER.serializeToTree(src);
  }

  @Override
  public Component deserialize(JsonElement json, Type typeOfT,
                               JsonDeserializationContext context) throws JsonParseException {
    return SERIALIZER.deserializeFromTree(json);
  }
}
