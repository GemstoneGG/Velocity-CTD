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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

public final class ComponentDeserializer extends JsonDeserializer<Component> {

  private static final GsonComponentSerializer SERIALIZER = GsonComponentSerializer.gson();

  @Override
  public Component deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    byte[] binary = p.getBinaryValue();
    if (binary == null) {
      return null;
    }
    return SERIALIZER.deserialize(new String(binary, StandardCharsets.UTF_8));
  }
}
