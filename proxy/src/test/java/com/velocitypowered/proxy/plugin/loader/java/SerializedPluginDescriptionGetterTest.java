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

import com.velocitypowered.api.plugin.ap.SerializedPluginDescription;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class SerializedPluginDescriptionGetterTest {

  @Test
  void oldNamesStillAnswer() throws Exception {
    SerializedPluginDescription.Dependency dependency = new SerializedPluginDescription.Dependency(
        "x", true);
    SerializedPluginDescription description = new SerializedPluginDescription("demo", "Demo", "1",
        "desc", "https://x", List.of("a"), List.of(dependency), List.of("alias"), "a.B");
    Object[][] expected = {
        {"getId", "demo"}, {"getName", "Demo"}, {"getVersion", "1"}, {"getDescription", "desc"},
        {"getUrl", "https://x"}, {"getAuthors", List.of("a")}, {"getDependencies", List.of(
            dependency)},
        {"getProvides", List.of("alias")}, {"getMain", "a.B"}};
    for (Object[] row : expected) {
      Method method = SerializedPluginDescription.class.getMethod((String) row[0]);
      assertEquals(row[1], method.invoke(description), (String) row[0]);
    }
    assertEquals("x", SerializedPluginDescription.Dependency.class.getMethod("getId").invoke(
        dependency));
    assertEquals(true, SerializedPluginDescription.Dependency.class.getMethod("isOptional").invoke(
        dependency));
  }
}
