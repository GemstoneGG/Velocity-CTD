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
  public JsonElement serialize(final Component src, final Type typeOfSrc,
                               final JsonSerializationContext context) {
    return SERIALIZER.serializeToTree(src);
  }

  @Override
  public Component deserialize(final JsonElement json, final Type typeOfT,
                               final JsonDeserializationContext context) throws JsonParseException {
    return SERIALIZER.deserializeFromTree(json);
  }
}
