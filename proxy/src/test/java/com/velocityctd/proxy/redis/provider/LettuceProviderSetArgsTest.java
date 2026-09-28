/*
 * Copyright (C) 2026 Velocity-CTD Contributors
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

package com.velocityctd.proxy.redis.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.lettuce.core.SetArgs;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.protocol.CommandArgs;
import org.junit.jupiter.api.Test;

class LettuceProviderSetArgsTest {

  private static String wire(SetArgs setArgs) {
    CommandArgs<String, String> args = new CommandArgs<>(StringCodec.UTF8);
    setArgs.build(args);
    return args.toCommandString();
  }

  @Test
  void expiryIsSetEx() {
    assertEquals("EX 30", wire(SetArgs.Builder.ex(30)));
  }

  @Test
  void ifAbsentIsSetNx() {
    assertEquals("NX", wire(SetArgs.Builder.nx()));
  }
}
