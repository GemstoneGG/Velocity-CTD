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

package com.velocitypowered.natives.util;

import com.google.common.collect.ImmutableList;
import com.velocitypowered.natives.NativeSetupException;
import com.velocitypowered.natives.compression.JavaVelocityCompressor;
import com.velocitypowered.natives.compression.LibdeflateVelocityCompressor;
import com.velocitypowered.natives.compression.VelocityCompressorFactory;
import com.velocitypowered.natives.encryption.JavaVelocityCipher;
import com.velocitypowered.natives.encryption.NativeVelocityCipher;
import com.velocitypowered.natives.encryption.VelocityCipherFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Enumerates all supported natives for Velocity.
 */
public final class Natives {

  private Natives() {
    throw new AssertionError();
  }

  private static Runnable copyAndLoadNative(final String path) {
    return () -> {
      try {
        InputStream nativeLib = Natives.class.getResourceAsStream(path);
        if (nativeLib == null) {
          throw new IllegalStateException("Native library " + path + " not found.");
        }

        Path tempFile = createTemporaryNativeFilename(path.substring(path.lastIndexOf('.')));
        Files.copy(nativeLib, tempFile, StandardCopyOption.REPLACE_EXISTING);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
          try {
            Files.deleteIfExists(tempFile);
          } catch (IOException ignored) {
            // Well, it doesn't matter...
          }
        }));

