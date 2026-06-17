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

package com.velocityctd.bootstrap;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;

/**
 * The class loader that hosts the proxy and all of its resolved libraries. Its parent is the
 * platform class loader so that proxy classes load cleanly without the bootstrap's own classpath
 * leaking into the proxy runtime.
 */
public final class DependencyClassLoader extends URLClassLoader {

  static {
    ClassLoader.registerAsParallelCapable();
  }

  private DependencyClassLoader(URL[] urls) {
    // ClassLoader name referenced in com.velocitypowered.proxy.Metrics.VelocityMetrics#isBootstrap
    super("velocity-bootstrap", urls, ClassLoader.getSystemClassLoader().getParent());
  }

  static DependencyClassLoader create(List<Path> jars) {
    URL[] urls = new URL[jars.size()];
    for (int i = 0; i < jars.size(); i++) {
      try {
        urls[i] = jars.get(i).toUri().toURL();
      } catch (Exception exception) {
        throw new IllegalStateException("Invalid library path: " + jars.get(i), exception);
      }
    }
    return new DependencyClassLoader(urls);
  }
}
