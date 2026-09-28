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

package com.velocitypowered.proxy.protocol.packet.brigadier;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.network.ProtocolVersion;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArgumentIdCollisionTest {

  @Test
  void noTwoTypesShareWireIdInAnyVersion() throws Exception {
    Class.forName(ArgumentPropertyRegistry.class.getName());
    Field f = ArgumentPropertyRegistry.class.getDeclaredField("byIdentifier");
    f.setAccessible(true);
    Map<ArgumentIdentifier, ?> byIdentifier = (Map<ArgumentIdentifier, ?>) f.get(null);
    List<String> collisions = new ArrayList<>();
    int checked = 0;
    for (ProtocolVersion v : ProtocolVersion.values()) {
      if (v.isUnknown() || v.isLegacy() || v.lessThan(ProtocolVersion.MINECRAFT_1_19)) {
        continue;
      }
      Map<Integer, String> seen = new HashMap<>();
      for (ArgumentIdentifier id : byIdentifier.keySet()) {
        Integer wire = id.getIdByProtocolVersion(v);
        if (wire == null || wire < 0) {
          continue;
        }
        checked++;
        String prev = seen.putIfAbsent(wire, id.getIdentifier());
        if (prev != null) {
          collisions.add(v + " id " + wire + ": " + prev + " / " + id.getIdentifier());
        }
      }
    }
    assertTrue(checked > 0, "no argument ids were checked");
    assertTrue(collisions.isEmpty(), String.join("\n", collisions));
  }
}