        try {
          System.load(tempFile.toAbsolutePath().toString());
        } catch (UnsatisfiedLinkError e) {
          throw new NativeSetupException("Unable to load native " + tempFile.toAbsolutePath(), e);
        }
      } catch (IOException e) {
        throw new NativeSetupException("Unable to copy natives", e);
      }
    };
  }

  private static Path createTemporaryNativeFilename(final String ext) throws IOException {
    String temporaryFolderPath = System.getProperty("velocity.natives-tmpdir");
    if (temporaryFolderPath != null) {
      return Files.createTempFile(Path.of(temporaryFolderPath), "native-", ext);
    } else {
      return Files.createTempFile("native-", ext);
    }
  }

  /**
   * Provides the best available {@link VelocityCompressorFactory} implementation for the current platform.
   *
   * <p>This loader attempts to initialize platform-optimized native implementations of libdeflate.
   * The native shared objects are selected based on OS, architecture, and libc (e.g., glibc or musl).
   * If no matching native is available, the Java-based fallback ({@link JavaVelocityCompressor}) will be used.</p>
   *
   * <p>The following native variants may be selected, depending on runtime environment:
   * <ul>
   *   <li>Linux x86_64 (glibc or musl)</li>
   *   <li>Linux AArch64 (glibc or musl)</li>
   *   <li>macOS ARM64</li>
   * </ul>
   * </p>
   *
   * @see LibdeflateVelocityCompressor
   * @see JavaVelocityCompressor
   */
  public static final NativeCodeLoader<VelocityCompressorFactory> compress = new NativeCodeLoader<>(
      ImmutableList.of(
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_X86_64,
              copyAndLoadNative("/linux_x86_64/velocity-compress.so"),
              "libdeflate (Linux x86_64)",
              LibdeflateVelocityCompressor.FACTORY), // compiled with Ubuntu 20.04
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_X86_64_MUSL,
              copyAndLoadNative("/linux_x86_64/velocity-compress-musl.so"),
              "libdeflate (Linux x86_64, musl)",
              LibdeflateVelocityCompressor.FACTORY), // compiled with Alpine 3.18

          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_AARCH64,
              copyAndLoadNative("/linux_aarch64/velocity-compress.so"),
              "libdeflate (Linux aarch64)",
              LibdeflateVelocityCompressor.FACTORY), // compiled with Ubuntu 20.04
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_AARCH64_MUSL,
              copyAndLoadNative("/linux_aarch64/velocity-compress-musl.so"),
              "libdeflate (Linux aarch64, musl)",
              LibdeflateVelocityCompressor.FACTORY), // compiled with Alpine 3.18

          new NativeCodeLoader.Variant<>(NativeConstraints.MACOS_AARCH64,
              copyAndLoadNative("/macos_arm64/velocity-compress.dylib"),
              "libdeflate (macOS ARM64 / Apple Silicon)",
              LibdeflateVelocityCompressor.FACTORY),
          new NativeCodeLoader.Variant<>(NativeCodeLoader.ALWAYS, () -> {
          }, "Java", JavaVelocityCompressor.FACTORY)
      )
  );

  /**
   * Provides the best available {@link VelocityCipherFactory} implementation for the current platform.
   *
   * <p>This loader attempts to initialize native OpenSSL-based AES-CFB8 cipher implementations
   * for the current system architecture and libc runtime. Multiple shared object variants are supported
   * for OpenSSL 1.1.x and 3.x.x. If no native variant is usable, it falls back to the standard
   * Java-based cipher using {@link JavaVelocityCipher}.</p>
   *
   * <p>The following native variants may be selected:
   * <ul>
   *   <li>Linux x86_64 (OpenSSL 1.1.x or 3.x.x, glibc or musl)</li>
   *   <li>Linux AArch64 (OpenSSL 1.1.x or 3.x.x, glibc or musl)</li>
   *   <li>macOS ARM64 (native build)</li>
   * </ul>
   * </p>
   *
   * @see NativeVelocityCipher
   * @see JavaVelocityCipher
   */
  public static final NativeCodeLoader<VelocityCipherFactory> cipher = new NativeCodeLoader<>(
      ImmutableList.of(
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_X86_64,
              copyAndLoadNative("/linux_x86_64/velocity-cipher.so"), // Any local version
              "OpenSSL local (Linux x86_64)", NativeVelocityCipher.FACTORY),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_X86_64,
              copyAndLoadNative("/linux_x86_64/velocity-cipher-ossl30x.so"), // Ubuntu 22.04
              "OpenSSL 3.x.x (Linux x86_64)", NativeVelocityCipher.FACTORY),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_X86_64,
              copyAndLoadNative("/linux_x86_64/velocity-cipher-ossl11x.so"), // Ubuntu 20.04
              "OpenSSL 1.1.x (Linux x86_64)", NativeVelocityCipher.FACTORY),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_X86_64_MUSL,
              copyAndLoadNative("/linux_x86_64/velocity-cipher-ossl30x-musl.so"), // Alpine 3.18
              "OpenSSL 3.x.x (Linux x86_64, musl)", NativeVelocityCipher.FACTORY),

          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_AARCH64,
              copyAndLoadNative("/linux_aarch64/velocity-cipher.so"),
              "OpenSSL local (Linux aarch64)", NativeVelocityCipher.FACTORY), // Any local version
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_AARCH64,
              copyAndLoadNative("/linux_aarch64/velocity-cipher-ossl30x.so"),
              "OpenSSL 3.x.x (Linux aarch64)", NativeVelocityCipher.FACTORY), // Ubuntu 22.04
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_AARCH64,
              copyAndLoadNative("/linux_aarch64/velocity-cipher-ossl11x.so"),
              "OpenSSL 1.1.x (Linux aarch64)", NativeVelocityCipher.FACTORY), // Ubuntu 20.04
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_AARCH64_MUSL,
              copyAndLoadNative("/linux_aarch64/velocity-cipher-ossl30x-musl.so"),
              "OpenSSL 3.x.x (Linux aarch64, musl)", NativeVelocityCipher.FACTORY), // Alpine 3.18

          new NativeCodeLoader.Variant<>(NativeConstraints.MACOS_AARCH64,
              copyAndLoadNative("/macos_arm64/velocity-cipher.dylib"),
              "native (macOS ARM64 / Apple Silicon)",
               NativeVelocityCipher.FACTORY),

          new NativeCodeLoader.Variant<>(NativeCodeLoader.ALWAYS, () -> {
          }, "Java", JavaVelocityCipher.FACTORY)
      )
  );
}
