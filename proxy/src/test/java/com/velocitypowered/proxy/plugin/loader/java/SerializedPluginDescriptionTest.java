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

package com.velocitypowered.proxy.plugin.loader.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.velocitypowered.api.plugin.InvalidPluginException;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.plugin.ap.SerializedPluginDescription;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SerializedPluginDescriptionTest {

  @TempDir
  Path dir;

  private Path jar(String json) throws Exception {
    Path path = Files.createTempFile(dir, "plugin", ".jar");
    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(path))) {
      out.putNextEntry(new JarEntry("velocity-plugin.json"));
      out.write(json.getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
    return path;
  }

  private String message(String json) throws Exception {
    JavaPluginLoader loader = new JavaPluginLoader(null, dir);
    return assertThrows(InvalidPluginException.class, () -> loader.loadCandidate(jar(json)))
        .getMessage();
  }

  @Test
  void probe() throws Exception {
    assertEquals("No plugin ID provided.", message("{\"main\":\"a.B\"}"));
    assertEquals("No plugin ID provided.", message("{\"id\":null,\"main\":\"a.B\"}"));
    assertEquals("No plugin main class provided.", message("{\"id\":\"demo\"}"));
    assertEquals("No plugin main class provided.", message("{\"id\":\"demo\",\"main\":null}"));
    assertEquals("Did not find a valid velocity-plugin.json.", message(""));
    assertEquals("Plugin ID 'Bad' is invalid.", message("{\"id\":\"Bad\",\"main\":\"a.B\"}"));
    assertEquals("Plugin ID '{\"x\":1}' is invalid.", message(
        "{\"id\":{\"x\":1},\"main\":\"a.B\"}"));
    assertEquals("Dependency ID 'Nope' for plugin 'demo' is invalid.",
        message("{\"id\":\"demo\",\"main\":\"a.B\",\"dependencies\":[{\"id\":\"Nope\"}]}"));
    PluginDescription ok = new JavaPluginLoader(null, dir).loadCandidate(jar(
        "{\"id\":\"demo\",\"main\":\"a.B\",\"name\":\"\",\"authors\":[\"me\"],"
            + "\"dependencies\":[{\"id\":\"dep\"},{\"id\":\"soft\",\"optional\":true}]}"));
    assertEquals("demo", ok.getId());
    assertFalse(ok.getName().isPresent(), "an empty name is no name");
    assertEquals(List.of("me"), ok.getAuthors());
    assertFalse(ok.getDependency("dep").orElseThrow().isOptional());
    assertTrue(ok.getDependency("soft").orElseThrow().isOptional());
  }

  @Test
  void serializedShapeIsUnchanged() {
    SerializedPluginDescription description = new SerializedPluginDescription("demo", "Demo", "",
        null, null, List.of("a"), List.of(new SerializedPluginDescription.Dependency("x", true)),
        null, "a.B");
    assertEquals("{\"id\":\"demo\",\"name\":\"Demo\",\"authors\":[\"a\"],"
        + "\"dependencies\":[{\"id\":\"x\",\"optional\":true}],\"provides\":[],\"main\":\"a.B\"}",
        new Gson().toJson(description));
    assertEquals(description, new Gson().fromJson(new Gson().toJson(description),
        SerializedPluginDescription.class));
  }
}
